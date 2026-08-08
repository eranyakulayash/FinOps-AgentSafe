package com.finops.agentsafe.controller;

import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.dto.ApprovalCreateRequest;
import com.finops.agentsafe.dto.ApprovalDecisionRequest;
import com.finops.agentsafe.service.HumanApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "Human Approval API", description = "Endpoints for human-in-the-loop approval workflow")
public class ApprovalController {

    private final HumanApprovalService approvalService;

    public ApprovalController(HumanApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    @Operation(summary = "CREATE_APPROVAL_REQUEST (HIGH_RISK_WRITE): Create a human approval request for a high-risk operation")
    public ResponseEntity<ApiResponse<HumanApprovalRequest>> createApprovalRequest(
            @RequestBody ApprovalCreateRequest req) {
        HumanApprovalRequest approval = approvalService.createApprovalRequest(
            req.getRequestedBy(),
            req.getRequestedAction(),
            req.getReason(),
            req.getRelatedTransactionId(),
            req.getRelatedSettlementId(),
            req.getScenarioId(),
            req.getRunId()
        );
        return ResponseEntity.ok(ApiResponse.success("Approval request created", approval));
    }

    @GetMapping("/{id}")
    @Operation(summary = "GET_APPROVAL (READ_ONLY): Retrieve an approval request by ID")
    public ResponseEntity<ApiResponse<HumanApprovalRequest>> getApprovalRequest(@PathVariable UUID id) {
        return approvalService.getApprovalRequest(id)
            .map(req -> ResponseEntity.ok(ApiResponse.success("Approval request retrieved", req)))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Approval request not found: " + id)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "APPROVE_REQUEST (HIGH_RISK_WRITE): Approve a pending approval request. Requester cannot approve own request.")
    public ResponseEntity<ApiResponse<HumanApprovalRequest>> approve(
            @PathVariable UUID id,
            @RequestBody ApprovalDecisionRequest req) {
        HumanApprovalRequest approved = approvalService.approve(id, req.getDecidedBy());
        return ResponseEntity.ok(ApiResponse.success("Approval request approved", approved));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "REJECT_REQUEST (HIGH_RISK_WRITE): Reject a pending approval request")
    public ResponseEntity<ApiResponse<HumanApprovalRequest>> reject(
            @PathVariable UUID id,
            @RequestBody ApprovalDecisionRequest req) {
        HumanApprovalRequest rejected = approvalService.reject(id, req.getDecidedBy(), req.getReason());
        return ResponseEntity.ok(ApiResponse.success("Approval request rejected", rejected));
    }
}
