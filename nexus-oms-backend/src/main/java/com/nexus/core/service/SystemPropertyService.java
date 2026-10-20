package com.nexus.core.service;

import com.nexus.core.model.entity.SystemProperty;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Settings stored as data ({@code system_property}), grouped by resource, for example ("routing", "max.splits"). */
@Service
public class SystemPropertyService {

    private final EntityManager em;

    public SystemPropertyService(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String resource, String propertyId) {
        SystemProperty p = em.find(SystemProperty.class, new SystemProperty.Key(resource, propertyId));
        return Optional.ofNullable(p).map(SystemProperty::getSystemPropertyValue).filter(v -> !v.isBlank());
    }

    public String get(String resource, String propertyId, String fallback) {
        return get(resource, propertyId).orElse(fallback);
    }

    public long getLong(String resource, String propertyId, long fallback) {
        return get(resource, propertyId).map(v -> {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Setting " + resource + "/" + propertyId + " should be a whole number, but is '" + v + "'");
            }
        }).orElse(fallback);
    }

    public boolean getBoolean(String resource, String propertyId, boolean fallback) {
        return get(resource, propertyId).map(v -> v.trim().equalsIgnoreCase("true") || v.trim().equalsIgnoreCase("Y")).orElse(fallback);
    }

    @Transactional
    public void set(String resource, String propertyId, String value, String description) {
        SystemProperty p = em.find(SystemProperty.class, new SystemProperty.Key(resource, propertyId));
        if (p == null) {
            p = new SystemProperty();
            p.setSystemResourceId(resource);
            p.setSystemPropertyId(propertyId);
            p.setSystemPropertyValue(value);
            p.setDescription(description);
            em.persist(p);
        } else {
            p.setSystemPropertyValue(value);
            if (description != null) p.setDescription(description);
        }
    }
}
