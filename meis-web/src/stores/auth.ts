import { defineStore } from 'pinia'
import http from '@/api/http'

export interface UserInfo {
  token: string
  userId: string
  username: string
  realName: string
  tenantId: string
  tenantCode: string
  schemaName: string
  roles: string[]
  userType?: 'platform' | 'tenant'
  permissions?: { menus?: string[]; buttons?: string[]; dataScope?: string }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserInfo | null
  }),
  getters: {
    isLoggedIn: (s) => !!s.user?.token,
    isPlatformAdmin: (s) => s.user?.userType === 'platform'
  },
  actions: {
    async login(tenantCode: string, username: string, password: string) {
      const { data } = await http.post('/auth/login', { tenantCode, username, password })
      if (data.code !== 0 && data.code !== 200) throw new Error(data.message || '登录失败')
      this.user = { ...data.data, userType: data.data.userType || 'tenant' }
      localStorage.setItem('meis_token', data.data.token)
      localStorage.setItem('meis_user', JSON.stringify(this.user))
    },
    async platformLogin(username: string, password: string) {
      const { data } = await http.post('/auth/platform/login', { username, password })
      if (data.code !== 0 && data.code !== 200) throw new Error(data.message || '登录失败')
      this.user = { ...data.data, userType: 'platform' }
      localStorage.setItem('meis_token', data.data.token)
      localStorage.setItem('meis_user', JSON.stringify(this.user))
    },
    restore() {
      const raw = localStorage.getItem('meis_user')
      if (raw) this.user = JSON.parse(raw)
    },
    async logout() {
      try {
        await http.post('/auth/logout')
      } catch {
        /* 网络失败仍本地登出 */
      }
      this.user = null
      localStorage.removeItem('meis_token')
      localStorage.removeItem('meis_user')
    }
  }
})
