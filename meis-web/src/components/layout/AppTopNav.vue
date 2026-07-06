<template>
  <el-header class="top-nav-header" :class="{ mobile: isMobile }">
    <button
      v-if="isMobile"
      type="button"
      class="mobile-menu-btn"
      aria-label="打开菜单"
      @click="emit('open-menu')"
    >
      <el-icon :size="20"><Menu /></el-icon>
    </button>

    <div class="brand" @click="emit('home')">      <span class="brand-logo">MEIS</span>
      <span class="brand-sub">{{ brandTitle }}</span>
    </div>

    <NavMenuState
      v-if="!isMobile && (loading || error)"
      :loading="loading"
      :error="error"
      variant="top"
      @retry="emit('retry')"
    />

    <nav v-else-if="!isMobile && !loading && !error" class="top-nav">
      <template v-for="mod in modules" :key="mod.id">
        <div
          v-if="mod.path && !mod.groups?.length"
          class="nav-item"
          :class="{ active: isModuleActive(mod) }"
          @click="onNavClick(mod.path!, mod.title)"
        >
          {{ mod.title }}
        </div>

        <div
          v-else-if="mod.groups?.length"
          class="nav-item has-dropdown"
          :class="{ active: isModuleActive(mod) }"
          @mouseenter="showDropdown(mod.id, $event)"
          @mouseleave="scheduleHideDropdown"
        >
          <span>{{ mod.title }}</span>
          <el-icon class="arrow"><ArrowDown /></el-icon>
        </div>
      </template>
    </nav>

    <teleport to="body">
      <transition name="fade">
        <div
          v-if="openMod"
          class="dropdown-panel dropdown-panel--fixed"
          :style="dropdownStyle"
          @mouseenter="cancelHideDropdown"
          @mouseleave="scheduleHideDropdown"
        >
          <div v-for="group in openMod.groups" :key="group.title || openMod.id" class="menu-group">
            <div v-if="group.title" class="group-title">{{ group.title }}</div>
            <div class="group-items">
              <a
                v-for="item in group.items"
                :key="item.id"
                class="menu-link"
                :class="{ active: activePath === item.path }"
                @click.prevent="onNavClick(item.path, item.title)"
              >
                {{ item.title }}
              </a>
            </div>
          </div>
        </div>
      </transition>
    </teleport>

    <div class="header-actions">
      <slot name="actions" />
    </div>
  </el-header>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { ArrowDown, Menu } from '@element-plus/icons-vue'
import NavMenuState from './NavMenuState.vue'
import { useLayoutStore } from '@/stores/layout'

interface MenuItem {
  id: string
  title: string
  path: string
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

interface TopModule {
  id: string
  title: string
  path?: string
  groups: MenuGroup[]
}

const props = defineProps<{
  modules: TopModule[]
  activePath: string
  brandTitle: string
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  select: [path: string, title: string]
  home: []
  retry: []
  'open-menu': []
}>()

const layoutStore = useLayoutStore()
const isMobile = computed(() => layoutStore.isMobile)

const openId = ref('')
const dropdownStyle = ref<Record<string, string>>({})
const hideTimer = ref<ReturnType<typeof setTimeout> | null>(null)

const openMod = computed(() => props.modules.find((m) => m.id === openId.value) ?? null)

function onNavClick(path: string, title: string) {
  cancelHideDropdown()
  openId.value = ''
  emit('select', path, title)
}

function isModuleActive(mod: TopModule) {
  if (mod.path && props.activePath === mod.path) return true
  return mod.groups?.some((g) => g.items.some((i) => i.path === props.activePath))
}

function showDropdown(modId: string, e: MouseEvent) {
  if (hideTimer.value) {
    clearTimeout(hideTimer.value)
    hideTimer.value = null
  }
  openId.value = modId
  const el = e.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  dropdownStyle.value = {
    top: `${rect.bottom}px`,
    left: `${rect.left}px`
  }
}

function scheduleHideDropdown() {
  hideTimer.value = setTimeout(() => {
    openId.value = ''
  }, 120)
}

function cancelHideDropdown() {
  if (hideTimer.value) {
    clearTimeout(hideTimer.value)
    hideTimer.value = null
  }
}

onUnmounted(() => {
  if (hideTimer.value) clearTimeout(hideTimer.value)
})
</script>

<style scoped>
.top-nav-header {
  height: var(--meis-header-height);
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(90deg, var(--meis-header-gradient-start) 0%, var(--meis-header-gradient-end) 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  z-index: 200;
  flex-shrink: 0;
  overflow: visible;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  cursor: pointer;
  margin-right: 16px;
  flex-shrink: 0;
}

.brand-logo {
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
}

.top-nav {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: visible;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  height: 0;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 18px;
  height: 56px;
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s;
  user-select: none;
}

.nav-item:hover,
.nav-item.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}

.nav-item.active {
  box-shadow: inset 0 -3px 0 var(--el-color-primary);
}

.arrow {
  font-size: 12px;
  opacity: 0.7;
}

.dropdown-panel {
  min-width: 160px;
  max-width: 720px;
  padding: 16px 20px;
  background: var(--meis-surface-header, #fff);
  border-radius: 0 0 8px 8px;
  box-shadow: var(--meis-shadow-dropdown, 0 8px 24px rgba(0, 0, 0, 0.15));
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  z-index: 300;
}

.dropdown-panel--fixed {
  position: fixed;
}

.dropdown-panel::before {
  content: '';
  position: absolute;
  top: -8px;
  left: 0;
  right: 0;
  height: 8px;
}

.menu-group {
  min-width: 140px;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-link {
  display: block;
  padding: 7px 10px;
  font-size: 13px;
  color: var(--meis-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.menu-link:hover,
.menu-link.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  margin-left: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.mobile-menu-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 4px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  flex-shrink: 0;
}

.mobile-menu-btn:hover {
  background: rgba(255, 255, 255, 0.12);
}

.top-nav-header.mobile {
  padding: 0 12px;
}

.top-nav-header.mobile .brand-sub {
  display: none;
}

.top-nav-header.mobile .mobile-menu-btn {
  display: inline-flex;
}
</style>
