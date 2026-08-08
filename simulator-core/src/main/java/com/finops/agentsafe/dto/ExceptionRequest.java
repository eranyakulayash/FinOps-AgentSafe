package com.finops.agentsafe.dto;

import com.finops.agentsafe.enums.ExceptionType;
import java.util.UUID;

public class ExceptionRequest {
    private String transactionId;
    private UUID batchId;
    private ExceptionType exceptionType;
    private String severity;
    private String details;

    public ExceptionRequest() {}

    public ExceptionRequest(String transactionId, UUID batchId, ExceptionType exceptionType, String severity, String details) {
        this.transactionId = transactionId;
        this.batchId = batchId;
        this.exceptionType = exceptionType;
        this.severity = severity;
        this.details = details;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public ExceptionType getExceptionType() { return exceptionType; }
    public void setExceptionType(ExceptionType exceptionType) { this.exceptionType = exceptionType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
