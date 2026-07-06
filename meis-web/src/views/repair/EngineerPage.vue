<template>
  <CrudPage :config="config" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '工程师', apiBase: '/repair', table: 'engineer' }
</script>
