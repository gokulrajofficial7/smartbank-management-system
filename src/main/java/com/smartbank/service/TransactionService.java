package com.smartbank.service;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import com.smartbank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByCustomer(Customer customer) {
        return transactionRepository.findAllByCustomer(customer);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByAccountNumber(String accountNumber) {
        return transactionRepository.findAllByAccountNumber(accountNumber);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions() {
        return transactionRepository.findTop10ByOrderByTransactionDateDesc();
    }

    @Transactional(readOnly = true)
    public long countTotalTransactions() {
        return transactionRepository.countTotalTransactions();
    }

    @Transactional(readOnly = true)
    public BigDecimal sumTotalDeposits() {
        BigDecimal total = transactionRepository.sumTotalDeposits();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public BigDecimal sumTotalWithdrawals() {
        BigDecimal total = transactionRepository.sumTotalWithdrawals();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with ID: " + id));
    }
}
