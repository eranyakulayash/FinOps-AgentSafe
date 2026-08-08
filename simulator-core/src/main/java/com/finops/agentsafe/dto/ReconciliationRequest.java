package com.finops.agentsafe.dto;

import java.util.UUID;

public class ReconciliationRequest {
    private String transactionId;
    private UUID settlementLineItemId;

    public ReconciliationRequest() {}

    public ReconciliationRequest(String transactionId, UUID settlementLineItemId) {
        this.transactionId = transactionId;
        this.settlementLineItemId = settlementLineItemId;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public UUID getSettlementLineItemId() { return settlementLineItemId; }
    public void setSettlementLineItemId(UUID settlementLineItemId) { this.settlementLineItemId = settlementLineItemId; }
}
