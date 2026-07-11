<template>
  <WorkflowCrudPage :config="config" save-url="/analytics/mapping" />
</template>

<script setup lang="ts">
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/analytics/mapping')!
</script>
