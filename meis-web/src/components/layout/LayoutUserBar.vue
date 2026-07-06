<template>
  <div class="layout-user-bar" :class="{ dark }">
    <span v-if="tenantLabel && dark" class="tenant">{{ tenantLabel }}</span>
    <el-dropdown trigger="click" @command="(cmd: string) => emit('command', cmd)">
      <span class="user-trigger">
        <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
        <span v-if="!dark" class="user-name">{{ userName }}</span>
        <el-icon><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="settings">
            <el-icon><Setting /></el-icon>
            偏好设置
          </el-dropdown-item>
          <el-dropdown-item divided command="logout">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, Setting, SwitchButton } from '@element-plus/icons-vue'

defineProps<{
  avatarText: string
  userName: string
  tenantLabel?: string
  dark?: boolean
}>()

const emit = defineEmits<{
  command: [cmd: string]
}>()
</script>

<style scoped>
.layout-user-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.layout-user-bar.dark .tenant {
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.layout-user-bar.dark .user-trigger {
  color: rgba(255, 255, 255, 0.9);
}

.layout-user-bar:not(.dark) .user-trigger {
  color: var(--meis-text-primary);
}

.user-name {
  font-size: var(--meis-font-subtitle);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-avatar {
  background: var(--el-color-primary);
  font-size: 13px;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
