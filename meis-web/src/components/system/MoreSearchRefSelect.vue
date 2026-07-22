<template>
  <el-select
    :model-value="selectValue"
    filterable
    remote
    reserve-keyword
    :multiple="multiple"
    :collapse-tags="multiple"
    :collapse-tags-tooltip="multiple"
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import { refSelectConfig, type RefSelectMeta } from '@/config/refSelectConfig'

const props = withDefaults(
  defineProps<{
    modelValue: string
    linkTable: string
    placeholder?: string
    multiple?: boolean
  }>(),
  { multiple: false }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:label': [value: string]
  search: []
}>()

const loading = ref(false)
const options = ref<{ label: string; value: string }[]>([])

const selectValue = computed(() => {
  if (props.multiple) {
    if (!props.modelValue?.trim()) return [] as string[]
    return props.modelValue.split(',').map((s) => s.trim()).filter(Boolean)
  }
  return props.modelValue || undefined
})

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
    if (!props.multiple) options.value = []
    return
  }
  loading.value = true
  try {
    let rows = await searchByLookup(meta, q)
    if (!rows?.length) {
      rows = await searchByList(meta, q)
    }
    const mapped = mapRows(rows ?? [], meta)
    if (props.multiple) {
      const keep = options.value.filter((o) => selectValue.value.includes(o.value))
      const seen = new Set(keep.map((o) => o.value))
      options.value = [...keep, ...mapped.filter((o) => !seen.has(o.value))]
    } else {
      options.value = mapped
    }
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
  const ids = props.multiple
    ? props.modelValue.split(',').map((s) => s.trim()).filter(Boolean)
    : [props.modelValue]
  const missing = ids.filter((id) => !options.value.some((o) => o.value === id))
  if (!missing.length) return
  for (const id of missing) {
    const detailUrl = meta.url.endsWith('/list') ? meta.url.replace(/\/list$/, `/${id}`) : null
    if (!detailUrl) continue
    try {
      const { data } = await http.get(detailUrl)
      const row = data.data
      if (!row) continue
      const label = formatLabel(row, meta)
      if (!options.value.some((o) => o.value === id)) {
        options.value = [...options.value, { label, value: id }]
      }
    } catch {
      // ignore
    }
  }
  if (!props.multiple) {
    const opt = options.value.find((o) => o.value === props.modelValue)
    if (opt) emit('update:label', opt.label)
  } else {
    const labels = ids
      .map((id) => options.value.find((o) => o.value === id)?.label)
      .filter(Boolean)
    emit('update:label', labels.join(', '))
  }
}

function onSelect(value: string | string[] | null | undefined) {
  if (props.multiple) {
    const arr = Array.isArray(value) ? value.map(String).filter(Boolean) : []
    const csv = arr.join(',')
    const labels = arr
      .map((id) => options.value.find((o) => o.value === id)?.label ?? id)
      .join(', ')
    emit('update:modelValue', csv)
    emit('update:label', labels)
    if (arr.length) void nextTick(() => emit('search'))
    return
  }
  const v = (value as string) ?? ''
  const option = options.value.find((o) => o.value === v)
  emit('update:modelValue', v)
  emit('update:label', option?.label ?? '')
  if (v) void nextTick(() => emit('search'))
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
.more-search-ref-select.el-select--multiple {
  width: 260px;
}
</style>
