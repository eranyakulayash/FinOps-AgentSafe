package com.finops.agentsafe.dto;

import java.math.BigDecimal;

public class RefundRequest {
    private String refundTxId;
    private String idempotencyKey;
    private String originalPaymentId;
    private BigDecimal refundAmount;

    public RefundRequest() {}

    public RefundRequest(String refundTxId, String idempotencyKey, String originalPaymentId, BigDecimal refundAmount) {
        this.refundTxId = refundTxId;
        this.idempotencyKey = idempotencyKey;
        this.originalPaymentId = originalPaymentId;
        this.refundAmount = refundAmount;
    }

    public String getRefundTxId() { return refundTxId; }
    public void setRefundTxId(String refundTxId) { this.refundTxId = refundTxId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(String originalPaymentId) { this.originalPaymentId = originalPaymentId; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
}
