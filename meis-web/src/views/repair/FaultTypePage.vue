<template>
  <CrudPage :config="config" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '故障库', apiBase: '/repair', table: 'fault_type_dict' }
</script>
