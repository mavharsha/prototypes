package com.example.ecommerce.domain.entity;

import java.time.Instant;
import java.util.UUID;

public class Category {
    private String id;
    private String name;
    private String description;
    private String slug;
    private String parentId;
    private boolean active;
    private int displayOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public Category() {
        this.id = UUID.randomUUID().toString();
        this.active = true;
        this.displayOrder = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Category(String name, String description) {
        this();
        this.name = name;
        this.description = description;
        this.slug = generateSlug(name);
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public static String generateSlug(String name) {
        if (name == null) return null;
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.slug = generateSlug(name); this.updatedAt = Instant.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; this.updatedAt = Instant.now(); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
