<template>
  <MasterDetailPage :config="config" save-url="/asset/entry" />
</template>

<script setup lang="ts">
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '设备入库',
  apiBase: '/asset',
  table: 'device_entry',
  masterDetail: true,
  detailTable: 'device_entry_item',
  foreignKey: 'entry_id'
}
</script>
