package com.nexus.core.service;

import com.nexus.core.model.basic.Enumeration;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.util.List;

/** Coded lists (sales channels, reasons, types) held as data in {@code enumeration}. */
@Service("coreEnumerationService")
@CoreTransactional(readOnly = true)
public class EnumerationService {

    private final EntityManager entityManager;

    public EnumerationService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The values of a list, in order. */
    public List<Enumeration> list(String enumTypeId) {
        return entityManager.createQuery("SELECT e FROM Enumeration e WHERE e.enumTypeId = :typeId ORDER BY e.sequenceNum, e.enumId", Enumeration.class)
                .setParameter("typeId", enumTypeId)
                .getResultList();
    }

    /** The enumeration, checked to belong to the expected list; otherwise an error naming the valid values. */
    public Enumeration require(String enumTypeId, String enumId) {
        Enumeration enumeration = enumId == null ? null : entityManager.find(Enumeration.class, enumId);
        boolean inList = enumeration != null && enumTypeId.equals(enumeration.getEnumTypeId());
        if (!inList) {
            List<String> validValues = list(enumTypeId).stream().map(Enumeration::getEnumId).toList();
            throw new IllegalArgumentException("'" + enumId + "' is not a valid value for " + enumTypeId + ". Valid values: " + String.join(", ", validValues));
        }
        return enumeration;
    }
}
