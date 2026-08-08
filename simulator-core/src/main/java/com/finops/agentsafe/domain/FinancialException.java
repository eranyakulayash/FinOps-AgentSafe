package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.ExceptionType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_exceptions")
public class FinancialException {

    @Id
    private UUID id;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false)
    private ExceptionType exceptionType;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FinancialException() {}

    public FinancialException(UUID id, String transactionId, UUID batchId, ExceptionType exceptionType, String severity, String status, String details) {
        this.id = id;
        this.transactionId = transactionId;
        this.batchId = batchId;
        this.exceptionType = exceptionType;
        this.severity = severity;
        this.status = status;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public ExceptionType getExceptionType() { return exceptionType; }
    public void setExceptionType(ExceptionType exceptionType) { this.exceptionType = exceptionType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
