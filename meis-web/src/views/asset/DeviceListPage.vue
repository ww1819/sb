<template>
  <div class="device-list-page">
    <CrudPage v-if="!selectedDevice" :config="config" @detail="openDevice" />
    <template v-else>
      <el-button @click="selectedDevice = null">返回列表</el-button>
      <DeviceDetailTabs :device="selectedDevice" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import http from '@/api/http'
import CrudPage from '@/components/CrudPage.vue'
import DeviceDetailTabs from '@/components/DeviceDetailTabs.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '设备台账', apiBase: '/asset', table: 'medical_device' }
const selectedDevice = ref<Record<string, unknown> | null>(null)

async function openDevice(row: Record<string, unknown>) {
  const { data } = await http.get(`/asset/device/${row.id}/detail`)
  selectedDevice.value = data.data
}
</script>

<style scoped>
.device-list-page {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>
