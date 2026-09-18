# Nexus OMS — Merchant Onboarding Guide

This guide covers how to onboard **both international and regional merchants**
onto the Nexus platform. It covers the full lifecycle: discovery → setup →
integration → go-live → ongoing operations.

---

## Table of Contents

1. [Onboarding Overview](#1-onboarding-overview)
2. [Merchant Categories](#2-merchant-categories)
3. [Regional Merchant Onboarding](#3-regional-merchant-onboarding)
4. [International Merchant Onboarding](#4-international-merchant-onboarding)
5. [Onboarding Checklist](#5-onboarding-checklist)
6. [Integration Setup](#6-integration-setup)
7. [Data Migration](#7-data-migration)
8. [Go-Live & Cutover](#8-go-live--cutover)
9. [Post-Onboarding Support](#9-post-onboarding-support)

---

## 1. Onboarding Overview

Onboarding a merchant onto Nexus involves:

1. **Discovery** — understand the merchant's business, volume, channels, carriers.
2. **Provisioning** — create the merchant's tenant, users, roles, permissions.
3. **Integration** — connect sales channels (Shopify, BigCommerce, Amazon, etc.)
   and carriers (FedEx, UPS, USPS, DHL).
4. **Data migration** — import existing products, orders, inventory, customers.
5. **Configuration** — set up warehouses, zones, bins, routing rules, rate cards.
6. **Testing** — UAT on the staging instance.
7. **Go-live** — cut over to production.
8. **Support** — monitor, tune, and expand.

---

## 2. Merchant Categories

Nexus serves two broad merchant categories, each with different needs:

### 2.1 Regional merchants

- Operate within **one country/region** (e.g., US only, or India only).
- **Channels:** Shopify, BigCommerce, Amazon US, Walmart, eBay.
- **Carriers:** USPS, FedEx, UPS (US); or local carriers (regional).
- **Currency:** single currency.
- **Tax:** single tax regime.
- **Language:** single language.
- **Volume:** typically 100 – 50,000 orders/day.

### 2.2 International merchants

- Operate across **multiple countries/regions** (cross-border).
- **Channels:** Shopify (multi-store), Amazon (multiple marketplaces),
  BigCommerce, plus regional marketplaces.
- **Carriers:** multiple international carriers + regional last-mile.
- **Currency:** multi-currency (USD, EUR, GBP, INR, etc.).
- **Tax:** multi-jurisdiction (VAT, GST, sales tax).
- **Language:** multi-language storefronts.
- **Volume:** typically 1,000 – 500,000+ orders/day.

---

## 3. Regional Merchant Onboarding

### 3.1 Discovery call

Gather:
- Business type (D2C, B2B, 3PL, marketplace seller)
- Sales channels + store URLs
- Order volume (daily/monthly)
- Product count + SKU structure
- Current carriers + rate contracts
- Existing systems (ERP, WMS, accounting)
- Pain points

### 3.2 Provision the tenant

```bash
# 1. Deploy an instance (see DEPLOYMENT.md) — or use the shared platform
# 2. Create the merchant's admin user
#    (via the frontend Users page or the /auth/register API)
# 3. Assign roles & permissions (RBAC)
#    - Admin, Ops Manager, Warehouse Staff, Finance, Viewer
```

### 3.3 Configure the warehouse

1. Create the warehouse (name, address, timezone).
2. Create zones (receiving, picking, packing, shipping, returns).
3. Create bins (A-01-01, B-02-03, etc.).
4. Set up picking/packing workflows.

### 3.4 Connect channels & carriers

| Channel | Setup |
|---------|-------|
| Shopify | OAuth app install, store URL, API token |
| BigCommerce | API token + store hash |
| Amazon | SP-API credentials |
| Walmart | Seller API credentials |
| eBay | OAuth token |

| Carrier | Setup |
|---------|-------|
| USPS | User ID + password |
| FedEx | Client ID + secret + account |
| UPS | Client ID + secret + account |
| DHL | API key + secret + account |

### 3.5 Import data

Use the **Generic Import Engine** (`/api/v1/import/{entityType}`):

```bash
# Products
curl -X POST http://localhost:8080/api/v1/import/products \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@products.csv"

# Orders
curl -X POST http://localhost:8080/api/v1/import/orders \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@orders.csv"

# Inventory, customers, shipments, returns, suppliers, purchase-orders,
# invoices, warehouses — same pattern
```

Supported formats: **CSV, JSON, XML, EDI (X12), XLSX**.

### 3.6 Configure business rules

- Routing rules (which warehouse/zone fulfills which order).
- Rate cards (carrier pricing).
- Replenishment rules (min/max stock).
- Fulfillment limits.
- Approval workflows.

### 3.7 UAT & go-live

1. Test on the UAT instance.
2. Verify order lifecycle: create → allocate → pick → pack → ship → deliver.
3. Verify carrier rate shopping + label printing.
4. Verify returns + refunds.
5. Cut over to production.

---

## 4. International Merchant Onboarding

International onboarding adds **multi-tenant, multi-currency, multi-tax,
multi-carrier** complexity on top of the regional flow.

### 4.1 Additional discovery

Beyond the regional checklist, gather:
- Countries/marketplaces served
- Currencies + FX handling
- Tax regimes (VAT, GST, sales tax) per country
- International carriers + regional last-mile partners
- Cross-border duties/customs handling
- Multi-language storefronts
- Localization (address formats, phone, units)

### 4.2 Multi-warehouse setup

International merchants typically need **multiple warehouses**:

| Warehouse | Region | Purpose |
|-----------|--------|---------|
| US-East | USA | Domestic US fulfillment |
| EU-West | Germany | EU fulfillment (avoid cross-border duties) |
| APAC | Singapore | Asia-Pacific fulfillment |
| UK | UK | UK domestic |

Each warehouse gets its own zones, bins, staff, and routing rules.

### 4.3 Multi-currency & multi-tax

- Configure per-warehouse currency.
- Configure per-country tax rates (VAT, GST, sales tax).
- Set up FX handling for cross-border orders.
- Configure duty/customs handling for international shipments.

### 4.4 Multi-carrier routing

International merchants need **carrier selection by region**:

| Region | Primary carrier | Backup |
|--------|-----------------|--------|
| US domestic | FedEx / UPS | USPS |
| US → EU | FedEx International | DHL Express |
| EU domestic | DHL / local | UPS |
| APAC | DHL / local | FedEx |

Configure rate cards per carrier + region, and let Nexus **rate-shop** at
shipment time.

### 4.5 Multi-channel integration

International merchants often run **multiple storefronts**:

- Shopify US store
- Shopify EU store (multi-currency)
- Amazon US, Amazon UK, Amazon DE
- BigCommerce (B2B)
- Regional marketplaces (Flipkart, Mercado Libre, etc.)

Each channel connects via its own integration; Nexus aggregates orders into a
single view.

### 4.6 Data migration (international)

Import per-country data:
- Products with per-country pricing + tax.
- Inventory per warehouse.
- Customers with international addresses.
- Orders with multi-currency amounts.
- Historical shipments for reporting.

### 4.7 UAT & go-live (international)

1. Test cross-border order flow end-to-end.
2. Verify multi-currency pricing + FX.
3. Verify per-country tax calculation.
4. Verify international carrier labels + customs docs.
5. Verify multi-warehouse routing.
6. Cut over per-region (phased rollout recommended).

---

## 5. Onboarding Checklist

### Pre-onboarding
- [ ] Discovery call completed
- [ ] Business type + channels identified
- [ ] Volume + product count estimated
- [ ] Carriers + rate contracts confirmed
- [ ] Existing systems identified

### Provisioning
- [ ] Instance deployed (or tenant created on shared platform)
- [ ] Admin user created
- [ ] Roles + permissions assigned
- [ ] Warehouse(s) + zones + bins created
- [ ] Staff users created

### Integration
- [ ] Sales channels connected
- [ ] Carriers connected
- [ ] Rate cards configured
- [ ] Webhooks/events verified

### Data
- [ ] Products imported
- [ ] Inventory imported
- [ ] Customers imported
- [ ] Orders imported
- [ ] Historical data migrated

### Configuration
- [ ] Routing rules set
- [ ] Replenishment rules set
- [ ] Fulfillment limits set
- [ ] Approval workflows set
- [ ] Tax/currency configured (international)

### Testing
- [ ] Order lifecycle tested (create → ship → deliver)
- [ ] Carrier rate shopping tested
- [ ] Label printing tested
- [ ] Returns + refunds tested
- [ ] UAT sign-off obtained

### Go-live
- [ ] Production instance ready
- [ ] Data migrated to production
- [ ] Cutover executed
- [ ] Monitoring active
- [ ] Support contact established

---

## 6. Integration Setup

### 6.1 Shopify

1. Create a Shopify app (custom or public).
2. Install on the merchant's store.
3. Get the API token + store domain.
4. Configure in Nexus: **Integration Hub → Shopify → Add store**.
5. Enable order import, product sync, inventory sync, fulfillment push.

### 6.2 BigCommerce

1. Create a BigCommerce API account.
2. Get the API token + store hash.
3. Configure in Nexus: **Integration Hub → BigCommerce → Add store**.
4. Enable order import, product sync, inventory sync, shipment push.

### 6.3 Amazon

1. Register as an Amazon SP-API developer.
2. Get SP-API credentials (client ID, secret, refresh token).
3. Configure in Nexus: **Integration Hub → Amazon → Add marketplace**.
4. Enable order import, inventory sync, fulfillment push.

### 6.4 Carriers

| Carrier | Credentials | Nexus config |
|---------|-------------|--------------|
| FedEx | Client ID, secret, account | Carrier page → FedEx |
| UPS | Client ID, secret, account | Carrier page → UPS |
| USPS | User ID, password | Carrier page → USPS |
| DHL | API key, secret, account | Carrier page → DHL |

---

## 7. Data Migration

### 7.1 Use the Generic Import Engine

```bash
# Auth
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YOUR_PASSWORD"}' | jq -r .token)

# Import products
curl -X POST http://localhost:8080/api/v1/import/products \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@products.csv"

# Import orders
curl -X POST http://localhost:8080/api/v1/import/orders \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@orders.csv"
```

### 7.2 Supported entity types

`products`, `orders`, `inventory`, `customers`, `shipments`, `returns`,
`suppliers`, `purchase-orders`, `invoices`, `warehouses`

### 7.3 Supported formats

`csv`, `json`, `xml`, `edi` (X12), `xlsx`

### 7.4 Templates

- `frontend/product-import-template.csv` — 14 products, 21 fields
- `frontend/order-import-template.csv` — 14 orders, 12 fields

---

## 8. Go-Live & Cutover

### 8.1 Phased rollout (recommended for international)

1. **Pilot** — onboard one region/warehouse first.
2. **Validate** — run in parallel with the old system for 1–2 weeks.
3. **Expand** — add more regions/warehouses.
4. **Full cutover** — switch all traffic to Nexus.

### 8.2 Cutover steps

1. Freeze data in the old system.
2. Final data migration to Nexus.
3. Point channels/carriers to Nexus.
4. Verify order flow end-to-end.
5. Announce go-live.

---

## 9. Post-Onboarding Support

### 9.1 First 30 days

- Daily healthcheck (`bash scripts/healthcheck.sh`).
- Monitor order volume vs. capacity.
- Watch for integration failures.
- Tune routing rules + rate cards.
- Collect feedback from warehouse staff.

### 9.2 Ongoing

- Weekly: review dashboards (Grafana).
- Monthly: capacity review, rate card renegotiation.
- Quarterly: feature expansion, new channels/carriers.

### 9.3 Support escalation

| Issue | Contact |
|-------|---------|
| Platform outage | Ops team (on-call) |
| Integration failure | Integration team |
| Data issue | Data team |
| Feature request | Product team |

---

*See also: [Deployment Guide](DEPLOYMENT.md) · [Sizing Guide](SIZING.md) ·
[Environment Guide](ENVIRONMENTS.md) · [Operations Guide](OPERATIONS.md).*