import { ref } from 'vue'
import http from '@/api/http'

const cache = new Map<string, { label: string; value: string }[]>()

export function useDict() {
  const loading = ref(false)

  async function loadDict(type: string) {
    if (cache.has(type)) return cache.get(type)!
    loading.value = true
    try {
      const { data } = await http.get('/system/sys_dict/list', { params: { limit: 500 } })
      const rows = data.data ?? []
      const items = rows
        .filter((r: Record<string, string>) => r.dict_type === type)
        .map((r: Record<string, string>) => ({ label: r.dict_label, value: r.dict_value ?? r.dict_code }))
      cache.set(type, items)
      return items
    } finally {
      loading.value = false
    }
  }

  function getCached(type: string) {
    return cache.get(type) ?? []
  }

  return { loadDict, getCached, loading }
}
