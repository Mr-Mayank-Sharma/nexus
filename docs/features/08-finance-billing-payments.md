# Feature: Finance, Billing & Payments

> Part of the Nexus OMS documentation set. See [index](../README.md).

## Overview
Order-to-cash and procure-to-pay support: invoices, invoice items, payments (Stripe connector), credit memos, freight invoice auditing and financial analytics.

## Business process
1. Shipment/order completion → `NxInvoice` + `NxInvoiceItem`.
2. Customer payment via Stripe → `NxPayments`.
3. Disputes/returns → `NxCreditMemo`.
4. Carrier invoices → freight audit against rate card (see Shipping feature).
5. Analytics aggregate AR, COGS, freight cost, promotion ROI.

## Use cases
- **UC-20** Freight audit
- **UC-28** Invoice & payments
- **UC-29** Promotion usage / ROI
- Credit memos & reconciliation

## Data flow
```mermaid
flowchart LR
    SHIP[Shipment/Order] --> INV[NxInvoice + Items]
    INV --> PAY[NxPayments via Stripe]
    RET[Return] --> CM[NxCreditMemo]
    FINV[Carrier invoice] --> AUDIT[nxFreight_audit_logs]
    INV --> ANA[Analytics]
    PROMO[NxPromotion] --> USAGE[NxPromotionUsage]
```

## Key entities (ER subset)
```mermaid
erDiagram
    NX_ORDERS ||--o{ NX_INVOICES : billed_by
    NX_INVOICES ||--o{ NX_INVOICE_ITEMS : contains
    NX_ORDERS ||--o{ NX_PAYMENTS : paid_by
    NX_PROMOTIONS ||--o{ NX_PROMOTION_USAGE : consumed_by
```
Tables: `nx_invoices` · `nx_invoice_items` · `nx_payments` · `nx_credit_memos` · `nx_promotions` · `nx_promotion_usage` · `nxFreight_invoices` · `nxFreight_invoice_lines` · `nxFreight_audit_logs`

## Who can access what
| Stage | Roles |
|---|---|
| Invoices | FINANCE (create/edit), PROCUREMENT_MANAGER (PO-linked), OPS_MANAGER (view), CEO (view) |
| Payments | FINANCE (create/edit) |
| Credit memos | FINANCE (create/edit) |
| Freight audit | FINANCE (create/edit), LOGISTICS_MANAGER (view) |
| Financial analytics | FINANCE, CEO, VIEWER |
| Import invoices | FINANCE (create) |

## Integrity notes
- Invoice/payment edit is allowed but **delete is not** for FINANCE — a control lineage.
- Freight overcharge detection pairs carrier invoices with the rate card.
