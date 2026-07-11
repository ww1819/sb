<template>
  <WorkflowCrudPage :config="config" save-url="/power/station" />
</template>

<script setup lang="ts">
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/power/station')!
</script>
