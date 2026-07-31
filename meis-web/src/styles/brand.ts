/** Global brand color presets + Element Plus / report CSS token application. */

export type BrandPresetId = 'default' | 'ease' | 'softBlue' | 'blush' | 'sand' | 'violet' | 'custom'

export interface BrandPreset {
  id: BrandPresetId
  labelZh: string
  hex: string
}

export const BRAND_PRESETS: BrandPreset[] = [
  { id: 'default', labelZh: '默认蓝', hex: '#1677ff' },
  { id: 'ease', labelZh: '防疲劳', hex: '#5B8C7A' },
  { id: 'softBlue', labelZh: '淡蓝', hex: '#6BA3D6' },
  { id: 'blush', labelZh: '粉色', hex: '#D48AA8' },
  { id: 'sand', labelZh: '暖沙', hex: '#C4A574' },
  { id: 'violet', labelZh: '雾紫', hex: '#7B6BA8' }
]

export const DEFAULT_BRAND_HEX = BRAND_PRESETS[0].hex

const HEX_RE = /^#([0-9a-fA-F]{6})$/

export function normalizeHex(input: string | null | undefined): string | null {
  if (!input) return null
  let s = input.trim()
  if (!s.startsWith('#')) s = `#${s}`
  if (s.length === 4 && /^#[0-9a-fA-F]{3}$/.test(s)) {
    s = `#${s[1]}${s[1]}${s[2]}${s[2]}${s[3]}${s[3]}`
  }
  return HEX_RE.test(s) ? s.toLowerCase() : null
}

function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const n = normalizeHex(hex) ?? DEFAULT_BRAND_HEX
  return {
    r: parseInt(n.slice(1, 3), 16),
    g: parseInt(n.slice(3, 5), 16),
    b: parseInt(n.slice(5, 7), 16)
  }
}

function rgbToHex(r: number, g: number, b: number): string {
  const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)))
  return `#${[clamp(r), clamp(g), clamp(b)].map((v) => v.toString(16).padStart(2, '0')).join('')}`
}

/** Mix `hex` toward white (t=0 keep, t=1 white) or black (negative via dark). */
function mix(hex: string, toward: 'white' | 'black', weight: number): string {
  const { r, g, b } = hexToRgb(hex)
  const t = Math.max(0, Math.min(1, weight))
  if (toward === 'white') {
    return rgbToHex(r + (255 - r) * t, g + (255 - g) * t, b + (255 - b) * t)
  }
  return rgbToHex(r * (1 - t), g * (1 - t), b * (1 - t))
}

export function resolveBrandHex(preset: BrandPresetId, customHex?: string | null): string {
  if (preset === 'custom') {
    return normalizeHex(customHex) ?? DEFAULT_BRAND_HEX
  }
  return BRAND_PRESETS.find((p) => p.id === preset)?.hex ?? DEFAULT_BRAND_HEX
}

export function getComputedBrandTokens(hex: string, dark: boolean) {
  const primary = normalizeHex(hex) ?? DEFAULT_BRAND_HEX
  const { r, g, b } = hexToRgb(primary)
  // Top/side nav chrome: deep tints of brand (replaces fixed #001529 / #003a70)
  const headerStart = mix(primary, 'black', 0.88)
  const headerEnd = mix(primary, 'black', 0.72)
  const sidebarDeep = mix(primary, 'black', 0.82)
  return {
    primary,
    rgb: `${r}, ${g}, ${b}`,
    light3: mix(primary, 'white', 0.3),
    light5: mix(primary, 'white', 0.5),
    light7: mix(primary, 'white', 0.7),
    light8: mix(primary, 'white', 0.8),
    light9: mix(primary, 'white', 0.9),
    dark2: mix(primary, 'black', 0.2),
    headerStart,
    headerEnd,
    sidebarDeep,
    reportHeaderBg: dark ? `rgba(${r}, ${g}, ${b}, 0.22)` : mix(primary, 'white', 0.82),
    reportHover: dark ? `rgba(${r}, ${g}, ${b}, 0.28)` : mix(primary, 'white', 0.88),
    reportBorder: dark ? `rgba(${r}, ${g}, ${b}, 0.35)` : mix(primary, 'white', 0.55),
    reportStripe: dark ? `rgba(${r}, ${g}, ${b}, 0.12)` : mix(primary, 'white', 0.9),
    reportWarnBg: dark ? 'rgba(250, 173, 20, 0.18)' : '#fff7e6',
    reportLine: dark ? '#303030' : '#d0d7de'
  }
}

/** Apply brand CSS variables on :root (Element primary + nav chrome + report surface). */
export function applyBrandColor(hex: string, dark = false) {
  const root = document.documentElement
  const t = getComputedBrandTokens(hex, dark)
  root.style.setProperty('--el-color-primary', t.primary)
  root.style.setProperty('--el-color-primary-light-3', t.light3)
  root.style.setProperty('--el-color-primary-light-5', t.light5)
  root.style.setProperty('--el-color-primary-light-7', t.light7)
  root.style.setProperty('--el-color-primary-light-8', t.light8)
  root.style.setProperty('--el-color-primary-light-9', t.light9)
  root.style.setProperty('--el-color-primary-dark-2', t.dark2)
  root.style.setProperty('--el-color-primary-rgb', t.rgb)
  root.style.setProperty('--meis-header-gradient-start', t.headerStart)
  root.style.setProperty('--meis-header-gradient-end', t.headerEnd)
  root.style.setProperty('--meis-sidebar-deep', t.sidebarDeep)
  root.style.setProperty('--meis-report-header-bg', t.reportHeaderBg)
  root.style.setProperty('--meis-report-hover', t.reportHover)
  root.style.setProperty('--meis-report-border', t.reportBorder)
  root.style.setProperty('--meis-report-stripe', t.reportStripe)
  root.style.setProperty('--meis-report-warn-bg', t.reportWarnBg)
  root.style.setProperty('--meis-report-line', t.reportLine)
  /* Keep system modal chrome in sync with brand / report surface */
  root.style.setProperty('--meis-modal-header-bg', t.reportHeaderBg)
  root.style.setProperty(
    '--meis-modal-footer-bg',
    dark ? mix(t.primary, 'black', 0.55) : mix(t.primary, 'white', 0.92)
  )
  root.style.setProperty('--meis-modal-border', t.reportBorder)
  root.style.setProperty('--meis-status-info', t.primary)
  root.dataset.brand = t.primary
}

/** Snapshot for Excel HTML export (inline styles; no CSS vars). */
export function getBrandExportColors(hex?: string, dark = false) {
  const t = getComputedBrandTokens(hex ?? DEFAULT_BRAND_HEX, dark)
  return {
    headerBg: t.reportHeaderBg,
    hoverBg: t.reportHover,
    border: t.reportBorder,
    stripe: t.reportStripe,
    line: t.reportLine,
    primary: t.primary
  }
}
