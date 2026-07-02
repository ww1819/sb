import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const PLATFORM_PATHS = ['/tenant/list', '/platform/tenant-menu', '/platform/package']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'tenant/list', component: () => import('@/views/TenantList.vue') },
        { path: 'platform/tenant-menu', component: () => import('@/views/platform/TenantMenuAuth.vue') },
        { path: 'platform/package', component: () => import('@/views/platform/PackagePage.vue') },
        { path: 'platform/integration', component: () => import('@/views/platform/IntegrationPage.vue') },
        { path: 'analytics/reports', component: () => import('@/views/Reports.vue') },
        { path: 'analytics/benefit', component: () => import('@/views/analytics/BenefitPage.vue') },
        { path: ':module/:page', component: () => import('@/views/ModulePage.vue') }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (to.path === '/login' && auth.isLoggedIn) {
    return auth.isPlatformAdmin ? '/tenant/list' : '/dashboard'
  }
  if (auth.isPlatformAdmin && to.meta.requiresAuth) {
    const allowed = PLATFORM_PATHS.some((p) => to.path === p || to.path.startsWith(p + '/'))
    if (!allowed) return '/tenant/list'
  }
})

export default router
