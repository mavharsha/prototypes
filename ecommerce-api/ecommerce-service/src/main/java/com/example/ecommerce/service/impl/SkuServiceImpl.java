package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.entity.Sku;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.domain.repository.SkuRepository;
import com.example.ecommerce.service.SkuService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class SkuServiceImpl implements SkuService {

    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;

    public SkuServiceImpl(SkuRepository skuRepository, ProductRepository productRepository) {
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
    }

    @Override
    public SkuDto createSku(String productId, CreateSkuRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        if (skuRepository.existsBySkuCode(request.skuCode())) {
            throw new DuplicateResourceException("SKU", "skuCode", request.skuCode());
        }
        Sku sku = new Sku(productId, request.skuCode(), request.stockQuantity());
        sku.setPriceOverride(request.priceOverride());
        if (request.lowStockThreshold() != null) sku.setLowStockThreshold(request.lowStockThreshold());
        if (request.attributes() != null) sku.setAttributes(request.attributes());
        sku.setBarcode(request.barcode());
        sku.setWeight(request.weight());
        sku.setDimensionLength(request.dimensionLength());
        sku.setDimensionWidth(request.dimensionWidth());
        sku.setDimensionHeight(request.dimensionHeight());
        return toDto(skuRepository.save(sku), product.getBasePrice());
    }

    @Override
    public SkuDto getSku(String id) {
        Sku sku = skuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SKU", id));
        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new NotFoundException("Product", sku.getProductId()));
        return toDto(sku, product.getBasePrice());
    }

    @Override
    public SkuDto getSkuByCode(String skuCode) {
        Sku sku = skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new NotFoundException("SKU", skuCode));
        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new NotFoundException("Product", sku.getProductId()));
        return toDto(sku, product.getBasePrice());
    }

    @Override
    public List<SkuDto> getSkusByProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        return skuRepository.findByProductId(productId).stream()
                .map(s -> toDto(s, product.getBasePrice())).collect(Collectors.toList());
    }

    @Override
    public List<SkuDto> getActiveSkusByProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        return skuRepository.findByProductIdAndActive(productId, true).stream()
                .map(s -> toDto(s, product.getBasePrice())).collect(Collectors.toList());
    }

    @Override
    public SkuDto updateSku(String id, UpdateSkuRequest request) {
        Sku sku = skuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SKU", id));
        if (request.priceOverride() != null) sku.setPriceOverride(request.priceOverride());
        if (request.lowStockThreshold() != null) sku.setLowStockThreshold(request.lowStockThreshold());
        if (request.attributes() != null) sku.setAttributes(request.attributes());
        if (request.barcode() != null) sku.setBarcode(request.barcode());
        if (request.weight() != null) sku.setWeight(request.weight());
        if (request.dimensionLength() != null) sku.setDimensionLength(request.dimensionLength());
        if (request.dimensionWidth() != null) sku.setDimensionWidth(request.dimensionWidth());
        if (request.dimensionHeight() != null) sku.setDimensionHeight(request.dimensionHeight());
        if (request.active() != null) sku.setActive(request.active());
        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new NotFoundException("Product", sku.getProductId()));
        return toDto(skuRepository.save(sku), product.getBasePrice());
    }

    @Override
    public SkuDto updateStock(String skuId, int quantity) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new NotFoundException("SKU", skuId));
        sku.setStockQuantity(quantity);
        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new NotFoundException("Product", sku.getProductId()));
        return toDto(skuRepository.save(sku), product.getBasePrice());
    }

    @Override
    public SkuDto adjustStock(String skuId, int adjustment) {
        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new NotFoundException("SKU", skuId));
        if (adjustment > 0) {
            sku.addStock(adjustment);
        } else if (adjustment < 0) {
            sku.reduceStock(Math.abs(adjustment));
        }
        Product product = productRepository.findById(sku.getProductId())
                .orElseThrow(() -> new NotFoundException("Product", sku.getProductId()));
        return toDto(skuRepository.save(sku), product.getBasePrice());
    }

    @Override
    public boolean checkAvailability(String skuCode, int quantity) {
        return skuRepository.findBySkuCode(skuCode)
                .map(sku -> sku.hasStock(quantity))
                .orElse(false);
    }

    @Override
    public void deleteSku(String id) {
        if (skuRepository.findById(id).isEmpty()) {
            throw new NotFoundException("SKU", id);
        }
        skuRepository.deleteById(id);
    }

    private SkuDto toDto(Sku s, java.math.BigDecimal basePrice) {
        return new SkuDto(s.getId(), s.getProductId(), s.getSkuCode(), s.getPriceOverride(),
                s.getEffectivePrice(basePrice), s.getStockQuantity(), s.getLowStockThreshold(),
                s.getStockStatus(), s.getAttributes(), s.getBarcode(), s.getWeight(),
                s.getDimensionLength(), s.getDimensionWidth(), s.getDimensionHeight(),
                s.isActive(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
