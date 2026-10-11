package com.smartbank.entity;

public enum AccountType {
    SAVINGS("Savings Account"),
    CHECKING("Checking Account"),
    BUSINESS("Business Account"),
    FIXED_DEPOSIT("Fixed Deposit Account"),
    LOAN("Loan Account");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
