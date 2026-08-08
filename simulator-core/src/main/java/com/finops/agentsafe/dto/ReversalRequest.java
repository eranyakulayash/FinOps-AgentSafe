package com.finops.agentsafe.dto;

import java.math.BigDecimal;

public class ReversalRequest {
    private String reversalTxId;
    private String idempotencyKey;
    private String originalPaymentId;
    private BigDecimal reversalAmount;
    private String requestedBy;

    public String getReversalTxId() { return reversalTxId; }
    public void setReversalTxId(String reversalTxId) { this.reversalTxId = reversalTxId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(String originalPaymentId) { this.originalPaymentId = originalPaymentId; }

    public BigDecimal getReversalAmount() { return reversalAmount; }
    public void setReversalAmount(BigDecimal reversalAmount) { this.reversalAmount = reversalAmount; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
}
