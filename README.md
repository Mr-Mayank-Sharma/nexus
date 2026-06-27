# Nexus — OMS & Supply Chain Platform

A full-stack Order Management System (OMS) and supply chain platform with warehouse management, inventory tracking, carrier rate shopping, returns management, B2B portal, EDI automation, and a generic file import engine.

## Architecture

```
nexus/
├── nexus-oms-backend/          # Spring Boot 3 + Java 17 backend
│   ├── src/main/java/com/nexus/oms/
│   │   ├── controller/         # REST controllers
│   │   ├── service/            # Business logic
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── entity/             # JPA entities (NxOrder, NxProduct, etc.)
│   │   ├── dto/                # Request/response DTOs
│   │   ├── security/           # JWT auth, tenant context
│   │   └── config/             # App configuration
│   ├── src/main/resources/
│   │   ├── application.yml     # Spring config
│   │   └── db/migration/       # Flyway migrations
│   └── target/oms-1.0.0.jar   # Compiled fat JAR
├── nexus-oms-frontend/         # React + Vite + TypeScript frontend
│   ├── src/
│   │   ├── pages/              # Page components
│   │   ├── components/         # Reusable UI components
│   │   ├── api/                # API client modules
│   │   └── hooks/              # React hooks
│   └── package.json
├── supply_chain_ai/            # Python ML pipeline
├── supply_chain_ai2/           # Additional AI modules
├── scripts/                    # Automation & setup scripts
├── deploy/                     # Deployment manifests
├── docs/                       # Documentation & blueprints
└── docker-compose.yml          # PostgreSQL, Redis, Kafka
```

## Tech Stack

| Layer       | Technology                              |
|-------------|-----------------------------------------|
| Backend     | Java 17, Spring Boot 3, Spring Security |
| Frontend    | React 19, Vite, TypeScript, Tailwind CSS|
| Database    | PostgreSQL 16                           |
| Cache       | Redis 7                                 |
| Messaging   | Apache Kafka                            |
| Migrations  | Flyway                                  |
| AI/ML       | Python, scikit-learn, TensorFlow        |

## Quick Start

### Prerequisites

- Java 17 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`)
- Node.js 18+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d postgres redis kafka
```

### 2. Start the backend

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
$JAVA_HOME/bin/java -jar nexus-oms-backend/target/oms-1.0.0.jar \
  --spring.jpa.hibernate.ddl-auto=update
```

The server starts on `http://localhost:8080/api/v1`.

### 3. Start the frontend

```bash
cd nexus-oms-frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:5173`.

### 4. Login

Default credentials (seeded via Flyway) — or register a new account at the login page.

## API Endpoints

### Generic Import Engine

All endpoints require authentication via `Authorization: Bearer <token>` header, except `/import/**` which are publicly accessible.

```bash
# Get supported entity types
GET /api/v1/import/entity-types

# Get supported file formats
GET /api/v1/import/formats

# Import a file (CSV, JSON, XML, EDI)
POST /api/v1/import/{entityType}
  -F "file=@data.csv"
```

**Supported entity types:** `products`, `orders`, `inventory`, `customers`, `shipments`, `returns`, `suppliers`, `purchase-orders`, `invoices`, `warehouses`

**Supported formats:** `csv`, `json`, `xml`, `edi` (X12), `xlsx`

**Example CSV templates are at:**
- `nexus-oms-frontend/product-import-template.csv` (14 products, 21 fields)
- `nexus-oms-frontend/order-import-template.csv` (14 order rows, 12 fields)

### Core API

| Endpoint              | Description                |
|-----------------------|----------------------------|
| `/auth/**`            | Authentication (login)     |
| `/orders/**`          | Order CRUD & lifecycle     |
| `/inventory/**`       | Inventory management       |
| `/shipments/**`       | Shipment tracking          |
| `/returns/**`         | Returns & RMA              |
| `/analytics/**`       | Business analytics         |
| `/webhooks/**`        | External webhooks          |
| `/ai/**`              | AI predictions             |

## File Import Engine

The generic import engine in `GenericImportService` parses uploaded files into structured records. It supports:

- **CSV** — quoted fields, configurable delimiters
- **JSON** — arrays and object wrappers (`data`, `records`, `items`)
- **XML** — simple tag-based record extraction
- **EDI X12** — ST/BEG/N1/PO1/SE segment parsing (850, 856, 810)
- **XLSX** — Excel spreadsheet support

All parsing returns a structured `ImportResult` with:
- `totalRecords` / `successCount` / `errorCount`
- Per-row `warnings` and `errors`
- `processingTimeMs`

## Project Status

- Backend compiled as fat JAR (`oms-1.0.0.jar`)
- Frontend runs via Vite dev server
- PostgreSQL with Flyway migrations at version 15+
- Kafka consumer groups for order lifecycle events (`order.created`, `order.confirmed`, `order.allocated`, `order.shipped`, `order.delivered`)
- File import engine tested with CSV for orders (14 records) and products (14 records)
