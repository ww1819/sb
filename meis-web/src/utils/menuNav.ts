import { getPageConfig } from '@/config/pageRegistry'

export interface NavMenuItem {
  id: string
  title: string
  path: string
}

export interface NavMenuGroup {
  title: string
  items: NavMenuItem[]
}

export interface NavModule {
  id: string
  title: string
  path?: string
  groups: NavMenuGroup[]
}

export interface FlatMenuItem {
  path: string
  title: string
  moduleTitle: string
  groupTitle?: string
  keywords: string
}

export interface BreadcrumbItem {
  label: string
}

export function normalizeNavModules(modules: NavModule[]): NavModule[] {
  return ensureExtraMenus(relocateMenusToSystem(modules.map((mod) => {
    const items = (mod.groups ?? []).flatMap((group) => group.items)
    if (items.length !== 1) return mod

    const only = items[0]
    const samePath = !!mod.path && mod.path === only.path
    const dashboardLeaf =
      mod.id === 'dashboard' && only.path === '/dashboard'

    if (samePath || dashboardLeaf) {
      return {
        id: mod.id,
        title: mod.title,
        path: only.path,
        groups: []
      }
    }

    return mod
  })))
}

const SYSTEM_MENU_RELOCATIONS: Array<{ match: (item: NavMenuItem) => boolean; target: NavMenuItem }> = [
  {
    match: (item) => /supplier/.test(item.path) || item.title.includes('供应商'),
    target: { id: 'system-supplier', title: '供应商管理', path: '/system/supplier' }
  },
  {
    match: (item) => /category/.test(item.path) || item.title.includes('设备分类') || item.title.includes('设备68'),
    target: { id: 'system-category', title: '设备分类', path: '/system/category' }
  },
  {
    match: (item) => /manufacturer/.test(item.path) || item.title.includes('生产厂商') || item.title.includes('生产厂家'),
    target: { id: 'system-manufacturer', title: '生产厂商', path: '/system/manufacturer' }
  }
]

const SYSTEM_CONFIG_MENU: NavMenuItem = {
  id: 'system-config',
  title: '系统配置',
  path: '/system/config'
}

/** 将供应商/设备分类/生产厂商从采购管理等模块归并到系统管理（兼容数据库未迁移场景） */
export function relocateMenusToSystem(modules: NavModule[]): NavModule[] {
  const relocated: NavMenuItem[] = []
  const sourceModuleIds = new Set(['purchase', 'dict'])

  const trimmed = modules.map((mod) => {
    if (!sourceModuleIds.has(mod.id)) return mod
    const groups = (mod.groups ?? [])
      .map((group) => {
        const kept: NavMenuItem[] = []
        for (const item of group.items) {
          const rule = SYSTEM_MENU_RELOCATIONS.find((r) => r.match(item))
          if (rule) relocated.push({ ...rule.target })
          else kept.push(item)
        }
        return { ...group, items: kept }
      })
      .filter((group) => group.items.length > 0)
    return { ...mod, groups }
  }).filter((mod) => {
    if (!sourceModuleIds.has(mod.id)) return true
    return (mod.groups ?? []).some((group) => group.items.length > 0)
  })

  const deduped = new Map<string, NavMenuItem>()
  for (const item of relocated) deduped.set(item.path, item)

  let systemIdx = trimmed.findIndex((m) => m.id === 'system')
  if (systemIdx < 0 && deduped.size === 0) return trimmed
  if (systemIdx < 0) {
    trimmed.push({
      id: 'system',
      title: '系统管理',
      groups: [{ title: '', items: [...deduped.values()] }]
    })
    systemIdx = trimmed.length - 1
  }

  const system = trimmed[systemIdx]
  const existingPaths = new Set((system.groups ?? []).flatMap((g) => g.items.map((i) => i.path)))
  const toAdd = [...deduped.values()].filter((item) => !existingPaths.has(item.path))
  if (!existingPaths.has(SYSTEM_CONFIG_MENU.path)) toAdd.push(SYSTEM_CONFIG_MENU)
  if (!toAdd.length) return trimmed

  const groups = [...(system.groups ?? [])]
  if (!groups.length) groups.push({ title: '', items: toAdd })
  else groups[0] = { ...groups[0], items: [...groups[0].items, ...toAdd] }

  trimmed[systemIdx] = { ...system, groups }
  return trimmed
}

/** 补齐库存查询、仓库维护菜单（兼容数据库未迁移） */
export function ensureExtraMenus(modules: NavModule[]): NavModule[] {
  const result = modules.map((m) => ({
    ...m,
    groups: (m.groups ?? []).map((g) => ({ ...g, items: [...g.items] }))
  }))

  const assetIdx = result.findIndex((m) => m.id === 'asset')
  if (assetIdx >= 0) {
    const asset = result[assetIdx]
    const groups = asset.groups?.length ? [...asset.groups] : [{ title: '', items: [] as NavMenuItem[] }]
    const items = [...groups[0].items]
    const hasStock = items.some((i) => i.path === '/asset/stock' || i.title === '库存查询')
    if (!hasStock) {
      const entryIdx = items.findIndex((i) => i.path === '/asset/entry' || i.title.includes('设备入库'))
      const stockItem: NavMenuItem = { id: 'asset-stock-query', title: '库存查询', path: '/asset/stock' }
      if (entryIdx >= 0) items.splice(entryIdx + 1, 0, stockItem)
      else items.push(stockItem)
      groups[0] = { ...groups[0], items }
      result[assetIdx] = { ...asset, groups }
    }
  }

  const systemIdx = result.findIndex((m) => m.id === 'system')
  if (systemIdx >= 0) {
    const system = result[systemIdx]
    const groups = system.groups?.length ? [...system.groups] : [{ title: '', items: [] as NavMenuItem[] }]
    const items = [...groups[0].items]
    const hasWh = items.some((i) => i.path === '/system/warehouse' || i.title === '仓库维护')
    if (!hasWh) {
      const campusIdx = items.findIndex((i) => i.path === '/system/campus' || i.title.includes('院区'))
      const whItem: NavMenuItem = { id: 'system-warehouse', title: '仓库维护', path: '/system/warehouse' }
      if (campusIdx >= 0) items.splice(campusIdx + 1, 0, whItem)
      else items.unshift(whItem)
      groups[0] = { ...groups[0], items }
      result[systemIdx] = { ...system, groups }
    }
  }

  return result
}

export function flattenMenus(modules: NavModule[]): FlatMenuItem[] {
  const list: FlatMenuItem[] = []
  for (const mod of modules) {
    if (mod.path && !mod.groups?.length) {
      list.push({
        path: mod.path,
        title: mod.title,
        moduleTitle: mod.title,
        keywords: mod.title.toLowerCase()
      })
    }
    for (const group of mod.groups ?? []) {
      for (const item of group.items) {
        list.push({
          path: item.path,
          title: item.title,
          moduleTitle: mod.title,
          groupTitle: group.title || undefined,
          keywords: [mod.title, group.title, item.title].filter(Boolean).join(' ').toLowerCase()
        })
      }
    }
  }
  return list
}

export function resolveBreadcrumb(
  modules: NavModule[],
  activePath: string,
  titleMap: Map<string, string>
): BreadcrumbItem[] {
  for (const mod of modules) {
    if (mod.path === activePath) {
      return [{ label: mod.title }]
    }
    for (const group of mod.groups ?? []) {
      for (const item of group.items) {
        if (item.path === activePath) {
          const crumbs: BreadcrumbItem[] = [{ label: mod.title }]
          if (group.title) crumbs.push({ label: group.title })
          crumbs.push({ label: titleMap.get(activePath) ?? item.title })
          return crumbs
        }
      }
    }
  }
  const fallback = titleMap.get(activePath) ?? getPageConfig(activePath)?.title
  if (fallback) return [{ label: fallback }]
  const seg = activePath.split('/').filter(Boolean).pop()
  return [{ label: seg ?? '当前页面' }]
}

export function filterMenuItems(items: FlatMenuItem[], keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return items.slice(0, 12)
  return items.filter((item) => item.keywords.includes(q) || item.path.includes(q)).slice(0, 12)
}
