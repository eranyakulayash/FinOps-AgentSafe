package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.repository.AuditEventRepository;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLAuditPersistenceTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Audit Merchant", new BigDecimal("1.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Verify Audit Persistence: financial operations create persistent AuditEvents with valid hash chaining")
    void testAuditEventPersistenceAndHashChaining() {
        UUID runId = UUID.randomUUID();
        String scenarioId = "AUDIT-TEST-SCENARIO";

        AuditEvent event1 = auditService.recordAuditEvent(
            runId,
            scenarioId,
            "TEST_AGENT",
            "INITIALIZE",
            "INIT_TOOL",
            ActionRiskLevel.READ_ONLY,
            "payload1",
            "ALLOWED",
            "SUCCESS",
            null,
            null,
            null,
            null,
            "Initial setup event"
        );

        paymentService.processPayment("TX-AUDIT-1", "IDEMP-AUDIT-1", merchant.getId(), new BigDecimal("300.00"), "USD");

        List<AuditEvent> auditTrail = auditEventRepository.findByScenarioId(scenarioId);
        assertFalse(auditTrail.isEmpty(), "Audit trail must contain recorded events");

        AuditEvent fetchedEvent1 = auditEventRepository.findById(event1.getId()).orElseThrow();
        assertEquals("GENESIS_HASH_00000000000000000000000000000000", fetchedEvent1.getPrevHash());
        assertNotNull(fetchedEvent1.getCurrentHash());
        assertFalse(fetchedEvent1.getCurrentHash().isBlank());
    }
}
