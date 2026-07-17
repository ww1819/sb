<template>
  <el-drawer
    v-model="visible"
    :title="brandTitle"
    direction="ltr"
    size="min(280px, 86vw)"
    append-to-body
    class="mobile-nav-drawer"
    @closed="emit('closed')"
  >
    <NavMenuState
      v-if="loading || error"
      :loading="loading"
      :error="error"
      variant="side"
      @retry="emit('retry')"
    />

    <el-menu
      v-else
      :default-active="activePath"
      :unique-opened="true"
      class="mobile-menu"
      @select="onSelect"
    >
      <template v-for="mod in modules" :key="mod.id">
        <el-menu-item v-if="mod.path && !mod.groups?.length" :index="mod.path">
          <el-icon><component :is="moduleIcon(mod.id)" /></el-icon>
          <span>{{ mod.title }}</span>
        </el-menu-item>

        <el-sub-menu v-else-if="mod.groups?.length" :index="mod.id">
          <template #title>
            <el-icon><component :is="moduleIcon(mod.id)" /></el-icon>
            <span>{{ mod.title }}</span>
          </template>
          <template v-for="(group, gi) in mod.groups" :key="`${mod.id}-${group.id || group.title || gi}`">
            <el-sub-menu
              v-if="group.title"
              :index="`${mod.id}/${group.id || group.title || `g${gi}`}`"
            >
              <template #title>
                <span>{{ group.title }}</span>
              </template>
              <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
                {{ item.title }}
              </el-menu-item>
              <template v-for="(sub, si) in group.groups ?? []" :key="`${group.id || gi}-sub-${sub.id || sub.title || si}`">
                <el-sub-menu
                  v-if="sub.title"
                  :index="`${mod.id}/${group.id || group.title || `g${gi}`}/${sub.id || sub.title || `s${si}`}`"
                >
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
  </el-drawer>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
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
import type { NavModule } from '@/utils/menuNav'

defineProps<{
  modules: NavModule[]
  activePath: string
  brandTitle: string
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  select: [path: string]
  retry: []
  closed: []
}>()

const visible = defineModel<boolean>({ default: false })

function onSelect(path: string) {
  visible.value = false
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
.mobile-menu {
  border-right: none;
}

.mobile-nav-drawer :deep(.nav-menu-state--side) {
  color: var(--meis-text-secondary);
}

.mobile-nav-drawer :deep(.side-sk-item .el-skeleton__item) {
  background: var(--el-fill-color) !important;
}
</style>
