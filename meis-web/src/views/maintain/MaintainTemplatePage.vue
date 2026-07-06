<template>
  <WorkflowCrudPage :config="config" save-url="/maintain/template" />
</template>

<script setup lang="ts">
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '保养模板', apiBase: '/maintain', table: 'maintenance_template' }
</script>
