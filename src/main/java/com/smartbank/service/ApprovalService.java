package com.smartbank.service;

import com.smartbank.entity.*;
import com.smartbank.exception.BankingException;
import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.repository.AccountRepository;
import com.smartbank.repository.ApprovalRequestRepository;
import com.smartbank.repository.CustomerRepository;
import com.smartbank.repository.TransactionRepository;
import com.smartbank.util.CurrencyUtil;
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
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ApprovalRequest submitForApproval(Transaction transaction, User maker, ApprovalType type,
                                             BigDecimal amount, String sourceAcc, String targetAcc, String notes) {
        Transaction persistentTxn = transaction;
        if (transaction != null) {
            persistentTxn = transactionRepository.save(transaction);
        }

        ApprovalRequest request = ApprovalRequest.builder()
                .transaction(persistentTxn)
                .maker(maker)
                .requestType(type)
                .status(ApprovalStatus.PENDING)
                .amount(amount)
                .sourceAccountNumber(sourceAcc)
                .targetAccountNumber(targetAcc)
                .makerNotes(notes)
                .requestedAt(LocalDateTime.now())
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);
        log.info("[MAKER-CHECKER] Created pending approval request #{} by maker {}", saved.getId(), maker.getUsername());

        auditService.logEvent(
                maker.getId(),
                maker.getUsername(),
                maker.getRole().name(),
                "APPROVAL_SUBMITTED",
                "APPROVAL_REQUEST",
                saved.getId().toString(),
                "Submitted " + type + " for " + CurrencyUtil.format(amount) + ". Source: " + sourceAcc + ", Target: " + targetAcc,
                "SYSTEM",
                "PENDING"
        );

        return saved;
    }

    @Transactional
    public ApprovalRequest approveRequest(Long requestId, User checker, String checkerNotes) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new BankingException("Approval request not found with ID: " + requestId));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BankingException("Request #" + requestId + " is already " + request.getStatus());
        }

        // DUAL-CONTROL: Maker cannot approve their own request!
        if (request.getMaker().getId().equals(checker.getId())) {
            log.error("[SECURITY VIOLATION] User {} attempted to approve their own request #{}",
                    checker.getUsername(), requestId);
            throw new BankingException("Dual-Control Violation: Maker cannot act as Checker for their own request.");
        }

        request.setChecker(checker);
        request.setCheckerNotes(checkerNotes);
        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        approvalRequestRepository.save(request);

        // Execute the pending transaction
        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            executeApprovedTransaction(transaction, checker);
        }

        auditService.logEvent(
                checker.getId(),
                checker.getUsername(),
                checker.getRole().name(),
                "APPROVAL_GRANTED",
                "APPROVAL_REQUEST",
                requestId.toString(),
                "Approved by " + checker.getUsername() + ". Notes: " + checkerNotes,
                "SYSTEM",
                "SUCCESS"
        );

        log.info("[MAKER-CHECKER] Request #{} APPROVED by checker {}", requestId, checker.getUsername());
        return request;
    }

    @Transactional
    public ApprovalRequest rejectRequest(Long requestId, User checker, String rejectionReason) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new BankingException("Approval request not found with ID: " + requestId));

        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BankingException("Request #" + requestId + " is already " + request.getStatus());
        }

        // Maker cannot reject/approve their own request
        if (request.getMaker().getId().equals(checker.getId())) {
            throw new BankingException("Dual-Control Violation: Maker cannot process their own request.");
        }

        request.setChecker(checker);
        request.setCheckerNotes(rejectionReason);
        request.setStatus(ApprovalStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now());
        approvalRequestRepository.save(request);

        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            transaction.setStatus(Transaction.TransactionStatus.REJECTED);
            transaction.setApprovedBy(checker);
            transaction.setRejectionReason(rejectionReason);
            transactionRepository.save(transaction);
        }

        auditService.logEvent(
                checker.getId(),
                checker.getUsername(),
                checker.getRole().name(),
                "APPROVAL_REJECTED",
                "APPROVAL_REQUEST",
                requestId.toString(),
                "Rejected by " + checker.getUsername() + ". Reason: " + rejectionReason,
                "SYSTEM",
                "REJECTED"
        );

        log.info("[MAKER-CHECKER] Request #{} REJECTED by checker {}", requestId, checker.getUsername());
        return request;
    }

    private void executeApprovedTransaction(Transaction transaction, User checker) {
        BigDecimal amount = transaction.getAmount();

        if (transaction.getTransactionType() == Transaction.TransactionType.TRANSFER_SENT ||
            transaction.getTransactionType() == Transaction.TransactionType.TRANSFER) {

            String senderAccNum = transaction.getSourceBankAccount() != null 
                    ? transaction.getSourceBankAccount().getAccountNumber() 
                    : (transaction.getSenderAccount() != null ? transaction.getSenderAccount().getAccountNumber() : null);
            String receiverAccNum = transaction.getTargetBankAccount() != null 
                    ? transaction.getTargetBankAccount().getAccountNumber() 
                    : (transaction.getReceiverAccount() != null ? transaction.getReceiverAccount().getAccountNumber() : null);

            if (senderAccNum == null || receiverAccNum == null) {
                throw new BankingException("Sender or receiver account number is missing");
            }

            Account senderAccount = accountRepository.findByAccountNumberWithLock(senderAccNum).orElse(null);
            Customer sender = customerRepository.findByAccountNumberWithLock(senderAccNum)
                    .orElse(senderAccount != null ? senderAccount.getCustomer() : null);

            Account receiverAccount = accountRepository.findByAccountNumberWithLock(receiverAccNum).orElse(null);
            Customer receiver = customerRepository.findByAccountNumberWithLock(receiverAccNum)
                    .orElse(receiverAccount != null ? receiverAccount.getCustomer() : null);

            if (sender == null && senderAccount == null) {
                throw new BankingException("Sender account not found: " + senderAccNum);
            }
            if (receiver == null && receiverAccount == null) {
                throw new BankingException("Receiver account not found: " + receiverAccNum);
            }

            BigDecimal senderBal = senderAccount != null ? senderAccount.getBalance() : sender.getBalance();
            if (senderBal.compareTo(amount) < 0) {
                throw new InsufficientFundsException(senderBal, amount);
            }

            if (senderAccount != null) {
                senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
                senderAccount.setLedgerBalance(senderAccount.getBalance());
                senderAccount = accountRepository.save(senderAccount);
            }
            if (receiverAccount != null) {
                receiverAccount.setBalance(receiverAccount.getBalance().add(amount));
                receiverAccount.setLedgerBalance(receiverAccount.getBalance());
                receiverAccount = accountRepository.save(receiverAccount);
            }

            if (sender != null) {
                sender.setBalance(senderAccount != null ? senderAccount.getBalance() : sender.getBalance().subtract(amount));
                sender = customerRepository.save(sender);
            }
            if (receiver != null) {
                receiver.setBalance(receiverAccount != null ? receiverAccount.getBalance() : receiver.getBalance().add(amount));
                receiver = customerRepository.save(receiver);
            }

            BigDecimal senderBalAfter = senderAccount != null ? senderAccount.getBalance() : sender.getBalance();
            BigDecimal receiverBalAfter = receiverAccount != null ? receiverAccount.getBalance() : (receiver != null ? receiver.getBalance() : BigDecimal.ZERO);

            transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
            transaction.setApprovedBy(checker);
            transaction.setBalanceAfterTransaction(senderBalAfter);
            transactionRepository.save(transaction);

            // Double-entry ledger
            ledgerService.recordTransferLedger(transaction, sender, receiver, senderAccount, receiverAccount, amount, senderBalAfter, receiverBalAfter);

        } else if (transaction.getTransactionType() == Transaction.TransactionType.WITHDRAWAL) {
            String accNum = transaction.getSourceBankAccount() != null 
                    ? transaction.getSourceBankAccount().getAccountNumber() 
                    : (transaction.getSenderAccount() != null ? transaction.getSenderAccount().getAccountNumber() : null);

            if (accNum == null) {
                throw new BankingException("Account number missing for withdrawal");
            }

            Account customerAccount = accountRepository.findByAccountNumberWithLock(accNum).orElse(null);
            Customer customer = customerRepository.findByAccountNumberWithLock(accNum)
                    .orElse(customerAccount != null ? customerAccount.getCustomer() : null);

            if (customer == null && customerAccount == null) {
                throw new BankingException("Account not found: " + accNum);
            }

            BigDecimal currentBal = customerAccount != null ? customerAccount.getBalance() : customer.getBalance();
            if (currentBal.compareTo(amount) < 0) {
                throw new InsufficientFundsException(currentBal, amount);
            }

            if (customerAccount != null) {
                customerAccount.setBalance(customerAccount.getBalance().subtract(amount));
                customerAccount.setLedgerBalance(customerAccount.getBalance());
                customerAccount = accountRepository.save(customerAccount);
            }

            if (customer != null) {
                customer.setBalance(customerAccount != null ? customerAccount.getBalance() : customer.getBalance().subtract(amount));
                customer = customerRepository.save(customer);
            }

            BigDecimal balAfter = customerAccount != null ? customerAccount.getBalance() : customer.getBalance();

            transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
            transaction.setApprovedBy(checker);
            transaction.setBalanceAfterTransaction(balAfter);
            transactionRepository.save(transaction);

            ledgerService.recordWithdrawalLedger(transaction, customer, customerAccount, amount, balAfter);
        }
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingRequestsForChecker(Long checkerUserId) {
        return approvalRequestRepository.findPendingForChecker(checkerUserId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> getAllPendingRequests() {
        return approvalRequestRepository.findByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING);
    }
}
