<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" detail-mode hide-add @detail="openDetail" />
    <AppModal v-model="visible" title="计量执行详情" size="xl">
      <template v-if="exec">
        <GroupedFormFields :table="config.table" :model="exec" />
        <FormSection title="设备明细" class="items-section">
          <el-table :data="execItems" border size="small">
            <el-table-column prop="device_code" label="设备编码" width="120" />
            <el-table-column prop="device_name" label="设备名称" min-width="140" />
            <el-table-column prop="certificate_no" label="证书编号" width="120" />
            <el-table-column prop="cost" label="费用" width="80" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'completed'" link type="primary" @click="openItem(row)">执行</el-button>
                <el-button v-else link @click="openItem(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </FormSection>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="exec?.status === 'pending'" type="primary" @click="startExec">开始执行</el-button>
      </template>
    </AppModal>
    <AppModal v-model="itemVisible" title="设备计量执行" size="lg">
      <template v-if="currentItem">
        <div class="item-header">{{ currentItem.device_code }} · {{ currentItem.device_name }}</div>
        <el-form label-width="100px" class="cert-form">
          <el-form-item label="证书编号">
            <el-input v-model="certNo" :disabled="currentItem.status === 'completed'" />
          </el-form-item>
          <el-form-item label="证书地址">
            <el-input v-model="certUrl" :disabled="currentItem.status === 'completed'" />
          </el-form-item>
          <el-form-item label="检定费用">
            <el-input-number v-model="cost" :min="0" :precision="2" :disabled="currentItem.status === 'completed'" />
          </el-form-item>
        </el-form>
        <el-table :data="itemResults" border size="small">
          <el-table-column prop="item_name" label="检定项目" min-width="140" />
          <el-table-column prop="item_content" label="检定内容" min-width="160" show-overflow-tooltip />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-select v-model="row.result_status" size="small" :disabled="currentItem.status === 'completed'">
                <el-option label="合格" value="pass" />
                <el-option label="不合格" value="fail" />
                <el-option label="限用" value="limited" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="实测值" width="140">
            <template #default="{ row }">
              <el-input v-model="row.result_value" size="small" :disabled="currentItem.status === 'completed'" />
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="itemVisible = false">关闭</el-button>
        <el-button v-if="currentItem?.status !== 'completed'" type="primary" @click="completeItem">完成</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FormSection from '@/components/form/FormSection.vue'
import AppModal from '@/components/AppModal.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '计量执行',
  apiBase: '/metrology',
  table: 'metrology_execution',
  listPageUrl: '/metrology/execution/page'
}
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const exec = ref<Record<string, unknown> | null>(null)
const execItems = ref<Record<string, unknown>[]>([])
const itemVisible = ref(false)
const currentItem = ref<Record<string, unknown> | null>(null)
const itemResults = ref<Record<string, unknown>[]>([])
const certNo = ref('')
const certUrl = ref('')
const cost = ref<number | null>(null)

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/metrology/execution/${row.id}`)
  exec.value = data.data ?? { ...row }
  execItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = true
}

function openItem(row: Record<string, unknown>) {
  currentItem.value = row
  itemResults.value = (row.results as Record<string, unknown>[]) ?? []
  certNo.value = String(row.certificate_no ?? '')
  certUrl.value = String(row.certificate_url ?? '')
  cost.value = row.cost != null ? Number(row.cost) : null
  itemVisible.value = true
}

async function startExec() {
  if (!exec.value?.id) return
  await http.post(`/metrology/execution/${exec.value.id}/start`, {})
  ElMessage.success('已开始执行')
  await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}

async function completeItem() {
  if (!currentItem.value?.id) return
  const hasFail = itemResults.value.some(r => r.result_status === 'fail')
  await http.post(`/metrology/execution/item/${currentItem.value.id}/complete`, {
    results: itemResults.value,
    overall_result: hasFail ? 'fail' : 'pass',
    certificate_no: certNo.value,
    certificate_url: certUrl.value,
    cost: cost.value
  })
  ElMessage.success('设备计量已完成')
  itemVisible.value = false
  if (exec.value?.id) await openDetail({ id: exec.value.id })
  crudRef.value?.load()
}
</script>

<style scoped>
.items-section { margin-top: 16px; }
.item-header { margin-bottom: 12px; font-weight: 600; }
.cert-form { margin-bottom: 12px; }
</style>
