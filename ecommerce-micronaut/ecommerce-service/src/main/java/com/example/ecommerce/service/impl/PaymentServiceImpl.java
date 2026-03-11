package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.PaymentDto;
import com.example.ecommerce.common.dto.PaymentRequest;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.common.exception.PaymentDeclinedException;
import com.example.ecommerce.domain.entity.Payment;
import com.example.ecommerce.domain.repository.PaymentRepository;
import com.example.ecommerce.service.PaymentService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Mock implementation of PaymentService.
 * Simulates card payment processing.
 */
@Singleton
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentDto processPayment(String orderId, String customerId, BigDecimal amount, PaymentRequest request) {
        String masked = "****" + request.cardNumber().substring(12);

        Payment payment = new Payment(orderId, customerId, amount, masked);

        // Mock decline: cards starting with "0000" are declined
        if (request.cardNumber().startsWith("0000")) {
            payment.decline();
            paymentRepository.save(payment);
            LOG.warn("Payment {} declined for order {} - card declined by issuer (mock)", payment.getId(), orderId);
            throw new PaymentDeclinedException("Card declined by issuer (mock)");
        }

        payment.approve();
        Payment saved = paymentRepository.save(payment);

        LOG.info("Payment {} approved for order {} - amount: {} - card: {}",
                saved.getId(), orderId, amount, masked);

        return toDto(saved);
    }

    @Override
    public PaymentDto getPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId));
    }

    @Override
    public PaymentDto getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Payment for order", orderId));
    }

    private PaymentDto toDto(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getMaskedCardNumber(),
                payment.getCreatedAt()
        );
    }
}
