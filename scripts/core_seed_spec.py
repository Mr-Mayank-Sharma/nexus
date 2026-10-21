"""
Seed data for the Nexus core model: rows every installation needs. Generated into
db/core/common/V<n>__<name>.sql (the same SQL runs on PostgreSQL and MySQL).

Ids follow OFBiz where OFBiz has one (ORDER_APPROVED, ITEM_APPROVED, WEB_SALES_CHANNEL, USD, WT_kg) and Moqui for
framework values (SmsgProduced). Countries use ISO 3166-1 alpha-3 ids (USA, IND); states use ISO 3166-2 (US-NJ, IN-MH).
"""

SEED = []  # (table, [column, ...], [row tuple, ...])


def rows(table, columns, data):
    SEED.append((table, columns, data))


FLOW = "Default"   # every status type's allowed changes live in the Default flow, as in Moqui

# ── enumerations ──
rows("enumeration_type", ["enum_type_id", "description"], [
    ("ORDER_SALES_CHANNEL", "Sales channel an order came from"),
    ("GEO_TYPE", "Kind of geography"),
    ("GEO_ASSOC_TYPE", "How two geographies relate"),
    ("USER_GROUP_TYPE", "Kind of user group"),
    ("DMC_EXEC_MODE", "How a data manager feed runs"),
    ("DATA_MANAGER_LOG_TYPE", "Import or export run"),
])
rows("enumeration", ["enum_id", "enum_type_id", "enum_code", "sequence_num", "description"], [
    ("WEB_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "WEB", 1, "Web store"),
    ("POS_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "POS", 2, "Point of sale"),
    ("PHONE_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "PHONE", 3, "Phone"),
    ("EMAIL_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "EMAIL", 4, "Email"),
    ("B2B_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "B2B", 5, "B2B portal or EDI"),
    ("MARKETPLACE_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "MARKETPLACE", 6, "Marketplace (Amazon, eBay, Walmart)"),
    ("AGENT_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "AGENT", 7, "AI shopping agent (ACP, UCP)"),
    ("UNKNWN_SALES_CHANNEL", "ORDER_SALES_CHANNEL", "UNKNOWN", 99, "Unknown"),
    ("GEOT_COUNTRY", "GEO_TYPE", "COUNTRY", 1, "Country"),
    ("GEOT_STATE", "GEO_TYPE", "STATE", 2, "State or province"),
    ("GEOT_CITY", "GEO_TYPE", "CITY", 3, "City"),
    ("GEOT_POSTAL_CODE", "GEO_TYPE", "POSTAL_CODE", 4, "Postal code"),
    ("GAT_REGIONS", "GEO_ASSOC_TYPE", "REGIONS", 1, "Contains"),
    ("UGT_ROLE", "USER_GROUP_TYPE", "ROLE", 1, "Role"),
    ("UGT_TEAM", "USER_GROUP_TYPE", "TEAM", 2, "Team"),
    ("DMC_EXEC_QUEUE", "DMC_EXEC_MODE", "QUEUE", 1, "Run in the background queue"),
    ("DMC_EXEC_IMMEDIATE", "DMC_EXEC_MODE", "IMMEDIATE", 2, "Run straight away"),
    ("DMLT_IMPORT", "DATA_MANAGER_LOG_TYPE", "IMPORT", 1, "Import"),
    ("DMLT_EXPORT", "DATA_MANAGER_LOG_TYPE", "EXPORT", 2, "Export"),
])

# ── statuses and allowed changes ──
STATUSES = {
    # type: [(statusId, description, initial)], [(from, to, transitionName)]
    "ORDER_STATUS": ([
        ("ORDER_CREATED", "Created", True), ("ORDER_PROCESSING", "Processing", False), ("ORDER_APPROVED", "Approved", False),
        ("ORDER_HOLD", "Held", False), ("ORDER_SENT", "Sent", False), ("ORDER_COMPLETED", "Completed", False),
        ("ORDER_REJECTED", "Rejected", False), ("ORDER_CANCELLED", "Cancelled", False)],
        [("ORDER_CREATED", "ORDER_PROCESSING", "Process"), ("ORDER_CREATED", "ORDER_APPROVED", "Approve"),
         ("ORDER_CREATED", "ORDER_HOLD", "Hold"), ("ORDER_CREATED", "ORDER_REJECTED", "Reject"),
         ("ORDER_CREATED", "ORDER_CANCELLED", "Cancel"), ("ORDER_PROCESSING", "ORDER_APPROVED", "Approve"),
         ("ORDER_PROCESSING", "ORDER_HOLD", "Hold"), ("ORDER_PROCESSING", "ORDER_CANCELLED", "Cancel"),
         ("ORDER_APPROVED", "ORDER_HOLD", "Hold"), ("ORDER_APPROVED", "ORDER_SENT", "Send"),
         ("ORDER_APPROVED", "ORDER_COMPLETED", "Complete"), ("ORDER_APPROVED", "ORDER_CANCELLED", "Cancel"),
         ("ORDER_HOLD", "ORDER_APPROVED", "Release"), ("ORDER_HOLD", "ORDER_CANCELLED", "Cancel"),
         ("ORDER_SENT", "ORDER_COMPLETED", "Complete"), ("ORDER_SENT", "ORDER_CANCELLED", "Cancel")]),
    "ORDER_ITEM_STATUS": ([
        ("ITEM_CREATED", "Created", True), ("ITEM_APPROVED", "Approved", False), ("ITEM_COMPLETED", "Completed", False),
        ("ITEM_REJECTED", "Rejected", False), ("ITEM_CANCELLED", "Cancelled", False)],
        [("ITEM_CREATED", "ITEM_APPROVED", "Approve"), ("ITEM_CREATED", "ITEM_REJECTED", "Reject"),
         ("ITEM_CREATED", "ITEM_CANCELLED", "Cancel"), ("ITEM_APPROVED", "ITEM_COMPLETED", "Complete"),
         ("ITEM_APPROVED", "ITEM_CANCELLED", "Cancel")]),
    "PAYMENT_PREF_STATUS": ([
        ("PAYMENT_NOT_RECEIVED", "Not received", True), ("PAYMENT_AUTHORIZED", "Authorized", False),
        ("PAYMENT_SETTLED", "Settled", False), ("PAYMENT_REFUNDED", "Refunded", False),
        ("PAYMENT_DECLINED", "Declined", False), ("PAYMENT_CANCELLED", "Cancelled", False)],
        [("PAYMENT_NOT_RECEIVED", "PAYMENT_AUTHORIZED", "Authorize"), ("PAYMENT_NOT_RECEIVED", "PAYMENT_SETTLED", "Settle"),
         ("PAYMENT_NOT_RECEIVED", "PAYMENT_DECLINED", "Decline"), ("PAYMENT_NOT_RECEIVED", "PAYMENT_CANCELLED", "Cancel"),
         ("PAYMENT_AUTHORIZED", "PAYMENT_SETTLED", "Settle"), ("PAYMENT_AUTHORIZED", "PAYMENT_CANCELLED", "Cancel"),
         ("PAYMENT_SETTLED", "PAYMENT_REFUNDED", "Refund")]),
    "SHIPMENT_STATUS": ([
        ("SHIPMENT_INPUT", "Created", True), ("SHIPMENT_SCHEDULED", "Scheduled", False), ("SHIPMENT_PICKED", "Picked", False),
        ("SHIPMENT_PACKED", "Packed", False), ("SHIPMENT_SHIPPED", "Shipped", False), ("SHIPMENT_DELIVERED", "Delivered", False),
        ("SHIPMENT_CANCELLED", "Cancelled", False)],
        [("SHIPMENT_INPUT", "SHIPMENT_SCHEDULED", "Schedule"), ("SHIPMENT_INPUT", "SHIPMENT_PICKED", "Pick"),
         ("SHIPMENT_INPUT", "SHIPMENT_PACKED", "Pack"), ("SHIPMENT_INPUT", "SHIPMENT_CANCELLED", "Cancel"),
         ("SHIPMENT_SCHEDULED", "SHIPMENT_PICKED", "Pick"), ("SHIPMENT_SCHEDULED", "SHIPMENT_CANCELLED", "Cancel"),
         ("SHIPMENT_PICKED", "SHIPMENT_PACKED", "Pack"), ("SHIPMENT_PICKED", "SHIPMENT_CANCELLED", "Cancel"),
         ("SHIPMENT_PACKED", "SHIPMENT_SHIPPED", "Ship"), ("SHIPMENT_PACKED", "SHIPMENT_CANCELLED", "Cancel"),
         ("SHIPMENT_SHIPPED", "SHIPMENT_DELIVERED", "Deliver")]),
    "PICKLIST_STATUS": ([
        ("PICKLIST_INPUT", "Created", True), ("PICKLIST_ASSIGNED", "Assigned", False), ("PICKLIST_PRINTED", "Printed", False),
        ("PICKLIST_PICKED", "Picked", False), ("PICKLIST_COMPLETED", "Completed", False), ("PICKLIST_CANCELLED", "Cancelled", False)],
        [("PICKLIST_INPUT", "PICKLIST_ASSIGNED", "Assign"), ("PICKLIST_INPUT", "PICKLIST_PRINTED", "Print"),
         ("PICKLIST_INPUT", "PICKLIST_PICKED", "Pick"), ("PICKLIST_INPUT", "PICKLIST_CANCELLED", "Cancel"),
         ("PICKLIST_ASSIGNED", "PICKLIST_PRINTED", "Print"), ("PICKLIST_ASSIGNED", "PICKLIST_PICKED", "Pick"),
         ("PICKLIST_ASSIGNED", "PICKLIST_CANCELLED", "Cancel"), ("PICKLIST_PRINTED", "PICKLIST_PICKED", "Pick"),
         ("PICKLIST_PRINTED", "PICKLIST_CANCELLED", "Cancel"), ("PICKLIST_PICKED", "PICKLIST_COMPLETED", "Complete")]),
    "RETURN_STATUS": ([
        ("RETURN_REQUESTED", "Requested", True), ("RETURN_ACCEPTED", "Accepted", False), ("RETURN_RECEIVED", "Received", False),
        ("RETURN_MAN_REFUND", "Manual refund required", False), ("RETURN_COMPLETED", "Completed", False),
        ("RETURN_CANCELLED", "Cancelled", False)],
        [("RETURN_REQUESTED", "RETURN_ACCEPTED", "Accept"), ("RETURN_REQUESTED", "RETURN_CANCELLED", "Cancel"),
         ("RETURN_ACCEPTED", "RETURN_RECEIVED", "Receive"), ("RETURN_ACCEPTED", "RETURN_MAN_REFUND", "Refund by hand"),
         ("RETURN_ACCEPTED", "RETURN_CANCELLED", "Cancel"), ("RETURN_RECEIVED", "RETURN_COMPLETED", "Complete"),
         ("RETURN_RECEIVED", "RETURN_MAN_REFUND", "Refund by hand"), ("RETURN_MAN_REFUND", "RETURN_COMPLETED", "Complete")]),
    "INVENTORY_ITEM_STATUS": ([
        ("INV_AVAILABLE", "Available", True), ("INV_ON_HOLD", "On hold", False), ("INV_DEFECTIVE", "Defective", False),
        ("INV_RETURNED", "Returned", False)],
        [("INV_AVAILABLE", "INV_ON_HOLD", "Hold"), ("INV_AVAILABLE", "INV_DEFECTIVE", "Mark defective"),
         ("INV_ON_HOLD", "INV_AVAILABLE", "Release"), ("INV_ON_HOLD", "INV_DEFECTIVE", "Mark defective"),
         ("INV_RETURNED", "INV_AVAILABLE", "Restock"), ("INV_RETURNED", "INV_DEFECTIVE", "Mark defective")]),
    "SystemMessage": ([
        ("SmsgProduced", "Produced", True), ("SmsgSending", "Sending", False), ("SmsgSent", "Sent", False),
        ("SmsgConfirmed", "Confirmed", False), ("SmsgReceived", "Received", False), ("SmsgConsuming", "Consuming", False),
        ("SmsgConsumed", "Consumed", False), ("SmsgRejected", "Rejected", False), ("SmsgError", "Error", False),
        ("SmsgCancelled", "Cancelled", False)],
        [("SmsgProduced", "SmsgSending", "Send"), ("SmsgProduced", "SmsgCancelled", "Cancel"),
         ("SmsgSending", "SmsgSent", "Sent"), ("SmsgSending", "SmsgError", "Fail"), ("SmsgSent", "SmsgConfirmed", "Confirm"),
         ("SmsgReceived", "SmsgConsuming", "Consume"), ("SmsgReceived", "SmsgRejected", "Reject"),
         ("SmsgReceived", "SmsgCancelled", "Cancel"), ("SmsgConsuming", "SmsgConsumed", "Consumed"),
         ("SmsgConsuming", "SmsgError", "Fail"), ("SmsgError", "SmsgSending", "Retry send"),
         ("SmsgError", "SmsgConsuming", "Retry consume"), ("SmsgError", "SmsgCancelled", "Cancel")]),
    "DataManagerLog": ([
        ("DmlsPending", "Pending", True), ("DmlsRunning", "Running", False), ("DmlsFinished", "Finished", False),
        ("DmlsCrashed", "Crashed", False), ("DmlsCancelled", "Cancelled", False)],
        [("DmlsPending", "DmlsRunning", "Start"), ("DmlsPending", "DmlsCancelled", "Cancel"),
         ("DmlsRunning", "DmlsFinished", "Finish"), ("DmlsRunning", "DmlsCrashed", "Crash"),
         ("DmlsRunning", "DmlsCancelled", "Cancel")]),
}
rows("status_type", ["status_type_id", "description"], [(t, t.replace("_", " ").title()) for t in STATUSES])
rows("status_item", ["status_id", "status_type_id", "status_code", "sequence_num", "description"],
     [(s, t, s.split("_", 1)[-1] if "_" in s else s, i + 1, d) for t, (items, _) in STATUSES.items() for i, (s, d, _) in enumerate(items)])
rows("status_flow", ["status_flow_id", "status_type_id", "description"], [(FLOW, "ORDER_STATUS", "Default allowed changes for every status type")])
rows("status_flow_item", ["status_flow_id", "status_id", "is_initial"],
     [(FLOW, s, "Y" if init else "N") for _, (items, _) in STATUSES.items() for s, _, init in items])
rows("status_flow_transition", ["status_flow_id", "status_id", "to_status_id", "transition_sequence", "transition_name"],
     [(FLOW, a, b, i + 1, n) for _, (_, trans) in STATUSES.items() for i, (a, b, n) in enumerate(trans)])

# ── units and currencies ──
rows("uom_type", ["uom_type_id", "description"], [
    ("CURRENCY_MEASURE", "Currency"), ("WEIGHT_MEASURE", "Weight"), ("LENGTH_MEASURE", "Length"),
    ("VOLUME_MEASURE", "Volume"), ("TIME_FREQ_MEASURE", "Time"),
])
rows("uom", ["uom_id", "uom_type_id", "abbreviation", "numeric_code", "description"], [
    ("USD", "CURRENCY_MEASURE", "$", 840, "US dollar"), ("EUR", "CURRENCY_MEASURE", "€", 978, "Euro"),
    ("GBP", "CURRENCY_MEASURE", "£", 826, "Pound sterling"), ("INR", "CURRENCY_MEASURE", "₹", 356, "Indian rupee"),
    ("CAD", "CURRENCY_MEASURE", "CA$", 124, "Canadian dollar"), ("AUD", "CURRENCY_MEASURE", "A$", 36, "Australian dollar"),
    ("JPY", "CURRENCY_MEASURE", "¥", 392, "Japanese yen"), ("CNY", "CURRENCY_MEASURE", "CN¥", 156, "Chinese yuan"),
    ("AED", "CURRENCY_MEASURE", "AED", 784, "UAE dirham"), ("SGD", "CURRENCY_MEASURE", "S$", 702, "Singapore dollar"),
    ("WT_kg", "WEIGHT_MEASURE", "kg", None, "Kilogram"), ("WT_g", "WEIGHT_MEASURE", "g", None, "Gram"),
    ("WT_lb", "WEIGHT_MEASURE", "lb", None, "Pound"), ("WT_oz", "WEIGHT_MEASURE", "oz", None, "Ounce"),
    ("LEN_m", "LENGTH_MEASURE", "m", None, "Metre"), ("LEN_cm", "LENGTH_MEASURE", "cm", None, "Centimetre"),
    ("LEN_mm", "LENGTH_MEASURE", "mm", None, "Millimetre"), ("LEN_in", "LENGTH_MEASURE", "in", None, "Inch"),
    ("LEN_ft", "LENGTH_MEASURE", "ft", None, "Foot"),
    ("VLIQ_L", "VOLUME_MEASURE", "L", None, "Litre"), ("VDRY_m3", "VOLUME_MEASURE", "m³", None, "Cubic metre"),
    ("TF_s", "TIME_FREQ_MEASURE", "s", None, "Second"), ("TF_min", "TIME_FREQ_MEASURE", "min", None, "Minute"),
    ("TF_hr", "TIME_FREQ_MEASURE", "h", None, "Hour"), ("TF_day", "TIME_FREQ_MEASURE", "d", None, "Day"),
    ("TF_wk", "TIME_FREQ_MEASURE", "wk", None, "Week"),
])
EPOCH = "2000-01-01 00:00:00"
rows("uom_conversion", ["uom_id", "uom_id_to", "from_date", "conversion_factor"], [
    ("WT_kg", "WT_g", EPOCH, "1000"), ("WT_lb", "WT_kg", EPOCH, "0.453592"), ("WT_oz", "WT_g", EPOCH, "28.349523"),
    ("LEN_m", "LEN_cm", EPOCH, "100"), ("LEN_cm", "LEN_mm", EPOCH, "10"), ("LEN_in", "LEN_cm", EPOCH, "2.54"),
    ("LEN_ft", "LEN_m", EPOCH, "0.3048"), ("TF_min", "TF_s", EPOCH, "60"), ("TF_hr", "TF_min", EPOCH, "60"),
    ("TF_day", "TF_hr", EPOCH, "24"), ("TF_wk", "TF_day", EPOCH, "7"),
])

# ── geography ──
COUNTRIES = [  # (alpha3, alpha2, numeric, name)
    ("USA", "US", "840", "United States"), ("IND", "IN", "356", "India"), ("GBR", "GB", "826", "United Kingdom"),
    ("CAN", "CA", "124", "Canada"), ("AUS", "AU", "036", "Australia"), ("DEU", "DE", "276", "Germany"),
    ("FRA", "FR", "250", "France"), ("NLD", "NL", "528", "Netherlands"), ("ITA", "IT", "380", "Italy"),
    ("ESP", "ES", "724", "Spain"), ("ARE", "AE", "784", "United Arab Emirates"), ("SAU", "SA", "682", "Saudi Arabia"),
    ("SGP", "SG", "702", "Singapore"), ("JPN", "JP", "392", "Japan"), ("CHN", "CN", "156", "China"),
    ("MEX", "MX", "484", "Mexico"), ("BRA", "BR", "076", "Brazil"), ("NZL", "NZ", "554", "New Zealand"),
    ("IRL", "IE", "372", "Ireland"), ("ZAF", "ZA", "710", "South Africa"),
]
US_STATES = [("AL", "Alabama"), ("AK", "Alaska"), ("AZ", "Arizona"), ("AR", "Arkansas"), ("CA", "California"),
             ("CO", "Colorado"), ("CT", "Connecticut"), ("DE", "Delaware"), ("DC", "District of Columbia"),
             ("FL", "Florida"), ("GA", "Georgia"), ("HI", "Hawaii"), ("ID", "Idaho"), ("IL", "Illinois"),
             ("IN", "Indiana"), ("IA", "Iowa"), ("KS", "Kansas"), ("KY", "Kentucky"), ("LA", "Louisiana"),
             ("ME", "Maine"), ("MD", "Maryland"), ("MA", "Massachusetts"), ("MI", "Michigan"), ("MN", "Minnesota"),
             ("MS", "Mississippi"), ("MO", "Missouri"), ("MT", "Montana"), ("NE", "Nebraska"), ("NV", "Nevada"),
             ("NH", "New Hampshire"), ("NJ", "New Jersey"), ("NM", "New Mexico"), ("NY", "New York"),
             ("NC", "North Carolina"), ("ND", "North Dakota"), ("OH", "Ohio"), ("OK", "Oklahoma"), ("OR", "Oregon"),
             ("PA", "Pennsylvania"), ("RI", "Rhode Island"), ("SC", "South Carolina"), ("SD", "South Dakota"),
             ("TN", "Tennessee"), ("TX", "Texas"), ("UT", "Utah"), ("VT", "Vermont"), ("VA", "Virginia"),
             ("WA", "Washington"), ("WV", "West Virginia"), ("WI", "Wisconsin"), ("WY", "Wyoming"), ("PR", "Puerto Rico")]
IN_STATES = [("AN", "Andaman and Nicobar Islands"), ("AP", "Andhra Pradesh"), ("AR", "Arunachal Pradesh"),
             ("AS", "Assam"), ("BR", "Bihar"), ("CH", "Chandigarh"), ("CT", "Chhattisgarh"),
             ("DH", "Dadra and Nagar Haveli and Daman and Diu"), ("DL", "Delhi"), ("GA", "Goa"), ("GJ", "Gujarat"),
             ("HR", "Haryana"), ("HP", "Himachal Pradesh"), ("JK", "Jammu and Kashmir"), ("JH", "Jharkhand"),
             ("KA", "Karnataka"), ("KL", "Kerala"), ("LA", "Ladakh"), ("LD", "Lakshadweep"), ("MP", "Madhya Pradesh"),
             ("MH", "Maharashtra"), ("MN", "Manipur"), ("ML", "Meghalaya"), ("MZ", "Mizoram"), ("NL", "Nagaland"),
             ("OR", "Odisha"), ("PY", "Puducherry"), ("PB", "Punjab"), ("RJ", "Rajasthan"), ("SK", "Sikkim"),
             ("TN", "Tamil Nadu"), ("TG", "Telangana"), ("TR", "Tripura"), ("UP", "Uttar Pradesh"),
             ("UT", "Uttarakhand"), ("WB", "West Bengal")]
rows("geo", ["geo_id", "geo_type_enum_id", "geo_name", "geo_code_alpha2", "geo_code_alpha3", "geo_code_numeric", "abbreviation"],
     [(a3, "GEOT_COUNTRY", n, a2, a3, num, a2) for a3, a2, num, n in COUNTRIES]
     + [(f"US-{c}", "GEOT_STATE", n, None, None, None, c) for c, n in US_STATES]
     + [(f"IN-{c}", "GEOT_STATE", n, None, None, None, c) for c, n in IN_STATES])
rows("geo_assoc", ["geo_id", "to_geo_id", "geo_assoc_type_enum_id"],
     [("USA", f"US-{c}", "GAT_REGIONS") for c, _ in US_STATES] + [("IND", f"IN-{c}", "GAT_REGIONS") for c, _ in IN_STATES])

# ── party (W4) ──
W4_SEED_START = len(SEED)
rows("party_type", ["party_type_id", "parent_type_id", "description"], [
    ("PERSON", None, "Person"), ("PARTY_GROUP", None, "Organisation"),
    ("INFORMAL_GROUP", "PARTY_GROUP", "Informal group"), ("LEGAL_ORGANIZATION", "PARTY_GROUP", "Company"),
])
rows("role_type", ["role_type_id", "parent_type_id", "description"], [
    ("CUSTOMER", None, "Customer"), ("BILL_TO_CUSTOMER", "CUSTOMER", "Bill-to customer"),
    ("SHIP_TO_CUSTOMER", "CUSTOMER", "Ship-to customer"), ("PLACING_CUSTOMER", "CUSTOMER", "Customer who placed the order"),
    ("ACCOUNT", None, "B2B account (a customer company)"), ("CONTACT", None, "Contact person"),
    ("SUPPLIER", None, "Supplier"), ("VENDOR", None, "Vendor"), ("CARRIER", None, "Carrier"),
    ("EMPLOYEE", None, "Employee"), ("INTERNAL_ORGANIZATIO", None, "The company running Nexus"),
    ("ORIGIN_FACILITY_OWNER", None, "Owns a facility"),
])
rows("party_relationship_type", ["party_relationship_type_id", "description"], [
    ("EMPLOYMENT", "Employment"), ("CONTACT_REL", "Contact of an account"), ("CUSTOMER_REL", "Customer of the company"),
    ("SUPPLIER_REL", "Supplier of the company"),
])
rows("party_identification_type", ["party_identification_type_id", "description"], [
    ("SHOPIFY_CUST_ID", "Shopify customer id"), ("BIGCOMMERCE_CUST_ID", "BigCommerce customer id"),
    ("ERP_CUST_ID", "Customer id in the ERP"), ("POS_CUST_ID", "Customer id in the point of sale"),
    ("TAX_ID", "Tax id (GSTIN, EIN, VAT)"), ("NEXUS_LEGACY_ID", "Id in the Nexus model before the core (migration)"),
])
rows("contact_mech_type", ["contact_mech_type_id", "parent_type_id", "description"], [
    ("POSTAL_ADDRESS", None, "Postal address"), ("TELECOM_NUMBER", None, "Phone number"),
    ("ELECTRONIC_ADDRESS", None, "Electronic address"),
    ("EMAIL_ADDRESS", "ELECTRONIC_ADDRESS", "Email address"), ("WEB_ADDRESS", "ELECTRONIC_ADDRESS", "Web address"),
])
rows("contact_mech_purpose_type", ["contact_mech_purpose_type_id", "description"], [
    ("PRIMARY_LOCATION", "Primary address"), ("SHIPPING_LOCATION", "Shipping address"), ("BILLING_LOCATION", "Billing address"),
    ("GENERAL_LOCATION", "Correspondence address"), ("PRIMARY_EMAIL", "Primary email"), ("ORDER_EMAIL", "Order notification email"),
    ("BILLING_EMAIL", "Billing email"), ("PRIMARY_PHONE", "Primary phone"), ("PHONE_MOBILE", "Mobile phone"),
    ("PHONE_WORK", "Work phone"), ("PHONE_SHIPPING", "Phone for deliveries"), ("PRIMARY_WEB_URL", "Website"),
])
rows("contact_mech_type_purpose", ["contact_mech_type_id", "contact_mech_purpose_type_id"],
     [("POSTAL_ADDRESS", p) for p in ("PRIMARY_LOCATION", "SHIPPING_LOCATION", "BILLING_LOCATION", "GENERAL_LOCATION")]
     + [("EMAIL_ADDRESS", p) for p in ("PRIMARY_EMAIL", "ORDER_EMAIL", "BILLING_EMAIL")]
     + [("TELECOM_NUMBER", p) for p in ("PRIMARY_PHONE", "PHONE_MOBILE", "PHONE_WORK", "PHONE_SHIPPING")]
     + [("WEB_ADDRESS", "PRIMARY_WEB_URL")])
rows("status_type", ["status_type_id", "description"], [("PARTY_STATUS", "Party status")])
rows("status_item", ["status_id", "status_type_id", "status_code", "sequence_num", "description"], [
    ("PARTY_ENABLED", "PARTY_STATUS", "ENABLED", 1, "Enabled"), ("PARTY_DISABLED", "PARTY_STATUS", "DISABLED", 2, "Disabled"),
])
rows("status_flow_item", ["status_flow_id", "status_id", "is_initial"], [(FLOW, "PARTY_ENABLED", "Y"), (FLOW, "PARTY_DISABLED", "N")])
rows("status_flow_transition", ["status_flow_id", "status_id", "to_status_id", "transition_sequence", "transition_name"], [
    (FLOW, "PARTY_ENABLED", "PARTY_DISABLED", 1, "Disable"), (FLOW, "PARTY_DISABLED", "PARTY_ENABLED", 1, "Enable"),
])

# ── product and product store (W5) ──
W5_SEED_START = len(SEED)
rows("product_type", ["product_type_id", "parent_type_id", "is_physical", "is_digital", "description"], [
    ("GOOD", None, "Y", "N", "Good"), ("FINISHED_GOOD", "GOOD", "Y", "N", "Finished good"),
    ("DIGITAL_GOOD", None, "N", "Y", "Digital good"), ("SERVICE", None, "N", "N", "Service"),
    ("MARKETING_PKG", "GOOD", "Y", "N", "Bundle sold as one product"), ("GIFT_CARD", None, "N", "Y", "Gift card"),
])
rows("product_assoc_type", ["product_assoc_type_id", "parent_type_id", "description"], [
    ("PRODUCT_VARIANT", None, "Variant of a virtual product"), ("PRODUCT_SUBSTITUTE", None, "Can ship instead"),
    ("PRODUCT_COMPONENT", None, "Component of a bundle or kit"), ("PRODUCT_COMPLEMENT", None, "Goes well with"),
    ("PRODUCT_UPGRADE", None, "Upgrade of"), ("PRODUCT_OBSOLESCENCE", None, "Replaced by"),
])
rows("good_identification_type", ["good_identification_type_id", "description"], [
    ("SKU", "Stock keeping unit"), ("UPCA", "UPC-A barcode"), ("EAN", "EAN-13 barcode"), ("ISBN", "ISBN"),
    ("MANUFACTURER_ID_NO", "Manufacturer part number"), ("SHOPIFY_PROD_ID", "Shopify product id"),
    ("SHOPIFY_VARIANT_ID", "Shopify variant id"), ("BIGCOMMERCE_PROD_ID", "BigCommerce product id"),
    ("BIGCOMMERCE_VARIANT_ID", "BigCommerce variant id"), ("ERP_ID", "Id in the ERP"),
    ("NEXUS_LEGACY_ID", "Id in the Nexus model before the core (migration)"),
])
rows("product_feature_type", ["product_feature_type_id", "description"], [
    ("COLOR", "Colour"), ("SIZE", "Size"), ("STYLE", "Style"), ("MATERIAL", "Material"), ("WIDTH", "Width"),
    ("BRAND", "Brand"), ("OTHER_FEATURE", "Other"),
])
rows("product_feature_appl_type", ["product_feature_appl_type_id", "description"], [
    ("STANDARD_FEATURE", "Standard"), ("SELECTABLE_FEATURE", "Customer chooses it (on the virtual product)"),
    ("DISTINGUISHING_FEAT", "Tells variants apart (on the variant)"), ("OPTIONAL_FEATURE", "Optional"),
])
rows("product_category_type", ["product_category_type_id", "description"], [
    ("CATALOG_CATEGORY", "Catalogue category"), ("BROWSE_ROOT", "Top of a browse tree"),
    ("INTERNAL_CATEGORY", "Internal grouping (routing, reports)"),
])
rows("product_price_type", ["product_price_type_id", "description"], [
    ("DEFAULT_PRICE", "Selling price"), ("LIST_PRICE", "List price (compare at)"), ("PROMO_PRICE", "Promotional price"),
    ("WHOLESALE_PRICE", "Wholesale price"), ("AVERAGE_COST", "Average cost"),
])
rows("product_price_purpose", ["product_price_purpose_id", "description"], [("PURCHASE", "Purchase"), ("RECURRING_CHARGE", "Recurring charge")])
rows("product_store_group", ["product_store_group_id", "product_store_group_name", "description"], [("_NA_", "All stores", "Prices that apply to every store")])
rows("enumeration_type", ["enum_type_id", "description"], [
    ("PRODUCT_STORE_SETTING", "A store setting"), ("PRODUCT_IDENTIFIER", "Which id a store's channel uses for products"),
])
rows("enumeration", ["enum_id", "enum_type_id", "enum_code", "sequence_num", "description"], [
    ("RELEASE_DAYS_B2B", "PRODUCT_STORE_SETTING", "RELEASE_DAYS_B2B", 1, "Days before ship date to release B2B orders"),
    ("RELEASE_DAYS_B2C", "PRODUCT_STORE_SETTING", "RELEASE_DAYS_B2C", 2, "Days before ship date to release B2C orders"),
    ("MAX_SPLIT_LOCATIONS", "PRODUCT_STORE_SETTING", "MAX_SPLIT_LOCATIONS", 3, "Most locations an order may ship from"),
    ("REJECT_PROFILE", "PRODUCT_STORE_SETTING", "REJECT_PROFILE", 4, "How many partial rejections before the whole order moves"),
    ("ORDER_HOLD_MINUTES", "PRODUCT_STORE_SETTING", "ORDER_HOLD_MINUTES", 5, "Minutes to hold new orders before routing (fraud and edits)"),
    ("PI_SKU", "PRODUCT_IDENTIFIER", "SKU", 1, "SKU"), ("PI_SHOPIFY_VARIANT", "PRODUCT_IDENTIFIER", "SHOPIFY_VARIANT_ID", 2, "Shopify variant id"),
    ("PI_UPCA", "PRODUCT_IDENTIFIER", "UPCA", 3, "UPC barcode"),
])

# ── facility and inventory (W6) ──
W6_SEED_START = len(SEED)
rows("facility_type", ["facility_type_id", "parent_type_id", "description"], [
    ("PHYSICAL_FACILITY", None, "Physical place"), ("WAREHOUSE", "PHYSICAL_FACILITY", "Warehouse"),
    ("DISTRIBUTION_CENTER", "WAREHOUSE", "Distribution centre"), ("RETAIL_STORE", "PHYSICAL_FACILITY", "Retail store"),
    ("DARK_STORE", "PHYSICAL_FACILITY", "Dark store (online orders only)"), ("THIRD_PARTY_LOGISTICS", "WAREHOUSE", "3PL warehouse"),
    ("VIRTUAL_FACILITY", None, "Virtual: not a place"), ("IN_TRANSIT", "VIRTUAL_FACILITY", "Stock moving between places"),
    ("DROPSHIP", "VIRTUAL_FACILITY", "Supplier ships directly"),
])
rows("facility_identification_type", ["facility_iden_type_id", "description"], [
    ("SHOPIFY_LOCATION_ID", "Shopify location id"), ("POS_LOCATION_ID", "Point of sale location id"),
    ("ERP_FACILITY_ID", "Facility id in the ERP"), ("WMS_FACILITY_ID", "Facility id in the WMS"),
    ("NEXUS_LEGACY_ID", "Id in the Nexus model before the core (migration)"),
])
rows("facility_group_type", ["facility_group_type_id", "description"], [
    ("BROKERING_GROUP", "Routing group"), ("REGION", "Region"), ("PICKUP_GROUP", "Stores offering pickup"),
    ("CLUSTER", "Store cluster for transfers"),
])
rows("inventory_item_type", ["inventory_item_type_id", "description"], [
    ("NON_SERIAL_INV_ITEM", "Counted in bulk"), ("SERIALIZED_INV_ITEM", "One item per serial number"),
])
rows("variance_reason", ["variance_reason_id", "description"], [
    ("VAR_LOST", "Lost"), ("VAR_FOUND", "Found"), ("VAR_DAMAGED", "Damaged"), ("VAR_STOLEN", "Stolen"),
    ("VAR_SAMPLE", "Sample or giveaway"), ("VAR_MISSHIP", "Mis-shipped"), ("VAR_COUNT", "Count correction"),
])
rows("enumeration_type", ["enum_type_id", "description"], [
    ("INV_DETAIL_REASON", "Why stock changed"), ("FACILITY_LOCATION_TYPE", "Kind of place inside a facility"),
])
rows("enumeration", ["enum_id", "enum_type_id", "enum_code", "sequence_num", "description"], [
    ("IID_RECEIPT", "INV_DETAIL_REASON", "RECEIPT", 1, "Received"), ("IID_SHIPMENT", "INV_DETAIL_REASON", "SHIPMENT", 2, "Shipped"),
    ("IID_RESERVE", "INV_DETAIL_REASON", "RESERVE", 3, "Reserved for an order"), ("IID_RELEASE", "INV_DETAIL_REASON", "RELEASE", 4, "Reservation released"),
    ("IID_RETURN", "INV_DETAIL_REASON", "RETURN", 5, "Returned by a customer"), ("IID_ADJUST", "INV_DETAIL_REASON", "ADJUST", 6, "Adjusted by hand"),
    ("IID_COUNT", "INV_DETAIL_REASON", "COUNT", 7, "Count variance"), ("IID_TRANSFER_OUT", "INV_DETAIL_REASON", "TRANSFER_OUT", 8, "Sent to another facility"),
    ("IID_TRANSFER_IN", "INV_DETAIL_REASON", "TRANSFER_IN", 9, "Received from another facility"),
    ("IID_HOLD", "INV_DETAIL_REASON", "HOLD", 10, "Held (damage, quality, rejection)"), ("IID_FEED", "INV_DETAIL_REASON", "FEED", 11, "Stock feed from ERP, POS or WMS"),
    ("IID_MIGRATION", "INV_DETAIL_REASON", "MIGRATION", 12, "Copied from the earlier Nexus model"),
    ("FLT_PICKLOC", "FACILITY_LOCATION_TYPE", "PICK", 1, "Pick location"), ("FLT_BULK", "FACILITY_LOCATION_TYPE", "BULK", 2, "Bulk storage"),
    ("FLT_BACKROOM", "FACILITY_LOCATION_TYPE", "BACKROOM", 3, "Store backroom"), ("FLT_SALESFLOOR", "FACILITY_LOCATION_TYPE", "SALESFLOOR", 4, "Sales floor"),
])
rows("status_type", ["status_type_id", "description"], [("INVENTORY_XFER_STATUS", "Transfer status")])
rows("status_item", ["status_id", "status_type_id", "status_code", "sequence_num", "description"], [
    ("IXF_REQUESTED", "INVENTORY_XFER_STATUS", "REQUESTED", 1, "Requested"), ("IXF_SCHEDULED", "INVENTORY_XFER_STATUS", "SCHEDULED", 2, "Approved"),
    ("IXF_EN_ROUTE", "INVENTORY_XFER_STATUS", "EN_ROUTE", 3, "In transit"), ("IXF_COMPLETE", "INVENTORY_XFER_STATUS", "COMPLETE", 4, "Received"),
    ("IXF_CANCELLED", "INVENTORY_XFER_STATUS", "CANCELLED", 5, "Cancelled"),
])
rows("status_flow_item", ["status_flow_id", "status_id", "is_initial"], [
    (FLOW, "IXF_REQUESTED", "Y"), (FLOW, "IXF_SCHEDULED", "N"), (FLOW, "IXF_EN_ROUTE", "N"), (FLOW, "IXF_COMPLETE", "N"), (FLOW, "IXF_CANCELLED", "N"),
])
rows("status_flow_transition", ["status_flow_id", "status_id", "to_status_id", "transition_sequence", "transition_name"], [
    (FLOW, "IXF_REQUESTED", "IXF_SCHEDULED", 1, "Approve"), (FLOW, "IXF_REQUESTED", "IXF_CANCELLED", 2, "Cancel"),
    (FLOW, "IXF_SCHEDULED", "IXF_EN_ROUTE", 1, "Ship"), (FLOW, "IXF_SCHEDULED", "IXF_CANCELLED", 2, "Cancel"),
    (FLOW, "IXF_EN_ROUTE", "IXF_COMPLETE", 1, "Receive"),
])

SEED_MIGRATIONS = [
    (2, "core_seed", SEED[:W4_SEED_START]),
    (4, "core_party_seed", SEED[W4_SEED_START:W5_SEED_START]),
    (6, "core_product_seed", SEED[W5_SEED_START:W6_SEED_START]),
    (8, "core_facility_inventory_seed", SEED[W6_SEED_START:]),
]
