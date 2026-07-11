<template>
  <div class="benefit-summary-page">
    <el-row :gutter="16" class="toolbar-row">
      <el-col :span="16">
        <el-space>
          <el-date-picker v-model="period" type="month" placeholder="汇总月份" value-format="YYYY-MM" />
          <el-button type="primary" :loading="recomputing" @click="recompute">重算汇总</el-button>
        </el-space>
      </el-col>
      <el-col :span="8">
        <el-select v-model="benefitLevel" placeholder="效益等级" clearable class="level-filter" @change="reload">
          <el-option label="优秀" value="excellent" />
          <el-option label="良好" value="good" />
          <el-option label="一般" value="normal" />
          <el-option label="较差" value="poor" />
        </el-select>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="科室效益排名">
          <el-table :data="ranking" size="small" max-height="280">
            <el-table-column prop="dept_name" label="科室" />
            <el-table-column prop="total_revenue" label="收入" />
            <el-table-column prop="net_profit" label="净利润" />
            <el-table-column prop="avg_profit_rate" label="利润率" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="效益趋势（近24月）">
          <el-table :data="trend" size="small" max-height="280">
            <el-table-column label="期间">
              <template #default="{ row }">{{ row.summary_year }}-{{ String(row.summary_month).padStart(2, '0') }}</template>
            </el-table-column>
            <el-table-column prop="revenue" label="收入" />
            <el-table-column prop="cost" label="成本" />
            <el-table-column prop="profit" label="利润" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-card header="设备效益汇总" class="summary-table-card">
      <CrudPage ref="crudRef" :config="config" hide-add />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const baseConfig = getPageConfig('/analytics/summary')!
const config = computed(() => ({
  ...baseConfig,
  listParams: {
    year: yearMonth.value.year,
    month: yearMonth.value.month,
    benefitLevel: benefitLevel.value || undefined
  } as Record<string, string | number | boolean | undefined>
}))
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const ranking = ref<Record<string, unknown>[]>([])
const trend = ref<Record<string, unknown>[]>([])
const period = ref('')
const benefitLevel = ref('')
const recomputing = ref(false)

const yearMonth = computed(() => {
  if (!period.value) return { year: undefined, month: undefined }
  const [y, m] = period.value.split('-')
  return { year: Number(y), month: Number(m) }
})

async function loadRanking() {
  const { data } = await http.get('/analytics/summary/dept-ranking', {
    params: { year: yearMonth.value.year, month: yearMonth.value.month }
  })
  ranking.value = data.data ?? []
}

async function loadTrend() {
  const { data } = await http.get('/analytics/summary/trend')
  trend.value = data.data ?? []
}

function reload() {
  crudRef.value?.load()
  loadRanking()
}

async function recompute() {
  if (!period.value) {
    ElMessage.warning('请选择汇总月份')
    return
  }
  recomputing.value = true
  try {
    const { data } = await http.post('/analytics/summary/recompute', yearMonth.value)
    ElMessage.success(`已重算 ${data.data?.deviceCount ?? 0} 台设备`)
    reload()
    loadTrend()
  } finally {
    recomputing.value = false
  }
}

watch(period, reload)
onMounted(() => {
  const now = new Date()
  period.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  loadTrend()
})
</script>

<style scoped>
.toolbar-row { margin-bottom: 16px; }
.summary-table-card { margin-top: 16px; }
.level-filter { width: 160px; float: right; }
</style>
