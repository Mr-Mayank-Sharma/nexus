import axios from 'axios'

const API_BASE_URL = '/api/v1'

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

client.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('nexus_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/** Map backend field names to frontend-expected names per entity type */
const FIELD_MAPS: Record<string, Record<string, string>> = {
  products: { name: 'productName', price: 'unitPrice', cost: 'costPrice', active: 'isActive' },
  product: { name: 'productName', price: 'unitPrice', cost: 'costPrice', active: 'isActive' },
  inventory: { name: 'productName', cost: 'unitCost', qty: 'quantityOnHand', price: 'unitPrice', active: 'isActive' },
  orders: { shipping: 'shippingCost', shippingMethod: 'fulfillmentType', shipBy: 'estimatedShipDate', tax: 'taxAmount' },
  order: { shipping: 'shippingCost', shippingMethod: 'fulfillmentType', shipBy: 'estimatedShipDate', tax: 'taxAmount' },
  returns: { id: 'rmaNumber', orderId: 'orderNumber', qty: 'quantity', createdAt: 'date', refund: 'refundAmount' },
  return: { id: 'rmaNumber', orderId: 'orderNumber', qty: 'quantity', createdAt: 'date', refund: 'refundAmount' },
  payments: { net: 'netAmount', createdAt: 'date' },
  invoices: { number: 'invoiceNumber', customerName: 'customer', dueAt: 'dueDate', paidAt: 'paidDate', issuedAt: 'date' },
  warehouses: { used: 'utilizedCapacity' },
  lists: { pickerId: 'assigneeId', waveId: 'name' },
}

/** Nested array maps — for items arrays inside entities */
const NESTED_FIELD_MAPS: Record<string, Record<string, Record<string, string>>> = {
  orders: { items: { name: 'productName', qty: 'quantity', price: 'unitPrice' } },
  order: { items: { name: 'productName', qty: 'quantity', price: 'unitPrice' } },
  lists: { items: { name: 'productName', qty: 'quantity', location: 'fromLocation' } },
}

function pluralToSingular(key: string): string {
  if (key.endsWith('ies')) return key.slice(0, -3) + 'y'
  if (key.endsWith('ses')) return key.slice(0, -2)
  if (key.endsWith('s')) return key.slice(0, -1)
  return key
}

function applyFieldMapping(body: Record<string, any>): Record<string, any> {
  if (!body || typeof body !== 'object') return body

  const { success, message, error, errors, page, limit, total, totalPages, totalElements, number, size, pagination: pg, content, ...rest } = body

  const pagination = (total != null || totalElements != null)
    ? {
        page: page ?? number ?? 0,
        limit: limit ?? size ?? 0,
        total: total ?? totalElements ?? 0,
        totalPages: totalPages ?? (Math.ceil((total ?? totalElements ?? 0) / (limit ?? size ?? 1)) || 1),
      }
    : undefined

  let data: any
  let entityType: string | null = null

  if (content !== undefined) {
    data = content
  } else if (pg !== undefined) {
    data = pg
  } else {
    const arrKey = Object.keys(rest).find(k => Array.isArray(rest[k]))
    if (arrKey) {
      entityType = arrKey
      data = rest[arrKey]
    } else {
      const objKeys = Object.keys(rest).filter(k => rest[k] !== null && typeof rest[k] === 'object')
      if (objKeys.length === 1) {
        entityType = objKeys[0]
        data = rest[objKeys[0]]
      } else if (Object.keys(rest).length > 0) {
        data = rest
      } else {
        data = undefined
      }
    }
  }

  // Apply field mapping
  if (data != null && entityType) {
    const map = FIELD_MAPS[entityType] || FIELD_MAPS[pluralToSingular(entityType)]
    const nestedMap = NESTED_FIELD_MAPS[entityType] || NESTED_FIELD_MAPS[pluralToSingular(entityType)]

    const mapItem = (item: any): any => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return item
      const result: Record<string, any> = { ...item }

      if (map) {
        for (const [bk, fk] of Object.entries(map)) {
          if (bk in result && bk !== fk) {
            result[fk] = result[bk]
          }
        }
      }

      if (nestedMap) {
        for (const [nestedField, itemMap] of Object.entries(nestedMap)) {
          if (Array.isArray(result[nestedField])) {
            result[nestedField] = result[nestedField].map((ni: any) => {
              if (!ni || typeof ni !== 'object') return ni
              const nm: Record<string, any> = { ...ni }
              for (const [bk, fk] of Object.entries(itemMap)) {
                if (bk in nm && bk !== fk) {
                  nm[fk] = nm[bk]
                }
              }
              return nm
            })
          }
        }
      }

      return result
    }

    if (Array.isArray(data)) {
      data = data.map(mapItem)
    } else {
      data = mapItem(data)
    }
  }

  const result: Record<string, any> = { success, data, message, error, errors, pagination }
  if (entityType && entityType !== 'data' && !(entityType in result)) {
    result[entityType] = data
  }
  return result
}

client.interceptors.response.use(
  (response) => {
    if (response.data && typeof response.data === 'object') {
      response.data = applyFieldMapping(response.data)
    }
    return response
  },
  (error) => {
    if (error.response?.data && typeof error.response.data === 'object') {
      error.response.data = applyFieldMapping(error.response.data)
    }
    if (error.response?.status === 401) {
      localStorage.removeItem('nexus_token')
      localStorage.removeItem('nexus_user')
      window.location.href = '/api/v1/'
    }
    return Promise.reject(error)
  },
)

export default client
