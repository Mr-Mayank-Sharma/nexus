package com.nexus.oms.generic;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Starting points for generic connections, written from each platform's public API documentation. None has been
 * run against a real store yet: preview a connection against your own store before scheduling it.
 */
final class GenericTemplates {

    private GenericTemplates() {}

    private static final String UNTESTED = "Written from the public API documentation; not yet run against a real store. Preview before you schedule it.";

    static final List<GenericConnectionService.Template> ALL = List.of(
        new GenericConnectionService.Template("woocommerce-orders", "WooCommerce: import orders", "IMPORT", "orders", "WOOCOMMERCE",
            "REST API v3 with a consumer key (username) and consumer secret (secret). " + UNTESTED,
            Map.of(
                "request", Map.of("baseUrl", "https://your-store.example.com", "path", "/wp-json/wc/v3/orders", "query", Map.of("status", "processing")),
                "auth", Map.of("type", "BASIC", "username", "ck_your_consumer_key"),
                "pagination", Map.of("type", "PAGE", "pageParam", "page", "sizeParam", "per_page", "size", 50, "maxPages", 40),
                "incremental", Map.of("param", "modified_after", "format", "ISO"),
                "recordsPath", "",
                "fields", Map.ofEntries(
                    entry("orderId", "id"), entry("orderNumber", "number"), entry("status", "status"), entry("currency", "currency"),
                    entry("total", "total"), entry("tax", "total_tax"), entry("shipping", "shipping_total"),
                    entry("email", "billing.email"), entry("customerName", "{billing.first_name} {billing.last_name}"), entry("phone", "billing.phone"),
                    entry("shipName", "{shipping.first_name} {shipping.last_name}"), entry("shipLine1", "shipping.address_1"), entry("shipLine2", "shipping.address_2"),
                    entry("shipCity", "shipping.city"), entry("shipState", "shipping.state"), entry("shipPostalCode", "shipping.postcode"),
                    entry("shipCountry", "shipping.country")),
                "lines", Map.of("path", "line_items", "fields", Map.of("sku", "sku", "name", "name", "quantity", "quantity", "unitPrice", "price")))),

        new GenericConnectionService.Template("magento2-orders", "Adobe Commerce (Magento 2): import orders", "IMPORT", "orders", "MAGENTO",
            "REST API with an integration access token (secret). Configurable products list a parent and a child line; map simple products only, or check the preview. " + UNTESTED,
            Map.of(
                "request", Map.of("baseUrl", "https://your-store.example.com", "path", "/rest/V1/orders", "query", Map.of(
                    "searchCriteria[filter_groups][0][filters][0][field]", "updated_at",
                    "searchCriteria[filter_groups][0][filters][0][condition_type]", "gt",
                    "searchCriteria[filter_groups][0][filters][0][value]", "2000-01-01 00:00:00")),
                "auth", Map.of("type", "BEARER"),
                "pagination", Map.of("type", "PAGE", "pageParam", "searchCriteria[currentPage]", "sizeParam", "searchCriteria[pageSize]", "size", 50, "maxPages", 40),
                "incremental", Map.of("param", "searchCriteria[filter_groups][0][filters][0][value]", "format", "ISO"),
                "recordsPath", "items",
                "fields", Map.ofEntries(
                    entry("orderId", "entity_id"), entry("orderNumber", "increment_id"), entry("status", "status"), entry("currency", "order_currency_code"),
                    entry("subtotal", "subtotal"), entry("total", "grand_total"), entry("tax", "tax_amount"), entry("shipping", "shipping_amount"),
                    entry("email", "customer_email"), entry("customerName", "{customer_firstname} {customer_lastname}"),
                    entry("shipLine1", "extension_attributes.shipping_assignments[0].shipping.address.street[0]"),
                    entry("shipCity", "extension_attributes.shipping_assignments[0].shipping.address.city"),
                    entry("shipState", "extension_attributes.shipping_assignments[0].shipping.address.region"),
                    entry("shipPostalCode", "extension_attributes.shipping_assignments[0].shipping.address.postcode"),
                    entry("shipCountry", "extension_attributes.shipping_assignments[0].shipping.address.country_id")),
                "lines", Map.of("path", "items", "fields", Map.of("sku", "sku", "name", "name", "quantity", "qty_ordered|int", "unitPrice", "price")))),

        new GenericConnectionService.Template("squarespace-orders", "Squarespace: import orders", "IMPORT", "orders", "SQUARESPACE",
            "Commerce Orders API with an API key (secret). Squarespace needs both modifiedAfter and modifiedBefore, so this template reads by cursor without an incremental filter. " + UNTESTED,
            Map.of(
                "request", Map.of("baseUrl", "https://api.squarespace.com", "path", "/1.0/commerce/orders", "headers", Map.of("User-Agent", "Nexus-OMS")),
                "auth", Map.of("type", "BEARER"),
                "pagination", Map.of("type", "CURSOR", "cursorParam", "cursor", "cursorPath", "pagination.nextPageCursor", "maxPages", 40),
                "recordsPath", "result",
                "fields", Map.ofEntries(
                    entry("orderId", "id"), entry("orderNumber", "orderNumber"), entry("status", "fulfillmentStatus"),
                    entry("currency", "grandTotal.currency"), entry("total", "grandTotal.value"), entry("subtotal", "subtotal.value"),
                    entry("tax", "taxTotal.value"), entry("shipping", "shippingTotal.value"), entry("email", "customerEmail"),
                    entry("customerName", "{shippingAddress.firstName} {shippingAddress.lastName}"), entry("phone", "shippingAddress.phone"),
                    entry("shipLine1", "shippingAddress.address1"), entry("shipLine2", "shippingAddress.address2"), entry("shipCity", "shippingAddress.city"),
                    entry("shipState", "shippingAddress.state"), entry("shipPostalCode", "shippingAddress.postalCode"), entry("shipCountry", "shippingAddress.countryCode")),
                "lines", Map.of("path", "lineItems", "fields", Map.of("sku", "sku", "name", "productName", "quantity", "quantity", "unitPrice", "unitPricePaid.value")))),

        new GenericConnectionService.Template("custom-orders", "Any REST API: import orders", "IMPORT", "orders", "CUSTOM",
            "A blank order import for any system with a JSON API, including B2B portals and EDI middleware. Map orderType to B2B and requestedShipDate for wholesale orders.",
            Map.of(
                "request", Map.of("baseUrl", "https://api.example.com", "path", "/orders"),
                "auth", Map.of("type", "BEARER"),
                "pagination", Map.of("type", "PAGE", "pageParam", "page", "sizeParam", "limit", "size", 100),
                "incremental", Map.of("param", "updated_since", "format", "ISO"),
                "recordsPath", "data",
                "fields", Map.of("orderId", "id", "orderNumber", "number", "email", "customer.email", "customerName", "customer.name",
                    "currency", "currency|default:USD", "orderType", "=B2C", "requestedShipDate", "ship_by"),
                "lines", Map.of("path", "lines", "fields", Map.of("sku", "sku", "quantity", "quantity", "unitPrice", "price")))),

        new GenericConnectionService.Template("custom-stock", "ERP, POS or 3PL: import stock counts", "IMPORT", "inventory", null,
            "Sets on-hand per SKU and location through the ledger. Map the other system's location codes to Nexus locations under 'locations'. Run as a dry run first.",
            Map.of(
                "request", Map.of("baseUrl", "https://erp.example.com", "path", "/api/stock"),
                "auth", Map.of("type", "HEADER", "headerName", "X-API-Key"),
                "pagination", Map.of("type", "OFFSET", "offsetParam", "offset", "sizeParam", "limit", "size", 500),
                "recordsPath", "items",
                "fields", Map.of("sku", "item_code|trim", "quantity", "on_hand|int", "location", "warehouse"),
                "locations", Map.of("WH-01", "paste a Nexus location id"))),

        new GenericConnectionService.Template("shipments-webhook", "Any system: send shipments and tracking", "EXPORT", "shipments", null,
            "Posts each new shipment (carrier, tracking number, order number) to another system, in batches of 25.",
            Map.of(
                "request", Map.of("baseUrl", "https://api.example.com", "path", "/fulfilments", "method", "POST"),
                "auth", Map.of("type", "BEARER"),
                "delivery", Map.of("mode", "HTTP", "batchSize", 25, "wrapper", "shipments"),
                "fields", Map.of("order_number", "orderNumber", "carrier", "carrier", "tracking_number", "trackingNumber", "shipped_at", "shippedAt"))),

        new GenericConnectionService.Template("orders-file", "Orders export to a CSV file", "EXPORT", "orders", null,
            "A CSV of orders changed since the last run, one row per line, for systems that take files.",
            Map.of(
                "delivery", Map.of("mode", "FILE"),
                "fields", Map.of("order_number", "orderNumber", "channel", "channel", "status", "status", "type", "orderType", "ship_by", "requestedShipDate", "total", "total"),
                "lines", Map.of("path", "lines", "fields", Map.of("sku", "sku", "qty", "quantity"))))
    );
}
