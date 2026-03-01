package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.ProductDto;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.repository.ProductRepository;
import com.example.ecommerce.service.ProductService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ProductService.
 * Handles mapping between DTOs and entities.
 */
@Singleton
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductDto createProduct(ProductDto dto) {
        Product product = new Product(
                dto.name(),
                dto.description(),
                dto.price(),
                dto.stock()
        );
        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Override
    public ProductDto getProduct(String id) {
        return productRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Override
    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDto updateProduct(String id, ProductDto dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));

        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setPrice(dto.price());
        product.setStock(dto.stock());

        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Override
    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException("Product", id);
        }
        productRepository.deleteById(id);
    }

    private ProductDto toDto(Product product) {
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock()
        );
    }
}
