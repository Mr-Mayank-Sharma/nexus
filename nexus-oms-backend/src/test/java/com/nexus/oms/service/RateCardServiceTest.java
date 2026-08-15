package com.nexus.oms.service;

import com.nexus.oms.entity.NxRateCard;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.RateCardRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateCardServiceTest {

    @Mock
    private RateCardRepository rateCardRepository;

    private RateCardService rateCardService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        rateCardService = new RateCardService(rateCardRepository);
        tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxRateCard card(UUID id, UUID clientId) {
        return NxRateCard.builder()
                .id(id)
                .tenantId(tenantId)
                .clientId(clientId)
                .clientName(clientId != null ? "Client" : null)
                .currency("USD")
                .perOrderFee(new BigDecimal("1.50"))
                .perLineFee(new BigDecimal("0.25"))
                .pickingFeePerLine(new BigDecimal("0.10"))
                .storageFeePerUnit(new BigDecimal("0.05"))
                .isActive(true)
                .build();
    }

    @Test
    void createRateCard_scopesToCurrentTenant() {
        NxRateCard input = card(null, null);
        when(rateCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxRateCard saved = rateCardService.createRateCard(input);

        assertEquals(tenantId, saved.getTenantId());
        assertNull(saved.getId());
        verify(rateCardRepository).save(saved);
    }

    @Test
    void getRateCard_rejectsCardOfAnotherTenant() {
        NxRateCard other = card(UUID.randomUUID(), null);
        other.setTenantId(UUID.randomUUID());
        when(rateCardRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThrows(ResourceNotFoundException.class, () -> rateCardService.getRateCard(other.getId()));
    }

    @Test
    void getRateCards_filtersByClientWhenProvided() {
        UUID clientId = UUID.randomUUID();
        when(rateCardRepository.findByTenantIdAndClientId(tenantId, clientId)).thenReturn(List.of(card(UUID.randomUUID(), clientId)));

        List<NxRateCard> result = rateCardService.getRateCards(clientId);

        assertEquals(1, result.size());
        verify(rateCardRepository).findByTenantIdAndClientId(tenantId, clientId);
        verify(rateCardRepository, never()).findByTenantId(tenantId);
    }

    @Test
    void resolveRateCard_fallsBackToDefaultCard() {
        UUID clientId = UUID.randomUUID();
        NxRateCard clientCard = card(UUID.randomUUID(), clientId);
        NxRateCard defaultCard = card(UUID.randomUUID(), null);

        when(rateCardRepository.findFirstByTenantIdAndClientIdAndIsActiveOrderByEffectiveFromDesc(tenantId, clientId, true))
                .thenReturn(Optional.empty());
        when(rateCardRepository.findFirstByTenantIdAndClientIdIsNullAndIsActiveOrderByEffectiveFromDesc(tenantId, true))
                .thenReturn(Optional.of(defaultCard));

        NxRateCard resolved = rateCardService.resolveRateCard(tenantId, clientId);

        assertEquals(defaultCard.getId(), resolved.getId());
    }

    @Test
    void resolveRateCard_throwsWhenNoActiveCard() {
        when(rateCardRepository.findFirstByTenantIdAndClientIdAndIsActiveOrderByEffectiveFromDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(rateCardRepository.findFirstByTenantIdAndClientIdIsNullAndIsActiveOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> rateCardService.resolveRateCard(tenantId, UUID.randomUUID()));
    }
}
