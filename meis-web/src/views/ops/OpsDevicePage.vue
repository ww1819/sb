<template>
  <div class="ops-device-page">
    <CrudPage ref="crudRef" :config="config" detail-mode :hide-add="true" @detail="openDetail" />

    <AppModal v-model="visible" :title="`${device?.device_name || ''} · 计划与执行`" size="xl">
      <el-tabs v-if="device">
        <el-tab-pane label="关联计划" name="plans">
          <el-table :data="plans" border size="small">
            <el-table-column prop="plan_no" label="计划单号" width="140" />
            <el-table-column prop="plan_name" label="计划名称" min-width="140" />
            <el-table-column prop="next_due_date" label="下次到期" width="120" />
            <el-table-column prop="approval_status" label="审核状态" width="100" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="执行记录" name="execs">
          <el-table :data="executions" border size="small">
            <el-table-column prop="execution_no" label="执行单号" width="140" />
            <el-table-column prop="source_type" label="来源" width="100" />
            <el-table-column prop="execution_status" label="状态" width="100" />
            <el-table-column prop="planned_date" label="计划日期" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="openAdHoc">直开执行单</el-button>
      </template>
    </AppModal>

    <AppModal v-model="adHocVisible" title="无计划直开执行单" size="md">
      <el-form label-width="100px">
        <el-form-item label="模板" required>
          <el-select v-model="adHoc.template_id" filterable style="width: 100%" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="String(t.id)" :label="String(t.template_name)" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="typeLabel" required>
          <el-input v-model="adHoc.typeValue" :placeholder="`填写${typeLabel}`" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="adHoc.planned_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adHocVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdHoc">创建</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import type { PageConfig } from '@/config/pageRegistry'

const props = defineProps<{
  module: 'maintain' | 'inspect' | 'pm'
}>()

const titles = { maintain: '保养设备管理', inspect: '巡检设备管理', pm: '预防性维护设备管理' }
const tables = { maintain: 'ops_maintain_device', inspect: 'ops_inspect_device', pm: 'ops_pm_device' }
const typeLabel = computed(() =>
  props.module === 'maintain' ? '保养级别' : props.module === 'inspect' ? '巡检类别' : 'PM类别'
)

const config = computed<PageConfig>(() => ({
  title: titles[props.module],
  apiBase: `/${props.module}`,
  table: tables[props.module],
  listPageUrl: `/${props.module}/device/page`,
  hideAdd: true
}))

const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const device = ref<Record<string, unknown> | null>(null)
const plans = ref<Record<string, unknown>[]>([])
const executions = ref<Record<string, unknown>[]>([])
const adHocVisible = ref(false)
const templates = ref<Record<string, unknown>[]>([])
const adHoc = ref<Record<string, unknown>>({
  template_id: null,
  typeValue: '',
  planned_date: null
})

async function openDetail(row: Record<string, unknown>) {
  device.value = row
  const id = row.id
  const [p, e] = await Promise.all([
    http.get(`/${props.module}/device/${id}/plans`),
    http.get(`/${props.module}/device/${id}/executions`)
  ])
  plans.value = p.data.data ?? []
  executions.value = e.data.data ?? []
  visible.value = true
}

async function openAdHoc() {
  if (!device.value?.id) return
  const table =
    props.module === 'maintain'
      ? 'maintenance_template'
      : props.module === 'inspect'
        ? 'inspection_template'
        : 'pm_template'
  const { data } = await http.get(`/${props.module}/${table}/list`)
  templates.value = data.data ?? []
  adHoc.value = { template_id: null, typeValue: '', planned_date: null }
  adHocVisible.value = true
}

function onTemplateChange() {
  const t = templates.value.find((x) => String(x.id) === String(adHoc.value.template_id))
  if (!t) return
  if (props.module === 'maintain') adHoc.value.typeValue = t.maintenance_level ?? ''
  if (props.module === 'inspect') adHoc.value.typeValue = t.inspection_type ?? t.type_name ?? ''
  if (props.module === 'pm') adHoc.value.typeValue = t.pm_type ?? t.type_name ?? ''
}

async function submitAdHoc() {
  if (!device.value?.id) return
  if (!adHoc.value.template_id) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!adHoc.value.typeValue) {
    ElMessage.warning(`请填写${typeLabel.value}`)
    return
  }
  const body: Record<string, unknown> = {
    template_id: adHoc.value.template_id,
    planned_date: adHoc.value.planned_date,
    items: [{ device_id: device.value.id }]
  }
  if (props.module === 'maintain') body.maintenance_level = adHoc.value.typeValue
  if (props.module === 'inspect') body.inspection_type = adHoc.value.typeValue
  if (props.module === 'pm') body.pm_type = adHoc.value.typeValue
  await http.post(`/${props.module}/execution/ad-hoc`, body)
  ElMessage.success('已创建执行单')
  adHocVisible.value = false
  if (device.value) await openDetail(device.value)
  crudRef.value?.load()
}

onMounted(() => {
  // CrudPage loads itself
})
</script>
