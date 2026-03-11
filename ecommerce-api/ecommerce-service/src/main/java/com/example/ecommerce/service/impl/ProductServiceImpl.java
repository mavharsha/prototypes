package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.enums.ProductStatus;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.entity.ProductAttribute;
import com.example.ecommerce.domain.entity.ProductImage;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.service.ProductService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductDto createProduct(CreateProductRequest request) {
        Product product = new Product(request.name(), request.description(), request.basePrice());
        if (productRepository.existsBySlug(product.getSlug())) {
            throw new DuplicateResourceException("Product", "slug", product.getSlug());
        }
        product.setBrandId(request.brandId());
        product.setCategoryId(request.categoryId());
        if (request.attributes() != null) {
            request.attributes().forEach(a -> product.addAttribute(new ProductAttribute(a.key(), a.value())));
        }
        product.setSeoTitle(request.seoTitle());
        product.setSeoDescription(request.seoDescription());
        product.setSeoKeywords(request.seoKeywords());
        return toDto(productRepository.save(product));
    }

    @Override
    public ProductDto getProduct(String id) {
        return toDto(productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id)));
    }

    @Override
    public ProductDto getProductBySlug(String slug) {
        return toDto(productRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Product", slug)));
    }

    @Override
    public ProductDto getProductWithSkus(String id) {
        return getProduct(id);
    }

    @Override
    public List<ProductSummaryDto> getAllProducts() {
        return productRepository.findAll().stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    @Override
    public List<ProductSummaryDto> getProductsByCategory(String categoryId) {
        return productRepository.findByCategoryId(categoryId).stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    @Override
    public List<ProductSummaryDto> getProductsByBrand(String brandId) {
        return productRepository.findByBrandId(brandId).stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    @Override
    public ProductDto updateProduct(String id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.brandId() != null) product.setBrandId(request.brandId());
        if (request.categoryId() != null) product.setCategoryId(request.categoryId());
        if (request.basePrice() != null) product.setBasePrice(request.basePrice());
        if (request.attributes() != null) {
            product.getAttributes().clear();
            request.attributes().forEach(a -> product.addAttribute(new ProductAttribute(a.key(), a.value())));
        }
        if (request.seoTitle() != null) product.setSeoTitle(request.seoTitle());
        if (request.seoDescription() != null) product.setSeoDescription(request.seoDescription());
        if (request.seoKeywords() != null) product.setSeoKeywords(request.seoKeywords());
        return toDto(productRepository.save(product));
    }

    @Override
    public ProductDto updateProductStatus(String id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        product.setStatus(status);
        return toDto(productRepository.save(product));
    }

    @Override
    public ProductDto addImage(String id, AddImageRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        ProductImage image = new ProductImage(request.url(), request.altText(),
                request.displayOrder() != null ? request.displayOrder() : product.getImages().size(),
                request.primary() != null ? request.primary() : false);
        product.addImage(image);
        return toDto(productRepository.save(product));
    }

    @Override
    public ProductDto removeImage(String productId, String imageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        product.removeImage(imageId);
        return toDto(productRepository.save(product));
    }

    @Override
    public ProductDto setPrimaryImage(String productId, String imageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        product.setPrimaryImage(imageId);
        return toDto(productRepository.save(product));
    }

    @Override
    public void deleteProduct(String id) {
        if (productRepository.findById(id).isEmpty()) {
            throw new NotFoundException("Product", id);
        }
        productRepository.deleteById(id);
    }

    private ProductDto toDto(Product p) {
        List<ProductImageDto> imageDtos = p.getImages().stream()
                .map(i -> new ProductImageDto(i.getId(), i.getUrl(), i.getAltText(), i.getDisplayOrder(), i.isPrimary()))
                .collect(Collectors.toList());
        List<ProductAttributeDto> attrDtos = p.getAttributes().stream()
                .map(a -> new ProductAttributeDto(a.getKey(), a.getValue()))
                .collect(Collectors.toList());
        return new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getSlug(),
                p.getBrandId(), p.getCategoryId(), p.getStatus(), p.getBasePrice(),
                imageDtos, attrDtos, p.getSeoTitle(), p.getSeoDescription(), p.getSeoKeywords(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private ProductSummaryDto toSummaryDto(Product p) {
        return new ProductSummaryDto(p.getId(), p.getName(), p.getSlug(),
                p.getBrandId(), p.getCategoryId(), p.getStatus(), p.getBasePrice(), p.getPrimaryImageUrl());
    }
}
