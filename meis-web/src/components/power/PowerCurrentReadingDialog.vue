<template>
  <AppModal v-model="visible" :title="title" size="xl" @close="onClose">
    <el-form :inline="true" class="filter-form">
      <el-form-item label="开始时间">
        <el-date-picker
          v-model="readAtFrom"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="开始时间"
          clearable
        />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker
          v-model="readAtTo"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="结束时间"
          clearable
        />
      </el-form-item>
      <el-form-item label="排序">
        <el-select v-model="sortOrder" style="width: 120px">
          <el-option label="读取时间↓" value="desc" />
          <el-option label="读取时间↑" value="asc" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button v-if="exportUrl" @click="onExport">导出</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" row-key="id" max-height="420">
      <el-table-column prop="tag_code" label="标签编码" min-width="110" />
      <el-table-column v-if="showStationCode" prop="station_code" label="基站编码" min-width="110" />
      <el-table-column prop="device_code" label="设备编码" min-width="110" />
      <el-table-column prop="current_ma" label="电流(mA)" width="100" align="right" />
      <el-table-column prop="read_at" label="读取时间" min-width="160">
        <template #default="{ row }">{{ fmt(row.read_at) }}</template>
      </el-table-column>
      <el-table-column prop="created_at" label="插入时间" min-width="160">
        <template #default="{ row }">{{ fmt(row.created_at) }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
        @size-change="onSearch"
      />
    </div>
  </AppModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { downloadApiFile } from '@/utils/fileDownload'
import AppModal from '@/components/AppModal.vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  listUrl: string
  exportUrl?: string
  showStationCode?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const readAtFrom = ref<string>()
const readAtTo = ref<string>()
const sortOrder = ref('desc')

function fmt(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, string | number> = {
      page: page.value,
      size: size.value,
      sortOrder: sortOrder.value
    }
    if (readAtFrom.value) params.readAtFrom = readAtFrom.value
    if (readAtTo.value) params.readAtTo = readAtTo.value
    const { data } = await http.get(props.listUrl, { params })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  void load()
}

async function onExport() {
  if (!props.exportUrl) return
  try {
    const params = new URLSearchParams({ sortOrder: sortOrder.value })
    if (readAtFrom.value) params.set('readAtFrom', readAtFrom.value)
    if (readAtTo.value) params.set('readAtTo', readAtTo.value)
    await downloadApiFile(`${props.exportUrl}?${params.toString()}`, 'tag_readings.csv')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败')
  }
}

function onClose() {
  rows.value = []
  total.value = 0
  page.value = 1
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    onSearch()
  }
)
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
