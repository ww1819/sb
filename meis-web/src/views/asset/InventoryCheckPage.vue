<template>
  <MasterDetailPage :config="config" save-url="/asset/inventory" />
</template>

<script setup lang="ts">
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '资产盘点',
  apiBase: '/asset',
  table: 'inventory_check',
  masterDetail: true,
  detailTable: 'inventory_check_item',
  foreignKey: 'check_id'
}
</script>
