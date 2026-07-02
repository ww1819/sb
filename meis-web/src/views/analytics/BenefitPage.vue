<template>
  <div class="benefit-page">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="科室效益排名">
          <el-table :data="ranking" size="small">
            <el-table-column prop="dept_name" label="科室" />
            <el-table-column prop="net_profit" label="净利润" />
            <el-table-column prop="avg_profit_rate" label="平均利润率" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="效益汇总">
          <CrudPage :config="{ title: '效益汇总', apiBase: '/analytics', table: 'device_benefit_summary' }" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'

const ranking = ref<Record<string, unknown>[]>([])

onMounted(async () => {
  const { data } = await http.get('/analytics/benefit/dept-ranking')
  ranking.value = data.data ?? []
})
</script>
