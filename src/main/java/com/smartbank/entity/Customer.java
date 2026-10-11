package com.smartbank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_number", length = 30, unique = true)
    private String customerNumber;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be 10-15 digits")
    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Builder.Default
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", length = 30)
    private KycStatus kycStatus = KycStatus.VERIFIED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Builder.Default
    @Column(name = "form60_submitted")
    private Boolean form60Submitted = false;

    @Builder.Default
    @Column(name = "itr_filer")
    private Boolean itrFiler = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Banking rule: NO CASCADE DELETE of financial transactions!
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "senderAccount", fetch = FetchType.LAZY)
    private List<Transaction> sentTransactions;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "receiverAccount", fetch = FetchType.LAZY)
    private List<Transaction> receivedTransactions;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (customerNumber == null && accountNumber != null) {
            customerNumber = "CUST-" + accountNumber;
        }
        if (kycStatus == null) {
            kycStatus = KycStatus.VERIFIED;
        }
        if (status == null) {
            status = CustomerStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
