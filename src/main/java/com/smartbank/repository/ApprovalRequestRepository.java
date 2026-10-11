package com.smartbank.repository;

import com.smartbank.entity.ApprovalRequest;
import com.smartbank.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByStatusOrderByRequestedAtAsc(ApprovalStatus status);

    @Query("SELECT r FROM ApprovalRequest r WHERE r.status = 'PENDING' AND r.maker.id <> :checkerUserId ORDER BY r.requestedAt ASC")
    List<ApprovalRequest> findPendingForChecker(@Param("checkerUserId") Long checkerUserId);

    Optional<ApprovalRequest> findByTransactionId(Long transactionId);

    List<ApprovalRequest> findBySourceAccountNumber(String sourceAccountNumber);
}
