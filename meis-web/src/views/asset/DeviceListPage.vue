<template>
  <div class="device-list-page">
    <CrudPage v-if="!selectedDevice" :config="config" @detail="openDevice">
      <template #form="{ form, fields }">
        <DeviceLedgerForm :model="form" :fields="fields" />
      </template>
    </CrudPage>
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
import DeviceLedgerForm from '@/components/asset/DeviceLedgerForm.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/asset/device')!
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
