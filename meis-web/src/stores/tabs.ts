import { defineStore } from 'pinia'
import router from '@/router'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

export const useTabsStore = defineStore('tabs', {
  state: () => ({
    tabs: [{ path: '/dashboard', title: '工作台', closable: false }] as TabItem[],
    activePath: '/dashboard'
  }),
  actions: {
    open(path: string, title: string) {
      const exists = this.tabs.find((t) => t.path === path)
      if (!exists) {
        this.tabs.push({
          path,
          title,
          closable: path !== '/dashboard'
        })
      } else if (title && exists.title !== title) {
        exists.title = title
      }
      this.activePath = path
      if (router.currentRoute.value.path !== path) {
        router.push(path)
      }
    },
    switchTo(path: string) {
      this.activePath = path
      if (router.currentRoute.value.path !== path) {
        router.push(path)
      }
    },
    close(path: string) {
      const tab = this.tabs.find((t) => t.path === path)
      if (!tab?.closable) return
      const idx = this.tabs.findIndex((t) => t.path === path)
      if (idx < 0) return
      this.tabs.splice(idx, 1)
      if (this.activePath === path) {
        const next = this.tabs[idx] ?? this.tabs[idx - 1] ?? this.tabs[0]
        if (next) this.switchTo(next.path)
      }
    },
    closeOthers(keepPath: string) {
      this.tabs = this.tabs.filter((t) => !t.closable || t.path === keepPath)
      if (!this.tabs.find((t) => t.path === this.activePath)) {
        this.switchTo(keepPath)
      }
    },
    closeAll() {
      const home = this.tabs.find((t) => !t.closable) ?? this.tabs[0]
      if (home) {
        this.tabs = [home]
        this.switchTo(home.path)
      }
    },
    reset() {
      this.tabs = [{ path: '/dashboard', title: '工作台', closable: false }]
      this.activePath = '/dashboard'
    }
  }
})
