<template>
  <div class="fee-manual-page">
    <!-- 上部：查询条件 -->
    <div class="query-box">
      <div class="section-bar">费用手工登记</div>
      <div class="query-body">
        <el-form :model="filters" class="filter-form" label-width="88px" @submit.prevent>
          <el-row :gutter="12" align="middle">
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="设备类别">
                <el-input v-model="filters.category" clearable placeholder="设备类别" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-form-item label="设备名称">
                <el-input v-model="filters.assetName" clearable placeholder="设备名称" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="14" :md="7">
              <el-form-item label="价格区间">
                <div class="price-range">
                  <el-input v-model="filters.priceMin" clearable placeholder="最低价" />
                  <span class="price-sep">—</span>
                  <el-input v-model="filters.priceMax" clearable placeholder="最高价" />
                </div>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="10" :md="5" class="filter-actions">
              <el-form-item label-width="0">
                <div class="action-btns">
                  <el-button type="primary" @click="onSearch">查询</el-button>
                  <el-button @click="importVisible = true">费用Excel导入</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>

    <!-- 下部：左资产列表 + 右维护表单 -->
    <div class="main-split">
      <div class="panel panel--left">
        <div class="section-bar">资产列表</div>
        <div class="panel-body">
          <el-table
            :data="filteredAssets"
            border
            stripe
            height="100%"
            highlight-current-row
            class="asset-table"
            empty-text="暂无资产（前端示意）"
            @current-change="onSelectAsset"
          >
            <el-table-column prop="device_code" label="设备编号" min-width="100" show-overflow-tooltip />
            <el-table-column prop="asset_name" label="设备名称" min-width="110" show-overflow-tooltip />
            <el-table-column prop="model" label="设备型号" min-width="100" show-overflow-tooltip />
            <el-table-column prop="serial_no" label="设备序列号" min-width="110" show-overflow-tooltip />
            <el-table-column prop="category" label="设备类别" min-width="90" show-overflow-tooltip />
            <el-table-column prop="price_range" label="价格区间" min-width="90" show-overflow-tooltip />
            <el-table-column prop="price" label="原值" min-width="90" align="right" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.price.toLocaleString('zh-CN') }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="panel panel--right">
        <div class="section-bar">费用维护信息</div>
        <div class="panel-body form-scroll">
          <el-form :model="form" label-width="110px" class="fee-form" @submit.prevent>
            <el-row :gutter="12">
              <el-col :xs="24" :md="8">
                <el-form-item label="统计年月">
                  <el-date-picker
                    v-model="form.period"
                    type="month"
                    value-format="YYYY-MM"
                    placeholder="选择年月"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="记录人">
                  <el-input v-model="form.recorder" clearable placeholder="记录人" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="记录时间">
                  <el-date-picker
                    v-model="form.record_time"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="记录时间"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12">
              <el-col :xs="24" :md="8">
                <el-form-item label="设备类别">
                  <el-input v-model="form.category" readonly placeholder="请先选择左侧资产" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="价格区间">
                  <el-input v-model="form.price_range" readonly placeholder="—" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="设备名称">
                  <el-input v-model="form.asset_name" readonly placeholder="请先选择左侧资产" />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="mode-checks">
              <el-checkbox v-model="form.by_person">
                点击选中，按单个人次来计算收入支出
              </el-checkbox>
              <el-checkbox v-model="form.by_allocate">
                点击选中，输入同种设备收入支出总数自动分摊到每台设备
              </el-checkbox>
            </div>

            <div class="fee-grid">
              <el-form-item label="收入(月)">
                <el-input v-model="form.income" clearable />
              </el-form-item>
              <el-form-item label="材料费(月)">
                <el-input v-model="form.material_fee" clearable />
              </el-form-item>
              <el-form-item label="折旧费">
                <el-input v-model="form.depreciation" clearable />
              </el-form-item>
              <el-form-item label="人员费">
                <el-input v-model="form.staff_fee" clearable />
              </el-form-item>
              <el-form-item label="维保费">
                <el-input v-model="form.maintain_fee" clearable />
              </el-form-item>
              <el-form-item label="水电费(月)">
                <el-input v-model="form.utility_fee" clearable />
              </el-form-item>
              <el-form-item label="管理费">
                <el-input v-model="form.manage_fee" clearable />
              </el-form-item>
              <el-form-item label="利息支出">
                <el-input v-model="form.interest_fee" clearable />
              </el-form-item>
              <el-form-item label="维修停机天数">
                <el-input v-model="form.downtime_days" clearable />
              </el-form-item>
              <el-form-item label="诊疗人次">
                <el-input v-model="form.visit_count" clearable />
              </el-form-item>
              <el-form-item label="场地费">
                <el-input v-model="form.site_fee" clearable />
              </el-form-item>
              <el-form-item label="应开机工作日">
                <el-input v-model="form.work_days" clearable />
              </el-form-item>
              <el-form-item label="月工作量">
                <el-input v-model="form.workload" clearable />
              </el-form-item>
              <el-form-item label="使用率(%)">
                <el-input v-model="form.usage_rate" clearable />
              </el-form-item>
              <el-form-item label="完好率(%)">
                <el-input v-model="form.integrity_rate" clearable />
              </el-form-item>
              <el-form-item label="阳性率(%)">
                <el-input v-model="form.positive_rate" clearable />
              </el-form-item>
              <el-form-item label="其他">
                <el-input v-model="form.other_fee" clearable />
              </el-form-item>
            </div>

            <div class="form-actions">
              <el-button type="primary" @click="onSave">保存</el-button>
              <el-button @click="onClear">清空</el-button>
            </div>

            <p class="form-note">每月填写一次，设备科每月收费一次</p>
            <p class="form-formula">
              总支出 = 折旧费用 + 材料费 + 人员费 + 维保费 + 水电费 + 管理费 + 场地费 + 利息支出 + 其他
            </p>
          </el-form>
        </div>
      </div>
    </div>

    <FeeExcelImportDialog v-model="importVisible" @imported="onFeeImported" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FeeExcelImportDialog from './FeeExcelImportDialog.vue'
import type { FeeImportRow } from './FeeExcelImportDialog.vue'

type AssetRow = {
  id: string
  device_code: string
  asset_name: string
  model: string
  serial_no: string
  category: string
  price: number
  price_range: string
}

const filters = reactive({
  category: '',
  assetName: '',
  priceMin: '',
  priceMax: ''
})

const applied = reactive({ ...filters })

function emptyForm() {
  return {
    period: '',
    recorder: 'demo',
    record_time: '',
    category: '',
    price_range: '',
    asset_name: '',
    device_code: '',
    by_person: false,
    by_allocate: false,
    income: '',
    depreciation: '',
    maintain_fee: '',
    manage_fee: '',
    downtime_days: '',
    site_fee: '',
    workload: '',
    integrity_rate: '',
    other_fee: '',
    material_fee: '',
    staff_fee: '',
    utility_fee: '',
    interest_fee: '',
    visit_count: '',
    work_days: '',
    usage_rate: '',
    positive_rate: ''
  }
}

const form = reactive(emptyForm())
const selectedId = ref('')
const importVisible = ref(false)

const MOCK_ASSETS: AssetRow[] = [
  {
    id: '1',
    device_code: 'SB-0001',
    asset_name: '多参数监护仪',
    model: 'BeneView T5',
    serial_no: 'SN20240001',
    category: '监护设备',
    price: 68000,
    price_range: '1-10万'
  },
  {
    id: '2',
    device_code: 'SB-0002',
    asset_name: '呼吸机',
    model: 'Servo-i',
    serial_no: 'SN20240002',
    category: '急救设备',
    price: 185000,
    price_range: '10-20万'
  },
  {
    id: '3',
    device_code: 'SB-0003',
    asset_name: '超声诊断仪',
    model: 'Vivid E95',
    serial_no: 'SN20240003',
    category: '影像设备',
    price: 520000,
    price_range: '50万元以上'
  },
  {
    id: '4',
    device_code: 'SB-0004',
    asset_name: '输液泵',
    model: 'Injectomat',
    serial_no: 'SN20240004',
    category: '输液设备',
    price: 8500,
    price_range: '万元以下'
  },
  {
    id: '5',
    device_code: 'SB-0005',
    asset_name: '心电图机',
    model: 'ECG-1250',
    serial_no: 'SN20240005',
    category: '电生理',
    price: 32000,
    price_range: '1-10万'
  },
  {
    id: '6',
    device_code: 'SB-0006',
    asset_name: '麻醉机',
    model: 'Aisys CS2',
    serial_no: 'SN20240006',
    category: '麻醉设备',
    price: 268000,
    price_range: '20-50万'
  },
  {
    id: '7',
    device_code: 'SB-0007',
    asset_name: '血球仪',
    model: 'XN-1000',
    serial_no: 'SN20240007',
    category: '检验设备',
    price: 156000,
    price_range: '10-20万'
  },
  {
    id: '8',
    device_code: 'SB-0008',
    asset_name: '除颤仪',
    model: 'Lifepak 20',
    serial_no: 'SN20240008',
    category: '急救设备',
    price: 98000,
    price_range: '1-10万'
  }
]

const filteredAssets = computed(() => {
  const cat = applied.category.trim()
  const name = applied.assetName.trim()
  const min = Number(applied.priceMin)
  const max = Number(applied.priceMax)
  return MOCK_ASSETS.filter((row) => {
    if (cat && !row.category.includes(cat) && !row.asset_name.includes(cat)) return false
    if (name && !row.asset_name.includes(name) && !row.device_code.includes(name)) return false
    if (applied.priceMin !== '' && Number.isFinite(min) && row.price < min) return false
    if (applied.priceMax !== '' && Number.isFinite(max) && row.price > max) return false
    return true
  })
})

function onSearch() {
  Object.assign(applied, { ...filters })
}

function onSelectAsset(row: AssetRow | undefined) {
  if (!row) return
  selectedId.value = row.id
  form.category = row.category
  form.price_range = row.price_range
  form.asset_name = row.asset_name
  form.device_code = row.device_code
}

function onSave() {
  if (!selectedId.value) {
    ElMessage.warning('请先在左侧选择固定资产')
    return
  }
  ElMessage.success('已保存（前端示意，未接后台）')
}

function onClear() {
  const keepAsset = {
    category: form.category,
    price_range: form.price_range,
    asset_name: form.asset_name,
    device_code: form.device_code
  }
  Object.assign(form, emptyForm(), keepAsset)
  ElMessage.info('已清空费用项')
}

function applyImportRow(row: FeeImportRow) {
  form.period = row['统计年月'] || form.period
  form.income = row['收入(月)'] || ''
  form.material_fee = row['材料费(月)'] || ''
  form.depreciation = row['折旧费'] || ''
  form.staff_fee = row['人员费'] || ''
  form.maintain_fee = row['维保费'] || ''
  form.utility_fee = row['水电费(月)'] || ''
  form.manage_fee = row['管理费'] || ''
  form.interest_fee = row['利息支出'] || ''
  form.downtime_days = row['维修停机天数'] || ''
  form.visit_count = row['诊疗人次'] || ''
  form.site_fee = row['场地费'] || ''
  form.work_days = row['应开机工作日'] || ''
  form.workload = row['月工作量'] || ''
  form.usage_rate = row['使用率(%)'] || ''
  form.integrity_rate = row['完好率(%)'] || ''
  form.positive_rate = row['阳性率(%)'] || ''
  form.other_fee = row['其他'] || ''
  if (row['设备名称']) form.asset_name = row['设备名称']
  if (row['设备编号']) form.device_code = row['设备编号']
}

function onFeeImported(rows: FeeImportRow[]) {
  if (!rows.length) return
  const preferred =
    rows.find((r) => r['设备编号'] && r['设备编号'] === form.device_code) || rows[0]
  const asset = MOCK_ASSETS.find((a) => a.device_code === preferred['设备编号'])
  if (asset) {
    selectedId.value = asset.id
    form.category = asset.category
    form.price_range = asset.price_range
    form.asset_name = asset.asset_name
    form.device_code = asset.device_code
  }
  applyImportRow(preferred)
}
</script>

<style scoped>
.fee-manual-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
  box-sizing: border-box;
}

.query-box,
.panel {
  background: #fff;
  border: 1px solid var(--meis-report-line);
  border-radius: 2px;
  overflow: hidden;
}

.query-box {
  flex-shrink: 0;
  border-color: var(--meis-report-border);
}

.section-bar {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #303133;
  border-bottom: 1px solid var(--meis-report-line);
  background: var(--meis-report-header-bg);
}

.query-box > .section-bar {
  border-bottom-color: var(--meis-report-border);
}

.query-body {
  padding: 12px 16px 4px;
}

.filter-form {
  margin-bottom: 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 12px;
  width: 100%;
}

.filter-actions :deep(.el-form-item__content) {
  justify-content: flex-start;
}

.action-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.price-range {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.price-sep {
  color: #909399;
  flex-shrink: 0;
}

.main-split {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 8px;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.panel--left {
  flex: 0 0 48%;
  min-width: 520px;
  max-width: 640px;
}

.panel--right {
  flex: 1;
}

.panel .section-bar {
  flex-shrink: 0;
}

.panel-body {
  flex: 1;
  min-height: 0;
  padding: 0;
}

.form-scroll {
  overflow: auto;
  padding: 12px 16px 16px;
}

.asset-table {
  width: 100%;
}

.panel--left :deep(.el-table) {
  --el-table-header-bg-color: var(--meis-report-header-bg);
  --el-table-row-hover-bg-color: var(--meis-report-hover);
  --el-table-current-row-bg-color: var(--meis-report-hover);
  --el-table-border-color: var(--meis-report-line);
}

.panel--left :deep(.el-table th.el-table__cell) {
  font-weight: 400;
  color: #303133;
}

.panel--left :deep(.el-table__row--striped td.el-table__cell) {
  background: var(--meis-report-stripe);
}

.mode-checks {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 4px 0 12px;
  padding: 8px 12px;
  background: #f5f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 2px;
}

.fee-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 12px;
  padding: 8px 8px 0;
  border: 1px solid var(--meis-report-line);
  border-radius: 2px;
  background: #fafcfe;
}

.fee-grid :deep(.el-form-item) {
  margin-bottom: 10px;
}

.fee-grid :deep(.el-form-item__label) {
  width: 96px !important;
}

.fee-grid :deep(.el-form-item__content) {
  margin-left: 96px !important;
}

.form-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

.form-note {
  margin: 12px 0 4px;
  font-size: 13px;
  color: #303133;
}

.form-formula {
  margin: 0;
  font-size: 13px;
  color: #f56c6c;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .main-split {
    flex-direction: column;
  }

  .panel--left {
    flex: none;
    min-width: 0;
    max-width: none;
    height: 260px;
  }

  .fee-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
