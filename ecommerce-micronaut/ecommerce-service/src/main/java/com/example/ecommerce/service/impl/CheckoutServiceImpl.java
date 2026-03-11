package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.CheckoutRequest;
import com.example.ecommerce.common.dto.CheckoutResponse;
import com.example.ecommerce.common.dto.OrderDto;
import com.example.ecommerce.common.dto.OrderDto.OrderItemDto;
import com.example.ecommerce.common.dto.PaymentDto;
import com.example.ecommerce.common.exception.CartEmptyException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.common.exception.PaymentDeclinedException;
import com.example.ecommerce.common.exception.ReservationExpiredException;
import com.example.ecommerce.domain.entity.Cart;
import com.example.ecommerce.domain.entity.CartItem;
import com.example.ecommerce.domain.entity.Reservation;
import com.example.ecommerce.domain.repository.CartRepository;
import com.example.ecommerce.service.CheckoutService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import com.example.ecommerce.service.ReservationService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of CheckoutService.
 * Orchestrates cart → order → payment flow.
 */
@Singleton
public class CheckoutServiceImpl implements CheckoutService {

    private static final Logger LOG = LoggerFactory.getLogger(CheckoutServiceImpl.class);

    private final CartRepository cartRepository;
    private final ReservationService reservationService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public CheckoutServiceImpl(CartRepository cartRepository,
                               ReservationService reservationService,
                               OrderService orderService,
                               PaymentService paymentService) {
        this.cartRepository = cartRepository;
        this.reservationService = reservationService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @Override
    public CheckoutResponse checkout(String customerId, CheckoutRequest request) {
        // 1. Get cart
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Cart", customerId));

        if (cart.isEmpty()) {
            throw new CartEmptyException(customerId);
        }

        // 2. Verify all reservations are still active
        List<Reservation> activeReservations = reservationService.getActiveReservations(customerId);
        for (CartItem item : cart.getItems()) {
            boolean hasActiveReservation = activeReservations.stream()
                    .anyMatch(r -> r.getProductId().equals(item.getProductId()));
            if (!hasActiveReservation) {
                throw new ReservationExpiredException(item.getProductId());
            }
        }

        // 3. Confirm reservations early to prevent expiry during checkout
        reservationService.confirmReservations(customerId);

        // 4. Build order from cart items
        List<OrderItemDto> orderItems = cart.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .collect(Collectors.toList());

        OrderDto orderDto = new OrderDto(null, customerId, orderItems, null, null, null);
        OrderDto createdOrder = orderService.createOrderFromCart(orderDto);

        // 5. Process payment
        PaymentDto payment;
        try {
            payment = paymentService.processPayment(
                    createdOrder.id(),
                    customerId,
                    createdOrder.totalAmount(),
                    request.payment()
            );
        } catch (PaymentDeclinedException e) {
            // Rollback: cancel the order and restore stock
            orderService.cancelOrder(createdOrder.id());
            throw e;
        }

        // 6. Confirm the order
        OrderDto confirmedOrder = orderService.confirmOrder(createdOrder.id());

        // 7. Clear the cart
        cart.clear();
        cartRepository.save(cart);

        LOG.info("Checkout completed for customer {} - order: {}, payment: {}",
                customerId, confirmedOrder.id(), payment.id());

        return new CheckoutResponse(confirmedOrder, payment, "Order placed successfully");
    }
}
