import { defineStore } from 'pinia'
import { MOBILE_BREAKPOINT } from '@/composables/useBreakpoint'
import {
  applyBrandColor,
  DEFAULT_BRAND_HEX,
  normalizeHex,
  resolveBrandHex,
  type BrandPresetId
} from '@/styles/brand'

export type NavMode = 'top' | 'side'
export type ThemeMode = 'light' | 'dark' | 'system'
export type { BrandPresetId }

const NAV_MODE_KEY = 'meis-nav-mode'
const SIDEBAR_KEY = 'meis-sidebar-collapsed'
const THEME_KEY = 'meis-theme-mode'
const BRAND_PRESET_KEY = 'meis-brand-preset'
const BRAND_CUSTOM_KEY = 'meis-brand-custom'

const PRESET_IDS: BrandPresetId[] = ['default', 'ease', 'softBlue', 'blush', 'sand', 'violet', 'custom']

function readNavMode(): NavMode {
  const raw = localStorage.getItem(NAV_MODE_KEY)
  return raw === 'side' ? 'side' : 'top'
}

function readThemeMode(): ThemeMode {
  const raw = localStorage.getItem(THEME_KEY)
  if (raw === 'dark' || raw === 'system') return raw
  return 'light'
}

function readBrandPreset(): BrandPresetId {
  const raw = localStorage.getItem(BRAND_PRESET_KEY) as BrandPresetId | null
  return raw && PRESET_IDS.includes(raw) ? raw : 'default'
}

function readBrandCustomHex(): string {
  return normalizeHex(localStorage.getItem(BRAND_CUSTOM_KEY)) ?? DEFAULT_BRAND_HEX
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
    brandPreset: readBrandPreset() as BrandPresetId,
    brandCustomHex: readBrandCustomHex(),
    themeRevision: 0,
    isMobile: typeof window !== 'undefined' ? window.innerWidth < MOBILE_BREAKPOINT : false,
    mobileNavOpen: false,
    contentFullscreen: false
  }),
  getters: {
    isTopNav: (s) => s.navMode === 'top',
    isSideNav: (s) => s.navMode === 'side',
    isDark: (s) => resolveDark(s.themeMode),
    brandHex: (s) => resolveBrandHex(s.brandPreset, s.brandCustomHex)
  },
  actions: {
    applyCurrentBrand() {
      applyBrandColor(this.brandHex, resolveDark(this.themeMode))
    },
    initTheme() {
      applyThemeMode(this.themeMode)
      this.applyCurrentBrand()
      initThemeWatcher(() => {
        this.applyCurrentBrand()
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
      this.applyCurrentBrand()
      this.themeRevision += 1
    },
    setBrandPreset(preset: BrandPresetId) {
      this.brandPreset = preset
      localStorage.setItem(BRAND_PRESET_KEY, preset)
      this.applyCurrentBrand()
      this.themeRevision += 1
    },
    setBrandCustom(hex: string) {
      const normalized = normalizeHex(hex) ?? DEFAULT_BRAND_HEX
      this.brandCustomHex = normalized
      this.brandPreset = 'custom'
      localStorage.setItem(BRAND_CUSTOM_KEY, normalized)
      localStorage.setItem(BRAND_PRESET_KEY, 'custom')
      this.applyCurrentBrand()
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
