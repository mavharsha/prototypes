package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.ProductSearchRequest;
import com.example.ecommerce.common.dto.ProductSearchResponse;

public interface ProductSearchService {
    ProductSearchResponse search(ProductSearchRequest request);
}
