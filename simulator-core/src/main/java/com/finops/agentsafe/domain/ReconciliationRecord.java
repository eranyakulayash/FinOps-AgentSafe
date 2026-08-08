package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.MatchStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_records")
public class ReconciliationRecord {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "settlement_line_item_id")
    private UUID settlementLineItemId;

    @Column(name = "discrepancy_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discrepancyAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus;

    @Column(name = "reconciled_at", nullable = false)
    private Instant reconciledAt;

    public ReconciliationRecord() {}

    public ReconciliationRecord(UUID id, String transactionId, UUID settlementLineItemId, BigDecimal discrepancyAmount, MatchStatus matchStatus) {
        this.id = id;
        this.transactionId = transactionId;
        this.settlementLineItemId = settlementLineItemId;
        this.discrepancyAmount = discrepancyAmount;
        this.matchStatus = matchStatus;
        this.reconciledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public UUID getSettlementLineItemId() { return settlementLineItemId; }
    public void setSettlementLineItemId(UUID settlementLineItemId) { this.settlementLineItemId = settlementLineItemId; }

    public BigDecimal getDiscrepancyAmount() { return discrepancyAmount; }
    public void setDiscrepancyAmount(BigDecimal discrepancyAmount) { this.discrepancyAmount = discrepancyAmount; }

    public MatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(MatchStatus matchStatus) { this.matchStatus = matchStatus; }

    public Instant getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Instant reconciledAt) { this.reconciledAt = reconciledAt; }
}
