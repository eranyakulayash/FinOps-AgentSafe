package com.finops.agentsafe.controller;

import com.finops.agentsafe.domain.ReconciliationRecord;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.dto.ReconciliationRequest;
import com.finops.agentsafe.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation API", description = "Endpoints for matching internal transactions against external settlement files")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/match")
    @Operation(summary = "RECONCILE (LOW_RISK_WRITE): Match an internal transaction with a settlement line item")
    public ResponseEntity<ApiResponse<ReconciliationRecord>> reconcileTransaction(@RequestBody ReconciliationRequest req) {
        ReconciliationRecord record = reconciliationService.reconcileTransaction(req.getTransactionId(), req.getSettlementLineItemId());
        return ResponseEntity.ok(ApiResponse.success("Reconciliation evaluated successfully", record));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "GET_RECONCILIATION (READ_ONLY): Query reconciliation status for a transaction")
    public ResponseEntity<ApiResponse<ReconciliationRecord>> getReconciliation(@PathVariable String transactionId) {
        return reconciliationService.getReconciliationByTransactionId(transactionId)
            .map(rec -> ResponseEntity.ok(ApiResponse.success("Reconciliation record found", rec)))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("No reconciliation record found for transaction: " + transactionId)));
    }
}
