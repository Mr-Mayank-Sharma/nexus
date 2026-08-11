# Feature: Import / Export Engine & Signed Bulk Data

> Part of the Nexus OMS documentation set. See [index](../README.md).

## Start here 📥📤

**Import/Export** is *moving lots of data at once*: a CSV of 1,000 products in, a nightly report out. The key idea: this is a **guarded door**, not an open one. Every upload needs a **signed ticket** (token), and every single row gets its own scorecard — "this row worked, that row failed, here's why."

> 📋 **Real life example — the spreadsheet upload:**
> You upload 1,000 products. Nexus validates them row by row: 998 load, 2 fail (one has a bad price, one a missing SKU). You see exactly which 2 rows failed and why — nothing silently disappears. Uploading again with the same ticket doesn't double-load (idempotency).

## Overview
Bulk data movement into and out of the platform: CSV/Excel/JSON imports (orders, products, inventory, invoices, shipments, carriers) and scheduled exports — all gated by **signed import tokens** and fully audited per record.

## Business process
1. `ImportTokenService` issues a signed, expiring token (or ADMIN/OPS uses role-scoped permission).
2. `GenericImportService` validates headers/records → `ImportRecordLog` per row.
3. Loaded records become entities; failures are reported, not swallowed.
4. `ImportExportEngine` also drives exports / scheduled batch delivery (SFTP/API).

## Use cases
- **UC-03** Bulk import orders
- **UC-37** Signed bulk import
- **UC-38** Scheduled export / batch

## Data flow
```mermaid
flowchart LR
    TOKEN[ImportTokenService] --> UP[Upload endpoint]
    UP --> GEN[GenericImportService]
    GEN --> VAL[Per-record validation]
    VAL -->|ok| LOAD[Entity load]
    VAL -->|fail| LOG[ImportRecordLog failed]
    LOAD --> HIST[ImportHistory]
    EXP[ImportExportEngine] --> JOB[Export jobs]
    JOB --> DL[Delivery: SFTP/API/webhook]
```

## Key entities (ER subset)
```mermaid
erDiagram
    IMPORT_HISTORY ||--o{ IMPORT_RECORD_LOG : contains
    IMPORT_HISTORY {
        uuid id PK
        uuid tenant_id
        string source
        string type
        int total_records
        int success_count
        int failed_count
    }
```
Tables: `import_history` · `import_record_log` · `nx_integration_import_jobs` · `nx_integration_export_jobs`

## Who can access what
| Role | Access |
|---|---|
| OPS_MANAGER | Full import (create/edit/delete) |
| WAREHOUSE_MANAGER | Import inventory (view/create) |
| PROCUREMENT_MANAGER | Import products/POs (view/create) |
| FINANCE | Import invoices (view/create) |
| LOGISTICS_MANAGER | Import shipments/carriers (view/create) |
| STORE_MANAGER | View import history |
| CEO | View import history/results |
| VIEWER | View import results |
| ADMIN | Wildcard (everything) |

## Integrity notes
- Tokens are **signed + expiring** — bulk endpoints are not anonymous (no open back doors).
- Idempotency keys prevent double-loading the same file.
- Every record has a status row in `ImportRecordLog` for reconciliation.

> 🧒 **Kid translation of the signed ticket:** Uploading data isn't like dropping a letter in any mailbox — it's a *registered parcel* with a ticket that expires. The post office logs every row, so a bad row can never silently vanish.
