<template>
  <el-tabs v-model="active">
    <el-tab-pane label="基本信息" name="base">
      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="(v, k) in baseFields" :key="k" :label="k">{{ v }}</el-descriptions-item>
      </el-descriptions>
    </el-tab-pane>
    <el-tab-pane label="维修记录" name="repair">
      <el-table :data="(device.repairs as unknown[]) ?? []" border><el-table-column prop="wo_no" label="工单号" /><el-table-column prop="status" label="状态" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="保养记录" name="maintain">
      <el-table :data="(device.maintenance as unknown[]) ?? []" border><el-table-column prop="record_no" label="记录号" /><el-table-column prop="status" label="状态" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="流转记录" name="transfer">
      <el-table :data="(device.transfers as unknown[]) ?? []" border><el-table-column prop="transfer_type" label="类型" /><el-table-column prop="status" label="状态" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="质控记录" name="qc">
      <el-table :data="(device.qc as unknown[]) ?? []" border><el-table-column prop="type" label="类型" /><el-table-column prop="created_at" label="时间" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="效益分析" name="benefit">
      <el-table :data="(device.benefit as unknown[]) ?? []" border><el-table-column prop="summary_year" label="年" /><el-table-column prop="net_profit" label="净利润" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="附件文档" name="files"><el-empty description="对接文件服务" /></el-tab-pane>
    <el-tab-pane label="操作日志" name="log">
      <el-table :data="(device.logs as unknown[]) ?? []" border><el-table-column prop="operation_desc" label="操作" /><el-table-column prop="created_at" label="时间" /></el-table>
    </el-tab-pane>
    <el-tab-pane label="二维码" name="qrcode">
      <p>设备编码：{{ device.device_code }}</p>
      <el-button @click="printLabel">打印标签</el-button>
    </el-tab-pane>
    <el-tab-pane label="关联合同" name="contract"><el-empty description="维保合同关联" /></el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
const props = defineProps<{ device: Record<string, unknown> }>()
const active = ref('base')
const baseFields = computed(() => {
  const skip = new Set(['repairs','maintenance','transfers','qc','benefit','logs'])
  return Object.fromEntries(Object.entries(props.device).filter(([k]) => !skip.has(k)))
})
function printLabel() { window.print() }
</script>
