# NexusShip — Complete Master Product Blueprint

> **Vision**: The operating system for global supply chain orchestration
> **Version**: 2.0 | **Date**: June 2026 | **Status**: Reference Architecture

---

## Table of Contents

1. [Product Vision & Strategy](#1-product-vision--strategy)
2. [Market & Competitive Analysis](#2-market--competitive-analysis)
3. [Platform Architecture](#3-platform-architecture)
4. [Feature Inventory & Capability Matrix](#4-feature-inventory--capability-matrix)
5. [Business Process Engineering](#5-business-process-engineering)
6. [Integration Architecture](#6-integration-architecture)
7. [Data Architecture & Canonical Model](#7-data-architecture--canonical-model)
8. [ML/AI Engine](#8-mlai-engine)
9. [UX & UI Architecture](#9-ux--ui-architecture)
10. [Implementation Roadmap](#10-implementation-roadmap)
11. [Go-To-Market Strategy](#11-go-to-market-strategy)
12. [Financial Model](#12-financial-model)
13. [Security & Compliance](#13-security--compliance)
14. [Risk Register](#14-risk-register)

---

## 1. Product Vision & Strategy

### 1.1 Vision Statement

NexusShip is a universal, AI-powered supply chain orchestration platform that integrates natively with every major ecosystem — from eCommerce storefronts (Shopify, BigCommerce, Magento) to enterprise ERPs (SAP S/4HANA, Oracle Fusion, NetSuite, Microsoft D365), WMS platforms (Manhattan, Blue Yonder, SAP EWM), and 150+ carriers globally.

Unlike point solutions that address shipping, inventory, or orders in isolation, NexusShip becomes the single command center through which all supply chain operations flow — absorbing native features of each connected platform and surfacing them through a unified interface with intelligent orchestration on top.

### 1.2 The Core Problem

Brands today operate across a patchwork of disconnected systems. A mid-market retailer typically juggles:
- 1 eCommerce storefront (Shopify, BigCommerce, or Magento)
- 1-2 ERP systems (NetSuite, SAP, Oracle, or D365)
- 1 WMS (Manhattan, Blue Yonder, ShipBob, or custom)
- 6-12 carrier accounts (UPS, FedEx, DHL, USPS, regional carriers)
- Multiple fulfillment nodes: warehouses, stores, 3PL partners, dropshippers

**Key pain points:**
- **No unified order visibility** — CS agents switch between 5+ systems to answer "where is my order?"
- **Overpaying on shipping by 18-34%** — manual carrier selection misses optimal rate/transit combinations
- **Inventory fragmentation** — 15-20% of orders become split shipments unnecessarily
- **ERP integration cost** — bespoke middleware costs $200K-$500K per ERP pair
- **Cross-border complexity** — 195+ jurisdictions with compliance errors costing $2,300/hold on average
- **Returns inefficiency** — 30% of return shipments use suboptimal carriers at 40%+ premium

### 1.3 Solution Overview

NexusShip solves these problems through six interconnected platform layers:

| Layer | Description | Key KPIs |
|-------|-------------|----------|
| **Order Intelligence Engine** | Multi-channel order unification, DOM, BOPIS/SFS/S2S orchestration | 99.99% order accuracy, <30s routing decision |
| **Carrier Intelligence Engine** | Real-time rate aggregation, ML-driven selection across 150+ carriers in 80 countries | 18-34% cost reduction, <120ms p99 rate response |
| **Inventory Intelligence Engine** | ATP computation, AI positioning, multi-echelon optimization | 99.5% inventory accuracy, 95%+ 2-day coverage |
| **ERP & WMS Connector Hub** | Pre-built certified integrations with canonical data model | 94% 30-day retention post-integration |
| **Compliance & Customs Module** | AI HS code classification, landed cost, restricted party screening | <80% confidence → WCO lookup fallback |
| **Executive Analytics Layer** | Real-time KPIs, carrier scorecards, lane profitability, BI | <1s dashboard load for 1M+ shipment history |

### 1.4 Key Performance Targets

| Metric | Target | Benchmark |
|--------|--------|-----------|
| Rate shopping latency (p99) | <120ms | Competitors: 500-2000ms |
| Multi-carrier coverage | 150+ direct + aggregator fallback | EasyPost: 100+, ShipStation: 50+ |
| Cost savings (mean) | 18-34% vs customer baseline | Industry avg: 8-12% |
| On-time delivery improvement | +8-15 percentage points | — |
| Invoice accuracy rate | 99.5%+ | Industry avg: 92-95% |
| Platform uptime SLA | 99.99% | Enterprise TMS: 99.95% |
| New ERP integration time | 4-6 weeks certified | Custom: 6-12 months |
| ML model retrain cycle | Weekly with automated CI/CD pipeline | — |
| Data isolation compliance | SOC2 Type II, GDPR, CCPA | — |

### 1.5 Market Opportunity

| Segment | TAM (2024) | Growth (CAGR) | Target |
|---------|------------|---------------|--------|
| Global logistics software | $18.9B | 11.9% | Primary |
| Shipping optimization (AI/ML multi-carrier) | $4.1B | 19.3% | Core |
| Order management systems | $3.2B | 14.1% | Adjacent |
| Warehouse management systems | $5.1B | 16.8% | Adjacent |

---

## 2. Market & Competitive Analysis

### 2.1 Competitive Landscape Map

```
                    ENTERPRISE
                        ↑
              Manhattan OMS  │  OTM Cloud
              IBM Sterling   │  Blue Yonder
              SAP EWM        │
                             │
        ──────── NexusShip ────────▶  API-FIRST
                             │
              ShipStation    │  project44
              Shippo         │  Flexport (Freight)
              EasyPost       │
                        ↓
                      SMB / STARTUP
```

NexusShip occupies a unique position: API-first AND enterprise-grade, targeting the underserved mid-market ($10M-$500M revenue) and lower enterprise segments.

### 2.2 Platform Deep-Dive Analysis

#### 2.2.1 Shopify / Shopify Plus

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | Native BOPIS, Ship-From-Store, Curbside Pickup, unified POS+eCommerce inventory, multi-location routing, CDP |
| **Weaknesses** | No DOM at enterprise scale, no ML carrier optimization, BOPIS only at checkout (not PDP), cannot mix BOPIS+shipping in one order, no inventory pool segmentation |
| **NexusShip Advantage** | PDP-level BOPIS availability, split-order engine, enterprise DOM with configurable rule matrix, ML carrier intelligence, channel-specific inventory pools |

**Features we inherit from Shopify:**
- BOPIS (native checkout integration)
- Ship-From-Store routing
- Curbside pickup with SMS notification
- Unified POS + eCommerce inventory
- Real-time inventory sync
- Multi-location intelligent order routing
- Customer data platform (CDP)

#### 2.2.2 BigCommerce

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | Best-in-class B2B pricing engine, open SaaS/headless, multi-storefront, international selling, native ERP connectors, wholesale order form |
| **Weaknesses** | No native WMS/3PL orchestration, basic fulfillment logic, no ML routing |
| **NexusShip Advantage** | Bridge BigCommerce to all WMS/3PL, AI-powered DOM, ML multi-carrier optimization |

**Features we inherit from BigCommerce:**
- Customer group pricing / tiered price lists
- Multi-storefront from single backend
- Multi-currency, multi-language, global tax rules
- BNPL integrations
- Wholesale order form for B2B reorder

#### 2.2.3 Magento (Adobe Commerce)

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | Deep customization (headless PWA, custom checkout), multi-warehouse management, advanced inventory, Adobe Commerce OMS, B2B module |
| **Weaknesses** | Highest TCO, expensive custom development, no ML carrier optimization |
| **NexusShip Advantage** | Same depth without custom code, real-time ML carrier intelligence, AI demand forecasting |

#### 2.2.4 CommerceTools

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | True composable commerce, API-first, cloud-native, microservices, multi-tenant |
| **Weaknesses** | Requires heavy integration effort, no built-in fulfillment/WMS, no carrier optimization |
| **NexusShip Advantage** | Fulfillment/WMS/carrier layer for CommerceTools deployments, pre-built connectors |

#### 2.2.5 Manhattan Associates (OMS + WMS)

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | True enterprise scale, real-time network inventory, same-day BOPIS/SFS, exception management, contact center integration |
| **Weaknesses** | Legacy on-premise options, expensive ($500K+/yr), slow innovation cycle, complex implementation (12-18 months) |
| **NexusShip Advantage** | Mid-market accessible pricing, faster deployment (4-12 weeks), modern API-first architecture, ML-native |

#### 2.2.6 Fluent Commerce

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | Cloud-native OMS, DOM-native architecture, multi-tenant, marketplace support |
| **Weaknesses** | Smaller ecosystem, limited carrier optimization, fewer ERP connectors |
| **NexusShip Advantage** | Broader carrier coverage, deeper ERP integration, ML portfolio |

#### 2.2.7 ShipBob

| Dimension | Assessment |
|-----------|------------|
| **Strengths** | 60+ fulfillment centers, AI inventory distribution, real-time inventory visibility, EDI B2B, DDP international |
| **Weaknesses** | 3PL operator — cannot integrate with SAP/Oracle ERP, no external carrier optimization, no ERP write-back |
| **NexusShip Advantage** | ERP integration first-party, multi-carrier (not just ShipBob network), financial controls, freight accruals |

**Features we inherit from ShipBob:**
- AI-powered Ideal Inventory Distribution
- 2-day delivery coverage calculator
- Real-time inventory visibility across nodes
- Automated restock transfer recommendations

### 2.3 Competitive Feature Matrix

| Feature | Shopify | BigCommerce | Magento | Manhattan | ShipBob | Fluent | NexusShip |
|---------|---------|-------------|---------|-----------|---------|--------|-----------|
| Multi-channel order intake | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅✅ |
| Distributed Order Management | ⚠️ Basic | ❌ | ⚠️ Basic | ✅ | ❌ | ✅ | ✅✅ ML |
| BOPIS | ✅ | ❌ | ⚠️ Custom | ✅ | ❌ | ✅ | ✅✅ |
| Ship-From-Store | ✅ | ❌ | ⚠️ Custom | ✅ | ❌ | ✅ | ✅✅ |
| Curbside Pickup | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| Endless Aisle | ⚠️ | ❌ | ⚠️ Custom | ✅ | ❌ | ✅ | ✅✅ |
| WMS-Lite | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ |
| ML Carrier Optimization | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅✅ |
| Rate Shopping (150+ carriers) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅✅ |
| ERP Integration (SAP/Oracle) | ❌ | ⚠️ Basic | ⚠️ | ✅ | ❌ | ❌ | ✅✅ |
| EDI (850/856/810) | ❌ | ❌ | ⚠️ Extension | ✅ | ✅ | ⚠️ | ✅ |
| Returns Management | ⚠️ Basic | ❌ | ⚠️ | ✅ | ✅ | ✅ | ✅✅ |
| B2B Commerce | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| AI Demand Forecasting | ❌ | ❌ | ❌ | ❌ | ⚠️ Basic | ❌ | ✅✅ |
| ML Invoice Anomaly Detection | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Multi-tenant/Multi-client | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅✅ |
| API-first Architecture | ⚠️ | ✅ | ✅ | ❌ | ⚠️ | ✅ | ✅✅ |
| Headless/Composable | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅✅ |

**Legend:** ✅✅ = Best-in-class | ✅ = Good | ⚠️ = Basic/Partial | ❌ = Not available

### 2.4 Defensible Moats

**Moat 1: ERP Integration Depth** — Building certified integrations with SAP (PartnerEdge), Oracle (OPN), NetSuite (SuiteApp), and Microsoft (AppSource) takes 18-24 months and $3-5M. No shipping-focused competitor has made this investment.

**Moat 2: Proprietary Carrier Performance Data** — Every shipment generates ground-truth carrier performance data (OTD, damage, invoice accuracy). After 12 months, lane-level data creates a virtuous cycle: more data → better ML → higher ROI → more customers → more data.

**Moat 3: Volume Aggregation Network Effects** — Cross-customer volume pooling enables NexusShip to negotiate superior master carrier rates, creating a self-reinforcing network: more customers → better rates → more customers.

**Moat 4: ML Model Quality Gap** — 8-model ML portfolio trained on proprietary multi-carrier, multi-customer, multi-industry data produces accuracy unreachable with synthetic/public data. Invoice anomaly detector alone saves $1.34/shipment average.

---

## 3. Platform Architecture

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CHANNEL LAYER                                │
│  Shopify │ BigCommerce │ Magento │ Amazon │ Walmart │ B2B │ POS     │
└────────────────────────────┬────────────────────────────────────────┘
                             │ Webhooks / REST / EDI
┌────────────────────────────▼────────────────────────────────────────┐
│                      INTEGRATION GATEWAY                            │
│  Webhook Engine │ API Gateway (Kong) │ EDI Adapter │ Connector Pool │
│  Idempotency Layer │ Rate Limiting │ Auth (OAuth 2.0 / JWT)        │
│  CDM Transformer │ Protocol Bridge (REST/SOAP/RFC/EDI)              │
└────────────────────────────┬────────────────────────────────────────┘
                             │ Canonical Data Model
┌────────────────────────────▼────────────────────────────────────────┐
│                     CORE PLATFORM (Microservices)                    │
│                                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  Order   │ │Inventory │ │Fulfillment│ │ Returns  │ │  B2B     │  │
│  │ Service  │ │ Service  │ │ Service   │ │ Service  │ │ Service  │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │Carrier   │ │ Rate     │ │Workflow  │ │  Rule    │ │Notification│  │
│  │ Service  │ │ Engine   │ │ Engine   │ │  Engine  │ │ Service   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
└────────────────────────────┬────────────────────────────────────────┘
                             │ Kafka Event Bus
┌────────────────────────────▼────────────────────────────────────────┐
│                       INTELLIGENCE LAYER                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ Carrier  │ │ Demand   │ │  Dim Wt  │ │ Invoice  │ │ Inventory │  │
│  │Optimizer │ │Forecast  │ │Predictor │ │ Anomaly  │ │ Optimizer │  │
│  │(XGBoost) │ │  (TFT)   │ │  (RF)    │ │  (IF)    │ │  (XGB)    │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                             │
│  │  HS Code │ │  Landed  │ │  Delivery│                             │
│  │ Classifier│ │  Cost    │ │  Success │                             │
│  │ (BERT)   │ │Calc      │ │ Predictor│                             │
│  └──────────┘ └──────────┘ └──────────┘                             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│                       DATA LAYER                                    │
│  PostgreSQL (Citus) │ Redis │ ClickHouse │ S3 Parquet │ Feast (FS) │
│  Kafka (MSK) │ Elasticsearch │ TimescaleDB │ MinIO                  │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Backend (Core)** | Java 21 + Spring Boot 3.x | Core microservices, REST APIs |
| **Backend (Streams)** | Spring Cloud Stream + Kafka | Event-driven architecture |
| **ML/AI** | Python 3.10, Flask, XGBoost, PyTorch (TFT) | ML model serving |
| **Frontend** | React 18 + TypeScript, Tailwind CSS | Admin UI, dashboards |
| **Mobile** | React Native (iOS/Android) | Store staff app, BOPIS |
| **API Gateway** | Kong / Spring Cloud Gateway | Rate limiting, auth, routing |
| **Database** | PostgreSQL 16 + Citus (sharding) | Primary OLTP data |
| **Cache** | Redis 7 (ElastiCache) | Session, rate cache, ATP holds |
| **Analytics DB** | ClickHouse | Time-series, analytics queries |
| **Time-series** | TimescaleDB | Tracking events, rate history |
| **Search** | Elasticsearch | Order search, log analytics |
| **Feature Store** | Feast (feast.dev) | ML feature management |
| **Streaming** | Kafka (MSK), Kafka Connect | Event bus, CDC |
| **Secrets** | HashiCorp Vault + AWS KMS | Credential management |
| **Observability** | Prometheus + Grafana + Jaeger + ELK | Metrics, traces, logs |
| **CI/CD** | GitHub Actions + ArgoCD | Build, deploy (GitOps) |
| **Container** | Docker + Kubernetes (EKS + GKE) | Orchestration |
| **Infrastructure** | Terraform + Crossplane | IaC, multi-cloud |

### 3.3 Microservice Boundaries

| Service | Domain | Responsibility | Data Store |
|---------|--------|----------------|------------|
| `order-ingestion` | Orders | Multi-channel order intake, normalization, idempotency | Postgres |
| `order-orchestrator` | Orders | DOM routing, allocation, lifecycle management | Postgres |
| `inventory-service` | Inventory | ATP, reservations, allocation, sync | Postgres + Redis |
| `fulfillment-service` | Fulfillment | Pick/pack/ship workflows, WMS-lite | Postgres |
| `carrier-service` | Carriers | Carrier account management, config | Postgres + Vault |
| `rate-engine` | Carriers | Rate shopping, normalization, caching | Redis (cache) |
| `label-service` | Carriers | Label generation, void, reprint | S3 |
| `returns-service` | Returns | RMA, disposition, refund, exchange | Postgres |
| `bopis-service` | Stores | BOPIS queue, curbside, SFS | Postgres + Redis |
| `channel-sync` | Integration | Bidirectional sync with eCommerce | Kafka |
| `edi-adapter` | Integration | EDI 850/856/810 parsing, AS2/SFTP | Postgres |
| `workflow-engine` | Platform | Configurable business process orchestration | Postgres |
| `rules-engine` | Platform | Business rules (routing, allocation, pricing) | Postgres + Redis |
| `notification-service` | Platform | Email, SMS, push, webhook delivery | Kafka |
| `compliance-service` | Platform | HS classification, landed cost, screening | Postgres |
| `ml-orchestrator` | ML | Model invocation, fallback, feature assembly | Redis |
| `audit-service` | Platform | Immutable audit log, event sourcing | Postgres + S3 |
| `tenant-service` | Platform | Multi-tenant config, user mgmt, RBAC | Postgres |
| `analytics-aggregator` | Analytics | Rollups, materialized views, reporting | ClickHouse |

### 3.4 Event-Driven Architecture

**Core Event Topics (Kafka):**

| Topic | Partition Key | Schema | Consumers |
|-------|---------------|--------|-----------|
| `order.created` | `customer_id` | OrderCreatedEvent | orchestration, channel-sync, analytics, notification |
| `order.confirmed` | `customer_id` | OrderConfirmedEvent | orchestration, inventory, analytics |
| `order.allocated` | `customer_id` | OrderAllocatedEvent | fulfillment, notification, analytics |
| `order.shipped` | `customer_id` | OrderShippedEvent | channel-sync, notification, analytics, invoice |
| `order.delivered` | `customer_id` | OrderDeliveredEvent | channel-sync, analytics, carrier-scorecard |
| `order.exception` | `customer_id` | OrderExceptionEvent | workflow, notification, analytics |
| `inventory.changed` | `sku_id` | InventoryChangedEvent | ATP recompute, channel-sync, demand-forecast |
| `inventory.low-stock` | `sku_id` | LowStockEvent | procurement, replenishment, notification |
| `carrier.rate.changed` | `carrier_id` | RateChangedEvent | rate-engine, analytics |
| `return.requested` | `customer_id` | ReturnRequestedEvent | returns, notification, analytics |
| `return.received` | `customer_id` | ReturnReceivedEvent | inventory, refund, analytics |
| `shipment.tracking` | `carrier_id` | TrackingEvent | order-orchestrator, analytics, carrier-scorecard |

**Event Schema (Avro / Protobuf):**

```protobuf
message OrderCreatedEvent {
  string event_id = 1;          // SHA-256 deterministic ID for idempotency
  string order_id = 2;
  string customer_id = 3;
  string channel = 4;           // SHOPIFY | BIGCOMMERCE | MAGENTO | AMAZON | EDI
  string channel_order_id = 5;
  OrderStatus status = 6;
  repeated LineItem items = 7;
  Address ship_to = 8;
  repeated string eligible_nodes = 9;
  Timestamp created_at = 10;
  map<string, string> metadata = 11;  // extensible attributes
}
```

### 3.5 API Design Standards

- **Protocol**: REST (CRUD) + gRPC (internal high-throughput) + GraphQL (frontend)
- **Versioning**: URL path v1, v2 (semantic versioning, N-1 backward compatibility)
- **Rate limiting**: Per-tenant, per-endpoint (Kong), burst=2x sustained
- **Authentication**: OAuth 2.0 (external), JWT (internal), mTLS (service-to-service)
- **Idempotency**: `Idempotency-Key` header (SHA-256 of channel+record+timestamp, 24h window)
- **Pagination**: Cursor-based (recommended) + offset-based (legacy)
- **Error format**: RFC 7807 Problem Details (application/problem+json)
- **Documentation**: OpenAPI 3.1, exported as interactive SwaggerUI

---

## 4. Feature Inventory & Capability Matrix

### 4.1 Order Management

| Feature | Priority | Notes |
|---------|----------|-------|
| Multi-channel order intake | P0 | Shopify, BigCommerce, Magento, WooCommerce, Amazon, Walmart, eBay, TikTok Shop, custom REST webhook |
| EDI 850 Purchase Order ingestion | P1 | B2B/wholesale order flow |
| Manual order entry (admin UI) | P1 | Customer lookup, real-time ATP check |
| Quote-to-Order workflow (B2B) | P2 | Draft quote, approval, PO attachment, convert |
| Phone/email order capture | P2 | CSR-assisted checkout screen |
| Order splitting | P0 | Auto-split across fulfillment nodes based on inventory |
| Backorder management | P1 | Partial fulfillment + auto-release on stock arrival |
| Pre-order capture | P2 | Collect payment now, fulfill on availability |
| Order hold/release management | P1 | Fraud hold, credit hold, manual review |
| Order cancellation workflow | P1 | Multi-step with inventory release |
| Order editing (post-confirmation) | P2 | Line item add/remove, address change |
| Order merge/unmerge | P2 | Combine/cancel split shipments |
| Distributed Order Management (DOM) | P0 | Proximity, cost, inventory-balance, SLA-priority routing |
| Split-shipment minimization | P0 | Prefer single-node fulfillment |
| Channel-specific inventory pools | P1 | Reserve stock per channel |
| Real-time fallback routing | P0 | 30-second auto-reroute on node rejection |
| Order timeline/audit trail | P0 | Immutable event log per order |
| SLA monitoring & escalation | P1 | Configurable SLA rules, auto-escalation |
| Bulk order operations | P1 | Batch status updates, label generation, routing |

### 4.2 Omnichannel Fulfillment

| Feature | Priority | Notes |
|---------|----------|-------|
| BOPIS (Buy Online, Pick Up In-Store) | P0 | PDP-level availability, same-day pickup, SMS/email notification |
| BOPIS queue mgmt (staff app) | P1 | Countdown timers, order prep, handoff confirmation |
| Curbside pickup | P1 | QR code / license plate check-in, staff mobile app |
| Partial BOPIS | P1 | Mix BOPIS + ship in single order |
| BOPIS hold mgmt (auto-release) | P1 | Configurable hold period, auto-restock unclaimed |
| Ship-From-Store (SFS) | P0 | Convert stores to fulfillment nodes |
| SFS capacity management | P1 | Per-store daily caps to protect retail operations |
| SFS staff pick list generation | P1 | Aisle/location guidance with planogram |
| SFS carrier label printing | P1 | Thermal printer (Zebra, Brother, Dymo) |
| Ship-To-Store (S2S) | P1 | DC-to-store customer pickup |
| Endless Aisle | P2 | In-store kiosk ordering out-of-stock items |
| Store-to-store transfer | P2 | Inventory rebalancing between stores |
| Distributed Order Management | P0 | Multi-node evaluation engine |

### 4.3 Inventory Management

| Feature | Priority | Notes |
|---------|----------|-------|
| Real-time ATP computation | P0 | Physical - allocated - reserved |
| Multi-location inventory | P0 | Warehouses, stores, 3PLs, in-transit, consignment |
| Lot/batch tracking | P1 | FEFO/FIFO allocation, regulated products |
| Serial number tracking | P2 | Individual unit tracking for high-value items |
| Safety stock management | P1 | Per-SKU, per-location minimum thresholds |
| Inventory aging analysis | P2 | Dead stock identification, slow-mover alerts |
| AI inventory positioning | P1 | Demand geography heatmap, redistribution recommendations |
| 2-day delivery coverage calculator | P2 | SKU-node coverage optimization |
| Seasonal redistribution planning | P2 | Proactive stock rebalancing for peaks |
| Network optimization simulation | P3 | What-if modeling (open/close nodes) |
| Cycle counting | P1 | Scheduled + ad-hoc cycle count workflows |
| Inventory sync (multi-channel) | P0 | Bidirectional sync with eCommerce, marketplaces |
| In-transit inventory tracking | P1 | Purchase order to receiving visibility |
| Consignment inventory management | P2 | Supplier-owned stock at customer location |

### 4.4 Warehouse Management (WMS-Lite)

| Feature | Priority | Notes |
|---------|----------|-------|
| Receiving (PO/ASN matching) | P1 | Inbound QC, put-away generation |
| Pick & Pack | P0 | Wave planning, batch/zone/cart picking |
| Packing station | P0 | Item scan verify, dim weight capture, carton selection |
| Kitting / Bundling | P2 | BOM-based kit assembly |
| QC exception handling | P1 | Damage flagging, auto-substitution |
| Wave planning (bulk optimization) | P1 | 10,000+ order wave evaluation |
| Picking strategies | P1 | FIFO, FEFO, LIFO configurable per SKU |
| Label printing (thermal) | P0 | PDF 4x6, ZPL, EPL2 |
| Mobile scanner support | P1 | Barcode/RFID scanning |
| Cross-docking | P2 | Inbound-to-outbound flow-through |
| Putaway optimization | P2 | Location suggestion based on velocity/category |
| Replenishment task generation | P1 | Pick-face refill triggers |
| Containerization recommendation | P1 | Box size optimization |

### 4.5 Carrier Management & Shipping

| Feature | Priority | Notes |
|---------|----------|-------|
| Multi-carrier rate shopping | P0 | 150+ carriers, parallel fanout <80ms |
| ML-powered carrier selection | P0 | XGBoost ranker factoring OTD, damage, invoice accuracy |
| Service level optimization | P1 | Auto-honor customer-facing delivery promises |
| Label generation (PDF/ZPL/EPL2) | P0 | S3 storage with presigned URL (24h TTL) |
| Label void/reprint | P1 | Carrier void windows (30min-90 days) |
| Rate caching | P1 | Redis, 15-min TTL, 60%+ hit rate target |
| Rate normalisation (canonical model) | P0 | Itemized surcharge breakdown |
| Surcharge prediction (ML) | P1 | DAS, residential, peak, additional handling |
| Bulk carrier optimization | P1 | Wave-level evaluation of 500+ orders |
| Multi-origin optimization | P1 | Ship-from-store (3-5 origin evaluation) |
| Carrier account management | P0 | Multi-account, per-location carrier config |
| Per-carrier routing guide enforcement | P1 | Contract compliance verification |
| Negotiated rate tier optimization | P2 | Volume-driven rate tier assignment |
| Carrier performance scorecards | P1 | Lane-level OTD, damage, invoice accuracy |
| 3PL module (multi-client) | P2 | Per-client markup, white-label, consolidated billing |
| LTL optimization | P3 | NMFC classification, freight class optimization |
| Ocean/air freight integration | P3 | Flexport API escalation |

### 4.6 Returns Management

| Feature | Priority | Notes |
|---------|----------|-------|
| Self-service returns portal | P0 | Branded widget/URL, configurable reason taxonomy |
| Instant exchange (advanced) | P1 | Ship replacement before receiving return |
| ML-selected return carrier | P1 | Optimize return transit cost/speed |
| QR-code drop-off | P1 | UPS Access Points, FedEx Office |
| Cross-border returns | P2 | Pre-generated customs docs |
| Returns ASN (pre-alert) | P1 | Inbound receiving capacity planning |
| Grading workflow | P1 | Physical inspection, grade capture (A/B/C) |
| Automated disposition routing | P1 | Restock, refurbish, liquidate, destroy rules |
| Refund automation | P0 | Auto-refund on QC approval |
| Carrier claim management | P2 | Damage/lost claim filing and tracking |
| Returns analytics | P1 | Return rate by SKU, reason taxonomy, cost |

### 4.7 B2B Commerce

| Feature | Priority | Notes |
|---------|----------|-------|
| Company account hierarchy | P2 | Parent + child accounts |
| Price list management | P2 | Customer-group, volume tier, contract pricing |
| Purchase order workflow | P2 | EDI 850 or manual PO |
| Credit limit enforcement | P2 | Real-time AR balance check |
| Quote management | P2 | Create, approve, convert to order |
| EDI compliance (850/856/810) | P1 | Walmart, Target, Home Depot, Kroger |
| Wholesale order form | P2 | Quick-entry SKU grid for B2B reorder |

### 4.8 Analytics & BI

| Feature | Priority | Notes |
|---------|----------|-------|
| Executive KPI dashboard | P0 | Orders today, OTD%, shipping cost vs budget, exceptions, carrier utilization |
| Order velocity chart | P0 | Hourly volume overlaid with fulfillment throughput |
| Interactive network map | P1 | Fulfillment node health indicators |
| Exception queue | P0 | Prioritized, SLA-sorted, one-click resolve |
| Carrier scorecards | P0 | Per-carrier OTD%, damage, invoice accuracy, cost/shipment |
| Cost breakdown waterfall | P1 | Base rate + surcharge decomposition |
| Lane analysis | P1 | Top 20 O-D pairs by volume/cost |
| Returns analytics | P1 | Return rate, reason taxonomy, cost |
| Executive summary (PDF export) | P2 | Monthly review pack |
| Carbon emissions tracking | P3 | Carrier-level emissions reporting |
| Carrier benchmarking | P2 | Peer group comparison |
| Profitability analysis | P3 | Per-order profitability |
| Custom report builder | P3 | Drag-and-drop report creation |

### 4.9 AI/ML Features

| Model | Type | Input | Output | Priority |
|-------|------|-------|--------|----------|
| Carrier Selection Optimizer | XGBoost Ranker | Shipment features, carrier features, rates | Ranked carrier list with scores | P0 |
| Delivery Success Predictor | Gradient Boosted Classifier | Route, temporal, carrier features | P(OnTime) per carrier-service | P1 |
| Surcharge Predictor | Tabular NN | Shipment dims, route, carrier, temporal | Predicted surcharge amounts | P1 |
| Dimensional Weight Predictor | Random Forest | SKU catalog, order items, packing history | Predicted box dims, dim weight | P1 |
| Invoice Anomaly Detector | Isolation Forest | Invoiced vs quoted rates, carrier baselines | Anomaly score, dispute packet | P1 |
| Demand Forecaster (TFT) | Temporal Fusion Transformer | Static + past/future covariates, 120d lookback | Quantile forecasts (P10/P50/P90) 28d horizon | P2 |
| Inventory Optimizer | XGBoost Classifier | Demand forecast, lead times, service targets | Reorder qty, safety stock recs | P2 |
| HS Code Classifier | Fine-tuned BERT | Product description, category, attributes | HS code with confidence score | P2 |
| Order Routing Optimizer | XGBoost Classifier | Order features, node distance, capacity | Optimal node assignment | P0 |

---

## 5. Business Process Engineering

### 5.1 Order Lifecycle State Machine

```
                    ┌─────────────────────────────────────────────┐
                    │              ORDER LIFECYCLE                │
                    └─────────────────────────────────────────────┘

  CHANNEL ──► DRAFT ──► PENDING_PAYMENT ──► CONFIRMED ──► ALLOCATED
                │                            │
                ▼                            ▼
           CANCELLED                    ON_HOLD (fraud/credit)
                                           │
                                           ▼
                                      RELEASED ──► ALLOCATED
                                                       │
                                                       ▼
                                              ┌─── ROUTING ───┐
                                              │   AUTOMATIC   │
                                              │   MANUAL      │
                                              │   EXCEPTION   │
                                              └───────┬───────┘
                                                       │
                                                       ▼
                                                  ALLOCATED
                                                       │
                                              ┌────────┴────────┐
                                              │                 │
                                         FULL_NODE      SPLIT_NODE
                                              │                 │
                                              ▼                 ▼
                                         PICKING         PICKING (N)
                                              │                 │
                                              ▼                 ▼
                                         PACKING           PACKING
                                              │                 │
                                              ▼                 ▼
                                         SHIPPED           SHIPPED
                                              │                 │
                                              └────────┬────────┘
                                                       │
                                                       ▼
                                                  IN_TRANSIT
                                                       │
                                                       ▼
                                                  DELIVERED
                                                       │
                                              ┌────────┴────────┐
                                              │                 │
                                         COMPLETED       RETURN_INIT
                                              │                 │
                                              ▼                 ▼
                                                           RETURN_REQ
                                                                │
                                                                ▼
                                                         INSPECTION
                                                                │
                                                    ┌───────────┴───────────┐
                                                    │                       │
                                                RESTOCK             DISPOSITION
                                                    │                       │
                                                    ▼                       ▼
                                                COMPLETED             CLOSED
```

### 5.2 BOPIS Process (Swimlane)

```
CUSTOMER              STOREFRONT              NEXUSSHIP OMS          STORE STAFF          CARRIER
   │                      │                       │                     │                   │
   │──Browse Product──▶   │                       │                     │                   │
   │                      │──Check PDP BOPIS──▶   │                     │                   │
   │                      │   availability by ZIP  │──ATP Lookup──▶      │                   │
   │                      │                       │◀──Stock Available──  │                   │
   │◀──"Pick Up Today"──  │                       │                     │                   │
   │                      │                       │                     │                   │
   │──Select Store────▶   │                       │                     │                   │
   │                      │                       │                     │                   │
   │──Place Order──────▶  │──Create Order───────▶  │                     │                   │
   │                      │                       │──Reserve Inventory──▶│                   │
   │                      │                       │                      │                   │
   │◀──Order Confirmed──  │◀──Confirmation──────  │                     │                   │
   │  with pickup code    │                       │                      │                   │
   │                      │                       │──Push to BOPIS Queue─▶│                   │
   │                      │                       │   (priority-sorted)  │                   │
   │                      │                       │                      │──Prep Order───────│
   │                      │                       │                      │──Scan Items───────│
   │                      │                       │◀──Status: READY────  │                   │
   │◀──"Ready for Pickup" │◀──Notification──────  │                     │                   │
   │  (SMS/Email)         │                       │                     │                   │
   │                      │                       │                     │                   │
   │──Arrive at Store────▶│                       │──Notify Staff────────▶│                   │
   │──Show QR Code───────▶│                       │                      │──Verify Code──────│
   │                      │                       │                      │──Handoff Items────│
   │                      │                       │◀──Handoff Confirm──  │                   │
   │                      │                       │──Status: PICKED_UP──▶│                   │
   │                      │                       │──Release Hold──────  │                   │
   │                      │                       │──Trigger Feedback────▶│                   │
```

### 5.3 Standard DTC Order Fulfillment

```
SHOPIFY                NEXUSSHIP OMS              AI SERVICES             WMS             CARRIER
   │                       │                         │                   │                 │
   │──Webhook: order.create│                         │                   │                 │
   │──HMAC Verify─────────▶│                         │                   │                 │
   │                       │──Normalize to CDM───────│                   │                 │
   │                       │──Check ATP──────────────│                   │                 │
   │                       │──Save Order (PENDING)───│                   │                 │
   │                       │──Publish order.created──│                   │                 │
   │                       │                         │                   │                 │
   │◀──Order Received Ack─│                         │                   │                 │
   │                       │                         │                   │                 │
   │──Payment Webhook────▶│──Confirm Payment─────────│                   │                 │
   │                       │──Reserve Inventory      │                   │                 │
   │                       │──Status: CONFIRMED      │                   │                 │
   │                       │                         │                   │                 │
   │                       │──POST /allocate────────▶│──Model 1: Routing──│                 │
   │                       │                         │──Model 2: Carrier──│                 │
   │                       │                         │──Model 3: Box──────│                 │
   │                       │                         │──Model 4: PickPack─│                 │
   │                       │◀──Allocation Decision───│                   │                 │
   │                       │──Update Order           │                   │                 │
   │                       │──Status: ALLOCATED      │                   │                 │
   │                       │                         │                   │                 │
   │                       │──Send to WMS────────────│──────────────────▶│                 │
   │                       │                         │                   │──Pick Items─────│
   │                       │                         │                   │──Pack Order─────│
   │                       │                         │                   │──Scan Dim Weight│
   │                       │                         │                   │                 │
   │                       │──Get Rates──────────────│──────────────────────────────────▶│
   │                       │◀──Ranked Rates──────────│───────────────────────────────────│
   │                       │──Select Optimal Carrier─│                   │                 │
   │                       │──Generate Label─────────│──────────────────────────────────▶│
   │                       │◀──Label + Tracking──────│───────────────────────────────────│
   │                       │                         │                   │                 │
   │                       │──Write Tracking to WMS─▶│                   │──Apply Label───│
   │                       │──Status: SHIPPED        │                   │──Manifest──────│
   │                       │                         │                   │                 │
   │──Tracking Update◀────│──Write Tracking to Shopify                   │                 │
   │                       │──Publish order.shipped──│                   │                 │
   │                       │                         │                   │                 │
   │                       │                         │                   │──Pickup────────▶│
   │                       │                         │                   │                 │──Scan──▶
   │                       │                         │                   │                 │──Sort──▶
   │                       │                         │                   │                 │──Transport──▶
   │                       │                         │                   │                 │──Deliver──▶
   │                       │                         │                   │                 │──Delivery Scan▶
   │                       │                         │                   │                 │
   │                       │◀──Delivery Webhook──────│───────────────────────────────────│
   │                       │──Status: DELIVERED      │                   │                 │
   │                       │──Publish order.delivered│                   │                 │
   │                       │──Update ML Feature Store│                   │                 │
```

### 5.4 Returns Process

```
CUSTOMER               RETURNS PORTAL          NEXUSSHIP OMS          WAREHOUSE            CARRIER
   │                       │                       │                     │                   │
   │──Initiate Return─────▶│                       │                     │                   │
   │                       │──Select Items/Reason──│                     │                   │
   │                       │──Photo Upload─────────▶                     │                   │
   │                       │(if defective)         │                     │                   │
   │                       │                       │──Create RMA Record──│                   │
   │                       │──ML Return Carrier    │                     │                   │
   │                       │  Optimization────────▶│─Return Label Gen───│──────────────────▶│
   │◀──QR Code + Label─────│◀─────────────────────│                     │                   │
   │                       │                       │                     │                   │
   │──Drop at Access Point▶│                       │──Notification:      │                   │
   │                       │                       │  Return Inbound────▶│                   │
   │                       │                       │  (Returns ASN)      │                   │
   │                       │                       │                     │                   │
   │                       │                       │◀──Carrier Scan──────│───────────────────│
   │                       │                       │──Status: IN_TRANSIT─│                   │
   │                       │                       │                     │                   │
   │                       │                       │◀──Receiving Scan────│                   │
   │                       │                       │──Inspection Trigger─▶│                   │
   │                       │                       │                     │──Grade Inspection─│
   │                       │                       │                     │  Grade A: Restock │
   │                       │                       │                     │  Grade B: Refurb  │
   │                       │                       │                     │  Grade C: Dispose  │
   │                       │                       │◀──Grade + Disposition│                   │
   │                       │                       │                     │                   │
   │                       │                       │──Process Disposition─│──Restock/Scrap────│
   │                       │                       │──Trigger Refund─────▶                   │
   │◀──Refund Confirmed────│◀──────────────────────│                     │                   │
   │                       │                       │                     │                   │
   │                       │                       │──Update ML Models───│                   │
   │                       │                       │  (return rate,      │                   │
   │                       │                       │   reason, carrier)  │                   │
```

### 5.5 Inventory Flow

```
                    INVENTORY LIFECYCLE STATE MACHINE

              ┌────────────────────────────────────────────────┐
              │               INVENTORY UNIT                    │
              └────────────────────────────────────────────────┘
                         │
                         ▼
                    ON_ORDER (PO placed to supplier)
                         │
                         ▼
                    IN_TRANSIT (shipped by supplier)
                         │
                         ▼
                    RECEIVING (being scanned at dock)
                         │
                    ┌────┴────┐
                    │         │
                    ▼         ▼
               PUTAWAY    QC_HOLD (damaged during transit)
                    │         │
                    │         ▼
                    │    RETURN_TO_SUPPLIER
                    │         │
                    └────┬────┘
                         ▼
                    AVAILABLE
                         │
                    ┌────┴────────────────┐
                    │                     │
                    ▼                     ▼
               SOFT_HELD (Redis,      ALLOCATED (order assigned,
               temporary,             physical reservation)
               cart timeout)               │
                    │                      │
                    └──────┬───────────────┘
                           ▼
                      PICKING
                           │
                           ▼
                      PACKED
                           │
                           ▼
                      SHIPPED (deducted from inventory)
                           │
                           ▼
                      ┌────────┐
                      │ RETURN │──► INSPECTION ──► RESTOCK (grade A)
                      └────────┘                  REFURBISH (grade B)
                                                  DISPOSE (grade C)
                                                  INSURANCE_CLAIM (lost/damaged)
```

### 5.6 Process Flow: B2B EDI Order (Walmart/Target)

```
BUYER(Retailer)        NEXUSSHIP EDI HUB        SELLER(Brand)           WMS           CARRIER
   │                       │                        │                   │               │
   │──EDI 850 (PO)───────▶│──AS2/SFTP Receive──────│                   │               │
   │                       │──X12 Parsing           │                   │               │
   │                       │──Map to CDM Order      │                   │               │
   │                       │──Validate PO (contract)│                   │               │
   │                       │──Check Credit Limit    │                   │               │
   │                       │                         │                   │               │
   │◀──EDI 855 (PO Ack)───│──Auto-Generate Ack──────│                   │               │
   │                       │──Status: CONFIRMED     │                   │               │
   │                       │                         │                   │               │
   │                       │──Allocate Inventory─────│──────────────────▶│               │
   │                       │──Publish order.allocated                   │               │
   │                       │                         │                   │               │
   │                       │─Generate Pick List──────│──────────────────▶│──Pick & Pack──│
   │                       │                         │                   │               │
   │                       │──Generate ASN Data──────│                   │               │
   │                       │──Create SSCC Barcodes───│                   │──Label Pallet─│
   │                       │──Book Carrier───────────│──────────────────────────────────▶│
   │                       │                         │                   │               │
   │◀──EDI 856 (ASN)──────│──EDI 856 Generation─────│                   │               │
   │                       │  (Shipment + Pallet +   │                   │               │
   │                       │   Carton hierarchy)     │                   │               │
   │                       │                         │                   │──Manifest─────│
   │                       │                         │                   │──Ship────────▶│
   │                       │                         │                   │               │
   │◀──EDI 810 (Invoice)──│──Generate Invoice────────│                   │               │
   │                       │                         │                   │               │
   │──Payment (EDI 820)──▶│──Match Payment───────────│                   │               │
```

---

## 6. Integration Architecture

### 6.1 Integration Hub Overview

```
                         ┌──────────────────────────────┐
                         │      NEXUSSHIP INTEGRATION HUB      │
                         └──────────────────────────────┘
                                      │
         ┌────────────────┬───────────┼───────────┬────────────────┐
         │                │           │           │                │
    ┌────▼────┐     ┌────▼────┐ ┌────▼────┐ ┌────▼────┐     ┌────▼────┐
    │eCommerce│     │   ERP   │ │   WMS   │ │Carriers │     │Marketpl.│
    │Connector│     │Connector│ │Connector│ │Connector│     │Connector│
    └─────────┘     └─────────┘ └─────────┘ └─────────┘     └─────────┘
         │                │           │           │                │
         ▼                ▼           ▼           ▼                ▼
    ┌─────────────────────────────────────────────────────────────────┐
    │                    CANONICAL DATA MODEL (CDM)                    │
    │   Order / Shipment / Inventory / Product / Customer / Return    │
    └─────────────────────────────────────────────────────────────────┘
         │                │           │           │                │
         ▼                ▼           ▼           ▼                ▼
    ┌─────────────────────────────────────────────────────────────────┐
    │                    CORE PLATFORM SERVICES                       │
    └─────────────────────────────────────────────────────────────────┘
```

### 6.2 Connector Specifications

#### 6.2.1 Shopify Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | Shopify Access Token (OAuth) + HMAC-SHA256 webhook verification |
| **Data Flow** | Webhook subscription → order.created/updated/fulfilled/cancelled |
| **APIs** | GraphQL Admin API (primary), REST Admin API (fallback) |
| **Write-back** | fulfillmentCreate mutation, location inventory levels |
| **Webhooks** | orders/create, orders/updated, orders/fulfilled, orders/cancelled, fulfilment_events/create |
| **Sync** | Full order sync (backfill), inventory level sync, location sync |
| **Rate Limits** | 40/sec per app (GraphQL), 2/sec (REST) |
| **Notable** | Shopify FulfillmentOrder resource for DOM integration |

#### 6.2.2 BigCommerce Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | OAuth 2.0 (client_credentials) or Basic Auth (legacy) |
| **Data Flow** | Webhooks + REST polling |
| **APIs** | Storefront API (GraphQL), Store Management API (REST) |
| **Write-back** | Orders API (PUT), Shipping API, Fulfillment API |
| **Webhooks** | store/order/*, store/shipment/* |
| **Notable** | Multi-storefront from single backend, B2B pricing engine |

#### 6.2.3 Magento (Adobe Commerce) Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | Integration Token (Admin) + OAuth 1.0a (legacy) |
| **Data Flow** | REST API polling + webhook module |
| **APIs** | REST (all), GraphQL (partial) |
| **Write-back** | Shipment creation, invoice creation, order status update |
| **Notable** | Highest customization depth, headless PWA support |

#### 6.2.4 CommerceTools Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | OAuth 2.0 client_credentials (project-scoped) |
| **Data Flow** | REST API + Message Queue subscriptions |
| **APIs** | REST (full), GraphQL (partial), Messages API |
| **Write-back** | Order update, delivery info, inventory entries |
| **Notable** | True composable/headless, cloud-native, microservices |

#### 6.2.5 SAP S/4HANA Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | RFC User (JCo) / OAuth 2.0 + XSUAA (BTP) / Basic (IDoc AS2) |
| **Integration Pattern** | Pattern A: BAPI/RFC direct (on-prem, <50ms); Pattern B: BTP OData (cloud); Pattern C: IDoc/ALE batch (legacy) |
| **Key APIs** | BAPI_OUTB_DELIVERY_CONFIRM, BAPI_SHIPMENT_CONFIRM, API_OUTBOUND_DELIVERY_SRV, API_TRANSPORTATION_SHIPMENT_SRV |
| **IDoc Types** | DESADV (dispatch advice), SHPMNT (shipment) |
| **Notable** | Plant master cache (T001W), HU management, hazmat (DGM), route determination (TVRO) |

#### 6.2.6 Oracle Fusion / EBS Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | OAuth 2.0 (Fusion Cloud) / SOAP session token (EBS 12.2.x) |
| **Integration Pattern** | OIC webhook trigger (cloud), SOAP gateway (EBS) |
| **Key APIs** | FreightCosts REST endpoint, Attachment API, WSH_UTIL_CORE (SOAP) |
| **Notable** | Oracle Shipping Execution (WSH) manages delivery trips/stops |

#### 6.2.7 NetSuite Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | OAuth 2.0 PKCE (preferred), OAuth 1.0a (legacy), Token-Based Auth |
| **Integration Method** | SuiteApp (managed bundle) with UserEvent script + SuiteTalk SOAP fallback |
| **Key Records** | Sales Order, Item Fulfillment, Shipping Item, Custom Records |
| **Write-back** | Auto-populate carrier, service level, cost on Item Fulfillment; Tracking Number |
| **Notable** | SuiteScript 2.1, Map/Reduce offload for long ops, governance-aware |

#### 6.2.8 Microsoft Dynamics 365 Connector

| Aspect | Detail |
|--------|--------|
| **Auth** | Microsoft Entra ID OAuth 2.0 |
| **Integration Method** | OData REST (F&SCM), AL Extension (Business Central AppSource) |
| **Key APIs** | salesOrders, salesShipments, customers, locations (BC API v2.0) |
| **Notable** | X++ Extension models (not overlayering), TM module route guides |

#### 6.2.9 WMS Connectors

| System | Method | Key Integration Points |
|--------|--------|----------------------|
| **Manhattan WMS** | REST + EDI | Order push, inventory sync, fulfillment status, wave management |
| **Blue Yonder WMS** | REST + EDI | Order management, inventory visibility, wave planning |
| **SAP EWM** | RFC/BAPI | Warehouse order creation, stock transfer, picking confirmation |
| **Oracle WMS** | REST (OIC) | Inbound/outbound processing, inventory transactions |
| **Infor WMS** | REST + EDI | Order fulfillment, inventory sync, shipping |
| **Extensiv (3PL Central)** | REST + EDI 940/945 | Fulfillment request/confirmation |
| **ShipBob** | REST | Order push, inventory sync, fulfillment pull |
| **ShipHero** | REST | Order creation, webhook events, tracking pull |
| **Deposco** | REST + EDI | Order management, fulfillment, inventory |

#### 6.2.10 Carrier API Specifications

| Carrier | API Type | Auth | Rate API | Ship API | Tracking | Notes |
|---------|----------|------|----------|----------|----------|-------|
| **UPS** | REST | OAuth 2.0 | Rating API v1 | Shipping API v1 | Tracking API v1 | 70+ surcharges; OAuth migration complete |
| **FedEx** | REST | OAuth 2.0 | Rate API v1 | Ship API v1 | Track API v1 | Migrated from WSDL 2022; unified token |
| **DHL Express** | REST | API Key + Bearer | Rate API | Shipment API | Tracking API | 3 business units; fastest customs for APAC |
| **DHL eCommerce** | REST | API Key | Rate API | Shipping API | Tracking API | 30-50% cheaper for <2kg international |
| **USPS** | REST | API Key | Pricing API | Shipping API | Tracking API | Cubic pricing; Commercial Plus via aggregators |
| **EasyPost** | REST | API Key | Rates API | Shipment API | Tracker API | Fallback aggregator: 100+ carriers |

### 6.3 Canonical Data Model (CDM) v1.0

#### Core Entity: Order

```typescript
interface Order {
  // Identity
  id: string;                    // NexusShip UUID
  externalId: string;            // Source system record ID
  erpSystem: ErpSystem;          // SAP | ORACLE | NETSUITE | D365
  channel: Channel;              // SHOPIFY | BIGCOMMERCE | MAGENTO | AMAZON | EDI | POS
  channelOrderId: string;        // Source channel order ID
  tenantId: string;              // Multi-tenant partition

  // Customer
  customerId: string;
  customerEmail: string;
  customerName: string;
  customerType: CustomerType;    // B2C | B2B

  // Status
  status: OrderStatus;           // DRAFT→CONFIRMED→ALLOCATED→SHIPPED→DELIVERED
  subStatus: string;             // Domain-specific (e.g., PICKING, PACKING)

  // Fulfillment
  fulfillmentType: FulfillmentType; // SHIP | BOPIS | CURBSIDE | SFS | S2S
  shipFrom: Address;
  shipTo: Address;
  billingAddress: Address;
  requestedDeliveryDate: Date;
  promisedDeliveryDate: Date;
  deliveryWindowStart: Date;
  deliveryWindowEnd: Date;

  // Commercial
  currency: string;              // ISO 4217
  subtotal: number;
  shippingCost: number;
  taxAmount: number;
  total: number;
  paymentStatus: PaymentStatus;
  paymentReference: string;

  // Items & Packages
  lineItems: LineItem[];
  packages: Package[];

  // Routing
  allocatedNodeId: string;
  allocationRule: string;        // Which rule triggered allocation
  allocationConfidence: number;  // ML confidence score
  splitShipments: string[];      // Child shipment IDs

  // Carrier
  carrierId: string;
  carrierService: string;
  trackingNumber: string;
  labelUrl: string;              // S3 presigned URL

  // Time
  createdAt: Timestamp;
  updatedAt: Timestamp;
  shippedAt: Timestamp;
  deliveredAt: Timestamp;

  // Audit
  createdBy: string;
  events: OrderEvent[];          // Immutable event log
  metadata: Record<string, any>; // Extensible attributes
}
```

#### Core Entity: Shipment

```typescript
interface Shipment {
  id: string;
  orderId: string;
  tenantId: string;

  // Carrier
  carrierId: string;
  carrierName: string;
  serviceLevel: string;          // UPS_GROUND, FEDEX_2DAY, etc.
  trackingNumber: string;
  labelUrl: string;              // S3 presigned URL (24h TTL)
  labelFormat: string;           // PDF | ZPL | EPL2
  voided: boolean;
  voidedAt: Timestamp;

  // Financial
  rate: Rate;
  costComponents: CostComponent[];  // Itemized surcharges
  currency: string;

  // Fulfillment
  originNodeId: string;
  packages: Package[];
  estimatedDelivery: Date;
  actualDelivery: Date;
  status: ShipmentStatus;
  events: TrackingEvent[];

  // Timestamps
  createdAt: Timestamp;
  shippedAt: Timestamp;
  deliveredAt: Timestamp;
  manifestClosedAt: Timestamp;
}
```

#### Core Entity: Rate

```typescript
interface Rate {
  id: string;
  carrierId: string;
  serviceLevel: string;
  baseRate: number;
  fuelSurcharge: number;
  residentialSurcharge: number;
  dasSurcharge: number;           // Delivery Area Surcharge
  extendedDasSurcharge: number;
  additionalHandling: number;
  peakSurcharge: number;
  largePackageSurcharge: number;
  oversizeSurcharge: number;
  otherSurcharges: SurchargeItem[];
  totalRate: number;
  currency: string;
  rateDate: Date;
  negotiatedDiscount: number;     // Percentage
  listRate: number;               // Undiscounted for benchmarking
  transitDays: number;
  guaranteedBy: Date;
  deliveryCommitment: string;     // EOB, 10:30AM, 12:00PM
}
```

#### Core Entity: Inventory

```typescript
interface Inventory {
  id: string;
  sku: string;
  tenantId: string;
  nodeId: string;                // Warehouse/Store/3PL
  nodeType: NodeType;            // WAREHOUSE | STORE | 3PL | DROPSHIPPER
  quantityOnHand: number;
  quantityAllocated: number;
  quantityReserved: number;      // Pending payment confirmation
  quantityInTransit: number;
  quantityOnOrder: number;       // PO placed to supplier
  quantityDamaged: number;
  quantityOnHold: number;
  availableToPromise: number;    // Computed: OnHand - Allocated - Reserved
  safetyStock: number;
  reorderPoint: number;
  reorderQuantity: number;
  lotNumber: string;
  serialNumber: string;
  expiryDate: Date;
  lastCountedAt: Timestamp;
  lastUpdatedAt: Timestamp;
}
```

### 6.4 Event Types (Canonical)

```protobuf
// Order Events
message OrderCreated { Order order; string channelOrderId; string channel; }
message OrderConfirmed { string orderId; string paymentRef; }
message OrderAllocated { string orderId; string nodeId; AllocatedItem[] items; }
message OrderShipped { string orderId; string trackingNumber; string carrier; Rate rate; }
message OrderDelivered { string orderId; Timestamp deliveredAt; string signature; }
message OrderException { string orderId; string exceptionType; string description; }
message OrderCancelled { string orderId; string reason; }

// Inventory Events
message InventoryChanged { string sku; string nodeId; int delta; int newAtp; string reason; }
message InventoryLowStock { string sku; string nodeId; int currentAtp; int reorderPoint; }
message InventoryReservation { string orderId; string sku; string nodeId; int qty; int ttlSeconds; }
message InventoryRelease { string orderId; string sku; string nodeId; int qty; }

// Shipment Events
message ShipmentBooked { string orderId; string carrier; string tracking; Label label; }
message ShipmentVoided { string orderId; string tracking; string reason; }
message ShipmentInTransit { string tracking; string scanLocation; Timestamp scanTime; }
message ShipmentOutForDelivery { string tracking; string driverId; string vehicleId; }
message ShipmentDelivered { string tracking; string signature; string deliveryPhotoUrl; }
message ShipmentException { string tracking; string exceptionCode; string description; }

// Return Events
message ReturnRequested { string returnId; string orderId; string reason; string[] items; }
message ReturnLabelGenerated { string returnId; string carrier; string tracking; string labelUrl; }
message ReturnReceived { string returnId; string nodeId; Timestamp receivedAt; }
message ReturnInspected { string returnId; string grade; string disposition; }
message ReturnRefunded { string returnId; string paymentRef; number amount; }
```

---

## 7. Data Architecture

### 7.1 Multi-Tenant Strategy

| Tenant Tier | Data Isolation | Scaling | Max Tenants/Node |
|-------------|---------------|---------|------------------|
| **Standard** | Row-level security (RLS) via `tenant_id` column | Citus hash sharding on `tenant_id` | 500 |
| **Enterprise** | Schema-per-tenant (dedicated schema) | Dedicated DB instance or PgBouncer pool | 50 |
| **HIPAA/Gov** | Dedicated DB instance + KMS CMK | Dedicated EKS node group | 10 |

### 7.2 PostgreSQL Schema Design

```
┌────────────────────────────────────────────────────────────────────┐
│                    CORE TABLES (Per-Tenant)                        │
├────────────────────────────────────────────────────────────────────┤
│ nx_order                          nx_order_item                    │
│ ┌─────────────────────────┐      ┌──────────────────────────┐     │
│ │ id (UUID PK)            │      │ id (UUID PK)             │     │
│ │ tenant_id (PK, SHARD)   │──────│ order_id (FK)            │     │
│ │ external_id             │      │ sku                      │     │
│ │ channel                 │      │ product_name             │     │
│ │ channel_order_id        │      │ quantity                 │     │
│ │ customer_id             │      │ unit_price               │     │
│ │ customer_email          │      │ allocated_node_id        │     │
│ │ customer_name           │      │ allocated_qty            │     │
│ │ fulfillment_type        │      │ shipped_qty              │     │
│ │ status                  │      │ returned_qty             │     │
│ │ sub_status              │      │ created_at               │     │
│ │ ship_from_id            │      │ updated_at               │     │
│ │ ship_to_address (JSONB) │      └──────────────────────────┘     │
│ │ billing_address (JSONB) │                                        │
│ │ currency                │      nx_shipment                      │
│ │ total                   │      ┌──────────────────────────┐     │
│ │ shipping_cost           │      │ id (UUID PK)             │     │
│ │ payment_status          │      │ order_id (FK)            │     │
│ │ payment_reference       │      │ tenant_id (FK, SHARD)    │     │
│ │ allocated_node_id       │      │ carrier_id               │     │
│ │ allocation_rule         │      │ service_level            │     │
│ │ carrier_id              │      │ tracking_number          │     │
│ │ tracking_number         │      │ label_url                │     │
│ │ label_url               │      │ label_format             │     │
│ │ promised_delivery       │      │ voided (bool)            │     │
│ │ requested_delivery      │      │ rate (JSONB)             │     │
│ │ created_at              │      │ cost_components (JSONB)  │     │
│ │ updated_at              │      │ origin_node_id           │     │
│ │ shipped_at              │      │ estimated_delivery       │     │
│ │ delivered_at            │      │ actual_delivery          │     │
│ │ metadata (JSONB)        │      │ status                   │     │
│ └─────────────────────────┘      │ created_at               │
│                                   │ shipped_at                │
│ nx_inventory                      │ manifest_closed_at        │
│ ┌─────────────────────────┐      └──────────────────────────┘     │
│ │ id (UUID PK)             │                                        │
│ │ tenant_id (PK, SHARD)   │      nx_tracking_event                 │
│ │ sku                      │      ┌──────────────────────────┐     │
│ │ node_id                  │      │ id (UUID PK)             │     │
│ │ quantity_on_hand         │      │ shipment_id (FK)         │     │
│ │ quantity_allocated       │      │ event_type               │     │
│ │ quantity_reserved        │      │ location                 │     │
│ │ quantity_in_transit      │      │ timestamp                │     │
│ │ quantity_on_order        │      │ description              │     │
│ │ quantity_damaged         │      │ raw_data (JSONB)         │     │
│ │ quantity_on_hold         │      └──────────────────────────┘     │
│ │ safety_stock             │                                        │
│ │ reorder_point            │      nx_return                        │
│ │ reorder_qty              │      ┌──────────────────────────┐     │
│ │ atp (computed)           │      │ id (UUID PK)             │     │
│ │ lot_number               │      │ order_id (FK)            │     │
│ │ serial_number            │      │ customer_id              │     │
│ │ expiry_date              │      │ reason                   │     │
│ │ last_counted_at          │      │ grade                    │     │
│ │ created_at               │      │ disposition              │     │
│ │ updated_at               │      │ carrier_id               │     │
│ └─────────────────────────┘      │ tracking_number           │     │
│                                   │ label_url                 │     │
│ nx_nodes                          │ refund_amount             │     │
│ ┌─────────────────────────┐      │ refund_reference          │     │
│ │ id (UUID PK)             │      │ status                    │     │
│ │ tenant_id (PK, SHARD)   │      │ inspected_at              │     │
│ │ name                     │      │ created_at                │     │
│ │ type (WH/STORE/3PL)     │      │ updated_at                │     │
│ │ address (JSONB)          │      └──────────────────────────┘     │
│ │ latitude                 │                                        │
│ │ longitude                │      nx_audit_log                     │
│ │ is_active                │      ┌──────────────────────────┐     │
│ │ capacity_daily           │      │ id (UUID PK)             │     │
│ │ cut_off_time             │      │ tenant_id (FK, SHARD)    │     │
│ │ carrier_config (JSONB)   │      │ entity_type              │     │
│ │ created_at               │      │ entity_id                │     │
│ │ updated_at               │      │ event_type               │     │
│ └─────────────────────────┘      │ actor_id                 │     │
│                                   │ actor_type               │     │
│ nx_carrier_account                │ data (JSONB)             │     │
│ ┌─────────────────────────┐      │ created_at               │     │
│ │ id (UUID PK)             │      └──────────────────────────┘     │
│ │ tenant_id (PK, SHARD)   │                                        │
│ │ carrier_id               │      nx_rate_cache                    │
│ │ account_number (encrypt) │      ┌──────────────────────────┐     │
│ │ meter_number (encrypt)   │      │ cache_key (SHA-256 PK)   │     │
│ │ api_key (encrypted)      │      │ origin_zip               │     │
│ │ api_secret (encrypted)   │      │ dest_zip                 │     │
│ │ negotiated_discount      │      │ weight_lb                │     │
│ │ contract_effective       │      │ dims_hash                │     │
│ │ contract_expiry          │      │ rates (JSONB)            │     │
│ │ is_active                │      │ created_at + 15min (TTL) │     │
│ │ node_id (nullable)       │      └──────────────────────────┘     │
│ │ created_at               │                                        │
│ │ updated_at               │      nx_inventory_reservation          │
│ └─────────────────────────┘      ┌──────────────────────────┐     │
│                                   │ id (UUID PK)             │     │
│                                   │ order_id                 │     │
│                                   │ tenant_id                │     │
│                                   │ sku                      │     │
│                                   │ node_id                  │     │
│                                   │ quantity                 │     │
│                                   │ status (ACTIVE/EXPIRED)  │     │
│                                   │ expires_at (TTL)         │     │
│                                   │ created_at               │     │
│                                   └──────────────────────────┘     │
└────────────────────────────────────────────────────────────────────┘
```

### 7.3 Indexing Strategy

| Table | Index | Type | Purpose |
|-------|-------|------|---------|
| nx_order | (tenant_id, status) | B-tree | Dashboard queries by status |
| nx_order | (tenant_id, created_at DESC) | B-tree | Recent order listing |
| nx_order | (tenant_id, channel_order_id) | UNIQUE B-tree | Idempotency check |
| nx_order | (tenant_id, customer_email) | B-tree | Customer lookup |
| nx_order | (external_id, erp_system) | UNIQUE B-tree | ERP deduplication |
| nx_order_item | (order_id) | B-tree | Order items query |
| nx_shipment | (order_id) | B-tree | Shipment lookup |
| nx_shipment | (tracking_number) | UNIQUE B-tree | Tracking lookup |
| nx_shipment | (tenant_id, carrier_id, shipped_at) | B-tree | Carrier analytics |
| nx_inventory | (tenant_id, sku, node_id) | UNIQUE B-tree | ATP computation |
| nx_inventory | (tenant_id, node_id) | B-tree | Node inventory view |
| nx_tracking_event | (shipment_id, timestamp) | B-tree | Tracking timeline |
| nx_return | (order_id) | B-tree | Return lookup |

### 7.4 ClickHouse Analytics Schema

```sql
-- Order analytics materialized view (hourly rollup)
CREATE TABLE analytics.orders_hourly (
  tenant_id String,
  hour DateTime,
  channel String,
  status String,
  fulfillment_type String,
  order_count Int64,
  total_revenue Decimal(18,2),
  total_shipping Decimal(18,2),
  avg_order_value Decimal(18,2)
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (tenant_id, hour, channel);

-- Carrier performance (daily per-lane)
CREATE TABLE analytics.carrier_daily (
  tenant_id String,
  date Date,
  carrier_id String,
  origin_zip String,
  dest_zip String,
  service_level String,
  shipment_count Int64,
  on_time_count Int64,
  delayed_count Int64,
  damaged_count Int64,
  avg_cost Decimal(18,2),
  total_cost Decimal(18,2),
  avg_transit_days Float64
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (tenant_id, date, carrier_id);
```

### 7.5 Redis Cache Strategy

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `rate:{sha256(origin,dest,weight,dims)}` | String (JSON) | 15 min | Cached rate quotes |
| `inventory:hold:{orderId}:{sku}:{nodeId}` | String | 15 min | Soft inventory hold |
| `inventory:atp:{sku}:{nodeId}` | String | 5 sec | Real-time ATP |
| `session:{token}` | Hash | 24 hr | User session |
| `idempotency:{sha256(key)}` | String | 24 hr | Idempotency key store |
| `carrier:performance:{carrierId}:{zone}` | String (JSON) | 1 hr | ML feature cache |
| `tenant:config:{tenantId}` | Hash | 5 min | Tenant configuration |
| `rate:lock:{orderId}` | String | 30 sec | Rate request mutex |

---

## 8. ML/AI Engine

### 8.1 Model Portfolio Overview

| Model | Algorithm | Training Data | Retrain Cycle | Latency SLA | Priority |
|-------|-----------|--------------|---------------|-------------|----------|
| Carrier Selection Optimizer | XGBoost Ranker | 6mo historical shipments (200K+) | Weekly | <50ms inference | P0 |
| Delivery Success Predictor | XGBoost Classifier | Tracking events + carrier OTD data | Weekly | <20ms | P1 |
| Surcharge Predictor | Tabular Neural Network | Historical invoice line items | Bi-weekly | <30ms | P1 |
| Dimensional Weight Predictor | Random Forest | SKU catalog + packing history | Monthly | <20ms | P1 |
| Invoice Anomaly Detector | Isolation Forest | Carrier invoices vs quoted rates | Daily | <100ms (batch) | P1 |
| Demand Forecaster | Temporal Fusion Transformer | 3yr+ sales history (daily) | Weekly | <200ms | P2 |
| Inventory Optimizer | XGBoost Classifier | Demand forecasts + lead times + stock levels | Weekly | <50ms | P2 |
| HS Code Classifier | Fine-tuned BERT | 500K classified shipments | Monthly | <100ms | P2 |
| Order Routing Optimizer | XGBoost Classifier | Order history + node performance | Weekly | <30ms | P0 |

### 8.2 Carrier Selection Optimizer (Core ML Model)

```python
class CarrierScoringModel:
    """XGBoost ranker for optimal carrier selection per shipment."""

    def __init__(self):
        self.model = XGBRanker(
            objective="rank:pairwise",
            n_estimators=800,
            max_depth=6,
            learning_rate=0.05,
            subsample=0.80,
            colsample_bytree=0.80,
            reg_alpha=0.10,
            reg_lambda=1.00,
            tree_method="gpu_hist"
        )

    def build_feature_vector(self, carrier, shipment, rates, surcharge_model):
        """Construct feature vector for a carrier-shipment pair."""
        rf = self._engineer_route_features(shipment.origin_zip, shipment.dest_zip)
        tf = self._engineer_temporal_features(shipment.ship_date)
        cf = self._engineer_carrier_features(carrier.id, rf.zone, shipment.customer_id)
        sp = surcharge_model.predict(carrier, shipment, rf)
        cost = rates[carrier.id].base_rate + sp.total_surcharges

        return [
            cost,                    # Total predicted landed cost
            cf.lane_otd_30d,         # 30-day on-time delivery (this lane)
            cf.lane_otd_90d,         # 90-day on-time delivery (this lane)
            cf.cust_otd_90d,         # 90-day OTD for this customer
            cf.damage_rate,          # Damage rate (all lanes)
            cf.exception_rate,       # Exception rate (this lane)
            cf.billing_acc,          # Invoice accuracy rate
            rf.zone,                 # Carrier zone (1-8)
            rf.distance_mi,          # Geodesic distance in miles
            rf.das_flag_encoded,     # 0=None, 1=DAS, 2=Extended
            tf.peak_active,          # Peak season binary flag
            tf.is_holiday,           # Holiday binary flag
            shipment.weight_lb,      # Actual weight
            shipment.dim_weight_lb,  # Dimensional weight
            shipment.service_req,    # Encoded service level
            carrier.reliability_tier # 1-5 from scorecard
        ]

    def rank_carriers(self, shipment, eligible_carriers, rates, surcharge_model):
        """Rank all eligible carriers for a shipment."""
        feature_matrix = [
            self.build_feature_vector(c, shipment, rates, surcharge_model)
            for c in eligible_carriers
        ]
        scores = self.model.predict(feature_matrix)
        ranked = sorted(zip(eligible_carriers, scores), key=lambda x: x[1], reverse=True)
        return [
            CarrierRecommendation(
                carrier=c,
                score=s,
                total_landed_cost=rates[c.id].base_rate + ...,
                estimated_delivery=c.transit_days + shipment.ship_date,
                otd_probability=self.delivery_success_model.predict(c, shipment)
            )
            for c, s in ranked
        ]
```

### 8.3 ML Training Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  RAW DATA    │───▶│  FEATURE     │───▶│  MODEL       │───▶│  DEPLOY      │
│  PostgreSQL  │    │  ENGINEERING │    │  TRAINING    │    │  MLflow      │
│  ClickHouse  │    │  Feast       │    │  (GPU/CPU)   │    │  Model Registry
│  S3 Parquet  │    │  Spark Jobs  │    │  Airflow DAG │    │  KServe      │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
       │                    │                    │                    │
       ▼                    ▼                    ▼                    ▼
  • Carrier invoices  • Temporal features   • XGBoost          • A/B testing
  • Tracking events   • Route features      • PyTorch (TFT)    • Shadow scoring
  • Order history     • Carrier features    • Scikit-learn     • Canary deploy
  • Rate history      • Customer features   • Isolation Forest • Auto-rollback
  • Return data       • SKU features        • BERT             • Monitoring
```

### 8.4 Feature Store (Feast)

```yaml
# feature_store.yaml
project: nexusship
registry: s3://nexusship-feast-registry/
provider: aws
online_store:
  type: redis
  connection_string: "nexusship-redis.redis.cache.windows.net:6380"

# Feature View: Carrier Performance
feature_view:
  name: carrier_performance
  entities:
    - carrier_id
    - lane_zone
  features:
    - name: lane_otd_30d
      dtype: FLOAT
      description: "30-day rolling on-time delivery rate for this carrier-lane"
    - name: lane_otd_90d
      dtype: FLOAT
    - name: damage_rate
      dtype: FLOAT
    - name: exception_rate
      dtype: FLOAT
    - name: billing_accuracy
      dtype: FLOAT
  batch_source:
    type: clickhouse
    table: analytics.carrier_daily_rollup
    timestamp_field: date
  online: true
```

---

## 9. UX & UI Architecture

### 9.1 Design Philosophy

NexusShip's UI draws inspiration from the best elements of each competitor:
- **From ShipBob**: Clean, visual-first dashboard with intuitive navigation — ops staff can onboard without documentation
- **From Shopify Admin**: Familiar commerce-admin patterns (order list, customer detail, fulfillment actions) that eCommerce operators already know
- **From Manhattan Associates**: Enterprise data density for logistics managers who need to see many orders and exceptions simultaneously

### 9.2 Design System Principles

| Principle | Application |
|-----------|------------|
| **Progressive disclosure** | Show overview first, reveal complexity on demand |
| **Data density with clarity** | Show 50+ orders in a table without visual noise |
| **Exception-first** | Surface what needs attention; green means "ignore" |
| **Batch operations** | Every action should work on 1 or 1,000 orders |
| **Keyboard-first** | Power users should never touch mouse for common tasks |
| **Mobile-responsive** | Field staff need mobile-first BOPIS/SFS tools |

### 9.3 Navigation Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  NexusShip                                                         │
│  ┌───────┬──────────────────────────────────────────────────────┐  │
│  │       │ [Search orders, SKUs, customers...]                  │  │
│  │       │                                                      │  │
│  │ 📊    │  MAIN CONTENT AREA                                   │  │
│  │ Dash- │                                                      │  │
│  │ board │                                                      │  │
│  │       │                                                      │  │
│  │ 📦    │                                                      │  │
│  │ Orders│                                                      │  │
│  │       │                                                      │  │
│  │ 📋    │                                                      │  │
│  │ Inven-│                                                      │  │
│  │ tory  │                                                      │  │
│  │       │                                                      │  │
│  │ 🚚    │                                                      │  │
│  │ Fulf- │                                                      │  │
│  │ ill-  │                                                      │  │
│  │ ment  │                                                      │  │
│  │       │                                                      │  │
│  │ ↩️    │                                                      │  │
│  │ Re-   │                                                      │  │
│  │ turns │                                                      │  │
│  │       │                                                      │  │
│  │ 🚛    │                                                      │  │
│  │ Car-  │                                                      │  │
│  │ riers │                                                      │  │
│  │       │                                                      │  │
│  │ 🔗    │                                                      │  │
│  │ Chan- │                                                      │  │
│  │ nels  │                                                      │  │
│  │       │                                                      │  │
│  │ 🏪    │                                                      │  │
│  │ Stores│                                                      │  │
│  │       │                                                      │  │
│  │ 📈    │                                                      │  │
│  │ Analy-│                                                      │  │
│  │ tics  │                                                      │  │
│  │       │                                                      │  │
│  │ ⚙️    │                                                      │  │
│  │ Set-  │ Exception  │ Network    │ Recent    │               │  │
│  │ tings │ Queue (3)  │ Map        │ Activity  │               │  │
│  │       │            │            │           │               │  │
│  └───────┴──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.4 Key Screen Specifications

#### 9.4.1 Command Center Dashboard

| Component | Type | Detail |
|-----------|------|--------|
| KPI Tiles | Top row (5) | Orders Today, OTD%, Shipping Cost vs Budget, Active Exceptions, Carrier Utilization |
| Order Velocity Chart | Line chart | Hourly volume last 24h, overlaid with fulfillment throughput |
| Network Map | Interactive | Geo view of all fulfillment nodes with health (green/orange/red) |
| Exception Queue | Right panel | Prioritized by SLA urgency, one-click resolve |
| Carrier Scorecard | Mini card | Current week OTD% for top 3 carriers with trend |
| Activity Feed | Bottom | Last 20 events with actor, timestamp, action |

#### 9.4.2 Order Management Screen

| Component | Detail |
|-----------|--------|
| Data Table | Order ID, Channel icon, Customer, Status badge, Items count, Destination, Carrier, Promised Delivery, Exception flag |
| Smart Filter | Channel, Status, Fulfillment Type (Ship/BOPIS/SFS), Carrier, Date Range, Exception Type |
| Batch Toolbar | Book Shipment, Print Labels, Reassign Node, Cancel |
| Status Colors | Blue=processing, Orange=exception, Green=shipped on-time, Red=late/overdue |
| Detail Drawer | Click row → full detail (timeline, tracking, communications) without leaving list |
| Quick Actions | Print Label, Track, Reassign, Cancel — visible on row hover |

#### 9.4.3 BOPIS Staff Mobile App

| Component | Detail |
|-----------|--------|
| Home Screen | Pending pickup count badge, urgency-sorted order list with countdown timers |
| Order Detail | Item photos, pick location in store, barcode scan verification |
| Action Buttons | "Mark Ready", "Handoff Complete", "Item Not Found" |
| Curbside Mode | Customer QR scan / license plate entry → staff brings order to parking |
| Offline Mode | Orders cached locally; syncs on connectivity restore |

#### 9.4.4 Analytics & BI Dashboard

| Component | Detail |
|-----------|--------|
| Carrier Scorecard | Per-carrier OTD%, damage rate, invoice accuracy, cost/shipment — filterable by lane, date range, customer segment |
| Cost Breakdown Waterfall | Total shipping spend decomposed: base rate + fuel + DAS + residential + handling + other |
| Lane Analysis | Top 20 origin-destination pairs by volume and cost |
| Returns Analytics | Return rate by SKU, reason taxonomy breakdown, avg processing cost, claim recovery rate |
| Executive Summary | One-click PDF export for monthly supply chain review |

### 9.5 User Personas

| Persona | Role | Key Needs | Screen Usage |
|---------|------|-----------|--------------|
| **Sarah** | VP Supply Chain | Strategic view, carrier performance, cost trends, executive reporting | Dashboard, Analytics, Carrier Scorecards |
| **Marcus** | Warehouse Manager | Pick/pack workflow, wave management, exception handling, staff coordination | Fulfillment, Orders, Inventory |
| **Priya** | Store Manager | BOPIS queue, SFS capacity, curbside pickup, in-store inventory | Stores, Orders, Inventory |
| **Alex** | Customer Service | Order lookup, status updates, return processing, exception resolution | Orders, Returns, Customer Detail |
| **Raj** | Ops Director | Daily order flow, carrier selection, cost optimization, SLA monitoring | Dashboard, Orders, Carriers |
| **Maria** | Logistics Analyst | Carrier performance analysis, lane optimization, cost benchmarking | Analytics, Carriers, Custom Reports |
| **David** | IT/Integration | API keys, connector config, webhook management, integration health | Settings, Channels, Integrations |

### 9.6 User Journeys

**Journey 1: New Order Lifecycle (Operations Team)**
1. Open Dashboard → see Orders Today = 847, up 12% WoW
2. Notice 14 exceptions flagged → click Exception Queue
3. See 3 "Inventory Short" exceptions → bulk assign to alternate nodes
4. See 2 "Address Validation" exceptions → click, correct addresses inline
5. Wave 500 orders for 2pm carrier cutoff → click "Wave Fulfillment"
6. System runs ML allocation → shows routing decisions with confidence scores
7. Approve wave → pick/pack instructions pushed to WMS
8. Monitor in Fulfillment screen → see real-time pick progress
9. Labels auto-generate → carrier manifests close
10. Dashboard updates: Exceptions cleared, OTD% holds at 97.3%

**Journey 2: BOPIS (Store Team)**
1. Staff opens mobile app → see 7 pending pickups
2. Taps order with 15min countdown → see item location in store
3. Scans each item barcode → marks as picked
4. Prepares bag → taps "Mark Ready"
5. Customer arrives → taps "Curbside Pickup" mode
6. Enters customer's license plate → app matches, notifies customer
7. Brings order to curb → taps "Handoff Complete"
8. System updates inventory, triggers feedback survey

**Journey 3: Returns Optimization (Analyst)**
1. Maria opens Returns Analytics → sees return rate trending up to 8.3%
2. Filters by SKU → "Premium Wireless Earbuds" has 14.2% return rate
3. Drills to reason breakdown → "Connectivity issues" = 62% of returns
4. Flags to product team via integrated Slack notification
5. Sets auto-disposition rule: "Earbuds, Grade B → Refurbish"

---

## 10. Implementation Roadmap

### 10.1 Phase Overview

```
Phase 0: Foundation     │████████████████████                        │ Months 1-3
Phase 1: Core Platform  │████████████████████████████████████████     │ Months 4-9
Phase 2: Enterprise     │████████████████████████████████████████████ │ Months 10-18
Phase 3: Intelligence   │████████████████████████████████████████████ │ Months 19-27
Phase 4: Scale          │████████████████████████████████████████████ │ Months 28-36
```

### 10.2 Phase 0: Foundation (Months 1-3)

**Infrastructure:**
- Provision EKS (us-east-1) + GKE (us-central1 for DR/GPU)
- Deploy PostgreSQL (Aurora + Citus), Redis (ElastiCache), Kafka (MSK), ClickHouse
- CI/CD: GitHub Actions + ArgoCD + GitOps
- Secrets: HashiCorp Vault + AWS KMS
- Observability: Prometheus + Grafana + Jaeger + ELK + PagerDuty
- Define CDM v1.0 as OpenAPI 3.1 spec
- Multi-tenant isolation: RLS + schema separation

**Hiring (Phase 0):**
- 2x Staff Backend Engineer (Java/Spring Boot)
- 1x ML Engineer (Python/XGBoost/PyTorch)
- 1x Frontend Lead (React/TypeScript)
- 1x DevOps Engineer (K8s/Terraform)
- 1x Product Manager
- 1x Tech Lead/Architect

### 10.3 Phase 1: Core Platform (Months 4-9)

**Rate Engine V1:**
- Direct carrier integrations: UPS (REST OAuth 2.0), FedEx (REST OAuth 2.0), USPS (REST), DHL Express (API Key), EasyPost fallback
- Parallel fanout: async worker pool, per-carrier circuit breakers, 3s timeout with cached degradation
- Rate normalization → canonical Rate model with itemized surcharges
- Surcharge prediction V1 (rules-based): DAS ZIP lookup, residential flag
- Rate cache: Redis 15-min TTL, target 60% hit rate
- Core API: `POST /v1/rates` → ranked carrier list with cost breakdowns

**ERP Connectors V1:**
- NetSuite: SuiteApp (UserEvent on Item Fulfillment + REST API) + SuiteTalk SOAP fallback
- D365 BC: AL extension (AppSource), OData v2 REST
- Integration Gateway: idempotency, exponential backoff retry, DLQ + PagerDuty
- CDM Transformer: YAML-based per-ERP field mapping

**Label Generation & Booking:**
- `POST /v1/shipments` → carrier Ship API → tracking + label S3 URL
- PDF 4x6 thermal, ZPL, EPL2 formats
- Void: `POST /v1/shipments/{id}/void`

**Order Management V1:**
- Order ingestion from Shopify, BigCommerce, Magento webhooks
- DOM engine V1 (rule-based: proximity, cost, inventory balance)
- BOPIS flow V1 (pickup queue, SMS notification, staff confirmation)
- Status state machine (DRAFT→CONFIRMED→ALLOCATED→SHIPPED→DELIVERED)

### 10.4 Phase 2: Enterprise (Months 10-18)

**SAP Integration (M10-15):**
- M10-11: JCo BAPI for ECC/S/4HANA on-prem (Outbound Delivery → Shipment Document)
- M12-13: BTP OData adapter for S/4HANA Cloud (XSUAA auth)
- M14: IDoc/ALE adapter (DESADV/SHPMNT) via AS2/SFTP
- M14-15: SAP App Center certification

**Oracle Integration (M10-14):**
- M10-12: Fusion Cloud REST + OIC adapter (event-driven shipment triggers)
- M13-14: EBS 12.2.x SOAP adapter via Integrated SOA Gateway
- M14: Oracle Cloud Marketplace listing

**ML Models V1 (M12-18):**
- M12: Feast feature store: carrier performance metrics, route statistics
- M13: Carrier Selection Optimizer V1 (XGBoost ranker, 200K shipments)
- M14: Delivery Success Predictor V1 (gradient boosted classifier)
- M15: Surcharge Predictor V1 (tabular NN)
- M16: Invoice Anomaly Detector V1 (Isolation Forest)
- M17: ML monitoring dashboard (accuracy, drift, data quality)

**Compliance Module V1 (M14-18):**
- M14-16: HS Code Classifier (fine-tuned BERT, 500K classifications)
- M16-17: Landed Cost Calculator (50 top international lanes)
- M17-18: Restricted Party Screening (OFAC/BIS real-time)

**Expanded Connectors:**
- Shopify: FulfillmentOrder API for native DOM integration
- BigCommerce: Full order/shipment/inventory sync
- Magento: REST + GraphQL integration
- CommerceTools: Messages API subscription
- ShipBob: Order push + inventory sync + fulfillment status pull
- ShipHero: Order creation + warehouse webhooks

### 10.5 Phase 3: Intelligence (Months 19-27)

- Demand Forecasting TFT: per-customer, per-SKU, 28-day quantile forecasts
- Dimensional Weight Predictor (Random Forest, target 60% DIM reduction)
- Wave Planning Optimizer: bulk carrier optimization for 10,000+ orders
- Multi-Origin Fulfillment Optimizer: omnichannel SFS (3-5 origin evaluation)
- 3PL Module: multi-client carrier accounts, per-client markup, white-label
- Returns Optimization: ML return carrier selection, drop-off vs pickup
- International carrier expansion: 50+ additional carriers (Yamato, Royal Mail, Australia Post, Canada Post)
- Advanced Analytics V2: carrier benchmarking, lane profitability, carbon tracking, churn prediction

### 10.6 Phase 4: Scale (Months 28-36)

- LTL Optimization: NMFC classification, freight class optimization, 50+ LTL carriers
- Ocean/Air Freight: Flexport API integration
- Carrier Volume Aggregation: cross-customer pooling, negotiated tier optimization
- Marketplace Integrations: Amazon Buy Shipping, Walmart GoLocal, Target Plus
- Global Regulatory: CBSA (Canada), HMRC (UK), EU OSS VAT, India IGST
- White-Label/OEM: Partner program for WMS ISVs, ERP extensions (revenue share)

### 10.7 Hiring Plan (Full)

| Phase | Roles | Headcount |
|-------|-------|-----------|
| **P0** | Backend (2), ML (1), Frontend (1), DevOps (1), PM (1), Architect (1) | 7 |
| **P1** | Backend (3), ML (1), Frontend (2), QA (1), TPM (1), Solutions Eng (1) | +9 |
| **P2** | Backend (2), ML (2), Frontend (1), QA (1), Customer Success (2), Sales (2) | +10 |
| **P3** | ML (2), Backend (2), Frontend (2), QA (1), CS (2), Sales (3), Marketing (2) | +14 |
| **P4** | Platform (3), SRE (2), International (2), Sales (5), CS (3) | +15 |
| **Total** | | **55** |

---

## 11. Go-To-Market Strategy

### 11.1 Ideal Customer Profiles

**ICP 1 — Mid-Market E-Commerce & Omnichannel Retailer (PRIMARY)**
- Revenue: $10M-$500M
- Shipping: 500-50,000 shipments/day
- ERP: NetSuite or D365 BC
- WMS: ShipHero, Deposco, Extensiv, or custom
- Pain: Overpaying on carrier rates, no landed cost visibility, manual carrier selection, billing disputes
- Budget: $50K-$300K/year
- Buyer: VP Operations, Director of Logistics, CFO (>$150K)

**ICP 2 — Enterprise Manufacturer/Distributor (SECONDARY)**
- Revenue: $500M+
- Shipping: 10,000-200,000 shipments/day (parcel + LTL)
- ERP: SAP S/4HANA or Oracle Fusion
- WMS: Manhattan, Blue Yonder, SAP EWM
- Pain: SAP LE-TRA limitations, cross-border compliance gaps, no carrier analytics
- Budget: $300K-$2M/year
- Buyer: VP Supply Chain, Director Global Logistics, CIO

**ICP 3 — Third-Party Logistics Provider / 3PL (TERTIARY)**
- Revenue: $5M-$200M logistics revenue
- Clients: 10-200
- Pain: Per-client carrier accounts, no unified billing, cannot demonstrate quantified ROI
- Budget: $30K-$150K/year
- Buyer: Owner/CEO, VP Operations

### 11.2 Pricing Model

| Tier | Price | Included | Target |
|------|-------|----------|--------|
| **Free** | $0 | 500 shipments/mo, rate shopping only, no ERP | PLG acquisition |
| **Starter** | $500/mo | 5,000 shipments, 2 ERP connectors, basic analytics | Small businesses |
| **Growth** | $2,500/mo | 25,000 shipments, 5 ERP, carrier scorecards, API access | Mid-market |
| **Scale** | $15,000/mo | 250,000 shipments, unlimited ERP, ML optimization, all analytics | Enterprise |
| **Enterprise** | Custom | Unlimited, dedicated infra, SLA 99.99%, white-label option | ICP 2 |

**Value-Based ROI Example:** $50M revenue, 10% shipping burden ($5M spend), saves 24% = $1.2M/yr. Scale tier at $180K/yr = 6.7x ROI Year 1.

### 11.3 Sales Motion

**Enterprise (>$100K ACV):** Inbound/outbound → Discovery → Technical qualification → Free cost analysis (90-day data) → 30-day POC → Business case → Legal/procurement → Close. Cycle: 30-90 days.

**PLG (<$50K ACV):** Free tier (500 shipments) → Self-serve onboarding (10 min to first rate) → In-product upgrade trigger ("Connect NetSuite") → 34% conversion at trigger → 94% retention with ERP integration.

### 11.4 Channel Strategy

- **Tier 1**: Systems integrators (Accenture, Deloitte, Wipro) — SAP/Oracle implementation partners
- **Tier 2**: ERP resellers (NetSuite Solution Providers, D365 VARs) — bundle NexusShip with ERP
- **Tier 3**: WMS ISVs (Extensiv, Deposco, ShipHero) — embedded rate engine via API

### 11.5 Marketing Strategy

- **Content**: Annual "State of Shipping Optimization" report, carrier rate change tracker (free tool), shipping savings ROI calculator
- **Target Publications**: DC Velocity, Inbound Logistics, Supply Chain Dive, Logistics Management
- **Conferences**: MODEX, CSCMP Edge, SAP Sapphire, Oracle CloudWorld, NetSuite SuiteConnect, NRF
- **Demand Gen**: LinkedIn ABM targeting VP Ops/Supply Chain at $10M-$500M revenue companies

---

## 12. Financial Model

### 12.1 Three-Year Revenue Projection

| Metric | Year 1 | Year 2 | Year 3 |
|--------|--------|--------|--------|
| Customers | 25 | 120 | 400 |
| Avg ACV | $65K | $85K | $110K |
| Net Revenue | $1.6M | $10.2M | $44M |
| Gross Margin | 65% | 72% | 78% |
| Operating Expenses | $3.5M | $7.5M | $15M |
| EBITDA | -$1.9M | $2.7M | $29M |
| ARR | $1.6M | $10.2M | $44M |
| NRR | — | 115% | 120% |
| CAC | $45K | $38K | $32K |
| LTV/CAC | 3.2x | 5.8x | 8.5x |

### 12.2 Unit Economics

| Metric | Year 1 | Year 2 | Year 3 |
|--------|--------|--------|--------|
| Avg shipments/customer/mo | 35,000 | 85,000 | 150,000 |
| Avg revenue/shipment | $0.15 | $0.10 | $0.08 |
| Infrastructure cost/shipment | $0.05 | $0.03 | $0.02 |
| Carrier API cost/shipment | $0.02 | $0.015 | $0.01 |
| Gross profit/shipment | $0.08 | $0.055 | $0.05 |
| Monthly churn | 3.5% | 2.0% | 1.2% |
| Customer lifetime (months) | 28 | 50 | 83 |

### 12.3 Funding Requirements

| Round | Amount | Timeline | Purpose |
|-------|--------|----------|---------|
| Seed | $3M | Pre-launch | Phase 0 + Phase 1 (7 hires, infra) |
| Series A | $10M | End Year 1 | Phase 2 + sales team (10 new hires) |
| Series B | $25M | End Year 2 | Phase 3 + international expansion (20 hires) |
| Series C | $50M | End Year 3 | Phase 4 + global scale (20 hires + M&A) |

---

## 13. Security & Compliance

### 13.1 Architecture Security

| Layer | Controls |
|-------|----------|
| **Network** | VPC isolation, private subnets for data plane, WAF (CloudFront/Kong), DDoS protection, egress filtering |
| **API** | OAuth 2.0 + JWT authentication, rate limiting (per-tenant, per-endpoint), API key rotation, HMAC webhook verification |
| **Service Mesh** | mTLS between all services, Istio authorization policies, network policies (K8s NetworkPolicy) |
| **Data** | AES-256 at rest (RDS, S3, Elasticache), TLS 1.3+ in transit, KMS with customer-managed CMK option |
| **Secrets** | HashiCorp Vault for carrier credentials, automatic rotation, audit logging of all access |
| **Audit** | Immutable audit log (append-only), tamper-evident (S3 Object Lock), 7-year retention |
| **Identity** | RBAC with least privilege, SSO/SAML/SCIM provisioning, MFA enforcement for admin users |
| **Infrastructure** | EKS pod security policies, container image scanning (Trivy), IaC scanning (Checkov), SAST/DAST pipeline |

### 13.2 Compliance Certifications

| Standard | Scope | Timeline |
|----------|-------|----------|
| SOC 2 Type II | Security, Availability, Confidentiality | End Year 1 |
| GDPR | EU customer PII handling, data residency | Phase 0 (by design) |
| CCPA | California right-to-deletion, 30-day purge | Phase 0 (by design) |
| PCI DSS | No cardholder data storage; carrier credential encryption | Phase 1 |
| ITAR/EAR | Export-controlled shipment screening, BIS Denied Party | Phase 2 |
| ISO 27001 | ISMS certification | End Year 2 |

### 13.3 Data Residency

| Region | Data Stores | Use Case |
|--------|-------------|----------|
| US (us-east-1, us-west-2) | Primary | US customers |
| EU (eu-west-1, eu-central-1) | Replicated | GDPR tenants |
| APAC (ap-southeast-1) | Replicated | APAC customers |

### 13.4 Encryption Strategy

- **At rest**: AES-256 (RDS, S3, ElastiCache, MSK, ClickHouse)
- **In transit**: TLS 1.3+ for all external + internal traffic
- **Key management**: AWS KMS (automatic key rotation), customer-managed CMK for enterprise tier
- **Field-level**: Carrier account numbers, API keys, credentials encrypted with Vault transit engine

---

## 14. Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| ERP certification delays (SAP, Oracle) | HIGH | HIGH | Start certification process early (Phase 1.5); parallel track with integration development |
| Carrier API breaking changes | MEDIUM | HIGH | API version pinning, sandbox testing pipeline, automated regression suite for top 5 carriers |
| ML model accuracy degradation | MEDIUM | MEDIUM | Weekly retrain cycle, automated drift detection, shadow scoring, fallback to rules-based |
| Multi-tenant data leak | LOW | CRITICAL | RLS enforcement at DB level, tenant isolation test suite, quarterly penetration testing |
| Key engineer departure | MEDIUM | HIGH | Comprehensive ADRs, code reviews enforced, knowledge base, competitive compensation |
| Enterprise sales cycle too long | MEDIUM | MEDIUM | PLG motion for <$50K ACV, free cost analysis as lead gen, 30-day POC with guaranteed ROI projection |
| Carrier rate negotiation failure | LOW | MEDIUM | Aggregator fallback (EasyPost), regional carrier diversification, volume aggregation program |
| International regulatory complexity | HIGH | MEDIUM | Phase 2 compliance module, restricted party screening, per-country regulatory checklist |
| Infrastructure cost overrun | MEDIUM | MEDIUM | Autoscaling with HPA/VPA, spot instance usage for ML training, ClickHouse tiered storage |
| Competition from platform-native features | MEDIUM | MEDIUM | Focus on ERP depth moat, carrier performance data moat, ML accuracy moat — hard to replicate |

---

## Appendix A: Carrier SCAC & API Reference

| Carrier | SCAC | API Base URL | Auth Method | Rate API | Ship API | Key Surcharges |
|---------|------|-------------|-------------|----------|----------|----------------|
| UPS | UPSS | https://onlinetools.ups.com/api | OAuth 2.0 | /rating/v1/Rate | /shipments/v1/ship | DAS, Residential, Fuel, Additional Handling, Peak, Large Pkg |
| FedEx | FXEX | https://apis.fedex.com | OAuth 2.0 | /rate/v1/rates/quotes | /ship/v1/shipments | DAS, Residential, Fuel, Additional Handling, Peak, Oversize |
| DHL Express | DHLE | https://express.api.dhl.com | API Key + Bearer | /rates | /shipments | Residential, Fuel, Remote Area, Dangerous Goods |
| USPS | USPS | https://api.usps.com | API Key | /prices | /shipments | DIM Weight, Cubic, Non-Machinable, Signature |
| DHL eCommerce | DHLE | https://api.dhlecommerce.com | API Key | /rates | /shipments | Fuel, Residential, Extended Area |

## Appendix B: Dimensional Weight Divisors

| Carrier | Divisor | Notes |
|---------|---------|-------|
| UPS | 139 (domestic), 139 (intl) | 166 for retail rates |
| FedEx | 139 (domestic), 139 (intl) | 166 for retail rates |
| DHL Express | 139 (all) | — |
| USPS | 166 (Priority Mail), 166 (Ground Advantage) | Cubic pricing available for <0.5 cu ft |
| DHL eCommerce | 139 | — |

## Appendix C: Key Glossary

| Term | Definition |
|------|------------|
| ATP | Available-to-Promise — inventory available for new orders (on-hand - allocated - reserved) |
| BOPIS | Buy Online, Pick Up In-Store |
| SFS | Ship-From-Store — retail store acts as fulfillment node |
| DOM | Distributed Order Management — multi-node order routing optimization |
| DAS | Delivery Area Surcharge — UPS/FedEx fee for remote ZIP codes |
| CDM | Canonical Data Model — normalized enterprise data schema |
| OTD | On-Time Delivery — percentage of shipments delivered by committed date |
| TFT | Temporal Fusion Transformer — deep learning architecture for multi-horizon forecasting |
| WMS-Lite | Lightweight warehouse management module within NexusShip |
| ERP Connector Hub | Pre-built, certified integration adapters for enterprise systems |
| DIM Weight | Dimensional weight — (L×W×H)/divisor; carrier bills on actual or DIM, whichever is greater |
| NMFC | National Motor Freight Classification — standard for LTL freight class determination |
| ASN | Advanced Shipping Notice (EDI 856) |
| SSCC | Serial Shipping Container Code — barcode standard for pallet/carton identification |

---

*This document represents the complete product blueprint for NexusShip v2.0. It synthesizes research across 30+ supply chain platforms, ERP systems, WMS platforms, and carrier networks into a single unified architecture ready for implementation.*
