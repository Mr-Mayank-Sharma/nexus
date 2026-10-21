package com.nexus.core.service;

import com.nexus.core.model.basic.Enumeration;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coded lists (sales channels, reasons, types) held as data in {@code enumeration}. */
@Service("coreEnumerationService")
@Transactional(readOnly = true)
public class EnumerationService {

    private final EntityManager em;

    public EnumerationService(EntityManager em) {
        this.em = em;
    }

    public List<Enumeration> list(String enumTypeId) {
        return em.createQuery("SELECT e FROM Enumeration e WHERE e.enumTypeId = :t ORDER BY e.sequenceNum, e.enumId", Enumeration.class)
                .setParameter("t", enumTypeId).getResultList();
    }

    /** The enumeration, checked to belong to the expected list; throws a readable error otherwise. */
    public Enumeration require(String enumTypeId, String enumId) {
        Enumeration e = enumId == null ? null : em.find(Enumeration.class, enumId);
        if (e == null || !enumTypeId.equals(e.getEnumTypeId())) {
            throw new IllegalArgumentException("'" + enumId + "' is not a valid value for " + enumTypeId + ". Valid values: "
                    + String.join(", ", list(enumTypeId).stream().map(Enumeration::getEnumId).toList()));
        }
        return e;
    }
}
