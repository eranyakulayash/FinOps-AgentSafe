package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.SettlementLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementLineItemRepository extends JpaRepository<SettlementLineItem, UUID> {
    List<SettlementLineItem> findByBatchId(UUID batchId);
    Optional<SettlementLineItem> findByExternalTxId(String externalTxId);
}
