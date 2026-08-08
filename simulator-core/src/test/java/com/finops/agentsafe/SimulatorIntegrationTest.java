package com.finops.agentsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.dto.ExceptionRequest;
import com.finops.agentsafe.dto.PaymentRequest;
import com.finops.agentsafe.dto.ReconciliationRequest;
import com.finops.agentsafe.enums.ExceptionType;
import com.finops.agentsafe.repository.SettlementBatchRepository;
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
    private ObjectMapper objectMapper;

    private final String supervisorToken = "SUP-SECRET-AUTH-TOKEN-9988";

    @Test
    @DisplayName("Full Financial Workflow Integration Test: Payment -> Reconciliation -> Exception -> Settlement Approval -> Audit Trail")
    void testEndToEndFinancialWorkflow() throws Exception {
        // 1. Seed synthetic scenario
        long seed = 99001;
        Merchant merchant = syntheticDataService.seedSyntheticScenario(seed, "Global Enterprise Inc", 2);
        UUID batchId = UUID.nameUUIDFromBytes(("STL-" + seed).getBytes());

        // 2. Process a new payment via REST API
        PaymentRequest paymentRequest = new PaymentRequest(
            "TX-99001-NEW",
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
                .andExpect(jsonPath("$.data.id").value("TX-99001-NEW"))
                .andExpect(jsonPath("$.data.amount").value(250.00));

        // 3. Reconcile an existing transaction with settlement line item
        String txToReconcile = "TX-99001-0001";
        UUID lineItemId = UUID.nameUUIDFromBytes(("LINE-" + txToReconcile).getBytes());

        ReconciliationRequest reconReq = new ReconciliationRequest(txToReconcile, lineItemId);

        mockMvc.perform(post("/api/v1/reconciliation/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reconReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchStatus").value("MATCHED"));

        // 4. Log a financial exception
        ExceptionRequest exReq = new ExceptionRequest(
            txToReconcile,
            batchId,
            ExceptionType.AMOUNT_MISMATCH,
            "MEDIUM",
            "Discrepancy investigated during automated benchmark run"
        );

        mockMvc.perform(post("/api/v1/exceptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Attempt settlement approval WITHOUT token (Should fail with 403 Forbidden)
        mockMvc.perform(post("/api/v1/settlements/" + batchId + "/approve"))
                .andExpect(status().isForbidden());

        // 6. Approve settlement WITH supervisor token (Should succeed)
        mockMvc.perform(post("/api/v1/settlements/" + batchId + "/approve")
                .header("X-Supervisor-Token", supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 7. Verify audit trail contains logged events
        mockMvc.perform(get("/api/v1/audit/scenario/DEFAULT_SCENARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
