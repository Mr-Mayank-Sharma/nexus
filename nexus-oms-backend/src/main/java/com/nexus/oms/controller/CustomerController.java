package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Customers", description = "Customer management APIs")
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Operation(summary = "List all customers")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxCustomer>>> getCustomers() {
        return ResponseEntity.ok(ApiResponse.success(
                customerRepository.findAll().stream()
                        .filter(c -> c.getTenantId().equals(TenantContext.getCurrentTenantId()))
                        .toList()));
    }

    @Operation(summary = "Get customer by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCustomer>> getCustomer(@PathVariable UUID id) {
        NxCustomer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @Operation(summary = "Create a new customer")
    @PostMapping
    public ResponseEntity<ApiResponse<NxCustomer>> createCustomer(@Valid @RequestBody NxCustomer customer) {
        customer.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(customerRepository.save(customer), "Customer created"));
    }

    @Operation(summary = "Update customer details")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCustomer>> updateCustomer(@PathVariable UUID id, @Valid @RequestBody NxCustomer updates) {
        NxCustomer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        if (updates.getName() != null) customer.setName(updates.getName());
        if (updates.getEmail() != null) customer.setEmail(updates.getEmail());
        if (updates.getPhone() != null) customer.setPhone(updates.getPhone());
        if (updates.getAddress() != null) customer.setAddress(updates.getAddress());
        return ResponseEntity.ok(ApiResponse.success(customerRepository.save(customer), "Customer updated"));
    }

    @Operation(summary = "Delete a customer")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
        customerRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Customer deleted"));
    }
}
