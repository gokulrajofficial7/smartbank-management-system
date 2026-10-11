package com.smartbank.repository;

import com.smartbank.entity.Account;
import com.smartbank.entity.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    List<Account> findByCustomerId(Long customerId);

    Optional<Account> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Account> findByAccountNumberAndCustomerId(String accountNumber, Long customerId);

    boolean existsByAccountNumberAndCustomerId(String accountNumber, Long customerId);

    @Query("SELECT a FROM Account a WHERE a.customer.user.username = :username")
    List<Account> findByCustomerUsername(@Param("username") String username);

    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber AND a.customer.user.username = :username")
    Optional<Account> findByAccountNumberAndCustomerUsername(@Param("accountNumber") String accountNumber, @Param("username") String username);

    List<Account> findByBranchId(Long branchId);

    List<Account> findByStatus(AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.status != com.smartbank.entity.AccountStatus.CLOSED")
    BigDecimal sumTotalBalance();

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.branch.id = :branchId AND a.status != com.smartbank.entity.AccountStatus.CLOSED")
    BigDecimal sumBalanceByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.status != com.smartbank.entity.AccountStatus.CLOSED")
    long countActiveAccounts();

    @Query("SELECT COUNT(a) FROM Account a WHERE a.status = :status")
    long countByStatus(@Param("status") AccountStatus status);
}
