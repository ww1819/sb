import { defineStore } from 'pinia'
import router from '@/router'
import { createHomeTab, getHomePath, isHomePath } from '@/utils/home'
import { clearPersistedTabs } from '@/utils/tabStorage'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

function createInitialState() {
  const home = createHomeTab()
  return {
    tabs: [home] as TabItem[],
    activePath: home.path
  }
}

export const useTabsStore = defineStore('tabs', {
  state: () => createInitialState(),
  actions: {
    persist() {
      // 页面刷新后统一回到首页，不再恢复上次标签页
    },
    open(path: string, title: string) {
      const exists = this.tabs.find((t) => t.path === path)
      if (!exists) {
        this.tabs.push({
          path,
          title,
          closable: !isHomePath(path)
        })
      } else if (title && exists.title !== title) {
        exists.title = title
      }
      this.activePath = path
      this.persist()
      if (router.currentRoute.value.path !== path) {
        router.push(path)
      }
    },
    switchTo(path: string) {
      this.activePath = path
      this.persist()
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
      } else {
        this.persist()
      }
    },
    closeOthers(keepPath: string) {
      this.tabs = this.tabs.filter((t) => !t.closable || t.path === keepPath)
      if (!this.tabs.find((t) => t.path === this.activePath)) {
        this.switchTo(keepPath)
      } else {
        this.persist()
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
      clearPersistedTabs()
      const home = createHomeTab()
      this.tabs = [home]
      this.activePath = home.path
    },
    ensureHomeTab() {
      const homePath = getHomePath()
      const home = this.tabs.find((t) => t.path === homePath)
      if (!home) {
        this.tabs.unshift(createHomeTab())
      } else {
        home.closable = false
        home.title = createHomeTab().title
      }
      const staleHome = this.tabs.find(
        (t) => !t.closable && t.path !== homePath
      )
      if (staleHome) {
        const idx = this.tabs.indexOf(staleHome)
        this.tabs.splice(idx, 1)
      }
      if (!this.tabs.some((t) => t.path === this.activePath)) {
        this.activePath = homePath
      }
      this.persist()
    }
  }
})
