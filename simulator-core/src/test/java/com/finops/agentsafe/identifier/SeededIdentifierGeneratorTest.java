package com.finops.agentsafe.identifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeededIdentifierGeneratorTest {

    @Test
    @DisplayName("Same seed produces same UUID sequence on two independent generator instances")
    void testSameSeedSameSequence() {
        long seed = 42L;
        SeededIdentifierGenerator gen1 = new SeededIdentifierGenerator(seed);
        SeededIdentifierGenerator gen2 = new SeededIdentifierGenerator(seed);

        for (int i = 0; i < 20; i++) {
            UUID id1 = gen1.nextUUID();
            UUID id2 = gen2.nextUUID();
            assertEquals(id1, id2, "At call #" + i + ", both generators with seed=" + seed + " must produce the same UUID");
        }
    }

    @Test
    @DisplayName("Different seeds produce different UUID sequences")
    void testDifferentSeedsDifferentSequences() {
        SeededIdentifierGenerator gen1 = new SeededIdentifierGenerator(1L);
        SeededIdentifierGenerator gen2 = new SeededIdentifierGenerator(2L);

        UUID id1 = gen1.nextUUID();
        UUID id2 = gen2.nextUUID();
        assertNotEquals(id1, id2, "Different seeds must produce different UUIDs");
    }

    @Test
    @DisplayName("Seeded UUIDs are valid RFC 4122 version 4 UUIDs")
    void testSeededUUIDsAreVersion4() {
        SeededIdentifierGenerator gen = new SeededIdentifierGenerator(999L);
        for (int i = 0; i < 10; i++) {
            UUID id = gen.nextUUID();
            assertEquals(4, id.version(), "UUID at call #" + i + " must be version 4");
            assertEquals(2, id.variant(), "UUID at call #" + i + " must have RFC 4122 variant");
        }
    }

    @Test
    @DisplayName("RandomIdentifierGenerator produces non-null, unique UUIDs")
    void testRandomGeneratorProducesUniqueIds() {
        RandomIdentifierGenerator gen = new RandomIdentifierGenerator();
        UUID id1 = gen.nextUUID();
        UUID id2 = gen.nextUUID();
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Two consecutive random UUIDs should be different");
    }

    @Test
    @DisplayName("nextTransactionId generates prefixed ID")
    void testNextTransactionId() {
        SeededIdentifierGenerator gen = new SeededIdentifierGenerator(7L);
        String txId = gen.nextTransactionId("PAY");
        assertTrue(txId.startsWith("PAY-"), "Transaction ID must start with prefix");
        assertTrue(txId.length() > 4, "Transaction ID must include UUID portion");
    }
}
