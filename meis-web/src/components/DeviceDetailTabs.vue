<template>
  <el-tabs v-model="active">
    <el-tab-pane v-for="g in displayGroups" :key="g.key" :label="g.title" :name="g.key">
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="f in g.fields" :key="f.prop" :label="f.label">
          <TableCellValue v-if="labelsReady" :field="fieldFor(f.prop)" :value="device[f.prop]" />
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-tab-pane>
    <el-tab-pane label="维修记录" name="repair">
      <el-table :data="(device.repairs as unknown[]) ?? []" border>
        <el-table-column prop="wo_no" label="工单号" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="report_time" label="报修时间" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="保养记录" name="maintain">
      <el-table :data="(device.maintenance as unknown[]) ?? []" border>
        <el-table-column prop="record_no" label="记录号" />
        <el-table-column prop="status" label="状态" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="流转记录" name="transfer">
      <el-table :data="(device.transfers as unknown[]) ?? []" border>
        <el-table-column prop="transfer_type" label="类型" />
        <el-table-column prop="status" label="状态" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="质控记录" name="qc">
      <el-table :data="(device.qc as unknown[]) ?? []" border>
        <el-table-column prop="type" label="类型" />
        <el-table-column prop="created_at" label="时间" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="效益分析" name="benefit">
      <el-table :data="(device.benefit as unknown[]) ?? []" border>
        <el-table-column prop="summary_year" label="年" />
        <el-table-column prop="net_profit" label="净利润" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="附件文档" name="files"><el-empty description="对接文件服务" /></el-tab-pane>
    <el-tab-pane label="操作日志" name="log">
      <el-table :data="(device.logs as unknown[]) ?? []" border>
        <el-table-column prop="operation_desc" label="操作" />
        <el-table-column prop="created_at" label="时间" />
      </el-table>
    </el-tab-pane>
    <el-tab-pane label="二维码" name="qrcode">
      <p>设备编码：{{ device.device_code }}</p>
      <el-button @click="printLabel">打印标签</el-button>
    </el-tab-pane>
    <el-tab-pane label="关联合同" name="contract"><el-empty description="维保合同关联" /></el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { deviceFieldLabels, deviceFieldGroups, groupTitles } from '@/config/fieldLabels'
import { collectLinkTables, fieldSchemaByProp } from '@/config/pageSchemas'
import { preloadRefLabelMaps } from '@/composables/useRefLabelMap'
import TableCellValue from '@/components/table/TableCellValue.vue'

const props = defineProps<{ device: Record<string, unknown> }>()
const active = ref('basic')
const labelsReady = ref(false)
const skipKeys = new Set(['repairs', 'maintenance', 'transfers', 'qc', 'benefit', 'logs', 'id'])

const displayGroups = computed(() => {
  const grouped = new Map<string, { prop: string; label: string }[]>()
  for (const [key, val] of Object.entries(props.device)) {
    if (skipKeys.has(key) || val === null || val === undefined || val === '') continue
    const g = deviceFieldGroups[key] ?? 'other'
    if (!grouped.has(g)) grouped.set(g, [])
    grouped.get(g)!.push({ prop: key, label: deviceFieldLabels[key] ?? key })
  }
  const order = ['basic', 'finance', 'location', 'time', 'status', 'attachment', 'remark', 'other']
  return order
    .filter((g) => grouped.has(g))
    .map((g) => ({ key: g, title: groupTitles[g] ?? g, fields: grouped.get(g)! }))
})

function fieldFor(prop: string) {
  const schema = fieldSchemaByProp('medical_device', prop)
  if (schema.label === prop && deviceFieldLabels[prop]) {
    return { ...schema, label: deviceFieldLabels[prop] }
  }
  return schema
}

function printLabel() {
  window.print()
}

onMounted(async () => {
  await preloadRefLabelMaps(collectLinkTables('medical_device'))
  labelsReady.value = true
})
</script>
