package com.smartbank.service;

import com.smartbank.entity.*;
import com.smartbank.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Records double-entry for a Cash Deposit:
     * DEBIT: Bank Cash Vault
     * CREDIT: Customer Account
     */
    @Transactional
    public void recordDepositLedger(Transaction transaction, Customer customer, BigDecimal amount, BigDecimal newBalance) {
        recordDepositLedger(transaction, customer, null, amount, newBalance);
    }

    @Transactional
    public void recordDepositLedger(Transaction transaction, Customer customer, Account account, BigDecimal amount, BigDecimal newBalance) {
        String accNum = (account != null) ? account.getAccountNumber() : (customer != null ? customer.getAccountNumber() : "UNKNOWN");
        // Credit entry for customer account
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(customer)
                .account(account)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Deposit credit to account " + accNum)
                .entryDate(LocalDateTime.now())
                .build();

        // Paired Debit entry representing bank cash intake
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(null)
                .account(null)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(null)
                .description("Cash in Vault intake for deposit to " + accNum)
                .entryDate(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(creditEntry);
        ledgerEntryRepository.save(debitEntry);
        log.info("[LEDGER] Recorded balanced deposit entries for transaction {}", transaction.getId());
    }

    /**
     * Records double-entry for a Cash Withdrawal:
     * DEBIT: Customer Account
     * CREDIT: Bank Cash Vault
     */
    @Transactional
    public void recordWithdrawalLedger(Transaction transaction, Customer customer, BigDecimal amount, BigDecimal newBalance) {
        recordWithdrawalLedger(transaction, customer, null, amount, newBalance);
    }

    @Transactional
    public void recordWithdrawalLedger(Transaction transaction, Customer customer, Account account, BigDecimal amount, BigDecimal newBalance) {
        String accNum = (account != null) ? account.getAccountNumber() : (customer != null ? customer.getAccountNumber() : "UNKNOWN");
        // Debit entry for customer account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(customer)
                .account(account)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Withdrawal debit from account " + accNum)
                .entryDate(LocalDateTime.now())
                .build();

        // Paired Credit entry representing cash disbursement
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(null)
                .account(null)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(null)
                .description("Cash in Vault disbursement for withdrawal from " + accNum)
                .entryDate(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        log.info("[LEDGER] Recorded balanced withdrawal entries for transaction {}", transaction.getId());
    }

    /**
     * Records double-entry for Fund Transfer:
     * DEBIT: Sender Account
     * CREDIT: Receiver Account
     */
    @Transactional
    public void recordTransferLedger(Transaction transaction, Customer sender, Customer receiver,
                                    BigDecimal amount, BigDecimal senderNewBalance, BigDecimal receiverNewBalance) {
        recordTransferLedger(transaction, sender, receiver, null, null, amount, senderNewBalance, receiverNewBalance);
    }

    @Transactional
    public void recordTransferLedger(Transaction transaction, Customer sender, Customer receiver,
                                    Account senderAccount, Account receiverAccount,
                                    BigDecimal amount, BigDecimal senderNewBalance, BigDecimal receiverNewBalance) {
        String senderAccNum = (senderAccount != null) ? senderAccount.getAccountNumber() : (sender != null ? sender.getAccountNumber() : "UNKNOWN");
        String receiverAccNum = (receiverAccount != null) ? receiverAccount.getAccountNumber() : (receiver != null ? receiver.getAccountNumber() : "UNKNOWN");

        // Debit entry for sender
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(sender)
                .account(senderAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceAfter(senderNewBalance)
                .description("Transfer debit from " + senderAccNum)
                .entryDate(LocalDateTime.now())
                .build();

        // Credit entry for receiver
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .customer(receiver)
                .account(receiverAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceAfter(receiverNewBalance)
                .description("Transfer credit to " + receiverAccNum)
                .entryDate(LocalDateTime.now())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);
        log.info("[LEDGER] Recorded balanced transfer entries for transaction {}", transaction.getId());
    }

    @Transactional(readOnly = true)
    public boolean verifyLedgerIntegrity() {
        List<Object[]> unbalanced = ledgerEntryRepository.findUnbalancedTransactions();
        if (!unbalanced.isEmpty()) {
            log.error("[LEDGER ALERT] Unbalanced transactions found: count={}", unbalanced.size());
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForAccount(Long accountId) {
        return ledgerEntryRepository.findByAccountIdOrderByEntryDateDesc(accountId);
    }
}
