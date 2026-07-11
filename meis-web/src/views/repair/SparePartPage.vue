<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="备件库存" name="stock">
      <CrudPage :config="config" />
    </el-tab-pane>
    <el-tab-pane label="库存预警" name="alerts">
      <el-table :data="alerts" border v-loading="loading">
        <el-table-column prop="part_code" label="编码" />
        <el-table-column prop="part_name" label="名称" />
        <el-table-column prop="stock_quantity" label="库存" />
        <el-table-column prop="min_stock" label="最低库存" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="流水记录" name="txn">
      <el-table :data="transactions" border v-loading="loading">
        <el-table-column prop="part_name" label="备件" />
        <el-table-column prop="txn_type" label="类型" />
        <el-table-column prop="quantity" label="数量" />
        <el-table-column prop="created_at" label="时间" />
      </el-table>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '配件档案管理', apiBase: '/repair', table: 'spare_part' }
const tab = ref('stock')
const loading = ref(false)
const alerts = ref<Record<string, unknown>[]>([])
const transactions = ref<Record<string, unknown>[]>([])

async function loadExtra() {
  loading.value = true
  try {
    if (tab.value === 'alerts') {
      const { data } = await http.get('/repair/spare/alerts')
      alerts.value = data.data ?? []
    }
    if (tab.value === 'txn') {
      const { data } = await http.get('/repair/spare/transactions')
      transactions.value = data.data ?? []
    }
  } finally {
    loading.value = false
  }
}

watch(tab, loadExtra)
onMounted(loadExtra)
</script>
