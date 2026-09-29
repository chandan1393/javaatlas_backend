package com.javaatlas.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.javaatlas.AppProperties;

/** Talks to the Razorpay Orders API and checks Razorpay signatures. */
@Component
public class RazorpayClient {

    private final AppProperties.Razorpay cfg;
    private final RestClient http;

    public RazorpayClient(AppProperties props) {
        this.cfg = props.razorpay();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .requestFactory(requestFactory)
                .build();
    }

    /** Creates an order and returns its id (order_...). Amount is in paise. */
    public String createOrder(int amountPaise, String receipt, Map<String, String> notes) {
        Map<?, ?> res = http.post()
                .uri("/orders")
                .headers(h -> h.setBasicAuth(cfg.keyId(), cfg.keySecret()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
                .retrieve()
                .body(Map.class);
        Object id = res == null ? null : res.get("id");
        if (id == null) {
            throw new IllegalStateException("Razorpay did not return an order id");
        }
        return id.toString();
    }

    /** Checkout signature = HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    public boolean isValidPayment(String orderId, String paymentId, String signature) {
        return matches(hmacSha256Hex(cfg.keySecret(), orderId + "|" + paymentId), signature);
    }

    /** Webhook signature = HMAC_SHA256(raw request body, webhook_secret). */
    public boolean isValidWebhook(String body, String signature) {
        return cfg.webhookEnabled() && matches(hmacSha256Hex(cfg.webhookSecret(), body), signature);
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    static boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
