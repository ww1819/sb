<template>
  <div class="inventory-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      detail-mode
      hide-add
      delete-url="/asset/inventory"
      :can-edit="canModify"
      :can-delete="canModify"
      @detail="openDetail"
      @add="createNew"
      @deleted="onDeleted"
    >
      <template #toolbar-extra>
        <el-button type="primary" @click="createNew">新增</el-button>
        <el-button v-if="master?.id && canModify(master)" type="warning" @click="saveMaster">保存</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button
          v-if="canModify(row) && row.id"
          link
          type="success"
          @click="approveRow(row)"
        >
          审核
        </el-button>
        <el-button
          v-if="row.id && isApproved(row) && row.status === 'planning'"
          link
          type="primary"
          @click="startRow(row)"
        >
          开始盘点
        </el-button>
        <el-button
          v-if="row.id && row.status === 'in_progress'"
          link
          type="warning"
          @click="completeRow(row)"
        >
          完成盘点
        </el-button>
      </template>
    </CrudPage>

    <AppModal v-model="visible" :title="modalTitle" size="xl">
      <template v-if="master">
        <GroupedFormFields :table="config.table" :model="master" :fields="formFields" />
        <el-card header="盘点明细" class="detail-card">
          <el-table :data="items" border max-height="360">
            <el-table-column
              v-for="f in itemFields"
              :key="f.prop"
              :prop="f.prop"
              :label="f.label"
              :min-width="f.width ?? 120"
            >
              <template #default="{ row }">
                <FieldRenderer
                  v-if="editable && !f.readonly && (f.linkTable || f.dictType || f.type === 'boolean')"
                  v-model="row[f.prop]"
                  :field="f"
                />
                <el-input
                  v-else-if="editable && !f.readonly && f.type === 'textarea'"
                  v-model="row[f.prop]"
                  type="textarea"
                  :rows="2"
                />
                <span v-else>{{ row[f.prop] ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="editable" label="操作" width="80" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" @click="items.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button v-if="editable" class="add-btn" type="primary" plain @click="pickerVisible = true">
            从台账选择设备
          </el-button>
        </el-card>
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="editable" type="primary" @click="saveMaster">保存</el-button>
      </template>
    </AppModal>

    <DeviceLedgerPicker
      v-model="pickerVisible"
      :dept-id="deptId"
      :campus-id="campusId"
      :check-id="checkId"
      :exclude-ids="excludeDeviceIds"
      @confirm="onDevicesPicked"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import AppModal from '@/components/AppModal.vue'
import GroupedFormFields from '@/components/form/GroupedFormFields.vue'
import FieldRenderer from '@/components/FieldRenderer.vue'
import DeviceLedgerPicker from '@/components/asset/DeviceLedgerPicker.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { getDetailFields, getSchema } from '@/config/pageSchemas'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const visible = ref(false)
const pickerVisible = ref(false)
const master = ref<Record<string, unknown> | null>(null)
const items = ref<Record<string, unknown>[]>([])
const itemFields = getDetailFields('inventory_check_item')
const formFields = computed(() => {
  const fields = getSchema('inventory_check')
  if (editable.value) return fields
  return fields.map((f) => ({ ...f, readonly: true }))
})

const modalTitle = computed(() => {
  if (!master.value?.id) return '资产盘点 新增'
  return isApproved(master.value) ? '资产盘点 查看' : '资产盘点 编辑'
})
const editable = computed(() => master.value != null && canModify(master.value))
const deptId = computed(() => (master.value?.dept_id ? String(master.value.dept_id) : ''))
const campusId = computed(() => (master.value?.campus_id ? String(master.value.campus_id) : ''))
const checkId = computed(() => (master.value?.id ? String(master.value.id) : ''))
const excludeDeviceIds = computed(() =>
  items.value.map((item) => String(item.device_id ?? '')).filter((id) => id && id !== 'undefined')
)

function isApproved(row: Record<string, unknown>) {
  return row.audit_status === 'approved'
}

function canModify(row: Record<string, unknown>) {
  return !isApproved(row)
}

function createNew() {
  master.value = {
    check_type: 'annual',
    status: 'planning',
    audit_status: 'pending',
    check_year: new Date().getFullYear()
  }
  items.value = []
  visible.value = true
}

async function openDetail(row: Record<string, unknown>) {
  const { data } = await http.get(`/asset/inventory/${row.id}`)
  master.value = data.data
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = true
}

function onDeleted(row: Record<string, unknown>) {
  if (master.value?.id && String(master.value.id) === String(row.id)) {
    visible.value = false
    master.value = null
    items.value = []
  }
}

function onDevicesPicked(devices: Record<string, unknown>[]) {
  const existing = new Set(excludeDeviceIds.value)
  for (const device of devices) {
    const id = String(device.id ?? '')
    if (!id || existing.has(id)) continue
    existing.add(id)
    items.value.push({
      device_id: id,
      device_code: device.device_code,
      device_name: device.device_name,
      expected_location: device.location_detail ?? '',
      actual_location: '',
      is_found: false,
      is_matched: false,
      condition_status: '',
      remark: ''
    })
  }
}

async function saveMaster() {
  if (!master.value || !editable.value) return
  if (!master.value.dept_id) {
    ElMessage.warning('请先选择科室')
    return
  }
  const { data } = await http.post('/asset/inventory', { ...master.value, items: items.value })
  master.value = data.data
  items.value = (data.data?.items as Record<string, unknown>[]) ?? []
  visible.value = false
  ElMessage.success('保存成功')
  crudRef.value?.load()
}

async function approveRow(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认审核该盘点单？审核后不可再修改或删除。', '审核', { type: 'warning' })
    await http.post(`/asset/inventory/${row.id}/approve`)
    ElMessage.success('审核成功')
    crudRef.value?.load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('审核失败')
    }
  }
}

async function startRow(row: Record<string, unknown>) {
  await http.post(`/asset/inventory/${row.id}/start`)
  ElMessage.success('盘点已开始')
  crudRef.value?.load()
}

async function completeRow(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm('确认完成该盘点任务？', '完成盘点', { type: 'warning' })
    await http.post(`/asset/inventory/${row.id}/complete`)
    ElMessage.success('盘点已完成')
    crudRef.value?.load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('操作失败')
    }
  }
}
</script>

<style scoped>
.detail-card {
  margin-top: 16px;
}
.add-btn {
  margin-top: 12px;
}
</style>
