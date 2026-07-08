import http from '@/api/http'
import { refSelectConfig } from '@/config/refSelectConfig'

const labelCache = new Map<string, Map<string, string>>()
const loading = new Map<string, Promise<Map<string, string>>>()

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
      const { data } = await http.get(meta.url, { params: { limit: 5000 } })
      const rows = data.data?.records ?? data.data ?? []
      const valueKey = meta.valueKey ?? 'id'
      for (const row of rows as Record<string, unknown>[]) {
        const id = row[valueKey] ?? row.id
        if (id == null || id === '') continue
        const label = String(row[meta.labelKey] ?? id)
        map.set(String(id), label)
      }
    } catch {
      // keep empty map
    }
    labelCache.set(linkTable, map)
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
