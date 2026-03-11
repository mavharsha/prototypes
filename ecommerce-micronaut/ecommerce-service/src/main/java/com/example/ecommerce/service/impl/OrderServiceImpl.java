package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.OrderDto;
import com.example.ecommerce.common.dto.OrderDto.OrderItemDto;
import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Order;
import com.example.ecommerce.domain.entity.OrderItem;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.repository.OrderRepository;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.service.OrderService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of OrderService.
 * Orchestrates order creation with stock validation.
 */
@Singleton
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderServiceImpl(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public OrderDto createOrder(OrderDto dto) {
        Order order = new Order(dto.customerId());

        // Validate stock and add items
        for (OrderItemDto itemDto : dto.items()) {
            Product product = productRepository.findById(itemDto.productId())
                    .orElseThrow(() -> new NotFoundException("Product", itemDto.productId()));

            if (!product.hasStock(itemDto.quantity())) {
                throw new InsufficientStockException(
                        product.getId(),
                        itemDto.quantity(),
                        product.getStock()
                );
            }

            // Reduce stock
            product.reduceStock(itemDto.quantity());
            productRepository.save(product);

            // Add to order
            order.addItem(product, itemDto.quantity());
        }

        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    @Override
    public OrderDto getOrder(String id) {
        return orderRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Order", id));
    }

    @Override
    public List<OrderDto> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDto> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public OrderDto confirmOrder(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));

        order.confirm();
        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    @Override
    public OrderDto cancelOrder(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));

        // Restore stock for cancelled items
        for (OrderItem item : order.getItems()) {
            productRepository.findById(item.getProductId())
                    .ifPresent(product -> {
                        product.addStock(item.getQuantity());
                        productRepository.save(product);
                    });
        }

        order.cancel();
        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    @Override
    public OrderDto createOrderFromCart(OrderDto dto) {
        Order order = new Order(dto.customerId());

        for (OrderItemDto itemDto : dto.items()) {
            OrderItem item = new OrderItem(
                    itemDto.productId(),
                    itemDto.productName(),
                    itemDto.quantity(),
                    itemDto.unitPrice()
            );
            order.getItems().add(item);
        }

        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> items = order.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .collect(Collectors.toList());

        return new OrderDto(
                order.getId(),
                order.getCustomerId(),
                items,
                order.calculateTotal(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
