<template>
  <WorkflowCrudPage :config="config" save-url="/asset/transfer" business-type="asset_transfer" />
</template>

<script setup lang="ts">
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '资产流转', apiBase: '/asset', table: 'asset_transfer' }
</script>
