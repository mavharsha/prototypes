package com.example.ecommerce.repository;

import com.example.ecommerce.domain.entity.Category;
import com.example.ecommerce.domain.repository.CategoryRepository;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<String, Category> store = new ConcurrentHashMap<>();

    public InMemoryCategoryRepository() {
        seedData();
    }

    private void seedData() {
        // Root categories
        Category electronics = new Category("Electronics", "Electronic devices and gadgets");
        Category clothing = new Category("Clothing", "Fashion and apparel");
        Category homeKitchen = new Category("Home & Kitchen", "Home furnishing and kitchen essentials");

        store.put(electronics.getId(), electronics);
        store.put(clothing.getId(), clothing);
        store.put(homeKitchen.getId(), homeKitchen);

        // Electronics subcategories
        Category laptops = new Category("Laptops", "Portable computers");
        laptops.setParentId(electronics.getId());
        laptops.setDisplayOrder(1);
        Category smartphones = new Category("Smartphones", "Mobile phones");
        smartphones.setParentId(electronics.getId());
        smartphones.setDisplayOrder(2);
        Category headphones = new Category("Headphones", "Audio headphones and earbuds");
        headphones.setParentId(electronics.getId());
        headphones.setDisplayOrder(3);

        store.put(laptops.getId(), laptops);
        store.put(smartphones.getId(), smartphones);
        store.put(headphones.getId(), headphones);

        // Clothing subcategories
        Category mens = new Category("Men's", "Men's clothing");
        mens.setParentId(clothing.getId());
        mens.setDisplayOrder(1);
        Category womens = new Category("Women's", "Women's clothing");
        womens.setParentId(clothing.getId());
        womens.setDisplayOrder(2);
        Category kids = new Category("Kids", "Children's clothing");
        kids.setParentId(clothing.getId());
        kids.setDisplayOrder(3);

        store.put(mens.getId(), mens);
        store.put(womens.getId(), womens);
        store.put(kids.getId(), kids);

        // Home & Kitchen subcategories
        Category furniture = new Category("Furniture", "Home furniture");
        furniture.setParentId(homeKitchen.getId());
        furniture.setDisplayOrder(1);
        Category appliances = new Category("Appliances", "Kitchen and home appliances");
        appliances.setParentId(homeKitchen.getId());
        appliances.setDisplayOrder(2);

        store.put(furniture.getId(), furniture);
        store.put(appliances.getId(), appliances);
    }

    @Override
    public Category save(Category category) {
        store.put(category.getId(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return store.values().stream().filter(c -> slug.equals(c.getSlug())).findFirst();
    }

    @Override
    public List<Category> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Category> findByParentId(String parentId) {
        return store.values().stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findRootCategories() {
        return store.values().stream()
                .filter(Category::isRoot)
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findByActive(boolean active) {
        return store.values().stream().filter(c -> c.isActive() == active).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return store.values().stream().anyMatch(c -> slug.equals(c.getSlug()));
    }
}
