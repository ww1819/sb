<template>
  <AppModal v-model="visible" title="选择设备" size="xl" @close="onClose">
    <el-form :inline="true" class="filter-form" @submit.prevent="load">
      <el-form-item label="科室">
        <el-input v-model="filters.deptName" clearable placeholder="科室名称" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="filters.deviceName" clearable placeholder="资产名称" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="规格">
        <el-input v-model="filters.specification" clearable placeholder="规格" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="资产编码">
        <el-input v-model="filters.deviceCode" clearable placeholder="资产编码" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="流水号">
        <el-input v-model="filters.financialCode" clearable placeholder="流水号" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="序列号">
        <el-input v-model="filters.serialNumber" clearable placeholder="序列号" @keyup.enter="load" />
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
      <el-table-column prop="dept_name" label="科室" min-width="120" show-overflow-tooltip />
      <el-table-column prop="device_name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" min-width="100" show-overflow-tooltip />
      <el-table-column prop="device_code" label="资产编码" min-width="120" />
      <el-table-column prop="financial_code" label="流水号" min-width="110" />
      <el-table-column prop="serial_number" label="序列号" min-width="110" />
      <el-table-column prop="device_status" label="状态" width="90" />
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" @click="confirmRow(selected!)">确认</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  confirm: [device: Record<string, unknown>]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const selected = ref<Record<string, unknown> | null>(null)
const selectedId = computed(() => (selected.value?.id ? String(selected.value.id) : ''))

const filters = reactive({
  deptName: '',
  deviceName: '',
  specification: '',
  deviceCode: '',
  financialCode: '',
  serialNumber: ''
})

function buildKeyword() {
  const parts = [
    filters.deviceName,
    filters.deviceCode,
    filters.specification,
    filters.financialCode,
    filters.serialNumber,
    filters.deptName
  ].filter((v) => v?.trim())
  return parts.join(' ').trim()
}

async function load() {
  loading.value = true
  try {
    const keyword = buildKeyword()
    const params: Record<string, string | number> = { page: 1, size: 100 }
    if (keyword) params.keyword = keyword
    const { data } = await http.get('/asset/medical_device/query/page', { params })
    if (data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.message || '加载设备列表失败')
      rows.value = []
      return
    }
    rows.value = (data.data?.records ?? []) as Record<string, unknown>[]
  } finally {
    loading.value = false
  }
}

function onReset() {
  filters.deptName = ''
  filters.deviceName = ''
  filters.specification = ''
  filters.deviceCode = ''
  filters.financialCode = ''
  filters.serialNumber = ''
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
