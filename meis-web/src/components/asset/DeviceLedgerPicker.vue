<template>
  <AppModal v-model="visible" title="选择盘点设备" size="xl" @close="onClose">
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
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      row-key="id"
      max-height="420"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" reserve-selection fixed="left" />
      <DeviceLedgerTableColumns code-fixed="left" />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selected.length" @click="confirm">
        确认添加（{{ selected.length }}）
      </el-button>
    </template>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import DeviceLedgerTableColumns from '@/components/table/DeviceLedgerTableColumns.vue'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'
import { useDict } from '@/composables/useDict'

const props = defineProps<{
  modelValue: boolean
  deptId?: string
  campusId?: string
  checkId?: string
  excludeIds?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  confirm: [devices: Record<string, unknown>[]]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const selected = ref<Record<string, unknown>[]>([])
const tableRef = ref()
const statusOptions = ref<{ label: string; value: string }[]>([])
const { syncFromTable, clearAll } = useCrossPageSelection()
const { loadDict } = useDict()

const filters = reactive({
  deviceCode: '',
  deviceName: '',
  specification: '',
  model: '',
  serialNumber: '',
  powerTagCode: '',
  deviceStatus: ''
})

async function load() {
  if (!props.deptId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const params: Record<string, string | string[]> = {
      deptId: props.deptId
    }
    if (props.campusId) params.campusId = props.campusId
    if (props.checkId) params.checkId = props.checkId
    if (props.excludeIds?.length) params.excludeIds = props.excludeIds
    if (filters.deviceCode.trim()) params.deviceCode = filters.deviceCode.trim()
    if (filters.deviceName.trim()) params.deviceName = filters.deviceName.trim()
    if (filters.specification.trim()) params.specification = filters.specification.trim()
    if (filters.model.trim()) params.model = filters.model.trim()
    if (filters.serialNumber.trim()) params.serialNumber = filters.serialNumber.trim()
    if (filters.powerTagCode.trim()) params.powerTagCode = filters.powerTagCode.trim()
    if (filters.deviceStatus) params.deviceStatus = filters.deviceStatus
    const { data } = await http.get('/asset/inventory/devices/candidates', { params })
    rows.value = (data.data ?? []) as Record<string, unknown>[]
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
  filters.deviceStatus = ''
  load()
}

function onSelectionChange(selection: Record<string, unknown>[]) {
  selected.value = selection
  syncFromTable(selection)
}

function onClearSelection() {
  clearAll(tableRef.value)
  selected.value = []
}

function confirm() {
  if (!selected.value.length) return
  emit('confirm', selected.value)
  visible.value = false
}

function onClose() {
  onClearSelection()
  onResetFiltersOnly()
}

function onResetFiltersOnly() {
  filters.deviceCode = ''
  filters.deviceName = ''
  filters.specification = ''
  filters.model = ''
  filters.serialNumber = ''
  filters.powerTagCode = ''
  filters.deviceStatus = ''
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    if (!props.deptId) {
      ElMessage.warning('请先在主表选择科室')
      visible.value = false
      return
    }
    onClearSelection()
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
