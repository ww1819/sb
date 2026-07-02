<template>
  <MasterDetailPage :config="config" save-url="/purchase/contract" business-type="purchase_contract" />
</template>

<script setup lang="ts">
import MasterDetailPage from '@/components/MasterDetailPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '采购合同',
  apiBase: '/purchase',
  table: 'purchase_contract'
}
</script>
