package com.smartbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(nullable = false, length = 100)
    private String purpose;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "APPLIED"; // APPLIED, UNDER_REVIEW, APPROVED, REJECTED, DISBURSED

    @Builder.Default
    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate = new BigDecimal("8.50");

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(length = 500)
    private String remarks;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
