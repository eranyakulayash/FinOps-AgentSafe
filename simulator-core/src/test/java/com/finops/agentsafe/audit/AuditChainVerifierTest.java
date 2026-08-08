package com.finops.agentsafe.audit;

import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.enums.ActionRiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuditChainVerifierTest {

    private static final String GENESIS_HASH = AuditChainVerifier.GENESIS_HASH;

    private final Function<String, String> hasher = input -> {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };

    private AuditEvent buildEvent(UUID id, UUID runId, String scenarioId, String actor, String toolUsed,
                                   String executionResult, String prevHash, Instant timestamp) {
        String rawToHash = prevHash + "|" + runId + "|" + scenarioId + "|" + actor + "|" + toolUsed + "|" + executionResult + "|" + timestamp.toEpochMilli();
        String currentHash = hasher.apply(rawToHash);
        AuditEvent event = new AuditEvent(id, runId, scenarioId, actor, "ACTION", toolUsed,
            ActionRiskLevel.LOW_RISK_WRITE, null, "ALLOWED", executionResult, null, null, null, null, null, prevHash, currentHash);
        event.setTimestamp(timestamp);
        return event;
    }

    @Test
    @DisplayName("Empty event list returns valid result with 0 events checked")
    void testEmptyChain() {
        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(), hasher);
        assertTrue(result.isValid());
        assertEquals(0, result.getEventsChecked());
        assertTrue(result.getBrokenLinks().isEmpty());
    }

    @Test
    @DisplayName("Single valid event chain passes verification")
    void testSingleEventValidChain() {
        UUID runId = UUID.randomUUID();
        Instant t = Instant.parse("2025-01-01T00:00:00Z");
        AuditEvent event = buildEvent(UUID.randomUUID(), runId, "SCENARIO-1", "AGENT", "TOOL", "SUCCESS", GENESIS_HASH, t);

        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(event), hasher);
        assertTrue(result.isValid(), "Single valid event should pass: " + result.getBrokenLinks());
        assertEquals(1, result.getEventsChecked());
    }

    @Test
    @DisplayName("Multi-event valid chain passes verification")
    void testMultiEventValidChain() {
        UUID runId = UUID.randomUUID();
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:01Z");
        Instant t3 = Instant.parse("2025-01-01T00:00:02Z");

        AuditEvent e1 = buildEvent(UUID.randomUUID(), runId, "SC", "A1", "T1", "SUCCESS", GENESIS_HASH, t1);
        AuditEvent e2 = buildEvent(UUID.randomUUID(), runId, "SC", "A2", "T2", "SUCCESS", e1.getCurrentHash(), t2);
        AuditEvent e3 = buildEvent(UUID.randomUUID(), runId, "SC", "A3", "T3", "SUCCESS", e2.getCurrentHash(), t3);

        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(e1, e2, e3), hasher);
        assertTrue(result.isValid(), "Valid 3-event chain should pass: " + result.getBrokenLinks());
        assertEquals(3, result.getEventsChecked());
    }

    @Test
    @DisplayName("Altering a stored audit event's executionResult causes content hash mismatch — tamper-evident chain detected")
    void testAlteredEventCausesHashMismatch() {
        UUID runId = UUID.randomUUID();
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:01Z");

        AuditEvent e1 = buildEvent(UUID.randomUUID(), runId, "SC", "A1", "T1", "SUCCESS", GENESIS_HASH, t1);
        AuditEvent e2 = buildEvent(UUID.randomUUID(), runId, "SC", "A2", "T2", "SUCCESS", e1.getCurrentHash(), t2);

        // TAMPER: Alter e1's executionResult AFTER the hash was computed
        e1.setExecutionResult("TAMPERED_VALUE");

        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(e1, e2), hasher);
        assertFalse(result.isValid(), "Tampered event must cause chain verification failure");
        assertFalse(result.getBrokenLinks().isEmpty(), "BrokenLinks must be non-empty after tampering");
        assertTrue(result.getBrokenLinks().stream().anyMatch(link -> link.contains("CONTENT_HASH_MISMATCH")));
    }

    @Test
    @DisplayName("Broken prevHash linkage is detected — missing link in chain")
    void testBrokenPrevHashLink() {
        UUID runId = UUID.randomUUID();
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T00:00:01Z");

        AuditEvent e1 = buildEvent(UUID.randomUUID(), runId, "SC", "A1", "T1", "SUCCESS", GENESIS_HASH, t1);
        // e2 has WRONG prevHash (simulating a gap or inserted event)
        AuditEvent e2 = buildEvent(UUID.randomUUID(), runId, "SC", "A2", "T2", "SUCCESS", "WRONG_PREV_HASH", t2);

        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(e1, e2), hasher);
        assertFalse(result.isValid(), "Broken prevHash link must fail verification");
        assertTrue(result.getBrokenLinks().stream().anyMatch(link -> link.contains("BROKEN_LINK")));
    }

    @Test
    @DisplayName("First event with wrong prevHash (not GENESIS) is detected")
    void testFirstEventWrongPrevHash() {
        UUID runId = UUID.randomUUID();
        Instant t = Instant.parse("2025-01-01T00:00:00Z");
        // First event has non-genesis prevHash
        AuditEvent e1 = buildEvent(UUID.randomUUID(), runId, "SC", "A1", "T1", "SUCCESS", "NOT_GENESIS", t);

        AuditChainVerifier.AuditChainVerificationResult result = AuditChainVerifier.verifyChain(List.of(e1), hasher);
        assertFalse(result.isValid(), "First event with non-genesis prevHash must fail");
    }
}
