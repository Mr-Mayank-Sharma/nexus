# 27. OMS-WMS contract

How the Nexus OMS and a warehouse management system talk. The same contract applies to Nexus WMS (when it is rebuilt),
SAP EWM, Manhattan, a 3PL's system, or anything else that runs a warehouse. A connector for a third-party WMS maps its
own format to these messages; nothing in the OMS changes per WMS.

Field names follow the OFBiz-aligned model ([doc 23](23-DATA-MODEL-OFBIZ-ALIGNED.md)) in camelCase on the wire.

## 1. Who owns what

| Thing | Owner | The other side |
|---|---|---|
| The customer order, its status, promises to the customer and the channel | OMS | WMS sees only the fulfilment request |
| Which facility ships an order (routing) | OMS | WMS may reject or short-pick; it never re-routes |
| Network availability (what channels may sell) | OMS | — |
| Physical stock inside a WMS-run facility (on hand per product, lot and bin) | WMS | OMS holds a copy per product and facility, updated by stock messages |
| Reservation at facility level (this order needs 2 of SKU X from DC-EAST) | OMS | — |
| Reservation at bin level (which 2 units, from which bin) | WMS | — |
| How the warehouse picks, packs and loads (waves, bins, labour) | WMS | — |
| Carrier label and tracking | Either: whoever ships. Confirmed to the OMS in the shipment confirmation | — |
| Inbound expectations (transfers and purchase receipts the facility should expect) | OMS (for transfers) or ERP (for purchases) | WMS confirms what actually arrived |

Rule of thumb: the OMS decides *what* and *where*; the WMS decides *how* inside the building and reports *what actually happened*.

## 2. Transport and delivery guarantees

- **Carrier.** HTTPS with JSON bodies, signed with HMAC-SHA256 over the raw body (`X-Nexus-Signature: sha256=...`) and a
  shared secret per facility. File drop (SFTP, one JSON or CSV file per message) is supported for systems that can't call APIs.
- **Storage.** Every message in or out is a row in `system_message` (doc 23 section 3), with type, remote, status,
  attempts and errors. Nothing is sent from inside a business transaction; an outbox job sends committed messages.
- **Idempotency.** Every message has a `messageId` (UUID, chosen by the sender) and a business key (`idempotencyKey`).
  A receiver that sees an `idempotencyKey` it already processed returns the original result and does nothing else.
- **Ordering.** Messages about the same order or ship group carry `sequence` (a counter per `orderId` +
  `shipGroupSeqId`, starting at 1). A receiver processes them in sequence order and holds a message whose predecessor
  has not arrived (for up to 15 minutes, then raises an error). Stock messages carry `asOf` instead; an older stock
  message never overwrites a newer one.
- **Retries.** The sender retries on network errors and HTTP 5xx with backoff (1 min, 5 min, 15 min, then hourly for
  24 hours), then marks the message failed and alerts. HTTP 4xx is not retried: it means the message is wrong.
- **Acknowledgement.** HTTP 200 with `{ "status": "ACCEPTED" }` means stored and will be processed. Business outcomes
  come back as their own messages (for example a short-pick), never as the HTTP response.
- **Time.** All timestamps are ISO 8601 in UTC (`2026-10-21T14:05:00Z`). Quantities are decimals as strings
  (`"2"`, `"0.5"`) to avoid floating-point surprises.

### Envelope

```json
{
  "messageId": "7f1c9a0e-4f7b-4c4e-9d3f-2b8f0a5e6c11",
  "messageType": "FULFILMENT_REQUEST",
  "idempotencyKey": "FR:10042:00001:1",
  "sequence": 1,
  "facilityId": "DC-EAST",
  "sentAt": "2026-10-21T14:05:00Z",
  "payload": { }
}
```

### Errors (HTTP 4xx body)

```json
{ "status": "REJECTED", "errors": [ { "code": "UNKNOWN_PRODUCT", "field": "items[1].productId", "message": "Product 10027-RED-9 is not set up at DC-EAST" } ] }
```

Error codes: `INVALID_MESSAGE`, `UNKNOWN_FACILITY`, `UNKNOWN_PRODUCT`, `UNKNOWN_ORDER`, `OUT_OF_SEQUENCE`,
`ALREADY_SHIPPED`, `ALREADY_CANCELLED`, `NOT_ALLOWED`.

## 3. Messages

| # | Message | Direction | Purpose |
|---|---|---|---|
| 1 | `FULFILMENT_REQUEST` | OMS → WMS | Ship these lines to this address |
| 2 | `CANCEL_REQUEST` | OMS → WMS | Stop fulfilling all or some lines |
| 3 | `CANCEL_RESPONSE` | WMS → OMS | What could and could not be cancelled |
| 4 | `SHORT_PICK` | WMS → OMS | Lines (or part of them) cannot be fulfilled here |
| 5 | `SHIPMENT_CONFIRMATION` | WMS → OMS | What shipped, in which packages, with tracking |
| 6 | `STOCK_SNAPSHOT` | WMS → OMS | Full on-hand per product at the facility |
| 7 | `STOCK_CHANGE` | WMS → OMS | A change since the last snapshot, with a reason |
| 8 | `INBOUND_NOTICE` | OMS → WMS | Expect this transfer or return |
| 9 | `RECEIPT_CONFIRMATION` | WMS → OMS | What actually arrived for a transfer or purchase |
| 10 | `RETURN_RECEIVED` | WMS → OMS | A customer return arrived and was graded |

### 3.1 FULFILMENT_REQUEST (OMS → WMS)

Sent when routing assigns a ship group to a WMS-run facility (and its release time has passed).

| Field | Type | Notes |
|---|---|---|
| `orderId` | id | OMS order id |
| `externalId` | string | Channel order number, for labels and support |
| `shipGroupSeqId` | id | One request per ship group |
| `productStoreId` | id | Brand or store, for packing slips and carrier accounts |
| `shipmentMethodTypeId`, `carrierPartyId` | id | Requested service (`NEXT_DAY`, `STANDARD`) and carrier, if chosen by the OMS |
| `shipByDate`, `promisedDatetime` | timestamp | Latest ship time and the date promised to the customer |
| `priority` | `1`-`9` | 1 is most urgent |
| `shipTo` | object | `toName`, `address1`, `address2`, `city`, `stateProvinceGeoId`, `postalCode`, `countryGeoId`, `phone`, `email` |
| `isGift`, `giftMessage` | | |
| `items[]` | | `orderItemSeqId`, `productId`, `goodIdentifications` (SKU, UPC), `quantity`, `unitPrice`, `itemDescription` |
| `itemGroups[]` | | Lines that must ship together in one package (`orderItemGroupSeqId`, `orderItemSeqIds`) |

```json
{
  "orderId": "10042", "externalId": "SHOP-#5521", "shipGroupSeqId": "00001", "productStoreId": "BRAND_A",
  "shipmentMethodTypeId": "STANDARD", "carrierPartyId": "UPS",
  "shipByDate": "2026-10-22T20:00:00Z", "promisedDatetime": "2026-10-24T22:00:00Z", "priority": 5,
  "shipTo": { "toName": "Asha Rao", "address1": "12 Hill Rd", "city": "Edison", "stateProvinceGeoId": "US-NJ", "postalCode": "08817", "countryGeoId": "USA", "phone": "+1 732 555 0100" },
  "items": [
    { "orderItemSeqId": "00001", "productId": "10027-RED-9", "goodIdentifications": { "SKU": "10027-RED-9", "UPCA": "012345678905" }, "quantity": "1", "unitPrice": "79.00" },
    { "orderItemSeqId": "00002", "productId": "20011-BLK-M", "quantity": "2", "unitPrice": "25.00" }
  ]
}
```

The WMS accepts the request (HTTP 200) or rejects it (4xx, for example `UNKNOWN_PRODUCT`). Accepting means it will
either ship it or send a `SHORT_PICK`.

### 3.2 CANCEL_REQUEST and CANCEL_RESPONSE

Request: `orderId`, `shipGroupSeqId`, `items[]` (`orderItemSeqId`, `quantity`; omit `items` to cancel the whole
ship group), `reasonEnumId`.

Response: per line, `cancelledQuantity` and `notCancelledQuantity` with `reason` (`ALREADY_PACKED`, `ALREADY_SHIPPED`).
Whatever was not cancelled will still arrive in a `SHIPMENT_CONFIRMATION`; the OMS then handles it as a return or
refund, never by assuming the cancel worked.

### 3.3 SHORT_PICK (WMS → OMS)

`orderId`, `shipGroupSeqId`, `items[]` (`orderItemSeqId`, `shortQuantity`), `reasonEnumId`
(`NOT_FOUND`, `DAMAGED`, `QUALITY_HOLD`, `WRONG_ITEM_IN_BIN`), `stockAdjusted` (true when the WMS already lowered its own
on-hand; the matching `STOCK_CHANGE` follows).

The OMS releases the facility reservation for the short quantity and re-routes it (excluding this facility for that
line, `excluded_order_facility`), splitting only if the store's split rules allow.

### 3.4 SHIPMENT_CONFIRMATION (WMS → OMS)

One per shipment; a ship group may ship in several.

| Field | Notes |
|---|---|
| `shipmentId` | WMS's id, kept as an external id |
| `orderId`, `shipGroupSeqId` | |
| `shippedAt` | |
| `carrierPartyId`, `shipmentMethodTypeId` | Actual carrier and service |
| `packages[]` | `packageSeqId`, `trackingCode`, `trackingUrl`, `weight`, `weightUomId`, `boxTypeId`, `contents[]` (`orderItemSeqId`, `quantity`, `lotId`, `serialNumbers[]`) |

```json
{
  "shipmentId": "WMS-SH-88812", "orderId": "10042", "shipGroupSeqId": "00001", "shippedAt": "2026-10-22T17:41:00Z",
  "carrierPartyId": "UPS", "shipmentMethodTypeId": "STANDARD",
  "packages": [
    { "packageSeqId": "1", "trackingCode": "1Z999AA10123456784", "weight": "1.8", "weightUomId": "WT_kg",
      "contents": [ { "orderItemSeqId": "00001", "quantity": "1" }, { "orderItemSeqId": "00002", "quantity": "2" } ] }
  ]
}
```

The OMS records the shipment and packages (item to package to tracking), consumes the reservation, sets line
statuses, tells the channel, and sends the customer a message.

### 3.5 STOCK_SNAPSHOT and STOCK_CHANGE (WMS → OMS)

Snapshot: `asOf`, `items[]` (`productId`, `quantityOnHand`, `quantityAvailable` (on hand minus what the WMS holds for
damage, quality or counts), optional `lots[]`). Sent at least daily and after a full count. The OMS replaces its copy
for that facility when `asOf` is newer than what it holds.

Change: `asOf`, `productId`, `quantityOnHandDiff`, `quantityAvailableDiff`, `reasonEnumId`
(`RECEIPT`, `SHIPMENT`, `ADJUSTMENT`, `DAMAGE`, `COUNT_VARIANCE`, `RETURN`, `TRANSFER_OUT`, `TRANSFER_IN`),
and the related `orderId`/`shipmentId`/`returnId` when there is one.

The OMS never counts stock it has itself shipped twice: a `SHIPMENT` change for a confirmed shipment is matched to it
and only its timing is used.

### 3.6 INBOUND_NOTICE and RECEIPT_CONFIRMATION

Notice (OMS → WMS): `inboundId` (transfer or return id), `inboundType` (`TRANSFER`, `RETURN`), `fromFacilityId`,
`expectedArrival`, `items[]` (`productId`, `quantity`, `lotId`).

Receipt (WMS → OMS): `inboundId` (or the ERP's purchase order id), `receivedAt`, `items[]` (`productId`,
`quantityAccepted`, `quantityRejected`, `rejectionReasonEnumId`). The OMS closes the transfer, records differences
against the tolerance rule (doc 24 section 4b) and makes accepted stock sellable.

### 3.7 RETURN_RECEIVED

`returnId` (OMS) or `externalReturnId`, `receivedAt`, `items[]` (`returnItemSeqId` or `productId`,
`quantityReceived`, `grade` (`RESELLABLE`, `DAMAGED`, `DEFECTIVE`), `disposition` (`RESTOCK`, `QUARANTINE`,
`RETURN_TO_VENDOR`, `DISPOSE`)). The OMS updates the return, triggers the refund rules and makes resellable units
available.

## 4. Status mapping

| OMS line status | Set when |
|---|---|
| `ITEM_APPROVED` | Order approved; not yet sent |
| `ITEM_APPROVED` + ship group `facilityId` set | Routed; `FULFILMENT_REQUEST` sent |
| `ITEM_COMPLETED` | Covered by a `SHIPMENT_CONFIRMATION` |
| `ITEM_CANCELLED` | Cancelled before shipping (confirmed by `CANCEL_RESPONSE`) |
| back to unrouted | Short-picked; re-routing starts |

## 5. Versioning

`messageType` names are fixed. New optional fields may be added at any time; receivers ignore fields they don't know.
A breaking change gets a new type name (`FULFILMENT_REQUEST_V2`) and both are accepted for at least six months.

## 6. Conformance tests

A WMS connector is accepted when it passes the contract tests: a fixed script of messages (request, partial
short-pick, two shipments, cancel after packing, stock change during picking, duplicate and out-of-order delivery)
with the expected final state of the order, reservations and stock in the OMS.
