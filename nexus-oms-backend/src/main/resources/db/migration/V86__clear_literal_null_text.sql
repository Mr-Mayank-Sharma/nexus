-- Store imports used to save a JSON null as the text 'null' (e.g. Shopify orders with no financial_status,
-- addresses with no phone). The importers now treat JSON null as missing; this clears what was already stored.
UPDATE nx_orders SET payment_status = NULL WHERE payment_status = 'null';
UPDATE nx_addresses SET phone = NULL WHERE phone = 'null';
