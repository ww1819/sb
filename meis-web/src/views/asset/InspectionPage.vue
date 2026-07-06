<template>
  <div class="inspection-page">
    <el-tabs v-model="tab" class="inspection-tabs">
      <el-tab-pane label="巡检计划" name="plan">
        <CrudPage :config="planConfig" @detail="openPlan" />
      </el-tab-pane>
      <el-tab-pane label="巡检记录" name="record">
        <CrudPage :config="recordConfig" />
      </el-tab-pane>
    </el-tabs>

    <AppModal v-model="visible" title="巡检计划详情" size="lg">
      <GroupedFormFields v-if="plan" :table="planConfig.table" :model="plan" />
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import type { PageConfig } from '@/config/pageRegistry'

const tab = ref('plan')
const planConfig: PageConfig = { title: '巡检计划', apiBase: '/asset', table: 'inspection_plan' }
const recordConfig: PageConfig = { title: '巡检记录', apiBase: '/asset', table: 'inspection_record' }
const visible = ref(false)
const plan = ref<Record<string, unknown> | null>(null)

function openPlan(row: Record<string, unknown>) {
  plan.value = { ...row }
  visible.value = true
}
</script>

<style scoped>
.inspection-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.inspection-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.inspection-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.inspection-tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>
