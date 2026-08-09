package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.service.PaymentService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ReadTransactionTool implements AgentTool {

    private final PaymentService paymentService;

    public ReadTransactionTool(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String getToolName() { return "READ_TRANSACTION"; }

    @Override
    public String getDescription() { return "Retrieve details of a transaction by transaction ID"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.READ_ONLY; }

    @Override
    public Map<String, String> getInputSchema() { return Map.of("transactionId", "String"); }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("transaction", "TransactionObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("READ_TRANSACTION"); }

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

        Optional<Transaction> txOpt = paymentService.getTransaction(txId);
        if (txOpt.isEmpty()) {
            return AgentToolResult.failure(request, AgentToolResult.Status.NON_RETRYABLE_FAILURE, "Transaction not found: " + txId, null);
        }

        return AgentToolResult.success(request, txOpt.get(), false, null);
    }
}
