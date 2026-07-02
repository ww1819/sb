import { useAuthStore } from '@/stores/auth'

export function usePermission() {
  const auth = useAuthStore()

  function hasMenu(menuCode: string): boolean {
    const menus = auth.user?.permissions?.menus as string[] | undefined
    if (!menus) return true
    if (menus.includes('*')) return true
    return menus.includes(menuCode)
  }

  function hasButton(code: string): boolean {
    const buttons = auth.user?.permissions?.buttons as string[] | undefined
    if (!buttons) return true
    if (buttons.includes('*')) return true
    return buttons.includes(code)
  }

  return { hasMenu, hasButton }
}
