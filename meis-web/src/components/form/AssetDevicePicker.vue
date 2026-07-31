<template>
  <AppModal v-model="visible" title="选择设备" size="xl" @close="onClose">
    <el-form :inline="true" class="filter-form" @submit.prevent="load">
      <el-form-item label="资产编码">
        <el-input v-model="filters.deviceCode" clearable placeholder="资产编码" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="filters.deviceName" clearable placeholder="名称/拼音简码" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="规格">
        <el-input v-model="filters.specification" clearable placeholder="规格" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="型号">
        <el-input v-model="filters.model" clearable placeholder="型号" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="序列号">
        <el-input v-model="filters.serialNumber" clearable placeholder="序列号" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="电流标签">
        <el-input v-model="filters.powerTagCode" clearable placeholder="电流监测编码" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="科室">
        <el-input v-model="filters.deptName" clearable placeholder="科室名称" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="设备状态">
        <el-select v-model="filters.deviceStatus" clearable placeholder="状态" style="width: 120px">
          <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
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
      <el-table-column width="48" fixed="left">
        <template #default="{ row }">
          <el-radio :model-value="selectedId" :value="String(row.id)" @change="selectRow(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="device_code" label="资产编码" width="120" fixed="left" show-overflow-tooltip />
      <el-table-column prop="device_name" label="资产名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="brand" label="品牌" width="90" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" width="100" show-overflow-tooltip />
      <el-table-column prop="model" label="型号" width="100" show-overflow-tooltip />
      <el-table-column prop="registration_no" label="注册证号" width="120" show-overflow-tooltip />
      <el-table-column prop="production_date" label="生产日期" width="110" />
      <el-table-column prop="serial_number" label="序列号" width="120" show-overflow-tooltip />
      <el-table-column prop="has_power_tag" label="电流标签" width="90">
        <template #default="{ row }">{{ row.has_power_tag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="power_tag_code" label="电流监测编码" width="120" show-overflow-tooltip />
      <el-table-column prop="dept_name" label="科室" width="110" show-overflow-tooltip />
      <el-table-column prop="warehouse_name" label="仓库" width="110" show-overflow-tooltip />
      <el-table-column prop="manufacturer_code" label="厂家编码" width="110" show-overflow-tooltip />
      <el-table-column prop="manufacturer_name" label="厂家名称" width="120" show-overflow-tooltip />
      <el-table-column prop="supplier_code" label="供应商编码" width="110" show-overflow-tooltip />
      <el-table-column prop="supplier_name" label="供应商名称" width="120" show-overflow-tooltip />
      <el-table-column prop="device_status" label="设备状态" width="90" fixed="right">
        <template #default="{ row }">
          <TableCellValue :field="{ prop: 'device_status', dictType: 'device_status' }" :value="row.device_status" />
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected" @click="confirmRow(selected!)">确认</el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import { useDict } from '@/composables/useDict'

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
const statusOptions = ref<{ label: string; value: string }[]>([])
const { loadDict } = useDict()

const filters = reactive({
  deviceCode: '',
  deviceName: '',
  specification: '',
  model: '',
  serialNumber: '',
  powerTagCode: '',
  deptName: '',
  deviceStatus: ''
})

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | number | boolean> = {
      page: 1,
      size: 100,
      hide_returned: true
    }
    if (filters.deviceCode.trim()) params.device_code = filters.deviceCode.trim()
    if (filters.deviceName.trim()) params.device_name = filters.deviceName.trim()
    if (filters.specification.trim()) params.specification = filters.specification.trim()
    if (filters.model.trim()) params.model = filters.model.trim()
    if (filters.serialNumber.trim()) params.serial_number = filters.serialNumber.trim()
    if (filters.powerTagCode.trim()) params.power_tag_code = filters.powerTagCode.trim()
    if (filters.deptName.trim()) params.dept_name = filters.deptName.trim()
    if (filters.deviceStatus) params.device_status = filters.deviceStatus
    const { data } = await http.get('/asset/device/page', { params })
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
  filters.deviceCode = ''
  filters.deviceName = ''
  filters.specification = ''
  filters.model = ''
  filters.serialNumber = ''
  filters.powerTagCode = ''
  filters.deptName = ''
  filters.deviceStatus = ''
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

onMounted(async () => {
  const opts = await loadDict('device_status')
  statusOptions.value = opts.map((o) => ({ label: o.label, value: o.value }))
})
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}
</style>
