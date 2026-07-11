<template>
  <div class="metrology-device-page">
    <SystemPageCard title="计量设备管理" :loading="loading" show-pager v-model:page="page" v-model:size="size" :total="total" @page-change="load">
      <template #filterBar>
        <PageFilterBar v-model:keyword="keyword" placeholder="设备编码 / 名称" @search="onSearch" @reset="onReset">
          <template #filters>
            <el-checkbox v-model="metrologyOnly" @change="onSearch">仅计量设备</el-checkbox>
          </template>
          <template #actions>
            <el-button :disabled="!selected.length" @click="toggleMetrology(true)">标记为计量设备</el-button>
            <el-button :disabled="!selected.length" @click="toggleMetrology(false)">取消计量标记</el-button>
            <el-button type="primary" :disabled="!selectedPlans.length" @click="batchGenerate">批量生成执行单</el-button>
          </template>
        </PageFilterBar>
      </template>
      <el-table v-loading="loading" :data="rows" stripe class="system-table" row-key="id" @selection-change="onSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="device_code" label="设备编码" min-width="120" />
        <el-table-column prop="device_name" label="设备名称" min-width="160" />
        <el-table-column prop="dept_name" label="科室" min-width="120" />
        <el-table-column prop="is_metrology" label="计量设备" width="90">
          <template #default="{ row }">
            <el-tag :type="row.is_metrology ? 'success' : 'info'" size="small">{{ row.is_metrology ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="next_calibration_date" label="下次检定" width="120" />
        <el-table-column prop="org_name" label="检定机构" min-width="140" />
        <el-table-column prop="plan_approval_status" label="计划审核" width="100" />
      </el-table>
    </SystemPageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import SystemPageCard from '@/components/SystemPageCard.vue'
import PageFilterBar from '@/components/PageFilterBar.vue'

const auth = useAuthStore()
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const metrologyOnly = ref(true)
const selected = ref<Record<string, unknown>[]>([])
const selectedPlans = computed(() => selected.value.filter(r => r.plan_id && r.plan_approval_status === 'approved'))

async function load() {
  loading.value = true
  try {
    const { data } = await http.get('/metrology/device/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined, metrologyOnly: metrologyOnly.value || undefined }
    })
    rows.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

function onSearch() { page.value = 1; load() }
function onReset() { keyword.value = ''; metrologyOnly.value = true; onSearch() }
function onSelect(val: Record<string, unknown>[]) { selected.value = val }

async function toggleMetrology(flag: boolean) {
  await http.post('/metrology/device/toggle', { deviceIds: selected.value.map(r => r.id), is_metrology: flag })
  ElMessage.success('已更新计量设备标记')
  load()
}

async function batchGenerate() {
  const planIds = selected.value.filter(r => r.plan_id && r.plan_approval_status === 'approved').map(r => String(r.plan_id))
  if (!planIds.length) { ElMessage.warning('请选择已审核计划的设备'); return }
  await http.post('/metrology/device/generate-execution', { planIds, created_by: auth.user?.id })
  ElMessage.success(`已生成 ${planIds.length} 条计量执行单`)
}

onMounted(load)
</script>
