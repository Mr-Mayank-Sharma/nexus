# Nexus WMS (parked)

Warehouse operations moved out of the OMS on 2026-10-21 (workstream W1, [docs/26](../docs/26-EXECUTION-PLAN.md)).
This folder is **reference only**: it is not part of any build, and nothing here is maintained until Nexus WMS is
rebuilt on the shared core and the OMS-WMS contract (W15).

What is here, with the paths it had in `nexus-oms-backend` and `nexus-oms-frontend`:

- `backend/src/main/java/com/nexus/oms/`: slotting, waves, pickers, labour and productivity, yard, dock doors and
  trailers, equipment and automation, RFID, ASN and inbound receiving, warehouse cycle counts, EDI automation,
  warehouse zones and bins (`WarehouseLayoutController`), and the 3D warehouse twin (`WarehouseTwinService`,
  `WarehouseTwinController`). 87 classes.
- `backend/src/test/java/com/nexus/oms/`: their tests (15 files).
- `frontend/src/pages/` and `frontend/src/features/twin/warehouse/`: the 12 WMS screens and the warehouse twin.

What stayed in the OMS: locations (`/warehouses` list, create, edit, delete and a space summary; the
**Locations** screen), store picking and packing, inventory, transfers, returns, procurement (without putaway
suggestions), and the order-flow twin.

The database tables behind this code are untouched; they are removed with the old model in W10.
