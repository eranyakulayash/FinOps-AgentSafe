package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {
    List<SettlementBatch> findByMerchantId(UUID merchantId);
}
