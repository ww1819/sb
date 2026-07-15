<template>
  <div class="asset-query-page">
    <SystemPageCard
      title="资产综合查询"
      :loading="loading"
      show-pager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      @page-change="load"
    >
      <template #filterBar>
        <PageFilterBar
          v-model:keyword="keyword"
          placeholder="编码 / 名称 / 品牌 / 型号 / 序列号 / 注册证号"
          @search="onSearch"
          @reset="onReset"
        >
          <template #filters>
            <el-select v-model="filters.campusId" placeholder="院区" clearable filterable class="filter-item" @change="onSearch">
              <el-option v-for="o in campuses" :key="o.id" :label="o.campus_name" :value="o.id" />
            </el-select>
            <el-select v-model="filters.deptId" placeholder="使用科室" clearable filterable class="filter-item" @change="onSearch">
              <el-option v-for="o in departments" :key="o.id" :label="o.dept_name" :value="o.id" />
            </el-select>
            <el-select v-model="filters.categoryId" placeholder="68码分类" clearable filterable class="filter-item" @change="onSearch">
              <el-option v-for="o in categories" :key="o.id" :label="`${o.category_code} ${o.category_name}`" :value="o.id" />
            </el-select>
            <el-select v-model="filters.assetCategoryId" placeholder="资产分类" clearable filterable class="filter-item" @change="onSearch">
              <el-option v-for="o in assetCategories" :key="o.id" :label="o.category_name" :value="o.id" />
            </el-select>
            <el-select v-model="filters.financeCategoryId" placeholder="财务分类" clearable filterable class="filter-item" @change="onSearch">
              <el-option v-for="o in financeCategories" :key="o.id" :label="o.finance_name" :value="o.id" />
            </el-select>
            <el-select v-model="filters.deviceStatus" placeholder="设备状态" clearable class="filter-item" @change="onSearch">
              <el-option v-for="o in deviceStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable class="filter-item" @change="onSearch">
              <el-option v-for="o in riskLevelOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-model="filters.flagFilter" placeholder="质控标记" clearable class="filter-item" @change="onSearch">
              <el-option label="计量设备" value="metrology" />
              <el-option label="保养设备" value="maintain" />
              <el-option label="巡检设备" value="inspection" />
              <el-option label="生命支持" value="life" />
              <el-option label="应急设备" value="emergency" />
            </el-select>
          </template>
        </PageFilterBar>
      </template>

      <el-table v-loading="loading" :data="rows" stripe class="system-table" :height="tableHeight" row-key="id" @row-dblclick="openDetail">
        <el-table-column prop="device_code" label="资产编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="device_name" label="资产名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="brand" label="品牌" min-width="100" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" min-width="100" show-overflow-tooltip />
        <el-table-column prop="dept_name" label="使用科室" min-width="120" show-overflow-tooltip />
        <el-table-column prop="campus_name" label="院区" min-width="100" show-overflow-tooltip />
        <el-table-column prop="category_name" label="68码分类" min-width="140" show-overflow-tooltip />
        <el-table-column prop="asset_category_name" label="资产分类" min-width="120" show-overflow-tooltip />
        <el-table-column prop="device_status" label="状态" width="100">
          <template #default="{ row }">
            <TableCellValue :field="{ prop: 'device_status', dictType: 'device_status' }" :value="row.device_status" />
          </template>
        </el-table-column>
        <el-table-column prop="original_value" label="原值" width="100" align="right" />
        <el-table-column prop="enable_date" label="启用日期" width="110" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <PageEmpty description="暂无匹配设备" />
        </template>
      </el-table>
    </SystemPageCard>

    <el-drawer v-model="detailVisible" title="设备档案详情" size="72%" destroy-on-close>
      <DeviceDetailTabs v-if="detailDevice" :device="detailDevice" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import http from '@/api/http'
import SystemPageCard from '@/components/system/SystemPageCard.vue'
import PageFilterBar from '@/components/system/PageFilterBar.vue'
import PageEmpty from '@/components/table/PageEmpty.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import DeviceDetailTabs from '@/components/DeviceDetailTabs.vue'
import { useSystemTableHeight } from '@/composables/useSystemTableHeight'
import { useDict } from '@/composables/useDict'

const { loadDict } = useDict()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const tableHeight = useSystemTableHeight()

const filters = reactive({
  campusId: '',
  deptId: '',
  categoryId: '',
  assetCategoryId: '',
  financeCategoryId: '',
  deviceStatus: '',
  riskLevel: '',
  flagFilter: ''
})

const campuses = ref<any[]>([])
const departments = ref<any[]>([])
const categories = ref<any[]>([])
const assetCategories = ref<any[]>([])
const financeCategories = ref<any[]>([])
const deviceStatusOptions = ref<{ label: string; value: string }[]>([])
const riskLevelOptions = ref<{ label: string; value: string }[]>([])

const detailVisible = ref(false)
const detailDevice = ref<Record<string, unknown> | null>(null)

async function loadLookups() {
  const [campusRes, deptRes, catRes, acRes, fcRes] = await Promise.all([
    http.get('/system/campuses'),
    http.get('/system/departments'),
    http.get('/system/medical_device_category/list'),
    http.get('/system/asset_category/list'),
    http.get('/system/finance_category/list')
  ])
  if (campusRes.data.code === 0) campuses.value = campusRes.data.data ?? []
  if (deptRes.data.code === 0) departments.value = deptRes.data.data ?? []
  if (catRes.data.code === 0) categories.value = catRes.data.data ?? []
  if (acRes.data.code === 0) assetCategories.value = acRes.data.data ?? []
  if (fcRes.data.code === 0) financeCategories.value = fcRes.data.data ?? []
  deviceStatusOptions.value = await loadDict('device_status')
  riskLevelOptions.value = await loadDict('risk_level')
}

function buildParams() {
  const params: Record<string, string | number | boolean> = {
    page: page.value,
    size: size.value
  }
  if (keyword.value) params.keyword = keyword.value
  if (filters.campusId) params.campusId = filters.campusId
  if (filters.deptId) params.deptId = filters.deptId
  if (filters.categoryId) params.categoryId = filters.categoryId
  if (filters.assetCategoryId) params.assetCategoryId = filters.assetCategoryId
  if (filters.financeCategoryId) params.financeCategoryId = filters.financeCategoryId
  if (filters.deviceStatus) params.deviceStatus = filters.deviceStatus
  if (filters.riskLevel) params.riskLevel = filters.riskLevel
  if (filters.flagFilter === 'metrology') params.isMetrology = true
  if (filters.flagFilter === 'maintain') params.isMaintainDevice = true
  if (filters.flagFilter === 'inspection') params.isInspectionDevice = true
  if (filters.flagFilter === 'life') params.isLifeSupport = true
  if (filters.flagFilter === 'emergency') params.isEmergency = true
  return params
}

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/asset/medical_device/query/page', { params: buildParams() })
    if (data.code === 0) {
      rows.value = data.data?.records ?? data.data?.items ?? data.data?.list ?? []
      total.value = data.data?.total ?? 0
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  keyword.value = ''
  filters.campusId = ''
  filters.deptId = ''
  filters.categoryId = ''
  filters.assetCategoryId = ''
  filters.financeCategoryId = ''
  filters.deviceStatus = ''
  filters.riskLevel = ''
  filters.flagFilter = ''
  onSearch()
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/asset/device/${row.id}/detail`)
  if (data.code === 0) {
    detailDevice.value = data.data
    detailVisible.value = true
  }
}

onMounted(async () => {
  await loadLookups()
  await load()
})
</script>

<style scoped>
.asset-query-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.filter-item {
  width: 150px;
}
</style>
