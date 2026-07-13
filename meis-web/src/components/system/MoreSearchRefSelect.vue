<template>
  <el-select
    :model-value="modelValue || undefined"
    filterable
    remote
    reserve-keyword
    :placeholder="placeholder"
    :loading="loading"
    clearable
    class="more-search-ref-select"
    :remote-method="remoteSearch"
    @update:model-value="onSelect"
    @clear="onClear"
  >
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import { refSelectConfig, type RefSelectMeta } from '@/config/refSelectConfig'

const props = defineProps<{
  modelValue: string
  linkTable: string
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:label': [value: string]
  search: []
}>()

const loading = ref(false)
const options = ref<{ label: string; value: string }[]>([])

function resolveLookupUrl(meta: RefSelectMeta): string | null {
  if (meta.lookupUrl) return meta.lookupUrl
  if (meta.url.endsWith('/list')) return meta.url.replace(/\/list$/, '/lookup')
  return null
}

function formatLabel(row: Record<string, unknown>, meta: RefSelectMeta): string {
  const name = row[meta.labelKey]
  if (name == null || name === '') return String(row[meta.valueKey ?? 'id'] ?? '')
  if (meta.codeKey) {
    const code = row[meta.codeKey]
    if (code != null && code !== '') return `${name} (${code})`
  }
  return String(name)
}

function mapRows(rows: Record<string, unknown>[], meta: RefSelectMeta) {
  const vk = meta.valueKey ?? 'id'
  return rows.map((row) => ({
    label: formatLabel(row, meta),
    value: String(row[vk] ?? '')
  }))
}

function rowMatchesKeyword(row: Record<string, unknown>, meta: RefSelectMeta, keyword: string) {
  const q = keyword.trim().toLowerCase()
  if (!q) return false
  const keys = [meta.labelKey, meta.codeKey, 'pinyin_code'].filter(Boolean) as string[]
  return keys.some((key) => {
    const value = row[key]
    return value != null && String(value).toLowerCase().includes(q)
  })
}

async function searchByLookup(meta: RefSelectMeta, keyword: string) {
  const lookupUrl = resolveLookupUrl(meta)
  if (!lookupUrl) return null
  const { data } = await http.get(lookupUrl, { params: { keyword, limit: 20 } })
  if (data.code !== 0 || !Array.isArray(data.data)) return null
  return data.data as Record<string, unknown>[]
}

async function searchByList(meta: RefSelectMeta, keyword: string) {
  const { data } = await http.get(meta.url, { params: { limit: 500 } })
  if (data.code !== 0) return []
  const rows = (data.data?.records ?? data.data ?? []) as Record<string, unknown>[]
  return rows.filter((row) => rowMatchesKeyword(row, meta, keyword)).slice(0, 20)
}

async function remoteSearch(keyword: string) {
  const meta = refSelectConfig[props.linkTable]
  if (!meta) return
  const q = keyword.trim()
  if (!q) {
    options.value = []
    return
  }
  loading.value = true
  try {
    let rows = await searchByLookup(meta, q)
    if (!rows?.length) {
      rows = await searchByList(meta, q)
    }
    options.value = mapRows(rows ?? [], meta)
  } catch {
    try {
      const rows = await searchByList(meta, q)
      options.value = mapRows(rows, meta)
    } catch {
      options.value = []
    }
  } finally {
    loading.value = false
  }
}

async function ensureSelectedOption() {
  const meta = refSelectConfig[props.linkTable]
  if (!meta || !props.modelValue) return
  if (options.value.some((o) => o.value === props.modelValue)) return
  const detailUrl = meta.url.endsWith('/list')
    ? meta.url.replace(/\/list$/, `/${props.modelValue}`)
    : null
  if (!detailUrl) return
  try {
    const { data } = await http.get(detailUrl)
    const row = data.data
    if (!row) return
    const label = formatLabel(row, meta)
    options.value = [{ label, value: props.modelValue }]
    emit('update:label', label)
  } catch {
    // ignore
  }
}

function onSelect(value: string) {
  const option = options.value.find((o) => o.value === value)
  emit('update:modelValue', value ?? '')
  emit('update:label', option?.label ?? '')
  if (value) {
    void nextTick(() => emit('search'))
  }
}

function onClear() {
  emit('update:modelValue', '')
  emit('update:label', '')
  void nextTick(() => emit('search'))
}

onMounted(() => {
  void ensureSelectedOption()
})

watch(
  () => props.modelValue,
  () => {
    void ensureSelectedOption()
  }
)
</script>

<style scoped>
.more-search-ref-select {
  width: 220px;
}
</style>
