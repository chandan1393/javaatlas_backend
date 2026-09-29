package com.javaatlas.payment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByRazorpayOrderId(String razorpayOrderId);

    boolean existsByUserIdAndCourse_IdAndStatus(Long userId, Long courseId, Enrollment.Status status);

    boolean existsByCourse_Id(Long courseId);

    long countByCourse_IdAndStatus(Long courseId, Enrollment.Status status);

    long countByStatus(Enrollment.Status status);

    List<Enrollment> findByUserIdAndStatusOrderByPaidAtDesc(Long userId, Enrollment.Status status);

    List<Enrollment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Enrollment> findTop200ByOrderByCreatedAtDesc();

    @Query("select sum(e.amountPaise) from Enrollment e where e.status = :status")
    Long sumAmountPaiseByStatus(@Param("status") Enrollment.Status status);
}
