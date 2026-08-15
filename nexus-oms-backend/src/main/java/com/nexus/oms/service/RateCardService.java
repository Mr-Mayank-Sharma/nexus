package com.nexus.oms.service;

import com.nexus.oms.entity.NxRateCard;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.RateCardRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RateCardService {

    private final RateCardRepository rateCardRepository;

    public RateCardService(RateCardRepository rateCardRepository) {
        this.rateCardRepository = rateCardRepository;
    }

    @Transactional
    public NxRateCard createRateCard(NxRateCard card) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        card.setTenantId(tenantId);
        card.setId(null);
        return rateCardRepository.save(card);
    }

    @Transactional
    public NxRateCard updateRateCard(UUID id, NxRateCard updates) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxRateCard card = rateCardRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("RateCard", id));

        if (updates.getClientId() != null) card.setClientId(updates.getClientId());
        if (updates.getClientName() != null) card.setClientName(updates.getClientName());
        if (updates.getCurrency() != null) card.setCurrency(updates.getCurrency());
        if (updates.getPerOrderFee() != null) card.setPerOrderFee(updates.getPerOrderFee());
        if (updates.getPerLineFee() != null) card.setPerLineFee(updates.getPerLineFee());
        if (updates.getPickingFeePerLine() != null) card.setPickingFeePerLine(updates.getPickingFeePerLine());
        if (updates.getStorageFeePerUnit() != null) card.setStorageFeePerUnit(updates.getStorageFeePerUnit());
        if (updates.getDescription() != null) card.setDescription(updates.getDescription());
        if (updates.getEffectiveFrom() != null) card.setEffectiveFrom(updates.getEffectiveFrom());
        if (updates.getEffectiveTo() != null) card.setEffectiveTo(updates.getEffectiveTo());
        if (updates.getIsActive() != null) card.setIsActive(updates.getIsActive());

        return rateCardRepository.save(card);
    }

    public List<NxRateCard> getRateCards(UUID clientId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (clientId != null) {
            return rateCardRepository.findByTenantIdAndClientId(tenantId, clientId);
        }
        return rateCardRepository.findByTenantId(tenantId);
    }

    public NxRateCard getRateCard(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return rateCardRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("RateCard", id));
    }

    public NxRateCard resolveRateCard(UUID tenantId, UUID clientId) {
        return rateCardRepository
                .findFirstByTenantIdAndClientIdAndIsActiveOrderByEffectiveFromDesc(tenantId, clientId, true)
                .or(() -> rateCardRepository.findFirstByTenantIdAndClientIdIsNullAndIsActiveOrderByEffectiveFromDesc(tenantId, true))
                .orElseThrow(() -> new BadRequestException("No active rate card found for client"));
    }

    @Transactional
    public void deleteRateCard(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxRateCard card = rateCardRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("RateCard", id));
        rateCardRepository.delete(card);
    }
}
