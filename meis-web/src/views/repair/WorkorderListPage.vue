<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <el-button v-if="wo?.id" @click="dispatch">派工</el-button>
        <el-button v-if="wo?.id" @click="act('accept')">接单</el-button>
        <el-button v-if="wo?.id" @click="act('complete')">完工</el-button>
        <el-button v-if="wo?.id" @click="act('verify')">验收</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" title="工单详情" size="xl">
      <el-row v-if="wo" :gutter="16">
        <el-col :span="16">
          <GroupedFormFields :table="config.table" :model="wo" />
        </el-col>
        <el-col :span="8">
          <FormSection title="工单时间轴">
            <el-timeline>
              <el-timeline-item v-for="e in timeline" :key="e.label" :timestamp="e.time" placement="top">
                {{ e.label }}
              </el-timeline-item>
            </el-timeline>
          </FormSection>
        </el-col>
      </el-row>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '维修工单', apiBase: '/repair', table: 'repair_workorder' }
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const wo = ref<Record<string, unknown> | null>(null)

const timeline = computed(() => {
  if (!wo.value) return []
  const w = wo.value
  return [
    { label: '报修', time: fmt(w.report_time) },
    { label: '派工', time: fmt(w.assigned_at) },
    { label: '响应', time: fmt(w.response_time) },
    { label: '到场', time: fmt(w.arrival_time) },
    { label: '维修开始', time: fmt(w.repair_start_time) },
    { label: '维修结束', time: fmt(w.repair_end_time) },
    { label: '验收', time: fmt(w.verify_time) }
  ].filter((e) => e.time)
})

function fmt(v: unknown) {
  return v ? String(v).slice(0, 19) : ''
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/repair/workorder/${row.id}`)
  wo.value = data.data ?? { ...row }
  visible.value = true
}

async function dispatch() {
  if (!wo.value?.id) return
  await http.post(`/repair/workorder/${wo.value.id}/dispatch`, { engineerId: wo.value.assigned_engineer_id })
  await openDetail(wo.value)
  crudRef.value?.load()
}

async function act(type: string) {
  if (!wo.value?.id) return
  if (type === 'accept') await http.post(`/repair/workorder/${wo.value.id}/accept`)
  if (type === 'complete') {
    await http.post(`/repair/workorder/${wo.value.id}/complete`, {
      solution_description: wo.value.solution_description ?? '维修完成',
      parts_cost: wo.value.parts_cost ?? 0,
      labor_cost: wo.value.labor_cost ?? 0,
      total_cost: wo.value.total_cost ?? 0
    })
  }
  if (type === 'verify') {
    await http.post(`/repair/workorder/${wo.value.id}/verify`, {
      verifier_id: wo.value.reporter_id,
      verify_result: wo.value.verify_result ?? 'pass',
      verify_comment: wo.value.verify_comment,
      satisfaction_rating: wo.value.satisfaction_rating ?? 5
    })
  }
  await openDetail(wo.value)
  crudRef.value?.load()
}
</script>
