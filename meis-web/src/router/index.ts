import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useRouteProgressStore } from '@/stores/routeProgress'
import { useTabsStore } from '@/stores/tabs'
import { getHomePath } from '@/utils/home'
import { clearPersistedTabs } from '@/utils/tabStorage'

const PLATFORM_PATHS = ['/tenant/list', '/platform/tenant-menu', '/platform/package']

let isFirstAuthenticatedNav = true

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: () => getHomePath()
        },
        { path: 'dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'tenant/list', component: () => import('@/views/TenantList.vue') },
        { path: 'platform/tenant-menu', component: () => import('@/views/platform/TenantMenuAuth.vue') },
        { path: 'platform/package', component: () => import('@/views/platform/PackagePage.vue') },
        { path: 'platform/integration', component: () => import('@/views/platform/IntegrationPage.vue') },
        { path: 'analytics/reports', component: () => import('@/views/Reports.vue') },
        { path: 'analytics/benefit', redirect: '/analytics/summary' },
        { path: ':module/:page', component: () => import('@/views/ModulePage.vue') },
        { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') }
      ]
    }
  ]
})

router.beforeEach((to, from) => {
  if (to.path !== from.path) {
    useRouteProgressStore().start()
  }

  const auth = useAuthStore()
  auth.restore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (to.meta.requiresAuth && auth.isLoggedIn && isFirstAuthenticatedNav) {
    isFirstAuthenticatedNav = false
    const tabs = useTabsStore()
    tabs.reset()
    clearPersistedTabs()
    const home = getHomePath()
    if (to.path !== home) {
      return home
    }
  }
  if (to.path === '/login' && auth.isLoggedIn) {
    return getHomePath()
  }
  if (auth.isPlatformAdmin && to.meta.requiresAuth) {
    const allowed = PLATFORM_PATHS.some((p) => to.path === p || to.path.startsWith(p + '/'))
    if (!allowed) return '/tenant/list'
  }
})

router.afterEach(() => {
  useRouteProgressStore().finish()
})

router.onError(() => {
  useRouteProgressStore().fail()
})

export default router
