package com.finops.agentsafe.tool;

import com.finops.agentsafe.domain.FinancialException;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ExceptionType;
import com.finops.agentsafe.repository.FinancialExceptionRepository;
import com.finops.agentsafe.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CreateExceptionTool implements AgentTool {

    private final FinancialExceptionRepository exceptionRepository;
    private final AuditService auditService;

    public CreateExceptionTool(FinancialExceptionRepository exceptionRepository, AuditService auditService) {
        this.exceptionRepository = exceptionRepository;
        this.auditService = auditService;
    }

    @Override
    public String getToolName() { return "CREATE_EXCEPTION"; }

    @Override
    public String getDescription() { return "Log a financial exception for a discrepancy or system error"; }

    @Override
    public ActionRiskLevel getRiskLevel() { return ActionRiskLevel.LOW_RISK_WRITE; }

    @Override
    public Map<String, String> getInputSchema() {
        return Map.of(
            "transactionId", "String",
            "batchId", "UUID/String",
            "exceptionType", "String",
            "severity", "String",
            "description", "String"
        );
    }

    @Override
    public Map<String, String> getOutputSchema() { return Map.of("financialException", "FinancialExceptionObject"); }

    @Override
    public Set<String> getRequiredPermissions() { return Set.of("CREATE_EXCEPTION"); }

    @Override
    public boolean isRequiresApproval() { return false; }

    @Override
    public boolean isIdempotent() { return true; }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        String txId = request.getParameter("transactionId", String.class);
        String batchIdStr = request.getParameter("batchId", String.class);
        String typeStr = request.getParameter("exceptionType", String.class);
        String severity = request.getParameter("severity", String.class);
        String description = request.getParameter("description", String.class);

        if (txId == null || typeStr == null) {
            return AgentToolResult.failure(request, AgentToolResult.Status.FAILED, "Missing required parameters 'transactionId' or 'exceptionType'", null);
        }

        UUID batchId = batchIdStr != null && !batchIdStr.isBlank() ? UUID.fromString(batchIdStr) : null;
        ExceptionType exType = ExceptionType.valueOf(typeStr.toUpperCase());

        FinancialException ex = new FinancialException(
            UUID.randomUUID(),
            txId,
            batchId,
            exType,
            severity != null ? severity : "MEDIUM",
            "OPEN",
            description
        );

        FinancialException saved = exceptionRepository.save(ex);

        auditService.recordAuditEvent(
            request.getContext() != null ? request.getContext().getRunId() : null,
            request.getContext() != null ? request.getContext().getScenarioId() : null,
            request.getContext() != null ? request.getContext().getActorId() : "AGENT",
            "CREATE_EXCEPTION",
            "CREATE_EXCEPTION",
            ActionRiskLevel.LOW_RISK_WRITE,
            txId + "|" + typeStr,
            "ALLOWED",
            "SUCCESS",
            null,
            saved.getId().toString(),
            null,
            null,
            "Financial exception created: " + description
        );

        return AgentToolResult.success(request, saved, true, saved.getId().toString());
    }
}
