<template>
  <CrudPage :config="config" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = {
  title: '维修进程类型',
  apiBase: '/repair',
  table: 'repair_process_type',
  enableView: true
}
</script>
