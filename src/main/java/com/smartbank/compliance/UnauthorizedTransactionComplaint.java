package com.smartbank.compliance;

import com.smartbank.entity.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity tracking complaints under RBI Master Directions on Customer Protection -
 * Limiting Liability of Customers in Unauthorized Electronic Banking Transactions
 * (DBR.No.Leg.BC.78/09.07.005/2017-18).
 */
@Entity
@Table(name = "unauthorized_transaction_complaints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnauthorizedTransactionComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_reference", unique = true, nullable = false, length = 60)
    private String complaintReference;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "transaction_reference", length = 60)
    private String transactionReference;

    @Column(name = "dispute_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal disputeAmount;

    @Column(name = "incident_date", nullable = false)
    private LocalDateTime incidentDate;

    @Column(name = "reported_date", nullable = false)
    private LocalDateTime reportedDate;

    @Column(name = "days_to_report", nullable = false)
    private int daysToReport;

    /**
     * Tiers:
     * - ZERO_LIABILITY: Notified <= 3 working days (Customer liability = 0)
     * - LIMITED_LIABILITY: Notified within 4 to 7 working days (Capped at ₹5,000 / ₹10,000 / ₹25,000)
     * - BOARD_POLICY: Notified beyond 7 working days (Per Bank Board Policy)
     */
    @Column(name = "liability_tier", nullable = false, length = 30)
    private String liabilityTier;

    @Column(name = "customer_liability_cap", nullable = false, precision = 15, scale = 2)
    private BigDecimal customerLiabilityCap;

    @Column(name = "bank_liability_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal bankLiabilityAmount;

    /**
     * RBI Mandate: Shadow reversal / credit to customer within 10 working days.
     */
    @Column(name = "shadow_reversal_deadline")
    private LocalDate shadowReversalDeadline;

    /**
     * RBI Mandate: Complete resolution within 90 calendar days.
     */
    @Column(name = "resolution_deadline")
    private LocalDate resolutionDeadline;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Column(name = "customer_description", length = 1000)
    private String customerDescription;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
