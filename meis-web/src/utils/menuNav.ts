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
  return ensureExtraMenus(ensureSystemConfigMenu(modules.map((mod) => {
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

const SYSTEM_CONFIG_MENU: NavMenuItem = {
  id: 'system-config',
  title: '系统配置',
  path: '/system/config'
}

/** 兼容未迁移时补齐系统配置入口（不再把供应商等主数据迁到系统管理） */
export function ensureSystemConfigMenu(modules: NavModule[]): NavModule[] {
  const result = modules.map((m) => ({
    ...m,
    groups: (m.groups ?? []).map((g) => ({ ...g, items: [...g.items] }))
  }))
  const systemIdx = result.findIndex((m) => m.id === 'system')
  if (systemIdx < 0) return result

  const system = result[systemIdx]
  const groups = system.groups?.length ? [...system.groups] : [{ title: '', items: [] as NavMenuItem[] }]
  const items = [...groups[0].items]
  const hasConfig = items.some((i) => i.path === SYSTEM_CONFIG_MENU.path || i.title === '系统配置')
  if (!hasConfig) {
    items.push({ ...SYSTEM_CONFIG_MENU })
    groups[0] = { ...groups[0], items }
    result[systemIdx] = { ...system, groups }
  }
  return result
}

/** 补齐库存查询菜单（兼容数据库未迁移；挂在库房管理 → 设备入库之后） */
export function ensureExtraMenus(modules: NavModule[]): NavModule[] {
  const result = modules.map((m) => ({
    ...m,
    groups: (m.groups ?? []).map((g) => ({ ...g, items: [...g.items] }))
  }))

  // 若仍挂在资产台账下，移到库房管理
  const assetIdx = result.findIndex((m) => m.id === 'asset' || m.title === '资产台账')
  if (assetIdx >= 0) {
    const asset = result[assetIdx]
    const groups = asset.groups?.length ? [...asset.groups] : [{ title: '', items: [] as NavMenuItem[] }]
    const items = [...groups[0].items]
    const stockIdx = items.findIndex((i) => i.path === '/asset/stock' || i.title === '库存查询')
    if (stockIdx >= 0) {
      items.splice(stockIdx, 1)
      groups[0] = { ...groups[0], items }
      result[assetIdx] = { ...asset, groups }
    }
  }

  const whIdx = result.findIndex((m) => m.id === 'warehouse' || m.title === '库房管理')
  if (whIdx >= 0) {
    const wh = result[whIdx]
    const groups = wh.groups?.length ? [...wh.groups] : [{ title: '', items: [] as NavMenuItem[] }]
    const items = [...groups[0].items]
    const hasStock = items.some((i) => i.path === '/asset/stock' || i.title === '库存查询')
    if (!hasStock) {
      const entryIdx = items.findIndex(
        (i) =>
          i.path === '/warehouse/entry' ||
          i.title.includes('设备入库') ||
          i.title.includes('备货入库')
      )
      const stockItem: NavMenuItem = { id: 'asset-stock-query', title: '库存查询', path: '/asset/stock' }
      if (entryIdx >= 0) items.splice(entryIdx + 1, 0, stockItem)
      else items.push(stockItem)
      groups[0] = { ...groups[0], items }
      result[whIdx] = { ...wh, groups }
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
