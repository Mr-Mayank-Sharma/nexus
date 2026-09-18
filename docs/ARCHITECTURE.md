# Nexus OMS — Platform Architecture Guide

This guide documents the **Nexus OMS & Supply Chain Platform** architecture —
the services, data flows, integrations, and how everything fits together.

---

## Table of Contents

1. [Platform Overview](#1-platform-overview)
2. [Service Architecture](#2-service-architecture)
3. [Data Model](#3-data-model)
4. [Integrations](#4-integrations)
5. [AI / ML Services](#5-ai--ml-services)
6. [Event Streaming (Kafka)](#6-event-streaming-kafka)
7. [Security Model](#7-security-model)
8. [Frontend Architecture](#8-frontend-architecture)

---

## 1. Platform Overview

Nexus is a **full-stack Order Management System (OMS)** and supply chain
platform. It covers the entire order lifecycle:

```
Order Creation → Order Approval → Allocation → Picking → Packing →
Shipping → Delivery → Returns → Refunds → Analytics
```

Plus supporting functions:
- **Inventory management** (multi-warehouse, zones, bins, cycle counts)
- **Procurement** (suppliers, purchase orders, ASNs, receiving)
- **Carrier management** (rate cards, rate shopping, labels, manifests)
- **Integrations** (Shopify, BigCommerce, Amazon, Walmart, eBay, EDI)
- **AI / ML** (order routing, demand forecasting, inventory optimization,
  box optimization, pick-pack-ship)
- **Analytics & reporting** (dashboards, billing, invoicing)
- **B2B / client portal** (B2B ordering, client self-service)

---

## 2. Service Architecture

### 2.1 Backend (Spring Boot 3, Java 17)

The core OMS API. Serves `/api/v1/**`.

**Modules (controllers):**

| Module | Endpoints |
|--------|-----------|
| Auth & RBAC | `/auth`, `/rbac`, `/users` |
| Orders | `/orders`, `/order-approvals`, `/parked-orders`, `/order-routing` |
| Inventory | `/inventory`, `/inventory-receipts`, `/cycle-counts`, `/replenishment` |
| Fulfillment | `/picking`, `/packing`, `/shipments`, `/manifests`, `/labels` |
| Returns | `/returns`, `/return-finance` |
| Carriers | `/carriers`, `/rate-cards`, `/rate-shopping` |
| Integrations | `/integrations`, `/connectors`, `/shopify`, `/bigcommerce` |
| AI | `/ai` (routing, forecasting, packing) |
| Analytics | `/analytics`, `/dashboard`, `/reports` |
| Finance | `/billing`, `/invoicing`, `/freight-audit` |
| Procurement | `/procurement`, `/asn`, `/suppliers` |
| Automation | `/automation`, `/edi-automation`, `/email-order-parsing` |
| B2B | `/client-portal`, `/b2b` |
| BOPIS | `/bopis`, `/endless-aisle` |
| Labor | `/labor`, `/picker` |
| Documents | `/documents` |
| Notifications | `/notifications` |
| Health | `/actuator/health` |

### 2.2 Frontend (React + Vite + TypeScript)

Single-page application served by nginx. Proxies `/api/` to the backend.

**Pages (60+):**
- Dashboard, Orders, Find Order, Create Order, Order Detail
- Inventory, Inventory Enhanced, Inventory Receiving, Cycle Count
- Fulfillment, Picking, Packing, Shipments, Manifests, Label Printing
- Returns, Return Finance
- Carriers, Rate Cards, Rate Shopping, Freight Audit
- Integrations (Hub, Marketplace, Stores), Shopify, BigCommerce, Amazon, eBay
- AI (Routing, Forecasting, Packing, Experiments, Briefing, Audit Trail)
- Analytics, Reports, Billing, Invoicing
- B2B Portal, Client Portal, BOPIS, Endless Aisle
- Automation, EDI, Email Order Parsing
- Labor Management, Notifications, Documents
- Settings, RBAC, Audit, Import/Export

### 2.3 AI Ops (Python, FastAPI, :5000)

Operational ML models:

| Model | Purpose |
|-------|---------|
| `model1_order_routing` | Route orders to optimal warehouse/zone |
| `model2_shipping_aggregator` | Aggregate shipments for cost savings |
| `model3_box_optimizer` | Optimize box selection for shipments |
| `model4_pick_pack_ship` | Optimize pick-pack-ship workflow |

Endpoints: `/api/health`, `/api/warmup`, `/api/predict/{route,carrier,box,pick-pack}`

### 2.4 AI Intel (Python, FastAPI, :5001)

Intelligence ML models:

| Model | Purpose |
|-------|---------|
| `model5_demand_forecasting` | Forecast demand per SKU/warehouse |
| `model6_inventory_optimization` | Optimize inventory levels |

Endpoints: `/api/health-extended`, `/api/warmup`, `/api/predict/{demand,inventory}`

### 2.5 Data stores

| Store | Purpose |
|-------|---------|
| **PostgreSQL** (pgvector) | Primary DB — all business data |
| **Redis** | Cache, sessions, rate limiting |
| **Kafka** | Event streaming — order lifecycle events |

### 2.6 Monitoring

| Service | Purpose |
|---------|---------|
| **Prometheus** | Metrics collection |
| **Grafana** | Dashboards + alerting |

---

## 3. Data Model

Core entities (PostgreSQL):

```
┌────────────┐     ┌────────────┐     ┌────────────┐
│  CUSTOMER  │────▶│   ORDER    │────▶│  ORDER ITEM│
└────────────┘     └────────────┘     └────────────┘
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ SHIPMENT │ │  RETURN  │ │ PAYMENT  │
        └──────────┘ └──────────┘ └──────────┘

┌────────────┐     ┌────────────┐     ┌────────────┐
│  PRODUCT   │────▶│  INVENTORY │────▶│ WAREHOUSE  │
└────────────┘     └────────────┘     └────────────┘
                          │
                          ▼
                    ┌────────────┐
                    │  ZONE/BIN  │
                    └────────────┘

┌────────────┐     ┌────────────┐     ┌────────────┐
│  SUPPLIER  │────▶│  PURCHASE  │────▶│    ASN     │
└────────────┘     │   ORDER    │     └────────────┘
                    └────────────┘
```

---

## 4. Integrations

### 4.1 Sales channels

| Channel | Direction | Data |
|---------|-----------|------|
| **Shopify** | Bidirectional | Orders in, fulfillment out, product/inventory sync |
| **BigCommerce** | Bidirectional | Orders in, fulfillment out, product/inventory sync |
| **Amazon** | Bidirectional | Orders in, fulfillment out, inventory sync |
| **Walmart** | Bidirectional | Orders in, fulfillment out |
| **eBay** | Bidirectional | Orders in, fulfillment out |

### 4.2 Carriers

| Carrier | Capability |
|---------|------------|
| **FedEx** | Rate shopping, labels, tracking |
| **UPS** | Rate shopping, labels, tracking |
| **USPS** | Rate shopping, labels, tracking |
| **DHL** | Rate shopping, labels, tracking |

### 4.3 EDI

- X12 EDI import/export (850 PO, 856 ASN, 940/945 warehouse, 810 invoice)
- EDI automation workflows

### 4.4 Email order parsing

- Parse orders from email (attachments, text)

### 4.5 Generic import engine

- CSV, JSON, XML, EDI, XLSX import for all entity types

---

## 5. AI / ML Services

### 5.1 AI Ops (operational)

| Model | Input | Output |
|-------|-------|--------|
| Order routing | Order, warehouse, capacity | Optimal warehouse/zone |
| Shipping aggregator | Shipments, carriers | Aggregated shipment plan |
| Box optimizer | Items, dimensions | Optimal box |
| Pick-pack-ship | Orders, bins | Optimized workflow |

### 5.2 AI Intel (intelligence)

| Model | Input | Output |
|-------|-------|--------|
| Demand forecasting | Historical sales, seasonality | Forecast per SKU/warehouse |
| Inventory optimization | Demand, lead time, cost | Optimal stock levels |

### 5.3 AI enablement

- `NEXUS_AI_ENABLED=true` enables AI features.
- `OPENAI_API_KEY` + `OPENAI_MODEL` for LLM features (briefings, audit trail).
- Models register via `register_model.py`.

---

## 6. Event Streaming (Kafka)

Kafka streams order lifecycle events:

```
Order Created → Order Approved → Order Allocated → Order Picked →
Order Packed → Order Shipped → Order Delivered → Order Returned
```

Each event is published to a topic; consumers react (notifications, analytics,
AI triggers, integrations).

**Topics (typical):**
- `order.created`
- `order.approved`
- `order.allocated`
- `order.picked`
- `order.packed`
- `order.shipped`
- `order.delivered`
- `order.returned`
- `inventory.updated`
- `shipment.created`

---

## 7. Security Model

### 7.1 Authentication

- JWT-based auth (`/auth/login`, `/auth/register`).
- `JWT_SECRET` from `.env` (>= 32 chars).
- Token expiry: `NEXUS_JWT_EXPIRATION_MS` (default 15 min).

### 7.2 Authorization (RBAC)

Roles: **Admin, Ops Manager, Warehouse Staff, Finance, Viewer**.

Permissions enforced via `PermissionAuthorizationFilter` + `SecurityConfig`.

### 7.3 Endpoint protection

- `/actuator/health` — public (for healthchecks).
- `/actuator/**` — ADMIN only (metrics, env, etc.).
- `/api/v1/**` — authenticated (JWT).
- Admin endpoints — ADMIN role.

### 7.4 SSO / OAuth (optional)

- Google, Microsoft, Okta OAuth configured via `.env` (`GOOGLE_CLIENT_ID`,
  `MICROSOFT_CLIENT_ID`, `OKTA_CLIENT_ID`, etc.).

---

## 8. Frontend Architecture

### 8.1 Stack

- **React 18** + **TypeScript**
- **Vite** build tool
- **nginx** static server + reverse proxy
- **React Router** for SPA routing

### 8.2 Routing

```
/                    → Dashboard
/orders              → Orders list
/orders/:id          → Order detail
/inventory           → Inventory
/fulfillment         → Fulfillment
/shipments           → Shipments
/returns             → Returns
/carriers            → Carriers
/integrations        → Integration Hub
/ai                  → AI platform
/analytics           → Analytics
/b2b                 → B2B portal
/settings            → Settings
```

### 8.3 API access

The frontend calls the backend via `/api/v1/**`, proxied by nginx:

```nginx
location /api/ {
    proxy_pass http://backend:8080;
}
```

---

*See also: [Deployment Guide](DEPLOYMENT.md) · [Onboarding Guide](ONBOARDING.md) ·
[Sizing Guide](SIZING.md) · [Environment Guide](ENVIRONMENTS.md) ·
[Operations Guide](OPERATIONS.md).*