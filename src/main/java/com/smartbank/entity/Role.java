package com.smartbank.entity;

import java.util.Set;

public enum Role {
    CUSTOMER("Customer", Set.of(
            Permission.VIEW_OWN_ACCOUNT,
            Permission.VIEW_OWN_TRANSACTION,
            Permission.TRANSFER_OWN,
            Permission.MANAGE_BENEFICIARIES,
            Permission.APPLY_LOAN,
            Permission.OPEN_FIXED_DEPOSIT,
            Permission.MANAGE_CARDS
    )),

    TELLER("Teller", Set.of(
            Permission.DEPOSIT_CASH,
            Permission.WITHDRAW_CASH_TELLER,
            Permission.VIEW_BRANCH_CUSTOMERS,
            Permission.PROCESS_OVER_THE_COUNTER
    )),

    CUSTOMER_SERVICE_OFFICER("Customer Service Officer", Set.of(
            Permission.MANAGE_CUSTOMER_PROFILE,
            Permission.OPEN_ACCOUNT,
            Permission.KYC_VERIFICATION,
            Permission.VIEW_BRANCH_CUSTOMERS
    )),

    LOAN_OFFICER("Loan Officer", Set.of(
            Permission.VIEW_LOAN_APPLICATIONS,
            Permission.CREATE_LOAN_APPLICATION,
            Permission.REVIEW_LOAN,
            Permission.VIEW_BRANCH_CUSTOMERS
    )),

    OPERATIONS_OFFICER("Operations Officer", Set.of(
            Permission.EXECUTE_END_OF_DAY,
            Permission.RECONCILE_ACCOUNTS
    )),

    BRANCH_MANAGER("Branch Manager", Set.of(
            Permission.APPROVE_TRANSACTIONS,
            Permission.APPROVE_LOAN,
            Permission.OVERRIDE_LIMITS,
            Permission.MANAGE_BRANCH_STAFF,
            Permission.VIEW_BRANCH_CUSTOMERS
    )),

    REGIONAL_MANAGER("Regional Manager", Set.of(
            Permission.APPROVE_TRANSACTIONS,
            Permission.OVERRIDE_LIMITS,
            Permission.VIEW_BRANCH_CUSTOMERS,
            Permission.VIEW_EXECUTIVE_DASHBOARD
    )),

    COMPLIANCE_OFFICER("Compliance Officer", Set.of(
            Permission.VIEW_AUDIT_LOGS,
            Permission.EXPORT_AUDIT_LOGS,
            Permission.VIEW_SUSPICIOUS_TRANSACTIONS,
            Permission.FREEZE_ACCOUNT,
            Permission.UNFREEZE_ACCOUNT,
            Permission.EXPORT_STR
    )),

    RISK_OFFICER("Risk Officer", Set.of(
            Permission.VIEW_RISK_METRICS,
            Permission.SET_TRANSACTION_LIMITS,
            Permission.VIEW_SUSPICIOUS_TRANSACTIONS
    )),

    AUDITOR("Auditor", Set.of(
            Permission.VIEW_ALL_RECORDS,
            Permission.VIEW_AUDIT_LOGS,
            Permission.EXPORT_AUDIT_LOGS,
            Permission.GENERATE_AUDIT_REPORTS
    )),

    BANK_ADMIN("Bank Administrator", Set.of(
            Permission.MANAGE_USERS,
            Permission.MANAGE_ROLES,
            Permission.SYSTEM_CONFIGURATION,
            Permission.SET_TRANSACTION_LIMITS
    )),

    SUPER_ADMIN("Super Administrator", Set.of(
            Permission.MANAGE_USERS,
            Permission.MANAGE_ROLES,
            Permission.SYSTEM_CONFIGURATION,
            Permission.APPROVE_TRANSACTIONS,
            Permission.VIEW_ALL_RECORDS
    )),

    EXECUTIVE("Executive / Board", Set.of(
            Permission.VIEW_EXECUTIVE_DASHBOARD,
            Permission.VIEW_FINANCIAL_REPORTS
    ));

    private final String displayName;
    private final Set<Permission> permissions;

    Role(String displayName, Set<Permission> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
