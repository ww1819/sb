import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export function usePagedList<T = Record<string, unknown>>(loader: (params: URLSearchParams) => Promise<{ code: number; data?: PageResult<T>; message?: string }>) {
  const rows = ref<T[]>([]) as { value: T[] }
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const keyword = ref('')
  const loading = ref(false)

  async function load(extra: Record<string, string> = {}) {
    loading.value = true
    try {
      const params = new URLSearchParams({
        page: String(page.value),
        size: String(size.value),
        ...(keyword.value ? { keyword: keyword.value } : {}),
        ...extra
      })
      const res = await loader(params)
      if (res.code === 0 && res.data) {
        rows.value = res.data.records ?? []
        total.value = res.data.total ?? 0
      } else {
        ElMessage.error(res.message || '加载失败')
      }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      ElMessage.error(err?.response?.data?.message || '加载失败')
    } finally {
      loading.value = false
    }
  }

  function search() {
    page.value = 1
    load()
  }

  function onPageChange() {
    load()
  }

  return { rows, total, page, size, keyword, loading, load, search, onPageChange }
}

export async function fetchPage<T>(url: string, params: URLSearchParams) {
  const { data } = await http.get(`${url}?${params}`)
  return data as { code: number; data?: PageResult<T>; message?: string }
}
