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

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.senderAccount = :customer OR t.receiverAccount = :customer ORDER BY t.transactionDate DESC")
    List<Transaction> findAllByCustomer(@Param("customer") Customer customer);

    @Query("SELECT t FROM Transaction t WHERE t.senderAccount.accountNumber = :accountNumber OR t.receiverAccount.accountNumber = :accountNumber ORDER BY t.transactionDate DESC")
    List<Transaction> findAllByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("SELECT t FROM Transaction t ORDER BY t.transactionDate DESC")
    List<Transaction> findAllOrderByDateDesc();

    @Query("SELECT COUNT(t) FROM Transaction t")
    long countTotalTransactions();

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.transactionType = 'DEPOSIT'")
    BigDecimal sumTotalDeposits();

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.transactionType = 'WITHDRAWAL'")
    BigDecimal sumTotalWithdrawals();

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :start AND :end ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Transaction> findTop10ByOrderByTransactionDateDesc();
}
