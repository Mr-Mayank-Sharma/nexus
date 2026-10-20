"""
The Nexus core data model, aligned with OFBiz and Moqui (docs/23-DATA-MODEL-OFBIZ-ALIGNED.md).

This file is the single source of truth. `python3 scripts/gen_core_model.py` turns it into:
  - nexus-oms-backend/src/main/resources/db/core/postgresql/V<n>__<name>.sql
  - nexus-oms-backend/src/main/resources/db/core/mysql/V<n>__<name>.sql
  - JPA entities under nexus-oms-backend/src/main/java/com/nexus/core/model/<package>/

Never edit generated files; change this spec and regenerate. A released migration is never changed: add a new
MIGRATIONS entry instead.

Field types are OFBiz/Moqui types (see TYPE_MAP in the generator). Every entity also gets the four standard
stamp columns (last_updated_stamp, last_updated_tx_stamp, created_stamp, created_tx_stamp).

Entity spec:
  name      OFBiz/Moqui entity name in PascalCase; table is snake_case of it
  package   Java sub-package under com.nexus.core.model
  doc       one line, becomes the class comment
  fields    list of (fieldName, type, options); options: pk, required, unique, audit (changes go to entity_audit_log), fk(<Entity>[, <field>])
  indexes   list of field lists (non-unique indexes beyond FKs)
"""

PK, REQ, UNIQUE, AUDIT = "pk", "required", "unique", "audit"


def fk(entity, field=None):
    return ("fk", entity, field)


ENTITIES = []


def entity(name, package, doc, fields, indexes=()):
    ENTITIES.append({"name": name, "package": package, "doc": doc, "fields": fields, "indexes": list(indexes)})


# ───────────────────────────── basic: enumerations, statuses, units, geography ─────────────────────────────

entity("EnumerationType", "basic", "A kind of coded list, such as sales channels or reject reasons.", [
    ("enumTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("EnumerationType", "enumTypeId")]),
    ("description", "text-medium", []),
])
entity("Enumeration", "basic", "One value of a coded list; admins can add values without a release.", [
    ("enumId", "id", [PK]),
    ("enumTypeId", "id", [REQ, fk("EnumerationType")]),
    ("parentEnumId", "id", [fk("Enumeration", "enumId")]),
    ("enumCode", "text-medium", []),
    ("enumName", "text-medium", []),
    ("sequenceNum", "number-integer", []),
    ("description", "text-medium", []),
    ("relatedEnumId", "id", [fk("Enumeration", "enumId")]),
], indexes=[["enumTypeId", "sequenceNum"]])
entity("EnumerationGroup", "basic", "A named set of enumerations used together (HotWax).", [
    ("enumerationGroupId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("EnumerationGroupMember", "basic", "Membership of an enumeration in a group, dated.", [
    ("enumerationGroupId", "id", [PK, fk("EnumerationGroup")]),
    ("enumId", "id", [PK, fk("Enumeration")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("StatusType", "basic", "A kind of status, such as order or shipment status.", [
    ("statusTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("StatusType", "statusTypeId")]),
    ("description", "text-medium", []),
])
entity("StatusItem", "basic", "One status value.", [
    ("statusId", "id", [PK]),
    ("statusTypeId", "id", [REQ, fk("StatusType")]),
    ("statusCode", "text-medium", []),
    ("sequenceNum", "number-integer", []),
    ("description", "text-medium", []),
])
entity("StatusFlow", "basic", "A set of allowed status changes; a store can have its own flow.", [
    ("statusFlowId", "id", [PK]),
    ("statusTypeId", "id", [REQ, fk("StatusType")]),
    ("description", "text-medium", []),
])
entity("StatusFlowItem", "basic", "A status that belongs to a flow; one is the initial status.", [
    ("statusFlowId", "id", [PK, fk("StatusFlow")]),
    ("statusId", "id", [PK, fk("StatusItem")]),
    ("isInitial", "indicator", []),
])
entity("StatusFlowTransition", "basic", "An allowed change from one status to another within a flow.", [
    ("statusFlowId", "id", [PK, fk("StatusFlow")]),
    ("statusId", "id", [PK, fk("StatusItem")]),
    ("toStatusId", "id", [PK, fk("StatusItem", "statusId")]),
    ("transitionSequence", "number-integer", []),
    ("transitionName", "text-medium", []),
    ("userPermissionId", "id-long", []),
])
entity("UomType", "basic", "A kind of unit: currency, weight, length, time.", [
    ("uomTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("Uom", "basic", "A unit of measure or currency.", [
    ("uomId", "id", [PK]),
    ("uomTypeId", "id", [REQ, fk("UomType")]),
    ("abbreviation", "text-short", []),
    ("numericCode", "number-integer", []),
    ("description", "text-medium", []),
])
entity("UomConversion", "basic", "How to convert one unit to another, dated.", [
    ("uomId", "id", [PK, fk("Uom")]),
    ("uomIdTo", "id", [PK, fk("Uom", "uomId")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("conversionFactor", "number-decimal", [REQ]),
])
entity("Geo", "basic", "A country, state, city or postal area.", [
    ("geoId", "id", [PK]),
    ("geoTypeEnumId", "id", [REQ, fk("Enumeration", "enumId")]),
    ("geoName", "text-medium", [REQ]),
    ("geoNameLocal", "text-medium", []),
    ("geoCodeAlpha2", "text-short", []),
    ("geoCodeAlpha3", "text-short", []),
    ("geoCodeNumeric", "text-short", []),
    ("abbreviation", "text-short", []),
])
entity("GeoAssoc", "basic", "One geo inside another (a state in a country).", [
    ("geoId", "id", [PK, fk("Geo")]),
    ("toGeoId", "id", [PK, fk("Geo", "geoId")]),
    ("geoAssocTypeEnumId", "id", [fk("Enumeration", "enumId")]),
])
entity("GeoPoint", "basic", "A position on the map.", [
    ("geoPointId", "id", [PK]),
    ("latitude", "number-decimal", [REQ]),
    ("longitude", "number-decimal", [REQ]),
    ("elevation", "number-decimal", []),
    ("information", "text-medium", []),
])

# ───────────────────────────── entity: ids, settings, history, notes ─────────────────────────────

entity("SequenceValueItem", "entity", "Next id per sequence, handed out in banks.", [
    ("seqName", "text-medium", [PK]),
    ("seqNum", "number-integer", [REQ]),
])
entity("SystemProperty", "entity", "A setting by resource and property id.", [
    ("systemResourceId", "id-long", [PK]),
    ("systemPropertyId", "text-medium", [PK]),
    ("systemPropertyValue", "text-long", [AUDIT]),
    ("description", "text-medium", []),
])
entity("EntityAuditLog", "entity", "A change to an audited field: old value, new value, who and when.", [
    ("auditHistorySeqId", "id", [PK]),
    ("changedEntityName", "text-medium", [REQ]),
    ("changedFieldName", "text-medium", [REQ]),
    ("pkPrimaryValue", "text-medium", [REQ]),
    ("pkSecondaryValue", "text-medium", []),
    ("pkRestCombinedValue", "text-medium", []),
    ("oldValueText", "text-long", []),
    ("newValueText", "text-long", []),
    ("changeReason", "text-medium", []),
    ("changedDate", "date-time", [REQ]),
    ("changedByUserId", "id", []),
], indexes=[["changedEntityName", "pkPrimaryValue"], ["changedDate"]])
entity("NoteData", "entity", "A note attached to an order, return or party.", [
    ("noteId", "id", [PK]),
    ("noteName", "text-medium", []),
    ("noteText", "text-long", []),
    ("noteDateTime", "date-time", []),
    ("noteByUserId", "id", []),
    ("moreInfoUrl", "text-intermediate", []),
])

# ───────────────────────────── security: users, groups, permissions ─────────────────────────────

entity("UserAccount", "security", "A person or system that signs in; links to a party.", [
    ("userId", "id", [PK]),
    ("username", "text-medium", [REQ, UNIQUE]),
    ("userFullName", "text-medium", []),
    ("partyId", "id", []),
    ("currentPassword", "text-medium", []),
    ("passwordHashType", "text-short", []),
    ("passwordSetDate", "date-time", []),
    ("requirePasswordChange", "indicator", []),
    ("disabled", "indicator", [AUDIT]),
    ("disabledDateTime", "date-time", []),
    ("successiveFailedLogins", "number-integer", []),
    ("emailAddress", "text-medium", []),
    ("locale", "text-short", []),
    ("timeZone", "text-short", []),
    ("currencyUomId", "id", [fk("Uom", "uomId")]),
    ("externalUserId", "text-medium", []),
    ("mfaEnabled", "indicator", []),
    ("mfaSecretRef", "text-medium", []),
], indexes=[["emailAddress"]])
entity("UserAuthIdentity", "security", "A sign-in identity from Google, Microsoft or Okta linked to a user (Nexus).", [
    ("providerId", "id", [PK]),
    ("externalSubject", "id-long", [PK]),
    ("userId", "id", [REQ, fk("UserAccount")]),
    ("emailAddress", "text-medium", []),
    ("linkedDate", "date-time", []),
])
entity("UserGroup", "security", "A group of users that share permissions.", [
    ("userGroupId", "id", [PK]),
    ("description", "text-medium", []),
    ("groupTypeEnumId", "id", [fk("Enumeration", "enumId")]),
])
entity("UserGroupMember", "security", "A user in a group, dated.", [
    ("userGroupId", "id", [PK, fk("UserGroup")]),
    ("userId", "id", [PK, fk("UserAccount")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])
entity("UserPermission", "security", "A permission, such as ORDER_UPDATE.", [
    ("userPermissionId", "id-long", [PK]),
    ("description", "text-medium", []),
])
entity("UserGroupPermission", "security", "A permission given to a group, dated.", [
    ("userGroupId", "id", [PK, fk("UserGroup")]),
    ("userPermissionId", "id-long", [PK, fk("UserPermission")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])
entity("UserLoginHistory", "security", "Each sign-in attempt.", [
    ("userId", "id", [PK, fk("UserAccount")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("successfulLogin", "indicator", []),
    ("clientIp", "text-short", []),
])
entity("UserPasswordHistory", "security", "Earlier password hashes, to stop reuse.", [
    ("userId", "id", [PK, fk("UserAccount")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("passwordHash", "text-medium", []),
])
entity("UserPreference", "security", "A per-user setting.", [
    ("userId", "id", [PK, fk("UserAccount")]),
    ("preferenceKey", "text-medium", [PK]),
    ("preferenceValue", "text-long", []),
])

# ───────────────────────────── service: scheduled jobs ─────────────────────────────

entity("ServiceJob", "service", "A scheduled job: what runs, when, and whether it is paused.", [
    ("jobName", "text-short", [PK]),
    ("description", "text-medium", []),
    ("serviceName", "text-medium", [REQ]),
    ("cronExpression", "text-short", [AUDIT]),
    ("fromDate", "date-time", []),
    ("thruDate", "date-time", []),
    ("repeatCount", "number-integer", []),
    ("paused", "indicator", [AUDIT]),
    ("expireLockTime", "number-integer", []),
    ("minRetryTime", "number-integer", []),
    ("priority", "number-integer", []),
    ("transactionTimeout", "number-integer", []),
])
entity("ServiceJobParameter", "service", "A parameter passed to a job's service.", [
    ("jobName", "text-short", [PK, fk("ServiceJob")]),
    ("parameterName", "text-short", [PK]),
    ("parameterValue", "text-long", []),
])
entity("ServiceJobRun", "service", "One run of a job, with results and errors.", [
    ("jobRunId", "id", [PK]),
    ("jobName", "text-short", [REQ, fk("ServiceJob")]),
    ("userId", "id", []),
    ("parameters", "text-long", []),
    ("results", "text-very-long", []),
    ("messages", "text-long", []),
    ("hasError", "indicator", []),
    ("errors", "text-long", []),
    ("hostAddress", "text-short", []),
    ("hostName", "text-medium", []),
    ("runThread", "text-medium", []),
    ("startTime", "date-time", []),
    ("endTime", "date-time", []),
], indexes=[["jobName", "startTime"]])
entity("ServiceJobRunLock", "service", "Which run holds a job, so only one server runs it at a time.", [
    ("jobName", "text-short", [PK, fk("ServiceJob")]),
    ("jobRunId", "id", []),
    ("lastRunTime", "date-time", []),
    ("lockedBy", "text-medium", []),
    ("lockedUntil", "date-time", []),
])

# ───────────────────────────── service: messages to and from other systems ─────────────────────────────

entity("SystemMessageType", "service", "A kind of message: an order webhook, a stock feed, a WMS request.", [
    ("systemMessageTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("SystemMessageType", "systemMessageTypeId")]),
    ("description", "text-medium", []),
    ("produceServiceName", "text-medium", []),
    ("consumeServiceName", "text-medium", []),
    ("sendServiceName", "text-medium", []),
    ("receiveServiceName", "text-medium", []),
    ("contentType", "text-short", []),
])
entity("SystemMessageRemote", "service", "Another system Nexus exchanges messages with; secrets are references, never values.", [
    ("systemMessageRemoteId", "id", [PK]),
    ("description", "text-medium", []),
    ("systemMessageTypeId", "id", [fk("SystemMessageType")]),
    ("sendUrl", "text-intermediate", []),
    ("receiveUrl", "text-intermediate", []),
    ("username", "text-medium", []),
    ("authHeaderName", "text-medium", []),
    ("secretRef", "text-medium", []),
    ("remoteId", "text-short", []),
    ("internalId", "text-short", []),
])
entity("SystemMessage", "service", "One message in or out, with status, attempts and its idempotency key.", [
    ("systemMessageId", "id", [PK]),
    ("systemMessageTypeId", "id", [REQ, fk("SystemMessageType")]),
    ("systemMessageRemoteId", "id", [fk("SystemMessageRemote")]),
    ("statusId", "id", [REQ, fk("StatusItem")]),
    ("isOutgoing", "indicator", [REQ]),
    ("initDate", "date-time", [REQ]),
    ("processedDate", "date-time", []),
    ("lastAttemptDate", "date-time", []),
    ("failCount", "number-integer", []),
    ("parentMessageId", "id", [fk("SystemMessage", "systemMessageId")]),
    ("remoteMessageId", "text-medium", []),
    ("idempotencyKey", "id-long", []),
    ("sequenceNum", "number-integer", []),
    ("orderId", "id", []),
    ("messageText", "text-very-long", []),
], indexes=[["statusId", "initDate"], ["systemMessageTypeId", "idempotencyKey"], ["orderId"]])
entity("SystemMessageError", "service", "An error from one attempt to process a message.", [
    ("systemMessageId", "id", [PK, fk("SystemMessage")]),
    ("errorDate", "date-time", [PK]),
    ("attemptedStatusId", "id", [fk("StatusItem", "statusId")]),
    ("errorText", "text-long", []),
])

# ───────────────────────────── data manager: imports and exports (HotWax) ─────────────────────────────

entity("DataManagerConfig", "datamanager", "One import or export feed and how it runs.", [
    ("configId", "id", [PK]),
    ("description", "text-medium", []),
    ("importServiceName", "text-medium", []),
    ("exportServiceName", "text-medium", []),
    ("delimiter", "text-short", []),
    ("fileNamePattern", "text-medium", []),
    ("executionModeEnumId", "id", [fk("Enumeration", "enumId")]),
    ("multiThreading", "indicator", []),
    ("importPath", "text-medium", []),
    ("exportPath", "text-medium", []),
    ("priority", "number-integer", []),
])
entity("DataManagerLog", "datamanager", "One run of a feed: files, record counts, status.", [
    ("logId", "id", [PK]),
    ("parentLogId", "id", [fk("DataManagerLog", "logId")]),
    ("configId", "id", [REQ, fk("DataManagerConfig")]),
    ("logTypeEnumId", "id", [fk("Enumeration", "enumId")]),
    ("statusId", "id", [REQ, fk("StatusItem")]),
    ("createdByUserId", "id", []),
    ("createdDate", "date-time", []),
    ("startDateTime", "date-time", []),
    ("finishDateTime", "date-time", []),
    ("cancelDateTime", "date-time", []),
    ("jobRunId", "id", []),
    ("uploadFileRef", "text-intermediate", []),
    ("errorFileRef", "text-intermediate", []),
    ("totalRecordCount", "number-integer", []),
    ("failedRecordCount", "number-integer", []),
], indexes=[["configId", "createdDate"]])


# ───────────────────────────── party: people, organisations, roles, contacts (W4) ─────────────────────────────

entity("PartyType", "party", "Person or organisation (and sub-kinds).", [
    ("partyTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("PartyType", "partyTypeId")]),
    ("description", "text-medium", []),
])
entity("Party", "party", "Anyone Nexus deals with: a customer, a company, a supplier, a carrier, an employee.", [
    ("partyId", "id", [PK]),
    ("partyTypeId", "id", [REQ, fk("PartyType")]),
    ("externalId", "id-long", []),
    ("statusId", "id", [fk("StatusItem")]),
    ("preferredCurrencyUomId", "id", [fk("Uom", "uomId")]),
    ("description", "text-long", []),
    ("ranking", "number-integer", []),
], indexes=[["externalId"]])
entity("Person", "party", "The person details of a party.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("salutation", "text-short", []),
    ("firstName", "text-medium", []),
    ("middleName", "text-medium", []),
    ("lastName", "text-medium", []),
    ("suffix", "text-short", []),
    ("nickname", "text-medium", []),
    ("gender", "text-short", []),
    ("birthDate", "date", []),
    ("comments", "text-long", []),
], indexes=[["lastName", "firstName"]])
entity("PartyGroup", "party", "The organisation details of a party.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("groupName", "text-medium", [REQ]),
    ("groupNameLocal", "text-medium", []),
    ("officeSiteName", "text-medium", []),
    ("logoImageUrl", "text-intermediate", []),
    ("comments", "text-long", []),
], indexes=[["groupName"]])
entity("RoleType", "party", "A role a party plays: customer, supplier, carrier, employee.", [
    ("roleTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("RoleType", "roleTypeId")]),
    ("description", "text-medium", []),
])
entity("PartyRole", "party", "A role a party has.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("roleTypeId", "id", [PK, fk("RoleType")]),
])
entity("PartyRelationshipType", "party", "How two parties relate: employment, account contact.", [
    ("partyRelationshipTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("PartyRelationship", "party", "One party related to another in given roles, dated.", [
    ("partyIdFrom", "id", [PK, fk("Party", "partyId")]),
    ("partyIdTo", "id", [PK, fk("Party", "partyId")]),
    ("roleTypeIdFrom", "id", [PK, fk("RoleType", "roleTypeId")]),
    ("roleTypeIdTo", "id", [PK, fk("RoleType", "roleTypeId")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("partyRelationshipTypeId", "id", [fk("PartyRelationshipType")]),
    ("comments", "text-long", []),
])
entity("PartyIdentificationType", "party", "A kind of outside id: Shopify customer id, tax id.", [
    ("partyIdentificationTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("PartyIdentification", "party", "A party's id in another system.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("partyIdentificationTypeId", "id", [PK, fk("PartyIdentificationType")]),
    ("idValue", "id-long", [REQ]),
], indexes=[["partyIdentificationTypeId", "idValue"]])
entity("PartyAttribute", "party", "Any extra named value on a party.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("attrName", "text-medium", [PK]),
    ("attrValue", "text-long", []),
])
entity("PartyStatus", "party", "Every status a party has had.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("statusId", "id", [PK, fk("StatusItem")]),
    ("statusDate", "date-time", [PK]),
    ("changeByUserId", "id", []),
])
entity("PartyNote", "party", "A note attached to a party.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("noteId", "id", [PK, fk("NoteData")]),
])
entity("ContactMechType", "party", "Postal address, phone number, email address, web address.", [
    ("contactMechTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("ContactMechType", "contactMechTypeId")]),
    ("description", "text-medium", []),
])
entity("ContactMechPurposeType", "party", "What a contact is for: shipping, billing, primary email.", [
    ("contactMechPurposeTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ContactMechTypePurpose", "party", "Which purposes suit which kind of contact.", [
    ("contactMechTypeId", "id", [PK, fk("ContactMechType")]),
    ("contactMechPurposeTypeId", "id", [PK, fk("ContactMechPurposeType")]),
])
entity("ContactMech", "party", "A way to reach someone: an address, a phone number, an email.", [
    ("contactMechId", "id", [PK]),
    ("contactMechTypeId", "id", [REQ, fk("ContactMechType")]),
    ("infoString", "text-medium", []),
    ("externalId", "id-long", []),
], indexes=[["infoString"]])
entity("PostalAddress", "party", "The address details of a contact, with map position.", [
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("toName", "text-medium", []),
    ("attnName", "text-medium", []),
    ("address1", "text-medium", []),
    ("address2", "text-medium", []),
    ("directions", "text-medium", []),
    ("city", "text-medium", []),
    ("postalCode", "text-short", []),
    ("postalCodeExt", "text-short", []),
    ("countryGeoId", "id", [fk("Geo", "geoId")]),
    ("stateProvinceGeoId", "id", [fk("Geo", "geoId")]),
    ("latitude", "number-decimal", []),
    ("longitude", "number-decimal", []),
    ("isResidential", "indicator", []),
], indexes=[["postalCode"]])
entity("TelecomNumber", "party", "The phone details of a contact.", [
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("countryCode", "text-short", []),
    ("areaCode", "text-short", []),
    ("contactNumber", "text-short", []),
    ("askForName", "text-medium", []),
])
entity("PartyContactMech", "party", "A contact that belongs to a party, dated.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("roleTypeId", "id", [fk("RoleType")]),
    ("allowSolicitation", "indicator", []),
    ("extension", "text-short", []),
    ("verified", "indicator", []),
    ("comments", "text-long", []),
])
entity("PartyContactMechPurpose", "party", "What a party's contact is for, dated.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("contactMechPurposeTypeId", "id", [PK, fk("ContactMechPurposeType")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])


# Migrations in order. Each lists the entities whose tables it creates. Released entries are never changed.
_W3 = ["EnumerationType", "Enumeration", "EnumerationGroup", "EnumerationGroupMember", "StatusType", "StatusItem",
       "StatusFlow", "StatusFlowItem", "StatusFlowTransition", "UomType", "Uom", "UomConversion", "Geo", "GeoAssoc",
       "GeoPoint", "SequenceValueItem", "SystemProperty", "EntityAuditLog", "NoteData", "UserAccount", "UserAuthIdentity",
       "UserGroup", "UserGroupMember", "UserPermission", "UserGroupPermission", "UserLoginHistory", "UserPasswordHistory",
       "UserPreference", "ServiceJob", "ServiceJobParameter", "ServiceJobRun", "ServiceJobRunLock", "SystemMessageType",
       "SystemMessageRemote", "SystemMessage", "SystemMessageError", "DataManagerConfig", "DataManagerLog"]
MIGRATIONS = [
    (1, "core_framework", _W3),
    (3, "core_party", [e["name"] for e in ENTITIES if e["package"] == "party"]),
]
