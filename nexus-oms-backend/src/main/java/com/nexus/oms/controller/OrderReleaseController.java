package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.FulfilmentReleaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Release by ship date: the windows, the orders on hold, rescheduling, and releasing early. */
@RestController
@RequestMapping("/order-release")
public class OrderReleaseController {

    private final FulfilmentReleaseService service;

    public OrderReleaseController(FulfilmentReleaseService service) {
        this.service = service;
    }

    public record PolicyRequest(int b2bDays, int b2cDays) {}
    public record ScheduleRequest(String orderType, LocalDateTime requestedShipDate) {}
    public record HeldView(UUID id, String orderNumber, String channel, String orderType, LocalDateTime requestedShipDate,
                           LocalDateTime releaseAt, String status, String customerEmail, java.math.BigDecimal total, String currency) {}

    static HeldView view(NxOrder o) {
        return new HeldView(o.getId(), o.getChannelOrderId() != null ? o.getChannelOrderId() : o.getExternalId(), o.getChannel(),
                o.getOrderType(), o.getRequestedShipDate(), o.getReleaseAt(), o.getStatus(), o.getCustomerEmail(), o.getTotal(), o.getCurrency());
    }

    @GetMapping("/policy")
    public ResponseEntity<ApiResponse<FulfilmentReleaseService.Policy>> policy() {
        return ResponseEntity.ok(ApiResponse.success(service.policy(TenantContext.getCurrentTenantId())));
    }

    @PutMapping("/policy")
    public ResponseEntity<ApiResponse<FulfilmentReleaseService.Policy>> updatePolicy(@RequestBody PolicyRequest r) {
        return ResponseEntity.ok(ApiResponse.success(service.updatePolicy(TenantContext.getCurrentTenantId(), r.b2bDays(), r.b2cDays()),
                "Release windows saved; orders on hold were re-planned"));
    }

    @GetMapping("/held")
    public ResponseEntity<ApiResponse<List<HeldView>>> held() {
        return ResponseEntity.ok(ApiResponse.success(service.held(TenantContext.getCurrentTenantId()).stream().map(OrderReleaseController::view).toList()));
    }

    @PutMapping("/orders/{orderId}/schedule")
    public ResponseEntity<ApiResponse<HeldView>> schedule(@PathVariable UUID orderId, @RequestBody ScheduleRequest r) {
        return ResponseEntity.ok(ApiResponse.success(view(service.schedule(TenantContext.getCurrentTenantId(), orderId, r.orderType(), r.requestedShipDate())), "Ship date saved"));
    }

    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<ApiResponse<HeldView>> release(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(view(service.releaseNow(TenantContext.getCurrentTenantId(), orderId)), "Released for fulfilment"));
    }
}
