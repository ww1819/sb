<template>
  <AppModal v-model="visible" title="选择基站" size="lg" @close="onClose">
    <el-form :inline="true" class="filter-form" @submit.prevent="load">
      <el-form-item label="关键词">
        <el-input v-model="keyword" clearable placeholder="编码/名称/位置" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      highlight-current-row
      max-height="420"
      @row-click="onRowClick"
      @row-dblclick="confirmRow"
    >
      <el-table-column width="48">
        <template #default="{ row }">
          <el-radio :model-value="selectedId" :value="String(row.id)" @change="selectRow(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="station_code" label="基站编码" min-width="120" />
      <el-table-column prop="station_name" label="基站名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="campus_name" label="院区" min-width="120" show-overflow-tooltip />
      <el-table-column prop="location" label="安装位置" min-width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90" />
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" @click="confirmRow(selected!)">确认</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  confirm: [station: Record<string, unknown>]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const selected = ref<Record<string, unknown> | null>(null)
const selectedId = computed(() => (selected.value?.id ? String(selected.value.id) : ''))
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | number | boolean> = { page: 1, size: 200, activeOnly: true }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const { data } = await http.get('/power/station/page', { params })
    if (data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.message || '加载基站列表失败')
      rows.value = []
      return
    }
    rows.value = (data.data?.records ?? []) as Record<string, unknown>[]
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  load()
}

function selectRow(row: Record<string, unknown>) {
  selected.value = row
}

function onRowClick(row: Record<string, unknown>) {
  selectRow(row)
}

function confirmRow(row: Record<string, unknown>) {
  emit('confirm', row)
  visible.value = false
}

function onClose() {
  selected.value = null
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    selected.value = null
    load()
  }
)
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}
</style>
