package com.nexus.core.service;

import com.nexus.core.model.entity.SystemProperty;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Settings stored as data ({@code system_property}), grouped by resource, for example ("routing", "max.splits"). */
@Service("coreSystemPropertyService")
public class SystemPropertyService {

    private final EntityManager entityManager;

    public SystemPropertyService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The setting; empty when it is not set or blank. */
    @Transactional(readOnly = true)
    public Optional<String> get(String resource, String propertyId) {
        SystemProperty property = entityManager.find(SystemProperty.class, new SystemProperty.Key(resource, propertyId));
        return Optional.ofNullable(property).map(SystemProperty::getSystemPropertyValue).filter(value -> !value.isBlank());
    }

    public String get(String resource, String propertyId, String fallback) {
        return get(resource, propertyId).orElse(fallback);
    }

    /** A whole-number setting, or the fallback when it is not set; a setting that isn't a number is an error. */
    public long getLong(String resource, String propertyId, long fallback) {
        return get(resource, propertyId).map(value -> {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalStateException("Setting " + resource + "/" + propertyId + " should be a whole number, but is '" + value + "'");
            }
        }).orElse(fallback);
    }

    /** "true" or "Y" (any case) is true; anything else set is false; not set is the fallback. */
    public boolean getBoolean(String resource, String propertyId, boolean fallback) {
        return get(resource, propertyId)
                .map(value -> value.trim().equalsIgnoreCase("true") || value.trim().equalsIgnoreCase("Y"))
                .orElse(fallback);
    }

    /** Sets the setting, creating it when missing; a null description keeps the one it had. */
    @Transactional
    public void set(String resource, String propertyId, String value, String description) {
        SystemProperty property = entityManager.find(SystemProperty.class, new SystemProperty.Key(resource, propertyId));
        if (property == null) {
            property = new SystemProperty();
            property.setSystemResourceId(resource);
            property.setSystemPropertyId(propertyId);
            property.setSystemPropertyValue(value);
            property.setDescription(description);
            entityManager.persist(property);
        } else {
            property.setSystemPropertyValue(value);
            if (description != null) {
                property.setDescription(description);
            }
        }
    }
}
