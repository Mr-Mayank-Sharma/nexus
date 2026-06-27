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

export interface User {
  id: string
  username: string
  email: string
  fullName: string
  role: UserRole
  avatar?: string
  permissions: string[]
}

export type UserRole = 'ADMIN' | 'MANAGER' | 'OPERATOR' | 'VIEWER'

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

export interface WarehouseStaff {
  id: string
  tenantId: string
  warehouseId: string
  name: string
  email: string
  role: string
  shift: string
  isActive: boolean
  itemsPickedToday: number
  itemsPackedToday: number
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
