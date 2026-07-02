<template>
  <CrudPage :config="config" />
  <el-card v-if="lastId" class="approval-card">
    <ApprovalPanel business-type="asset_transfer" :business-id="lastId" />
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CrudPage from '@/components/CrudPage.vue'
import ApprovalPanel from '@/components/ApprovalPanel.vue'
import type { PageConfig } from '@/config/pageRegistry'

const config: PageConfig = { title: '资产流转', apiBase: '/asset', table: 'asset_transfer' }
const lastId = ref('')
</script>

<style scoped>
.approval-card { margin-top: 16px; }
</style>
