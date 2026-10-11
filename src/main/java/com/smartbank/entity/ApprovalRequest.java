package com.smartbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maker_user_id", nullable = false)
    private User maker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checker_user_id")
    private User checker;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 40)
    private ApprovalType requestType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "source_account_number", length = 30)
    private String sourceAccountNumber;

    @Column(name = "target_account_number", length = 30)
    private String targetAccountNumber;

    @Column(name = "maker_notes", length = 500)
    private String makerNotes;

    @Column(name = "checker_notes", length = 500)
    private String checkerNotes;

    @Builder.Default
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
