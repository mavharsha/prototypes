package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.OrderDto;

import java.util.List;

/**
 * Service interface for order operations.
 */
public interface OrderService {

    OrderDto createOrder(OrderDto orderDto);

    OrderDto getOrder(String id);

    List<OrderDto> getAllOrders();

    List<OrderDto> getOrdersByCustomer(String customerId);

    OrderDto confirmOrder(String id);

    OrderDto cancelOrder(String id);

    /**
     * Creates an order from cart items without reducing stock.
     * Stock was already reserved by the reservation system.
     */
    OrderDto createOrderFromCart(OrderDto orderDto);
}
