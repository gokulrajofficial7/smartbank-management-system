package com.smartbank.repository;

import com.smartbank.entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    List<LoanApplication> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<LoanApplication> findByStatusOrderByCreatedAtAsc(String status);
}
