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
