package com.nexus.core.service;

import com.nexus.core.model.basic.Geo;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns the free text channels and old records hold ("US", "United States", "usa"; "NJ", "New Jersey", "US-NJ")
 * into geo ids. Unknown or ambiguous text returns empty rather than a guess.
 */
@Service("coreGeoService")
@Transactional(readOnly = true)
public class GeoService {

    private final EntityManager entityManager;

    public GeoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** A country by id, two- or three-letter code, or name, matched without case. */
    public Optional<String> countryId(String text) {
        if (isBlank(text)) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        List<Geo> matches = entityManager.createQuery("SELECT g FROM Geo g WHERE g.geoTypeEnumId = 'GEOT_COUNTRY' AND (UPPER(g.geoId) = :upperText "
                        + "OR UPPER(g.geoCodeAlpha2) = :upperText OR UPPER(g.geoCodeAlpha3) = :upperText OR LOWER(g.geoName) = :lowerText)", Geo.class)
                .setParameter("upperText", trimmed.toUpperCase(Locale.ROOT))
                .setParameter("lowerText", trimmed.toLowerCase(Locale.ROOT))
                .getResultList();
        return onlyMatch(matches);
    }

    /** A state inside the given country, by code ("NJ"), full id ("US-NJ") or name ("New Jersey"). */
    public Optional<String> stateId(String countryGeoId, String text) {
        if (isBlank(text) || isBlank(countryGeoId)) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        List<Geo> matches = entityManager.createQuery("SELECT g FROM Geo g, GeoAssoc a WHERE a.geoId = :countryId AND a.toGeoId = g.geoId "
                        + "AND (UPPER(g.geoId) = :upperText OR UPPER(g.abbreviation) = :upperText OR LOWER(g.geoName) = :lowerText)", Geo.class)
                .setParameter("countryId", countryGeoId)
                .setParameter("upperText", trimmed.toUpperCase(Locale.ROOT))
                .setParameter("lowerText", trimmed.toLowerCase(Locale.ROOT))
                .getResultList();
        return onlyMatch(matches);
    }

    private static Optional<String> onlyMatch(List<Geo> matches) {
        return matches.size() == 1 ? Optional.of(matches.get(0).getGeoId()) : Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
