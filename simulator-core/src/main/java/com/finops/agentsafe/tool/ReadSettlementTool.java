package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.SettlementBatch;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.SettlementService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ReadSettlementTool implements AgentTool {

    private final SettlementService settlementService;

    public ReadSettlementTool(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Override
    public String getToolName() { return "READ_SETTLEMENT"; }

    @Override
    public String getDescription() { return "Retrieve details of a settlement batch by batch ID"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("batchId", "UUID/String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("settlementBatch", "SettlementBatchObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("READ_SETTLEMENT"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String batchIdStr = request.getParameter("batchId", String.class);
        if (batchIdStr == null || batchIdStr.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing required parameter 'batchId'", null);
        }

        UUID batchId = UUID.fromString(batchIdStr);
        Optional<SettlementBatch> batchOpt = settlementService.getSettlementBatch(batchId);
        if (batchOpt.isEmpty()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE, "Settlement batch not found: " + batchId, null);
        }

        return AgentToolResult.success(request, batchOpt.get(), false, null);
    }
}
