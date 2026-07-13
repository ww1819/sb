<template>
  <div class="shared-device-page">
    <CrudPage ref="crudRef" :config="config" hide-add :can-delete="() => false">
      <template #toolbar-extra>
        <el-button type="primary" @click="openRegister">新增公用设备</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">计费维护</el-button>
        <el-button link type="primary" @click="openLoans(row)">借用记录</el-button>
        <el-button link type="danger" @click="cancelShared(row)">取消公用</el-button>
      </template>
    </CrudPage>

    <AppModal v-model="registerVisible" title="登记公用设备" size="lg">
      <el-form v-if="registerForm" label-width="120px">
        <el-form-item label="选择设备" required>
          <RefSelect v-model="registerForm.device_id" link-table="shared_device_candidate" placeholder="从非公用设备中选择" />
        </el-form-item>
        <el-form-item label="计费方式" required>
          <el-select v-model="registerForm.shared_fee_mode" style="width: 100%">
            <el-option v-for="o in feeModeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="registerForm.shared_fee_mode === 'time'" label="计时单位">
          <el-select v-model="registerForm.shared_fee_time_unit" style="width: 100%">
            <el-option v-for="o in feeUnitOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价(元)" required>
          <el-input-number v-model="registerForm.shared_fee_unit_price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRegister">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="editVisible" title="公用设备计费维护" size="lg">
      <GroupedFormFields v-if="record" table="shared_device" :model="record" :fields="editFields" />
      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </AppModal>

    <AppModal v-model="loansVisible" :title="loansTitle" size="xl">
      <el-table :data="loanRows" border stripe size="small">
        <el-table-column prop="loan_no" label="借调单号" min-width="140" />
        <el-table-column prop="to_dept_name" label="借入科室" min-width="120" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <TableCellValue :field="{ prop: 'status', dictType: 'loan_status' }" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="fee_unit_price" label="单价" width="100" />
        <el-table-column prop="loan_start" label="计划开始" width="120" />
        <el-table-column prop="loan_end" label="计划结束" width="120" />
      </el-table>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import RefSelect from '@/components/form/RefSelect.vue'
import TableCellValue from '@/components/table/TableCellValue.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getSchema } from '@/config/pageSchemas'
import { useDict } from '@/composables/useDict'

const config = getPageConfig('/shared/device')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const { loadDict } = useDict()

const registerVisible = ref(false)
const editVisible = ref(false)
const loansVisible = ref(false)
const registerForm = ref<Record<string, unknown> | null>(null)
const record = ref<Record<string, unknown> | null>(null)
const loanRows = ref<Record<string, unknown>[]>([])
const loansTitle = ref('借用记录')

const feeModeOptions = ref<{ label: string; value: string }[]>([])
const feeUnitOptions = ref<{ label: string; value: string }[]>([])

const editFields = getSchema('shared_device').filter((f) =>
  ['shared_fee_mode', 'shared_fee_time_unit', 'shared_fee_unit_price', 'location_detail'].includes(f.prop)
)

function openRegister() {
  registerForm.value = {
    shared_fee_mode: 'time',
    shared_fee_time_unit: 'day',
    shared_fee_unit_price: 0
  }
  registerVisible.value = true
}

async function submitRegister() {
  if (!registerForm.value?.device_id) {
    ElMessage.warning('请选择设备')
    return
  }
  await http.post('/shared/device/register', registerForm.value)
  registerVisible.value = false
  ElMessage.success('已登记为公用设备')
  crudRef.value?.load()
}

async function openEdit(row: Record<string, unknown>) {
  const { data } = await http.get(`/shared/device/${row.id}`)
  record.value = data.data
  editVisible.value = true
}

async function save() {
  if (!record.value) return
  await http.post('/shared/device', record.value)
  editVisible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

async function openLoans(row: Record<string, unknown>) {
  loansTitle.value = `借用记录 — ${row.device_name ?? ''}`
  const { data } = await http.get(`/shared/device/${row.id}/loans`)
  loanRows.value = data.data ?? []
  loansVisible.value = true
}

async function cancelShared(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认取消该设备的公用资格？', '取消公用设备', { type: 'warning' })
    await http.post(`/shared/device/${row.id}/cancel`)
    ElMessage.success('已取消公用设备')
    crudRef.value?.load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('操作失败')
  }
}

onMounted(async () => {
  feeModeOptions.value = await loadDict('shared_fee_mode')
  feeUnitOptions.value = await loadDict('shared_fee_time_unit')
})
</script>
