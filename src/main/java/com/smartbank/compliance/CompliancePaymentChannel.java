package com.smartbank.compliance;

/**
 * Supported payment channels within SmartBank.
 * Note: External clearing systems (UPI, IMPS, NEFT, RTGS) are simulated
 * with genuine Indian regulatory thresholds and validation rules.
 */
public enum CompliancePaymentChannel {
    UPI("UPI", "Unified Payments Interface (Simulated NPCI Network)"),
    IMPS("IMPS", "Immediate Payment Service (Simulated NPCI 24x7)"),
    NEFT("NEFT", "National Electronic Funds Transfer (Simulated RBI Batch Clearing)"),
    RTGS("RTGS", "Real Time Gross Settlement (Simulated RBI High-Value System)"),
    INTERNAL("Internal Transfer", "SmartBank Book Transfer (Intra-Bank)"),
    BRANCH_CASH("Branch Cash", "Branch Counter Cashier Desk"),
    ATM("ATM", "Automated Teller Machine");

    private final String displayName;
    private final String description;

    CompliancePaymentChannel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
