<template>
  <!-- PLT-DEV-LIST-01：从属设备必显列（可插在业务列之前） -->
  <el-table-column
    v-if="showCode"
    prop="device_code"
    :label="codeLabel"
    :width="codeWidth"
    :fixed="codeFixed"
    show-overflow-tooltip
  />
  <el-table-column
    v-if="showName"
    prop="device_name"
    :label="nameLabel"
    :min-width="nameMinWidth"
    show-overflow-tooltip
  />
  <el-table-column prop="brand" label="品牌" width="90" show-overflow-tooltip />
  <el-table-column prop="specification" label="规格" width="100" show-overflow-tooltip />
  <el-table-column prop="model" label="型号" width="100" show-overflow-tooltip />
  <el-table-column prop="registration_no" label="医疗器械注册证号" width="140" show-overflow-tooltip />
  <el-table-column prop="production_date" label="生产日期" width="110" />
  <el-table-column prop="serial_number" label="序列号" width="120" show-overflow-tooltip />
  <el-table-column prop="has_power_tag" label="电流标签" width="90">
    <template #default="{ row }">
      {{ row.has_power_tag === true || row.has_power_tag === 'true' || row.has_power_tag === 1 ? '是' : '否' }}
    </template>
  </el-table-column>
  <el-table-column prop="power_tag_code" label="电流监测编码" width="120" show-overflow-tooltip />
  <el-table-column prop="dept_name" label="科室" width="110" show-overflow-tooltip />
  <el-table-column prop="warehouse_name" label="仓库" width="110" show-overflow-tooltip />
  <el-table-column prop="manufacturer_code" label="厂家编码" width="110" show-overflow-tooltip />
  <el-table-column prop="manufacturer_name" label="厂家名称" width="130" show-overflow-tooltip />
  <el-table-column prop="supplier_code" label="供应商编码" width="110" show-overflow-tooltip />
  <el-table-column prop="supplier_name" label="供应商名称" width="130" show-overflow-tooltip />
  <el-table-column prop="device_status" label="设备状态" width="100">
    <template #default="{ row }">
      <TableCellValue :field="{ prop: 'device_status', dictType: 'device_status' }" :value="row.device_status" />
    </template>
  </el-table-column>
</template>

<script setup lang="ts">
import TableCellValue from '@/components/table/TableCellValue.vue'

withDefaults(
  defineProps<{
    showCode?: boolean
    showName?: boolean
    codeLabel?: string
    nameLabel?: string
    codeWidth?: number
    nameMinWidth?: number
    codeFixed?: boolean | 'left' | 'right'
  }>(),
  {
    showCode: true,
    showName: true,
    codeLabel: '资产编码',
    nameLabel: '资产名称',
    codeWidth: 120,
    nameMinWidth: 140,
    codeFixed: false
  }
)
</script>
