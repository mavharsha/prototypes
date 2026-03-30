package com.example.ecommerce.service;

import com.example.ecommerce.common.dto.CalculatePriceRequest;
import com.example.ecommerce.common.dto.PriceBreakdownDto;

public interface PricingService {
    PriceBreakdownDto calculatePrice(CalculatePriceRequest request);
}
