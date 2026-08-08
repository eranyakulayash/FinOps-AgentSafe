package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {
    Optional<ReconciliationRecord> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
}
