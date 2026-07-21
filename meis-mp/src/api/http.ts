import { API_BASE, STORAGE_TOKEN, STORAGE_USER } from '@/config/env'

export interface ApiResult<T = unknown> {
  success?: boolean
  code?: number
  message?: string
  data?: T
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown> | unknown
  params?: Record<string, string | number | boolean | undefined | null>
  auth?: boolean
}

function buildQuery(params?: RequestOptions['params']) {
  if (!params) return ''
  const parts: string[] = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  }
  return parts.length ? `?${parts.join('&')}` : ''
}

function readUser(): Record<string, string> | null {
  try {
    const raw = uni.getStorageSync(STORAGE_USER)
    if (!raw) return null
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return null
  }
}

export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, params, auth = true } = options
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  if (auth) {
    const token = uni.getStorageSync(STORAGE_TOKEN)
    if (token) headers.Authorization = `Bearer ${token}`
    const user = readUser()
    if (user) {
      if (user.tenantId) headers['X-Tenant-Id'] = user.tenantId
      if (user.schemaName) headers['X-Tenant-Schema'] = user.schemaName
      if (user.userId) headers['X-User-Id'] = user.userId
      if (user.username) headers['X-Username'] = user.username
      if (user.permissions) {
        try {
          headers['X-Permissions'] = JSON.stringify(user.permissions)
        } catch {
          /* ignore */
        }
      }
    }
  }
  const fullUrl = `${API_BASE}${url}${buildQuery(params)}`
  return new Promise((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data: data as UniApp.RequestOptions['data'],
      header: headers,
      timeout: 30000,
      success: (res) => {
        const status = res.statusCode || 0
        const body = res.data as ApiResult<T>
        if (status === 401) {
          uni.removeStorageSync(STORAGE_TOKEN)
          uni.removeStorageSync(STORAGE_USER)
          uni.showToast({ title: '登录已过期', icon: 'none' })
          setTimeout(() => {
            uni.reLaunch({ url: '/pages/login/index' })
          }, 400)
          reject(new Error(body?.message || '未登录'))
          return
        }
        if (status >= 400) {
          reject(new Error(body?.message || `请求失败(${status})`))
          return
        }
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code !== 0 && body.success === false) {
            reject(new Error(body.message || '业务错误'))
            return
          }
          resolve(body.data as T)
          return
        }
        resolve(body as T)
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络异常'))
      }
    })
  })
}

export const http = {
  get: <T = unknown>(url: string, params?: RequestOptions['params'], auth = true) =>
    request<T>({ url, method: 'GET', params, auth }),
  post: <T = unknown>(url: string, data?: RequestOptions['data'], auth = true) =>
    request<T>({ url, method: 'POST', data, auth }),
  put: <T = unknown>(url: string, data?: RequestOptions['data'], auth = true) =>
    request<T>({ url, method: 'PUT', data, auth }),
  patch: <T = unknown>(url: string, data?: RequestOptions['data'], auth = true) =>
    request<T>({ url, method: 'PATCH', data, auth })
}
