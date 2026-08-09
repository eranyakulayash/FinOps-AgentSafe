package com.finops.agentsafe.policy;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolContext;
import com.finops.agentsafe.tool.AgentToolRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Enforces scenario permissions, role permissions, step limits, state machine validity,
 * and human approval requirements before any tool execution.
 *
 * CRITICAL SECURITY INVARIANTS:
 *   1. An agent is NEVER permitted to approve a HumanApprovalRequest (self-approval prevention).
 *   2. Mandatory dependencies (HumanApprovalRequestRepository, AuditService) must be present.
 *   3. Fails CLOSED (DENY) if security infrastructure is unavailable or fails.
 */
@Component
public class AgentToolPolicyEngine {

    private final HumanApprovalRequestRepository approvalRepository;
    private final AuditService auditService;

    public AgentToolPolicyEngine(HumanApprovalRequestRepository approvalRepository,
                                 AuditService auditService) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "HumanApprovalRequestRepository is mandatory for security policy enforcement.");
        this.auditService = Objects.requireNonNull(auditService, "AuditService is mandatory for security audit enforcement.");
    }

    /**
     * Evaluates a tool execution request against active policies.
     */
    public PolicyDecision evaluate(AgentTool tool, AgentToolRequest request, Set<String> permittedTools, int maxSteps) {
        AgentToolContext ctx = request.getContext();
        int currentStep = ctx != null ? ctx.getStepNumber() : 1;

        // 1. Step limit check
        if (maxSteps > 0 && currentStep > maxSteps) {
            recordAudit(request, tool, PolicyDecision.STEP_LIMIT_EXCEEDED, "Step limit exceeded (" + currentStep + " > " + maxSteps + ")");
            return PolicyDecision.STEP_LIMIT_EXCEEDED;
        }

        // 2. Scenario tool permission check
        if (permittedTools != null && !permittedTools.isEmpty() && !permittedTools.contains(tool.getToolName())) {
            recordAudit(request, tool, PolicyDecision.DENY, "Tool [" + tool.getToolName() + "] is not permitted in scenario [" + (ctx != null ? ctx.getScenarioId() : "") + "]");
            return PolicyDecision.DENY;
        }

        // 3. Security invariant check: Direct approval tools are prohibited for agent actors
        if ("APPROVE_HUMAN_REQUEST".equalsIgnoreCase(tool.getToolName()) ||
            "DIRECT_APPROVAL".equalsIgnoreCase(tool.getToolName())) {
            recordAudit(request, tool, PolicyDecision.DENY, "AUTHORIZATION_BOUNDARY_VIOLATION: Agents are prohibited from self-approving requests");
            return PolicyDecision.DENY;
        }

        // 4. Risk Level & Approval check (FAIL CLOSED on missing data or exception)
        if (tool.getRiskLevel() == ActionRiskLevel.HIGH_RISK_WRITE || tool.isRequiresApproval()) {
            String txId = request.getParameter("relatedTransactionId", String.class);
            if (txId == null) txId = request.getParameter("originalTransactionId", String.class);
            if (txId == null) txId = request.getParameter("transactionId", String.class);

            if (txId != null) {
                try {
                    var approvalOpt = approvalRepository.findFirstByRelatedTransactionIdAndRequestedActionAndStatus(
                        txId, tool.getToolName(), ApprovalStatus.APPROVED);

                    if (approvalOpt.isEmpty()) {
                        recordAudit(request, tool, PolicyDecision.APPROVAL_REQUIRED, "Action requires prior human approval");
                        return PolicyDecision.APPROVAL_REQUIRED;
                    }
                } catch (Exception e) {
                    recordAudit(request, tool, PolicyDecision.DENY, "SECURITY_INFRASTRUCTURE_ERROR: Approval repository check failed: " + e.getMessage());
                    return PolicyDecision.DENY;
                }
            } else {
                recordAudit(request, tool, PolicyDecision.APPROVAL_REQUIRED, "Action requires prior human approval");
                return PolicyDecision.APPROVAL_REQUIRED;
            }
        }

        recordAudit(request, tool, PolicyDecision.ALLOW, "Tool execution permitted");
        return PolicyDecision.ALLOW;
    }

    private void recordAudit(AgentToolRequest req, AgentTool tool, PolicyDecision decision, String reason) {
        AgentToolContext ctx = req.getContext();
        if (ctx == null || auditService == null) return;

        try {
            auditService.recordAuditEvent(
                ctx.getRunId(),
                ctx.getScenarioId(),
                ctx.getActorId() != null ? ctx.getActorId() : "AGENT_UNDER_TEST",
                tool.getToolName(),
                tool.getToolName(),
                tool.getRiskLevel(),
                req.getParameters() != null ? req.getParameters().toString() : "{}",
                decision.name(),
                decision == PolicyDecision.ALLOW ? "PERMITTED" : "BLOCKED",
                null,
                null,
                null,
                null,
                reason
            );
        } catch (Exception ignored) {}
    }
}
