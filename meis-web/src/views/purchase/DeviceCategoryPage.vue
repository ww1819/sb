<template>
  <CrudPage :config="config" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '设备分类', apiBase: '/system', table: 'medical_device_category' }
</script>
