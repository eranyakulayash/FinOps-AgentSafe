package com.finops.agentsafe.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_line_items")
public class SettlementLineItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    @JsonIgnore
    private SettlementBatch batch;

    @Column(name = "external_tx_id", nullable = false)
    private String externalTxId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public SettlementLineItem() {}

    public SettlementLineItem(UUID id, SettlementBatch batch, String externalTxId, BigDecimal amount, BigDecimal fee, BigDecimal netAmount) {
        this.id = id;
        this.batch = batch;
        this.externalTxId = externalTxId;
        this.amount = amount;
        this.fee = fee;
        this.netAmount = netAmount;
        this.processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SettlementBatch getBatch() { return batch; }
    public void setBatch(SettlementBatch batch) { this.batch = batch; }

    public String getExternalTxId() { return externalTxId; }
    public void setExternalTxId(String externalTxId) { this.externalTxId = externalTxId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
