package com.smartbank.repository;

import com.smartbank.entity.Customer;
import com.smartbank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    @Query("SELECT t FROM Transaction t WHERE (t.senderAccount = :customer AND t.transactionType != 'TRANSFER_RECEIVED') OR (t.receiverAccount = :customer AND t.transactionType != 'TRANSFER_SENT') ORDER BY t.transactionDate DESC")
    List<Transaction> findAllByCustomer(@Param("customer") Customer customer);

    @Query("SELECT t FROM Transaction t WHERE (t.senderAccount.accountNumber = :accountNumber AND t.transactionType != 'TRANSFER_RECEIVED') OR (t.receiverAccount.accountNumber = :accountNumber AND t.transactionType != 'TRANSFER_SENT') ORDER BY t.transactionDate DESC")
    List<Transaction> findAllByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("SELECT t FROM Transaction t WHERE (t.senderAccount.id = :customerId AND t.transactionType != 'TRANSFER_RECEIVED') OR (t.receiverAccount.id = :customerId AND t.transactionType != 'TRANSFER_SENT') ORDER BY t.transactionDate DESC")
    List<Transaction> findAllByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT t FROM Transaction t ORDER BY t.transactionDate DESC")
    List<Transaction> findAllOrderByDateDesc();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = 'SUCCESS'")
    long countTotalTransactions();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = 'DEPOSIT' AND t.status = 'SUCCESS'")
    BigDecimal sumTotalDeposits();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = 'WITHDRAWAL' AND t.status = 'SUCCESS'")
    BigDecimal sumTotalWithdrawals();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.transactionType = :type AND t.status = :status")
    BigDecimal sumAmountByTypeAndStatus(@Param("type") Transaction.TransactionType type, @Param("status") Transaction.TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :start AND :end ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Transaction> findTop10ByOrderByTransactionDateDesc();

    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING_APPROVAL' ORDER BY t.transactionDate ASC")
    List<Transaction> findPendingApprovals();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.receiverAccount.id = :customerId AND t.transactionType = 'DEPOSIT' AND t.status = 'SUCCESS' AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal sumDepositsForCustomerInPeriod(@Param("customerId") Long customerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.senderAccount.id = :customerId AND t.transactionType = 'WITHDRAWAL' AND t.status = 'SUCCESS' AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal sumWithdrawalsForCustomerInPeriod(@Param("customerId") Long customerId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE (t.senderAccount.accountNumber = :senderAcc OR t.sourceBankAccount.accountNumber = :senderAcc) AND (t.receiverAccount.accountNumber = :receiverAcc OR t.targetBankAccount.accountNumber = :receiverAcc) AND t.amount = :amount AND t.transactionType = 'TRANSFER_SENT' AND t.status = 'SUCCESS' AND t.transactionDate >= :cutoff")
    boolean existsRecentDuplicateTransfer(@Param("senderAcc") String senderAcc, @Param("receiverAcc") String receiverAcc, @Param("amount") BigDecimal amount, @Param("cutoff") LocalDateTime cutoff);
}
