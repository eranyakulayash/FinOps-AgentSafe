package com.finops.agentsafe.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "fee_rate_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRatePercentage;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Merchant() {}

    public Merchant(UUID id, String name, BigDecimal feeRatePercentage, String status) {
        this.id = id;
        this.name = name;
        this.feeRatePercentage = feeRatePercentage;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getFeeRatePercentage() { return feeRatePercentage; }
    public void setFeeRatePercentage(BigDecimal feeRatePercentage) { this.feeRatePercentage = feeRatePercentage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
