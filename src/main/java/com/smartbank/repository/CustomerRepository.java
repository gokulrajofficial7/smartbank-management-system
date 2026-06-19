package com.smartbank.repository;

import com.smartbank.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByEmail(String email);

    boolean existsByAccountNumber(String accountNumber);

    List<Customer> findByNameContainingIgnoreCase(String name);

    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :query OR LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Customer> searchCustomers(@Param("query") String query);

    @Query("SELECT COUNT(c) FROM Customer c")
    long countTotalCustomers();

    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal sumTotalBalance();

    @Query("SELECT c FROM Customer c ORDER BY c.balance DESC")
    List<Customer> findAllOrderByBalanceDesc();
}
