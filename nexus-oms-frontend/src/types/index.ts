export interface Order {
  id: string
  orderNumber: string
  customerName: string
  customerEmail: string
  channel: OrderChannel
  status: OrderStatus
  items: OrderItem[]
  total: number
  subtotal: number
  shippingCost: number
  tax: number
  currency: string
  shippingAddress: Address
  billingAddress: Address
  fulfillmentType: FulfillmentType
  allocationNodeId?: string
  carrier?: string
  trackingNumber?: string
  promisedDeliveryDate?: string
  estimatedShipDate?: string
  shippedDate?: string
  deliveredDate?: string
  createdAt: string
  updatedAt: string
  priority: Priority
  hasException: boolean
  exceptionReason?: string
  tags: string[]
  notes: string
}

export interface OrderItem {
  id?: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  imageUrl?: string
}

export interface Address {
  street: string
  city: string
  state: string
  zip: string
  country: string
}

export type OrderChannel = 'SHOPIFY' | 'AMAZON' | 'WOOCOMMERCE' | 'MANUAL' | 'API'
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'ALLOCATED' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED' | 'EXCEPTION' | 'RETURNED'
export type FulfillmentType = 'STANDARD' | 'EXPRESS' | 'SAME_DAY' | 'CROSS_BORDER'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface Inventory {
  sku: string
  productName: string
  category: string
  nodeId: string
  nodeName: string
  quantityOnHand: number
  quantityAllocated: number
  quantityReserved: number
  quantityInTransit: number
  quantityDamaged: number
  atp: number
  safetyStock: number
  reorderPoint: number
  unitCost: number
  totalValue: number
  lastCountedAt: string
  updatedAt: string
}

export interface Shipment {
  id: string
  orderId: string
  orderNumber: string
  carrier: string
  service: string
  trackingNumber: string
  status: ShipmentStatus
  shipDate: string
  estimatedDelivery: string
  actualDelivery?: string
  weight: number
  dimensions: string
  shippingCost: number
  labels: ShipmentLabel[]
  events: ShipmentEvent[]
  createdAt: string
  updatedAt: string
}

export interface ShipmentLabel {
  id: string
  format: string
  url: string
  size: string
}

export interface ShipmentEvent {
  timestamp: string
  location: string
  status: string
  description: string
}

export type ShipmentStatus = 'CREATED' | 'LABELED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'EXCEPTION'

export interface DashboardKPI {
  id: string
  label: string
  value: string
  change: number
  trend: 'up' | 'down' | 'neutral'
  icon: string
  prefix?: string
  suffix?: string
}

export interface OrderVelocity {
  hour: string
  orders: number
  fulfilled: number
}

export interface CarrierPerformance {
  carrier: string
  totalShipments: number
  otdRate: number
  avgTransitDays: number
  damageRate: number
  avgCostPerShipment: number
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  errors?: string[]
  pagination?: {
    page: number
    limit: number
    total: number
    totalPages: number
  }
}

export type UserRole =
  | 'ADMIN'
  | 'CEO'
  | 'OPS_MANAGER'
  | 'WAREHOUSE_MANAGER'
  | 'PICKER'
  | 'PACKER'
  | 'LOADER'
  | 'STORE_MANAGER'
  | 'BOPIS_OWNER'
  | 'CUSTOMER_SUPPORT'
  | 'PROCUREMENT_MANAGER'
  | 'FINANCE'
  | 'LOGISTICS_MANAGER'
  | 'VIEWER'

export const ROLE_HIERARCHY: Record<UserRole, number> = {
  ADMIN: 100,
  CEO: 90,
  OPS_MANAGER: 80,
  WAREHOUSE_MANAGER: 70,
  STORE_MANAGER: 70,
  LOGISTICS_MANAGER: 70,
  PROCUREMENT_MANAGER: 60,
  FINANCE: 60,
  BOPIS_OWNER: 60,
  CUSTOMER_SUPPORT: 50,
  PICKER: 40,
  PACKER: 40,
  LOADER: 40,
  VIEWER: 10,
}

export interface User {
  id: string
  username: string
  email: string
  fullName: string
  role: UserRole
  avatar?: string
  permissions: string[]
  securityGroups?: string[]
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  role: string
  username: string
  tenantId?: string
  tenantName?: string
  mfaRequired?: boolean
  mfaToken?: string
  email?: string
  fullName?: string
  passwordResetRequired?: boolean
  ssoProvider?: string
  permissions?: string[]
  securityGroups?: string[]
}

export interface LoginRequest {
  username: string
  password: string
  tenantId?: string
  rememberMe?: boolean
}

export interface MfaVerificationRequest {
  mfaToken: string
  totpCode: string
}

export interface SsoLoginRequest {
  provider: string
  idToken: string
  tenantId?: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface TenantInfo {
  id: string
  name: string
  domain?: string
  logoUrl?: string
  isActive: boolean
  plan?: string
}

export interface PaginationParams {
  page?: number
  limit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface OrderFilters extends PaginationParams {
  search?: string
  channel?: OrderChannel
  status?: OrderStatus
  fulfillmentType?: FulfillmentType
  priority?: Priority
  hasException?: boolean
  dateFrom?: string
  dateTo?: string
}

export interface InventoryFilters extends PaginationParams {
  search?: string
  nodeId?: string
  category?: string
  lowStock?: boolean
}

export interface OrderTimelineEvent {
  id: string
  type: 'CREATED' | 'CONFIRMED' | 'ALLOCATED' | 'SHIPPED' | 'DELIVERED' | 'EXCEPTION' | 'CANCELLED' | 'NOTE'
  title: string
  description: string
  timestamp: string
  user?: string
}

export interface AiModel {
  id: string
  name: string
  description: string
  version: string
  type: 'DEMAND_FORECAST' | 'ALLOCATION' | 'CARRIER_SELECTION' | 'ANOMALY_DETECTION' | 'RETURNS_PREDICTION' | 'INVENTORY_OPTIMIZATION'
  status: 'ACTIVE' | 'TRAINING' | 'ERROR' | 'DISABLED'
  accuracy: number
  precision: number
  recall: number
  f1Score: number
  lastTrained: string
  trainingHistory: TrainingRun[]
}

export interface AiPlatformModel {
  id: string
  tenantId?: string
  name: string
  displayName?: string
  description?: string
  modelType: string
  category: 'GLOBAL' | 'TENANT' | 'HYBRID'
  baseModelId?: string
  status: string
  currentVersion?: string
  inputSchema?: string
  outputSchema?: string
  config?: string
  tags?: string
  isActive?: boolean
  createdAt: string
  updatedAt: string
}

export interface TrainingRun {
  id: string
  startedAt: string
  completedAt?: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  accuracy: number
  loss: number
  epochs: number
  datasetSize: number
}

export interface ReturnItem {
  id?: string
  sku: string
  productName: string
  quantity: number
  returnReason?: string
  returnReasonDetail?: string
  condition?: string
  conditionNotes?: string
  grade?: string
  disposition?: Disposition
  refundAmount?: number
  status?: string
  orderItemId?: string
}

export interface Return {
  id: string
  rmaNumber: string
  orderId: string
  orderNumber?: string
  customerId?: string
  customerName: string
  customerEmail?: string
  status: ReturnStatus
  items: ReturnItem[]
  reason: string
  grade?: string
  disposition?: string
  refundAmount: number
  refundReference?: string
  refundStatus: RefundStatus
  returnChannel?: string
  rmaType?: string
  returnLabelUrl?: string
  returnTrackingNumber?: string
  rejectedReason?: string
  exchangeOrderId?: string
  createdAt: string
  updatedAt: string
}

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'IN_TRANSIT' | 'RECEIVED' | 'INSPECTED' | 'REFUNDED' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
export type Disposition = 'PENDING' | 'RESTOCK' | 'REFURBISH' | 'RETURN_TO_VENDOR' | 'DISPOSE' | 'DONATE'
export type RefundStatus = 'PENDING' | 'PROCESSED' | 'COMPLETED' | 'FAILED'

export interface RoutingRule {
  id: string
  tenantId: string
  name: string
  description?: string
  priority: number
  ruleType: string
  conditions?: string
  actions?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface InventoryReceipt {
  id: string
  tenantId: string
  nodeId: string
  receiptType: string
  referenceNumber?: string
  sku: string
  productName?: string
  quantity: number
  unitCost?: number
  lotNumber?: string
  expiryDate?: string
  status: string
  receivedBy?: string
  createdAt: string
  receivedAt?: string
}

export interface CycleCount {
  id: string
  tenantId: string
  nodeId: string
  sku: string
  productName?: string
  expectedQty: number
  countedQty?: number
  status: string
  countedBy?: string
  notes?: string
  createdAt: string
  countedAt?: string
  updatedAt: string
}

export interface Node {
  id: string
  tenantId: string
  name: string
  type: string
  address?: Address
  isActive: boolean
}

export interface SyncLog {
  id: string
  tenantId: string
  integrationType: string
  syncType: string
  status: string
  startedAt: string
  completedAt?: string
  itemsProcessed: number
  itemsSucceeded: number
  itemsFailed: number
  errorMessage?: string
  details?: string
}

export interface Picklist {
  id: string
  tenantId: string
  name: string
  waveType: 'SINGLE_ORDER' | 'BATCH' | 'WAVE' | 'ZONE'
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  status: 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  assigneeId?: string
  totalItems: number
  pickedItems: number
  orderIds?: string[]
  notes?: string
  startedAt?: string
  completedAt?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface PicklistItem {
  id: string
  picklistId: string
  tenantId: string
  orderId: string
  orderItemId?: string
  sku: string
  productName?: string
  quantity: number
  pickedQuantity: number
  fromBinId?: string
  fromLocation?: string
  status: 'PENDING' | 'PICKING' | 'PICKED' | 'SKIPPED' | 'CANCELLED'
  pickedAt?: string
  pickedBy?: string
  notes?: string
  createdAt: string
}

export interface NxPackage {
  id: string
  tenantId: string
  orderId: string
  picklistId?: string
  packageType: 'BOX' | 'PALLET' | 'CRATE' | 'ENVELOPE' | 'TUBE' | 'BAG'
  boxName?: string
  weightLbs?: number
  widthIn?: number
  heightIn?: number
  depthIn?: number
  items?: string
  itemCount: number
  trackingNumber?: string
  carrierId?: string
  carrierName?: string
  serviceLevel?: string
  labelUrl?: string
  labelFormat?: string
  shippingCost?: number
  status: 'PENDING_PACK' | 'PACKING' | 'PACKED' | 'LABELED' | 'SHIPPED' | 'VOIDED'
  notes?: string
  packedBy?: string
  packedAt?: string
  shippedAt?: string
  createdAt: string
  updatedAt: string
}

export interface Customer {
  id: string
  tenantId: string
  externalId?: string
  name: string
  email?: string
  phone?: string
  address?: string
  createdAt: string
}

export interface WarehouseStaff {
  id: string
  tenantId: string
  warehouseId: string
  userId?: string
  name: string
  email: string
  role: string
  shift: string
  isActive: boolean
  pickCount?: number
  itemsPickedToday?: number
  itemsPackedToday?: number
  createdAt: string
}

export interface OrderAllocation {
  id: string
  orderId: string
  tenantId: string
  nodeId?: string
  nodeName?: string
  nodeType: string
  priority: number
  quantityAllocated: number
  quantityRequested: number
  status: 'PENDING' | 'ALLOCATED' | 'PARTIALLY_ALLOCATED' | 'FAILED' | 'CANCELLED'
  deliveryPromiseDate?: string
  deliveryPromiseConfidence: number
  allocationStrategy: 'RULE_BASED' | 'AI_OPTIMIZED' | 'HYBRID' | 'MANUAL'
  ruleId?: string
  ruleName?: string
  costEstimated: number
  distanceKm?: number
  allocatedAt?: string
  allocatedBy?: string
  createdAt: string
  updatedAt: string
}

export interface FulfillmentException {
  id: string
  orderId: string
  allocationId?: string
  tenantId: string
  type: 'INVENTORY_SHORTAGE' | 'CARRIER_FAILURE' | 'CAPACITY_EXCEEDED' | 'WORKER_ABSENT' | 'WEATHER_DELAY' | 'SHIPPING_ADDRESS_ISSUE' | 'PAYMENT_HOLD' | 'CREDIT_HOLD' | 'FRAUD_FLAG' | 'CUSTOMER_REQUEST' | 'SYSTEM_ERROR' | 'OTHER'
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ESCALATED' | 'CLOSED'
  title: string
  description?: string
  resolution?: string
  suggestedAction?: string
  autoResolvable: boolean
  resolutionStrategy?: string
  detectedAt: string
  resolvedAt?: string
  resolvedBy?: string
  assignedTo?: string
  escalatedAt?: string
  createdAt: string
  updatedAt: string
}

export interface EmailParsedOrder {
  id: string
  tenantId: string
  emailMessageId?: string
  emailSubject?: string
  emailFrom?: string
  emailTo?: string
  emailReceivedAt?: string
  attachmentFilename?: string
  attachmentType?: 'PDF' | 'CSV' | 'HTML' | 'TEXT' | 'NONE'
  parsedData?: Record<string, any>
  orderId?: string
  status: 'NEW' | 'PARSED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'DUPLICATE' | 'FAILED'
  confidenceScore: number
  customerName?: string
  customerEmail?: string
  customerPhone?: string
  orderTotal?: number
  itemCount: number
  shippingAddress?: Record<string, any>
  rejectionReason?: string
  matchedCustomerId?: string
  createdAt: string
  processedAt?: string
}

export interface EdiDocument {
  id: string
  tenantId: string
  docType: '850' | '856' | '810'
  filename?: string
  rawContent?: string
  parsedStatus: 'PENDING' | 'PARSED' | 'FAILED' | 'VALIDATED' | 'ERROR'
  parsedData?: Record<string, any>
  validationErrors?: string[]
  orderId?: string
  shipmentId?: string
  invoiceId?: string
  partnerId?: string
  partnerName?: string
  controlNumber?: string
  interchangeControlNumber?: string
  groupControlNumber?: string
  testIndicator: boolean
  processedAt?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface EdiPartner {
  id: string
  tenantId: string
  partnerCode: string
  partnerName: string
  qualifier: string
  interchangeId?: string
  isActive: boolean
  supportedDocs?: string[]
  createdAt: string
  updatedAt: string
}

export interface AllocationResult {
  orderId: string
  strategy: string
  status: string
  allocations: OrderAllocation[]
  estimatedDeliveryDate?: string
  confidenceScore: number
  totalCost: number
  exceptions: FulfillmentException[]
  explanation: string
  executionTimeMs: number
}

export interface RateQuote {
  carrierCode: string
  carrierName: string
  serviceLevel: string
  serviceName: string
  totalCost: number
  baseRate: number
  perKgCharge: number
  fuelSurcharge: number
  residentialSurcharge: number
  transitDaysMin: number
  transitDaysMax: number
  estimatedDelivery: string
  recommendation?: string
  zone?: string
}

export interface RateShoppingResult {
  fromZip: string
  toZip: string
  toCountry: string
  totalWeightKg: number
  declaredValue: number
  numPackages: number
  rates: RateQuote[]
  fastest?: RateQuote
  cheapest?: RateQuote
  bestValue?: RateQuote
  selected?: RateQuote
  executionTimeMs: number
}

export interface Product {
  id: string
  sku: string
  productName: string
  description?: string
  category?: string
  unitPrice: number
  costPrice?: number
  imageUrl?: string
  weight?: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface Workflow {
  id: string
  name: string
  description: string
  category: string
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT' | 'ARCHIVED'
  triggerType: 'MANUAL' | 'SCHEDULED' | 'EVENT'
  triggerConfig: Record<string, any>
  isActive: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface WorkflowStep {
  id: string
  workflowId: string
  name: string
  type: string
  config: Record<string, any>
  order: number
  isActive: boolean
  createdAt: string
}

export interface WorkflowExecution {
  id: string
  workflowId: string
  workflowName: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  triggerType: string
  startedAt: string
  completedAt?: string
  duration?: number
  input: Record<string, any>
  output: Record<string, any>
  errorMessage?: string
  createdBy: string
}

export interface Document {
  id: string
  name: string
  description: string
  type: string
  mimeType: string
  size: number
  category: string
  tags: string[]
  entityType: string
  entityId: string
  currentVersion: number
  uploadedBy: string
  uploadedByName: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface DocumentVersion {
  id: string
  documentId: string
  version: number
  mimeType: string
  size: number
  url: string
  uploadedBy: string
  notes: string
  createdAt: string
}

export interface Supplier {
  id: string
  name: string
  code: string
  status: 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED'
  category: string
  taxId: string
  paymentTerms: string
  currency: string
  primaryContactName: string
  primaryContactEmail: string
  primaryContactPhone: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  rating: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface SupplierContact {
  id: string
  supplierId: string
  name: string
  email: string
  phone: string
  title: string
  isPrimary: boolean
}

export interface SupplierContract {
  id: string
  supplierId: string
  title: string
  startDate: string
  endDate: string
  value: number
  terms: string
  status: 'ACTIVE' | 'EXPIRED' | 'TERMINATED'
}

export interface PurchaseRequest {
  id: string
  requestNumber: string
  title: string
  description: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CONVERTED'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
  requestedBy: string
  department: string
  items: PurchaseRequestItem[]
  totalAmount: number
  notes: string
  createdAt: string
  updatedAt: string
}

export interface PurchaseRequestItem {
  id: string
  requestId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  notes: string
}

export interface PurchaseOrder {
  id: string
  poNumber: string
  supplierId: string
  supplierName: string
  requestId: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'SENT' | 'CONFIRMED' | 'SHIPPED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED'
  items: PurchaseOrderItem[]
  subtotal: number
  tax: number
  shippingCost: number
  totalAmount: number
  paymentTerms: string
  expectedDeliveryDate: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface PurchaseOrderItem {
  id: string
  poId: string
  sku: string
  productName: string
  quantity: number
  quantityReceived: number
  unitPrice: number
  totalPrice: number
}

export interface Rfq {
  id: string
  rfqNumber: string
  title: string
  description: string
  status: 'DRAFT' | 'SENT' | 'UNDER_REVIEW' | 'AWARDED' | 'CANCELLED'
  items: RfqItem[]
  supplierIds: string[]
  responseDeadline: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface RfqItem {
  id: string
  rfqId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
}

export interface RfqResponse {
  id: string
  rfqId: string
  supplierId: string
  supplierName: string
  items: RfqResponseItem[]
  totalAmount: number
  paymentTerms: string
  deliveryDate: string
  notes: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  submittedAt: string
}

export interface RfqResponseItem {
  id: string
  responseId: string
  sku: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface Warehouse {
  id: string
  name: string
  code: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  status: string
  capacity: number
  utilizedCapacity: number
  managerName: string
  contactEmail: string
  contactPhone: string
  timezone: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface WarehouseZone {
  id: string
  warehouseId: string
  name: string
  code: string
  type: 'RECEIVING' | 'PICKING' | 'PACKING' | 'STORAGE' | 'SHIPPING'
  capacity: number
  utilizedCapacity: number
  isActive: boolean
  createdAt: string
}

export interface WarehouseBin {
  id: string
  warehouseId: string
  zoneId: string
  code: string
  aisle: string
  rack: string
  shelf: string
  position: string
  type: string
  width: number
  height: number
  depth: number
  maxWeight: number
  currentWeight: number
  status: 'EMPTY' | 'OCCUPIED' | 'RESERVED' | 'BLOCKED'
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface WarehouseEquipment {
  id: string
  warehouseId: string
  name: string
  type: 'FORKLIFT' | 'PALLET_JACK' | 'CONVEYOR' | 'SCANNER' | 'DOLLY' | 'OTHER'
  status: 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE' | 'OUT_OF_SERVICE'
  model: string
  serialNumber: string
  lastMaintenanceDate: string
  nextMaintenanceDate: string
  isActive: boolean
  createdAt: string
}

export interface AiSuggestion {
  actionType: string
  label: string
  description: string
  confidence: number
  orderId: string
}

export interface AiActionHistory {
  id: string
  actionType: string
  label: string
  status: 'SUCCESS' | 'FAILED'
  actor: string
  details: string
  timestamp: string
}

export interface AiExecuteRequest {
  actionType: string
  autoExecute?: boolean
}

export interface AuditEntry {
  id: string
  tenantId: string
  flowId?: string
  messageId?: string
  entityType: string
  entityId: string
  action: string
  status: string
  requestPayload?: string
  responsePayload?: string
  sourceSystem?: string
  targetSystem?: string
  processingTimeMs?: number
  errorMessage?: string
  ipAddress?: string
  userAgent?: string
  createdBy?: string
  createdAt: string
}

export interface AuditPage {
  content: AuditEntry[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface BigCommerceConfig {
  id: string
  tenantId: string
  storeHash: string
  accessToken: string
  clientId?: string
  apiPath: string
  isActive: boolean
  autoSyncOrders: boolean
  autoSyncInventory: boolean
  syncIntervalMinutes: number
  lastOrderSyncAt?: string
  lastProductSyncAt?: string
  lastInventorySyncAt?: string
}

export interface SyncResult {
  syncLogId: string
  syncType: string
  status: string
  itemsProcessed: number
  itemsSucceeded: number
  itemsFailed: number
  message?: string
}

export interface ImportResult {
  entityType: string
  fileName: string
  format: string
  totalRecords: number
  successCount: number
  errorCount: number
  errors: string[]
  warnings: string[]
  skippedCount: number
  processingTimeMs: number
}

export interface ImportFormat {
  id: string
  label: string
  extensions: string
}

export interface ImportHistorySummary {
  id: string
  fileName: string
  importType: string
  fileFormat: string
  importMode: string
  status: string
  totalRecords: number
  successCount: number
  failedCount: number
  duplicateCount: number
  processingTimeMs: number
  startedAt: string
  completedAt: string
}

export interface ImportRecordLogEntry {
  id: string
  rowNumber: number
  status: string
  errorCode: string | null
  errorMessage: string | null
  suggestedResolution: string | null
  stage: string
  createdAt: string
}

export interface ImportHistoryDetail extends ImportHistorySummary {
  storedFilePath: string | null
  errorFilePath: string | null
  fileSizeBytes: number
}

export interface PagedResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}

export interface IntegrationStore {
  id: string
  tenantId: string
  storeCode: string
  storeName: string
  platform: string
  platformType?: string
  status: string
  currency: string
  defaultLocale: string
  timezone: string
  externalStoreId?: string
  externalDomain?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface IntegrationStoreSetting {
  id: string
  storeId: string
  settingType: string
  settingValue?: string
  description?: string
  isEncrypted: boolean
}

export interface SyncTypeStatus {
  syncType: string
  enabled: boolean
  intervalMinutes: number
  lastSyncAt?: string
  lastSyncStatus?: string
  lastSyncMessage?: string
}

export interface StoreSyncStatus {
  storeId: string
  storeCode: string
  storeName: string
  platform: string
  connected: boolean
  syncTypes: SyncTypeStatus[]
  lastError?: string
}

export interface ConnectorMetadata {
  name: string
  version: string
  vendor: string
  platformType: string
  category: string
  description: string
  supportedSyncTypes: string[]
  supportedProtocols: string[]
  supportedAuthTypes: string[]
  requiredSettings: string[]
  supportsWebhooks: boolean
}

export interface ConnectorInstance {
  id: string
  platform: string
  name: string
  category: string
  status: Record<string, any>
  health: { status: string; lastSuccessAt: string; lastErrorAt: string; consecutiveFailures: number }
  supportedSyncTypes: string[]
}

export interface BatchJob {
  jobId: string
  connectorId: string
  syncType: string
  status: string
  itemsSucceeded: number
  itemsFailed: number
  error: string
  startedAt: string
  completedAt: string
  durationMs: number
}

export interface Invoice {
  id: string
  invoiceNumber: string
  orderId: string
  orderNumber: string
  supplierId?: string
  supplierName?: string
  customerName: string
  customerEmail: string
  status: 'DRAFT' | 'SENT' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'REFUNDED'
  items: InvoiceItem[]
  subtotal: number
  tax: number
  taxRate: number
  shippingCost: number
  discount: number
  totalAmount: number
  amountPaid: number
  amountDue: number
  currency: string
  dueDate: string
  issuedDate: string
  paidDate?: string
  notes: string
  createdAt: string
  updatedAt: string
}

export interface InvoiceItem {
  id: string
  invoiceId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface Payment {
  id: string
  invoiceId: string
  invoiceNumber: string
  transactionId: string
  amount: number
  currency: string
  method: 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER' | 'CHECK' | 'CASH' | 'WIRE_TRANSFER' | 'OTHER'
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED'
  reference: string
  notes: string
  processedAt: string
  createdAt: string
}

export interface CreditMemo {
  id: string
  creditMemoNumber: string
  invoiceId: string
  invoiceNumber: string
  reason: string
  items: CreditMemoItem[]
  subtotal: number
  tax: number
  totalAmount: number
  status: 'DRAFT' | 'ISSUED' | 'APPLIED' | 'VOID'
  issuedDate: string
  appliedDate?: string
  notes: string
  createdAt: string
}

export interface CreditMemoItem {
  id: string
  creditMemoId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  totalPrice: number
}

export interface NotificationTemplate {
  id: string
  code: string
  name: string
  description: string
  channel: 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP' | 'SLACK'
  subject: string
  body: string
  variables: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface NotificationLog {
  id: string
  templateCode: string
  channel: string
  recipient: string
  subject: string
  body: string
  status: 'SENT' | 'DELIVERED' | 'FAILED' | 'READ'
  errorMessage?: string
  readAt?: string
  sentAt: string
  createdAt: string
}

export interface AlertRule {
  id: string
  name: string
  description: string
  metric: string
  condition: 'GREATER_THAN' | 'LESS_THAN' | 'EQUAL_TO' | 'NOT_EQUAL_TO'
  threshold: number
  channel: string
  recipients: string[]
  isActive: boolean
  cooldownMinutes: number
  lastTriggeredAt?: string
  createdAt: string
  updatedAt: string
}

export interface RolePermission {
  id: string
  role: string
  resource: string
  action: 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'ALL'
  conditions?: Record<string, any>
  isGranted: boolean
  createdAt: string
  updatedAt: string
}

export interface UserRole {
  id: string
  userId: string
  userName: string
  userEmail: string
  role: string
  grantedBy: string
  grantedAt: string
  expiresAt?: string
}

export interface Team {
  id: string
  name: string
  description: string
  memberCount: number
  roles: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CompanySettings {
  id: string
  companyName: string
  legalName: string
  taxId: string
  registrationNumber: string
  logo: string
  address: {
    street: string
    city: string
    state: string
    zip: string
    country: string
  }
  phone: string
  email: string
  website: string
  timezone: string
  currency: string
  dateFormat: string
  languages: string[]
  featureFlags: Record<string, boolean>
  securityPolicy: {
    passwordMinLength: number
    requireSpecialChars: boolean
    requireNumbers: boolean
    requireUppercase: boolean
    maxLoginAttempts: number
    sessionTimeoutMinutes: number
    twoFactorRequired: boolean
    allowedIpRanges: string[]
  }
  createdAt: string
  updatedAt: string
}

export interface AiModelVersion {
  id: string
  modelId: string
  version: string
  modelFileUrl?: string
  accuracy?: number
  precision?: number
  recall?: number
  f1Score?: number
  latencyMs?: number
  status: string
  createdAt: string
}

export interface AiTrainingJob {
  id: string
  modelId: string
  name?: string
  version?: string
  status: string
  jobType: string
  accuracy?: number
  precision?: number
  recall?: number
  f1Score?: number
  loss?: number
  epochs?: number
  datasetSize?: number
  durationSeconds?: number
  errorMessage?: string
  startedAt?: string
  completedAt?: string
  createdAt: string
}

export interface AiFeatureDefinition {
  id: string
  name: string
  displayName?: string
  description?: string
  featureGroup: string
  dataType: string
  entityType?: string
  isActive?: boolean
}

export interface AiDeployment {
  id: string
  modelId: string
  versionId: string
  environment: string
  status: string
  deployedAt: string
}

export interface AiInferenceLog {
  id: string
  modelId: string
  versionId?: string
  input: string
  output: string
  latencyMs: number
  confidence?: number
  status?: string
  fallbackUsed?: boolean
  fallbackReason?: string
  ruleEngineUsed?: boolean
  cost?: number
  tokensUsed?: number
  createdAt: string
}

export interface AiRuleFallback {
  name: string
  type?: string
  condition?: string
  action?: string
  priority?: number
}

export interface AiExperiment {
  id: string
  tenantId: string
  modelId: string
  name: string
  description?: string
  experimentType?: string
  championVersionId?: string
  challengerVersionId?: string
  trafficSplit?: number
  successMetric?: string
  status: string
  winnerVersionId?: string
  startDate?: string
  endDate?: string
  results?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationImportJob {
  id: string
  tenantId: string
  flowId?: string
  jobName: string
  sourceType: string
  targetType: string
  fileName?: string
  fileSize?: number
  recordCount: number
  successCount: number
  errorCount: number
  status: string
  errorSummary?: string
  processingTimeMs?: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationExportJob {
  id: string
  tenantId: string
  flowId?: string
  jobName: string
  exportType: string
  format: string
  status: string
  recordCount: number
  fileSize?: number
  fileUrl?: string
  errorSummary?: string
  processingTimeMs?: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationDLQ {
  id: string
  messageId: string
  flowId?: string
  flowName?: string
  errorCategory: string
  errorMessage: string
  errorDetail?: string
  retryCount: number
  lastRetryAt?: string
  status: string
  payload?: string
  createdAt: string
}

export interface IntegrationEndpoint {
  id: string
  name: string
  type: string
  status: string
  lastTestedAt?: string
  errorMessage?: string
}

export interface IntegrationFlow {
  id: string
  name: string
  description?: string
  sourceType: string
  targetType: string
  status: string
  schedule?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationFlowStep {
  id: string
  flowId: string
  stepOrder: number
  stepType: string
  config?: Record<string, unknown>
  createdAt: string
}

export interface IntegrationTransformMapping {
  id: string
  name: string
  sourceFormat: string
  targetFormat: string
  mappingRules?: string
  createdAt: string
  updatedAt: string
}

export interface IntegrationValidationRule {
  id: string
  name: string
  entityType: string
  ruleType: string
  ruleExpression: string
  errorMessage: string
  severity: string
  active: boolean
  createdAt: string
}

export interface IntegrationCDCEvent {
  id: string
  entityType: string
  entityId: string
  eventType: string
  payload?: string
  processed: boolean
  createdAt: string
}

export interface IntegrationAuditLog {
  id: string
  action: string
  entityType: string
  entityId: string
  details?: string
  performedBy?: string
  createdAt: string
}
