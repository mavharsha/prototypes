package com.example.ecommerce.domain.entity;

import java.time.Instant;
import java.util.UUID;

public class Brand {
    private String id;
    private String name;
    private String description;
    private String logoUrl;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Brand() {
        this.id = UUID.randomUUID().toString();
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Brand(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; this.updatedAt = Instant.now(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
