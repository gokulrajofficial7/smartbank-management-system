package com.smartbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "transaction_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 30)
    private AccountType accountType;

    @Builder.Default
    @Column(name = "single_transaction_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal singleTransactionLimit = new BigDecimal("50000.00");

    @Builder.Default
    @Column(name = "daily_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyLimit = new BigDecimal("200000.00");

    @Builder.Default
    @Column(name = "approval_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal approvalThreshold = new BigDecimal("25000.00");
}
