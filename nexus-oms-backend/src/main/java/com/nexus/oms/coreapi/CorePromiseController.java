package com.nexus.oms.coreapi;

import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.service.PromiseService;
import com.nexus.oms.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Can you deliver this, here, by then?" (doc 25, features 2 and 4). Admin only for now; the agent order API will use it. */
@RestController
@RequestMapping("/core/promise")
@Tag(name = "Core promise", description = "Availability and delivery-date promise")
public class CorePromiseController {

    private final PromiseService promise;
    private final EntityManager em;

    public CorePromiseController(PromiseService promise, EntityManager em) {
        this.promise = promise;
        this.em = em;
    }

    public record Item(String sku, String productId, BigDecimal quantity) {
    }

    public record Request(List<Item> items, PromiseService.Destination shipTo, String shipToContactMechId, String shipmentMethodTypeId) {
    }

    @Operation(summary = "Where and when these items can be delivered: ship date, expected and promised delivery, confidence and why")
    @PostMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PromiseService.Answer>> promise(@RequestBody Request r) {
        Map<String, BigDecimal> products = new LinkedHashMap<>();
        for (Item i : r.items() == null ? List.<Item>of() : r.items()) {
            String id = i.productId() != null ? i.productId() : em.createQuery("SELECT g.productId FROM GoodIdentification g WHERE g.goodIdentificationTypeId = 'SKU' "
                    + "AND g.idValue = :v AND g.thruDate IS NULL", String.class).setParameter("v", i.sku()).setMaxResults(1).getResultList().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown SKU " + i.sku()));
            products.merge(id, i.quantity() == null ? BigDecimal.ONE : i.quantity(), BigDecimal::add);
        }
        PromiseService.Destination to = r.shipTo();
        if (to == null && r.shipToContactMechId() != null) {
            PostalAddress a = em.find(PostalAddress.class, r.shipToContactMechId());
            if (a == null) throw new IllegalArgumentException("Unknown address " + r.shipToContactMechId());
            to = PromiseService.Destination.of(a);
        }
        return ResponseEntity.ok(ApiResponse.success(promise.promise(products, to, r.shipmentMethodTypeId(), null), "Promise"));
    }
}
