package com.example.ecommerce.domain.entity;

import java.util.UUID;

public class ProductImage {
    private String id;
    private String url;
    private String altText;
    private int displayOrder;
    private boolean primary;

    public ProductImage() {
        this.id = UUID.randomUUID().toString();
    }

    public ProductImage(String url, String altText, int displayOrder, boolean primary) {
        this();
        this.url = url;
        this.altText = altText;
        this.displayOrder = displayOrder;
        this.primary = primary;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
