package com.finops.agentsafe.domain;

import com.finops.agentsafe.enums.SettlementStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatch {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "file_reference", nullable = false)
    private String fileReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_gross_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalGrossAmount;

    @Column(name = "total_fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFeeAmount;

    @Column(name = "total_net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalNetAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SettlementLineItem> lineItems = new ArrayList<>();

    public SettlementBatch() {}

    public SettlementBatch(UUID id, UUID merchantId, String fileReference, String notes, BigDecimal totalGrossAmount, BigDecimal totalFeeAmount, BigDecimal totalNetAmount, SettlementStatus status) {
        this.id = id;
        this.merchantId = merchantId;
        this.fileReference = fileReference;
        this.notes = notes;
        this.totalGrossAmount = totalGrossAmount;
        this.totalFeeAmount = totalFeeAmount;
        this.totalNetAmount = totalNetAmount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public String getFileReference() { return fileReference; }
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getTotalGrossAmount() { return totalGrossAmount; }
    public void setTotalGrossAmount(BigDecimal totalGrossAmount) { this.totalGrossAmount = totalGrossAmount; }

    public BigDecimal getTotalFeeAmount() { return totalFeeAmount; }
    public void setTotalFeeAmount(BigDecimal totalFeeAmount) { this.totalFeeAmount = totalFeeAmount; }

    public BigDecimal getTotalNetAmount() { return totalNetAmount; }
    public void setTotalNetAmount(BigDecimal totalNetAmount) { this.totalNetAmount = totalNetAmount; }

    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<SettlementLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<SettlementLineItem> lineItems) { this.lineItems = lineItems; }
}
