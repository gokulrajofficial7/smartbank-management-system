package com.smartbank.repository;

import com.smartbank.entity.AccountType;
import com.smartbank.entity.Role;
import com.smartbank.entity.TransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long> {

    Optional<TransactionLimit> findByRoleAndAccountType(Role role, AccountType accountType);

    Optional<TransactionLimit> findByRole(Role role);
}
