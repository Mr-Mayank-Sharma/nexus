# NexusShip OMS — HotWax Gap Closure Implementation Plan

## Executive Summary

This document outlines the implementation plan to close all feature gaps between NexusShip OMS and HotWax Commerce, and surpass HotWax in each capability area. The plan covers 10 prioritized gaps across order lifecycle, inventory management, store fulfillment, and operational efficiency.

**Current State:**
- Backend: 497 tests passing, 43 controllers, 51+ services, 100 entities
- Frontend: 395 tests passing, 70+ pages, 46 API modules
- HotWax Analysis: Full glossary, BPMs, and how-to guides scraped

**Target State:**
- All 10 gaps closed with production-ready implementations
- NexusShip surpasses HotWax in each capability area

---

## Gap 1: Advanced ATP Calculation Engine

### HotWax Capability
- QOH → ATP → Online ATP calculation pipeline
- Safety stock thresholds per SKU/facility
- Store pickup buffer days
- Shipping ATP limits by carrier/service
- Facility exclusion rules

### Current NexusShip State
- `NxInventory` entity has `safetyStock`, `reorderPoint`, `reorderQty` fields
- `ATPRulesPage.tsx` exists with thresholds, safety stock, store pickup, shipping tabs (UI only)
- `InventoryService` has basic QOH/ATP but lacks advanced calculation

### Implementation Plan

#### Backend Changes

**1. Entity: `NxATPCalculation.java`**
```java
@Entity
@Table(name = "nx_atp_calculations")
- id (UUID, PK)
- tenantId (UUID)
- sku (String)
- nodeId (UUID)
- calculationType (String: "QOH", "ATP", "ONLINE_ATP")
- quantityOnHand (Integer)
- quantityAllocated (Integer)
- quantityReserved (Integer)
- quantityInTransit (Integer)
- safetyStock (Integer)
- availableToPromise (Integer)
- onlineATP (Integer)
- calculatedAt (LocalDateTime)
- expiresAt (LocalDateTime)
```

**2. Entity: `NxATPConfig.java`**
```java
@Entity
@Table(name = "nx_atp_configs")
- id (UUID, PK)
- tenantId (UUID)
- sku (String)
- nodeId (UUID)
- safetyStockThreshold (Integer)
- reorderPoint (Integer)
- reorderQuantity (Integer)
- leadTimeDays (Integer)
- storePickupBufferDays (Integer)
- maxShippingATP (Integer)
- carrierId (String)
- serviceLevel (String)
- enabled (Boolean)
```

**3. Service: `ATPCalculationService.java`**
```java
@Service
- calculateATP(sku, nodeId): NxATPCalculation
  - QOH = quantityOnHand - quantityAllocated - quantityReserved
  - ATP = QOH - safetyStock
  - OnlineATP = ATP - pendingTransfers - inTransitQuantity
  
- calculateOnlineATP(sku, nodeId): Integer
  - Considers: QOH, allocated, reserved, inTransit, safetyStock
  - Applies facility exclusion rules
  
- bulkCalculateATP(tenantId): List<NxATPCalculation>
  - Batch calculation for all SKUs at a node
  
- getATPWithThresholds(sku, nodeId): ATPResponse
  - Returns ATP with threshold warnings
  - Indicates if below safety stock, reorder point, etc.
```

**4. Migration: `V27__create_atp_tables.sql`**
```sql
CREATE TABLE nx_atp_calculations (...);
CREATE TABLE nx_atp_configs (...);
CREATE INDEX idx_atp_sku_node ON nx_atp_calculations(sku, node_id);
```

**5. Controller: `ATPCalculationController.java`**
```java
@RestController
@RequestMapping("/api/v1/atp")
- POST /calculate - Calculate ATP for SKU/node
- POST /bulk-calculate - Batch ATP calculation
- GET /config/{sku}/{node} - Get ATP config
- PUT /config/{sku}/{node} - Update ATP config
- GET /thresholds/{node} - Get items below thresholds
```

#### Frontend Changes

**1. Update `ATPRulesPage.tsx`**
- Connect to real backend API
- Add ATP calculation visualization
- Show threshold warnings with color coding
- Add bulk calculation trigger

**2. New Component: `ATPCalculator.tsx`**
- Real-time ATP calculation form
- Visual pipeline: QOH → ATP → Online ATP
- Threshold indicators

#### Tests
- Unit tests for ATP calculation logic
- Integration tests for bulk calculations
- Frontend tests for ATP visualization

---

## Gap 2: Transfer Orders

### HotWax Capability
- Warehouse-to-store transfers
- Store-to-store transfers
- Store-to-warehouse transfers
- Full transfer lifecycle (created → approved → in-transit → received)
- Transfer tracking and reporting

### Current NexusShip State
- No `TransferOrder` entity exists
- No transfer-related controllers or services
- `TransfersPage.tsx` does NOT exist in frontend

### Implementation Plan

#### Backend Changes

**1. Entity: `NxTransferOrder.java`**
```java
@Entity
@Table(name = "nx_transfer_orders")
- id (UUID, PK)
- tenantId (UUID)
- transferNumber (String, unique)
- transferType (String: "WAREHOUSE_TO_STORE", "STORE_TO_STORE", "STORE_TO_WAREHOUSE")
- sourceNodeId (UUID)
- destinationNodeId (UUID)
- status (String: "DRAFT", "PENDING_APPROVAL", "APPROVED", "IN_TRANSIT", "RECEIVED", "CANCELLED")
- priority (String: "LOW", "NORMAL", "HIGH", "URGENT")
- requestedBy (UUID)
- approvedBy (UUID)
- expectedArrival (LocalDateTime)
- actualArrival (LocalDateTime)
- notes (String)
- metadata (JSONB)
- createdAt, updatedAt
```

**2. Entity: `NxTransferOrderItem.java`**
```java
@Entity
@Table(name = "nx_transfer_order_items")
- id (UUID, PK)
- transferOrderId (UUID, FK)
- tenantId (UUID)
- sku (String)
- productName (String)
- quantityRequested (Integer)
- quantityShipped (Integer)
- quantityReceived (Integer)
- unitCost (BigDecimal)
- status (String: "PENDING", "SHIPPED", "RECEIVED", "CANCELLED")
- createdAt, updatedAt
```

**3. Service: `TransferOrderService.java`**
```java
@Service
- createTransferOrder(request): NxTransferOrder
- approveTransferOrder(id, approvedBy): NxTransferOrder
- shipTransferOrder(id, shippedBy, items): NxTransferOrder
- receiveTransferOrder(id, receivedBy, items): NxTransferOrder
- cancelTransferOrder(id, cancelledBy): NxTransferOrder
- getTransferOrders(filters): List<NxTransferOrder>
- getTransferOrder(id): NxTransferOrder
- getTransferOrderItems(transferOrderId): List<NxTransferOrderItem>
- getTransfersByNode(nodeId): List<NxTransferOrder>
- getTransfersInTransit(): List<NxTransferOrder>
```

**4. Migration: `V28__create_transfer_order_tables.sql`**
```sql
CREATE TABLE nx_transfer_orders (...);
CREATE TABLE nx_transfer_order_items (...);
CREATE INDEX idx_transfer_status ON nx_transfer_orders(status);
CREATE INDEX idx_transfer_source ON nx_transfer_orders(source_node_id);
CREATE INDEX idx_transfer_dest ON nx_transfer_orders(destination_node_id);
```

**5. Controller: `TransferOrderController.java`**
```java
@RestController
@RequestMapping("/api/v1/transfers")
- POST / - Create transfer order
- GET / - List transfers with filters
- GET /{id} - Get transfer details
- PUT /{id}/approve - Approve transfer
- PUT /{id}/ship - Ship transfer
- PUT /{id}/receive - Receive transfer
- PUT /{id}/cancel - Cancel transfer
- GET /in-transit - Get all in-transit transfers
- GET /node/{nodeId} - Get transfers by node
```

#### Frontend Changes

**1. New Page: `TransfersPage.tsx`**
```typescript
- Tabs: Active | In-Transit | Received | All
- Transfer list with status badges
- Create transfer wizard
- Transfer detail modal
- Bulk approve/ship actions
```

**2. New Page: `TransferDetailPage.tsx`**
```typescript
- Transfer info card
- Item list with quantities
- Timeline/status tracker
- Action buttons based on status
```

**3. API Module: `transfers.ts`**
```typescript
- createTransfer(data)
- getTransfers(filters)
- getTransfer(id)
- approveTransfer(id)
- shipTransfer(id, items)
- receiveTransfer(id, items)
- cancelTransfer(id)
```

#### Tests
- Unit tests for transfer lifecycle
- Integration tests for approval workflow
- Frontend tests for transfer pages

---

## Gap 3: Pre-order/Backorder Parking

### HotWax Capability
- Pre-order holding queue
- Backorder parking
- Auto-release on promise date
- Inventory promise tracking
- Customer notification on availability

### Current NexusShip State
- `PreOrdersPage.tsx` exists but uses MOCK data
- No backend entity for pre-order parking
- No auto-release mechanism

### Implementation Plan

#### Backend Changes

**1. Entity: `NxPreOrderParking.java`**
```java
@Entity
@Table(name = "nx_preorder_parking")
- id (UUID, PK)
- tenantId (UUID)
- orderId (UUID)
- orderItemId (UUID)
- sku (String)
- productName (String)
- quantityOrdered (Integer)
- quantityPromised (Integer)
- promiseDate (LocalDate)
- parkingType (String: "PRE_ORDER", "BACKORDER")
- status (String: "PARKED", "PROMISED", "FULFILLED", "CANCELLED", "EXPIRED")
- parkedAt (LocalDateTime)
- releasedAt (LocalDateTime)
- releasedBy (String)
- notes (String)
- metadata (JSONB)
- createdAt, updatedAt
```

**2. Service: `PreOrderParkingService.java`**
```java
@Service
- parkOrder(parkRequest): NxPreOrderParking
  - Moves order items to parking queue
  - Sets promise date based on inventory forecast
  
- releaseParkedOrder(id): NxPreOrderParking
  - Releases parked order for fulfillment
  - Updates status to PROMISED
  
- autoReleaseByDate(): List<NxPreOrderParking>
  - Scheduled job: releases orders where promiseDate <= today
  - Checks inventory availability before release
  
- getParkedOrders(filters): List<NxPreOrderParking>
- getParkedOrdersBySku(sku): List<NxPreOrderParking>
- cancelParkedOrder(id): NxPreOrderParking
- extendPromiseDate(id, newDate): NxPreOrderParking
```

**3. Migration: `V29__create_preorder_parking.sql`**
```sql
CREATE TABLE nx_preorder_parking (...);
CREATE INDEX idx_parking_status ON nx_preorder_parking(status);
CREATE INDEX idx_parking_promise ON nx_preorder_parking(promise_date);
CREATE INDEX idx_parking_sku ON nx_preorder_parking(sku);
```

**4. Scheduled Job: `PreOrderReleaseScheduler.java`**
```java
@Component
@Scheduled(cron = "0 0 6 * * ?")  // Daily at 6 AM
- Runs autoReleaseByDate()
- Sends notifications for released orders
- Logs release actions
```

**5. Controller: `PreOrderParkingController.java`**
```java
@RestController
@RequestMapping("/api/v1/preorder-parking")
- POST / - Park an order
- GET / - List parked orders
- GET /{id} - Get parked order details
- PUT /{id}/release - Manual release
- PUT /{id}/cancel - Cancel parked order
- PUT /{id}/extend - Extend promise date
- POST /auto-release - Trigger auto-release (admin)
- GET /stats - Parking statistics
```

#### Frontend Changes

**1. Update `PreOrdersPage.tsx`**
- Replace mock data with real API calls
- Add parking queue view
- Show promise dates with countdown
- Add bulk release actions

**2. New Component: `ParkingQueue.tsx`**
- Visual queue of parked orders
- Promise date timeline
- Quick release buttons
- Filter by SKU, date, status

#### Tests
- Unit tests for parking logic
- Integration tests for auto-release scheduler
- Frontend tests for parking UI

---

## Gap 4: BOPIS Operational App

### HotWax Capability
- Store associate picking interface
- Proof of Delivery (POD) capture
- Ship-to-store workflow
- Customer notification on ready for pickup
- Pickup confirmation with signature

### Current NexusShip State
- `BOPISPage.tsx` exists with orders, catalog, ship-to-store tabs (basic)
- No POD capture functionality
- No store associate picking interface

### Implementation Plan

#### Backend Changes

**1. Entity: `NxBopisPickup.java`**
```java
@Entity
@Table(name = "nx_bopis_pickups")
- id (UUID, PK)
- tenantId (UUID)
- orderId (UUID)
- storeNodeId (UUID)
- pickerId (UUID)
- status (String: "PICKING", "READY", "PICKED_UP", "EXPIRED", "CANCELLED")
- pickedAt (LocalDateTime)
- readyAt (LocalDateTime)
- pickedUpAt (LocalDateTime)
- podSignatureUrl (String)
- podPhotoUrl (String)
- podNotes (String)
- customerName (String)
- customerPhone (String)
- customerEmail (String)
- pickupCode (String)
- createdAt, updatedAt
```

**2. Service: `BopisService.java`**
```java
@Service
- assignPicker(orderId, storeNodeId, pickerId): NxBopisPickup
- startPicking(pickupId): NxBopisPickup
- completePicking(pickupId, items): NxBopisPickup
  - Marks items as picked
  - Notifies customer: ready for pickup
  
- confirmPickup(pickupId, podData): NxBopisPickup
  - Captures POD (signature, photo)
  - Updates status to PICKED_UP
  
- expirePickups(): List<NxBopisPickup>
  - Scheduled job: expires unclaimed pickups after X days
  
- getPickupsByStore(storeNodeId): List<NxBopisPickup>
- getPickupsByPicker(pickerId): List<NxBopisPickup>
- getPickupStats(storeNodeId): Map
```

**3. Migration: `V30__create_bopis_pickup.sql`**
```sql
CREATE TABLE nx_bopis_pickups (...);
CREATE INDEX idx_bopis_store ON nx_bopis_pickups(store_node_id);
CREATE INDEX idx_bopis_picker ON nx_bopis_pickups(picker_id);
CREATE INDEX idx_bopis_status ON nx_bopis_pickups(status);
```

**4. Controller: `BopisController.java`**
```java
@RestController
@RequestMapping("/api/v1/bopis")
- POST /assign - Assign picker to order
- PUT /{id}/start-picking - Start picking
- PUT /{id}/complete-picking - Complete picking
- PUT /{id}/confirm-pickup - Confirm pickup with POD
- GET /store/{storeId} - Get pickups by store
- GET /picker/{pickerId} - Get pickups by picker
- GET /stats/{storeId} - Get pickup statistics
- POST /notify-customer/{id} - Send pickup notification
```

#### Frontend Changes

**1. Update `BOPISPage.tsx`**
- Add picker assignment UI
- Add POD capture form (signature pad, photo upload)
- Add pickup code verification
- Add customer notification triggers

**2. New Page: `BopisAssociatePage.tsx`**
```typescript
// Store associate mobile-optimized view
- My assigned pickups list
- Start picking workflow
- Item scanning/confirmation
- POD capture (signature + photo)
- Complete pickup
```

**3. New Component: `PODCapture.tsx`**
```typescript
- Signature pad (canvas-based)
- Photo upload
- Notes field
- Submit POD
```

**4. API Module: `bopis.ts`**
```typescript
- assignPicker(orderId, storeId, pickerId)
- startPicking(pickupId)
- completePicking(pickupId, items)
- confirmPickup(pickupId, podData)
- getPickupsByStore(storeId)
- getPickupsByPicker(pickerId)
- getPickupStats(storeId)
```

#### Tests
- Unit tests for pickup lifecycle
- Integration tests for POD capture
- Frontend tests for associate interface

---

## Gap 5: Rejection Handling

### HotWax Capability
- Configurable rejection reasons
- Inventory impact rules per reason
- Customer notification on rejection
- Refund/exchange workflow
- Rejection analytics

### Current NexusShip State
- Basic return handling exists
- No configurable rejection reasons
- No inventory impact rules

### Implementation Plan

#### Backend Changes

**1. Entity: `NxRejectionReason.java`**
```java
@Entity
@Table(name = "nx_rejection_reasons")
- id (UUID, PK)
- tenantId (UUID)
- code (String, unique)
- name (String)
- description (String)
- category (String: "QUALITY", "DAMAGE", "WRONG_ITEM", "LATE_DELIVERY", "CUSTOMER_REQUEST")
- inventoryImpact (String: "RESTOCK", "DAMAGED", "SCRAP", "RETURN_TO_VENDOR")
- refundRequired (Boolean)
- exchangeAllowed (Boolean)
- enabled (Boolean)
- createdAt, updatedAt
```

**2. Entity: `NxOrderRejection.java`**
```java
@Entity
@Table(name = "nx_order_rejections")
- id (UUID, PK)
- tenantId (UUID)
- orderId (UUID)
- orderItemId (UUID)
- rejectionReasonId (UUID)
- sku (String)
- quantityRejected (Integer)
- rejectionNotes (String)
- rejectedBy (UUID)
- rejectedAt (LocalDateTime)
- refundStatus (String: "PENDING", "PROCESSED", "VOIDED")
- refundAmount (BigDecimal)
- exchangeOrderId (UUID)
- inventoryRestocked (Boolean)
- customerNotified (Boolean)
- createdAt, updatedAt
```

**3. Service: `RejectionService.java`**
```java
@Service
- rejectOrderItem(rejectRequest): NxOrderRejection
  - Validates rejection reason
  - Applies inventory impact rules
  - Triggers refund if required
  - Notifies customer
  
- getRejectionReasons(tenantId): List<NxRejectionReason>
- createRejectionReason(reason): NxRejectionReason
- updateRejectionReason(id, reason): NxOrderRejection
  
- getRejections(filters): List<NxOrderRejection>
- getRejectionStats(): Map
- processRefund(rejectionId): NxOrderRejection
```

**4. Migration: `V31__create_rejection_tables.sql`**
```sql
CREATE TABLE nx_rejection_reasons (...);
CREATE TABLE nx_order_rejections (...);
CREATE INDEX idx_rejection_order ON nx_order_rejections(order_id);
CREATE INDEX idx_rejection_reason ON nx_order_rejections(rejection_reason_id);
```

**5. Controller: `RejectionController.java`**
```java
@RestController
@RequestMapping("/api/v1/rejections")
- POST / - Reject order item
- GET / - List rejections
- GET /{id} - Get rejection details
- GET /reasons - List rejection reasons
- POST /reasons - Create rejection reason
- PUT /reasons/{id} - Update rejection reason
- POST /{id}/process-refund - Process refund
- GET /stats - Rejection statistics
```

#### Frontend Changes

**1. New Page: `RejectionsPage.tsx`**
```typescript
- Rejection list with reason filters
- Create rejection form
- Rejection detail modal
- Refund processing UI
- Rejection analytics dashboard
```

**2. New Component: `RejectionReasonManager.tsx`**
- CRUD for rejection reasons
- Inventory impact configuration
- Enable/disable reasons

**3. API Module: `rejections.ts`**
```typescript
- rejectOrderItem(data)
- getRejections(filters)
- getRejection(id)
- getRejectionReasons()
- createRejectionReason(data)
- updateRejectionReason(id, data)
- processRefund(id)
- getRejectionStats()
```

#### Tests
- Unit tests for rejection logic
- Integration tests for inventory impact
- Frontend tests for rejection UI

---

## Gap 6: Picker Role & Assignment

### HotWax Capability
- Named picker assignment
- Replaceable picker (reassign)
- FIFO picklist generation
- Picker performance tracking
- Picker workload balancing

### Current NexusShip State
- `NxPicklist` entity has `assigneeId` field
- No picker role entity
- No FIFO logic
- No picker performance tracking

### Implementation Plan

#### Backend Changes

**1. Entity: `NxPicker.java`**
```java
@Entity
@Table(name = "nx_pickers")
- id (UUID, PK)
- tenantId (UUID)
- userId (UUID)
- storeNodeId (UUID)
- pickerCode (String, unique)
- status (String: "ACTIVE", "INACTIVE", "ON_BREAK")
- currentPicklistId (UUID)
- totalPicksToday (Integer)
- totalPicksAllTime (Integer)
- avgPickTimeSeconds (Integer)
- accuracyRate (BigDecimal)
- lastActiveAt (LocalDateTime)
- createdAt, updatedAt
```

**2. Entity: `NxPickAssignment.java`**
```java
@Entity
@Table(name = "nx_pick_assignments")
- id (UUID, PK)
- tenantId (UUID)
- picklistId (UUID)
- pickerId (UUID)
- assignedAt (LocalDateTime)
- startedAt (LocalDateTime)
- completedAt (LocalDateTime)
- status (String: "ASSIGNED", "IN_PROGRESS", "COMPLETED", "REASSIGNED")
- reassignReason (String)
- reassignedTo (UUID)
```

**3. Service: `PickerService.java`**
```java
@Service
- createPicker(picker): NxPicker
- updatePicker(id, picker): NxPicker
- getPickers(storeNodeId): List<NxPicker>
- getAvailablePickers(storeNodeId): List<NxPicker>
  
- assignPicklist(picklistId, pickerId): NxPickAssignment
  - FIFO: assigns to picker with fewest active picklists
  
- reassignPicklist(picklistId, newPickerId, reason): NxPickAssignment
  - Replaces picker on active picklist
  
- completePickAssignment(assignmentId): NxPickAssignment
  - Updates picker stats
  
- getPickerStats(pickerId): Map
- getPickerWorkload(storeNodeId): Map
```

**4. Update `NxPicklist` Entity**
```java
- Add: pickerName (String)
- Add: pickerCode (String)
- Add: assignedAt (LocalDateTime)
```

**5. Migration: `V32__create_picker_tables.sql`**
```sql
CREATE TABLE nx_pickers (...);
CREATE TABLE nx_pick_assignments (...);
ALTER TABLE nx_picklists ADD COLUMN picker_name VARCHAR(255);
ALTER TABLE nx_picklists ADD COLUMN picker_code VARCHAR(50);
ALTER TABLE nx_picklists ADD COLUMN assigned_at TIMESTAMP;
CREATE INDEX idx_picker_store ON nx_pickers(store_node_id);
```

**6. Controller: `PickerController.java`**
```java
@RestController
@RequestMapping("/api/v1/pickers")
- POST / - Create picker
- GET / - List pickers
- GET /{id} - Get picker details
- PUT /{id} - Update picker
- GET /available/{storeId} - Get available pickers
- POST /assign - Assign picklist to picker
- POST /reassign - Reassign picklist
- GET /{id}/stats - Get picker stats
- GET /workload/{storeId} - Get picker workload
```

#### Frontend Changes

**1. New Page: `PickersPage.tsx`**
```typescript
- Picker list with status
- Picker detail modal
- Workload visualization
- Performance metrics
```

**2. Update `PickingPage.tsx`**
- Add picker assignment dropdown
- Show FIFO picklist order
- Add reassign button

**3. API Module: `pickers.ts`**
```typescript
- createPicker(data)
- getPickers(storeId)
- getAvailablePickers(storeId)
- assignPicklist(picklistId, pickerId)
- reassignPicklist(picklistId, newPickerId, reason)
- getPickerStats(pickerId)
- getPickerWorkload(storeId)
```

#### Tests
- Unit tests for picker assignment logic
- Integration tests for FIFO assignment
- Frontend tests for picker UI

---

## Gap 7: Brokering Queue & Scheduled Runs

### HotWax Capability
- Brokering waiting area for unallocated orders
- Scheduled brokering runs (interval-based)
- Priority-based brokering
- Brokering history and audit
- Manual brokering trigger

### Current NexusShip State
- `OrderRoutingService` has allocation logic
- No brokering queue entity
- No scheduled brokering runs
- No brokering history

### Implementation Plan

#### Backend Changes

**1. Entity: `NxBrokeringQueue.java`**
```java
@Entity
@Table(name = "nx_brokering_queue")
- id (UUID, PK)
- tenantId (UUID)
- orderId (UUID)
- priority (String: "LOW", "NORMAL", "HIGH", "URGENT")
- status (String: "WAITING", "PROCESSING", "ALLOCATED", "FAILED", "EXPIRED")
- attempts (Integer)
- maxAttempts (Integer)
- lastAttemptAt (LocalDateTime)
- nextRunAt (LocalDateTime)
- allocatedNodeId (UUID)
- failureReason (String)
- metadata (JSONB)
- enteredAt (LocalDateTime)
- exitedAt (LocalDateTime)
- createdAt, updatedAt
```

**2. Entity: `NxBrokeringRun.java`**
```java
@Entity
@Table(name = "nx_brokering_runs")
- id (UUID, PK)
- tenantId (UUID)
- runType (String: "SCHEDULED", "MANUAL", "PRIORITY")
- startedAt (LocalDateTime)
- completedAt (LocalDateTime)
- ordersProcessed (Integer)
- ordersAllocated (Integer)
- ordersFailed (Integer)
- executionTimeMs (Integer)
- status (String: "RUNNING", "COMPLETED", "FAILED")
- triggeredBy (UUID)
- metadata (JSONB)
```

**3. Service: `BrokeringService.java`**
```java
@Service
- enqueueOrder(orderId, priority): NxBrokeringQueue
  - Adds order to brokering queue
  
- processBrokeringQueue(): NxBrokeringRun
  - Main brokering logic:
    1. Fetch waiting orders by priority
    2. For each order, call OrderRoutingService.allocateOrder()
    3. Update queue status
    4. Log run statistics
  
- processPriorityQueue(): NxBrokeringRun
  - Processes only HIGH/URGENT priority orders
  
- manualBrokeringRun(orderIds): NxBrokeringRun
  - Admin trigger for specific orders
  
- getQueue(filters): List<NxBrokeringQueue>
- getQueueStats(): Map
- getRunHistory(filters): List<NxBrokeringRun>
- expireStaleOrders(): List<NxBrokeringQueue>
  - Marks orders as EXPIRED if in queue too long
```

**4. Migration: `V33__create_brokering_tables.sql`**
```sql
CREATE TABLE nx_brokering_queue (...);
CREATE TABLE nx_brokering_runs (...);
CREATE INDEX idx_brokering_status ON nx_brokering_queue(status);
CREATE INDEX idx_brokering_priority ON nx_brokering_queue(priority);
CREATE INDEX idx_brokering_next_run ON nx_brokering_queue(next_run_at);
```

**5. Scheduled Job: `BrokeringScheduler.java`**
```java
@Component
@Scheduled(cron = "0 */5 * * * ?")  // Every 5 minutes
- Calls brokeringService.processBrokeringQueue()
- Logs run results
- Triggers alerts on failures
```

**6. Controller: `BrokeringController.java`**
```java
@RestController
@RequestMapping("/api/v1/brokering")
- POST /enqueue - Enqueue order
- POST /process - Manual brokering run
- POST /process/priority - Process priority queue
- GET /queue - Get brokering queue
- GET /queue/stats - Queue statistics
- GET /runs - Get run history
- GET /runs/{id} - Get run details
- DELETE /queue/{id} - Remove from queue
- POST /expire-stale - Expire stale orders
```

#### Frontend Changes

**1. New Page: `BrokeringQueuePage.tsx`**
```typescript
- Queue list with priority colors
- Manual brokering trigger
- Run history timeline
- Queue statistics dashboard
- Real-time updates (polling)
```

**2. New Component: `BrokeringStats.tsx`**
```typescript
- Orders waiting
- Orders processing
- Allocation success rate
- Average wait time
```

**3. API Module: `brokering.ts`**
```typescript
- enqueueOrder(orderId, priority)
- processBrokeringQueue()
- processPriorityQueue()
- getQueue(filters)
- getQueueStats()
- getRunHistory(filters)
- getRunDetails(id)
- removeFromQueue(id)
- expireStaleOrders()
```

#### Tests
- Unit tests for brokering logic
- Integration tests for scheduled runs
- Frontend tests for queue UI

---

## Gap 8: Order Approval Engine

### HotWax Capability
- Fraud detection checks
- Auto-approve criteria
- Manual review queue
- Approval history
- Risk scoring

### Current NexusShip State
- No order approval entity
- No fraud detection
- No auto-approve logic

### Implementation Plan

#### Backend Changes

**1. Entity: `NxOrderApproval.java`**
```java
@Entity
@Table(name = "nx_order_approvals")
- id (UUID, PK)
- tenantId (UUID)
- orderId (UUID)
- status (String: "PENDING", "AUTO_APPROVED", "MANUAL_REVIEW", "APPROVED", "REJECTED")
- riskScore (BigDecimal)
- fraudChecks (JSONB)
- autoApproveEligible (Boolean)
- reviewNotes (String)
- reviewedBy (UUID)
- reviewedAt (LocalDateTime)
- approvedAt (LocalDateTime)
- rejectedAt (LocalDateTime)
- rejectionReason (String)
- metadata (JSONB)
- createdAt, updatedAt
```

**2. Entity: `NxApprovalRule.java`**
```java
@Entity
@Table(name = "nx_approval_rules")
- id (UUID, PK)
- tenantId (UUID)
- name (String)
- description (String)
- ruleType (String: "AMOUNT_THRESHOLD", "VELOCITY", "GEOLOCATION", "DEVICE", "CUSTOMER_HISTORY")
- condition (JSONB)
- action (String: "AUTO_APPROVE", "MANUAL_REVIEW", "REJECT")
- priority (Integer)
- enabled (Boolean)
- createdAt, updatedAt
```

**3. Service: `OrderApprovalService.java`**
```java
@Service
- submitForApproval(orderId): NxOrderApproval
  - Runs fraud checks
  - Calculates risk score
  - Determines auto-approve eligibility
  
- autoApprove(approvalId): NxOrderApproval
  - Auto-approves low-risk orders
  
- manualReview(approvalId, decision, notes): NxOrderApproval
  - Manual approve/reject with notes
  
- calculateRiskScore(order): BigDecimal
  - Factors: amount, velocity, geolocation, device, customer history
  
- evaluateApprovalRules(order): List<NxApprovalRule>
  - Matches order against approval rules
  
- getApprovals(filters): List<NxOrderApproval>
- getApprovalStats(): Map
- getApprovalRules(tenantId): List<NxApprovalRule>
- createApprovalRule(rule): NxApprovalRule
```

**4. Migration: `V34__create_approval_tables.sql`**
```sql
CREATE TABLE nx_order_approvals (...);
CREATE TABLE nx_approval_rules (...);
CREATE INDEX idx_approval_order ON nx_order_approvals(order_id);
CREATE INDEX idx_approval_status ON nx_order_approvals(status);
```

**5. Controller: `OrderApprovalController.java`**
```java
@RestController
@RequestMapping("/api/v1/approvals")
- POST /submit/{orderId} - Submit order for approval
- PUT /{id}/approve - Manual approve
- PUT /{id}/reject - Manual reject
- GET / - List approvals
- GET /{id} - Get approval details
- GET /pending - Get pending approvals
- GET /stats - Approval statistics
- GET /rules - Get approval rules
- POST /rules - Create approval rule
- PUT /rules/{id} - Update approval rule
```

#### Frontend Changes

**1. New Page: `OrderApprovalsPage.tsx`**
```typescript
- Pending approvals queue
- Risk score visualization
- Fraud check details
- Approve/reject actions
- Approval history
```

**2. New Component: `ApprovalRuleManager.tsx`**
- CRUD for approval rules
- Rule testing interface
- Enable/disable rules

**3. API Module: `approvals.ts`**
```typescript
- submitForApproval(orderId)
- approveOrder(approvalId, notes)
- rejectOrder(approvalId, reason)
- getApprovals(filters)
- getApproval(id)
- getPendingApprovals()
- getApprovalStats()
- getApprovalRules()
- createApprovalRule(data)
```

#### Tests
- Unit tests for risk scoring
- Integration tests for approval workflow
- Frontend tests for approval UI

---

## Gap 9: Fulfillment Limits

### HotWax Capability
- Max orders per day per store
- Fulfillment capacity tracking
- Disable fulfillment toggle
- Capacity alerts
- Workload balancing

### Current NexusShip State
- No fulfillment limit entity
- No capacity tracking per store
- No disable toggle

### Implementation Plan

#### Backend Changes

**1. Entity: `NxFulfillmentLimit.java`**
```java
@Entity
@Table(name = "nx_fulfillment_limits")
- id (UUID, PK)
- tenantId (UUID)
- nodeId (UUID)
- maxOrdersPerDay (Integer)
- maxOrdersPerWeek (Integer)
- maxItemsPerDay (Integer)
- currentOrdersToday (Integer)
- currentOrdersThisWeek (Integer)
- currentItemsToday (Integer)
- fulfillmentEnabled (Boolean)
- alertThreshold (BigDecimal)  // e.g., 0.8 = 80% capacity alert
- lastResetAt (LocalDateTime)
- createdAt, updatedAt
```

**2. Entity: `NxFulfillmentCapacityLog.java`**
```java
@Entity
@Table(name = "nx_fulfillment_capacity_log")
- id (UUID, PK)
- tenantId (UUID)
- nodeId (UUID)
- orderId (UUID)
- action (String: "ORDER_ASSIGNED", "ORDER_REMOVED", "LIMIT_REACHED", "LIMIT_RESET")
- ordersBefore (Integer)
- ordersAfter (Integer)
- capacityPercentage (BigDecimal)
- createdAt
```

**3. Service: `FulfillmentLimitService.java`**
```java
@Service
- createLimit(limit): NxFulfillmentLimit
- updateLimit(id, limit): NxFulfillmentLimit
- getLimit(nodeId): NxFulfillmentLimit
- getLimits(tenantId): List<NxFulfillmentLimit>
  
- checkCapacity(nodeId): CapacityResponse
  - Returns current capacity vs limits
  - Indicates if at/over limit
  
- incrementOrderCount(nodeId, orderId): NxFulfillmentLimit
  - Called when order assigned to node
  - Checks limit before incrementing
  - Logs capacity change
  
- decrementOrderCount(nodeId, orderId): NxFulfillmentLimit
  - Called when order removed from node
  
- toggleFulfillment(nodeId, enabled): NxFulfillmentLimit
  - Enables/disables fulfillment for node
  
- resetDailyCounts(): void
  - Scheduled job: resets daily counters
  
- getCapacityAlerts(tenantId): List<NxFulfillmentCapacityLog>
  - Returns nodes above alert threshold
  
- getCapacityHistory(nodeId, dateRange): List<NxFulfillmentCapacityLog>
```

**4. Migration: `V35__create_fulfillment_limits.sql`**
```sql
CREATE TABLE nx_fulfillment_limits (...);
CREATE TABLE nx_fulfillment_capacity_log (...);
CREATE INDEX idx_fulfillment_node ON nx_fulfillment_limits(node_id);
CREATE INDEX idx_capacity_log_node ON nx_fulfillment_capacity_log(node_id);
```

**5. Scheduled Job: `FulfillmentLimitScheduler.java`**
```java
@Component
@Scheduled(cron = "0 0 0 * * ?")  // Daily at midnight
- Resets daily order/item counts
- Logs reset action
```

**6. Controller: `FulfillmentLimitController.java`**
```java
@RestController
@RequestMapping("/api/v1/fulfillment-limits")
- POST / - Create limit
- GET / - List limits
- GET /{nodeId} - Get limit by node
- PUT /{id} - Update limit
- GET /{nodeId}/capacity - Check capacity
- PUT /{nodeId}/toggle - Toggle fulfillment
- GET /alerts - Get capacity alerts
- GET /history/{nodeId} - Get capacity history
- POST /reset - Manual reset (admin)
```

#### Frontend Changes

**1. New Page: `FulfillmentLimitsPage.tsx`**
```typescript
- Limit configuration per node
- Capacity visualization (gauges)
- Alert management
- Toggle fulfillment on/off
- Capacity history chart
```

**2. New Component: `CapacityGauge.tsx`**
```typescript
- Visual gauge showing current vs max
- Color coding: green/yellow/red
- Alert threshold indicator
```

**3. API Module: `fulfillmentLimits.ts`**
```typescript
- createLimit(data)
- getLimits()
- getLimit(nodeId)
- updateLimit(id, data)
- checkCapacity(nodeId)
- toggleFulfillment(nodeId, enabled)
- getCapacityAlerts()
- getCapacityHistory(nodeId, dateRange)
```

#### Tests
- Unit tests for capacity checking
- Integration tests for limit enforcement
- Frontend tests for capacity UI

---

## Gap 10: Advance Label Generation

### HotWax Capability
- Pre-generate labels during pick
- Label printing queue
- Batch label printing
- Label status tracking
- Reprint capability

### Current NexusShip State
- `NxShipment` has `labelUrl` field
- `LabelPrintingPage.tsx` exists
- No advance label generation
- No label queue

### Implementation Plan

#### Backend Changes

**1. Entity: `NxLabelJob.java`**
```java
@Entity
@Table(name = "nx_label_jobs")
- id (UUID, PK)
- tenantId (UUID)
- shipmentId (UUID)
- orderId (UUID)
- status (String: "QUEUED", "GENERATING", "READY", "PRINTED", "FAILED", "VOIDED")
- labelUrl (String)
- labelFormat (String: "PNG", "PDF", "ZPL")
- carrierId (String)
- trackingNumber (String)
- printerId (UUID)
- generatedAt (LocalDateTime)
- printedAt (LocalDateTime)
- failedReason (String)
- retryCount (Integer)
- metadata (JSONB)
- createdAt, updatedAt
```

**2. Service: `LabelGenerationService.java`**
```java
@Service
- queueLabelGeneration(shipmentId): NxLabelJob
  - Creates label job in QUEUED status
  - Triggers async generation
  
- generateLabel(shipmentId): NxLabelJob
  - Calls carrier API for label
  - Stores label URL
  - Updates status to READY
  
- batchGenerateLabels(shipmentIds): List<NxLabelJob>
  - Batch label generation
  
- printLabel(jobId, printerId): NxLabelJob
  - Sends label to printer
  - Updates status to PRINTED
  
- reprintLabel(jobId): NxLabelJob
  - Reprints existing label
  
- getLabelJobs(filters): List<NxLabelJob>
- getLabelJob(id): NxLabelJob
- getLabelQueue(): List<NxLabelJob>
- voidLabel(jobId): NxLabelJob
```

**3. Migration: `V36__create_label_jobs.sql`**
```sql
CREATE TABLE nx_label_jobs (...);
CREATE INDEX idx_label_shipment ON nx_label_jobs(shipment_id);
CREATE INDEX idx_label_status ON nx_label_jobs(status);
CREATE INDEX idx_label_printer ON nx_label_jobs(printer_id);
```

**4. Async Handler: `LabelGenerationHandler.java`**
```java
@Component
@KafkaListener(topics = "label.generation.request")
- Listens for label generation requests
- Processes labels asynchronously
- Publishes completion events
```

**5. Controller: `LabelGenerationController.java`**
```java
@RestController
@RequestMapping("/api/v1/labels")
- POST /queue/{shipmentId} - Queue label generation
- POST /generate/{shipmentId} - Generate label (sync)
- POST /batch - Batch generate labels
- PUT /{id}/print - Print label
- PUT /{id}/reprint - Reprint label
- PUT /{id}/void - Void label
- GET /queue - Get label queue
- GET /{id} - Get label job details
- GET /shipment/{shipmentId} - Get label for shipment
```

#### Frontend Changes

**1. Update `LabelPrintingPage.tsx`**
- Add label queue view
- Add batch print functionality
- Add reprint capability
- Show label status tracking

**2. New Component: `LabelQueue.tsx`**
```typescript
- Queue of labels ready to print
- Select multiple for batch print
- Printer selection
- Print status indicators
```

**3. API Module: `labels.ts`**
```typescript
- queueLabelGeneration(shipmentId)
- generateLabel(shipmentId)
- batchGenerateLabels(shipmentIds)
- printLabel(jobId, printerId)
- reprintLabel(jobId)
- voidLabel(jobId)
- getLabelQueue()
- getLabelJob(id)
- getLabelForShipment(shipmentId)
```

#### Tests
- Unit tests for label generation logic
- Integration tests for async label processing
- Frontend tests for label queue UI

---

## Implementation Order

### Phase 1: Foundation (Weeks 1-2)
1. **Gap 2: Transfer Orders** - Core entity + service + migration
2. **Gap 7: Brokering Queue** - Core entity + service + migration
3. **Gap 9: Fulfillment Limits** - Core entity + service + migration

### Phase 2: Order Lifecycle (Weeks 3-4)
4. **Gap 1: ATP Calculation Engine** - Advanced ATP logic
5. **Gap 3: Pre-order Parking** - Parking + auto-release
6. **Gap 8: Order Approval Engine** - Fraud detection + approval workflow

### Phase 3: Store Operations (Weeks 5-6)
7. **Gap 4: BOPIS Operational App** - Store associate interface
8. **Gap 5: Rejection Handling** - Configurable rejections
9. **Gap 6: Picker Role & Assignment** - Named pickers + FIFO

### Phase 4: Integration (Week 7)
10. **Gap 10: Advance Label Generation** - Pre-generate during pick

### Phase 5: Testing & Polish (Week 8)
- Integration testing
- Performance testing
- Security audit
- Documentation

---

## Success Metrics

| Gap | Metric | Target |
|-----|--------|--------|
| 1 | ATP Calculation Accuracy | >99% |
| 2 | Transfer Order Processing Time | <5 minutes |
| 3 | Auto-release Success Rate | >95% |
| 4 | BOPIS Pickup Time | <15 minutes |
| 5 | Rejection Processing Time | <2 minutes |
| 6 | Picker Assignment Time | <30 seconds |
| 7 | Brokering Queue Processing | <10 minutes |
| 8 | Order Approval Time | <1 minute |
| 9 | Fulfillment Limit Enforcement | 100% |
| 10 | Label Generation Time | <30 seconds |

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Database migration conflicts | High | Use sequential version numbers, test migrations |
| API breaking changes | High | Version APIs, maintain backward compatibility |
| Frontend state management | Medium | Use React Query for server state |
| Performance degradation | Medium | Add indexes, implement pagination |
| Security vulnerabilities | High | Follow OWASP guidelines, security audit |

---

## Conclusion

This plan provides a comprehensive roadmap to close all HotWax gaps and surpass their capabilities. Each gap includes:
- Detailed entity/service/controller designs
- Migration scripts
- Frontend components
- Test coverage requirements

**Estimated Timeline:** 8 weeks
**Team Size:** 2-3 developers
**Priority:** High (strategic competitive advantage)

---

*Document Version: 1.0*
*Last Updated: $(date)*
*Author: NexusShip Architecture Team*
