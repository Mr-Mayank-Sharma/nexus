package com.nexus.oms.service;

import com.nexus.oms.entity.NxPromotion;
import com.nexus.oms.entity.NxPromotionUsage;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PromotionRepository;
import com.nexus.oms.repository.PromotionUsageRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PromotionService {

    private static final Logger log = LoggerFactory.getLogger(PromotionService.class);

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;

    public PromotionService(PromotionRepository promotionRepository, PromotionUsageRepository promotionUsageRepository) {
        this.promotionRepository = promotionRepository;
        this.promotionUsageRepository = promotionUsageRepository;
    }

    // ─── CRUD Operations ────────────────────────────────────────────────

    @Transactional
    public NxPromotion createPromotion(NxPromotion promotion) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        promotion.setTenantId(tenantId);

        if (promotion.getCouponCode() != null && !promotion.getCouponCode().isBlank()) {
            Optional<NxPromotion> existing = promotionRepository.findByCouponCode(promotion.getCouponCode());
            if (existing.isPresent()) {
                throw new BadRequestException("Coupon code already exists: " + promotion.getCouponCode());
            }
            promotion.setCouponCode(promotion.getCouponCode().toUpperCase());
        }

        if (promotion.getEndDate().isBefore(promotion.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        return promotionRepository.save(promotion);
    }

    @Transactional
    public NxPromotion updatePromotion(UUID id, NxPromotion updates) {
        NxPromotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));

        if (updates.getName() != null) promotion.setName(updates.getName());
        if (updates.getDescription() != null) promotion.setDescription(updates.getDescription());
        if (updates.getPromotionType() != null) promotion.setPromotionType(updates.getPromotionType());
        if (updates.getDiscountValue() != null) promotion.setDiscountValue(updates.getDiscountValue());
        if (updates.getMinOrderAmount() != null) promotion.setMinOrderAmount(updates.getMinOrderAmount());
        if (updates.getMinQuantity() != null) promotion.setMinQuantity(updates.getMinQuantity());
        if (updates.getMaxUsesTotal() != null) promotion.setMaxUsesTotal(updates.getMaxUsesTotal());
        if (updates.getMaxUsesPerCustomer() != null) promotion.setMaxUsesPerCustomer(updates.getMaxUsesPerCustomer());
        if (updates.getStartDate() != null) promotion.setStartDate(updates.getStartDate());
        if (updates.getEndDate() != null) promotion.setEndDate(updates.getEndDate());
        if (updates.getApplicableChannels() != null) promotion.setApplicableChannels(updates.getApplicableChannels());
        if (updates.getApplicableProductIds() != null) promotion.setApplicableProductIds(updates.getApplicableProductIds());
        if (updates.getApplicableCategoryIds() != null) promotion.setApplicableCategoryIds(updates.getApplicableCategoryIds());
        if (updates.getStackable() != null) promotion.setStackable(updates.getStackable());
        if (updates.getPriority() != null) promotion.setPriority(updates.getPriority());
        if (updates.getActive() != null) promotion.setActive(updates.getActive());

        if (updates.getCouponCode() != null && !updates.getCouponCode().isBlank()) {
            String newCode = updates.getCouponCode().toUpperCase();
            if (!newCode.equals(promotion.getCouponCode())) {
                Optional<NxPromotion> existing = promotionRepository.findByCouponCode(newCode);
                if (existing.isPresent()) {
                    throw new BadRequestException("Coupon code already exists: " + newCode);
                }
            }
            promotion.setCouponCode(newCode);
        }

        return promotionRepository.save(promotion);
    }

    public List<NxPromotion> getPromotions() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return promotionRepository.findByTenantIdOrderByPriorityAsc(tenantId);
    }

    public NxPromotion getPromotion(UUID id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));
    }

    @Transactional
    public void deletePromotion(UUID id) {
        promotionRepository.deleteById(id);
    }

    // ─── Promotion Application ──────────────────────────────────────────

    public Map<String, Object> validateCoupon(String couponCode, UUID customerId, BigDecimal orderTotal) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxPromotion promotion = promotionRepository.findActivePromotionByCoupon(tenantId, couponCode, LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired coupon code"));

        // Check total usage limit
        if (promotion.getMaxUsesTotal() != null && promotion.getCurrentUses() >= promotion.getMaxUsesTotal()) {
            throw new BadRequestException("Coupon usage limit reached");
        }

        // Check per-customer usage limit
        if (promotion.getMaxUsesPerCustomer() != null && customerId != null) {
            long customerUses = promotionUsageRepository.countByPromotionIdAndCustomerId(promotion.getId(), customerId);
            if (customerUses >= promotion.getMaxUsesPerCustomer()) {
                throw new BadRequestException("Per-customer usage limit reached");
            }
        }

        // Check minimum order amount
        if (promotion.getMinOrderAmount() != null && orderTotal != null) {
            if (orderTotal.compareTo(promotion.getMinOrderAmount()) < 0) {
                throw new BadRequestException("Minimum order amount not met: " + promotion.getMinOrderAmount());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("promotionId", promotion.getId());
        result.put("promotionType", promotion.getPromotionType());
        result.put("discountValue", promotion.getDiscountValue());
        result.put("name", promotion.getName());
        result.put("stackable", promotion.getStackable());

        return result;
    }

    public Map<String, Object> calculateDiscount(String couponCode, UUID customerId, BigDecimal orderTotal, int quantity) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxPromotion promotion = promotionRepository.findActivePromotionByCoupon(tenantId, couponCode, LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired coupon code"));

        BigDecimal discount = BigDecimal.ZERO;

        switch (promotion.getPromotionType()) {
            case "PERCENTAGE":
                discount = orderTotal.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100));
                break;
            case "FIXED_AMOUNT":
                discount = promotion.getDiscountValue().min(orderTotal);
                break;
            case "FREE_SHIPPING":
                // Shipping discount handled separately
                discount = BigDecimal.ZERO;
                break;
            case "BUY_X_GET_Y":
                if (promotion.getMinQuantity() != null && quantity >= promotion.getMinQuantity()) {
                    discount = promotion.getDiscountValue();
                }
                break;
            default:
                discount = BigDecimal.ZERO;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("promotionId", promotion.getId());
        result.put("promotionType", promotion.getPromotionType());
        result.put("discountAmount", discount);
        result.put("originalTotal", orderTotal);
        result.put("finalTotal", orderTotal.subtract(discount).max(BigDecimal.ZERO));

        return result;
    }

    @Transactional
    public void recordUsage(UUID promotionId, UUID orderId, UUID customerId, String couponCode, BigDecimal discountAmount, BigDecimal orderTotal) {
        NxPromotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", promotionId));

        promotion.setCurrentUses(promotion.getCurrentUses() + 1);
        promotionRepository.save(promotion);

        NxPromotionUsage usage = NxPromotionUsage.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .promotionId(promotionId)
                .orderId(orderId)
                .customerId(customerId)
                .couponCode(couponCode)
                .discountAmount(discountAmount)
                .orderTotal(orderTotal)
                .usedAt(LocalDateTime.now())
                .build();
        promotionUsageRepository.save(usage);

        log.info("Promotion usage recorded: promotionId={}, orderId={}, discount={}", promotionId, orderId, discountAmount);
    }

    // ─── Analytics ──────────────────────────────────────────────────────

    public Map<String, Object> getPromotionStats(UUID promotionId) {
        NxPromotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", promotionId));

        long totalUses = promotionUsageRepository.countByPromotionId(promotionId);
        BigDecimal totalDiscount = promotionUsageRepository.sumDiscountByPromotionId(promotionId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("promotionId", promotionId);
        stats.put("name", promotion.getName());
        stats.put("totalUses", totalUses);
        stats.put("totalDiscountGiven", totalDiscount);
        stats.put("maxUsesTotal", promotion.getMaxUsesTotal());
        stats.put("remainingUses", promotion.getMaxUsesTotal() != null ? promotion.getMaxUsesTotal() - totalUses : null);
        stats.put("currentUsesDB", promotion.getCurrentUses());

        return stats;
    }

    public List<Map<String, Object>> getActivePromotionsForOrder(BigDecimal orderTotal, int quantity, String channel) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxPromotion> activePromos = promotionRepository.findActivePromotions(tenantId, LocalDateTime.now());

        return activePromos.stream()
                .filter(p -> {
                    if (p.getMinOrderAmount() != null && orderTotal.compareTo(p.getMinOrderAmount()) < 0) return false;
                    if (p.getMinQuantity() != null && quantity < p.getMinQuantity()) return false;
                    if (p.getApplicableChannels() != null && !p.getApplicableChannels().isBlank()) {
                        String channels = p.getApplicableChannels().toUpperCase();
                        if (!channels.contains("ALL") && channel != null && !channels.contains(channel.toUpperCase())) {
                            return false;
                        }
                    }
                    if (p.getMaxUsesTotal() != null && p.getCurrentUses() >= p.getMaxUsesTotal()) return false;
                    return true;
                })
                .map(p -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", p.getId());
                    info.put("name", p.getName());
                    info.put("promotionType", p.getPromotionType());
                    info.put("discountValue", p.getDiscountValue());
                    info.put("couponCode", p.getCouponCode());
                    info.put("priority", p.getPriority());
                    return info;
                })
                .collect(Collectors.toList());
    }
}
