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


# ───────────────────────────── product: catalogue, variants, ids, features, prices (W5) ─────────────────────────────

entity("ProductType", "product", "Finished good, digital good, service, bundle, gift card.", [
    ("productTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("ProductType", "productTypeId")]),
    ("isPhysical", "indicator", []),
    ("isDigital", "indicator", []),
    ("description", "text-medium", []),
])
entity("Product", "product", "Something Nexus sells or stocks; a virtual product groups its variants.", [
    ("productId", "id", [PK]),
    ("productTypeId", "id", [REQ, fk("ProductType")]),
    ("primaryProductCategoryId", "id", [fk("ProductCategory", "productCategoryId")]),
    ("primaryProductStoreId", "id", [fk("ProductStore", "productStoreId")]),
    ("internalName", "text-medium", []),
    ("productName", "text-medium", []),
    ("brandName", "text-medium", []),
    ("description", "text-long", []),
    ("longDescription", "text-very-long", []),
    ("smallImageUrl", "text-intermediate", []),
    ("mediumImageUrl", "text-intermediate", []),
    ("largeImageUrl", "text-intermediate", []),
    ("originalImageUrl", "text-intermediate", []),
    ("isVirtual", "indicator", []),
    ("isVariant", "indicator", []),
    ("requireInventory", "indicator", []),
    ("returnable", "indicator", []),
    ("taxable", "indicator", []),
    ("chargeShipping", "indicator", []),
    ("introductionDate", "date-time", []),
    ("salesDiscontinuationDate", "date-time", []),
    ("productWeight", "number-decimal", []),
    ("weightUomId", "id", [fk("Uom", "uomId")]),
    ("productHeight", "number-decimal", []),
    ("productWidth", "number-decimal", []),
    ("productDepth", "number-decimal", []),
    ("dimensionUomId", "id", [fk("Uom", "uomId")]),
], indexes=[["internalName"], ["productName"]])
entity("ProductAssocType", "product", "How two products relate: variant, substitute, kit component.", [
    ("productAssocTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("ProductAssocType", "productAssocTypeId")]),
    ("description", "text-medium", []),
])
entity("ProductAssoc", "product", "One product related to another, dated and ordered.", [
    ("productId", "id", [PK, fk("Product")]),
    ("productIdTo", "id", [PK, fk("Product", "productId")]),
    ("productAssocTypeId", "id", [PK, fk("ProductAssocType")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
    ("quantity", "number-decimal", []),
    ("reason", "text-medium", []),
], indexes=[["productIdTo", "productAssocTypeId"]])
entity("GoodIdentificationType", "product", "A kind of product id: SKU, UPC, EAN, a channel's product id.", [
    ("goodIdentificationTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("GoodIdentification", "product", "A product's id of one kind, dated.", [
    ("goodIdentificationTypeId", "id", [PK, fk("GoodIdentificationType")]),
    ("productId", "id", [PK, fk("Product")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("idValue", "id-long", [REQ]),
], indexes=[["goodIdentificationTypeId", "idValue"]])
entity("ProductFeatureType", "product", "Colour, size, style, material.", [
    ("productFeatureTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ProductFeature", "product", "One feature value, such as size 9 or colour red.", [
    ("productFeatureId", "id", [PK]),
    ("productFeatureTypeId", "id", [REQ, fk("ProductFeatureType")]),
    ("description", "text-medium", []),
    ("abbrev", "text-short", []),
    ("idCode", "text-short", []),
    ("defaultSequenceNum", "number-integer", []),
], indexes=[["productFeatureTypeId", "idCode"]])
entity("ProductFeatureApplType", "product", "How a feature applies: standard, selectable, distinguishing.", [
    ("productFeatureApplTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ProductFeatureAppl", "product", "A feature applied to a product, dated.", [
    ("productId", "id", [PK, fk("Product")]),
    ("productFeatureId", "id", [PK, fk("ProductFeature")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("productFeatureApplTypeId", "id", [fk("ProductFeatureApplType")]),
    ("sequenceNum", "number-integer", []),
])
entity("ProductCategoryType", "product", "Catalogue, browse or internal category.", [
    ("productCategoryTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ProductCategory", "product", "A category in the catalogue tree.", [
    ("productCategoryId", "id", [PK]),
    ("productCategoryTypeId", "id", [fk("ProductCategoryType")]),
    ("primaryParentCategoryId", "id", [fk("ProductCategory", "productCategoryId")]),
    ("categoryName", "text-medium", [REQ]),
    ("description", "text-long", []),
], indexes=[["categoryName"]])
entity("ProductCategoryMember", "product", "A product in a category, dated.", [
    ("productCategoryId", "id", [PK, fk("ProductCategory")]),
    ("productId", "id", [PK, fk("Product")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("ProductCategoryRollup", "product", "A category inside another, dated.", [
    ("productCategoryId", "id", [PK, fk("ProductCategory")]),
    ("parentProductCategoryId", "id", [PK, fk("ProductCategory", "productCategoryId")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("ProdCatalog", "product", "A catalogue: the categories one store sells from.", [
    ("prodCatalogId", "id", [PK]),
    ("catalogName", "text-medium", [REQ]),
])
entity("ProdCatalogCategory", "product", "A category in a catalogue, dated.", [
    ("prodCatalogId", "id", [PK, fk("ProdCatalog")]),
    ("productCategoryId", "id", [PK, fk("ProductCategory")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("ProductPriceType", "product", "Default, list, promotional, wholesale price, or average cost.", [
    ("productPriceTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ProductPricePurpose", "product", "What a price is for: purchase or recurring charge.", [
    ("productPricePurposeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("ProductStoreGroup", "product", "A group of stores that share prices.", [
    ("productStoreGroupId", "id", [PK]),
    ("productStoreGroupName", "text-medium", []),
    ("description", "text-medium", []),
])
entity("ProductPrice", "product", "A product's price of one type, in one currency, for a store group, dated.", [
    ("productId", "id", [PK, fk("Product")]),
    ("productPriceTypeId", "id", [PK, fk("ProductPriceType")]),
    ("productPricePurposeId", "id", [PK, fk("ProductPricePurpose")]),
    ("currencyUomId", "id", [PK, fk("Uom", "uomId")]),
    ("productStoreGroupId", "id", [PK, fk("ProductStoreGroup")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("price", "currency-precise", [REQ]),
])

# ───────────────────────────── product store: one per brand or sales channel (W5) ─────────────────────────────

entity("ProductStore", "product", "A store: a brand or channel with its own currency, rules and settings.", [
    ("productStoreId", "id", [PK]),
    ("storeName", "text-medium", [REQ]),
    ("companyName", "text-medium", []),
    ("primaryStoreGroupId", "id", [fk("ProductStoreGroup", "productStoreGroupId")]),
    ("payToPartyId", "id", [fk("Party", "partyId")]),
    ("defaultCurrencyUomId", "id", [fk("Uom", "uomId")]),
    ("defaultLocaleString", "text-short", []),
    ("defaultTimeZoneString", "text-short", []),
    ("defaultSalesChannelEnumId", "id", [fk("Enumeration", "enumId")]),
    ("productIdentifierEnumId", "id", [fk("Enumeration", "enumId")]),
    ("orderNumberPrefix", "text-short", []),
    ("externalId", "id-long", []),
    ("storeDomain", "text-medium", []),
    ("reserveInventory", "indicator", []),
    ("requireInventory", "indicator", []),
    ("checkInventory", "indicator", []),
    ("enableBrokering", "indicator", []),
    ("allowSplit", "indicator", []),
    ("autoApproveOrder", "indicator", []),
    ("enablePreOrderAutoReleasing", "indicator", []),
    ("daysToReturn", "number-integer", []),
    ("autoAcceptReturn", "indicator", []),
    ("restockingFeePercentage", "number-decimal", []),
    ("headerApprovedStatus", "id", [fk("StatusItem", "statusId")]),
    ("itemApprovedStatus", "id", [fk("StatusItem", "statusId")]),
])
entity("ProductStoreSetting", "product", "A store setting by setting type (HotWax).", [
    ("productStoreId", "id", [PK, fk("ProductStore")]),
    ("settingTypeEnumId", "id", [PK, fk("Enumeration", "enumId")]),
    ("settingValue", "text-medium", []),
])
entity("ProductStoreCatalog", "product", "A catalogue a store sells from, dated.", [
    ("productStoreId", "id", [PK, fk("ProductStore")]),
    ("prodCatalogId", "id", [PK, fk("ProdCatalog")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("ProductStoreRole", "product", "A party's role in a store, such as its staff or owner.", [
    ("partyId", "id", [PK, fk("Party")]),
    ("roleTypeId", "id", [PK, fk("RoleType")]),
    ("productStoreId", "id", [PK, fk("ProductStore")]),
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
# ───────────────────────────── facility: stores, warehouses, groups, store links (W6) ─────────────────────────────

entity("FacilityType", "facility", "Warehouse, distribution centre, retail store, dark store, 3PL, in transit.", [
    ("facilityTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("FacilityType", "facilityTypeId")]),
    ("description", "text-medium", []),
])
entity("Facility", "facility", "A place that holds stock or ships orders: a store, a warehouse, a 3PL.", [
    ("facilityId", "id", [PK]),
    ("facilityTypeId", "id", [REQ, fk("FacilityType")]),
    ("parentFacilityId", "id", [fk("Facility", "facilityId")]),
    ("ownerPartyId", "id", [fk("Party", "partyId")]),
    ("facilityName", "text-medium", [REQ]),
    ("externalId", "id-long", []),
    ("description", "text-long", []),
    ("defaultDaysToShip", "number-integer", []),
    ("openedDate", "date-time", []),
    ("closedDate", "date-time", []),
    ("openTime", "time", []),
    ("closeTime", "time", []),
    ("facilityTimeZone", "text-short", []),
    ("maximumOrderLimit", "number-integer", []),
    ("postalCode", "text-short", []),
    ("latitude", "number-decimal", []),
    ("longitude", "number-decimal", []),
], indexes=[["facilityTypeId"]])
entity("FacilityIdentificationType", "facility", "A kind of outside id for a facility: POS location id, Shopify location id.", [
    ("facilityIdenTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("FacilityIdentification", "facility", "A facility's id in another system, dated (HotWax).", [
    ("facilityIdenTypeId", "id", [PK, fk("FacilityIdentificationType")]),
    ("facilityId", "id", [PK, fk("Facility")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("idValue", "id-long", [REQ]),
], indexes=[["facilityIdenTypeId", "idValue"]])
entity("FacilityGroupType", "facility", "Brokering group, region, pickup group.", [
    ("facilityGroupTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("FacilityGroup", "facility", "A named set of facilities that routing can target in order.", [
    ("facilityGroupId", "id", [PK]),
    ("facilityGroupTypeId", "id", [REQ, fk("FacilityGroupType")]),
    ("primaryParentGroupId", "id", [fk("FacilityGroup", "facilityGroupId")]),
    ("facilityGroupName", "text-medium", [REQ]),
    ("description", "text-long", []),
])
entity("FacilityGroupMember", "facility", "A facility in a group with its place in the order, dated.", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("facilityGroupId", "id", [PK, fk("FacilityGroup")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
], indexes=[["facilityGroupId", "sequenceNum"]])
entity("FacilityContactMech", "facility", "A contact (address, phone) of a facility, dated.", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])
entity("FacilityContactMechPurpose", "facility", "What a facility's contact is for, dated.", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("contactMechId", "id", [PK, fk("ContactMech")]),
    ("contactMechPurposeTypeId", "id", [PK, fk("ContactMechPurposeType")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])
entity("FacilityLocation", "facility", "A place inside a facility: area, aisle, section, level, position.", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("locationSeqId", "id", [PK]),
    ("locationTypeEnumId", "id", [fk("Enumeration", "enumId")]),
    ("areaId", "text-short", []),
    ("aisleId", "text-short", []),
    ("sectionId", "text-short", []),
    ("levelId", "text-short", []),
    ("positionId", "text-short", []),
])
entity("FacilityParty", "facility", "A party's role at a facility (manager, staff), dated.", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("partyId", "id", [PK, fk("Party")]),
    ("roleTypeId", "id", [PK, fk("RoleType")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
])
entity("FacilityOrderCount", "facility", "Orders a facility took on a day, for its daily limit (HotWax).", [
    ("facilityId", "id", [PK, fk("Facility")]),
    ("entryDate", "date", [PK]),
    ("lastOrderCount", "number-integer", [REQ]),
])
entity("ProductStoreFacility", "facility", "A facility that serves a store, dated and ordered.", [
    ("productStoreId", "id", [PK, fk("ProductStore")]),
    ("facilityId", "id", [PK, fk("Facility")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNum", "number-integer", []),
])
entity("ProductStoreFacilityGroup", "facility", "A facility group that serves a store, dated (HotWax).", [
    ("productStoreId", "id", [PK, fk("ProductStore")]),
    ("facilityGroupId", "id", [PK, fk("FacilityGroup")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("sequenceNumber", "number-integer", []),
])

# ───────────────────────────── inventory: stock, its history, counts, transfers (W6) ─────────────────────────────

entity("ProductFacility", "inventory", "A product at a facility: safety stock, reorder levels, brokering switches, sales velocity.", [
    ("productId", "id", [PK, fk("Product")]),
    ("facilityId", "id", [PK, fk("Facility")]),
    ("minimumStock", "number-decimal", []),
    ("reorderPoint", "number-decimal", []),
    ("reorderQuantity", "number-decimal", []),
    ("maximumStock", "number-decimal", []),
    ("daysToShip", "number-integer", []),
    ("lastInventoryCount", "number-decimal", []),
    ("computedLastInventoryCount", "number-decimal", []),
    ("allowBrokering", "indicator", []),
    ("allowPickup", "indicator", []),
    ("salesVelocity", "number-decimal", []),
], indexes=[["facilityId"]])
entity("InventoryItemType", "inventory", "Counted in bulk or one serial number per item.", [
    ("inventoryItemTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("Lot", "inventory", "A production or receipt lot with its expiry.", [
    ("lotId", "id", [PK]),
    ("creationDate", "date-time", []),
    ("quantity", "number-decimal", []),
    ("expirationDate", "date-time", []),
])
entity("InventoryItem", "inventory", "A quantity of one product at one facility (and lot or serial number).", [
    ("inventoryItemId", "id", [PK]),
    ("inventoryItemTypeId", "id", [REQ, fk("InventoryItemType")]),
    ("productId", "id", [REQ, fk("Product")]),
    ("facilityId", "id", [REQ, fk("Facility")]),
    ("locationSeqId", "id", []),
    ("lotId", "id", [fk("Lot")]),
    ("statusId", "id", [fk("StatusItem")]),
    ("ownerPartyId", "id", [fk("Party", "partyId")]),
    ("datetimeReceived", "date-time", []),
    ("expireDate", "date-time", []),
    ("quantityOnHandTotal", "number-decimal", [REQ]),
    ("availableToPromiseTotal", "number-decimal", [REQ]),
    ("accountingQuantityTotal", "number-decimal", []),
    ("unitCost", "number-decimal", []),
    ("currencyUomId", "id", [fk("Uom", "uomId")]),
    ("serialNumber", "text-medium", []),
    ("comments", "text-long", []),
], indexes=[["productId", "facilityId"], ["facilityId"]])
entity("InventoryItemDetail", "inventory", "One change to an inventory item: on hand and available, why, and what caused it.", [
    ("inventoryItemId", "id", [PK, fk("InventoryItem")]),
    ("inventoryItemDetailSeqId", "id", [PK]),
    ("effectiveDate", "date-time", [REQ]),
    ("quantityOnHandDiff", "number-decimal", []),
    ("availableToPromiseDiff", "number-decimal", []),
    ("accountingQuantityDiff", "number-decimal", []),
    ("unitCost", "number-decimal", []),
    ("orderId", "id", []),
    ("orderItemSeqId", "id", []),
    ("shipGroupSeqId", "id", []),
    ("shipmentId", "id", []),
    ("returnId", "id", []),
    ("physicalInventoryId", "id", []),
    ("reasonEnumId", "id", [fk("Enumeration", "enumId")]),
    ("changeByUserId", "id", []),
    ("description", "text-medium", []),
], indexes=[["effectiveDate"], ["orderId"]])
entity("VarianceReason", "inventory", "Why a count differs: lost, found, damaged, stolen.", [
    ("varianceReasonId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("PhysicalInventory", "inventory", "A stock count.", [
    ("physicalInventoryId", "id", [PK]),
    ("physicalInventoryDate", "date-time", [REQ]),
    ("partyId", "id", [fk("Party")]),
    ("generalComments", "text-long", []),
])
entity("InventoryItemVariance", "inventory", "The difference a count found for an item.", [
    ("inventoryItemId", "id", [PK, fk("InventoryItem")]),
    ("physicalInventoryId", "id", [PK, fk("PhysicalInventory")]),
    ("varianceReasonId", "id", [fk("VarianceReason")]),
    ("availableToPromiseVar", "number-decimal", []),
    ("quantityOnHandVar", "number-decimal", []),
    ("comments", "text-long", []),
])
entity("InventoryTransfer", "inventory", "Stock moving from one facility to another.", [
    ("inventoryTransferId", "id", [PK]),
    ("statusId", "id", [REQ, fk("StatusItem")]),
    ("inventoryItemId", "id", [REQ, fk("InventoryItem")]),
    ("facilityId", "id", [REQ, fk("Facility")]),
    ("facilityIdTo", "id", [REQ, fk("Facility", "facilityId")]),
    ("quantity", "number-decimal", [REQ]),
    ("sendDate", "date-time", []),
    ("receiveDate", "date-time", []),
    ("comments", "text-long", []),
], indexes=[["facilityIdTo", "statusId"]])

# ───────────────────────────── order: header, items, ship groups, reservations, money (W7) ─────────────────────────────

entity("OrderType", "order", "Sales order or purchase order.", [
    ("orderTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("OrderType", "orderTypeId")]),
    ("description", "text-medium", []),
])
entity("OrderHeader", "order", "An order: who, where from, when, its status and its total.", [
    ("orderId", "id", [PK]),
    ("orderTypeId", "id", [REQ, fk("OrderType")]),
    ("orderName", "text-medium", []),
    ("externalId", "id-long", []),
    ("salesChannelEnumId", "id", [fk("Enumeration", "enumId")]),
    ("orderDate", "date-time", [REQ]),
    ("entryDate", "date-time", []),
    ("priority", "number-integer", []),
    ("statusId", "id", [REQ, fk("StatusItem"), AUDIT]),
    ("currencyUomId", "id", [fk("Uom", "uomId")]),
    ("productStoreId", "id", [fk("ProductStore")]),
    ("originFacilityId", "id", [fk("Facility", "facilityId")]),
    ("createdBy", "id", []),
    ("isRushOrder", "indicator", []),
    ("grandTotal", "currency-amount", []),
    ("remainingSubTotal", "currency-amount", []),
], indexes=[["externalId"], ["orderDate"], ["statusId"], ["productStoreId", "orderDate"]])
entity("OrderIdentificationType", "order", "A kind of outside id for an order: Shopify order id, marketplace order id, PO number.", [
    ("orderIdentificationTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("OrderIdentification", "order", "An order's id in another system, dated (HotWax).", [
    ("orderIdentificationTypeId", "id", [PK, fk("OrderIdentificationType")]),
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("fromDate", "date-time", [PK]),
    ("thruDate", "date-time", []),
    ("idValue", "id-long", [REQ]),
], indexes=[["orderIdentificationTypeId", "idValue"]])
entity("OrderStatus", "order", "Every status an order, item or payment had, with when, who and why.", [
    ("orderStatusId", "id", [PK]),
    ("statusId", "id", [REQ, fk("StatusItem")]),
    ("orderId", "id", [REQ, fk("OrderHeader")]),
    ("orderItemSeqId", "id", []),
    ("orderPaymentPreferenceId", "id", []),
    ("statusDatetime", "date-time", [REQ]),
    ("statusUserLogin", "id", []),
    ("changeReason", "text-long", []),
], indexes=[["orderId", "statusDatetime"]])
entity("OrderRole", "order", "A party's role on an order: placing customer, bill-to, ship-to, sales rep.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("partyId", "id", [PK, fk("Party")]),
    ("roleTypeId", "id", [PK, fk("RoleType")]),
], indexes=[["partyId"]])
entity("OrderContactMech", "order", "An order's contacts by purpose: ship-to address, bill-to address, order email.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("contactMechPurposeTypeId", "id", [PK, fk("ContactMechPurposeType")]),
    ("contactMechId", "id", [PK, fk("ContactMech")]),
])
entity("ShipmentMethodType", "order", "How an order ships: standard, next day, store pickup.", [
    ("shipmentMethodTypeId", "id", [PK]),
    ("description", "text-medium", []),
    ("sequenceNum", "number-integer", []),
])
entity("OrderItemShipGroup", "order", "A set of an order's items that ship together: from where, how, to which address.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("shipGroupSeqId", "id", [PK]),
    ("shipmentMethodTypeId", "id", [fk("ShipmentMethodType")]),
    ("carrierPartyId", "id", [fk("Party", "partyId")]),
    ("facilityId", "id", [fk("Facility")]),
    ("contactMechId", "id", [fk("ContactMech")]),
    ("telecomContactMechId", "id", [fk("ContactMech", "contactMechId")]),
    ("trackingNumber", "text-medium", []),
    ("shippingInstructions", "text-long", []),
    ("maySplit", "indicator", []),
    ("isGift", "indicator", []),
    ("giftMessage", "text-long", []),
    ("shipAfterDate", "date-time", []),
    ("shipByDate", "date-time", []),
    ("estimatedShipDate", "date-time", []),
    ("estimatedDeliveryDate", "date-time", []),
], indexes=[["facilityId"]])
entity("OrderItemType", "order", "Product item, service item.", [
    ("orderItemTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("OrderItemType", "orderItemTypeId")]),
    ("description", "text-medium", []),
])
entity("OrderItem", "order", "A line of an order: product, quantity, price, its own status and ship group.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("orderItemSeqId", "id", [PK]),
    ("externalId", "id-long", []),
    ("orderItemTypeId", "id", [REQ, fk("OrderItemType")]),
    ("shipGroupSeqId", "id", []),
    ("productId", "id", [fk("Product")]),
    ("quantity", "number-decimal", [REQ]),
    ("cancelQuantity", "number-decimal", []),
    ("unitPrice", "currency-amount", []),
    ("unitListPrice", "currency-amount", []),
    ("itemDescription", "text-medium", []),
    ("statusId", "id", [REQ, fk("StatusItem"), AUDIT]),
    ("isPromo", "indicator", []),
    ("isModifiedPrice", "indicator", []),
    ("comments", "text-long", []),
    ("autoCancelDate", "date-time", []),
    ("estimatedShipDate", "date-time", []),
    ("estimatedDeliveryDate", "date-time", []),
], indexes=[["productId"], ["statusId"]])
entity("OrderItemShipGroupAssoc", "order", "How much of an item goes in a ship group (an item split across groups).", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("orderItemSeqId", "id", [PK]),
    ("shipGroupSeqId", "id", [PK]),
    ("quantity", "number-decimal", [REQ]),
    ("cancelQuantity", "number-decimal", []),
])
entity("OrderItemShipGrpInvRes", "order", "Stock held for an order item at an inventory item.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("shipGroupSeqId", "id", [PK]),
    ("orderItemSeqId", "id", [PK]),
    ("inventoryItemId", "id", [PK, fk("InventoryItem")]),
    ("reserveOrderEnumId", "id", [fk("Enumeration", "enumId")]),
    ("quantity", "number-decimal", [REQ]),
    ("quantityNotAvailable", "number-decimal", []),
    ("reservedDatetime", "date-time", []),
    ("createdDatetime", "date-time", []),
    ("promisedDatetime", "date-time", []),
    ("priority", "number-integer", []),
], indexes=[["inventoryItemId"]])
entity("OrderAdjustmentType", "order", "Shipping charge, sales tax, promotion, discount, fee.", [
    ("orderAdjustmentTypeId", "id", [PK]),
    ("parentTypeId", "id", [fk("OrderAdjustmentType", "orderAdjustmentTypeId")]),
    ("description", "text-medium", []),
])
entity("OrderAdjustment", "order", "An amount added to or taken off an order, an item or a ship group.", [
    ("orderAdjustmentId", "id", [PK]),
    ("orderAdjustmentTypeId", "id", [REQ, fk("OrderAdjustmentType")]),
    ("orderId", "id", [REQ, fk("OrderHeader")]),
    ("orderItemSeqId", "id", []),
    ("shipGroupSeqId", "id", []),
    ("amount", "currency-amount", [REQ]),
    ("sourcePercentage", "number-decimal", []),
    ("description", "text-medium", []),
    ("comments", "text-long", []),
    ("taxAuthGeoId", "id", [fk("Geo", "geoId")]),
    ("productPromoId", "id", []),
    ("sourceReferenceId", "id-long", []),
    ("createdDate", "date-time", []),
    ("createdByUserLogin", "id", []),
], indexes=[["orderId"]])
entity("PaymentMethodType", "order", "Card, cash, gift card, paid on the channel (Shopify, marketplace), cash on delivery.", [
    ("paymentMethodTypeId", "id", [PK]),
    ("description", "text-medium", []),
])
entity("OrderPaymentPreference", "order", "How an order is paid and how much, with its own status.", [
    ("orderPaymentPreferenceId", "id", [PK]),
    ("orderId", "id", [REQ, fk("OrderHeader")]),
    ("orderItemSeqId", "id", []),
    ("paymentMethodTypeId", "id", [fk("PaymentMethodType")]),
    ("paymentMethodId", "id", []),
    ("statusId", "id", [REQ, fk("StatusItem"), AUDIT]),
    ("maxAmount", "currency-amount", []),
    ("manualRefNum", "text-medium", []),
    ("manualAuthCode", "text-medium", []),
    ("createdDate", "date-time", []),
    ("createdByUserLogin", "id", []),
], indexes=[["orderId"]])
entity("OrderAttribute", "order", "A named value on an order, for anything without its own column.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("attrName", "text-medium", [PK]),
    ("attrValue", "text-long", []),
])
entity("OrderItemAttribute", "order", "A named value on an order item.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("orderItemSeqId", "id", [PK]),
    ("attrName", "text-medium", [PK]),
    ("attrValue", "text-long", []),
])
entity("OrderHeaderNote", "order", "A note on an order.", [
    ("orderId", "id", [PK, fk("OrderHeader")]),
    ("noteId", "id", [PK, fk("NoteData")]),
    ("internalNote", "indicator", []),
])

MIGRATIONS = [
    (1, "core_framework", _W3),
    (3, "core_party", [e["name"] for e in ENTITIES if e["package"] == "party"]),
    (5, "core_product", [e["name"] for e in ENTITIES if e["package"] == "product"]),
    (7, "core_facility_inventory", [e["name"] for e in ENTITIES if e["package"] in ("facility", "inventory")]),
    (9, "core_order", [e["name"] for e in ENTITIES if e["package"] == "order"]),
]
