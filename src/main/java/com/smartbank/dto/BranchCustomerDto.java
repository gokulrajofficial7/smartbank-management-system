package com.smartbank.dto;

import com.smartbank.entity.AccountType;
import com.smartbank.entity.CustomerStatus;
import com.smartbank.entity.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Branch Manager Customer Directory views.
 * Exposes only authorized customer oversight fields and masks sensitive identifiers.
 * Strictly hides passwords, credentials, OTPs, and unnecessary personal data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchCustomerDto {

    private Long id;
    private String name;
    private String rawAccountNumber;
    private String maskedAccountNumber;
    private String customerNumber;
    private AccountType accountType;
    private CustomerStatus status;
    private KycStatus kycStatus;
    private String branchName;
    private String branchCode;
    private LocalDateTime createdAt;

    /**
     * Masks account numbers to protect sensitive customer financial identifiers.
     * Preserves only the standard prefix and the last four digits (e.g. SB••••••6893).
     */
    public static String maskAccountNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "••••";
        }
        String clean = raw.trim();
        if (clean.length() <= 4) {
            return "••••" + clean;
        }
        String last4 = clean.substring(clean.length() - 4);
        if (clean.startsWith("SB")) {
            return "SB••••••" + last4;
        }
        return "•••• •••• " + last4;
    }
}
