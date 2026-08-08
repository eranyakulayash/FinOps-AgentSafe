package com.finops.agentsafe.controller;

import com.finops.agentsafe.domain.SettlementBatch;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settlements")
@Tag(name = "Settlement API", description = "Endpoints for viewing, approving, and rejecting merchant settlement batches")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/{batchId}")
    @Operation(summary = "GET_SETTLEMENT_FILE (READ_ONLY): Retrieve a settlement batch by ID")
    public ResponseEntity<ApiResponse<SettlementBatch>> getSettlementBatch(@PathVariable UUID batchId) {
        return settlementService.getSettlementBatch(batchId)
            .map(batch -> ResponseEntity.ok(ApiResponse.success("Settlement batch retrieved", batch)))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Settlement batch not found: " + batchId)));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "GET_MERCHANT_SETTLEMENTS (READ_ONLY): Query settlement batches by merchant")
    public ResponseEntity<ApiResponse<List<SettlementBatch>>> getBatchesByMerchant(@PathVariable UUID merchantId) {
        List<SettlementBatch> batches = settlementService.getBatchesByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success("Merchant settlement batches retrieved", batches));
    }

    @PostMapping("/{batchId}/approve")
    @Operation(summary = "APPROVE_SETTLEMENT (HIGH_RISK_WRITE): Approve settlement batch payout. Requires X-Supervisor-Token")
    public ResponseEntity<ApiResponse<SettlementBatch>> approveSettlement(
            @PathVariable UUID batchId,
            @RequestHeader(value = "X-Supervisor-Token", required = false) String token) {
        SettlementBatch approved = settlementService.approveSettlement(batchId, token);
        return ResponseEntity.ok(ApiResponse.success("Settlement batch approved successfully", approved));
    }

    @PostMapping("/{batchId}/reject")
    @Operation(summary = "REJECT_SETTLEMENT (HIGH_RISK_WRITE): Reject settlement batch. Requires X-Supervisor-Token")
    public ResponseEntity<ApiResponse<SettlementBatch>> rejectSettlement(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "Discrepancy identified") String reason,
            @RequestHeader(value = "X-Supervisor-Token", required = false) String token) {
        SettlementBatch rejected = settlementService.rejectSettlement(batchId, reason, token);
        return ResponseEntity.ok(ApiResponse.success("Settlement batch rejected successfully", rejected));
    }
}
