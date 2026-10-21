package com.nexus.core.service;

import com.nexus.core.model.facility.Facility;
import com.nexus.core.model.facility.FacilityGroup;
import com.nexus.core.model.facility.FacilityGroupMember;
import com.nexus.core.model.facility.FacilityIdentification;
import com.nexus.core.model.facility.FacilityOrderCount;
import com.nexus.core.model.facility.ProductStoreFacility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Facilities (stores, warehouses, 3PLs), their outside ids, the groups routing targets in order, the stores they
 * serve, and the daily order limit (HotWax {@code FacilityOrderCount}): a facility with a {@code maximumOrderLimit}
 * takes at most that many orders a day.
 */
@Service("coreFacilityService")
@Transactional
public class FacilityService {

    private final EntityManager em;
    private final SequenceService sequences;

    public FacilityService(EntityManager em, SequenceService sequences) {
        this.em = em;
        this.sequences = sequences;
    }

    public record NewFacility(String facilityId, String facilityTypeId, String facilityName, String ownerPartyId,
                              String externalId, String postalCode, BigDecimal latitude, BigDecimal longitude,
                              LocalTime openTime, LocalTime closeTime, String timeZone, Long maximumOrderLimit) {
    }

    /** Creates a facility; a null id takes the next one from the sequence. */
    public String create(NewFacility n) {
        if (n.facilityName() == null || n.facilityName().isBlank()) throw new IllegalArgumentException("A facility needs a name");
        String id = n.facilityId() == null ? sequences.nextId("Facility") : n.facilityId();
        if (!id.matches("[A-Za-z0-9_.-]{1,40}")) {
            throw new IllegalArgumentException("A facility id is 1 to 40 letters, digits, dots, dashes or underscores, not '" + id + "'");
        }
        if (em.find(Facility.class, id) != null) throw new IllegalArgumentException("Facility " + id + " already exists");
        if (n.maximumOrderLimit() != null && n.maximumOrderLimit() < 0) throw new IllegalArgumentException("A daily order limit can't be negative");
        Facility f = new Facility();
        f.setFacilityId(id);
        f.setFacilityTypeId(n.facilityTypeId() == null ? "WAREHOUSE" : n.facilityTypeId());
        f.setFacilityName(n.facilityName().trim());
        f.setOwnerPartyId(n.ownerPartyId());
        f.setExternalId(n.externalId());
        f.setPostalCode(n.postalCode());
        f.setLatitude(n.latitude());
        f.setLongitude(n.longitude());
        f.setOpenTime(n.openTime());
        f.setCloseTime(n.closeTime());
        f.setFacilityTimeZone(n.timeZone());
        f.setMaximumOrderLimit(n.maximumOrderLimit());
        f.setOpenedDate(CoreClock.now());
        em.persist(f);
        return id;
    }

    /** Sets the current outside id of a kind (ending any earlier one); a blank value does nothing. */
    public void setIdentification(String facilityId, String typeId, String value) {
        if (value == null || value.isBlank()) return;
        List<FacilityIdentification> current = em.createQuery("SELECT i FROM FacilityIdentification i WHERE i.facilityId = :f "
                        + "AND i.facilityIdenTypeId = :t AND i.thruDate IS NULL", FacilityIdentification.class)
                .setParameter("f", facilityId).setParameter("t", typeId).getResultList();
        if (current.stream().anyMatch(i -> i.getIdValue().equals(value.trim()))) return;
        LocalDateTime now = CoreClock.now();
        current.forEach(i -> i.setThruDate(now));
        FacilityIdentification i = new FacilityIdentification();
        i.setFacilityIdenTypeId(typeId);
        i.setFacilityId(facilityId);
        i.setFromDate(now);
        i.setIdValue(value.trim());
        em.persist(i);
    }

    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return em.createQuery("SELECT i.facilityId FROM FacilityIdentification i WHERE i.facilityIdenTypeId = :t "
                        + "AND i.idValue = :v AND i.thruDate IS NULL ORDER BY i.fromDate DESC", String.class)
                .setParameter("t", typeId).setParameter("v", value.trim()).setMaxResults(1).getResultList().stream().findFirst();
    }

    /** The group of this type and name, created when missing. */
    public String group(String facilityGroupTypeId, String name) {
        List<String> found = em.createQuery("SELECT g.facilityGroupId FROM FacilityGroup g WHERE g.facilityGroupTypeId = :t "
                        + "AND g.facilityGroupName = :n", String.class)
                .setParameter("t", facilityGroupTypeId).setParameter("n", name.trim()).getResultList();
        if (!found.isEmpty()) return found.get(0);
        FacilityGroup g = new FacilityGroup();
        g.setFacilityGroupId(sequences.nextId("FacilityGroup"));
        g.setFacilityGroupTypeId(facilityGroupTypeId);
        g.setFacilityGroupName(name.trim());
        em.persist(g);
        return g.getFacilityGroupId();
    }

    /** Puts a facility in a group at a place in its order; a facility already in the group keeps its place. */
    public void addToGroup(String facilityId, String facilityGroupId, Long sequenceNum) {
        if (members(facilityGroupId).contains(facilityId)) return;
        FacilityGroupMember m = new FacilityGroupMember();
        m.setFacilityId(facilityId);
        m.setFacilityGroupId(facilityGroupId);
        m.setFromDate(CoreClock.now());
        m.setSequenceNum(sequenceNum);
        em.persist(m);
    }

    public void removeFromGroup(String facilityId, String facilityGroupId) {
        LocalDateTime now = CoreClock.now();
        em.createQuery("SELECT m FROM FacilityGroupMember m WHERE m.facilityId = :f AND m.facilityGroupId = :g "
                        + "AND m.thruDate IS NULL", FacilityGroupMember.class)
                .setParameter("f", facilityId).setParameter("g", facilityGroupId).getResultList()
                .forEach(m -> m.setThruDate(now));
    }

    /** A group's current facilities in order. */
    @Transactional(readOnly = true)
    public List<String> members(String facilityGroupId) {
        LocalDateTime now = CoreClock.now();
        return em.createQuery("SELECT m.facilityId FROM FacilityGroupMember m WHERE m.facilityGroupId = :g AND m.fromDate <= :now "
                        + "AND (m.thruDate IS NULL OR m.thruDate > :now) ORDER BY m.sequenceNum, m.fromDate", String.class)
                .setParameter("g", facilityGroupId).setParameter("now", now).getResultList();
    }

    /** Lets a facility serve a store's orders; an existing current link is kept. */
    public void linkStore(String productStoreId, String facilityId, Long sequenceNum) {
        if (storeFacilities(productStoreId).contains(facilityId)) return;
        ProductStoreFacility s = new ProductStoreFacility();
        s.setProductStoreId(productStoreId);
        s.setFacilityId(facilityId);
        s.setFromDate(CoreClock.now());
        s.setSequenceNum(sequenceNum);
        em.persist(s);
    }

    @Transactional(readOnly = true)
    public List<String> storeFacilities(String productStoreId) {
        LocalDateTime now = CoreClock.now();
        return em.createQuery("SELECT s.facilityId FROM ProductStoreFacility s WHERE s.productStoreId = :p AND s.fromDate <= :now "
                        + "AND (s.thruDate IS NULL OR s.thruDate > :now) ORDER BY s.sequenceNum, s.fromDate", String.class)
                .setParameter("p", productStoreId).setParameter("now", now).getResultList();
    }

    /**
     * Counts one more order at the facility for the day, unless that would pass its daily limit. Returns false
     * (and counts nothing) when the facility is full for the day. The day's row is locked, so two orders can't
     * both take the last place.
     */
    public boolean takeOrder(String facilityId, LocalDate day) {
        Facility f = em.find(Facility.class, facilityId);
        if (f == null) throw new IllegalArgumentException("Unknown facility " + facilityId);
        FacilityOrderCount c = em.find(FacilityOrderCount.class, new FacilityOrderCount.Key(facilityId, day), LockModeType.PESSIMISTIC_WRITE);
        long taken = c == null ? 0 : c.getLastOrderCount();
        if (f.getMaximumOrderLimit() != null && taken >= f.getMaximumOrderLimit()) return false;
        if (c == null) {
            c = new FacilityOrderCount();
            c.setFacilityId(facilityId);
            c.setEntryDate(day);
            c.setLastOrderCount(1L);
            em.persist(c);
        } else {
            c.setLastOrderCount(taken + 1);
        }
        return true;
    }

    /** Gives back an order's place for the day (the order was moved or cancelled). */
    public void releaseOrder(String facilityId, LocalDate day) {
        FacilityOrderCount c = em.find(FacilityOrderCount.class, new FacilityOrderCount.Key(facilityId, day), LockModeType.PESSIMISTIC_WRITE);
        if (c != null && c.getLastOrderCount() > 0) c.setLastOrderCount(c.getLastOrderCount() - 1);
    }

    /** Orders the facility can still take on the day; empty when it has no limit. */
    @Transactional(readOnly = true)
    public Optional<Long> ordersLeft(String facilityId, LocalDate day) {
        Facility f = em.find(Facility.class, facilityId);
        if (f == null) throw new IllegalArgumentException("Unknown facility " + facilityId);
        if (f.getMaximumOrderLimit() == null) return Optional.empty();
        FacilityOrderCount c = em.find(FacilityOrderCount.class, new FacilityOrderCount.Key(facilityId, day));
        return Optional.of(Math.max(0, f.getMaximumOrderLimit() - (c == null ? 0 : c.getLastOrderCount())));
    }
}
