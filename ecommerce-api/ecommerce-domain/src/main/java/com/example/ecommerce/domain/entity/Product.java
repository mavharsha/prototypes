package com.example.ecommerce.domain.entity;

import com.example.ecommerce.common.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Product {
    private String id;
    private String name;
    private String description;
    private String slug;
    private String brandId;
    private String categoryId;
    private ProductStatus status;
    private BigDecimal basePrice;
    private List<ProductImage> images;
    private List<ProductAttribute> attributes;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private Instant createdAt;
    private Instant updatedAt;

    public Product() {
        this.id = UUID.randomUUID().toString();
        this.status = ProductStatus.DRAFT;
        this.images = new ArrayList<>();
        this.attributes = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Product(String name, String description, BigDecimal basePrice) {
        this();
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.slug = Category.generateSlug(name);
    }

    // Business logic
    public void activate() {
        this.status = ProductStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
        this.updatedAt = Instant.now();
    }

    public void addImage(ProductImage image) {
        if (images.isEmpty()) {
            image.setPrimary(true);
        }
        images.add(image);
        this.updatedAt = Instant.now();
    }

    public void removeImage(String imageId) {
        boolean wasPrimary = images.stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .map(ProductImage::isPrimary)
                .orElse(false);
        images.removeIf(img -> img.getId().equals(imageId));
        if (wasPrimary && !images.isEmpty()) {
            images.get(0).setPrimary(true);
        }
        this.updatedAt = Instant.now();
    }

    public void setPrimaryImage(String imageId) {
        images.forEach(img -> img.setPrimary(img.getId().equals(imageId)));
        this.updatedAt = Instant.now();
    }

    public void addAttribute(ProductAttribute attribute) {
        attributes.removeIf(a -> a.getKey().equals(attribute.getKey()));
        attributes.add(attribute);
        this.updatedAt = Instant.now();
    }

    public void removeAttribute(String key) {
        attributes.removeIf(a -> a.getKey().equals(key));
        this.updatedAt = Instant.now();
    }

    public String getPrimaryImageUrl() {
        return images.stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.slug = Category.generateSlug(name); this.updatedAt = Instant.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = Instant.now(); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getBrandId() { return brandId; }
    public void setBrandId(String brandId) { this.brandId = brandId; this.updatedAt = Instant.now(); }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; this.updatedAt = Instant.now(); }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; this.updatedAt = Instant.now(); }
    public List<ProductImage> getImages() { return images; }
    public void setImages(List<ProductImage> images) { this.images = images; }
    public List<ProductAttribute> getAttributes() { return attributes; }
    public void setAttributes(List<ProductAttribute> attributes) { this.attributes = attributes; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
    public String getSeoKeywords() { return seoKeywords; }
    public void setSeoKeywords(String seoKeywords) { this.seoKeywords = seoKeywords; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
