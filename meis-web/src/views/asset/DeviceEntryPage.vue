<template>
  <div class="entry-page">
    <MasterDetailPage :config="config" save-url="/asset/entry">
      <template #actions-after="{ reload, getSelectedRows }">
        <el-button type="success" @click="reviewSelected(getSelectedRows, reload)">审核</el-button>
      </template>
      <template #row-actions="{ row }">
        <el-button link type="primary" @click.stop="printEntry(row)">打印</el-button>
      </template>
      <template #detail-header-actions="{ master, replaceItems }">
        <el-button v-if="master" type="primary" plain @click="openContractRef(master, replaceItems)">
          引用合同
        </el-button>
      </template>
    </MasterDetailPage>

    <AppModal v-model="contractRefVisible" title="引用合同" size="xl">
      <el-table
        v-loading="contractRefLoading"
        :data="contractRefRows"
        border
        size="small"
        max-height="420"
        row-key="id"
        @selection-change="onContractRefSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column type="index" label="序号" width="64" align="center" />
        <el-table-column prop="contract_code" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="device_name" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="specification" label="设备规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatAmount(row.unit_price) }}</template>
        </el-table-column>
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="contractRefVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmContractRef">确认</el-button>
      </template>
    </AppModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import AppModal from '@/components/AppModal.vue'
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import { printEntryDoc } from '@/utils/printDoc'
import { getPageConfig } from '@/config/pageRegistry'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)

const contractRefVisible = ref(false)
const contractRefLoading = ref(false)
const contractRefRows = ref<Record<string, unknown>[]>([])
const contractRefSelected = ref<Record<string, unknown>[]>([])
const contractRefMaster = ref<Record<string, unknown> | null>(null)
const contractRefReplaceItems = ref<((rows: Record<string, unknown>[]) => void) | null>(null)

function formatAmount(v: unknown) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

async function openContractRef(
  master: Record<string, unknown>,
  replaceItems: (rows: Record<string, unknown>[]) => void
) {
  contractRefMaster.value = master
  contractRefReplaceItems.value = replaceItems
  contractRefSelected.value = []
  contractRefVisible.value = true
  contractRefLoading.value = true
  try {
    const { data } = await http.get('/purchase/contract/ref-items', {
      params: { page: 1, size: 500 }
    })
    contractRefRows.value = data.data?.records ?? []
  } catch {
    contractRefRows.value = []
    ElMessage.error('加载合同明细失败')
  } finally {
    contractRefLoading.value = false
  }
}

function onContractRefSelectionChange(rows: Record<string, unknown>[]) {
  contractRefSelected.value = rows
}

function confirmContractRef() {
  const master = contractRefMaster.value
  const replaceItems = contractRefReplaceItems.value
  if (!master || !replaceItems) return
  if (!contractRefSelected.value.length) {
    ElMessage.warning('请至少选择一条合同明细')
    return
  }
  const mapped = contractRefSelected.value.map((row) => {
    const quantity = row.quantity ?? 1
    const unitPrice = row.unit_price ?? null
    let totalPrice: number | null = null
    if (row.amount != null && row.amount !== '') {
      const a = Number(row.amount)
      totalPrice = Number.isFinite(a) ? a : null
    } else {
      const q = Number(quantity)
      const p = Number(unitPrice)
      if (Number.isFinite(q) && Number.isFinite(p)) {
        totalPrice = Math.round(q * p * 100) / 100
      }
    }
    return {
      device_name: String(row.device_name ?? ''),
      specification: String(row.specification ?? ''),
      brand: String(row.brand ?? ''),
      quantity,
      unit_price: unitPrice,
      total_price: totalPrice,
      manufacturer_id: row.manufacturer_id != null ? String(row.manufacturer_id) : '',
      manufacturer_name: String(row.manufacturer_name ?? '')
    }
  })
  const first = contractRefSelected.value[0]
  if (first?.contract_id != null) master.contract_id = first.contract_id
  if (first?.supplier_id != null) master.supplier_id = first.supplier_id
  replaceItems(mapped)
  contractRefVisible.value = false
  ElMessage.success(`已引入 ${mapped.length} 条合同明细`)
}

async function printEntry(row: Record<string, unknown>) {
  if (!row?.id) return
  try {
    const { data } = await http.get(`/asset/entry/${row.id}`)
    if (data?.code != null && data.code !== 0) {
      ElMessage.error(data.message || '加载入库单失败')
      return
    }
    const entry = { ...(data.data ?? {}), ...row } as Record<string, unknown>
    if (!entry.warehouse_name && row.warehouse_name) entry.warehouse_name = row.warehouse_name
    if (!entry.supplier_name && row.supplier_name) entry.supplier_name = row.supplier_name
    let hospital = '医疗机构'
    try {
      const campus = await http.get('/system/campuses', { params: { limit: 1 } })
      const rows = campus.data?.data?.records ?? campus.data?.data ?? []
      if (rows[0]?.campus_name) hospital = String(rows[0].campus_name)
    } catch {
      // ignore
    }
    const ok = printEntryDoc(entry, hospital)
    if (!ok) ElMessage.warning('无法调起打印，请检查浏览器设置')
  } catch {
    ElMessage.error('打印失败')
  }
}

async function reviewSelected(
  getSelectedRows?: () => Record<string, unknown>[],
  reload?: () => void
) {
  const rows = (getSelectedRows?.() ?? []).filter((r) => r?.id)
  if (!rows.length) {
    ElMessage.warning('请先勾选要审核的入库单')
    return
  }
  const pending = rows.filter(
    (r) => String(r.status ?? '') !== 'completed' && String(r.approval_status ?? '') !== 'approved'
  )
  if (!pending.length) {
    ElMessage.warning('所选入库单均已审核')
    return
  }
  const noWarehouse = pending.filter((r) => r.warehouse_id == null || String(r.warehouse_id).trim() === '')
  if (noWarehouse.length) {
    ElMessage.warning('请先为入库单选择仓库后再审核')
    return
  }
  await ElMessageBox.confirm(
    `确认审核选中的 ${pending.length} 张入库单？审核通过后将按明细数量生成设备台账。`,
    '审核'
  )
  let ok = 0
  let deviceCount = 0
  for (const row of pending) {
    const { data } = await http.post(`/asset/entry/${row.id}/complete`, {})
    if (data?.code != null && data.code !== 0) {
      ElMessage.error(data.message || `审核失败：${row.entry_no ?? row.id}`)
      break
    }
    ok += 1
    deviceCount += Number(data.data?.device_count ?? 0)
  }
  if (ok > 0) {
    ElMessage.success(`已审核 ${ok} 张入库单，生成 ${deviceCount} 台设备台账`)
    reload?.()
  }
}
</script>
