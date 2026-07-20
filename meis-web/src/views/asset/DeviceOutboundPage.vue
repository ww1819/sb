<template>
  <MasterDetailPage :config="config" save-url="/asset/outbound">
    <template #actions-after="{ reload, getSelectedRows }">
      <el-button type="success" @click="reviewSelected(getSelectedRows, reload)">审核</el-button>
    </template>
    <template #row-actions="{ row }">
      <el-button link type="primary" @click.stop="printOutbound(row)">打印</el-button>
    </template>
    <template #detail-header-actions="{ master, reload }">
      <el-button
        v-if="master?.id && canIssue(master)"
        type="success"
        @click="issue(master, reload)"
      >
        确认发放
      </el-button>
    </template>
  </MasterDetailPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import { getPageConfig } from '@/config/pageRegistry'
import { printOutboundDoc } from '@/utils/printDoc'

const route = useRoute()
const path = computed(() => '/' + String(route.params.module) + '/' + String(route.params.page))
const config = computed(() => getPageConfig(path.value)!)

function canIssue(row: Record<string, unknown>) {
  const approval = String(row.approval_status ?? '')
  const status = String(row.status ?? '')
  return approval !== 'approved' && status !== 'issued'
}

async function issue(form: Record<string, unknown>, reload?: () => void) {
  const { data } = await http.post(`/asset/outbound/${form.id}/issue`)
  if (data?.code != null && data.code !== 0) {
    ElMessage.error(data.message || '发放失败')
    return
  }
  ElMessage.success('发放成功')
  reload?.()
}

async function printOutbound(row: Record<string, unknown>) {
  if (!row?.id) return
  try {
    const { data } = await http.get(`/asset/outbound/${row.id}`)
    if (data?.code != null && data.code !== 0) {
      ElMessage.error(data.message || '加载出库单失败')
      return
    }
    const doc = { ...(data.data ?? {}), ...row } as Record<string, unknown>
    if (!doc.warehouse_name && row.warehouse_name) doc.warehouse_name = row.warehouse_name
    if (!doc.dept_name && row.dept_name) doc.dept_name = row.dept_name
    let hospital = '医疗机构'
    try {
      const campus = await http.get('/system/campuses', { params: { limit: 1 } })
      const rows = campus.data?.data?.records ?? campus.data?.data ?? []
      if (rows[0]?.campus_name) hospital = String(rows[0].campus_name)
    } catch {
      // ignore
    }
    const ok = printOutboundDoc(doc, hospital)
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
    ElMessage.warning('请先勾选要审核的出库单')
    return
  }
  const pending = rows.filter(
    (r) => String(r.status ?? '') !== 'issued' && String(r.approval_status ?? '') !== 'approved'
  )
  if (!pending.length) {
    ElMessage.warning('所选出库单均已审核')
    return
  }
  const noWarehouse = pending.filter((r) => r.warehouse_id == null || String(r.warehouse_id).trim() === '')
  if (noWarehouse.length) {
    ElMessage.warning('请先为出库单选择仓库后再审核')
    return
  }
  await ElMessageBox.confirm(
    `确认审核选中的 ${pending.length} 张出库单？审核通过后将按明细回写设备台账为在用。`,
    '审核'
  )
  let ok = 0
  for (const row of pending) {
    const { data } = await http.post(`/asset/outbound/${row.id}/issue`)
    if (data?.code != null && data.code !== 0) {
      ElMessage.error(data.message || `审核失败：${row.outbound_no ?? row.id}`)
      break
    }
    ok++
  }
  if (ok > 0) {
    ElMessage.success(`已审核 ${ok} 张出库单`)
    reload?.()
  }
}
</script>
