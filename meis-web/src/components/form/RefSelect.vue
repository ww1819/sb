<template>
  <el-select
    v-model="model"
    filterable
    clearable
    :placeholder="placeholder"
    :loading="loading"
    style="width: 100%"
    @visible-change="onOpen"
  >
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import http from '@/api/http'
import { refSelectConfig } from '@/config/refSelectConfig'

const props = defineProps<{
  modelValue: unknown
  linkTable: string
  placeholder?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [v: unknown] }>()

const loading = ref(false)
const options = ref<{ label: string; value: string }[]>([])
const loaded = ref(false)

const model = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

async function load() {
  const meta = refSelectConfig[props.linkTable]
  if (!meta || loaded.value) return
  loading.value = true
  try {
    const { data } = await http.get(meta.url, { params: { limit: 500 } })
    const rows = data.data ?? []
    const vk = meta.valueKey ?? 'id'
    options.value = rows.map((r: Record<string, unknown>) => ({
      label: String(r[meta.labelKey] ?? r[vk] ?? ''),
      value: String(r[vk] ?? '')
    }))
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function onOpen(visible: boolean) {
  if (visible) void load()
}
</script>
