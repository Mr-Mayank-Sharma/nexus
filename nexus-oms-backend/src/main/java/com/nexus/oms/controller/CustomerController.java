package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxCustomer>>> getCustomers() {
        return ResponseEntity.ok(ApiResponse.success(
                customerRepository.findAll().stream()
                        .filter(c -> c.getTenantId().equals(TenantContext.getCurrentTenantId()))
                        .toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCustomer>> getCustomer(@PathVariable UUID id) {
        NxCustomer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxCustomer>> createCustomer(@RequestBody NxCustomer customer) {
        customer.setTenantId(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success(customerRepository.save(customer), "Customer created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxCustomer>> updateCustomer(@PathVariable UUID id, @RequestBody NxCustomer updates) {
        NxCustomer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        if (updates.getName() != null) customer.setName(updates.getName());
        if (updates.getEmail() != null) customer.setEmail(updates.getEmail());
        if (updates.getPhone() != null) customer.setPhone(updates.getPhone());
        if (updates.getAddress() != null) customer.setAddress(updates.getAddress());
        return ResponseEntity.ok(ApiResponse.success(customerRepository.save(customer), "Customer updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
        customerRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Customer deleted"));
    }
}
