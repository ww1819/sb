<template>
  <MasterDetailPage
    :config="config"
    save-url="/purchase/plan"
    business-type="purchase_plan"
  />
</template>

<script setup lang="ts">
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '采购计划',
  apiBase: '/purchase',
  table: 'purchase_plan',
  masterDetail: true,
  detailTable: 'purchase_plan_item',
  foreignKey: 'plan_id'
}
</script>
