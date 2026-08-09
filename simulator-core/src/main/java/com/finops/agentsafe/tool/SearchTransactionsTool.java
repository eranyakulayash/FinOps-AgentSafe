package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.PaymentService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SearchTransactionsTool implements AgentTool {

    private final PaymentService paymentService;

    public SearchTransactionsTool(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String getToolName() { return "SEARCH_TRANSACTIONS"; }

    @Override
    public String getDescription() { return "Search transactions by merchant ID"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("merchantId", "UUID/String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("transactions", "List<TransactionObject>"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("SEARCH_TRANSACTIONS"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String mIdStr = request.getParameter("merchantId", String.class);
        if (mIdStr == null || mIdStr.isBlank()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing required parameter 'merchantId'", null);
        }

        UUID merchantId = UUID.fromString(mIdStr);
        List<Transaction> list = paymentService.getTransactionsByMerchant(merchantId);
        return AgentToolResult.success(request, list, false, null);
    }
}
