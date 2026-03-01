package com.example.ecommerce.common.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Can be used across all modules.
 */
public class NotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public NotFoundException(String resourceType, String resourceId) {
        super(String.format("%s not found with id: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
