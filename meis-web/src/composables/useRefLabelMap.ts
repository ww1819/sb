import { ref } from 'vue'
import http from '@/api/http'
import { refSelectConfig, type RefSelectMeta } from '@/config/refSelectConfig'
import { formatRefRowLabel } from '@/utils/refLabel'

const labelCache = new Map<string, Map<string, string>>()
/** hideCode=true 时的名称-only 缓存（采购计划等例外） */
const labelCacheNameOnly = new Map<string, Map<string, string>>()
const loading = new Map<string, Promise<Map<string, string>>>()
export const labelCacheVersion = ref(0)

async function loadMap(linkTable: string, hideCode: boolean): Promise<Map<string, string>> {
  const cache = hideCode ? labelCacheNameOnly : labelCache
  if (cache.has(linkTable)) return cache.get(linkTable)!
  const loadKey = `${linkTable}:${hideCode ? 'n' : 'c'}`
  if (loading.has(loadKey)) return loading.get(loadKey)!

  const promise = (async () => {
    const meta = refSelectConfig[linkTable]
    const map = new Map<string, string>()
    if (!meta) {
      cache.set(linkTable, map)
      return map
    }
    try {
      const { data } = await http.get(meta.url, { params: { limit: 500 } })
      const rows = data.data?.records ?? data.data ?? []
      const valueKey = meta.valueKey ?? 'id'
      for (const row of rows as Record<string, unknown>[]) {
        const id = row[valueKey] ?? row.id
        if (id == null || id === '') continue
        map.set(String(id), formatRefRowLabel(row, meta, hideCode))
      }
    } catch {
      // keep empty map
    }
    cache.set(linkTable, map)
    labelCacheVersion.value++
    loading.delete(loadKey)
    return map
  })()

  loading.set(loadKey, promise)
  return promise
}

export async function ensureRefLabelMap(linkTable: string, hideCode = false): Promise<Map<string, string>> {
  return loadMap(linkTable, hideCode)
}

export async function preloadRefLabelMaps(linkTables: string[]) {
  const unique = [...new Set(linkTables.filter(Boolean))]
  await Promise.all(unique.map((t) => ensureRefLabelMap(t, false)))
}

export function resolveRefLabel(
  linkTable: string | undefined,
  value: unknown,
  opts?: { hideCode?: boolean }
): string {
  if (!linkTable || value === null || value === undefined || value === '') return ''
  const hideCode = !!opts?.hideCode
  const cache = hideCode ? labelCacheNameOnly : labelCache
  const map = cache.get(linkTable)
  if (!map) return String(value)
  return map.get(String(value)) ?? String(value)
}

export function useRefLabelMaps() {
  return { ensureRefLabelMap, preloadRefLabelMaps, resolveRefLabel, labelCache }
}

export type { RefSelectMeta }
