# Nexus Gateway

One API every Nexus installation calls for carrier rates, labels, voids, return labels, address checks and tracking,
and for customer emails. Carrier and email credentials live only here, per client and per store; a client's OMS keeps
just the gateway address and its API key (doc 26, W11; doc 24, section 5).

## Run it

```bash
export NEXUS_GATEWAY_MASTER_KEY=...   # 32+ characters; encrypts stored credentials. Keep it safe: lose it and the secrets are unreadable.
export NEXUS_GATEWAY_ADMIN_TOKEN=...  # 16+ characters; for the /admin API
export NEXUS_GATEWAY_DB_URL=jdbc:postgresql://host:5432/nexus_gateway   # optional; a local H2 file otherwise
export NEXUS_GATEWAY_DB_USERNAME=... NEXUS_GATEWAY_DB_PASSWORD=...
mvn package && java -jar target/nexus-gateway-0.1.0.jar      # port 8097 (PORT to change)
```

The gateway refuses to start without the master key.

## Set up a client (operator, header `X-Admin-Token`)

| Call | What it does |
|---|---|
| `POST /admin/clients {"name": "..."}` | A client and its API key (shown once; only its hash is kept) |
| `POST /admin/clients/{id}/rotate-key` | A new key; the old one stops at once |
| `PUT /admin/clients/{id}/accounts` | A carrier or email account: `storeId` (null for every store), `provider`, `environment` (SANDBOX or PRODUCTION), `settings`, `secrets` (encrypted, never returned) |
| `GET /admin/clients/{id}/accounts` | Accounts with secret names, never values |
| `PUT /admin/clients/{id}/templates` | An email template: `templateId`, `subject`, `bodyHtml` with `{{name}}` placeholders, per store or for every store |
| `GET /admin/requests`, `GET /admin/health?hours=24` | Every call with outcome and time; calls, errors and timings per operation and provider |

Providers and what they need:

| Provider | Settings | Secrets |
|---|---|---|
| `FEDEX` | `accountNumber` | `clientId`, `clientSecret` (FedEx Developer Portal project) |
| `UPS` | `accountNumber` | `clientId`, `clientSecret` (UPS Developer Portal app) |
| `NEXUS_TEST` | none | none: a test carrier, so an installation can run end to end before its carrier accounts exist |
| `SMTP` | `host`, `port` (587), `username`, `from`, `starttls` | `password` (works with SES, SendGrid, Postmark, Mailtrap) |
| `TEST_EMAIL` | none | none: keeps messages in memory (`GET /admin/clients/{id}/test-outbox`) |

## The client API (header `X-Nexus-Gateway-Key`)

| Call | What it does |
|---|---|
| `POST /v1/rates` | Rates from one carrier or every carrier the client has, cheapest first |
| `POST /v1/labels` | Labels (`isReturn: true` for a return label); each kept for reprinting |
| `GET /v1/labels/{labelId}` | A label again |
| `POST /v1/labels/void` | Void by tracking number |
| `POST /v1/addresses/validate` | Valid, ambiguous, the carrier's suggestion, residential or not |
| `GET /v1/tracking/{carrier}/{number}` | Status (LABEL_CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION) and events |
| `POST /v1/email/send` | A template rendered with data (values HTML-escaped) and sent |
| `GET /v1/requests` | The client's own call log |

Errors are `{"error": "..."}`: 400 for a bad request, 409 when an account or template is not set up, 422 when the
carrier refused (with its reason), 502 when the carrier failed.

## From the OMS

`PUT /core/gateway/stores/{productStoreId}` with `url`, `apiKey` (stored encrypted) and `defaultCarrier`;
`POST /core/gateway/shipments/{shipmentId}/label`; `POST /core/gateway/stores/{productStoreId}/test-email`.

## Adding a carrier

Implement `CarrierAdapter` (rates, label, void, validate, track) as a Spring bean and add its code to
`Accounts.PROVIDERS`. Every client can then use it with its own account.

## Status

The FedEx and UPS adapters follow the carriers' published REST APIs and are checked against a local server that
answers like them (`CarrierAdaptersTest`); they still need a run against the real sandboxes before production. The
test carrier and test email have been run end to end from the OMS.
