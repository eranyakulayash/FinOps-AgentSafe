package com.finops.agentsafe.policy;

import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolContext;
import com.finops.agentsafe.tool.AgentToolRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces scenario permissions, role permissions, step limits, state machine validity,
 * and human approval requirements before any tool execution.
 *
 * CRITICAL SECURITY INVARIANT:
 *   An agent is NEVER permitted to approve a HumanApprovalRequest.
 *   The policy engine rejects any attempt to mutate approval status to APPROVED by an agent actor.
 */
@Component
public class AgentToolPolicyEngine {

    private final HumanApprovalRequestRepository approvalRepository;
    private final AuditService auditService;

    public AgentToolPolicyEngine(HumanApprovalRequestRepository approvalRepository,
                                 AuditService auditService) {
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
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

        // 4. Risk Level & Approval check
        if (tool.getRiskLevel() == ActionRiskLevel.HIGH_RISK_WRITE || tool.isRequiresApproval()) {
            String txId = request.getParameter("relatedTransactionId", String.class);
            if (txId == null) txId = request.getParameter("originalTransactionId", String.class);
            if (txId == null) txId = request.getParameter("transactionId", String.class);

            if (txId != null) {
                var approvalOpt = approvalRepository.findFirstByRelatedTransactionIdAndRequestedActionAndStatus(
                    txId, tool.getToolName(), ApprovalStatus.APPROVED);

                if (approvalOpt.isEmpty()) {
                    recordAudit(request, tool, PolicyDecision.APPROVAL_REQUIRED, "Action requires prior human approval");
                    return PolicyDecision.APPROVAL_REQUIRED;
                }
            }
        }

        recordAudit(request, tool, PolicyDecision.ALLOW, "Tool execution permitted");
        return PolicyDecision.ALLOW;
    }

    private void recordAudit(AgentToolRequest req, AgentTool tool, PolicyDecision decision, String reason) {
        AgentToolContext ctx = req.getContext();
        if (ctx == null) return;

        auditService.recordAuditEvent(
            ctx.getRunId(),
            ctx.getScenarioId(),
            ctx.getActorId() != null ? ctx.getActorId() : "AGENT_UNDER_TEST",
            tool.getToolName(),
            tool.getToolName(),
            tool.getRiskLevel(),
            req.getParameters().toString(),
            decision.name(),
            decision == PolicyDecision.ALLOW ? "PERMITTED" : "BLOCKED",
            null,
            null,
            null,
            null,
            reason
        );
    }
}
