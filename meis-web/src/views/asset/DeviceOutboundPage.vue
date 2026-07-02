<template>
  <MasterDetailPage :config="config" save-url="/asset/outbound" business-type="device_outbound" />
</template>

<script setup lang="ts">
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '设备出库',
  apiBase: '/asset',
  table: 'device_outbound',
  masterDetail: true,
  detailTable: 'device_outbound_item',
  foreignKey: 'outbound_id'
}
</script>
