package com.nexus.oms.config;

import com.nexus.oms.service.IntegrationStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Encrypts integration secrets that were stored as plaintext before settings were sealed. */
@Component
public class StoreSecretUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreSecretUpgrade.class);
    private final IntegrationStoreService storeService;

    public StoreSecretUpgrade(IntegrationStoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int sealed = storeService.sealLegacySecrets();
            if (sealed > 0) log.info("Encrypted {} integration secret(s) that were stored as plaintext", sealed);
        } catch (RuntimeException e) {
            // Never block startup: secrets are still protected on every new write.
            log.error("Could not encrypt legacy integration secrets: {}", e.getMessage());
        }
    }
}
