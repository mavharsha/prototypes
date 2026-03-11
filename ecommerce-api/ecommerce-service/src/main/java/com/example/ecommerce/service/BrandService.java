package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.*;
import java.util.List;

public interface BrandService {
    BrandDto createBrand(CreateBrandRequest request);
    BrandDto getBrand(String id);
    List<BrandDto> getAllBrands();
    List<BrandDto> getActiveBrands();
    List<BrandDto> searchBrands(String query);
    BrandDto updateBrand(String id, UpdateBrandRequest request);
    void deleteBrand(String id);
}
