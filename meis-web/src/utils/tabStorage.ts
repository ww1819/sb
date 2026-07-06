import type { TabItem } from '@/stores/tabs'
import { createHomeTab, isHomePath } from '@/utils/home'

const STORAGE_PREFIX = 'meis-tabs'
const MAX_TABS = 10

interface PersistedTabs {
  userKey: string
  tabs: TabItem[]
  activePath: string
}

function getUserKey(): string | null {
  const raw = localStorage.getItem('meis_user')
  if (!raw) return null
  try {
    const user = JSON.parse(raw)
    if (user.userType === 'platform') {
      return `platform:${user.userId ?? user.username}`
    }
    return `tenant:${user.tenantCode}:${user.userId ?? user.username}`
  } catch {
    return null
  }
}

function storageKey(userKey: string) {
  return `${STORAGE_PREFIX}:${userKey}`
}

function normalizeTabs(tabs: TabItem[]): TabItem[] {
  const home = createHomeTab()
  const seen = new Set<string>()
  const list: TabItem[] = []

  const homeTab = tabs.find((t) => isHomePath(t.path)) ?? home
  list.push({ ...homeTab, closable: false, title: home.title })
  seen.add(home.path)

  for (const tab of tabs) {
    if (seen.has(tab.path) || isHomePath(tab.path)) continue
    list.push({
      path: tab.path,
      title: tab.title || tab.path,
      closable: true
    })
    seen.add(tab.path)
    if (list.length >= MAX_TABS) break
  }

  return list
}

export function loadPersistedTabs(): { tabs: TabItem[]; activePath: string } | null {
  const userKey = getUserKey()
  if (!userKey) return null

  const raw = localStorage.getItem(storageKey(userKey))
  if (!raw) return null

  try {
    const data = JSON.parse(raw) as PersistedTabs
    if (data.userKey !== userKey || !Array.isArray(data.tabs) || !data.activePath) {
      return null
    }
    const tabs = normalizeTabs(data.tabs)
    const activePath = tabs.some((t) => t.path === data.activePath)
      ? data.activePath
      : tabs[0].path
    return { tabs, activePath }
  } catch {
    return null
  }
}

export function savePersistedTabs(tabs: TabItem[], activePath: string) {
  const userKey = getUserKey()
  if (!userKey) return

  const payload: PersistedTabs = {
    userKey,
    tabs: normalizeTabs(tabs),
    activePath
  }
  localStorage.setItem(storageKey(userKey), JSON.stringify(payload))
}

export function clearPersistedTabs() {
  const userKey = getUserKey()
  if (!userKey) return
  localStorage.removeItem(storageKey(userKey))
}
