package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.PaymentDto;
import com.example.ecommerce.common.dto.PaymentRequest;

import java.math.BigDecimal;

/**
 * Service interface for payment processing.
 */
public interface PaymentService {

    PaymentDto processPayment(String orderId, String customerId, BigDecimal amount, PaymentRequest paymentRequest);

    PaymentDto getPayment(String paymentId);

    PaymentDto getPaymentByOrderId(String orderId);
}
