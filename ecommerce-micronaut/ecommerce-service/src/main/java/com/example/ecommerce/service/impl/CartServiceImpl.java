package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.AddToCartRequest;
import com.example.ecommerce.common.dto.CartDto;
import com.example.ecommerce.common.dto.CartDto.CartItemDto;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Cart;
import com.example.ecommerce.domain.entity.CartItem;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.repository.CartRepository;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.service.ReservationService;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of CartService.
 * Manages shopping cart with stock reservation.
 */
@Singleton
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ReservationService reservationService;

    public CartServiceImpl(CartRepository cartRepository,
                           ProductRepository productRepository,
                           ReservationService reservationService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.reservationService = reservationService;
    }

    @Override
    public CartDto getCart(String customerId) {
        return cartRepository.findByCustomerId(customerId)
                .map(this::toDto)
                .orElse(new CartDto(null, customerId, Collections.emptyList(),
                        java.math.BigDecimal.ZERO, null));
    }

    @Override
    public CartDto addItem(String customerId, AddToCartRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product", request.productId()));

        // Reserve stock (this reduces available stock immediately)
        reservationService.reserveStock(customerId, request.productId(), request.quantity());

        Cart cart = getOrCreateCart(customerId);
        cart.addItem(product.getId(), product.getName(), request.quantity(), product.getPrice());
        cartRepository.save(cart);

        return toDto(cart);
    }

    @Override
    public CartDto removeItem(String customerId, String productId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Cart", customerId));

        cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));

        // Cancel reservation (restores stock)
        reservationService.cancelReservation(customerId, productId);

        cart.removeItem(productId);
        cartRepository.save(cart);

        return toDto(cart);
    }

    @Override
    public CartDto updateItemQuantity(String customerId, String productId, int quantity) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Cart", customerId));

        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("CartItem", productId));

        // Cancel old reservation and create new one with updated quantity
        reservationService.cancelReservation(customerId, productId);
        reservationService.reserveStock(customerId, productId, quantity);

        item.setQuantity(quantity);
        cartRepository.save(cart);

        return toDto(cart);
    }

    @Override
    public void clearCart(String customerId) {
        cartRepository.findByCustomerId(customerId).ifPresent(cart -> {
            // Cancel all reservations (restores stock)
            reservationService.cancelAllReservations(customerId);
            cart.clear();
            cartRepository.save(cart);
        });
    }

    private Cart getOrCreateCart(String customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Cart cart = new Cart(customerId);
                    return cartRepository.save(cart);
                });
    }

    private CartDto toDto(Cart cart) {
        List<CartItemDto> items = cart.getItems().stream()
                .map(item -> new CartItemDto(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new CartDto(
                cart.getId(),
                cart.getCustomerId(),
                items,
                cart.calculateTotal(),
                cart.getUpdatedAt()
        );
    }
}
