package com.smartbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", unique = true, length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    // Preserved Customer mappings for backward compatibility
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id")
    private Customer senderAccount;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id")
    private Customer receiverAccount;

    // Banking Account entity mappings
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_bank_account_id")
    private Account sourceBankAccount;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_bank_account_id")
    private Account targetBankAccount;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "balance_after_transaction", precision = 15, scale = 2)
    private BigDecimal balanceAfterTransaction;

    @Builder.Default
    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private TransactionChannel channel = TransactionChannel.ONLINE;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id")
    private User initiatedBy;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        if (referenceNumber == null) {
            referenceNumber = "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (fee == null) {
            fee = BigDecimal.ZERO;
        }
        if (channel == null) {
            channel = TransactionChannel.ONLINE;
        }
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_SENT,
        TRANSFER_RECEIVED,
        TRANSFER,
        LOAN_DISBURSEMENT,
        FEE,
        INTEREST
    }

    public enum TransactionStatus {
        SUCCESS,
        FAILED,
        PENDING,
        PENDING_APPROVAL,
        REJECTED,
        REVERSED
    }
}
