import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

const API_BASE_URL = '/api/v1'

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let isRefreshing = false
let failedQueue: Array<{
  resolve: (value?: unknown) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

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

// ── Automatic snake_case ↔ camelCase conversion ──────────────────────────────

function snakeToCamel(str: string): string {
  return str.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase())
}

function camelToSnake(str: string): string {
  return str.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`)
}

/** Keys that should NOT be converted (pagination/meta fields) */
const SKIP_CONVERT = new Set([
  'success', 'message', 'error', 'errors',
  'page', 'limit', 'total', 'totalPages', 'totalElements', 'number', 'size',
  'content', 'data', 'pagination',
])

function convertKeys<T>(obj: T, converter: (key: string) => string): T {
  if (obj === null || obj === undefined || typeof obj !== 'object') return obj

  if (Array.isArray(obj)) {
    return obj.map((item) => convertKeys(item, converter)) as T
  }

  const result: Record<string, any> = {}
  for (const [key, value] of Object.entries(obj as Record<string, any>)) {
    const newKey = converter(key)
    result[newKey] = convertKeys(value, converter)
  }
  return result as T
}

/** Convert response keys: snake_case → camelCase (handles raw SQL results) */
function responseToCamel(obj: any): any {
  if (obj === null || obj === undefined || typeof obj !== 'object') return obj

  if (Array.isArray(obj)) {
    return obj.map((item) => responseToCamel(item))
  }

  const result: Record<string, any> = {}
  for (const [key, value] of Object.entries(obj)) {
    // Convert the key but preserve known top-level meta keys
    const camelKey = SKIP_CONVERT.has(key) ? key : snakeToCamel(key)
    result[camelKey] = responseToCamel(value)
  }
  return result
}

// ── Pagination normalization ─────────────────────────────────────────────────

function normalizePagination(body: Record<string, any>): Record<string, any> {
  if (!body || typeof body !== 'object') return body

  const {
    success, message, error, errors,
    page, limit, total, totalPages, totalElements, number, size,
    pagination: pg, content, ...rest
  } = body

  const pagination = (total != null || totalElements != null)
    ? {
        page: page ?? number ?? 0,
        limit: limit ?? size ?? 0,
        total: total ?? totalElements ?? 0,
        totalPages: totalPages ?? (Math.ceil((total ?? totalElements ?? 0) / (limit ?? size ?? 1)) || 1),
      }
    : undefined

  let data: any

  if (content !== undefined) {
    data = content
  } else if (pg !== undefined) {
    data = pg
  } else {
    const arrKey = Object.keys(rest).find(k => Array.isArray(rest[k]))
    if (arrKey) {
      data = rest[arrKey]
    } else {
      const objKeys = Object.keys(rest).filter(k => rest[k] !== null && typeof rest[k] === 'object')
      if (objKeys.length === 1) {
        data = rest[objKeys[0]]
      } else if (Object.keys(rest).length > 0) {
        data = rest
      } else {
        data = undefined
      }
    }
  }

  const result: Record<string, any> = { success, data, message, error, errors, pagination }
  return result
}

// ── Response interceptor ─────────────────────────────────────────────────────

client.interceptors.response.use(
  (response) => {
    window.dispatchEvent(new CustomEvent('nexus:backend-reachable'))
    if (response.data && typeof response.data === 'object') {
      response.data = normalizePagination(responseToCamel(response.data))
    }
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean; _retries?: number }
    
    if (error.response?.data && typeof error.response.data === 'object') {
      error.response.data = normalizePagination(responseToCamel(error.response.data))
    }

    // Detect backend connection failures (no response at all)
    const isNetworkError = !error.response && (
      error.code === 'ECONNABORTED' ||
      error.code === 'ERR_NETWORK' ||
      error.message?.includes('Network Error') ||
      error.message?.includes('timeout')
    )
    if (isNetworkError) {
      window.dispatchEvent(new CustomEvent('nexus:backend-unreachable', { detail: { error } }))
      // Auto-retry once for transient network errors — but NOT if already retried or during refresh
      if (!originalRequest._retries && !originalRequest._retry) {
        originalRequest._retries = 1
        await new Promise(r => setTimeout(r, 2000))
        return client(originalRequest)
      }
      // If we already retried and still failing, fall through to reject
    }
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('nexus_refresh_token')
      
      if (!refreshToken) {
        localStorage.removeItem('nexus_token')
        localStorage.removeItem('nexus_user')
        localStorage.removeItem('nexus_refresh_token')
        window.location.href = '/#/login'
        return Promise.reject(error)
      }
      
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${token}`
          }
          return client(originalRequest)
        }).catch(err => Promise.reject(err))
      }
      
      originalRequest._retry = true
      isRefreshing = true
      
      try {
        const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken }, { timeout: 8000 })
        const newToken = data.data?.accessToken || data.accessToken
        if (newToken) {
          localStorage.setItem('nexus_token', newToken)
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
          }
          processQueue(null, newToken)
          return client(originalRequest)
        }
        // No token in response — treat as auth failure
        throw new Error('Refresh response missing access token')
      } catch (refreshError) {
        processQueue(refreshError, null)
        localStorage.removeItem('nexus_token')
        localStorage.removeItem('nexus_user')
        localStorage.removeItem('nexus_refresh_token')
        window.location.href = '/#/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }
    
    return Promise.reject(error)
  },
)

export default client
