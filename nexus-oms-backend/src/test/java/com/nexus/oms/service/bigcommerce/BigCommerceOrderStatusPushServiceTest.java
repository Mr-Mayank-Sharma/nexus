package com.nexus.oms.service.bigcommerce;

import com.nexus.oms.entity.NxBigCommerceConfig;
import com.nexus.oms.repository.NxBigCommerceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BigCommerce has no partial-fulfilment or allocation-failure status. The dangerous failure mode
 * here is not a crash — it is a wrong number: pushing FAILED as CANCELLED (5) cancels a live
 * customer order in the storefront, and pushing PARTIALLY_ALLOCATED as a completed status tells the
 * customer the order is handled when lines are still short. So these tests assert the absence of a
 * push, not a mapped value.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BigCommerceOrderStatusPushServiceTest {

    private static final String EXTERNAL_ID = "4211";

    @Mock
    private BigCommerceClient bcClient;
    @Mock
    private NxBigCommerceConfigRepository configRepository;

    private BigCommerceOrderStatusPushService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new BigCommerceOrderStatusPushService(bcClient, configRepository);
        tenantId = UUID.randomUUID();

        NxBigCommerceConfig config = NxBigCommerceConfig.builder()
                .tenantId(tenantId)
                .storeHash("abc123")
                .accessToken("token")
                .apiPath("/api/v2")
                .isActive(true)
                .build();
        when(configRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(Optional.of(config));
    }

    @Test
    void pushOrderStatus_PartiallyAllocated_SkipsPushEntirely() {
        service.pushOrderStatus(tenantId, EXTERNAL_ID, "PARTIALLY_ALLOCATED");

        verify(bcClient, never()).updateOrderStatus(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void pushOrderStatus_FailedAllocation_IsNotPushedAsCancelled() {
        service.pushOrderStatus(tenantId, EXTERNAL_ID, "FAILED");

        // Explicitly not 5 (CANCELLED) and not 11/10 (handled). Nothing goes out; the state stays
        // visible in the OMS where it can be resolved properly.
        verify(bcClient, never()).updateOrderStatus(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void pushOrderStatus_Confirmed_PushesAwaitingFulfilment() {
        service.pushOrderStatus(tenantId, EXTERNAL_ID, "CONFIRMED");

        verify(bcClient).updateOrderStatus("/api/v2/stores/abc123", "token", 4211, 11);
    }

    @Test
    void pushOrderStatus_Allocated_PushesAwaitingFulfilment() {
        service.pushOrderStatus(tenantId, EXTERNAL_ID, "ALLOCATED");

        verify(bcClient).updateOrderStatus("/api/v2/stores/abc123", "token", 4211, 11);
    }

    @Test
    void pushOrderStatus_Shipped_PushesShippedStatus() {
        service.pushOrderStatus(tenantId, EXTERNAL_ID, "SHIPPED");

        verify(bcClient).updateOrderStatus("/api/v2/stores/abc123", "token", 4211, 2);
    }
}
