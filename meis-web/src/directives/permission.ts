import type { App } from 'vue'
import { useAuthStore } from '@/stores/auth'

export function setupPermissionDirective(app: App) {
  app.directive('permission', {
    mounted(el, binding) {
      const auth = useAuthStore()
      const code = binding.value as string
      const buttons = auth.user?.permissions?.buttons ?? ['*']
      if (!buttons.includes('*') && !buttons.includes(code)) {
        el.style.display = 'none'
      }
    }
  })
}
