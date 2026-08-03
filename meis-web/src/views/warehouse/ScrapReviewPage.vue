<template>
  <WorkflowCrudPage
    :config="config"
    save-url="/asset/scrap"
    business-type="device_scrap"
    hide-add
    force-view-mode
    :can-edit="() => false"
    :can-delete="() => false"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowCrudPage from '@/components/WorkflowCrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = computed(() => getPageConfig('/warehouse/scrap-review')!)
</script>
