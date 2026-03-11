package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.CheckoutRequest;
import com.example.ecommerce.common.dto.CheckoutResponse;

/**
 * Service interface for checkout operations.
 */
public interface CheckoutService {

    CheckoutResponse checkout(String customerId, CheckoutRequest request);
}
