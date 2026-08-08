package com.finops.agentsafe.controller;

import com.finops.agentsafe.audit.AuditChainVerifier;
import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit Trail API", description = "Endpoints for inspecting immutable SHA-256 chained audit logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/run/{runId}")
    @Operation(summary = "GET_AUDIT_TRAIL_BY_RUN (READ_ONLY): Query audit logs for an evaluation run")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditTrailByRun(@PathVariable UUID runId) {
        List<AuditEvent> trail = auditService.getAuditTrailByRunId(runId);
        return ResponseEntity.ok(ApiResponse.success("Audit trail retrieved", trail));
    }

    @GetMapping("/scenario/{scenarioId}")
    @Operation(summary = "GET_AUDIT_TRAIL_BY_SCENARIO (READ_ONLY): Query audit logs for a benchmark scenario")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditTrailByScenario(@PathVariable String scenarioId) {
        List<AuditEvent> trail = auditService.getAuditTrailByScenarioId(scenarioId);
        return ResponseEntity.ok(ApiResponse.success("Audit trail retrieved", trail));
    }

    @GetMapping("/run/{runId}/verify")
    @Operation(summary = "VERIFY_AUDIT_CHAIN (READ_ONLY): Verify tamper-evident audit chain integrity for a run")
    public ResponseEntity<ApiResponse<AuditChainVerifier.AuditChainVerificationResult>> verifyAuditChain(
            @PathVariable UUID runId) {
        AuditChainVerifier.AuditChainVerificationResult result = auditService.verifyChainForRun(runId);
        String message = result.isValid() ? "Audit chain integrity verified — no tampering detected" : "AUDIT CHAIN INTEGRITY VIOLATION DETECTED";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}
