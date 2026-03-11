package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.enums.ProductStatus;
import java.util.List;

public interface ProductService {
    ProductDto createProduct(CreateProductRequest request);
    ProductDto getProduct(String id);
    ProductDto getProductBySlug(String slug);
    ProductDto getProductWithSkus(String id);
    List<ProductSummaryDto> getAllProducts();
    List<ProductSummaryDto> getProductsByCategory(String categoryId);
    List<ProductSummaryDto> getProductsByBrand(String brandId);
    ProductDto updateProduct(String id, UpdateProductRequest request);
    ProductDto updateProductStatus(String id, ProductStatus status);
    ProductDto addImage(String id, AddImageRequest request);
    ProductDto removeImage(String productId, String imageId);
    ProductDto setPrimaryImage(String productId, String imageId);
    void deleteProduct(String id);
}
