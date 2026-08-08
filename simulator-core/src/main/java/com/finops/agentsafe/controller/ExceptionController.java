package com.finops.agentsafe.controller;

import com.finops.agentsafe.domain.FinancialException;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.dto.ExceptionRequest;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.failure.FailureInjectionContext;
import com.finops.agentsafe.repository.FinancialExceptionRepository;
import com.finops.agentsafe.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exceptions")
@Tag(name = "Exception API", description = "Endpoints for logging and querying financial exceptions")
public class ExceptionController {

    private final FinancialExceptionRepository exceptionRepository;
    private final AuditService auditService;

    public ExceptionController(FinancialExceptionRepository exceptionRepository, AuditService auditService) {
        this.exceptionRepository = exceptionRepository;
        this.auditService = auditService;
    }

    @PostMapping
    @Operation(summary = "CREATE_EXCEPTION (LOW_RISK_WRITE): Log a financial operational exception")
    public ResponseEntity<ApiResponse<FinancialException>> createException(@RequestBody ExceptionRequest req) {
        FinancialException ex = new FinancialException(
            UUID.randomUUID(),
            req.getTransactionId(),
            req.getBatchId(),
            req.getExceptionType(),
            req.getSeverity() != null ? req.getSeverity() : "MEDIUM",
            "OPEN",
            req.getDetails()
        );

        FinancialException saved = exceptionRepository.save(ex);

        auditService.recordAuditEvent(
            FailureInjectionContext.getRunId(),
            FailureInjectionContext.getScenarioId(),
            "AGENT_UNDER_TEST",
            "CREATE_EXCEPTION",
            "CREATE_EXCEPTION",
            ActionRiskLevel.LOW_RISK_WRITE,
            req.getTransactionId() + "|" + req.getExceptionType(),
            "ALLOWED",
            "SUCCESS",
            "NONE",
            saved.getId().toString(),
            null,
            null,
            "Exception created: " + req.getDetails()
        );

        return ResponseEntity.ok(ApiResponse.success("Financial exception logged", saved));
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "GET_EXCEPTIONS (READ_ONLY): Query exceptions by transaction ID")
    public ResponseEntity<ApiResponse<List<FinancialException>>> getExceptionsByTransaction(@PathVariable String transactionId) {
        List<FinancialException> list = exceptionRepository.findByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.success("Exceptions retrieved", list));
    }
}
