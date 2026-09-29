package com.javaatlas.payment;

import java.time.Instant;

import com.javaatlas.course.Course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One order for one course. PAID means the learner owns the course. */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    public enum Status { CREATED, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "amount_paise", nullable = false)
    private int amountPaise;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Razorpay order id (order_...), or free-... for free courses. */
    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 60)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 60)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Enrollment() {
    }

    public Enrollment(Long userId, Course course, int amountPaise, String razorpayOrderId) {
        this.userId = userId;
        this.course = course;
        this.amountPaise = amountPaise;
        this.currency = "INR";
        this.razorpayOrderId = razorpayOrderId;
        this.status = Status.CREATED;
        this.createdAt = Instant.now();
    }

    /** Safe to call twice (browser confirmation and webhook can both arrive). */
    public void markPaid(String paymentId) {
        if (status != Status.PAID) {
            status = Status.PAID;
            razorpayPaymentId = paymentId;
            paidAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Course getCourse() { return course; }
    public int getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
