import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import router from '@/router'
import { useTabsStore } from '@/stores/tabs'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

let handling401 = false

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

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status as number | undefined
    const message = error.response?.data?.message as string | undefined

    if (status === 401) {
      const onLoginPage = router.currentRoute.value.path === '/login'
      if (!handling401 && !onLoginPage) {
        handling401 = true
        localStorage.removeItem('meis_token')
        localStorage.removeItem('meis_user')
        useTabsStore().reset()
        ElMessageBox.alert(message || '登录已过期，请重新登录', '提示', {
          confirmButtonText: '确定',
          type: 'warning',
          showClose: false,
          closeOnClickModal: false,
          closeOnPressEscape: false
        }).finally(() => {
          handling401 = false
          router.push('/login')
        })
      }
      return Promise.reject(error)
    }

    if (status === 403) {
      ElMessage.warning(message || '没有权限访问')
      return Promise.reject(error)
    }

    if (status && status >= 500) {
      ElMessage.error(message || '服务器错误，请稍后重试')
      return Promise.reject(error)
    }

    if (!error.response) {
      ElMessage.error('网络异常，请检查连接')
      return Promise.reject(error)
    }

    return Promise.reject(error)
  }
)

export default http
