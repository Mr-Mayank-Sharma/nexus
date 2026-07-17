package com.nexus.oms.service;

import com.nexus.oms.entity.Product;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ProductRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(value = "products", key = "'list'")
    public List<Product> getProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getTenantId().equals(TenantContext.getCurrentTenantId()))
                .toList();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProduct(UUID id) {
        return productRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product createProduct(Product product) {
        product.setId(null);
        product.setTenantId(TenantContext.getCurrentTenantId());
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product updateProduct(UUID id, Product updates) {
        Product product = getProduct(id);
        if (updates.getSku() != null) product.setSku(updates.getSku());
        if (updates.getProductName() != null) product.setProductName(updates.getProductName());
        if (updates.getDescription() != null) product.setDescription(updates.getDescription());
        if (updates.getCategory() != null) product.setCategory(updates.getCategory());
        if (updates.getUnitPrice() != null) product.setUnitPrice(updates.getUnitPrice());
        if (updates.getCostPrice() != null) product.setCostPrice(updates.getCostPrice());
        if (updates.getWeight() != null) product.setWeight(updates.getWeight());
        if (updates.getIsActive() != null) product.setIsActive(updates.getIsActive());
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }
}
