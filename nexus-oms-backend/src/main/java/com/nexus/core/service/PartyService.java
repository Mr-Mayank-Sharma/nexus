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
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * People and organisations (OFBiz party model): a party has a type, roles, outside ids, and contacts (addresses,
 * phones, emails) that are dated and tagged with purposes such as SHIPPING_LOCATION or PRIMARY_EMAIL.
 */
@Service("corePartyService")
@CoreTransactional
public class PartyService {

    public static final String STATUS_TYPE = "PARTY_STATUS";

    static final String PERSON = "PERSON";
    static final String ORGANIZATION = "PARTY_GROUP";
    static final String EMAIL = "EMAIL_ADDRESS";
    static final String PHONE = "TELECOM_NUMBER";
    static final String POSTAL_ADDRESS = "POSTAL_ADDRESS";
    // Column lengths.
    private static final int MAX_PHONE_LENGTH = 63;
    private static final int MAX_USER_ID_LENGTH = 40;

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final GeoService geoService;

    public PartyService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, GeoService geoService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.geoService = geoService;
    }

    /** A person's name as given; one word goes to the first name. */
    public record PersonName(String firstName, String lastName) {

        /** Splits a full name at its last space: "Mary Ann Smith" is first name "Mary Ann", last name "Smith". */
        public static PersonName parse(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return new PersonName(null, null);
            }
            String normalised = fullName.trim().replaceAll("\\s+", " ");
            int lastSpace = normalised.lastIndexOf(' ');
            if (lastSpace < 0) {
                return new PersonName(normalised, null);
            }
            return new PersonName(normalised.substring(0, lastSpace), normalised.substring(lastSpace + 1));
        }
    }

    public record AddressInput(String toName, String address1, String address2, String city, String state,
                               String postalCode, String country, BigDecimal latitude, BigDecimal longitude, Boolean residential) {
    }

    // ── parties ──

    public String createPerson(PersonName name, String externalId) {
        Party party = newParty(PERSON, externalId);
        Person person = new Person();
        person.setPartyId(party.getPartyId());
        person.setFirstName(blankToNull(name.firstName()));
        person.setLastName(blankToNull(name.lastName()));
        entityManager.persist(person);
        return party.getPartyId();
    }

    public String createOrganization(String groupName, String externalId) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("An organisation needs a name");
        }
        Party party = newParty(ORGANIZATION, externalId);
        PartyGroup group = new PartyGroup();
        group.setPartyId(party.getPartyId());
        group.setGroupName(groupName.trim());
        entityManager.persist(group);
        return party.getPartyId();
    }

    /** Gives the party a role; a role it already has is kept. */
    public void addRole(String partyId, String roleTypeId) {
        boolean alreadyHasRole = entityManager.find(PartyRole.class, new PartyRole.Key(partyId, roleTypeId)) != null;
        if (alreadyHasRole) {
            return;
        }
        PartyRole role = new PartyRole();
        role.setPartyId(partyId);
        role.setRoleTypeId(roleTypeId);
        entityManager.persist(role);
    }

    /** Sets the party's outside id of a kind, replacing the earlier value; a blank value does nothing. */
    public void setIdentification(String partyId, String typeId, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        PartyIdentification identification = entityManager.find(PartyIdentification.class, new PartyIdentification.Key(partyId, typeId));
        if (identification == null) {
            identification = new PartyIdentification();
            identification.setPartyId(partyId);
            identification.setPartyIdentificationTypeId(typeId);
            identification.setIdValue(value.trim());
            entityManager.persist(identification);
        } else {
            identification.setIdValue(value.trim());
        }
    }

    @CoreTransactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        return entityManager.createQuery("SELECT i.partyId FROM PartyIdentification i WHERE i.partyIdentificationTypeId = :typeId AND i.idValue = :value",
                        String.class)
                .setParameter("typeId", typeId)
                .setParameter("value", value)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Enables or disables a party, checking the status flow and keeping the history. */
    public void setStatus(String partyId, String statusId) {
        Party party = entityManager.find(Party.class, partyId);
        if (party == null) {
            throw new IllegalArgumentException("Unknown party " + partyId);
        }
        statusService.requireAllowed(null, party.getStatusId(), statusId);
        party.setStatusId(statusId);
        recordStatus(partyId, statusId);
    }

    // ── contacts ──

    /** Adds an email (stored lower-case) with the given purposes and returns its contact id; null for a blank email. */
    public String addEmail(String partyId, String email, String... purposes) {
        if (email == null || email.isBlank()) {
            return null;
        }
        ContactMech contact = newContact(EMAIL, email.trim().toLowerCase(Locale.ROOT));
        linkToParty(partyId, contact.getContactMechId(), purposes);
        return contact.getContactMechId();
    }

    /** Adds a phone number (cut to the column's length) and returns its contact id; null for a blank number. */
    public String addPhone(String partyId, String number, String... purposes) {
        if (number == null || number.isBlank()) {
            return null;
        }
        ContactMech contact = newContact(PHONE, null);
        String trimmedNumber = number.trim();
        TelecomNumber phone = new TelecomNumber();
        phone.setContactMechId(contact.getContactMechId());
        phone.setContactNumber(trimmedNumber.length() > MAX_PHONE_LENGTH ? trimmedNumber.substring(0, MAX_PHONE_LENGTH) : trimmedNumber);
        entityManager.persist(phone);
        linkToParty(partyId, contact.getContactMechId(), purposes);
        return contact.getContactMechId();
    }

    /** Adds a postal address and returns its contact id. Country and state text are matched to geo ids when they can be. */
    public String addPostalAddress(String partyId, AddressInput input, String... purposes) {
        ContactMech contact = newContact(POSTAL_ADDRESS, null);
        PostalAddress address = new PostalAddress();
        address.setContactMechId(contact.getContactMechId());
        address.setToName(blankToNull(input.toName()));
        address.setAddress1(blankToNull(input.address1()));
        address.setAddress2(blankToNull(input.address2()));
        address.setCity(blankToNull(input.city()));
        address.setPostalCode(blankToNull(input.postalCode()));
        Optional<String> countryGeoId = geoService.countryId(input.country());
        address.setCountryGeoId(countryGeoId.orElse(null));
        address.setStateProvinceGeoId(countryGeoId.flatMap(country -> geoService.stateId(country, input.state())).orElse(null));
        address.setLatitude(input.latitude());
        address.setLongitude(input.longitude());
        address.setIsResidential(input.residential());
        entityManager.persist(address);
        linkToParty(partyId, contact.getContactMechId(), purposes);
        return contact.getContactMechId();
    }

    /** Current (not ended) contact ids of a party for a purpose, newest first. */
    @CoreTransactional(readOnly = true)
    public List<String> contacts(String partyId, String purposeTypeId) {
        return entityManager.createQuery("SELECT p.contactMechId FROM PartyContactMechPurpose p WHERE p.partyId = :partyId "
                        + "AND p.contactMechPurposeTypeId = :purpose AND p.thruDate IS NULL ORDER BY p.fromDate DESC", String.class)
                .setParameter("partyId", partyId)
                .setParameter("purpose", purposeTypeId)
                .getResultList();
    }

    /** Parties with this email as a current contact (matched case-insensitively). */
    @CoreTransactional(readOnly = true)
    public List<String> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return entityManager.createQuery("SELECT DISTINCT link.partyId FROM PartyContactMech link, ContactMech contact "
                        + "WHERE contact.contactMechId = link.contactMechId AND contact.contactMechTypeId = :emailType "
                        + "AND contact.infoString = :email AND link.thruDate IS NULL", String.class)
                .setParameter("emailType", EMAIL)
                .setParameter("email", email.trim().toLowerCase(Locale.ROOT))
                .getResultList();
    }

    // ── internals ──

    private Party newParty(String partyTypeId, String externalId) {
        Party party = new Party();
        party.setPartyId(sequenceService.nextId("Party"));
        party.setPartyTypeId(partyTypeId);
        party.setExternalId(blankToNull(externalId));
        party.setStatusId(statusService.initialStatus(null, STATUS_TYPE));
        entityManager.persist(party);
        recordStatus(party.getPartyId(), party.getStatusId());
        return party;
    }

    /** Writes a {@code party_status} row, by the signed-in user when there is one. */
    private void recordStatus(String partyId, String statusId) {
        PartyStatus history = new PartyStatus();
        history.setPartyId(partyId);
        history.setStatusId(statusId);
        history.setStatusDate(CoreClock.now());
        history.setChangeByUserId(signedInUser());
        entityManager.persist(history);
    }

    private static String signedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String name = authentication.getName();
        return name.substring(0, Math.min(MAX_USER_ID_LENGTH, name.length()));
    }

    private ContactMech newContact(String contactMechTypeId, String infoString) {
        ContactMech contact = new ContactMech();
        contact.setContactMechId(sequenceService.nextId("ContactMech"));
        contact.setContactMechTypeId(contactMechTypeId);
        contact.setInfoString(infoString);
        entityManager.persist(contact);
        return contact;
    }

    /** Links the contact to the party from now, with each purpose. */
    private void linkToParty(String partyId, String contactMechId, String... purposes) {
        LocalDateTime now = CoreClock.now();
        PartyContactMech link = new PartyContactMech();
        link.setPartyId(partyId);
        link.setContactMechId(contactMechId);
        link.setFromDate(now);
        entityManager.persist(link);
        for (String purpose : purposes) {
            PartyContactMechPurpose purposeRow = new PartyContactMechPurpose();
            purposeRow.setPartyId(partyId);
            purposeRow.setContactMechId(contactMechId);
            purposeRow.setContactMechPurposeTypeId(purpose);
            purposeRow.setFromDate(now);
            entityManager.persist(purposeRow);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
