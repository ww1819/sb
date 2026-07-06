<template>

  <el-container class="layout" :class="layoutClass">

    <AppSidebar

      v-if="layoutStore.isSideNav && !layoutStore.isMobile"

      :modules="modules"

      :active-path="tabsStore.activePath"

      :collapsed="layoutStore.sidebarCollapsed"

      :brand-title="brandTitle"

      :loading="menuLoading"

      :error="menuError"

      @select="onMenuSelect"

      @home="openPage(homePath, homeTitle)"

      @retry="loadMenus"

    />



    <el-container

      class="layout-main"

      :class="{ 'layout-main--fullscreen': layoutStore.contentFullscreen }"

      direction="vertical"

    >

      <template v-if="!layoutStore.contentFullscreen">

        <AppTopNav

          v-if="layoutStore.isTopNav"

          :modules="modules"

          :active-path="tabsStore.activePath"

          :brand-title="brandTitle"

          :loading="menuLoading"

          :error="menuError"

          @select="onMenuSelectWithTitle"

          @home="openPage(homePath, homeTitle)"

          @retry="loadMenus"

          @open-menu="layoutStore.openMobileNav()"

        >

          <template #actions>

            <HeaderToolbar

              dark

              :show-notifications="!auth.isPlatformAdmin"

              :fullscreen="layoutStore.contentFullscreen"

              @open-search="menuSearchOpen = true"

              @view-all-messages="openMessageCenter"

              @toggle-fullscreen="layoutStore.toggleContentFullscreen()"

              @open-help="helpOpen = true"

            >

              <LayoutUserBar

                :avatar-text="avatarText"

                :user-name="auth.user?.realName || auth.user?.username || ''"

                :tenant-label="userLabel"

                dark

                @command="onUserCommand"

              />

            </HeaderToolbar>

          </template>

        </AppTopNav>



        <el-header v-else class="side-mode-header" :class="{ mobile: layoutStore.isMobile }">

          <div class="header-left">

            <el-button class="collapse-btn" text @click="onSideHeaderMenuClick">

              <el-icon :size="18">

                <component :is="sideHeaderMenuIcon" />

              </el-icon>

            </el-button>

            <PageBreadcrumb :items="breadcrumbItems" />

          </div>

          <HeaderToolbar

            :show-notifications="!auth.isPlatformAdmin"

            :fullscreen="layoutStore.contentFullscreen"

            @open-search="menuSearchOpen = true"

            @view-all-messages="openMessageCenter"

            @toggle-fullscreen="layoutStore.toggleContentFullscreen()"

            @open-help="helpOpen = true"

          >

            <LayoutUserBar

              :avatar-text="avatarText"

              :user-name="auth.user?.realName || auth.user?.username || ''"

              :tenant-label="userLabel"

              @command="onUserCommand"

            />

          </HeaderToolbar>

        </el-header>



        <TabBar />

      </template>



      <el-main class="main-content">

        <div v-if="layoutStore.contentFullscreen" class="fullscreen-bar">

          <span class="fullscreen-title">{{ currentPageTitle }}</span>

          <el-button type="primary" link @click="layoutStore.setContentFullscreen(false)">

            <el-icon><Close /></el-icon>

            退出全屏

          </el-button>

        </div>



        <PageBreadcrumb

          v-if="!layoutStore.contentFullscreen && layoutStore.isTopNav"

          :items="breadcrumbItems"

        />



        <div class="page-container">

          <router-view v-slot="{ Component, route: r }">

            <transition name="page-fade-slide" mode="out-in">

              <keep-alive :max="20">

                <component :is="Component" v-if="Component" :key="r.fullPath" class="page-view" />

              </keep-alive>

            </transition>

          </router-view>

        </div>



        <AppFooter v-if="!layoutStore.contentFullscreen && !layoutStore.isMobile" />

      </el-main>

    </el-container>



    <MenuSearchDialog

      v-model="menuSearchOpen"

      :modules="modules"

      @select="onMenuSelectWithTitle"

    />



    <LayoutPreferencesDrawer

      v-model="preferencesOpen"

      :user-name="auth.user?.realName || auth.user?.username || ''"

      :tenant-label="userLabel"

      :home-path="homePath"

      :home-title="homeTitle"

    />



    <MobileNavDrawer

      v-model="layoutStore.mobileNavOpen"

      :modules="modules"

      :active-path="tabsStore.activePath"

      :brand-title="brandTitle"

      :loading="menuLoading"

      :error="menuError"

      @select="onMenuSelect"

      @retry="loadMenus"

    />



    <HelpDrawer v-model="helpOpen" />

  </el-container>

</template>



<script setup lang="ts">

import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { Close, Expand, Fold, Menu } from '@element-plus/icons-vue'

import http from '@/api/http'

import { useAuthStore } from '@/stores/auth'

import { useLayoutStore } from '@/stores/layout'

import { useTabsStore } from '@/stores/tabs'

import TabBar from '@/components/TabBar.vue'

import AppSidebar from '@/components/layout/AppSidebar.vue'

import AppTopNav from '@/components/layout/AppTopNav.vue'

import LayoutUserBar from '@/components/layout/LayoutUserBar.vue'

import PageBreadcrumb from '@/components/layout/PageBreadcrumb.vue'

import HeaderToolbar from '@/components/layout/HeaderToolbar.vue'

import MenuSearchDialog from '@/components/layout/MenuSearchDialog.vue'

import AppFooter from '@/components/layout/AppFooter.vue'

import LayoutPreferencesDrawer from '@/components/layout/LayoutPreferencesDrawer.vue'

import MobileNavDrawer from '@/components/layout/MobileNavDrawer.vue'

import HelpDrawer from '@/components/layout/HelpDrawer.vue'

import { resolveBreadcrumb, type NavModule } from '@/utils/menuNav'



const route = useRoute()

const router = useRouter()

const auth = useAuthStore()

const layoutStore = useLayoutStore()

const tabsStore = useTabsStore()

const modules = ref<NavModule[]>([])

const titleMap = ref<Map<string, string>>(new Map())

const menuSearchOpen = ref(false)

const menuLoading = ref(true)

const menuError = ref('')

const preferencesOpen = ref(false)

const helpOpen = ref(false)



const homePath = computed(() => (auth.isPlatformAdmin ? '/tenant/list' : '/dashboard'))

const homeTitle = computed(() => (auth.isPlatformAdmin ? '租户列表' : '工作台'))

const brandTitle = computed(() => (auth.isPlatformAdmin ? '平台管理' : '医院设备管理'))

const userLabel = computed(() =>

  auth.isPlatformAdmin

    ? `平台 · ${auth.user?.realName || auth.user?.username}`

    : `${auth.user?.tenantCode} · ${auth.user?.realName}`

)

const avatarText = computed(() => {

  const name = auth.user?.realName || auth.user?.username || 'U'

  return name.slice(0, 1).toUpperCase()

})

const layoutClass = computed(() => ({

  'layout--side': layoutStore.isSideNav,

  'layout--top': layoutStore.isTopNav,

  'layout--mobile': layoutStore.isMobile

}))

const breadcrumbItems = computed(() =>

  resolveBreadcrumb(modules.value, tabsStore.activePath, titleMap.value)

)

const currentPageTitle = computed(

  () => tabsStore.tabs.find((t) => t.path === tabsStore.activePath)?.title ?? '当前页面'

)

const sideHeaderMenuIcon = computed(() => {

  if (layoutStore.isMobile) return Menu

  return layoutStore.sidebarCollapsed ? Expand : Fold

})



function onKeydown(e: KeyboardEvent) {

  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {

    e.preventDefault()

    menuSearchOpen.value = true

    return

  }

  if (e.key === 'Escape' && layoutStore.contentFullscreen) {

    layoutStore.setContentFullscreen(false)

  }

}



function onSideHeaderMenuClick() {

  if (layoutStore.isMobile) {

    layoutStore.openMobileNav()

    return

  }

  layoutStore.toggleSidebar()

}



function onUserCommand(cmd: string) {

  if (cmd === 'logout') {

    logout()

    return

  }

  if (cmd === 'settings') {

    preferencesOpen.value = true

  }

}



function onMenuSelect(path: string) {

  const title = titleMap.value.get(path) ?? defaultTitle(path)

  openPage(path, title)

}



function onMenuSelectWithTitle(path: string, title: string) {

  openPage(path, title)

}



function openMessageCenter() {

  openPage('/dashboard', '工作台')

}



async function loadMenus() {

  menuLoading.value = true

  menuError.value = ''



  try {

    const store = useAuthStore()

    if (store.isPlatformAdmin) {

      const { data } = await http.get('/system/menus/platform-nav')

      if (data.code === 0) {

        modules.value = data.data ?? []

        titleMap.value = buildTitleMap(modules.value)

        if (!modules.value.length) {

          menuError.value = '暂无可用菜单'

        }

      } else {

        menuError.value = data.message || '菜单加载失败'

      }

      return

    }



    const rawMenus = store.user?.permissions?.menus

    const menus = !rawMenus?.length || rawMenus.includes('*') ? ['*'] : rawMenus

    const { data } = await http.post('/system/menus', { menus })

    if (data.code === 0) {

      modules.value = data.data ?? []

      titleMap.value = buildTitleMap(modules.value)

      if (!modules.value.length) {

        menuError.value = '暂无可用菜单，请联系管理员分配权限'

      }

    } else {

      menuError.value = data.message || '菜单加载失败'

    }

  } catch {

    menuError.value = '菜单加载失败，请检查网络或稍后重试'

  } finally {

    menuLoading.value = false

  }

}



onMounted(async () => {

  window.addEventListener('keydown', onKeydown)

  layoutStore.initBreakpoint()

  tabsStore.ensureHomeTab()



  if (route.path !== tabsStore.activePath && route.path !== '/login') {

    router.replace(tabsStore.activePath)

  }



  await loadMenus()

})



onUnmounted(() => {

  window.removeEventListener('keydown', onKeydown)

  layoutStore.setContentFullscreen(false)

})



watch(

  () => route.path,

  (path) => {

    if (!path || path === '/login') return

    const title = titleMap.value.get(path) ?? defaultTitle(path)

    if (!tabsStore.tabs.find((t) => t.path === path)) {

      tabsStore.open(path, title)

    } else {

      tabsStore.switchTo(path)

    }

  },

  { immediate: true }

)



function buildTitleMap(list: NavModule[]) {

  const map = new Map<string, string>()

  for (const mod of list) {

    if (mod.path) map.set(mod.path, mod.title)

    for (const g of mod.groups ?? []) {

      for (const item of g.items) map.set(item.path, item.title)

    }

  }

  map.set('/tenant/list', '租户列表')

  map.set('/analytics/reports', '统计报表')

  map.set('/dashboard', '工作台')

  return map

}



function defaultTitle(path: string) {

  const seg = path.split('/').filter(Boolean).pop()

  return seg ?? path

}



function openPage(path: string, title: string) {

  tabsStore.open(path, title)

}



function logout() {

  layoutStore.setContentFullscreen(false)

  tabsStore.reset()

  auth.logout()

  router.push('/login')

}

</script>



<style scoped>

.layout {

  height: 100vh;

  background: var(--meis-page-bg);

}



.layout--top {

  flex-direction: column;

}



.layout--side {

  flex-direction: row;

}



.layout--mobile.layout--side {

  flex-direction: column;

}



.layout-main {

  min-width: 0;

  flex: 1;

}



.layout-main--fullscreen {

  position: fixed;

  inset: 0;

  z-index: 5000;

  background: var(--meis-page-bg);

}



.side-mode-header {

  height: var(--meis-header-height);

  padding: 0 20px;

  display: flex;

  align-items: center;

  justify-content: space-between;

  background: var(--meis-surface-header);

  border-bottom: 1px solid var(--meis-border-light);

  box-shadow: var(--meis-shadow-sm);

  flex-shrink: 0;

  z-index: 200;

}



.side-mode-header.mobile {

  padding: 0 12px;

}



.header-left {

  display: flex;

  align-items: center;

  gap: 8px;

  min-width: 0;

  flex: 1;

}



.header-left :deep(.page-breadcrumb) {

  margin-bottom: 0;

}



.collapse-btn {

  color: var(--meis-text-secondary);

}



.collapse-btn:hover {

  color: var(--meis-text-primary);

  background: var(--meis-table-hover);

}



.main-content {

  padding: 16px 20px;

  overflow: auto;

  flex: 1;

  background: var(--meis-page-bg);

}



.layout-main--fullscreen .main-content {

  padding: 12px 16px;

}



.fullscreen-bar {

  display: flex;

  align-items: center;

  justify-content: space-between;

  gap: 12px;

  margin-bottom: 12px;

  padding-bottom: 12px;

  border-bottom: 1px solid var(--meis-border-light);

}



.fullscreen-title {

  font-size: 16px;

  font-weight: 600;

  color: var(--meis-text-primary);

}



.page-container {

  min-height: 100%;

}



.page-view {

  min-height: 100%;

}



@media (max-width: 767px) {

  .main-content {

    padding: 12px;

  }



  .header-left :deep(.page-breadcrumb) {

    font-size: 12px;

  }

}

</style>

<template>
  <el-container class="layout">
    <el-header class="top-header">
      <div class="brand" @click="openPage(homePath, homeTitle)">
        <span class="brand-logo">MEIS</span>
        <span class="brand-sub">{{ auth.isPlatformAdmin ? '平台管理' : '医院设备管理' }}</span>
      </div>

      <nav class="top-nav">
        <template v-for="mod in modules" :key="mod.id">
          <div
            v-if="!mod.groups?.length"
            class="nav-item"
            :class="{ active: isModuleActive(mod) }"
            @click="openPage(mod.path!, mod.title)"
          >
            {{ mod.title }}
          </div>

          <div
            v-else
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
                  :class="{ active: tabsStore.activePath === item.path }"
                  @click.prevent="openPage(item.path, item.title)"
                >
                  {{ item.title }}
                </a>
              </div>
            </div>
          </div>
        </transition>
      </teleport>

      <div class="user-bar">
        <span class="tenant">{{ userLabel }}</span>
        <el-dropdown trigger="click" @command="onUserCommand">
          <span class="user-trigger">
            <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <TabBar />

    <el-main class="main-content">
      <div class="page-container">
        <router-view v-slot="{ Component, route: r }">
        <keep-alive :max="20">
          <component :is="Component" v-if="Component" :key="r.fullPath" />
        </keep-alive>
        </router-view>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useTabsStore } from '@/stores/tabs'
import TabBar from '@/components/TabBar.vue'

interface MenuItem {
  id: string
  title: string
  path: string
  table?: string
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

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const tabsStore = useTabsStore()
const modules = ref<TopModule[]>([])
const openId = ref('')
const dropdownStyle = ref<Record<string, string>>({})
const hideTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const titleMap = ref<Map<string, string>>(new Map())
const homePath = computed(() => (auth.isPlatformAdmin ? '/tenant/list' : '/dashboard'))
const homeTitle = computed(() => (auth.isPlatformAdmin ? '租户列表' : '工作台'))
const userLabel = computed(() =>
  auth.isPlatformAdmin
    ? `平台 / ${auth.user?.realName || auth.user?.username}`
    : `${auth.user?.tenantCode} / ${auth.user?.realName}`
)
const avatarText = computed(() => {
  const name = auth.user?.realName || auth.user?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

function onUserCommand(cmd: string) {
  if (cmd === 'logout') void logout()
}

const openMod = computed(() => modules.value.find((m) => m.id === openId.value) ?? null)

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

onMounted(async () => {
  const store = useAuthStore()
  if (store.isPlatformAdmin) {
    const { data } = await http.get('/system/menus/platform-nav')
    if (data.code === 0) {
      modules.value = data.data
      titleMap.value = buildTitleMap(data.data)
    }
    return
  }
  const rawMenus = store.user?.permissions?.menus
  const menus = !rawMenus?.length || rawMenus.includes('*') ? ['*'] : rawMenus
  const { data } = await http.post('/system/menus', { menus })
  if (data.code === 0) {
    modules.value = data.data
    titleMap.value = buildTitleMap(data.data)
  }
})

onUnmounted(() => {
  if (hideTimer.value) clearTimeout(hideTimer.value)
})

watch(
  () => route.path,
  (path) => {
    if (!path || path === '/login') return
    const title = titleMap.value.get(path) ?? defaultTitle(path)
    if (!tabsStore.tabs.find((t) => t.path === path)) {
      tabsStore.open(path, title)
    } else {
      tabsStore.activePath = path
    }
  },
  { immediate: true }
)

function buildTitleMap(list: TopModule[]) {
  const map = new Map<string, string>()
  for (const mod of list) {
    if (mod.path) map.set(mod.path, mod.title)
    for (const g of mod.groups ?? []) {
      for (const item of g.items) map.set(item.path, item.title)
    }
  }
  map.set('/tenant/list', '租户列表')
  map.set('/analytics/reports', '统计报表')
  return map
}

function defaultTitle(path: string) {
  const seg = path.split('/').filter(Boolean).pop()
  return seg ?? path
}

function openPage(path: string, title: string) {
  cancelHideDropdown()
  openId.value = ''
  tabsStore.open(path, title)
}

function isModuleActive(mod: TopModule) {
  const active = tabsStore.activePath
  if (mod.path && active === mod.path) return true
  return mod.groups?.some((g) => g.items.some((i) => i.path === active))
}

async function logout() {
  tabsStore.reset()
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
  flex-direction: column;
  background: var(--meis-page-bg);
}

.top-header {
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
  background: #fff;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
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
  color: #606266;
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

.user-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  margin-left: auto;
}

.tenant {
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.9);
}

.user-avatar {
  background: var(--el-color-primary);
  font-size: 13px;
}

.main-content {
  padding: 16px 20px;
  overflow: auto;
  flex: 1;
  background: var(--meis-page-bg);
}

.page-container {
  min-height: 100%;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
