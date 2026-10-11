package com.smartbank.service;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Role;
import com.smartbank.entity.Transaction;
import com.smartbank.entity.User;
import com.smartbank.exception.BankingException;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = authorizationService.getCurrentCustomer().orElse(null);
            if (customer != null) {
                return transactionRepository.findAllByCustomer(customer);
            }
            return List.of();
        }
        return transactionRepository.findAllOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByCustomer(Customer customer) {
        if (customer != null) {
            authorizationService.verifyCustomerAccess(customer.getId());
            return transactionRepository.findAllByCustomer(customer);
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByAccountNumber(String accountNumber) {
        authorizationService.verifyAccountAccess(accountNumber);
        return transactionRepository.findAllByAccountNumber(accountNumber);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions() {
        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = authorizationService.getCurrentCustomer().orElse(null);
            if (customer != null) {
                return transactionRepository.findAllByCustomer(customer).stream().limit(10).toList();
            }
            return List.of();
        }
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
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new BankingException("Transaction not found with ID: " + id));

        User currentUser = authorizationService.getCurrentUser().orElse(null);
        if (currentUser != null && currentUser.getRole() == Role.CUSTOMER) {
            Customer customer = authorizationService.getCurrentCustomer().orElse(null);
            if (customer == null) {
                throw new AccessDeniedException("Forbidden: No customer record");
            }
            boolean isSender = txn.getSenderAccount() != null && txn.getSenderAccount().getId().equals(customer.getId());
            boolean isReceiver = txn.getReceiverAccount() != null && txn.getReceiverAccount().getId().equals(customer.getId());
            if (!isSender && !isReceiver) {
                log.warn("[SECURITY VIOLATION - IDOR PREVENTED] Customer {} attempted to view transaction #{}",
                        currentUser.getUsername(), id);
                throw new AccessDeniedException("Forbidden: You do not have permission to view this transaction.");
            }
        }
        return txn;
    }
}
