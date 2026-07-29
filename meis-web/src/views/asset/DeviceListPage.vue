<template>
  <div class="device-list-page">
    <CrudPage
      ref="crudRef"
      :config="config"
      enable-view
      delete-url="/asset/device"
      :can-delete="(row) => row.can_delete !== false"
      @selection-change="onSelectionCount"
    >
      <template #form="{ form, fields, mode }">
        <DeviceLedgerForm :model="form" :fields="fields" :mode="mode" />
      </template>
      <template #actions-after>
        <el-button :loading="selectingAll" @click="onSelectAllQuery">全选</el-button>
        <el-button @click="onClearSelection">取消全选</el-button>
        <el-button @click="openBatch">
          批量修改<span v-if="selectedCount">（{{ selectedCount }}）</span>
        </el-button>
        <el-button @click="onBatchPrint">打印</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="onPrint(row)">打印</el-button>
      </template>
    </CrudPage>

    <el-dialog v-model="batchVisible" title="批量修改设备" width="560px" destroy-on-close>
      <p class="batch-hint">
        仅更新下方<strong>勾选</strong>的字段；未勾选保持不变。作用域：{{ scopeHint }}。
      </p>
      <div class="batch-fields">
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setDept">修改科室</el-checkbox>
          <el-select
            v-model="batchForm.dept_id"
            :disabled="!batchForm.setDept"
            filterable
            clearable
            placeholder="选择科室（可清空）"
            class="batch-field-control"
          >
            <el-option v-for="d in depts" :key="d.id" :label="d.dept_name" :value="d.id" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setManageDept">修改管理科室</el-checkbox>
          <el-select
            v-model="batchForm.manage_dept_id"
            :disabled="!batchForm.setManageDept"
            filterable
            clearable
            placeholder="选择管理科室（可清空）"
            class="batch-field-control"
          >
            <el-option v-for="d in depts" :key="d.id" :label="d.dept_name" :value="d.id" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setStatus">修改设备状态</el-checkbox>
          <el-select
            v-model="batchForm.device_status"
            :disabled="!batchForm.setStatus"
            clearable
            placeholder="设备状态"
            class="batch-field-control"
          >
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setRisk">修改风险等级</el-checkbox>
          <el-select
            v-model="batchForm.risk_level"
            :disabled="!batchForm.setRisk"
            clearable
            placeholder="风险等级"
            class="batch-field-control"
          >
            <el-option v-for="o in riskOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setCampus">修改院区</el-checkbox>
          <el-select
            v-model="batchForm.campus_id"
            :disabled="!batchForm.setCampus"
            filterable
            clearable
            placeholder="院区"
            class="batch-field-control"
          >
            <el-option v-for="c in campuses" :key="c.id" :label="c.campus_name" :value="c.id" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setBuilding">修改楼宇</el-checkbox>
          <el-select
            v-model="batchForm.building_id"
            :disabled="!batchForm.setBuilding"
            filterable
            clearable
            placeholder="楼宇"
            class="batch-field-control"
          >
            <el-option v-for="b in buildings" :key="b.id" :label="b.building_name" :value="b.id" />
          </el-select>
        </div>
        <div class="batch-field">
          <el-checkbox v-model="batchForm.setWarehouse">修改仓库</el-checkbox>
          <el-select
            v-model="batchForm.warehouse_id"
            :disabled="!batchForm.setWarehouse"
            filterable
            clearable
            placeholder="仓库（可清空）"
            class="batch-field-control"
          >
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouse_name" :value="w.id" />
          </el-select>
        </div>
        <div v-for="flag in boolFlags" :key="flag.key" class="batch-field">
          <el-checkbox v-model="batchForm.boolSet[flag.key]">修改{{ flag.label }}</el-checkbox>
          <el-switch
            v-model="batchForm.boolVal[flag.key]"
            :disabled="!batchForm.boolSet[flag.key]"
            active-text="是"
            inactive-text="否"
            class="batch-field-control"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBatch" @click="saveBatch">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { useDict } from '@/composables/useDict'
import { promptListActionScope, assertScopeSelection, type ListActionScope } from '@/composables/useListActionScope'
import { printAssetLabelFromRow, printAssetLabelsBatch } from '@/utils/printAssetLabel'

const config = getPageConfig('/asset/device')!
const crudRef = ref<InstanceType<typeof CrudPage> | null>(null)
const { loadDict } = useDict()

const selectingAll = ref(false)
const batchVisible = ref(false)
const savingBatch = ref(false)
const batchScope = ref<ListActionScope>('selected')
const selectedCount = ref(0)

function onSelectionCount(count: number) {
  selectedCount.value = count
}

const depts = ref<{ id: string; dept_name: string }[]>([])
const campuses = ref<{ id: string; campus_name: string }[]>([])
const buildings = ref<{ id: string; building_name: string }[]>([])
const warehouses = ref<{ id: string; warehouse_name: string }[]>([])
const statusOptions = ref<{ label: string; value: string }[]>([])
const riskOptions = ref<{ label: string; value: string }[]>([])

const boolFlags = [
  { key: 'is_life_support', label: '生命支持' },
  { key: 'is_emergency', label: '应急设备' },
  { key: 'is_metrology', label: '计量设备' },
  { key: 'is_shared_device', label: '公用设备' },
  { key: 'is_maintain_device', label: '保养设备' },
  { key: 'is_inspection_device', label: '巡检设备' },
  { key: 'is_pm_device', label: '预防性维护设备' }
] as const

const batchForm = reactive({
  setDept: false,
  dept_id: '' as string,
  setManageDept: false,
  manage_dept_id: '' as string,
  setStatus: false,
  device_status: '' as string,
  setRisk: false,
  risk_level: '' as string,
  setCampus: false,
  campus_id: '' as string,
  setBuilding: false,
  building_id: '' as string,
  setWarehouse: false,
  warehouse_id: '' as string,
  boolSet: Object.fromEntries(boolFlags.map((f) => [f.key, false])) as Record<string, boolean>,
  boolVal: Object.fromEntries(boolFlags.map((f) => [f.key, false])) as Record<string, boolean>
})

const scopeHint = computed(() =>
  batchScope.value === 'all'
    ? '当前查询全部结果'
    : `已勾选 ${selectedCount.value} 条`
)

onMounted(async () => {
  const [d, c, b, w, st, rk] = await Promise.all([
    http.get('/system/departments'),
    http.get('/system/campuses'),
    http.get('/system/building/list'),
    http.get('/system/warehouses'),
    loadDict('device_status'),
    loadDict('risk_level')
  ])
  if (d.data.code === 0 || d.data.code === 200) depts.value = d.data.data ?? []
  campuses.value = (Array.isArray(c.data.data) ? c.data.data : []).map((r: Record<string, unknown>) => ({
    id: String(r.id),
    campus_name: String(r.campus_name ?? '')
  }))
  buildings.value = (Array.isArray(b.data.data) ? b.data.data : b.data.data?.records ?? []).map(
    (r: Record<string, unknown>) => ({
      id: String(r.id),
      building_name: String(r.building_name ?? '')
    })
  )
  warehouses.value = (Array.isArray(w.data.data) ? w.data.data : w.data.data?.records ?? []).map(
    (r: Record<string, unknown>) => ({
      id: String(r.id),
      warehouse_name: String(r.warehouse_name ?? '')
    })
  )
  statusOptions.value = st.filter((o) => o.value !== 'returned')
  riskOptions.value = rk
})

async function onSelectAllQuery() {
  selectingAll.value = true
  try {
    const filters = crudRef.value?.getFilterQueryParams() ?? {}
    const { data } = await http.get('/asset/device/ids', { params: filters })
    if (data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.message || '全选失败')
      return
    }
    const ids = (data.data ?? []) as string[]
    if (!ids.length) {
      ElMessage.warning('当前查询无结果')
      return
    }
    await crudRef.value?.selectAllByIds(ids)
    ElMessage.success(`已勾选当前查询全部 ${ids.length} 条`)
  } catch (e: unknown) {
    const msg =
      (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '全选失败'
    ElMessage.error(msg)
  } finally {
    selectingAll.value = false
  }
}

function onClearSelection() {
  crudRef.value?.clearSelection()
}

async function onPrint(row: Record<string, unknown>) {
  try {
    await printAssetLabelFromRow(row)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '打印失败'
    ElMessage.error(msg)
  }
}

async function onBatchPrint() {
  const rows = crudRef.value?.getSelectedRows() ?? []
  if (!rows.length) {
    ElMessage.warning('请先勾选要打印的设备')
    return
  }
  try {
    await printAssetLabelsBatch(rows)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '打印失败'
    ElMessage.error(msg)
  }
}

function resetBatchForm() {
  batchForm.setDept = false
  batchForm.dept_id = ''
  batchForm.setManageDept = false
  batchForm.manage_dept_id = ''
  batchForm.setStatus = false
  batchForm.device_status = ''
  batchForm.setRisk = false
  batchForm.risk_level = ''
  batchForm.setCampus = false
  batchForm.campus_id = ''
  batchForm.setBuilding = false
  batchForm.building_id = ''
  batchForm.setWarehouse = false
  batchForm.warehouse_id = ''
  for (const f of boolFlags) {
    batchForm.boolSet[f.key] = false
    batchForm.boolVal[f.key] = false
  }
}

async function openBatch() {
  const count = selectedCount.value
  const scope = await promptListActionScope(count, '批量修改')
  if (!scope || !assertScopeSelection(scope, count)) return
  batchScope.value = scope
  resetBatchForm()
  batchVisible.value = true
}

async function saveBatch() {
  const fields: Record<string, unknown> = {}
  if (batchForm.setDept) fields.dept_id = batchForm.dept_id || null
  if (batchForm.setManageDept) fields.manage_dept_id = batchForm.manage_dept_id || null
  if (batchForm.setStatus) fields.device_status = batchForm.device_status || null
  if (batchForm.setRisk) fields.risk_level = batchForm.risk_level || null
  if (batchForm.setCampus) fields.campus_id = batchForm.campus_id || null
  if (batchForm.setBuilding) fields.building_id = batchForm.building_id || null
  if (batchForm.setWarehouse) fields.warehouse_id = batchForm.warehouse_id || null
  for (const f of boolFlags) {
    if (batchForm.boolSet[f.key]) fields[f.key] = batchForm.boolVal[f.key]
  }
  if (!Object.keys(fields).length) {
    ElMessage.warning('请至少勾选一项要修改的字段')
    return
  }
  await ElMessageBox.confirm(`将对${scopeHint.value}批量修改已勾选字段，是否继续？`, '批量修改', {
    type: 'warning'
  })
  savingBatch.value = true
  try {
    const payload: Record<string, unknown> = { fields }
    if (batchScope.value === 'all') {
      payload.all = true
      Object.assign(payload, crudRef.value?.getFilterQueryParams() ?? {})
    } else {
      payload.ids = crudRef.value?.selectedIds() ?? []
    }
    const { data } = await http.post('/asset/device/batch-update', payload)
    if (data.code !== 0 && data.code !== 200) {
      ElMessage.error(data.message || '批量修改失败')
      return
    }
    ElMessage.success(`已更新 ${data.data?.updated ?? 0} 条`)
    batchVisible.value = false
    crudRef.value?.clearSelection()
    await crudRef.value?.load()
  } catch (e: unknown) {
    const msg =
      (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '批量修改失败'
    ElMessage.error(msg)
  } finally {
    savingBatch.value = false
  }
}
</script>

<style scoped>
.device-list-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.batch-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.batch-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.batch-field {
  display: grid;
  grid-template-columns: 160px 1fr;
  align-items: center;
  gap: 8px;
}

.batch-field-control {
  width: 100%;
}
</style>
