package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.Product;
import com.nexus.oms.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;

@Tag(name = "Products", description = "Product management APIs")
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "List all products, optionally filtered by search query, category, price range, or active status")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active) {
        if (q == null && category == null && minPrice == null && maxPrice == null && active == null) {
            return ResponseEntity.ok(ApiResponse.success(productService.getProducts()));
        }
        return ResponseEntity.ok(ApiResponse.success(
                productService.searchProducts(q, category, minPrice, maxPrice, active)));
    }

    @Operation(summary = "Get product by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(id)));
    }

    @Operation(summary = "Create a new product")
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@Valid @RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.success(productService.createProduct(product), "Product created"));
    }

    @Operation(summary = "Update product details")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable UUID id, @RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateProduct(id, product), "Product updated"));
    }

    @Operation(summary = "Delete a product")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted"));
    }
}
