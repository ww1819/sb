<template>
  <AppModal v-model="visible" title="标签绑定记录" size="lg" @close="rows = []">
    <el-table v-loading="loading" :data="rows" row-key="id" max-height="420">
      <el-table-column prop="device_code" label="设备编码" min-width="120" />
      <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bound_at" label="绑定开始" min-width="160">
        <template #default="{ row }">{{ fmt(row.bound_at) }}</template>
      </el-table-column>
      <el-table-column prop="unbound_at" label="绑定结束" min-width="160">
        <template #default="{ row }">{{ fmt(row.unbound_at) || '当前有效' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
    </el-table>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{
  modelValue: boolean
  tagId?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  if (!props.tagId) return
  loading.value = true
  try {
    const { data } = await http.get(`/power/tag/${props.tagId}/bind-log`)
    rows.value = data.data ?? []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void load()
  }
)
</script>
