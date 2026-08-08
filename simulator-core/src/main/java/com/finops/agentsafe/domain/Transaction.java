package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.enums.TransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private String id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "original_payment_id")
    private String originalPaymentId;

    @Column(name = "scenario_id")
    private String scenarioId;

    @Column(name = "run_id")
    private java.util.UUID runId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Transaction() {}

    public Transaction(String id, String idempotencyKey, UUID merchantId, BigDecimal amount, String currency, TransactionType type, TransactionStatus status, String originalPaymentId) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.originalPaymentId = originalPaymentId;
        this.createdAt = Instant.now();
    }

    public Transaction(String id, String idempotencyKey, UUID merchantId, BigDecimal amount, String currency,
                       TransactionType type, TransactionStatus status, String originalPaymentId,
                       String scenarioId, java.util.UUID runId, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.originalPaymentId = originalPaymentId;
        this.scenarioId = scenarioId;
        this.runId = runId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(String originalPaymentId) { this.originalPaymentId = originalPaymentId; }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public java.util.UUID getRunId() { return runId; }
    public void setRunId(java.util.UUID runId) { this.runId = runId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
