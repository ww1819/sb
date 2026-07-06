<template>
  <div v-if="loading" class="nav-menu-state" :class="[`nav-menu-state--${variant}`]">
    <template v-if="variant === 'top'">
      <el-skeleton animated>
        <template #template>
          <div class="top-sk-row">
            <el-skeleton-item v-for="i in 5" :key="i" variant="text" class="top-sk-item" />
          </div>
        </template>
      </el-skeleton>
    </template>
    <template v-else>
      <div v-for="i in 6" :key="i" class="side-sk-item">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="text" style="width: 72%; height: 14px" />
          </template>
        </el-skeleton>
      </div>
    </template>
  </div>

  <div v-else-if="error" class="nav-menu-state nav-menu-state--error" :class="[`nav-menu-state--${variant}`]">
    <el-icon class="err-icon"><WarningFilled /></el-icon>
    <span class="err-text">{{ error }}</span>
    <el-button size="small" link :type="variant === 'top' ? 'primary' : 'warning'" @click="emit('retry')">
      重试
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { WarningFilled } from '@element-plus/icons-vue'

defineProps<{
  loading?: boolean
  error?: string
  variant: 'top' | 'side'
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<style scoped>
.nav-menu-state--top {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  padding: 0 8px;
}

.top-sk-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.top-sk-item {
  width: 72px;
  height: 16px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.18) !important;
}

.nav-menu-state--side {
  padding: 12px 16px;
}

.side-sk-item {
  margin-bottom: 14px;
}

.side-sk-item :deep(.el-skeleton__item) {
  background: rgba(255, 255, 255, 0.12) !important;
}

.nav-menu-state--error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.nav-menu-state--top.nav-menu-state--error {
  color: rgba(255, 255, 255, 0.88);
}

.nav-menu-state--side.nav-menu-state--error {
  color: rgba(255, 255, 255, 0.75);
  padding: 16px;
  flex-direction: column;
  align-items: flex-start;
}

.err-icon {
  flex-shrink: 0;
}

.err-text {
  flex: 1;
  line-height: 1.5;
}
</style>
