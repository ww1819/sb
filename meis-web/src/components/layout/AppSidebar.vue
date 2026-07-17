<template>
  <aside class="app-sidebar" :class="{ collapsed }" :style="{ width: sidebarWidth }">
    <div class="sidebar-brand" @click="emit('home')">
      <span class="brand-logo">M</span>
      <span v-show="!collapsed" class="brand-text">{{ brandTitle }}</span>
    </div>

    <el-scrollbar class="sidebar-scroll">
      <NavMenuState
        v-if="loading || error"
        :loading="loading"
        :error="error"
        variant="side"
        @retry="emit('retry')"
      />

      <el-menu
        v-else
        ref="menuRef"
        :default-active="activePath"
        :default-openeds="openedModules"
        :unique-opened="true"
        :collapse="collapsed"
        :collapse-transition="false"
        class="sidebar-menu"
        @select="onSelect"
      >
        <template v-for="mod in modules" :key="mod.id">
          <el-menu-item v-if="mod.path && !mod.groups?.length" :index="mod.path">
            <el-icon><component :is="moduleIcon(mod.id)" /></el-icon>
            <template #title>{{ mod.title }}</template>
          </el-menu-item>

          <el-sub-menu v-else-if="mod.groups?.length" :index="mod.id">
            <template #title>
              <el-icon><component :is="moduleIcon(mod.id)" /></el-icon>
              <span>{{ mod.title }}</span>
            </template>
            <template v-for="(group, gi) in mod.groups" :key="`${mod.id}-${group.id || group.title || gi}`">
              <el-sub-menu v-if="group.title" :index="groupSubIndex(mod.id, group, gi)">
                <template #title>
                  <span>{{ group.title }}</span>
                </template>
                <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
                  {{ item.title }}
                </el-menu-item>
                <template v-for="(sub, si) in group.groups ?? []" :key="`${group.id || gi}-sub-${sub.id || sub.title || si}`">
                  <el-sub-menu v-if="sub.title" :index="groupSubIndex(groupSubIndex(mod.id, group, gi), sub, si)">
                    <template #title>
                      <span>{{ sub.title }}</span>
                    </template>
                    <el-menu-item v-for="item in sub.items" :key="item.path" :index="item.path">
                      {{ item.title }}
                    </el-menu-item>
                  </el-sub-menu>
                  <el-menu-item v-for="item in sub.items" v-else :key="item.path" :index="item.path">
                    {{ item.title }}
                  </el-menu-item>
                </template>
              </el-sub-menu>
              <el-menu-item v-for="item in group.items" v-else :key="item.path" :index="item.path">
                {{ item.title }}
              </el-menu-item>
            </template>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import type { MenuInstance } from 'element-plus'
import {
  Box,
  DataAnalysis,
  Document,
  Monitor,
  Odometer,
  OfficeBuilding,
  Setting,
  ShoppingCart,
  Tools,
  Warning
} from '@element-plus/icons-vue'
import NavMenuState from './NavMenuState.vue'

interface MenuItem {
  id: string
  title: string
  path: string
}

interface MenuGroup {
  id?: string
  title: string
  items: MenuItem[]
  groups?: MenuGroup[]
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
  collapsed: boolean
  brandTitle: string
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  select: [path: string]
  home: []
  retry: []
}>()

const menuRef = ref<MenuInstance>()

function groupSubIndex(modId: string, group: MenuGroup, gi: number) {
  return `${modId}/${group.id || group.title || `g${gi}`}`
}

/** 当前路径对应需展开的一级模块 + 二级/三级分组 index */
function findOpenIndexes(path: string): string[] {
  for (const mod of props.modules) {
    if (mod.path === path) return []
    for (const [gi, group] of (mod.groups ?? []).entries()) {
      if (group.items.some((item) => item.path === path)) {
        const idxs = [mod.id]
        if (group.title) idxs.push(groupSubIndex(mod.id, group, gi))
        return idxs
      }
      for (const [si, sub] of (group.groups ?? []).entries()) {
        if (sub.items.some((item) => item.path === path)) {
          const idxs = [mod.id]
          if (group.title) idxs.push(groupSubIndex(mod.id, group, gi))
          if (sub.title) idxs.push(groupSubIndex(groupSubIndex(mod.id, group, gi), sub, si))
          return idxs
        }
      }
    }
  }
  return []
}

const openedModules = computed(() => findOpenIndexes(props.activePath))

watch(
  () => props.activePath,
  (path) => {
    if (!path) return
    menuRef.value?.updateActiveIndex(path)
    for (const idx of findOpenIndexes(path)) {
      menuRef.value?.open(idx)
    }
  }
)

const sidebarWidth = computed(() =>
  props.collapsed ? 'var(--meis-sidebar-collapsed-width)' : 'var(--meis-sidebar-width)'
)

function onSelect(path: string) {
  emit('select', path)
}

function moduleIcon(id: string): Component {
  const map: Record<string, Component> = {
    dashboard: Odometer,
    purchase: ShoppingCart,
    asset: Box,
    repair: Tools,
    maintain: Setting,
    qc: Monitor,
    special: Warning,
    analytics: DataAnalysis,
    system: Setting,
    tenant: OfficeBuilding,
    platform: OfficeBuilding,
    'maintenance-contract': Document
  }
  return map[id] ?? Document
}
</script>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, var(--meis-header-gradient-start) 0%, #002140 100%);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  transition: width 0.2s ease;
  flex-shrink: 0;
  z-index: 210;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--meis-header-height);
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.brand-text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-scroll {
  flex: 1;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
  padding: 8px 0 16px;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  color: rgba(255, 255, 255, 0.82);
  height: 44px;
  line-height: 44px;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary) !important;
  color: #fff;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  background: transparent;
}

.sidebar-menu :deep(.el-sub-menu .el-menu) {
  background: rgba(0, 0, 0, 0.15);
}

/* 二级分组（如保养管理）：与一级子项同色，右侧有展开箭头可收起 */
.sidebar-menu :deep(.el-sub-menu .el-sub-menu .el-sub-menu__title) {
  padding-left: 44px !important;
  color: rgba(255, 255, 255, 0.82);
  font-size: 14px;
}

.sidebar-menu :deep(.el-sub-menu .el-sub-menu .el-menu-item) {
  padding-left: 60px !important;
}

.collapsed .brand-text {
  display: none;
}
</style>
