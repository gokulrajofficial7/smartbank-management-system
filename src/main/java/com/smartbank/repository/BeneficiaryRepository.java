package com.smartbank.repository;

import com.smartbank.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByCustomerIdAndActiveTrue(Long customerId);

    Optional<Beneficiary> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Beneficiary> findByCustomerIdAndTargetAccountNumber(Long customerId, String targetAccountNumber);
}
