<template>
  <CrudPage :config="config" />
</template>

<script setup lang="ts">
import CrudPage from '@/components/CrudPage.vue'
import { getPageConfig } from '@/config/pageRegistry'

const config = getPageConfig('/purchase/supplier')!
</script>
