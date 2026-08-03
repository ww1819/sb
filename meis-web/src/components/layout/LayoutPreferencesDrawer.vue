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

    <div class="pref-section">
      <div class="pref-label">系统颜色</div>
      <div class="pref-hint">主色同时作用于菜单高亮、按钮与分析报表表头</div>
      <div class="brand-presets">
        <button
          v-for="p in presets"
          :key="p.id"
          type="button"
          class="brand-swatch"
          :class="{ active: brandPreset === p.id }"
          :title="p.labelZh"
          :style="{ background: p.hex }"
          @click="onBrandPreset(p.id)"
        >
          <span class="brand-swatch-name">{{ p.labelZh }}</span>
        </button>
      </div>
      <div class="brand-custom-row">
        <span class="pref-hint">自定义</span>
        <el-color-picker
          v-model="customColor"
          color-format="hex"
          @change="onCustomColor"
        />
        <span class="brand-current" :style="{ background: layoutStore.brandHex }" />
        <code class="brand-hex">{{ layoutStore.brandHex }}</code>
      </div>
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
import {
  useLayoutStore,
  type BrandPresetId,
  type NavMode,
  type ThemeMode
} from '@/stores/layout'
import { BRAND_PRESETS } from '@/styles/brand'

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
const brandPreset = ref<BrandPresetId>(layoutStore.brandPreset)
const customColor = ref(layoutStore.brandCustomHex)
const presets = BRAND_PRESETS

watch(visible, (open) => {
  if (!open) return
  navMode.value = layoutStore.navMode
  themeMode.value = layoutStore.themeMode
  sidebarCollapsed.value = layoutStore.sidebarCollapsed
  brandPreset.value = layoutStore.brandPreset
  customColor.value = layoutStore.brandCustomHex
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

function onBrandPreset(id: BrandPresetId) {
  brandPreset.value = id
  layoutStore.setBrandPreset(id)
  if (id !== 'custom') {
    const hit = BRAND_PRESETS.find((p) => p.id === id)
    if (hit) customColor.value = hit.hex
  }
}

function onCustomColor(val: string | null) {
  if (!val) return
  customColor.value = val
  brandPreset.value = 'custom'
  layoutStore.setBrandCustom(val)
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
  margin-bottom: 10px;
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

.brand-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.brand-swatch {
  position: relative;
  height: 44px;
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  padding: 0;
}

.brand-swatch.active {
  border-color: var(--meis-text-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}

.brand-swatch-name {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 2px 4px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  text-align: center;
}

.brand-custom-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-custom-row .pref-hint {
  margin: 0;
}

.brand-current {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  border: 1px solid var(--meis-border-light);
}

.brand-hex {
  font-size: 12px;
  color: var(--meis-text-secondary);
}
</style>
