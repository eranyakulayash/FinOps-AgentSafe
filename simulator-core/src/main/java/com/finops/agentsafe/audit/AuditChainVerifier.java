package com.finops.agentsafe.audit;

import com.finops.agentsafe.domain.AuditEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Tamper-evident audit chain verifier.
 *
 * Verifies the integrity of an audit event chain by:
 *   1. Sorting events by timestamp
 *   2. Checking the first event's prevHash equals the genesis hash
 *   3. Checking each event's prevHash equals the preceding event's currentHash
 *   4. Re-computing each event's currentHash from its raw fields and comparing
 *
 * Note: This verifier detects tampering. It does NOT prevent tampering.
 * The correct term is "tamper-evident audit chain", not "tamper-proof".
 */
public class AuditChainVerifier {

    public static final String GENESIS_HASH = "GENESIS_HASH_00000000000000000000000000000000";

    /**
     * Verify a list of audit events for chain integrity.
     *
     * @param events  List of audit events (will be sorted by timestamp internally)
     * @param hasher  SHA-256 hash function (to recompute currentHash from raw fields)
     * @return AuditChainVerificationResult with details of any broken links
     */
    public static AuditChainVerificationResult verifyChain(List<AuditEvent> events, Function<String, String> hasher) {
        if (events == null || events.isEmpty()) {
            return AuditChainVerificationResult.empty();
        }

        List<AuditEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(AuditEvent::getTimestamp));

        List<String> brokenLinks = new ArrayList<>();
        boolean valid = true;

        for (int i = 0; i < sorted.size(); i++) {
            AuditEvent event = sorted.get(i);

            // Check prevHash linkage
            String expectedPrevHash = (i == 0) ? GENESIS_HASH : sorted.get(i - 1).getCurrentHash();
            if (!expectedPrevHash.equals(event.getPrevHash())) {
                brokenLinks.add(String.format(
                    "BROKEN_LINK at index %d (eventId=%s): expected prevHash [%s] but found [%s]",
                    i, event.getId(), expectedPrevHash, event.getPrevHash()
                ));
                valid = false;
            }

            // Re-compute currentHash from raw fields and verify
            String rawToHash = event.getPrevHash() + "|" + event.getRunId() + "|" + event.getScenarioId()
                + "|" + event.getActor() + "|" + event.getToolUsed() + "|" + event.getExecutionResult()
                + "|" + event.getTimestamp().toEpochMilli();
            String recomputedHash = hasher.apply(rawToHash);

            if (!recomputedHash.equals(event.getCurrentHash())) {
                brokenLinks.add(String.format(
                    "CONTENT_HASH_MISMATCH at index %d (eventId=%s): recomputed hash [%s] differs from stored hash [%s]",
                    i, event.getId(), recomputedHash, event.getCurrentHash()
                ));
                valid = false;
            }
        }

        return new AuditChainVerificationResult(valid, sorted.size(), brokenLinks);
    }

    /**
     * Result of an audit chain verification.
     */
    public static class AuditChainVerificationResult {
        private final boolean valid;
        private final int eventsChecked;
        private final List<String> brokenLinks;

        public AuditChainVerificationResult(boolean valid, int eventsChecked, List<String> brokenLinks) {
            this.valid = valid;
            this.eventsChecked = eventsChecked;
            this.brokenLinks = brokenLinks;
        }

        public static AuditChainVerificationResult empty() {
            return new AuditChainVerificationResult(true, 0, List.of());
        }

        public boolean isValid() { return valid; }
        public int getEventsChecked() { return eventsChecked; }
        public List<String> getBrokenLinks() { return brokenLinks; }

        @Override
        public String toString() {
            return "AuditChainVerificationResult{valid=" + valid +
                   ", eventsChecked=" + eventsChecked +
                   ", brokenLinks=" + brokenLinks + "}";
        }
    }
}
