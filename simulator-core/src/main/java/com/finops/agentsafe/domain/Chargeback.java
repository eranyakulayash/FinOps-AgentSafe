package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.ChargebackStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a chargeback dispute initiated against an original payment transaction.
 *
 * Lifecycle (ChargebackStateMachine):
 *   OPEN → UNDER_REVIEW → ACCEPTED | DISPUTED → RESOLVED → CLOSED
 *
 * Financial invariants:
 *   - amount cannot exceed the original transaction amount
 *   - duplicate chargebacks on the same transaction are prevented by idempotency_key UNIQUE constraint
 *   - transitions are validated by ChargebackStateMachine
 */
@Entity
@Table(name = "chargebacks")
public class Chargeback {

    @Id
    private UUID id;

    @Column(name = "original_transaction_id", nullable = false)
    private String originalTransactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargebackStatus status;

    @Column(name = "scenario_id")
    private String scenarioId;

    @Column(name = "run_id")
    private UUID runId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Chargeback() {}

    public Chargeback(UUID id, String originalTransactionId, BigDecimal amount, String reasonCode,
                      String idempotencyKey, ChargebackStatus status, String scenarioId,
                      UUID runId, Instant createdAt) {
        this.id = id;
        this.originalTransactionId = originalTransactionId;
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.scenarioId = scenarioId;
        this.runId = runId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOriginalTransactionId() { return originalTransactionId; }
    public void setOriginalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public ChargebackStatus getStatus() { return status; }
    public void setStatus(ChargebackStatus status) { this.status = status; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
