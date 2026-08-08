package com.finops.agentsafe.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentRequest {
    private String transactionId;
    private String idempotencyKey;
    private UUID merchantId;
    private BigDecimal amount;
    private String currency;

    public PaymentRequest() {}

    public PaymentRequest(String transactionId, String idempotencyKey, UUID merchantId, BigDecimal amount, String currency) {
        this.transactionId = transactionId;
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
