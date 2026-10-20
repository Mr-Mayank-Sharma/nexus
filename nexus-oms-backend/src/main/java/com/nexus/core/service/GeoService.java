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
 * into geo ids. Unknown text returns empty rather than a guess.
 */
@Service
@Transactional(readOnly = true)
public class GeoService {

    private final EntityManager em;

    public GeoService(EntityManager em) {
        this.em = em;
    }

    public Optional<String> countryId(String text) {
        if (blank(text)) return Optional.empty();
        String t = text.trim();
        List<Geo> found = em.createQuery("SELECT g FROM Geo g WHERE g.geoTypeEnumId = 'GEOT_COUNTRY' AND (UPPER(g.geoId) = :u "
                        + "OR UPPER(g.geoCodeAlpha2) = :u OR UPPER(g.geoCodeAlpha3) = :u OR LOWER(g.geoName) = :l)", Geo.class)
                .setParameter("u", t.toUpperCase(Locale.ROOT)).setParameter("l", t.toLowerCase(Locale.ROOT)).getResultList();
        return found.size() == 1 ? Optional.of(found.get(0).getGeoId()) : Optional.empty();
    }

    /** A state inside the given country, by code ("NJ"), full id ("US-NJ") or name ("New Jersey"). */
    public Optional<String> stateId(String countryGeoId, String text) {
        if (blank(text) || blank(countryGeoId)) return Optional.empty();
        String t = text.trim();
        List<Geo> found = em.createQuery("SELECT g FROM Geo g, GeoAssoc a WHERE a.geoId = :c AND a.toGeoId = g.geoId "
                        + "AND (UPPER(g.geoId) = :u OR UPPER(g.abbreviation) = :u OR LOWER(g.geoName) = :l)", Geo.class)
                .setParameter("c", countryGeoId).setParameter("u", t.toUpperCase(Locale.ROOT))
                .setParameter("l", t.toLowerCase(Locale.ROOT)).getResultList();
        return found.size() == 1 ? Optional.of(found.get(0).getGeoId()) : Optional.empty();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
