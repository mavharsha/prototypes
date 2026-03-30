package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.enums.ShippingType;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.entity.Sku;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.domain.repository.SkuRepository;
import com.example.ecommerce.service.PricingService;
import com.example.ecommerce.service.config.PricingConfig;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PricingServiceImpl implements PricingService {

    private final PricingConfig pricingConfig;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    public PricingServiceImpl(PricingConfig pricingConfig, ProductRepository productRepository,
                              SkuRepository skuRepository) {
        this.pricingConfig = pricingConfig;
        this.productRepository = productRepository;
        this.skuRepository = skuRepository;
    }

    @Override
    public PriceBreakdownDto calculatePrice(CalculatePriceRequest request) {
        List<PricingLineItemDto> lineItems = new ArrayList<>();

        for (PricingItemRequest item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new NotFoundException("Product", item.productId()));

            final BigDecimal unitPrice;
            final String resolvedSkuId;

            if (item.skuId() != null && !item.skuId().isBlank()) {
                resolvedSkuId = item.skuId();
                Sku sku = skuRepository.findById(resolvedSkuId)
                        .orElseThrow(() -> new NotFoundException("SKU", resolvedSkuId));
                unitPrice = sku.getEffectivePrice(product.getBasePrice());
            } else {
                resolvedSkuId = null;
                unitPrice = product.getBasePrice();
            }

            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));

            lineItems.add(new PricingLineItemDto(
                    item.productId(),
                    resolvedSkuId,
                    product.getName(),
                    unitPrice,
                    item.quantity(),
                    itemSubtotal
            ));
        }

        BigDecimal subtotal = lineItems.stream()
                .map(PricingLineItemDto::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxAmount = subtotal.multiply(pricingConfig.getTaxRate())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingCost = request.shippingType() == ShippingType.PRIORITY
                ? pricingConfig.getPriorityShippingRate()
                : pricingConfig.getStandardShippingRate();

        BigDecimal total = subtotal.add(taxAmount).add(shippingCost);

        return new PriceBreakdownDto(
                lineItems,
                subtotal,
                pricingConfig.getTaxRate(),
                taxAmount,
                request.shippingType(),
                shippingCost,
                total,
                Instant.now()
        );
    }
}
