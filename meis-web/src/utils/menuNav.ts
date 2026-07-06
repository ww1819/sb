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
  const fallback = titleMap.get(activePath)
  if (fallback) return [{ label: fallback }]
  const seg = activePath.split('/').filter(Boolean).pop()
  return [{ label: seg ?? '当前页面' }]
}

export function filterMenuItems(items: FlatMenuItem[], keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return items.slice(0, 12)
  return items.filter((item) => item.keywords.includes(q) || item.path.includes(q)).slice(0, 12)
}
