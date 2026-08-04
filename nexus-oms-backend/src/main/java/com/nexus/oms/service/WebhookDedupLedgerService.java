package com.nexus.oms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Transactional deduplication ledger for inbound eCommerce webhooks.
 * Uses the unique constraint on (source, external_id) to prevent
 * double-processing of webhook events from Shopify and BigCommerce.
 */
@Service
public class WebhookDedupLedgerService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDedupLedgerService.class);

    private final JdbcTemplate jdbcTemplate;

    public WebhookDedupLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Try to claim processing of an external order. Returns true if this is the first
     * time this external_id has been seen (i.e., safe to process).
     * Returns false if the order was already processed (duplicate).
     */
    public boolean tryClaimProcessing(UUID tenantId, String source, String externalId, String eventId) {
        try {
            int inserted = jdbcTemplate.update(
                "INSERT INTO webhook_dedup_ledger (tenant_id, source, external_id, event_id, status) " +
                "VALUES (?, ?, ?, ?, 'PROCESSED') " +
                "ON CONFLICT (source, external_id) DO NOTHING",
                tenantId, source, externalId, eventId);
            return inserted > 0;
        } catch (Exception e) {
            log.warn("Dedup ledger write failed, allowing processing: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Check if an external order has already been processed.
     */
    public boolean isAlreadyProcessed(String source, String externalId) {
        try {
            var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_dedup_ledger WHERE source = ? AND external_id = ?",
                Integer.class, source, externalId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Dedup ledger check failed: {}", e.getMessage());
            return false;
        }
    }
}
