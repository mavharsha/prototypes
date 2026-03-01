package com.example.ecommerce.api.controller;

import com.example.ecommerce.common.dto.OrderDto;
import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.service.OrderService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

/**
 * REST controller for Order operations.
 * All order operations require authentication.
 */
@Controller("/api/orders")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class OrderController {

    private final OrderService orderService;
    private final AuditLogger auditLogger;

    public OrderController(OrderService orderService, AuditLogger auditLogger) {
        this.orderService = orderService;
        this.auditLogger = auditLogger;
    }

    @Get
    @Secured({"ADMIN"})  // Only admins can see all orders
    public List<OrderDto> getAllOrders(Authentication authentication) {
        auditLogger.logDataAccess(authentication.getName(), "Order", "*", "LIST_ALL");
        return orderService.getAllOrders();
    }

    @Get("/{id}")
    public OrderDto getOrder(@PathVariable String id, Authentication authentication) {
        auditLogger.logDataAccess(authentication.getName(), "Order", id, "READ");
        return orderService.getOrder(id);
    }

    @Get("/customer/{customerId}")
    public List<OrderDto> getOrdersByCustomer(@PathVariable String customerId, Authentication authentication) {
        // Users can only view their own orders (unless admin)
        if (!authentication.getRoles().contains("ADMIN") && !authentication.getName().equals(customerId)) {
            auditLogger.logFailure(authentication.getName(), "LIST", "Order", customerId, "Unauthorized access attempt");
            throw new SecurityException("Cannot view other customer's orders");
        }
        auditLogger.logDataAccess(authentication.getName(), "Order", "customer:" + customerId, "LIST");
        return orderService.getOrdersByCustomer(customerId);
    }

    @Post
    public HttpResponse<OrderDto> createOrder(@Body OrderDto orderDto, Authentication authentication) {
        OrderDto created = orderService.createOrder(orderDto);
        auditLogger.logSuccess(authentication.getName(), "CREATE", "Order", created.id());
        return HttpResponse.created(created);
    }

    @Post("/{id}/confirm")
    public OrderDto confirmOrder(@PathVariable String id, Authentication authentication) {
        OrderDto confirmed = orderService.confirmOrder(id);
        auditLogger.logSuccess(authentication.getName(), "CONFIRM", "Order", id);
        return confirmed;
    }

    @Post("/{id}/cancel")
    public OrderDto cancelOrder(@PathVariable String id, Authentication authentication) {
        OrderDto cancelled = orderService.cancelOrder(id);
        auditLogger.logSuccess(authentication.getName(), "CANCEL", "Order", id);
        return cancelled;
    }
}
