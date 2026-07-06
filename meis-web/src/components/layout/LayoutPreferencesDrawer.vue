<template>
  <el-drawer
    v-model="visible"
    title="偏好设置"
    direction="rtl"
    size="360px"
    append-to-body
    class="preferences-drawer"
  >
    <div class="pref-section">
      <div class="pref-label">菜单布局</div>
      <el-radio-group v-model="navMode" class="pref-radio" @change="onNavChange">
        <el-radio-button value="top">顶部菜单</el-radio-button>
        <el-radio-button value="side">左侧菜单</el-radio-button>
      </el-radio-group>
    </div>

    <div class="pref-section">
      <div class="pref-label">主题模式</div>
      <el-radio-group v-model="themeMode" class="pref-radio" @change="onThemeChange">
        <el-radio-button value="light">浅色</el-radio-button>
        <el-radio-button value="dark">深色</el-radio-button>
        <el-radio-button value="system">跟随系统</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="navMode === 'side'" class="pref-section">
      <div class="pref-row">
        <div>
          <div class="pref-label">侧栏默认折叠</div>
          <div class="pref-hint">进入系统时左侧菜单是否收起</div>
        </div>
        <el-switch v-model="sidebarCollapsed" @change="onSidebarChange" />
      </div>
    </div>

    <div class="pref-section">
      <div class="pref-label">首页说明</div>
      <div class="pref-info">
        当前角色首页为 <strong>{{ homeTitle }}</strong>（{{ homePath }}），由账号类型自动决定。
      </div>
    </div>

    <div class="pref-section">
      <div class="pref-label">当前账号</div>
      <div class="pref-info user-info">
        <div>{{ userName }}</div>
        <div v-if="tenantLabel" class="pref-hint">{{ tenantLabel }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useLayoutStore, type NavMode, type ThemeMode } from '@/stores/layout'

defineProps<{
  userName: string
  tenantLabel?: string
  homePath: string
  homeTitle: string
}>()

const visible = defineModel<boolean>({ default: false })
const layoutStore = useLayoutStore()

const navMode = ref<NavMode>(layoutStore.navMode)
const themeMode = ref<ThemeMode>(layoutStore.themeMode)
const sidebarCollapsed = ref(layoutStore.sidebarCollapsed)

watch(visible, (open) => {
  if (!open) return
  navMode.value = layoutStore.navMode
  themeMode.value = layoutStore.themeMode
  sidebarCollapsed.value = layoutStore.sidebarCollapsed
})

function onNavChange(mode: NavMode) {
  layoutStore.setNavMode(mode)
}

function onThemeChange(mode: ThemeMode) {
  layoutStore.setThemeMode(mode)
}

function onSidebarChange(collapsed: boolean) {
  layoutStore.setSidebarCollapsed(collapsed)
}
</script>

<style scoped>
.pref-section {
  margin-bottom: 24px;
}

.pref-label {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--meis-text-primary);
}

.pref-radio {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.pref-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pref-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--meis-text-secondary);
  line-height: 1.5;
}

.pref-info {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--meis-surface-muted);
  border: 1px solid var(--meis-border-light);
  font-size: 13px;
  color: var(--meis-text-secondary);
  line-height: 1.6;
}

.pref-info strong {
  color: var(--meis-text-primary);
}

.user-info {
  color: var(--meis-text-primary);
}
</style>
