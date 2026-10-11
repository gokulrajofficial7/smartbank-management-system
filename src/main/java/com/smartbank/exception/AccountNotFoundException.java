package com.smartbank.exception;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountNumber) {
        super("Account not found with account number: " + accountNumber);
    }

    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
