<template>
  <div>
    <h3>{{ title }}</h3>
    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column v-for="col in columns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const route = useRoute()
const loading = ref(false)
const rows = ref<any[]>([])
const columns = ref<string[]>([])

const apiPrefix = computed(() => {
  const m = route.params.module as string
  const map: Record<string, string> = {
    purchase: 'purchase', asset: 'asset', repair: 'repair', maintain: 'maintain',
    qc: 'qc', 'maintenance-contract': 'maintenance-contract', special: 'special',
    system: 'system', analytics: 'analytics'
  }
  return map[m] || m
})

const table = computed(() => {
  const key = `${route.params.module}/${route.params.page}`
  const map: Record<string, string> = {
    'purchase/plan': 'purchase_plan', 'purchase/project': 'purchase_project', 'purchase/bidding': 'purchase_plan_item', 'purchase/contract': 'purchase_contract',
    'asset/device': 'medical_device', 'asset/entry': 'device_entry', 'asset/transfer': 'asset_transfer',
    'asset/inventory': 'inventory_check', 'asset/scrap': 'device_scrap',
    'repair/workorder': 'repair_workorder', 'repair/engineer': 'engineer', 'repair/spare': 'spare_part',
    'maintain/template': 'maintenance_template', 'maintain/plan': 'maintenance_plan', 'maintain/record': 'maintenance_record',
    'qc/risk': 'risk_assessment', 'qc/adverse': 'adverse_event', 'qc/metrology': 'metrology_record', 'qc/performance': 'performance_test',
    'maintenance-contract/list': 'maintenance_contract', 'maintenance-contract/fulfillment': 'maintenance_fulfillment',
    'special/life': 'life_support_device', 'special/emergency': 'emergency_device_pool', 'special/leased': 'leased_device',
    'analytics/benefit': 'device_benefit_summary',
    'system/campus': 'campus', 'system/dept': 'department', 'system/user': 'sys_user', 'system/role': 'sys_role',
    'system/dict': 'sys_dict', 'system/log': 'sys_operation_log'
  }
  return map[key] || (route.params.page as string).replace(/-/g, '_')
})

const title = computed(() => `${apiPrefix.value} / ${table.value}`)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get(`/${apiPrefix.value}/${table.value}/list`)
    if (data.code === 0) {
      rows.value = data.data || []
      columns.value = rows.value.length ? Object.keys(rows.value[0]) : []
    }
  } finally {
    loading.value = false
  }
}

async function remove(id: string) {
  await ElMessageBox.confirm('确认删除？', '提示')
  const { data } = await http.delete(`/${apiPrefix.value}/${table.value}/${id}`)
  if (data.code === 0) {
    ElMessage.success('已删除')
    load()
  }
}

onMounted(load)
</script>
