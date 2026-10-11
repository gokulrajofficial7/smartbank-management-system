package com.smartbank.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnauthorizedTransactionComplaintRepository extends JpaRepository<UnauthorizedTransactionComplaint, Long> {

    List<UnauthorizedTransactionComplaint> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<UnauthorizedTransactionComplaint> findByComplaintReference(String complaintReference);

    List<UnauthorizedTransactionComplaint> findAllByOrderByCreatedAtDesc();
}
