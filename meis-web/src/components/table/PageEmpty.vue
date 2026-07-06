<template>
  <div class="page-empty">
    <el-empty :description="description" :image-size="imageSize">
      <el-button v-if="actionText" type="primary" @click="$emit('action')">{{ actionText }}</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
    actionText?: string
    imageSize?: number
  }>(),
  {
    description: '暂无数据',
    imageSize: 88
  }
)

defineEmits<{
  action: []
}>()
</script>

<style scoped>
.page-empty {
  padding: var(--meis-space-lg) var(--meis-space-md);
}

.page-empty :deep(.el-empty__description) {
  margin-top: 12px;
  font-size: var(--meis-font-subtitle);
  color: var(--meis-text-secondary);
}
</style>
