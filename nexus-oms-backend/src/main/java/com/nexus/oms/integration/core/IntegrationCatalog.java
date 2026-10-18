package com.nexus.oms.integration.core;

import java.util.*;

/**
 * What Nexus can honestly do with each outside system, and what backs the claim.
 *
 * Evidence, strongest first:
 *   TESTED           automated tests run the logic (outside HTTP mocked); CI-checked.
 *   CODE_ONLY        written, but no automated test covers it.
 *   NOT_IMPLEMENTED  the screen may list the platform; Nexus does nothing with it for this capability.
 *
 * {@code liveVerified} would mean "run against the real platform with real credentials". Nothing has been,
 * so it is false everywhere, and a test fails if anyone sets it without also recording how in {@link #LIVE_EVIDENCE}.
 * Guard tests keep this in step with the connector metadata, the scheduler and the registered platforms.
 */
public final class IntegrationCatalog {

    public enum Evidence { TESTED, CODE_ONLY, NOT_IMPLEMENTED }

    public record Capability(String name, Evidence evidence, boolean liveVerified, String note) {}

    /** group: sales-channels, point-of-sale, shipping, erp-accounting, payments, edi, messaging, identity, ai, equipment, analytics */
    public record Entry(String key, String name, String group, String tier, String route, String summary,
                        List<Capability> capabilities) {
        public boolean hasAnythingWorking() {
            return capabilities.stream().anyMatch(c -> c.evidence() != Evidence.NOT_IMPLEMENTED);
        }
    }

    /** Where a live verification is recorded, per "<entry key>/<capability>". Empty: nothing has been run against a real platform. */
    public static final Map<String, String> LIVE_EVIDENCE = Map.of(
        "SHOPIFY/Connect with sealed credentials", "2026-10-15: dev shop nexus-ship-ufb7gevv; token saved sealed, never returned by the API; shop, scopes and locations read",
        "SHOPIFY/Order import", "2026-10-15: dev shop; 5,052 of the 5,052 orders the API lists imported, order total 3,679,289.59 and 9,445 line items equal Shopify's. 8 older orders need the read_all_orders scope",
        "SHOPIFY/Product sync", "2026-10-15: dev shop; 4,935 products (9,996 variants) imported, product count equals Shopify's",
        "SHOPIFY/Inventory publishing by location", "2026-10-15: dev shop; one SKU (100001) published for real to one location: Shopify read back 7 units, a repeat run reported 'already correct' and wrote nothing. Full-catalogue publish not run",
        "BIGCOMMERCE/Product sync", "2026-10-15: dev store; 13 products and 68 variants imported, equal to the store's catalogue");

    private static Capability t(String n, String note) { return new Capability(n, Evidence.TESTED, live(n), note); }
    private static Capability c(String n, String note) { return new Capability(n, Evidence.CODE_ONLY, false, note); }
    private static Capability x(String n, String note) { return new Capability(n, Evidence.NOT_IMPLEMENTED, false, note); }
    private static boolean live(String n) { return false; }
    /** Run against the real platform; each one needs a dated entry in {@link #LIVE_EVIDENCE} (a test enforces it). */
    private static Capability tl(String n, String note) { return new Capability(n, Evidence.TESTED, true, note); }
    private static Capability cl(String n, String note) { return new Capability(n, Evidence.CODE_ONLY, true, note); }

    private static final String CONNECT_TEST = "Connection test";
    private static final String PREVIEW = "Read preview";

    private static Entry generic(String key, String name, String group, String route) {
        return new Entry(key, name, group, "GENERIC", route,
                "No dedicated connector. You can save an address and credentials and test that the address answers; Nexus reads nothing into its own data and writes nothing back.",
                List.of(c(CONNECT_TEST, "A GET to the address you supply"),
                        c(PREVIEW, "Counts records from paths you configure; imports nothing"),
                        x("Orders, inventory, fulfilments, refunds", "Not built for " + name)));
    }

    private static final List<Entry> ENTRIES = List.of(
        // ── Sales channels ───────────────────────────────────────────────
        new Entry("SHOPIFY", "Shopify", "sales-channels", "NATIVE", "/integrations/shopify",
            "Built into Nexus end to end: orders arrive by verified webhook and scheduled import, stock is published per location, shipments and refunds go back.",
            List.of(tl("Connect with sealed credentials", "Secrets are encrypted at rest and never returned by the API"),
                    t("Receive webhooks", "Signature verified first, duplicates ignored"),
                    cl("Order import", "Checked live against a dev shop (totals match). Webhook and scheduled; no automated test"),
                    cl("Product sync", "Checked live against a dev shop (counts match). No automated test"),
                    tl("Inventory publishing by location", "Dry run by default, drift detection, per-location mapping. One SKU written and read back live; a full-catalogue publish has not been run"),
                    c("Fulfilment push", "No automated test"),
                    c("Refund sync", "No automated test"))),
        new Entry("BIGCOMMERCE", "BigCommerce", "sales-channels", "NATIVE", "/integrations/bigcommerce",
            "Built into Nexus: orders, products, customers, stock, shipments and refunds, on a schedule and by webhook.",
            List.of(c("Order import", "Ran live against a dev store with no orders, so it proves the connection only"),
                    cl("Product sync", "Checked live against a dev store (counts match). No automated test"),
                    c("Customer import", "No automated test"),
                    t("Inventory push", "Unit tested"), t("Order status push", "Unit tested"),
                    c("Shipment push", "No automated test"), c("Refund sync", "No automated test"),
                    c("Receive webhooks", "No automated test"))),
        new Entry("MAGENTO", "Magento / Adobe Commerce", "sales-channels", "CONNECTOR", "/integration-hub",
            "A connector that can authenticate and read orders and products. Nothing is imported into Nexus.",
            List.of(c(CONNECT_TEST, "Reads the store's websites"), c(PREVIEW, "Counts orders and products"),
                    x("Importing orders or products", "Not built"), x("Inventory, fulfilment and refund push", "Not built"))),
        new Entry("AMAZON", "Amazon", "sales-channels", "CONNECTOR", "/integrations/amazon",
            "A connector that can obtain a token and read orders and catalogue entries. No marketplace sync exists.",
            List.of(c(CONNECT_TEST, "Obtains a Selling Partner access token"), c(PREVIEW, "Counts orders and catalogue items"),
                    x("Importing orders", "Not built"), x("Inventory feeds and fulfilment", "Not built"))),
        generic("EBAY", "eBay", "sales-channels", "/integrations/ebay"),
        generic("WALMART", "Walmart Marketplace", "sales-channels", "/integrations/walmart"),
        generic("ETSY", "Etsy", "sales-channels", "/integration-hub"),
        generic("ALIBABA", "Alibaba.com", "sales-channels", "/integration-hub"),

        // ── Point of sale ────────────────────────────────────────────────
        new Entry("POS_PUSH", "POS stock feed (any system)", "point-of-sale", "NATIVE", "/store-operations",
            "Any store system, or middleware in front of it, can push its stock counts. Nexus compares them with its own and corrects only on request.",
            List.of(t("Receive counts", "Per-source secret, hash stored, stale and out-of-order reports handled"),
                    t("Compare with Nexus stock", "Match, differs, old, not reported, not stocked"),
                    t("Reconcile", "Preview first, ledger-backed, never below what is allocated"),
                    x("Pulling from a vendor's API", "No vendor adapter exists"),
                    x("Ingesting individual sales", "Counts only"))),
        generic("SQUARE", "Square", "point-of-sale", "/store-operations"),
        generic("LIGHTSPEED", "Lightspeed", "point-of-sale", "/store-operations"),

        // ── Shipping ─────────────────────────────────────────────────────
        new Entry("CARRIER_LABELS", "Carrier labels", "shipping", "NATIVE", "/carriers",
            "Labels come from a configured carrier adapter, or carry a tracking number you enter from your carrier account. Production never invents one.",
            List.of(t("Enter a tracking number from your carrier", "Recorded as MANUAL"),
                    t("Refuse to invent tracking numbers in production", "Demo labels are SIM- prefixed and only exist when simulation is on"),
                    c("Buy a label through a configured adapter", "Adapters are configuration-driven; none has been run against a carrier"),
                    x("Tracking updates from carriers", "No carrier tracking feed"))),
        new Entry("RATE_CARDS", "Rate shopping from your rate cards", "shipping", "NATIVE", "/rate-shopping",
            "Compares the carrier rates you have entered. It does not ask carriers for live quotes.",
            List.of(t("Compare entered rates", "Unit tested"), x("Live carrier quotes", "Not built"))),
        new Entry("FEDEX", "FedEx", "shipping", "CONNECTOR", "/integration-hub",
            "A connector that can authenticate and call FedEx's rate, shipment and tracking APIs. Not wired into order fulfilment.",
            List.of(c(CONNECT_TEST, "OAuth token request"), c("Create shipment", "Spends money at FedEx when run"),
                    x("Tracking feed into orders", "Not built"))),
        generic("UPS", "UPS", "shipping", "/integration-hub"),
        generic("DHL", "DHL Express", "shipping", "/integration-hub"),
        generic("PROJECT44", "project44", "shipping", "/integration-hub"),
        generic("FOURKITES", "FourKites", "shipping", "/integration-hub"),

        // ── ERP & accounting ─────────────────────────────────────────────
        new Entry("QUICKBOOKS", "QuickBooks Online", "erp-accounting", "CONNECTOR", "/integration-hub",
            "Can post credit memos for refunds, safely repeatable. Reading invoices is a preview only.",
            List.of(t("Refund credit memos", "Deduplicated, unit tested"), c(CONNECT_TEST, "Reads company info"),
                    c(PREVIEW, "Counts invoices"), x("Automatic webhooks", "Set up in the Intuit developer portal"))),
        new Entry("SAP", "SAP S/4HANA", "erp-accounting", "CONNECTOR", "/integration-hub",
            "Not integrated. Only a reachability probe over SOAP exists.",
            List.of(c(CONNECT_TEST, "SOAP protocol only; does not prove credentials"),
                    x("Orders, products, stock, deliveries, credit memos", "Not built"))),
        new Entry("NETSUITE", "NetSuite", "erp-accounting", "NATIVE", "/integration-hub",
            "Reads stock counts from NetSuite into Nexus through SuiteQL with token-based authentication. Built from NetSuite's published API and not yet run against a real account. Set up through the API for now; there is no screen for it yet.",
            List.of(c("Connect with sealed credentials", "Account id and four token values are stored sealed; configured through the API, no screen yet"),
                    c(CONNECT_TEST, "A one-row SuiteQL read; never run against a real account"),
                    c("Inventory import", "Dry run by default, never overwrites existing stock unless asked; the query is a setting because account layouts differ"),
                    x("Order and fulfilment sync", "Not built"))),
        generic("ORACLE_ERP", "Oracle ERP Cloud", "erp-accounting", "/integration-hub"),
        generic("DYNAMICS_365", "Microsoft Dynamics 365", "erp-accounting", "/integration-hub"),
        generic("XERO", "Xero", "erp-accounting", "/integration-hub"),
        generic("SAGE", "Sage", "erp-accounting", "/integration-hub"),
        new Entry("SALESFORCE", "Salesforce", "erp-accounting", "CONNECTOR", "/integration-hub",
            "Can authenticate and count orders. Nothing is imported.",
            List.of(c(CONNECT_TEST, "Lists sobjects"), c(PREVIEW, "Counts orders"), x("Importing or writing records", "Not built"))),
        generic("HUBSPOT", "HubSpot", "erp-accounting", "/integration-hub"),
        generic("ZOHO_CRM", "Zoho CRM", "erp-accounting", "/integration-hub"),
        generic("MANHATTAN", "Manhattan Associates", "erp-accounting", "/integration-hub"),
        generic("BLUE_YONDER", "Blue Yonder", "erp-accounting", "/integration-hub"),
        generic("ORACLE_WMS", "Oracle WMS Cloud", "erp-accounting", "/integration-hub"),

        // ── Payments ─────────────────────────────────────────────────────
        new Entry("STRIPE", "Stripe", "payments", "CONNECTOR", "/integration-hub",
            "Can issue refunds against a charge. Reading charges is a preview; payment webhooks are not received.",
            List.of(c("Issue a refund", "Moves real money when run"), c(CONNECT_TEST, "Reads the balance"),
                    c(PREVIEW, "Counts charges"), x("Payment webhooks", "Not received"))),
        generic("PAYPAL", "PayPal", "payments", "/integration-hub"),
        generic("ADYEN", "Adyen", "payments", "/integration-hub"),

        // ── EDI ──────────────────────────────────────────────────────────
        new Entry("EDI", "EDI (X12)", "edi", "NATIVE", "/edi",
            "Reads and writes the X12 documents (850, 856, 810, 940, 855, 997). Documents are uploaded or downloaded by hand.",
            List.of(t("Parse and generate X12 documents", "Unit tested"),
                    x("Automatic transport (AS2, SFTP, VAN)", "Not built"))),

        // ── Messaging ────────────────────────────────────────────────────
        new Entry("TWILIO", "Twilio SMS", "messaging", "CONNECTOR", "/integration-hub",
            "Can send a text you compose. No sender or message is assumed.",
            List.of(c("Send an SMS", "Needs to, body and a sender; sends to a real phone"), c(CONNECT_TEST, "Reads the account"),
                    x("Delivery status callbacks", "Not received"))),
        generic("SENDGRID", "SendGrid", "messaging", "/integration-hub"),
        generic("SLACK", "Slack", "messaging", "/integration-hub"),

        // ── Identity ─────────────────────────────────────────────────────
        new Entry("SSO", "Single sign-on and MFA", "identity", "NATIVE", "/account/security",
            "OpenID Connect ID-token sign-in and authenticator-app MFA with recovery codes.",
            List.of(t("OIDC sign-in", "ID token validated against the provider's keys"), t("Authenticator-app MFA", "Verified end to end"),
                    x("SCIM user provisioning", "Not built"))),
        new Entry("OKTA", "Okta (directory read)", "identity", "CONNECTOR", "/integration-hub",
            "Can count directory users. Sign-in does not depend on this connector.",
            List.of(c(CONNECT_TEST, "Reads the organisation"), c(PREVIEW, "Counts users"))),
        generic("AUTH0", "Auth0", "identity", "/integration-hub"),

        // ── AI ───────────────────────────────────────────────────────────
        new Entry("OPENAI", "OpenAI assistant", "ai", "NATIVE", "/ai",
            "The assistant answers only when you configure a model key; otherwise it says it is unavailable.",
            List.of(t("Assistant answers from a configured model", "Refuses rather than invents"),
                    c(CONNECT_TEST, "Lists models"))),
        generic("ANTHROPIC", "Anthropic Claude", "ai", "/integration-hub"),
        generic("AWS_BEDROCK", "AWS Bedrock", "ai", "/integration-hub"),

        // ── Equipment ────────────────────────────────────────────────────
        new Entry("EQUIPMENT", "Warehouse equipment", "equipment", "NATIVE", "/automation-systems",
            "Commands go to a system's HTTP endpoint; the equipment reports back by authenticated callback and heartbeat. Silent commands time out and silent equipment raises an alert.",
            List.of(t("Dispatch commands to an endpoint", "Unit tested"), t("Callbacks and heartbeat", "Authenticated, idempotent"),
                    t("Timeouts, alerts and safe retry", "Unit tested"),
                    x("Industrial protocols (OPC-UA, MQTT, vendor APIs)", "Plain HTTPS JSON only"))),

        // ── Analytics ────────────────────────────────────────────────────
        generic("POWER_BI", "Microsoft Power BI", "analytics", "/integration-hub"),
        generic("TABLEAU", "Tableau", "analytics", "/integration-hub"),
        generic("LOOKER", "Looker", "analytics", "/integration-hub"));

    public static final List<String> GROUP_ORDER = List.of("sales-channels", "point-of-sale", "shipping", "erp-accounting",
            "payments", "edi", "messaging", "identity", "ai", "equipment", "analytics");

    public static List<Entry> entries() { return ENTRIES; }

    private IntegrationCatalog() {}
}
