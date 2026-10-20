package com.nexus.core.service;

import com.nexus.core.model.party.ContactMech;
import com.nexus.core.model.party.Party;
import com.nexus.core.model.party.PartyContactMech;
import com.nexus.core.model.party.PartyContactMechPurpose;
import com.nexus.core.model.party.PartyGroup;
import com.nexus.core.model.party.PartyIdentification;
import com.nexus.core.model.party.PartyRole;
import com.nexus.core.model.party.PartyStatus;
import com.nexus.core.model.party.Person;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.model.party.TelecomNumber;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * People and organisations (OFBiz party model): a party has a type, roles, outside ids, and contacts (addresses,
 * phones, emails) that are dated and tagged with purposes such as SHIPPING_LOCATION or PRIMARY_EMAIL.
 */
@Service
@Transactional
public class PartyService {

    public static final String STATUS_TYPE = "PARTY_STATUS";

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final GeoService geos;

    public PartyService(EntityManager em, SequenceService sequences, StatusService statuses, GeoService geos) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.geos = geos;
    }

    /** A person's name as given; one word goes to the first name. */
    public record PersonName(String firstName, String lastName) {
        public static PersonName parse(String full) {
            if (full == null || full.isBlank()) return new PersonName(null, null);
            String t = full.trim().replaceAll("\\s+", " ");
            int i = t.lastIndexOf(' ');
            return i < 0 ? new PersonName(t, null) : new PersonName(t.substring(0, i), t.substring(i + 1));
        }
    }

    public record AddressInput(String toName, String address1, String address2, String city, String state,
                               String postalCode, String country, BigDecimal latitude, BigDecimal longitude, Boolean residential) {
    }

    public String createPerson(PersonName name, String externalId) {
        Party p = newParty("PERSON", externalId);
        Person person = new Person();
        person.setPartyId(p.getPartyId());
        person.setFirstName(trim(name.firstName()));
        person.setLastName(trim(name.lastName()));
        em.persist(person);
        return p.getPartyId();
    }

    public String createOrganization(String groupName, String externalId) {
        if (groupName == null || groupName.isBlank()) throw new IllegalArgumentException("An organisation needs a name");
        Party p = newParty("PARTY_GROUP", externalId);
        PartyGroup g = new PartyGroup();
        g.setPartyId(p.getPartyId());
        g.setGroupName(groupName.trim());
        em.persist(g);
        return p.getPartyId();
    }

    public void addRole(String partyId, String roleTypeId) {
        if (em.find(PartyRole.class, new PartyRole.Key(partyId, roleTypeId)) == null) {
            PartyRole r = new PartyRole();
            r.setPartyId(partyId);
            r.setRoleTypeId(roleTypeId);
            em.persist(r);
        }
    }

    public void setIdentification(String partyId, String typeId, String value) {
        if (value == null || value.isBlank()) return;
        PartyIdentification id = em.find(PartyIdentification.class, new PartyIdentification.Key(partyId, typeId));
        if (id == null) {
            id = new PartyIdentification();
            id.setPartyId(partyId);
            id.setPartyIdentificationTypeId(typeId);
            id.setIdValue(value.trim());
            em.persist(id);
        } else {
            id.setIdValue(value.trim());
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        List<String> ids = em.createQuery("SELECT i.partyId FROM PartyIdentification i WHERE i.partyIdentificationTypeId = :t AND i.idValue = :v", String.class)
                .setParameter("t", typeId).setParameter("v", value).getResultList();
        return ids.stream().findFirst();
    }

    /** Adds an email (stored lower-case) with the given purposes and returns its contact id. */
    public String addEmail(String partyId, String email, String... purposes) {
        if (email == null || email.isBlank()) return null;
        ContactMech cm = newContact("EMAIL_ADDRESS", email.trim().toLowerCase(Locale.ROOT));
        link(partyId, cm.getContactMechId(), purposes);
        return cm.getContactMechId();
    }

    public String addPhone(String partyId, String number, String... purposes) {
        if (number == null || number.isBlank()) return null;
        ContactMech cm = newContact("TELECOM_NUMBER", null);
        TelecomNumber t = new TelecomNumber();
        t.setContactMechId(cm.getContactMechId());
        t.setContactNumber(number.trim().length() > 63 ? number.trim().substring(0, 63) : number.trim());
        em.persist(t);
        link(partyId, cm.getContactMechId(), purposes);
        return cm.getContactMechId();
    }

    /** Adds a postal address; country and state text are matched to geo ids when they can be. */
    public String addPostalAddress(String partyId, AddressInput a, String... purposes) {
        ContactMech cm = newContact("POSTAL_ADDRESS", null);
        PostalAddress pa = new PostalAddress();
        pa.setContactMechId(cm.getContactMechId());
        pa.setToName(trim(a.toName()));
        pa.setAddress1(trim(a.address1()));
        pa.setAddress2(trim(a.address2()));
        pa.setCity(trim(a.city()));
        pa.setPostalCode(trim(a.postalCode()));
        Optional<String> country = geos.countryId(a.country());
        pa.setCountryGeoId(country.orElse(null));
        pa.setStateProvinceGeoId(country.flatMap(c -> geos.stateId(c, a.state())).orElse(null));
        pa.setLatitude(a.latitude());
        pa.setLongitude(a.longitude());
        pa.setIsResidential(a.residential());
        em.persist(pa);
        link(partyId, cm.getContactMechId(), purposes);
        return cm.getContactMechId();
    }

    /** Current (not ended) contact ids of a party for a purpose, newest first. */
    @Transactional(readOnly = true)
    public List<String> contacts(String partyId, String purposeTypeId) {
        return em.createQuery("SELECT p.contactMechId FROM PartyContactMechPurpose p WHERE p.partyId = :party "
                        + "AND p.contactMechPurposeTypeId = :purpose AND p.thruDate IS NULL ORDER BY p.fromDate DESC", String.class)
                .setParameter("party", partyId).setParameter("purpose", purposeTypeId).getResultList();
    }

    @Transactional(readOnly = true)
    public List<String> findByEmail(String email) {
        if (email == null || email.isBlank()) return List.of();
        return em.createQuery("SELECT DISTINCT pcm.partyId FROM PartyContactMech pcm, ContactMech cm WHERE cm.contactMechId = pcm.contactMechId "
                        + "AND cm.contactMechTypeId = 'EMAIL_ADDRESS' AND cm.infoString = :e AND pcm.thruDate IS NULL", String.class)
                .setParameter("e", email.trim().toLowerCase(Locale.ROOT)).getResultList();
    }

    /** Enables or disables a party, checking the status flow and keeping the history. */
    public void setStatus(String partyId, String statusId) {
        Party p = em.find(Party.class, partyId);
        if (p == null) throw new IllegalArgumentException("Unknown party " + partyId);
        statuses.requireAllowed(null, p.getStatusId(), statusId);
        p.setStatusId(statusId);
        recordStatus(partyId, statusId);
    }

    private Party newParty(String typeId, String externalId) {
        Party p = new Party();
        p.setPartyId(sequences.nextId("Party"));
        p.setPartyTypeId(typeId);
        p.setExternalId(trim(externalId));
        p.setStatusId(statuses.initialStatus(null, STATUS_TYPE));
        em.persist(p);
        recordStatus(p.getPartyId(), p.getStatusId());
        return p;
    }

    private void recordStatus(String partyId, String statusId) {
        PartyStatus s = new PartyStatus();
        s.setPartyId(partyId);
        s.setStatusId(statusId);
        s.setStatusDate(LocalDateTime.now());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        s.setChangeByUserId(auth == null || auth.getName() == null ? null : auth.getName().substring(0, Math.min(40, auth.getName().length())));
        em.persist(s);
    }

    private ContactMech newContact(String typeId, String infoString) {
        ContactMech cm = new ContactMech();
        cm.setContactMechId(sequences.nextId("ContactMech"));
        cm.setContactMechTypeId(typeId);
        cm.setInfoString(infoString);
        em.persist(cm);
        return cm;
    }

    private void link(String partyId, String contactMechId, String... purposes) {
        LocalDateTime now = LocalDateTime.now();
        PartyContactMech pcm = new PartyContactMech();
        pcm.setPartyId(partyId);
        pcm.setContactMechId(contactMechId);
        pcm.setFromDate(now);
        em.persist(pcm);
        for (String purpose : purposes) {
            PartyContactMechPurpose pp = new PartyContactMechPurpose();
            pp.setPartyId(partyId);
            pp.setContactMechId(contactMechId);
            pp.setContactMechPurposeTypeId(purpose);
            pp.setFromDate(now);
            em.persist(pp);
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
