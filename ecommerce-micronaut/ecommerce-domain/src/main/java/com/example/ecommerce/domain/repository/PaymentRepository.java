package com.example.ecommerce.domain.repository;

import com.example.ecommerce.domain.entity.Payment;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Payment persistence.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(String id);

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByCustomerId(String customerId);
}
