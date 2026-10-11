package com.smartbank.repository;

import com.smartbank.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByAccountNumber(String accountNumber);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByUserId(Long userId);

    @Query("SELECT c FROM Customer c WHERE c.user.username = :username")
    Optional<Customer> findByUserUsername(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);

    boolean existsByEmail(String email);

    boolean existsByAccountNumber(String accountNumber);

    List<Customer> findByNameContainingIgnoreCase(String name);

    @Query("SELECT c FROM Customer c WHERE c.branch.id = :branchId")
    List<Customer> findByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :query OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Customer> searchCustomers(@Param("query") String query);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.branch WHERE c.id = :id")
    Optional<Customer> findByIdWithBranch(@Param("id") Long id);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.branch.id = :branchId AND (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED)")
    long countActiveCustomersByBranchId(@Param("branchId") Long branchId);

    @Query(value = "SELECT c FROM Customer c LEFT JOIN FETCH c.branch WHERE c.branch.id = :branchId AND (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) ORDER BY c.id ASC",
           countQuery = "SELECT COUNT(c) FROM Customer c WHERE c.branch.id = :branchId AND (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED)")
    Page<Customer> findActiveCustomersByBranchId(@Param("branchId") Long branchId, Pageable pageable);

    @Query(value = "SELECT c FROM Customer c LEFT JOIN FETCH c.branch WHERE c.branch.id = :branchId AND (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.accountNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))",
           countQuery = "SELECT COUNT(c) FROM Customer c WHERE c.branch.id = :branchId AND (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.accountNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Customer> searchActiveCustomersByBranchId(@Param("branchId") Long branchId, @Param("query") String query, Pageable pageable);

    @Query(value = "SELECT c FROM Customer c LEFT JOIN FETCH c.branch WHERE (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) ORDER BY c.id ASC",
           countQuery = "SELECT COUNT(c) FROM Customer c WHERE (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED)")
    Page<Customer> findAllActiveCustomers(Pageable pageable);

    @Query(value = "SELECT c FROM Customer c LEFT JOIN FETCH c.branch WHERE (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.accountNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))",
           countQuery = "SELECT COUNT(c) FROM Customer c WHERE (c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED) AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.accountNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Customer> searchAllActiveCustomers(@Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED")
    long countTotalCustomers();

    @Query("SELECT COALESCE(SUM(c.balance), 0) FROM Customer c WHERE c.status IS NULL OR c.status != com.smartbank.entity.CustomerStatus.DELETED")
    BigDecimal sumTotalBalance();

    @Query("SELECT c FROM Customer c ORDER BY c.balance DESC")
    List<Customer> findAllOrderByBalanceDesc();
}
