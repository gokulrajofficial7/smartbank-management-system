package com.smartbank.repository;

import com.smartbank.entity.KycDocument;
import com.smartbank.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByCustomerId(Long customerId);

    List<KycDocument> findByStatus(KycStatus status);
}
