package com.nexus.oms.service.bigcommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BigCommerceCustomerImportService {

    private final BigCommerceClient bcClient;
    private final NxBigCommerceConfigRepository configRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    public BigCommerceCustomerImportService(BigCommerceClient bcClient,
                                             NxBigCommerceConfigRepository configRepository,
                                             NxSyncLogRepository syncLogRepository,
                                             CustomerRepository customerRepository,
                                             AddressRepository addressRepository) {
        this.bcClient = bcClient;
        this.configRepository = configRepository;
        this.syncLogRepository = syncLogRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public SyncResult importCustomers(UUID tenantId) {
        NxBigCommerceConfig config = configRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new BadRequestException("BigCommerce is not configured for this tenant."));

        NxSyncLog syncLog = NxSyncLog.builder()
                .tenantId(tenantId)
                .integrationType("BIGCOMMERCE")
                .syncType("CUSTOMER_IMPORT")
                .status("RUNNING")
                .build();
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0, succeeded = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            String apiPath = config.getApiPath() + "/stores/" + config.getStoreHash();
            Map<String, String> params = new HashMap<>();
            params.put("limit", "250");
            params.put("page", "1");

            JsonNode response = bcClient.getCustomers(apiPath, config.getAccessToken(), params);
            JsonNode customers = response != null && response.has("data") ? response.get("data") : null;

            if (customers != null && customers.isArray()) {
                for (JsonNode customer : customers) {
                    try {
                        importSingleCustomer(tenantId, customer);
                        succeeded++;
                    } catch (Exception e) {
                        failed++;
                        errors.add("Customer " + customer.get("id").asText() + ": " + e.getMessage());
                    }
                    processed++;
                }
            }

            syncLog.setStatus("COMPLETED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            if (!errors.isEmpty()) syncLog.setErrorMessage(String.join("; ", errors));
            syncLogRepository.save(syncLog);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsSucceeded(succeeded);
            syncLog.setItemsFailed(failed);
            syncLog.setErrorMessage(e.getMessage());
            syncLogRepository.save(syncLog);
        }

        return SyncResult.builder()
                .syncLogId(syncLog.getId())
                .syncType("CUSTOMER_IMPORT")
                .status(syncLog.getStatus())
                .itemsProcessed(processed)
                .itemsSucceeded(succeeded)
                .itemsFailed(failed)
                .message(syncLog.getErrorMessage())
                .build();
    }

    private void importSingleCustomer(UUID tenantId, JsonNode customer) {
        String externalId = String.valueOf(customer.get("id").asInt());
        String email = customer.has("email") ? customer.get("email").asText() : null;
        if (email == null || email.isBlank()) return;

        String name = buildName(customer);
        String phone = customer.has("phone") ? customer.get("phone").asText() : null;

        Optional<NxCustomer> existing = customerRepository.findByTenantIdAndExternalId(tenantId, externalId);
        if (existing.isEmpty()) {
            existing = customerRepository.findByTenantIdAndEmail(tenantId, email);
        }

        if (existing.isPresent()) {
            NxCustomer customerEntity = existing.get();
            boolean changed = false;
            if (!name.equals(customerEntity.getName())) { customerEntity.setName(name); changed = true; }
            if (phone != null && !phone.equals(customerEntity.getPhone())) { customerEntity.setPhone(phone); changed = true; }
            if (customerEntity.getExternalId() == null) { customerEntity.setExternalId(externalId); changed = true; }
            if (changed) customerRepository.save(customerEntity);
            return;
        }

        Address address = Address.builder()
                .tenantId(tenantId)
                .addressType("PRIMARY")
                .build();
        if (customer.has("address")) {
            JsonNode addr = customer.get("address");
            address.setAddressLine1(addr.has("address1") ? addr.get("address1").asText() : null);
            address.setCity(addr.has("city") ? addr.get("city").asText() : null);
            address.setState(addr.has("state_or_province") ? addr.get("state_or_province").asText() : null);
            address.setPostalCode(addr.has("postal_code") ? addr.get("postal_code").asText() : null);
            address.setCountry(addr.has("country") ? addr.get("country").asText() : null);
        }
        Address savedAddress = addressRepository.save(address);

        customerRepository.save(NxCustomer.builder()
                .tenantId(tenantId)
                .externalId(externalId)
                .name(name)
                .email(email)
                .phone(phone)
                .addressId(savedAddress.getId())
                .build());
    }

    private String buildName(JsonNode customer) {
        String firstName = customer.has("first_name") ? customer.get("first_name").asText("") : "";
        String lastName = customer.has("last_name") ? customer.get("last_name").asText("") : "";
        String name = (firstName + " " + lastName).trim();
        return name.isEmpty() ? "BigCommerce Customer" : name;
    }
}
