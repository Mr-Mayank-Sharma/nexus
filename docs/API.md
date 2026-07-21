# NexusShip — API Reference

Base URL: `https://your-domain.com/api/v1`

All endpoints require `Authorization: Bearer <token>` header unless marked otherwise.

## Authentication

### POST /auth/login

Authenticate user and receive JWT tokens.

**Request:**
```json
{
  "email": "user@company.com",
  "password": "secure-password"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "uuid",
    "email": "user@company.com",
    "roles": ["ROLE_ORDER_MANAGER"]
  }
}
```

### POST /auth/refresh

Refresh an expired access token.

**Request:**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

### POST /auth/mfa/verify

Verify TOTP MFA code during login.

**Request:**
```json
{
  "tempToken": "temp-token-from-login",
  "code": "123456"
}
```

---

## Orders

### GET /orders

List orders with pagination and filters.

**Query Parameters:**
| Param | Type | Description |
|---|---|---|
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default 20) |
| `status` | string | Filter by status |
| `customerId` | UUID | Filter by customer |
| `sortBy` | string | Sort field |
| `sortDir` | string | `asc` or `desc` |

### GET /orders/{id}

Get order details including items, shipments, and audit trail.

### POST /orders

Create a new order.

**Request:**
```json
{
  "customerId": "uuid",
  "items": [
    {
      "sku": "PROD-001",
      "productName": "Widget A",
      "quantity": 10,
      "unitPrice": 29.99
    }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Austin",
    "state": "TX",
    "zip": "73301",
    "country": "US"
  }
}
```

### POST /orders/{id}/confirm

Confirm an order (moves to CONFIRMED status, triggers allocation).

### POST /orders/{id}/allocate

Allocate inventory for order items.

### POST /orders/{id}/ship

Ship an order. Requires allocated status.

### POST /orders/{id}/deliver

Mark an order as delivered.

### POST /orders/{id}/cancel

Cancel an order.

### POST /orders/{id}/modify

Modify order items or shipping address (requires PENDING status).

### POST /orders/{id}/split

Split order into multiple shipment groups.

### POST /orders/{id}/merge

Merge multiple orders into one.

---

## Inventory

### GET /inventory

List inventory with filters.

**Query Parameters:**
| Param | Type | Description |
|---|---|---|
| `sku` | string | Filter by SKU |
| `warehouseId` | UUID | Filter by warehouse |
| `page` | int | Page number |
| `size` | int | Page size |

### GET /inventory/{sku}

Get inventory for a specific SKU.

### GET /inventory/atp/{sku}

Get Available-to-Promise quantity.

### POST /inventory/adjust

Adjust inventory quantity.

**Request:**
```json
{
  "sku": "PROD-001",
  "quantityChange": -5,
  "reason": "Damaged in transit"
}
```

### POST /inventory/receive

Receive inventory at a warehouse node.

---

## Shipments

### GET /shipments

List shipments with filters.

### GET /shipments/{id}

Get shipment details with tracking events.

### POST /shipments/{id}/track

Add a tracking event to a shipment.

### POST /shipments/{id}/deliver

Mark shipment as delivered.

---

## Analytics

### GET /analytics/overview

Dashboard overview KPIs (orders, revenue, OTD rate).

### GET /analytics/cost-breakdown

Cost analysis by carrier, surcharge type.

### GET /analytics/lane-performance

Lane-level performance metrics.

### GET /analytics/order-velocity

Hourly order volume over last 24 hours.

### GET /analytics/carrier-performance

Per-carrier OTD, cost, and SLA metrics.

---

## AI Predictions

### POST /ai/predict/demand

Demand forecasting for SKU + time range.

### POST /ai/predict/routing

AI-powered order routing recommendation.

### POST /ai/predict/anomaly

Anomaly detection on order patterns.

### GET /ai/experiments

List active ML experiments.

### GET /ai/briefing

AI-generated executive briefing.

### GET /ai/audit-trail

AI decision audit trail.

---

## File Import

### GET /import/entity-types

List supported entity types for import.

### GET /import/formats

List supported file formats.

### POST /import/{entityType}

Import data from file (CSV, JSON, XML, EDI, XLSX).

**Request:** `multipart/form-data`
| Field | Type | Description |
|---|---|---|
| `file` | File | Import file |

**Supported entity types:** `products`, `orders`, `inventory`, `customers`, `shipments`, `returns`, `suppliers`, `purchase-orders`, `invoices`, `warehouses`

---

## Returns

### GET /returns

List returns/RMAs.

### POST /returns

Create a return request.

### POST /returns/{id}/approve

Approve a return.

### POST /returns/{id}/reject

Reject a return.

### POST /returns/{id}/refund

Process a refund.

---

## Webhooks

### GET /webhooks

List configured webhooks.

### POST /webhooks

Register a new webhook endpoint.

### DELETE /webhooks/{id}

Remove a webhook.

---

## Carriers

### GET /carriers

List carrier configurations.

### POST /carriers

Add a new carrier.

### GET /carriers/rate-shop

Compare rates across carriers for a shipment.

**Query Parameters:**
| Param | Type | Description |
|---|---|---|
| `origin` | string | Origin ZIP |
| `destination` | string | Destination ZIP |
| `weight` | number | Package weight (lbs) |
| `serviceLevel` | string | Service level |

---

## Users & RBAC

### GET /users

List users (admin only).

### POST /users

Create a user account.

### PUT /users/{id}/roles

Assign roles to a user.

### GET /users/permissions

Get current user's effective permissions.

---

## Roles

14 predefined roles: `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_WAREHOUSE_MANAGER`, `ROLE_ORDER_MANAGER`, `ROLE_INVENTORY_MANAGER`, `ROLE_SHIPPING_MANAGER`, `ROLE_CS_REP`, `ROLE_B2B_SELLER`, `ROLE_ANALYST`, `ROLE_AI_ENGINEER`, `ROLE_AUDITOR`, `ROLE_VIEWER`, `ROLE_API_USER`, `ROLE_IMPORT_OPERATOR`.

---

## Error Responses

All errors follow consistent format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-07-21T10:00:00Z",
  "path": "/api/v1/orders"
}
```

| Status | Description |
|---|---|
| 400 | Bad request / validation error |
| 401 | Unauthorized (missing/invalid token) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Resource not found |
| 409 | Conflict (duplicate, state conflict) |
| 429 | Rate limited |
| 500 | Internal server error |

---

## Rate Limiting

- **Login**: 5 attempts per 30 seconds
- **MFA verification**: 5 attempts per 30 seconds
- **Import**: 10 requests per minute
- **General API**: 100 requests per minute per user

Rate limit headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1690000000
```

## WebSocket Events

Connect via `ws://host/ws?token={jwt}` for real-time events:

| Event | Payload |
|---|---|
| `order.created` | `{ orderId, status, timestamp }` |
| `order.confirmed` | `{ orderId, status, timestamp }` |
| `order.shipped` | `{ orderId, trackingNumber, carrier }` |
| `order.delivered` | `{ orderId, deliveredAt }` |
| `inventory.low_stock` | `{ productId, sku, currentQty, threshold }` |
| `shipment.update` | `{ shipmentId, status, location }` |
| `system.alert` | `{ severity, title, message }` |
