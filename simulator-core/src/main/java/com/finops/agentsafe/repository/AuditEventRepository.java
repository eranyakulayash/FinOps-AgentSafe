package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByRunId(UUID runId);

    List<AuditEvent> findByScenarioId(String scenarioId);

    Optional<AuditEvent> findTopByOrderByTimestampDesc();
}
