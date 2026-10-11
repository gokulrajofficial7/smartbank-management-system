package com.smartbank.repository;

import com.smartbank.entity.FixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {

    List<FixedDeposit> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<FixedDeposit> findByDepositNumber(String depositNumber);
}
