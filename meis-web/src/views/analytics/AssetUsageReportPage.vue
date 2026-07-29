<template>
  <div class="asset-usage-report">
    <el-tabs v-model="activeTab" class="report-tabs">
      <el-tab-pane label="明细表" name="detail" />
      <el-tab-pane label="汇总表" name="summary" />
    </el-tabs>

    <!-- 明细表：查询 + 表格 -->
    <template v-if="activeTab === 'detail'">
      <div class="query-box">
        <div class="query-title">查询条件</div>
        <el-form :model="detailFilters" class="filter-form" label-width="80px" @submit.prevent>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="科室">
                <el-input v-model="detailFilters.deptName" clearable placeholder="科室搜索" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="资产名称">
                <el-input v-model="detailFilters.assetName" clearable placeholder="资产名称搜索" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
              <el-form-item label-width="0">
                <el-button type="primary" @click="onDetailSearch">查询</el-button>
                <el-button @click="onDetailReset">重置</el-button>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="资产规格">
                <el-input v-model="detailFilters.specification" clearable placeholder="资产规格搜索" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <el-table :data="detailRows" border empty-text="暂无数据（前端样式示意，未接后台）">
        <el-table-column prop="asset_code" label="资产编号" min-width="140" />
        <el-table-column prop="dept_name" label="科室" min-width="120" />
        <el-table-column prop="asset_name" label="资产名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="usage_rate" label="使用率" width="100" align="right" />
        <el-table-column prop="specification" label="资产规格" min-width="140" show-overflow-tooltip />
        <el-table-column prop="price" label="资产价格" width="120" align="right" />
      </el-table>
    </template>

    <!-- 汇总表：查询 + 表格 -->
    <template v-else>
      <div class="query-box">
        <div class="query-title">查询条件</div>
        <el-form :model="summaryFilters" class="filter-form" label-width="80px" @submit.prevent>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="科室">
                <el-input v-model="summaryFilters.deptName" clearable placeholder="科室搜索" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="使用率≥">
                <el-input v-model="summaryFilters.minUsageRate" clearable placeholder="最低使用率，如 60" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8" class="filter-actions">
              <el-form-item label-width="0">
                <el-button type="primary" @click="onSummarySearch">查询</el-button>
                <el-button @click="onSummaryReset">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <el-table :data="summaryRows" border empty-text="暂无数据（前端样式示意，未接后台）">
        <el-table-column prop="dept_name" label="科室" min-width="160" />
        <el-table-column prop="asset_count" label="资产数量" width="120" align="right" />
        <el-table-column prop="avg_usage_rate" label="平均使用率" width="120" align="right" />
        <el-table-column prop="total_price" label="资产总价" min-width="140" align="right" />
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'

const activeTab = ref<'detail' | 'summary'>('detail')

const detailFilters = reactive({
  deptName: '',
  assetName: '',
  specification: ''
})

const summaryFilters = reactive({
  deptName: '',
  minUsageRate: ''
})

/** 仅前端样式示意，不请求后台 */
const detailRows = ref<
  {
    asset_code: string
    dept_name: string
    asset_name: string
    usage_rate: string
    specification: string
    price: string
  }[]
>([])

const summaryRows = ref<
  {
    dept_name: string
    asset_count: string
    avg_usage_rate: string
    total_price: string
  }[]
>([])

function onDetailSearch() {
  // 本期不接后台
}

function onDetailReset() {
  detailFilters.deptName = ''
  detailFilters.assetName = ''
  detailFilters.specification = ''
}

function onSummarySearch() {
  // 本期不接后台
}

function onSummaryReset() {
  summaryFilters.deptName = ''
  summaryFilters.minUsageRate = ''
}
</script>

<style scoped>
.asset-usage-report {
  min-height: 0;
}

.report-tabs {
  margin-bottom: 0;
}

.report-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.query-box {
  margin-bottom: 8px;
  margin-top: 0;
  padding: 12px 16px 4px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 4px;
}

.query-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
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
</style>
