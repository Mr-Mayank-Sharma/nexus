package com.nexus.oms.coreapi;

import com.nexus.core.service.ProductService;
import com.nexus.core.service.PromiseService;
import com.nexus.oms.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Can you deliver this, here, by then?" (doc 25, features 2 and 4). Admin only for now; the agent order API uses the service directly. */
@RestController
@RequestMapping("/core/promise")
@Tag(name = "Core promise", description = "Availability and delivery-date promise")
public class CorePromiseController {

    private final PromiseService promiseService;
    private final ProductService productService;

    public CorePromiseController(PromiseService promiseService, ProductService productService) {
        this.promiseService = promiseService;
        this.productService = productService;
    }

    /** An item by product id or by SKU; one when no quantity is given. */
    public record Item(String sku, String productId, BigDecimal quantity) {
    }

    /** Where to: coordinates and postal code, or the id of a saved address. */
    public record Request(List<Item> items, PromiseService.Destination shipTo, String shipToContactMechId, String shipmentMethodTypeId) {
    }

    @Operation(summary = "Where and when these items can be delivered: ship date, expected and promised delivery, confidence and why")
    @PostMapping
    public ResponseEntity<ApiResponse<PromiseService.Answer>> promise(@RequestBody Request request) {
        Map<String, BigDecimal> quantityByProduct = new LinkedHashMap<>();
        for (Item item : request.items() == null ? List.<Item>of() : request.items()) {
            String productId = item.productId() != null
                    ? item.productId()
                    : productService.findByIdentification("SKU", item.sku()).orElseThrow(() -> new IllegalArgumentException("Unknown SKU " + item.sku()));
            BigDecimal quantity = item.quantity() == null ? BigDecimal.ONE : item.quantity();
            quantityByProduct.merge(productId, quantity, BigDecimal::add);
        }
        PromiseService.Destination destination = request.shipTo();
        if (destination == null && request.shipToContactMechId() != null) {
            destination = promiseService.destinationOf(request.shipToContactMechId());
        }
        PromiseService.Answer answer = promiseService.promise(quantityByProduct, destination, request.shipmentMethodTypeId(), null);
        return ResponseEntity.ok(ApiResponse.success(answer, "Promise"));
    }
}
