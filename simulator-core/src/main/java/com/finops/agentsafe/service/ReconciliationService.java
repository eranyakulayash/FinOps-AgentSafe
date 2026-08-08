package com.finops.agentsafe.service;

import com.finops.agentsafe.domain.FinancialException;
import com.finops.agentsafe.domain.ReconciliationRecord;
import com.finops.agentsafe.domain.SettlementLineItem;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.ActionRiskLevel;
import com.finops.agentsafe.enums.ExceptionType;
import com.finops.agentsafe.enums.MatchStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.failure.FailureInjectionContext;
import com.finops.agentsafe.repository.FinancialExceptionRepository;
import com.finops.agentsafe.repository.ReconciliationRecordRepository;
import com.finops.agentsafe.repository.SettlementLineItemRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.validator.FinancialInvariantValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final SettlementLineItemRepository lineItemRepository;
    private final ReconciliationRecordRepository reconciliationRepository;
    private final FinancialExceptionRepository exceptionRepository;
    private final FinancialInvariantValidator invariantValidator;
    private final AuditService auditService;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 SettlementLineItemRepository lineItemRepository,
                                 ReconciliationRecordRepository reconciliationRepository,
                                 FinancialExceptionRepository exceptionRepository,
                                 FinancialInvariantValidator invariantValidator,
                                 AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.exceptionRepository = exceptionRepository;
        this.invariantValidator = invariantValidator;
        this.auditService = auditService;
    }

    @Transactional
    public ReconciliationRecord reconcileTransaction(String transactionId, UUID lineItemId) {
        Transaction tx = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Internal transaction not found: " + transactionId));

        boolean alreadyReconciled = reconciliationRepository.existsByTransactionId(transactionId);
        invariantValidator.validateNotAlreadyReconciled(alreadyReconciled, transactionId);

        SettlementLineItem lineItem = lineItemRepository.findById(lineItemId)
            .orElseThrow(() -> new IllegalArgumentException("Settlement line item not found: " + lineItemId));

        BigDecimal internalAmount = tx.getAmount();
        BigDecimal externalAmount = lineItem.getAmount();
        BigDecimal discrepancy = internalAmount.subtract(externalAmount).abs();

        MatchStatus matchStatus;
        if (discrepancy.compareTo(BigDecimal.ZERO) == 0) {
            matchStatus = MatchStatus.MATCHED;
            tx.setStatus(TransactionStatus.RECONCILED);
            transactionRepository.save(tx);
        } else {
            matchStatus = MatchStatus.AMOUNT_MISMATCH;
            FinancialException ex = new FinancialException(
                UUID.randomUUID(),
                transactionId,
                lineItem.getBatch().getId(),
                ExceptionType.AMOUNT_MISMATCH,
                "HIGH",
                "OPEN",
                String.format("Discrepancy of $%s between internal transaction (%s) and external settlement (%s)", discrepancy, internalAmount, externalAmount)
            );
            exceptionRepository.save(ex);
        }

        ReconciliationRecord record = new ReconciliationRecord(
            UUID.randomUUID(),
            transactionId,
            lineItemId,
            discrepancy,
            matchStatus
        );

        ReconciliationRecord saved = reconciliationRepository.save(record);

        auditService.recordAuditEvent(
            FailureInjectionContext.getRunId(),
            FailureInjectionContext.getScenarioId(),
            "AGENT_UNDER_TEST",
            "RECONCILE_TRANSACTION",
            "RECONCILE",
            ActionRiskLevel.LOW_RISK_WRITE,
            transactionId + "|" + lineItemId,
            "ALLOWED",
            matchStatus.name(),
            tx.getStatus().name(),
            saved.getId().toString(),
            null,
            null,
            "Reconciliation evaluated: " + matchStatus
        );

        return saved;
    }

    public Optional<ReconciliationRecord> getReconciliationByTransactionId(String transactionId) {
        return reconciliationRepository.findByTransactionId(transactionId);
    }
}
