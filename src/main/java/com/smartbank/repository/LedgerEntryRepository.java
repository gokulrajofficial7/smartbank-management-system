package com.smartbank.repository;

import com.smartbank.entity.LedgerEntry;
import com.smartbank.entity.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(Long transactionId);

    List<LedgerEntry> findByAccountIdOrderByEntryDateDesc(Long accountId);

    @Query("SELECT t.id, " +
           "COALESCE(SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN l.entryType = 'CREDIT' THEN l.amount ELSE 0 END), 0) " +
           "FROM LedgerEntry l JOIN l.transaction t " +
           "GROUP BY t.id " +
           "HAVING COALESCE(SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE 0 END), 0) <> " +
           "COALESCE(SUM(CASE WHEN l.entryType = 'CREDIT' THEN l.amount ELSE 0 END), 0)")
    List<Object[]> findUnbalancedTransactions();

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.entryType = :entryType")
    BigDecimal sumAmountByEntryType(@Param("entryType") LedgerEntryType entryType);
}
