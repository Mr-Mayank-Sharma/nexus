package com.nexus.oms.setup;

import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.dto.RegisterRequest;
import com.nexus.oms.entity.NxBigCommerceConfig;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.repository.NxBigCommerceConfigRepository;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.AuthService;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.tenancy.PlatformDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sets up a local Nexus from the {@code .env} file when the application starts, so a fresh laptop needs no clicking:
 * <ol>
 *   <li>the first company and its ADMIN ({@code NEXUS_ADMIN_USERNAME}, {@code NEXUS_ADMIN_PASSWORD},
 *       {@code NEXUS_ADMIN_EMAIL}, {@code NEXUS_COMPANY_NAME}), when that username does not exist yet;</li>
 *   <li>the Shopify store ({@code SHOPIFY_SHOP_DOMAIN} with {@code SHOPIFY_ACCESS_TOKEN}, or
 *       {@code SHOPIFY_CLIENT_ID} and {@code SHOPIFY_CLIENT_SECRET}), created or updated for that company;</li>
 *   <li>the BigCommerce store ({@code BIGCOMMERCE_STORE_HASH}, {@code BIGCOMMERCE_ACCESS_TOKEN},
 *       {@code BIGCOMMERCE_CLIENT_ID}).</li>
 * </ol>
 * Blank values are skipped, so it does nothing unless the file asks for it, and running it again changes only what
 * the file changed. Secrets are sealed by the credential vault like ones typed in the app.
 */
@Component
public class LocalSetupFromEnvironment {

    private static final Logger log = LoggerFactory.getLogger(LocalSetupFromEnvironment.class);
    static final String SHOPIFY_STORE_CODE = "shopify";

    private final AuthService authService;
    private final PlatformDirectory platformDirectory;
    private final IntegrationStoreService storeService;
    private final NxIntegrationStoreRepository storeRepository;
    private final NxBigCommerceConfigRepository bigCommerceConfigRepository;
    private final SetupValues values;

    /** Everything the file can set; blank means "not given". */
    public record SetupValues(String adminUsername, String adminPassword, String adminEmail, String companyName,
                              String shopifyShopDomain, String shopifyAccessToken, String shopifyClientId, String shopifyClientSecret,
                              String bigCommerceStoreHash, String bigCommerceAccessToken, String bigCommerceClientId) {
    }

    public LocalSetupFromEnvironment(AuthService authService, PlatformDirectory platformDirectory, IntegrationStoreService storeService,
                                     NxIntegrationStoreRepository storeRepository, NxBigCommerceConfigRepository bigCommerceConfigRepository,
                                     @Value("${NEXUS_ADMIN_USERNAME:}") String adminUsername,
                                     @Value("${NEXUS_ADMIN_PASSWORD:}") String adminPassword,
                                     @Value("${NEXUS_ADMIN_EMAIL:}") String adminEmail,
                                     @Value("${NEXUS_COMPANY_NAME:}") String companyName,
                                     @Value("${SHOPIFY_SHOP_DOMAIN:}") String shopifyShopDomain,
                                     @Value("${SHOPIFY_ACCESS_TOKEN:}") String shopifyAccessToken,
                                     @Value("${SHOPIFY_CLIENT_ID:}") String shopifyClientId,
                                     @Value("${SHOPIFY_CLIENT_SECRET:}") String shopifyClientSecret,
                                     @Value("${BIGCOMMERCE_STORE_HASH:}") String bigCommerceStoreHash,
                                     @Value("${BIGCOMMERCE_ACCESS_TOKEN:}") String bigCommerceAccessToken,
                                     @Value("${BIGCOMMERCE_CLIENT_ID:}") String bigCommerceClientId) {
        this.authService = authService;
        this.platformDirectory = platformDirectory;
        this.storeService = storeService;
        this.storeRepository = storeRepository;
        this.bigCommerceConfigRepository = bigCommerceConfigRepository;
        this.values = new SetupValues(adminUsername, adminPassword, adminEmail, companyName, shopifyShopDomain, shopifyAccessToken, shopifyClientId,
                shopifyClientSecret, bigCommerceStoreHash, bigCommerceAccessToken, bigCommerceClientId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setUpAtStartUp() {
        if (blank(values.adminUsername())) {
            return;
        }
        try {
            // Step 1: the company and its admin.
            Optional<UUID> company = platformDirectory.companyOfUsername(values.adminUsername());
            if (company.isEmpty()) {
                company = createAdmin();
            }
            if (company.isEmpty()) {
                return;
            }
            UUID tenantId = company.get();

            // Step 2: the stores, as that company.
            TenantContext.callAs(tenantId, () -> {
                connectShopify(tenantId);
                connectBigCommerce(tenantId);
                return null;
            });
        } catch (RuntimeException failure) {
            log.error("Local setup from .env failed: {}", failure.getMessage(), failure);
        }
    }

    private Optional<UUID> createAdmin() {
        if (blank(values.adminPassword())) {
            log.warn("NEXUS_ADMIN_USERNAME is set but NEXUS_ADMIN_PASSWORD is blank: no admin created");
            return Optional.empty();
        }
        RegisterRequest request = new RegisterRequest(values.adminUsername(), values.adminPassword(), blankToNull(values.adminEmail()), "ADMIN",
                blank(values.companyName()) ? values.adminUsername() : values.companyName());
        authService.register(request);
        log.info("Local setup: created company and admin {}", values.adminUsername());
        return platformDirectory.companyOfUsername(values.adminUsername());
    }

    private void connectShopify(UUID tenantId) {
        if (blank(values.shopifyShopDomain())) {
            return;
        }
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("shop_domain", values.shopifyShopDomain().trim());
        putIfGiven(credentials, "access_token", values.shopifyAccessToken());
        putIfGiven(credentials, "client_id", values.shopifyClientId());
        putIfGiven(credentials, "client_secret", values.shopifyClientSecret());

        Optional<NxIntegrationStore> existing = storeRepository.findByTenantIdAndStoreCode(tenantId, SHOPIFY_STORE_CODE);
        if (existing.isPresent()) {
            credentials.forEach((key, value) -> storeService.updateSetting(existing.get().getId(), key, value));
            log.info("Local setup: Shopify store {} updated from .env", values.shopifyShopDomain());
            return;
        }
        IntegrationStoreRequest request = new IntegrationStoreRequest();
        request.setStoreCode(SHOPIFY_STORE_CODE);
        request.setStoreName("Shopify " + values.shopifyShopDomain().trim());
        request.setPlatform("SHOPIFY");
        request.setExternalDomain(values.shopifyShopDomain().trim());
        request.setSettings(credentials);
        request.setIsActive(true);
        storeService.createStore(tenantId, request);
        log.info("Local setup: Shopify store {} connected from .env", values.shopifyShopDomain());
    }

    private void connectBigCommerce(UUID tenantId) {
        if (blank(values.bigCommerceStoreHash()) || blank(values.bigCommerceAccessToken())) {
            return;
        }
        NxBigCommerceConfig config = bigCommerceConfigRepository.findByTenantId(tenantId)
                .orElse(NxBigCommerceConfig.builder().tenantId(tenantId).build());
        config.setStoreHash(values.bigCommerceStoreHash().trim());
        config.setAccessToken(values.bigCommerceAccessToken().trim());
        config.setClientId(blankToNull(values.bigCommerceClientId()));
        config.setIsActive(true);
        bigCommerceConfigRepository.save(config);
        log.info("Local setup: BigCommerce store {} connected from .env", values.bigCommerceStoreHash());
    }

    private static void putIfGiven(Map<String, String> settings, String key, String value) {
        if (!blank(value)) {
            settings.put(key, value.trim());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
