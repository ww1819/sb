<template>
  <WorkflowCrudPage :config="config" save-url="/maintain/record" />
</template>

<script setup lang="ts">
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '保养记录', apiBase: '/maintain', table: 'maintenance_record' }
</script>
