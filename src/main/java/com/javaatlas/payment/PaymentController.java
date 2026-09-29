package com.javaatlas.payment;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;
import com.javaatlas.course.Course;
import com.javaatlas.course.CourseRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Checkout flow:
 * 1. POST /order   free course: enrolls immediately. Paid course: creates a Razorpay order
 *                  (the price comes from the database, never from the browser).
 * 2. The browser opens Razorpay Checkout and the learner pays.
 * 3. POST /verify  checks Razorpay's signature and marks the enrollment PAID.
 * 4. POST /webhook is Razorpay's server-to-server backup, in case the browser closes before step 3.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final Pattern EVENT = Pattern.compile("\"event\"\\s*:\\s*\"([a-z._]+)\"");
    private static final Pattern ORDER_ID = Pattern.compile("\"order_id\"\\s*:\\s*\"(order_[A-Za-z0-9]+)\"");
    private static final Pattern PAYMENT_ID = Pattern.compile("\"id\"\\s*:\\s*\"(pay_[A-Za-z0-9]+)\"");

    public record OrderRequest(@NotBlank(message = "Choose a course.") String courseSlug) {
    }

    public record OrderResponse(boolean free, String courseSlug, String orderId, int amount, String currency, String keyId,
                                String courseTitle, String name, String email) {
    }

    public record VerifyRequest(@NotBlank String razorpayOrderId,
                                @NotBlank String razorpayPaymentId,
                                @NotBlank String razorpaySignature) {
    }

    public record VerifyResponse(String status, String courseSlug) {
    }

    private final AppProperties props;
    private final RazorpayClient razorpay;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;

    private final com.javaatlas.common.RateLimiter limiter;

    public PaymentController(AppProperties props, RazorpayClient razorpay, EnrollmentRepository enrollments, CourseRepository courses,
                             com.javaatlas.common.RateLimiter limiter) {
        this.limiter = limiter;
        this.props = props;
        this.razorpay = razorpay;
        this.enrollments = enrollments;
        this.courses = courses;
    }

    @PostMapping("/order")
    public OrderResponse order(@Valid @RequestBody OrderRequest req, @AuthenticationPrincipal Jwt jwt) {
        if (!limiter.allow("pay-order:" + jwt.getSubject(), 20, java.time.Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many checkout attempts. Please wait a while and try again.");
        }
        Course course = courses.findBySlug(req.courseSlug())
                .filter(Course::isPublished)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "That course doesn’t exist."));
        Long userId = Long.valueOf(jwt.getSubject());
        if (enrollments.existsByUserIdAndCourse_IdAndStatus(userId, course.getId(), Enrollment.Status.PAID)) {
            throw new ApiException(HttpStatus.CONFLICT, "already_enrolled", "You’re already enrolled in this course.");
        }
        String name = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");

        if (course.getPriceInr() == 0) {
            Enrollment free = new Enrollment(userId, course, 0, "free-" + UUID.randomUUID().toString().substring(0, 18));
            free.markPaid("free");
            enrollments.save(free);
            return new OrderResponse(true, course.getSlug(), free.getRazorpayOrderId(), 0, "INR", null, course.getTitle(), name, email);
        }

        if (!props.razorpay().enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "checkout_disabled", "Payments aren’t open yet. Please try again later.");
        }
        int amountPaise = Math.multiplyExact(course.getPriceInr(), 100);
        String receipt = "ja-" + userId + "-" + System.currentTimeMillis();   // Razorpay allows up to 40 characters
        String orderId;
        try {
            orderId = razorpay.createOrder(amountPaise, receipt,
                    Map.of("courseSlug", course.getSlug(), "userId", String.valueOf(userId)));
        } catch (RuntimeException e) {
            log.error("Could not create Razorpay order", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "payment_provider_error", "The payment provider didn’t respond. Please try again in a minute.");
        }
        enrollments.save(new Enrollment(userId, course, amountPaise, orderId));
        return new OrderResponse(false, course.getSlug(), orderId, amountPaise, "INR", props.razorpay().keyId(), course.getTitle(), name, email);
    }

    @PostMapping("/verify")
    @Transactional
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest req, @AuthenticationPrincipal Jwt jwt) {
        if (!limiter.allow("pay-verify:" + jwt.getSubject(), 60, java.time.Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts. Please wait a while and try again.");
        }
        Long userId = Long.valueOf(jwt.getSubject());
        Enrollment enrollment = enrollments.findByRazorpayOrderId(req.razorpayOrderId())
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "We couldn’t find that order."));
        if (!razorpay.isValidPayment(req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            log.warn("Invalid Razorpay signature for order {}", req.razorpayOrderId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_signature",
                    "We couldn’t verify this payment. If money was deducted, contact support with your payment ID.");
        }
        enrollment.markPaid(req.razorpayPaymentId());
        return new VerifyResponse("PAID", enrollment.getCourse().getSlug());
    }

    /**
     * Configure in the Razorpay dashboard: Settings, Webhooks, URL https://YOUR_API/api/payments/webhook,
     * events payment.captured and order.paid, and the same secret as RAZORPAY_WEBHOOK_SECRET.
     * The body is only read after its signature is verified, so simple pattern matching is safe here.
     */
    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<Void> webhook(@RequestBody String body,
                                        @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        if (!props.razorpay().webhookEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!razorpay.isValidWebhook(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String event = find(EVENT, body);
        if ("payment.captured".equals(event) || "order.paid".equals(event)) {
            String orderId = find(ORDER_ID, body);
            String paymentId = find(PAYMENT_ID, body);
            if (orderId != null) {
                enrollments.findByRazorpayOrderId(orderId).ifPresent(e -> e.markPaid(paymentId));
            }
        }
        return ResponseEntity.ok().build();
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
