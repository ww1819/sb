import { defineStore } from 'pinia'
import { MOBILE_BREAKPOINT } from '@/composables/useBreakpoint'

export type NavMode = 'top' | 'side'
export type ThemeMode = 'light' | 'dark' | 'system'

const NAV_MODE_KEY = 'meis-nav-mode'
const SIDEBAR_KEY = 'meis-sidebar-collapsed'
const THEME_KEY = 'meis-theme-mode'

function readNavMode(): NavMode {
  const raw = localStorage.getItem(NAV_MODE_KEY)
  return raw === 'side' ? 'side' : 'top'
}

function readThemeMode(): ThemeMode {
  const raw = localStorage.getItem(THEME_KEY)
  if (raw === 'dark' || raw === 'system') return raw
  return 'light'
}

function resolveDark(mode: ThemeMode) {
  if (mode === 'dark') return true
  if (mode === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return false
}

export function applyThemeMode(mode: ThemeMode) {
  const dark = resolveDark(mode)
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.dataset.theme = dark ? 'dark' : 'light'
}

let mediaListener: ((e: MediaQueryListEvent) => void) | null = null

export function initThemeWatcher(onChange?: () => void) {
  if (mediaListener) return
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  mediaListener = () => {
    const mode = readThemeMode()
    if (mode === 'system') {
      applyThemeMode('system')
      onChange?.()
    }
  }
  mq.addEventListener('change', mediaListener)
}

let breakpointListener: (() => void) | null = null

export const useLayoutStore = defineStore('layout', {
  state: () => ({
    navMode: readNavMode() as NavMode,
    sidebarCollapsed: localStorage.getItem(SIDEBAR_KEY) === '1',
    themeMode: readThemeMode() as ThemeMode,
    themeRevision: 0,
    isMobile: typeof window !== 'undefined' ? window.innerWidth < MOBILE_BREAKPOINT : false,
    mobileNavOpen: false,
    contentFullscreen: false
  }),
  getters: {
    isTopNav: (s) => s.navMode === 'top',
    isSideNav: (s) => s.navMode === 'side',
    isDark: (s) => resolveDark(s.themeMode)
  },
  actions: {
    initTheme() {
      applyThemeMode(this.themeMode)
      initThemeWatcher(() => {
        this.themeRevision += 1
      })
      this.initBreakpoint()
    },
    initBreakpoint() {
      if (breakpointListener) return
      const update = () => {
        this.isMobile = window.innerWidth < MOBILE_BREAKPOINT
        if (!this.isMobile) {
          this.mobileNavOpen = false
        }
      }
      breakpointListener = update
      update()
      window.addEventListener('resize', update)
    },
    openMobileNav() {
      this.mobileNavOpen = true
    },
    closeMobileNav() {
      this.mobileNavOpen = false
    },
    toggleContentFullscreen() {
      this.contentFullscreen = !this.contentFullscreen
    },
    setContentFullscreen(fullscreen: boolean) {
      this.contentFullscreen = fullscreen
    },
    setNavMode(mode: NavMode) {
      this.navMode = mode
      localStorage.setItem(NAV_MODE_KEY, mode)
    },
    setThemeMode(mode: ThemeMode) {
      this.themeMode = mode
      localStorage.setItem(THEME_KEY, mode)
      applyThemeMode(mode)
      this.themeRevision += 1
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(SIDEBAR_KEY, this.sidebarCollapsed ? '1' : '0')
    },
    setSidebarCollapsed(collapsed: boolean) {
      this.sidebarCollapsed = collapsed
      localStorage.setItem(SIDEBAR_KEY, collapsed ? '1' : '0')
    }
  }
})
