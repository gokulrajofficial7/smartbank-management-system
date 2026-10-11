package com.smartbank.entity;

public enum Permission {
    // Customer permissions
    VIEW_OWN_ACCOUNT,
    VIEW_OWN_TRANSACTION,
    TRANSFER_OWN,
    MANAGE_BENEFICIARIES,
    APPLY_LOAN,
    OPEN_FIXED_DEPOSIT,
    MANAGE_CARDS,

    // Teller / Branch permissions
    DEPOSIT_CASH,
    WITHDRAW_CASH_TELLER,
    VIEW_BRANCH_CUSTOMERS,
    PROCESS_OVER_THE_COUNTER,

    // Customer Service Officer permissions
    MANAGE_CUSTOMER_PROFILE,
    OPEN_ACCOUNT,
    KYC_VERIFICATION,

    // Loan Officer permissions
    VIEW_LOAN_APPLICATIONS,
    CREATE_LOAN_APPLICATION,
    REVIEW_LOAN,

    // Operations Officer permissions
    EXECUTE_END_OF_DAY,
    RECONCILE_ACCOUNTS,

    // Branch Manager permissions
    APPROVE_TRANSACTIONS,
    APPROVE_LOAN,
    OVERRIDE_LIMITS,
    MANAGE_BRANCH_STAFF,

    // Compliance & Risk permissions
    VIEW_AUDIT_LOGS,
    EXPORT_AUDIT_LOGS,
    VIEW_SUSPICIOUS_TRANSACTIONS,
    FREEZE_ACCOUNT,
    UNFREEZE_ACCOUNT,
    EXPORT_STR,
    VIEW_RISK_METRICS,
    SET_TRANSACTION_LIMITS,

    // Auditor permissions
    VIEW_ALL_RECORDS,
    GENERATE_AUDIT_REPORTS,

    // Admin & Executive permissions
    MANAGE_USERS,
    MANAGE_ROLES,
    SYSTEM_CONFIGURATION,
    VIEW_EXECUTIVE_DASHBOARD,
    VIEW_FINANCIAL_REPORTS
}
