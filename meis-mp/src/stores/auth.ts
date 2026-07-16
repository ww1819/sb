import { defineStore } from 'pinia'
import { http } from '@/api/http'
import { STORAGE_TOKEN, STORAGE_USER } from '@/config/env'

export interface UserInfo {
  token: string
  userId: string
  username: string
  realName: string
  tenantId: string
  tenantCode: string
  schemaName: string
  roles?: string[]
  userType?: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserInfo | null
  }),
  getters: {
    isLoggedIn: (s) => !!s.user?.token,
    displayName: (s) => s.user?.realName || s.user?.username || ''
  },
  actions: {
    restore() {
      try {
        const token = uni.getStorageSync(STORAGE_TOKEN)
        const raw = uni.getStorageSync(STORAGE_USER)
        if (!token || !raw) {
          this.user = null
          return
        }
        const user = typeof raw === 'string' ? JSON.parse(raw) : raw
        this.user = { ...user, token }
      } catch {
        this.user = null
      }
    },
    async login(tenantCode: string, username: string, password: string) {
      const data = await http.post<UserInfo>(
        '/auth/login',
        { tenantCode, username, password },
        false
      )
      if (!data?.token) throw new Error('登录响应无效')
      this.user = data
      uni.setStorageSync(STORAGE_TOKEN, data.token)
      uni.setStorageSync(STORAGE_USER, JSON.stringify(data))
    },
    logout() {
      this.user = null
      uni.removeStorageSync(STORAGE_TOKEN)
      uni.removeStorageSync(STORAGE_USER)
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
})
