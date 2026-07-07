<template>
  <div class="workflow-crud">
    <CrudPage ref="crudRef" :config="config" @detail="openDetail">
      <template #toolbar-extra>
        <el-button v-if="acceptance?.id" type="primary" @click="submitApproval">提交验收审批</el-button>
        <el-button
          v-if="acceptance?.id && acceptance.approval_status === 'approved' && !acceptance.entry_id"
          type="success"
          @click="passAcceptance"
        >
          验收通过并生成入库单
        </el-button>
        <el-button v-if="acceptance?.id" @click="printDoc">打印验收单</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" title="安装验收 详情" size="xl">
      <template v-if="acceptance">
        <el-tabs>
          <el-tab-pane label="验收信息">
            <GroupedFormFields :table="config.table" :model="acceptance" />
          </el-tab-pane>
          <el-tab-pane label="验收清单">
            <el-table :data="checkItems" border size="small" max-height="320">
              <el-table-column prop="item_name" label="检查项目" min-width="140" />
              <el-table-column prop="check_standard" label="验收标准" min-width="180" />
              <el-table-column label="结果" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.check_result" size="small">
                    <el-option label="待检" value="pending" />
                    <el-option label="合格" value="passed" />
                    <el-option label="不合格" value="failed" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="通过" width="70">
                <template #default="{ row }">
                  <el-switch v-model="row.is_passed" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.remark" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="验收小组">
            <el-table :data="members" border size="small" max-height="280">
              <el-table-column label="角色" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.member_role" size="small">
                    <el-option label="质控" value="quality" />
                    <el-option label="工程" value="engineering" />
                    <el-option label="临床" value="clinical" />
                    <el-option label="设备科" value="equipment" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="姓名" width="140">
                <template #default="{ row }">
                  <el-input v-model="row.member_name" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="签字人" min-width="160">
                <template #default="{ row }">
                  <FieldRenderer v-model="row.user_id" :field="{ prop: 'user_id', label: '签字人', linkTable: 'sys_user' }" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.remark" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <ApprovalPanel
          v-if="acceptance.id"
          business-type="purchase_acceptance"
          :business-id="String(acceptance.id)"
          @changed="reload"
        />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
        <el-button type="warning" @click="submitApproval">提交验收审批</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printAcceptanceDoc } from '@/utils/printDoc'

const auth = useAuthStore()
const config = getPageConfig('/purchase/acceptance')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const acceptance = ref<Record<string, unknown> | null>(null)
const checkItems = ref<Record<string, unknown>[]>([])
const members = ref<Record<string, unknown>[]>([])

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/purchase/acceptance/${row.id}`)
  acceptance.value = data.data
  checkItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  members.value = (data.data?.members as Record<string, unknown>[]) ?? []
  visible.value = true
}

async function save() {
  if (!acceptance.value) return
  await http.post('/purchase/acceptance', {
    ...acceptance.value,
    items: checkItems.value,
    members: members.value
  })
  ElMessage.success('已保存')
  visible.value = false
  crudRef.value?.load()
}

async function submitApproval() {
  if (!acceptance.value?.id) return
  await http.post('/purchase/acceptance', {
    ...acceptance.value,
    items: checkItems.value,
    members: members.value
  })
  try {
    await http.post(`/purchase/acceptance/${acceptance.value.id}/submit`, { applicantId: auth.user?.userId })
    ElMessage.success('已提交验收审批')
    await reload()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || '提交失败')
  }
}

async function passAcceptance() {
  if (!acceptance.value?.id) return
  const { data } = await http.post(`/purchase/acceptance/${acceptance.value.id}/pass`)
  ElMessage.success(`已生成入库单 ${data.data?.entry_no ?? ''}`)
  acceptance.value = data.data
  crudRef.value?.load()
}

async function reload() {
  if (!acceptance.value?.id) return
  const { data } = await http.get(`/purchase/acceptance/${acceptance.value.id}`)
  acceptance.value = data.data
  checkItems.value = (data.data?.items as Record<string, unknown>[]) ?? []
  members.value = (data.data?.members as Record<string, unknown>[]) ?? []
  crudRef.value?.load()
}

function printDoc() {
  if (!acceptance.value) return
  printAcceptanceDoc({
    ...acceptance.value,
    items: checkItems.value,
    members: members.value
  })
}
</script>
