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
        科室/管理科室/院区/楼宇/仓库请在台账详情用「改科室」「改位置」（真变更/纠错），不可批量静默改。
      </p>
      <div class="batch-fields">
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
  setStatus: false,
  device_status: '' as string,
  setRisk: false,
  risk_level: '' as string,
  boolSet: Object.fromEntries(boolFlags.map((f) => [f.key, false])) as Record<string, boolean>,
  boolVal: Object.fromEntries(boolFlags.map((f) => [f.key, false])) as Record<string, boolean>
})

const scopeHint = computed(() =>
  batchScope.value === 'all'
    ? '当前查询全部结果'
    : `已勾选 ${selectedCount.value} 条`
)

onMounted(async () => {
  const [st, rk] = await Promise.all([loadDict('device_status'), loadDict('risk_level')])
  statusOptions.value = st.filter((o: { value: string }) => o.value !== 'returned')
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
  batchForm.setStatus = false
  batchForm.device_status = ''
  batchForm.setRisk = false
  batchForm.risk_level = ''
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
  if (batchForm.setStatus) fields.device_status = batchForm.device_status || null
  if (batchForm.setRisk) fields.risk_level = batchForm.risk_level || null
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
