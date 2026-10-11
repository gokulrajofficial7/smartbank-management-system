package com.smartbank.compliance;

/**
 * Categorization of banking rules enforced by SmartBank.
 */
public enum ComplianceCategory {
    /**
     * Statutory rules directly mandated by the Reserve Bank of India (RBI)
     * and statutory authorities (e.g. Income Tax Department, NPCI).
     */
    RBI_STATUTORY,

    /**
     * Bank-specific internal operational risk and administrative policies.
     */
    BANK_OPERATIONAL,

    /**
     * SmartBank simulated demonstration limits for internship/sandbox evaluation.
     */
    SMARTBANK_DEMO,

    /**
     * Automated velocity and anti-fraud risk control triggers.
     */
    FRAUD_RISK_CONTROL
}
