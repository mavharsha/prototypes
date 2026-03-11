package com.example.ecommerce.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PriceHistory {
    private String id;
    private String entityType;
    private String entityId;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private String changedBy;
    private String reason;
    private Instant changedAt;

    public PriceHistory() {
        this.id = UUID.randomUUID().toString();
        this.changedAt = Instant.now();
    }

    public PriceHistory(String entityType, String entityId, BigDecimal oldPrice, BigDecimal newPrice, String changedBy, String reason) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public void setOldPrice(BigDecimal oldPrice) { this.oldPrice = oldPrice; }
    public BigDecimal getNewPrice() { return newPrice; }
    public void setNewPrice(BigDecimal newPrice) { this.newPrice = newPrice; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
