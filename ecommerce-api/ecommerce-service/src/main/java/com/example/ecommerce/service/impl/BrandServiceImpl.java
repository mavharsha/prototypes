package com.example.ecommerce.service.impl;

import com.example.ecommerce.common.dto.*;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.NotFoundException;
import com.example.ecommerce.domain.entity.Brand;
import com.example.ecommerce.domain.repository.BrandRepository;
import com.example.ecommerce.service.BrandService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    public BrandServiceImpl(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Override
    public BrandDto createBrand(CreateBrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Brand", "name", request.name());
        }
        Brand brand = new Brand(request.name(), request.description());
        brand.setLogoUrl(request.logoUrl());
        return toDto(brandRepository.save(brand));
    }

    @Override
    public BrandDto getBrand(String id) {
        return toDto(brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Brand", id)));
    }

    @Override
    public List<BrandDto> getAllBrands() {
        return brandRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<BrandDto> getActiveBrands() {
        return brandRepository.findByActive(true).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<BrandDto> searchBrands(String query) {
        return brandRepository.searchByName(query).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public BrandDto updateBrand(String id, UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Brand", id));
        if (request.name() != null) brand.setName(request.name());
        if (request.description() != null) brand.setDescription(request.description());
        if (request.logoUrl() != null) brand.setLogoUrl(request.logoUrl());
        if (request.active() != null) brand.setActive(request.active());
        return toDto(brandRepository.save(brand));
    }

    @Override
    public void deleteBrand(String id) {
        if (brandRepository.findById(id).isEmpty()) {
            throw new NotFoundException("Brand", id);
        }
        brandRepository.deleteById(id);
    }

    private BrandDto toDto(Brand b) {
        return new BrandDto(b.getId(), b.getName(), b.getDescription(), b.getLogoUrl(),
                b.isActive(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
