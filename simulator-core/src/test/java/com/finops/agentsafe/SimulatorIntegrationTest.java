package com.finops.agentsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.SettlementBatch;
import com.finops.agentsafe.domain.SettlementLineItem;
import com.finops.agentsafe.dto.ExceptionRequest;
import com.finops.agentsafe.dto.PaymentRequest;
import com.finops.agentsafe.dto.ReconciliationRequest;
import com.finops.agentsafe.enums.ExceptionType;
import com.finops.agentsafe.enums.SettlementStatus;
import com.finops.agentsafe.repository.SettlementBatchRepository;
import com.finops.agentsafe.repository.SettlementLineItemRepository;
import com.finops.agentsafe.service.SyntheticDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SimulatorIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SyntheticDataService syntheticDataService;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private SettlementLineItemRepository lineItemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final String supervisorToken = "SUP-SECRET-AUTH-TOKEN-9988";

    @Test
    @DisplayName("Full Financial Workflow Integration Test: Payment -> Reconciliation -> Exception -> Settlement Approval -> Audit Trail")
    void testEndToEndFinancialWorkflow() throws Exception {
        // 1. Seed synthetic scenario — creates a merchant, transactions, and a settlement batch
        long seed = 99001;
        Merchant merchant = syntheticDataService.seedSyntheticScenario(seed, "Global Enterprise Inc", 2);

        // The batch created by the seeder is keyed from seed + default generator version.
        // We use this batch for the settlement-approval steps (6-7).
        UUID seededBatchId = UUID.nameUUIDFromBytes(
            ("STL-" + seed + "-" + SyntheticDataService.DEFAULT_GENERATOR_VERSION).getBytes());

        // 2. Process a new payment via REST API — this is the transaction we will reconcile.
        String newTxId = "TX-99001-NEW";
        PaymentRequest paymentRequest = new PaymentRequest(
            newTxId,
            "IDEMP-TX-99001-NEW",
            merchant.getId(),
            new BigDecimal("250.00"),
            "USD"
        );

        mockMvc.perform(post("/api/v1/transactions/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(newTxId))
                .andExpect(jsonPath("$.data.amount").value(250.00));

        // 3. Create a self-contained settlement batch + line item for TX-99001-NEW.
        //    This makes the reconciliation step fully deterministic and independent of
        //    seed-generated transaction IDs (which use a versioned format: TX-seed-ver-NNNN).
        UUID reconBatchId = UUID.randomUUID();
        SettlementBatch reconBatch = new SettlementBatch(
            reconBatchId,
            merchant.getId(),
            "recon-test-batch.csv",
            "Self-contained reconciliation batch for SimulatorIntegrationTest",
            new BigDecimal("250.00"),
            new BigDecimal("6.25"),
            new BigDecimal("243.75"),
            SettlementStatus.UNPROCESSED
        );
        batchRepository.save(reconBatch);

        UUID lineItemId = UUID.randomUUID();
        SettlementLineItem lineItem = new SettlementLineItem(
            lineItemId,
            reconBatch,
            "EXT-" + newTxId,
            new BigDecimal("250.00"),
            new BigDecimal("6.25"),
            new BigDecimal("243.75")
        );
        lineItemRepository.save(lineItem);

        // 4. Reconcile TX-99001-NEW against the line item created above
        ReconciliationRequest reconReq = new ReconciliationRequest(newTxId, lineItemId);

        mockMvc.perform(post("/api/v1/reconciliation/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reconReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED"));

        // 5. Log a financial exception against the same transaction
        ExceptionRequest exReq = new ExceptionRequest(
            newTxId,
            reconBatchId,
            ExceptionType.AMOUNT_MISMATCH,
            "MEDIUM",
            "Discrepancy investigated during automated benchmark run"
        );

        mockMvc.perform(post("/api/v1/exceptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6. Attempt settlement approval WITHOUT token (Should fail with 403 Forbidden)
        mockMvc.perform(post("/api/v1/settlements/" + seededBatchId + "/approve"))
                .andExpect(status().isForbidden());

        // 7. Approve seeded settlement batch WITH supervisor token (Should succeed)
        mockMvc.perform(post("/api/v1/settlements/" + seededBatchId + "/approve")
                .header("X-Supervisor-Token", supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 8. Verify audit trail contains logged events
        mockMvc.perform(get("/api/v1/audit/scenario/DEFAULT_SCENARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
