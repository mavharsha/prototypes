package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Brand;
import com.example.ecommerce.domain.repository.BrandRepository;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemoryBrandRepository implements BrandRepository {

    private final Map<String, Brand> store = new ConcurrentHashMap<>();

    public InMemoryBrandRepository() {
        seedData();
    }

    private void seedData() {
        Brand apple = new Brand("Apple", "Think different");
        apple.setLogoUrl("https://example.com/logos/apple.png");
        Brand samsung = new Brand("Samsung", "Inspire the world, create the future");
        samsung.setLogoUrl("https://example.com/logos/samsung.png");
        Brand sony = new Brand("Sony", "Be moved");
        sony.setLogoUrl("https://example.com/logos/sony.png");
        Brand nike = new Brand("Nike", "Just do it");
        nike.setLogoUrl("https://example.com/logos/nike.png");
        Brand adidas = new Brand("Adidas", "Impossible is nothing");
        adidas.setLogoUrl("https://example.com/logos/adidas.png");

        store.put(apple.getId(), apple);
        store.put(samsung.getId(), samsung);
        store.put(sony.getId(), sony);
        store.put(nike.getId(), nike);
        store.put(adidas.getId(), adidas);
    }

    @Override
    public Brand save(Brand brand) {
        store.put(brand.getId(), brand);
        return brand;
    }

    @Override
    public Optional<Brand> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Brand> findByName(String name) {
        return store.values().stream().filter(b -> name.equalsIgnoreCase(b.getName())).findFirst();
    }

    @Override
    public List<Brand> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Brand> findByActive(boolean active) {
        return store.values().stream().filter(b -> b.isActive() == active).collect(Collectors.toList());
    }

    @Override
    public List<Brand> searchByName(String query) {
        String lowerQuery = query.toLowerCase();
        return store.values().stream()
                .filter(b -> b.getName().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public boolean existsByName(String name) {
        return store.values().stream().anyMatch(b -> name.equalsIgnoreCase(b.getName()));
    }
}
