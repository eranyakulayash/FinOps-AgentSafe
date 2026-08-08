package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HumanApprovalRequestRepository extends JpaRepository<HumanApprovalRequest, UUID> {

    List<HumanApprovalRequest> findByScenarioId(String scenarioId);

    List<HumanApprovalRequest> findByRelatedTransactionIdAndStatus(String relatedTransactionId, ApprovalStatus status);

    List<HumanApprovalRequest> findByRequestedByAndStatus(String requestedBy, ApprovalStatus status);

    List<HumanApprovalRequest> findByStatusAndExpiresAtBefore(ApprovalStatus status, Instant now);

    Optional<HumanApprovalRequest> findFirstByRelatedTransactionIdAndRequestedActionAndStatus(
        String relatedTransactionId, String requestedAction, ApprovalStatus status);
}
