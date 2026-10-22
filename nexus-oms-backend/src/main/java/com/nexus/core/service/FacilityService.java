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

    static final String DEFAULT_FACILITY_TYPE = "WAREHOUSE";
    private static final String FACILITY_ID_PATTERN = "[A-Za-z0-9_.-]{1,40}";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;

    public FacilityService(EntityManager entityManager, SequenceService sequenceService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
    }

    public record NewFacility(String facilityId, String facilityTypeId, String facilityName, String ownerPartyId,
                              String externalId, String postalCode, BigDecimal latitude, BigDecimal longitude,
                              LocalTime openTime, LocalTime closeTime, String timeZone, Long maximumOrderLimit) {
    }

    // ── facilities ──

    /** Creates a facility; a null id takes the next one from the sequence, a null type is a warehouse. */
    public String create(NewFacility newFacility) {
        if (newFacility.facilityName() == null || newFacility.facilityName().isBlank()) {
            throw new IllegalArgumentException("A facility needs a name");
        }
        String facilityId = newFacility.facilityId() == null ? sequenceService.nextId("Facility") : newFacility.facilityId();
        if (!facilityId.matches(FACILITY_ID_PATTERN)) {
            throw new IllegalArgumentException("A facility id is 1 to 40 letters, digits, dots, dashes or underscores, not '" + facilityId + "'");
        }
        if (entityManager.find(Facility.class, facilityId) != null) {
            throw new IllegalArgumentException("Facility " + facilityId + " already exists");
        }
        if (newFacility.maximumOrderLimit() != null && newFacility.maximumOrderLimit() < 0) {
            throw new IllegalArgumentException("A daily order limit can't be negative");
        }

        Facility facility = new Facility();
        facility.setFacilityId(facilityId);
        facility.setFacilityTypeId(newFacility.facilityTypeId() == null ? DEFAULT_FACILITY_TYPE : newFacility.facilityTypeId());
        facility.setFacilityName(newFacility.facilityName().trim());
        facility.setOwnerPartyId(newFacility.ownerPartyId());
        facility.setExternalId(newFacility.externalId());
        facility.setPostalCode(newFacility.postalCode());
        facility.setLatitude(newFacility.latitude());
        facility.setLongitude(newFacility.longitude());
        facility.setOpenTime(newFacility.openTime());
        facility.setCloseTime(newFacility.closeTime());
        facility.setFacilityTimeZone(newFacility.timeZone());
        facility.setMaximumOrderLimit(newFacility.maximumOrderLimit());
        facility.setOpenedDate(CoreClock.now());
        entityManager.persist(facility);
        return facilityId;
    }

    /** Sets the current outside id of a kind (ending any earlier one); a blank value does nothing. */
    public void setIdentification(String facilityId, String typeId, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmedValue = value.trim();
        List<FacilityIdentification> current = entityManager.createQuery("SELECT i FROM FacilityIdentification i WHERE i.facilityId = :facilityId "
                        + "AND i.facilityIdenTypeId = :typeId AND i.thruDate IS NULL", FacilityIdentification.class)
                .setParameter("facilityId", facilityId)
                .setParameter("typeId", typeId)
                .getResultList();
        boolean alreadySet = current.stream().anyMatch(identification -> identification.getIdValue().equals(trimmedValue));
        if (alreadySet) {
            return;
        }
        LocalDateTime now = CoreClock.now();
        current.forEach(identification -> identification.setThruDate(now));

        FacilityIdentification identification = new FacilityIdentification();
        identification.setFacilityIdenTypeId(typeId);
        identification.setFacilityId(facilityId);
        identification.setFromDate(now);
        identification.setIdValue(trimmedValue);
        entityManager.persist(identification);
    }

    /** The facility with this current outside id, newest first when several share it. */
    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return entityManager.createQuery("SELECT i.facilityId FROM FacilityIdentification i WHERE i.facilityIdenTypeId = :typeId "
                        + "AND i.idValue = :value AND i.thruDate IS NULL ORDER BY i.fromDate DESC", String.class)
                .setParameter("typeId", typeId)
                .setParameter("value", value.trim())
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    // ── groups ──

    /** The group of this type and name, created when missing. */
    public String group(String facilityGroupTypeId, String name) {
        List<String> existing = entityManager.createQuery("SELECT g.facilityGroupId FROM FacilityGroup g WHERE g.facilityGroupTypeId = :typeId "
                        + "AND g.facilityGroupName = :name", String.class)
                .setParameter("typeId", facilityGroupTypeId)
                .setParameter("name", name.trim())
                .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        FacilityGroup group = new FacilityGroup();
        group.setFacilityGroupId(sequenceService.nextId("FacilityGroup"));
        group.setFacilityGroupTypeId(facilityGroupTypeId);
        group.setFacilityGroupName(name.trim());
        entityManager.persist(group);
        return group.getFacilityGroupId();
    }

    /** Puts a facility in a group at a place in its order; a facility already in the group keeps its place. */
    public void addToGroup(String facilityId, String facilityGroupId, Long sequenceNum) {
        if (members(facilityGroupId).contains(facilityId)) {
            return;
        }
        FacilityGroupMember member = new FacilityGroupMember();
        member.setFacilityId(facilityId);
        member.setFacilityGroupId(facilityGroupId);
        member.setFromDate(CoreClock.now());
        member.setSequenceNum(sequenceNum);
        entityManager.persist(member);
    }

    /** Ends the facility's current membership of the group. */
    public void removeFromGroup(String facilityId, String facilityGroupId) {
        LocalDateTime now = CoreClock.now();
        entityManager.createQuery("SELECT m FROM FacilityGroupMember m WHERE m.facilityId = :facilityId AND m.facilityGroupId = :groupId "
                        + "AND m.thruDate IS NULL", FacilityGroupMember.class)
                .setParameter("facilityId", facilityId)
                .setParameter("groupId", facilityGroupId)
                .getResultList()
                .forEach(member -> member.setThruDate(now));
    }

    /** A group's current facilities in order. */
    @Transactional(readOnly = true)
    public List<String> members(String facilityGroupId) {
        LocalDateTime now = CoreClock.now();
        return entityManager.createQuery("SELECT m.facilityId FROM FacilityGroupMember m WHERE m.facilityGroupId = :groupId AND m.fromDate <= :now "
                        + "AND (m.thruDate IS NULL OR m.thruDate > :now) ORDER BY m.sequenceNum, m.fromDate", String.class)
                .setParameter("groupId", facilityGroupId)
                .setParameter("now", now)
                .getResultList();
    }

    /** A group with the names of its current members, in order. */
    public record GroupSummary(String facilityGroupId, String name, String facilityGroupTypeId, List<String> memberNames) {
    }

    /** Every facility group, by name, with its members. */
    @Transactional(readOnly = true)
    public List<GroupSummary> groupSummaries() {
        List<FacilityGroup> groups = entityManager.createQuery("SELECT g FROM FacilityGroup g ORDER BY g.facilityGroupName", FacilityGroup.class)
                .getResultList();
        return groups.stream()
                .map(group -> new GroupSummary(group.getFacilityGroupId(), group.getFacilityGroupName(), group.getFacilityGroupTypeId(),
                        memberNames(group.getFacilityGroupId())))
                .toList();
    }

    private List<String> memberNames(String facilityGroupId) {
        return entityManager.createQuery("SELECT f.facilityName FROM FacilityGroupMember m, Facility f WHERE f.facilityId = m.facilityId "
                        + "AND m.facilityGroupId = :groupId AND m.thruDate IS NULL ORDER BY m.sequenceNum", String.class)
                .setParameter("groupId", facilityGroupId)
                .getResultList();
    }

    // ── stores ──

    /** Lets a facility serve a store's orders; an existing current link is kept. */
    public void linkStore(String productStoreId, String facilityId, Long sequenceNum) {
        if (storeFacilities(productStoreId).contains(facilityId)) {
            return;
        }
        ProductStoreFacility link = new ProductStoreFacility();
        link.setProductStoreId(productStoreId);
        link.setFacilityId(facilityId);
        link.setFromDate(CoreClock.now());
        link.setSequenceNum(sequenceNum);
        entityManager.persist(link);
    }

    /** The facilities that serve a store now, in order. */
    @Transactional(readOnly = true)
    public List<String> storeFacilities(String productStoreId) {
        LocalDateTime now = CoreClock.now();
        return entityManager.createQuery("SELECT s.facilityId FROM ProductStoreFacility s WHERE s.productStoreId = :storeId AND s.fromDate <= :now "
                        + "AND (s.thruDate IS NULL OR s.thruDate > :now) ORDER BY s.sequenceNum, s.fromDate", String.class)
                .setParameter("storeId", productStoreId)
                .setParameter("now", now)
                .getResultList();
    }

    // ── daily order limit ──

    /**
     * Counts one more order at the facility for the day, unless that would pass its daily limit. Returns false
     * (and counts nothing) when the facility is full for the day. The day's row is locked, so two orders can't
     * both take the last place.
     */
    public boolean takeOrder(String facilityId, LocalDate day) {
        Facility facility = loadFacility(facilityId);
        FacilityOrderCount dayCount = lockDayCount(facilityId, day);
        long ordersTaken = dayCount == null ? 0 : dayCount.getLastOrderCount();
        boolean full = facility.getMaximumOrderLimit() != null && ordersTaken >= facility.getMaximumOrderLimit();
        if (full) {
            return false;
        }
        incrementDayCount(dayCount, facilityId, day);
        return true;
    }

    /** Counts an order at the facility for the day even past its limit (a person chose it, or a rule ignores limits). */
    public void countOrder(String facilityId, LocalDate day) {
        incrementDayCount(lockDayCount(facilityId, day), facilityId, day);
    }

    /** Gives back an order's place for the day (the order was moved or cancelled). */
    public void releaseOrder(String facilityId, LocalDate day) {
        FacilityOrderCount dayCount = lockDayCount(facilityId, day);
        if (dayCount != null && dayCount.getLastOrderCount() > 0) {
            dayCount.setLastOrderCount(dayCount.getLastOrderCount() - 1);
        }
    }

    /** Orders the facility can still take on the day; empty when it has no limit. */
    @Transactional(readOnly = true)
    public Optional<Long> ordersLeft(String facilityId, LocalDate day) {
        Facility facility = loadFacility(facilityId);
        if (facility.getMaximumOrderLimit() == null) {
            return Optional.empty();
        }
        FacilityOrderCount dayCount = entityManager.find(FacilityOrderCount.class, new FacilityOrderCount.Key(facilityId, day));
        long ordersTaken = dayCount == null ? 0 : dayCount.getLastOrderCount();
        return Optional.of(Math.max(0, facility.getMaximumOrderLimit() - ordersTaken));
    }

    // ── internals ──

    private Facility loadFacility(String facilityId) {
        Facility facility = entityManager.find(Facility.class, facilityId);
        if (facility == null) {
            throw new IllegalArgumentException("Unknown facility " + facilityId);
        }
        return facility;
    }

    /** The facility's count for the day, locked until the transaction ends; null when it has taken no order yet. */
    private FacilityOrderCount lockDayCount(String facilityId, LocalDate day) {
        return entityManager.find(FacilityOrderCount.class, new FacilityOrderCount.Key(facilityId, day), LockModeType.PESSIMISTIC_WRITE);
    }

    private void incrementDayCount(FacilityOrderCount dayCount, String facilityId, LocalDate day) {
        if (dayCount == null) {
            FacilityOrderCount firstOrder = new FacilityOrderCount();
            firstOrder.setFacilityId(facilityId);
            firstOrder.setEntryDate(day);
            firstOrder.setLastOrderCount(1L);
            entityManager.persist(firstOrder);
        } else {
            dayCount.setLastOrderCount(dayCount.getLastOrderCount() + 1);
        }
    }
}
