import { ref } from 'vue'
import http from '@/api/http'
import { refSelectConfig, type RefSelectMeta } from '@/config/refSelectConfig'

const labelCache = new Map<string, Map<string, string>>()
const loading = new Map<string, Promise<Map<string, string>>>()
export const labelCacheVersion = ref(0)

function refRowLabel(row: Record<string, unknown>, meta: RefSelectMeta): string {
  const valueKey = meta.valueKey ?? 'id'
  const id = row[valueKey] ?? row.id
  const name = row[meta.labelKey]
  if (name != null && name !== '') return String(name)
  if (meta.codeKey) {
    const code = row[meta.codeKey]
    if (code != null && code !== '') return String(code)
  }
  return String(id ?? '')
}

export async function ensureRefLabelMap(linkTable: string): Promise<Map<string, string>> {
  if (labelCache.has(linkTable)) return labelCache.get(linkTable)!
  if (loading.has(linkTable)) return loading.get(linkTable)!

  const promise = (async () => {
    const meta = refSelectConfig[linkTable]
    const map = new Map<string, string>()
    if (!meta) {
      labelCache.set(linkTable, map)
      return map
    }
    try {
      const { data } = await http.get(meta.url, { params: { limit: 500 } })
      const rows = data.data?.records ?? data.data ?? []
      const valueKey = meta.valueKey ?? 'id'
      for (const row of rows as Record<string, unknown>[]) {
        const id = row[valueKey] ?? row.id
        if (id == null || id === '') continue
        map.set(String(id), refRowLabel(row, meta))
      }
    } catch {
      // keep empty map
    }
    labelCache.set(linkTable, map)
    labelCacheVersion.value++
    loading.delete(linkTable)
    return map
  })()

  loading.set(linkTable, promise)
  return promise
}

export async function preloadRefLabelMaps(linkTables: string[]) {
  const unique = [...new Set(linkTables.filter(Boolean))]
  await Promise.all(unique.map((t) => ensureRefLabelMap(t)))
}

export function resolveRefLabel(linkTable: string | undefined, value: unknown): string {
  if (!linkTable || value === null || value === undefined || value === '') return ''
  const map = labelCache.get(linkTable)
  if (!map) return String(value)
  return map.get(String(value)) ?? String(value)
}

export function useRefLabelMaps() {
  return { ensureRefLabelMap, preloadRefLabelMaps, resolveRefLabel, labelCache }
}
