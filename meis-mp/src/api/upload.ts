import { API_BASE, STORAGE_TOKEN, STORAGE_USER } from '@/config/env'

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  const token = uni.getStorageSync(STORAGE_TOKEN)
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    const raw = uni.getStorageSync(STORAGE_USER)
    const user = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (user) {
      if (user.tenantId) headers['X-Tenant-Id'] = user.tenantId
      if (user.schemaName) headers['X-Tenant-Schema'] = user.schemaName
      if (user.userId) headers['X-User-Id'] = user.userId
      if (user.username) headers['X-Username'] = user.username
      if (user.permissions) {
        const p = typeof user.permissions === 'string'
          ? JSON.parse(user.permissions)
          : user.permissions
        headers['X-Permissions'] = JSON.stringify({
          buttons: p.buttons ?? [],
          dataScope: p.dataScope ?? 'self',
          deptIds: p.deptIds ?? [],
          warehouseIds: p.warehouseIds ?? []
        })
      }
    }
  } catch {
    /* ignore */
  }
  return headers
}

/** 上传本地临时文件，返回 URL */
export function uploadFile(filePath: string, filename = 'photo.jpg'): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE}/file/upload`,
      filePath,
      name: 'file',
      header: authHeaders(),
      formData: { filename },
      success: (res) => {
        try {
          const body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (res.statusCode && res.statusCode >= 400) {
            reject(new Error(body?.message || `上传失败(${res.statusCode})`))
            return
          }
          const url = body?.data?.url || body?.data || body?.url
          if (!url || typeof url !== 'string') {
            reject(new Error('上传响应无 URL'))
            return
          }
          resolve(url)
        } catch (e) {
          reject(e instanceof Error ? e : new Error('上传解析失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '上传失败'))
    })
  })
}

export function chooseAndUploadImage(count = 1): Promise<string[]> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: async (res) => {
        try {
          const paths = res.tempFilePaths || []
          const urls: string[] = []
          for (const p of paths) {
            urls.push(await uploadFile(p))
          }
          resolve(urls)
        } catch (e) {
          reject(e)
        }
      },
      fail: () => reject(new Error('已取消选图'))
    })
  })
}
