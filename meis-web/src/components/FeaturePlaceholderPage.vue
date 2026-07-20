<template>
  <SystemPageCard :title="title" :subtitle="subtitle">
    <el-empty>
      <template #description>
        <p>{{ description || '功能开发中，菜单入口已开通' }}</p>
        <p v-if="hint" class="hint">{{ hint }}</p>
      </template>
    </el-empty>
  </SystemPageCard>
</template>

<script setup lang="ts">
import SystemPageCard from '@/components/system/SystemPageCard.vue'

defineProps<{
  title: string
  subtitle?: string
  description?: string
  hint?: string
}>()
</script>

<style scoped>
.hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
