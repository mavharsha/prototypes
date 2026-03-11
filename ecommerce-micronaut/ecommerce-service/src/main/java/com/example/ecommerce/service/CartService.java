package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.AddToCartRequest;
import com.example.ecommerce.common.dto.CartDto;

/**
 * Service interface for cart operations.
 */
public interface CartService {

    CartDto getCart(String customerId);

    CartDto addItem(String customerId, AddToCartRequest request);

    CartDto removeItem(String customerId, String productId);

    CartDto updateItemQuantity(String customerId, String productId, int quantity);

    void clearCart(String customerId);
}
