<template>
  <el-select
    v-model="model"
    filterable
    clearable
    :multiple="multiple"
    :collapse-tags="multiple"
    :collapse-tags-tooltip="multiple"
    :disabled="disabled"
    :placeholder="placeholder"
    :loading="loading"
    style="width: 100%"
    @visible-change="onOpen"
  >
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import { refSelectConfig, type RefSelectMeta } from '@/config/refSelectConfig'

function refRowLabel(row: Record<string, unknown>, meta: RefSelectMeta): string {
  const vk = meta.valueKey ?? 'id'
  const name = row[meta.labelKey]
  const code = meta.codeKey ? row[meta.codeKey] : null
  if (code != null && code !== '' && name != null && name !== '') return `${code} ${name}`
  if (name != null && name !== '') return String(name)
  if (code != null && code !== '') return String(code)
  return String(row[vk] ?? '')
}

const props = withDefaults(
  defineProps<{
    modelValue: unknown
    linkTable: string
    placeholder?: string
    disabled?: boolean
    multiple?: boolean
    /** 不出现在选项中的值（如编辑时排除自身及子孙，避免成环） */
    excludeValues?: string[]
  }>(),
  { multiple: false, excludeValues: () => [] }
)
const emit = defineEmits<{ 'update:modelValue': [v: unknown] }>()

const loading = ref(false)
const allOptions = ref<{ label: string; value: string }[]>([])
const loaded = ref(false)

const options = computed(() => {
  const ban = new Set((props.excludeValues ?? []).map(String).filter(Boolean))
  if (!ban.size) return allOptions.value
  return allOptions.value.filter((o) => !ban.has(o.value))
})

const model = computed({
  get: () => props.modelValue,
  // 清空时统一为 null，便于后端 UUID 列落库为一级（无上级）
  set: (v) => emit('update:modelValue', v === undefined || v === '' ? null : v)
})

async function load() {
  const meta = refSelectConfig[props.linkTable]
  if (!meta) return
  loading.value = true
  try {
    const { data } = await http.get(meta.url, { params: { limit: 500 } })
    const rows = data.data?.records ?? data.data ?? []
    const vk = meta.valueKey ?? 'id'
    allOptions.value = rows.map((r: Record<string, unknown>) => ({
      label: refRowLabel(r, meta),
      value: String(r[vk] ?? '')
    }))
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function onOpen(visible: boolean) {
  if (visible && !loaded.value) void load()
}

onMounted(() => {
  void load()
})

watch(
  () => props.modelValue,
  (v) => {
    if (v != null && v !== '' && !(Array.isArray(v) && v.length === 0) && !loaded.value) void load()
  }
)
</script>
