export interface ConnectorConfig {
  active: boolean
  sandbox: boolean
  lastSync: string | null
  ordersSynced: number
  error: string | null
  hasCredentials: boolean
}

export interface ConnectorStatus {
  success: boolean
  connectors: Record<string, ConnectorConfig>
}

export interface AmazonConfig {
  clientId?: string
  clientSecret?: string
  awsAccessKey?: string
  awsSecretKey?: string
  roleArn?: string
  marketplaceId?: string
  refreshToken?: string
  sandbox?: boolean
}

export interface EbayConfig {
  clientId?: string
  clientSecret?: string
  refreshToken?: string
  siteId?: number
  sandbox?: boolean
}

export interface WalmartConfig {
  clientId?: string
  clientSecret?: string
  refreshToken?: string
  channelType?: string
  sandbox?: boolean
}

export interface AmazonOrder {
  orderId: string
  status: string
  total: number
  currency: string
  items: Array<{ sku: string; qty: number }>
  purchaseDate: string
  buyerEmail: string
}

export interface EbayOrder {
  orderId: string
  status: string
  total: number
  currency: string
  items: Array<{ sku: string; qty: number }>
  creationDate: string
  buyerUsername: string
}

export interface WalmartOrder {
  orderId: string
  status: string
  total: number
  currency: string
  items: Array<{ sku: string; qty: number }>
  orderDate: string
  customerName: string
}

export interface AmazonListing {
  sku: string
  title: string
  price: number
  qty: number
  status: string
}

export interface EbayListing {
  sku: string
  title: string
  price: number
  qty: number
  status: string
}

export interface WalmartListing {
  sku: string
  title: string
  price: number
  qty: number
  status: string
}

export interface AmazonInventory {
  listings: Array<{ sku: string; fulfillable: number; inbound: number; reserved: number }>
}

export interface EbayInventory {
  inventory: Array<{ sku: string; available: number; price: number }>
}

export interface WalmartInventory {
  items: Array<{ sku: string; qty: number; fulfillmentType: string }>
}

export type Marketplace = 'amazon' | 'ebay' | 'walmart' | 'bigcommerce'

export interface ConnectorResponse<T> {
  success: boolean
  mock: boolean
  error?: string
  note?: string
  orders?: T[]
  order?: T
  listings?: T[]
  inventory?: T
  items?: T[]
  message?: string
}
