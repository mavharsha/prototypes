package com.example.ecommerce.repository;

import com.example.ecommerce.common.enums.ProductStatus;
import com.example.ecommerce.domain.entity.Category;
import com.example.ecommerce.domain.entity.Product;
import com.example.ecommerce.domain.entity.ProductAttribute;
import com.example.ecommerce.domain.entity.ProductImage;
import com.example.ecommerce.domain.repository.CategoryRepository;
import com.example.ecommerce.domain.repository.BrandRepository;
import com.example.ecommerce.domain.repository.ProductRepository;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new ConcurrentHashMap<>();
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public InMemoryProductRepository(CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        seedData();
    }

    private void seedData() {
        // Find category and brand IDs
        String laptopsCatId = categoryRepository.findBySlug("laptops").map(Category::getId).orElse(null);
        String smartphonesCatId = categoryRepository.findBySlug("smartphones").map(Category::getId).orElse(null);
        String headphonesCatId = categoryRepository.findBySlug("headphones").map(Category::getId).orElse(null);
        String mensCatId = categoryRepository.findBySlug("mens").map(Category::getId).orElse(null);

        String appleId = brandRepository.findByName("Apple").map(b -> b.getId()).orElse(null);
        String samsungId = brandRepository.findByName("Samsung").map(b -> b.getId()).orElse(null);
        String sonyId = brandRepository.findByName("Sony").map(b -> b.getId()).orElse(null);
        String nikeId = brandRepository.findByName("Nike").map(b -> b.getId()).orElse(null);
        String adidasId = brandRepository.findByName("Adidas").map(b -> b.getId()).orElse(null);

        // Product 1: MacBook Pro
        Product macbook = new Product("MacBook Pro 16", "Apple MacBook Pro 16-inch with M3 Pro chip", new BigDecimal("2499.99"));
        macbook.setCategoryId(laptopsCatId);
        macbook.setBrandId(appleId);
        macbook.activate();
        macbook.addImage(new ProductImage("https://example.com/images/macbook-pro-1.jpg", "MacBook Pro front view", 0, true));
        macbook.addImage(new ProductImage("https://example.com/images/macbook-pro-2.jpg", "MacBook Pro side view", 1, false));
        macbook.addAttribute(new ProductAttribute("processor", "M3 Pro"));
        macbook.addAttribute(new ProductAttribute("display", "16-inch Liquid Retina XDR"));
        macbook.setSeoTitle("MacBook Pro 16 - Apple Laptop");
        macbook.setSeoDescription("Powerful MacBook Pro with M3 Pro chip");
        macbook.setSeoKeywords("macbook, apple, laptop, m3");
        store.put(macbook.getId(), macbook);

        // Product 2: Galaxy S24 Ultra
        Product galaxy = new Product("Samsung Galaxy S24 Ultra", "Samsung flagship smartphone with S Pen", new BigDecimal("1299.99"));
        galaxy.setCategoryId(smartphonesCatId);
        galaxy.setBrandId(samsungId);
        galaxy.activate();
        galaxy.addImage(new ProductImage("https://example.com/images/galaxy-s24-1.jpg", "Galaxy S24 Ultra front", 0, true));
        galaxy.addAttribute(new ProductAttribute("display", "6.8-inch Dynamic AMOLED"));
        galaxy.addAttribute(new ProductAttribute("camera", "200MP main sensor"));
        store.put(galaxy.getId(), galaxy);

        // Product 3: iPhone 15 Pro
        Product iphone = new Product("iPhone 15 Pro", "Apple iPhone 15 Pro with A17 Pro chip", new BigDecimal("999.99"));
        iphone.setCategoryId(smartphonesCatId);
        iphone.setBrandId(appleId);
        iphone.activate();
        iphone.addImage(new ProductImage("https://example.com/images/iphone-15-1.jpg", "iPhone 15 Pro", 0, true));
        iphone.addAttribute(new ProductAttribute("processor", "A17 Pro"));
        iphone.addAttribute(new ProductAttribute("material", "Titanium"));
        store.put(iphone.getId(), iphone);

        // Product 4: Sony WH-1000XM5
        Product sonyHeadphones = new Product("Sony WH-1000XM5", "Industry-leading noise cancelling headphones", new BigDecimal("349.99"));
        sonyHeadphones.setCategoryId(headphonesCatId);
        sonyHeadphones.setBrandId(sonyId);
        sonyHeadphones.activate();
        sonyHeadphones.addImage(new ProductImage("https://example.com/images/sony-xm5-1.jpg", "Sony WH-1000XM5", 0, true));
        sonyHeadphones.addAttribute(new ProductAttribute("type", "Over-ear"));
        sonyHeadphones.addAttribute(new ProductAttribute("noise_cancelling", "Yes"));
        sonyHeadphones.addAttribute(new ProductAttribute("battery_life", "30 hours"));
        store.put(sonyHeadphones.getId(), sonyHeadphones);

        // Product 5: AirPods Pro
        Product airpods = new Product("AirPods Pro 2", "Apple AirPods Pro with USB-C", new BigDecimal("249.99"));
        airpods.setCategoryId(headphonesCatId);
        airpods.setBrandId(appleId);
        airpods.activate();
        airpods.addImage(new ProductImage("https://example.com/images/airpods-pro-1.jpg", "AirPods Pro 2", 0, true));
        airpods.addAttribute(new ProductAttribute("type", "In-ear"));
        airpods.addAttribute(new ProductAttribute("noise_cancelling", "Yes"));
        store.put(airpods.getId(), airpods);

        // Product 6: Samsung Galaxy Book
        Product galaxyBook = new Product("Samsung Galaxy Book4 Pro", "Ultra-thin AMOLED laptop", new BigDecimal("1449.99"));
        galaxyBook.setCategoryId(laptopsCatId);
        galaxyBook.setBrandId(samsungId);
        galaxyBook.activate();
        galaxyBook.addImage(new ProductImage("https://example.com/images/galaxy-book-1.jpg", "Galaxy Book4 Pro", 0, true));
        galaxyBook.addAttribute(new ProductAttribute("processor", "Intel Core Ultra 7"));
        galaxyBook.addAttribute(new ProductAttribute("display", "16-inch AMOLED"));
        store.put(galaxyBook.getId(), galaxyBook);

        // Product 7: Nike Air Max
        Product airMax = new Product("Nike Air Max 90", "Classic Nike Air Max sneakers", new BigDecimal("129.99"));
        airMax.setCategoryId(mensCatId);
        airMax.setBrandId(nikeId);
        airMax.activate();
        airMax.addImage(new ProductImage("https://example.com/images/air-max-90-1.jpg", "Nike Air Max 90", 0, true));
        airMax.addAttribute(new ProductAttribute("type", "Sneakers"));
        airMax.addAttribute(new ProductAttribute("material", "Leather/Mesh"));
        store.put(airMax.getId(), airMax);

        // Product 8: Adidas Ultraboost
        Product ultraboost = new Product("Adidas Ultraboost 23", "Premium running shoes with Boost technology", new BigDecimal("189.99"));
        ultraboost.setCategoryId(mensCatId);
        ultraboost.setBrandId(adidasId);
        ultraboost.activate();
        ultraboost.addImage(new ProductImage("https://example.com/images/ultraboost-1.jpg", "Adidas Ultraboost 23", 0, true));
        ultraboost.addAttribute(new ProductAttribute("type", "Running shoes"));
        ultraboost.addAttribute(new ProductAttribute("cushioning", "Boost"));
        store.put(ultraboost.getId(), ultraboost);
    }

    @Override
    public Product save(Product product) {
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findBySlug(String slug) {
        return store.values().stream().filter(p -> slug.equals(p.getSlug())).findFirst();
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Product> findByCategoryId(String categoryId) {
        return store.values().stream().filter(p -> categoryId.equals(p.getCategoryId())).collect(Collectors.toList());
    }

    @Override
    public List<Product> findByBrandId(String brandId) {
        return store.values().stream().filter(p -> brandId.equals(p.getBrandId())).collect(Collectors.toList());
    }

    @Override
    public List<Product> findByStatus(ProductStatus status) {
        return store.values().stream().filter(p -> status.equals(p.getStatus())).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return store.values().stream().anyMatch(p -> slug.equals(p.getSlug()));
    }
}
