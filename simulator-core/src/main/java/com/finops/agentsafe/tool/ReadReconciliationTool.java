package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.ReconciliationRecord;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.ReconciliationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ReadReconciliationTool implements AgentTool {

    private final ReconciliationService reconciliationService;

    public ReadReconciliationTool(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public String getToolName() { return "READ_RECONCILIATION"; }

    @Override
    public String getDescription() { return "Retrieve reconciliation status for a transaction"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("transactionId", "String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("reconciliationRecord", "ReconciliationRecordObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("READ_RECONCILIATION"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String txId = request.getParameter("transactionId", String.class);
        if (txId == null || txId.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing required parameter 'transactionId'", null);
        }

        Optional<ReconciliationRecord> recOpt = reconciliationService.getReconciliationByTransactionId(txId);
        if (recOpt.isEmpty()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE, "Reconciliation record not found for transaction: " + txId, null);
        }

        return AgentToolResult.success(request, recOpt.get(), false, null);
    }
}
