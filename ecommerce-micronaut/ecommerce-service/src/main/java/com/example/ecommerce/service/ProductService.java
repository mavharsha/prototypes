package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.ProductDto;

import java.util.List;

/**
 * Service interface for product operations.
 */
public interface ProductService {

    ProductDto createProduct(ProductDto productDto);

    ProductDto getProduct(String id);

    List<ProductDto> getAllProducts();

    ProductDto updateProduct(String id, ProductDto productDto);

    void deleteProduct(String id);
}
