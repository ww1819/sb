<template>
  <AppModal v-model="visible" title="选择盘点设备" size="xl" @close="onClose">
    <PageFilterBar v-model:keyword="keyword" placeholder="资产编码/名称" @search="load" @reset="onReset" />
    <ListSelectionBar
      :count="selectedCount"
      :has-current-page-rows="rows.length > 0"
      :cross-page-hint="false"
      @select-page="onSelectPage"
      @clear="onClearSelection"
    />
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      row-key="id"
      max-height="420"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column prop="device_code" label="资产编码" min-width="120" />
      <el-table-column prop="device_name" label="资产名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="brand" label="品牌" min-width="100" />
      <el-table-column prop="specification" label="规格" min-width="100" />
      <el-table-column prop="model" label="型号" min-width="100" />
      <el-table-column prop="location_detail" label="位置" min-width="120" show-overflow-tooltip />
      <el-table-column prop="device_status" label="状态" min-width="90" />
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
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import ListSelectionBar from '@/components/ListSelectionBar.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import { useCrossPageSelection } from '@/composables/useCrossPageSelection'

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
const keyword = ref('')
const rows = ref<Record<string, unknown>[]>([])
const selected = ref<Record<string, unknown>[]>([])
const tableRef = ref()
const { selectedCount, syncFromTable, selectCurrentPage, clearAll } = useCrossPageSelection()

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
    const { data } = await http.get('/asset/inventory/devices/candidates', { params })
    let list = (data.data ?? []) as Record<string, unknown>[]
    if (keyword.value) {
      const kw = keyword.value.toLowerCase()
      list = list.filter((r) => {
        const code = String(r.device_code ?? '').toLowerCase()
        const name = String(r.device_name ?? '').toLowerCase()
        return code.includes(kw) || name.includes(kw)
      })
    }
    rows.value = list
  } finally {
    loading.value = false
  }
}

function onReset() {
  keyword.value = ''
  load()
}

function onSelectionChange(selection: Record<string, unknown>[]) {
  selected.value = selection
  syncFromTable(selection)
}

function onSelectPage() {
  selectCurrentPage(tableRef.value, rows.value)
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
  keyword.value = ''
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
</script>
