import { ref } from 'vue'
import http from '@/api/http'

const cache = new Map<string, { label: string; value: string }[]>()
/** 缓存写入后递增，供列表单元格响应式刷新中文标签 */
const cacheVersion = ref(0)

export function useDict() {
  const loading = ref(false)

  async function loadDict(type: string) {
    if (!type) return []
    if (cache.has(type)) return cache.get(type)!
    loading.value = true
    try {
      const { data } = await http.get(`/system/dict/type/${type}`)
      const rows = data.data ?? []
      const items = rows.map((r: Record<string, string>) => ({
        label: r.dict_label,
        value: r.dict_value ?? r.dict_code
      }))
      cache.set(type, items)
      cacheVersion.value += 1
      return items
    } finally {
      loading.value = false
    }
  }

  async function preloadDictTypes(types: Iterable<string | undefined | null>) {
    const unique = [...new Set([...types].filter((t): t is string => !!t))]
    await Promise.all(unique.map((t) => loadDict(t)))
  }

  function getCached(type: string) {
    return cache.get(type) ?? []
  }

  /** 从缓存解析中文标签；未命中返回 null（调用方回退原值） */
  function resolveDictLabel(type: string | undefined, value: unknown): string | null {
    void cacheVersion.value
    if (!type || value === null || value === undefined || value === '') return null
    const items = cache.get(type)
    if (!items?.length) return null
    const hit = items.find((i) => String(i.value) === String(value))
    return hit?.label ?? null
  }

  return { loadDict, preloadDictTypes, getCached, resolveDictLabel, cacheVersion, loading }
}
