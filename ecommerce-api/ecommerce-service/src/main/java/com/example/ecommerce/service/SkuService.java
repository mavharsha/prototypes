package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.*;
import java.util.List;

public interface SkuService {
    SkuDto createSku(String productId, CreateSkuRequest request);
    SkuDto getSku(String id);
    SkuDto getSkuByCode(String skuCode);
    List<SkuDto> getSkusByProduct(String productId);
    List<SkuDto> getActiveSkusByProduct(String productId);
    SkuDto updateSku(String id, UpdateSkuRequest request);
    SkuDto updateStock(String skuId, int quantity);
    SkuDto adjustStock(String skuId, int adjustment);
    boolean checkAvailability(String skuCode, int quantity);
    void deleteSku(String id);
}
