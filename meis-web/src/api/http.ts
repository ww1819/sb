import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('meis_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const raw = localStorage.getItem('meis_user')
  if (raw) {
    const user = JSON.parse(raw)
    if (user.userType !== 'platform') {
      config.headers['X-Tenant-Id'] = user.tenantId
      config.headers['X-Tenant-Schema'] = user.schemaName
    }
    config.headers['X-User-Id'] = user.userId
    config.headers['X-Username'] = user.username
    if (user.permissions) {
      config.headers['X-Permissions'] = JSON.stringify(user.permissions)
    }
  }
  return config
})

export default http
