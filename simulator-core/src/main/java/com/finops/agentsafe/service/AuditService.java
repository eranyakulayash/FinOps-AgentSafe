package com.finops.agentsafe.service;

import com.finops.agentsafe.audit.AuditChainVerifier;
import com.finops.agentsafe.clock.SimulatorClock;
import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.identifier.IdentifierGenerator;
import com.finops.agentsafe.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final SimulatorClock clock;
    private final IdentifierGenerator identifierGenerator;

    public AuditService(AuditEventRepository auditEventRepository,
                        SimulatorClock clock,
                        IdentifierGenerator identifierGenerator) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
        this.identifierGenerator = identifierGenerator;
    }

    @Transactional
    public AuditEvent recordAuditEvent(
            UUID runId,
            String scenarioId,
            String actor,
            String requestedAction,
            String toolUsed,
            ActionRiskLevel riskLevel,
            String inputPayload,
            String authzDecision,
            String executionResult,
            String beforeStateRef,
            String afterStateRef,
            String injectedFailureType,
            String humanApprovalInfo,
            String reasoningSummary) {

        String inputPayloadHash = hashString(inputPayload != null ? inputPayload : "");

        Optional<AuditEvent> lastEvent = auditEventRepository.findTopByOrderByTimestampDesc();
        String prevHash = lastEvent.map(AuditEvent::getCurrentHash).orElse("GENESIS_HASH_00000000000000000000000000000000");

        Instant now = clock.now();
        String rawToHash = prevHash + "|" + runId + "|" + scenarioId + "|" + actor + "|" + toolUsed + "|" + executionResult + "|" + now.toEpochMilli();
        String currentHash = hashString(rawToHash);

        AuditEvent event = new AuditEvent(
            identifierGenerator.nextUUID(),
            runId,
            scenarioId,
            actor,
            requestedAction,
            toolUsed,
            riskLevel,
            inputPayloadHash,
            authzDecision,
            executionResult,
            beforeStateRef,
            afterStateRef,
            injectedFailureType,
            humanApprovalInfo,
            reasoningSummary,
            prevHash,
            currentHash
        );
        event.setTimestamp(now);

        return auditEventRepository.save(event);
    }

    public List<AuditEvent> getAuditTrailByRunId(UUID runId) {
        return auditEventRepository.findByRunId(runId);
    }

    public List<AuditEvent> getAuditTrailByScenarioId(String scenarioId) {
        return auditEventRepository.findByScenarioId(scenarioId);
    }

    /**
     * Verifies the tamper-evident audit chain for all events in a run.
     * Returns a verification result indicating any broken links.
     */
    public AuditChainVerifier.AuditChainVerificationResult verifyChainForRun(UUID runId) {
        List<AuditEvent> events = auditEventRepository.findByRunId(runId);
        return AuditChainVerifier.verifyChain(events, this::hashString);
    }

    public String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
