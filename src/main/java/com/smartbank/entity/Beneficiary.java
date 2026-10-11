package com.smartbank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "target_account_number", nullable = false, length = 30)
    private String targetAccountNumber;

    @Column(name = "target_account_name", nullable = false, length = 100)
    private String targetAccountName;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(length = 50)
    private String nickname;

    @Column(name = "cooling_off_until")
    private LocalDateTime coolingOffUntil;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isCoolingOffActive() {
        return coolingOffUntil != null && coolingOffUntil.isAfter(LocalDateTime.now());
    }
}
