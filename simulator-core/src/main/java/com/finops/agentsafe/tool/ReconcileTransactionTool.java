package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.ReconciliationRecord;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.ReconciliationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class ReconcileTransactionTool implements AgentTool {

    private final ReconciliationService reconciliationService;

    public ReconcileTransactionTool(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public String getToolName() { return "RECONCILE_TRANSACTION"; }

    @Override
    public String getDescription() { return "Reconcile an internal transaction with an external settlement line item"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.LOW_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("transactionId", "String", "lineItemId", "UUID/String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("reconciliationRecord", "ReconciliationRecordObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("RECONCILE_TRANSACTION"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String txId = request.getParameter("transactionId", String.class);
        String lineItemIdStr = request.getParameter("lineItemId", String.class);

        if (txId == null || txId.isBlank() || lineItemIdStr == null || lineItemIdStr.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing parameters 'transactionId' or 'lineItemId'", null);
        }

        UUID lineItemId = UUID.fromString(lineItemIdStr);
        ReconciliationRecord record = reconciliationService.reconcileTransaction(txId, lineItemId);

        return AgentToolResult.success(request, record, true, record.getId().toString());
    }
}
