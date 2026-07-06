import type { TabItem } from '@/stores/tabs'

export function isPlatformUser(): boolean {
  const raw = localStorage.getItem('meis_user')
  if (!raw) return false
  try {
    return JSON.parse(raw).userType === 'platform'
  } catch {
    return false
  }
}

export function getHomePath(): string {
  return isPlatformUser() ? '/tenant/list' : '/dashboard'
}

export function getHomeTitle(): string {
  return isPlatformUser() ? '租户列表' : '工作台'
}

export function isHomePath(path: string): boolean {
  return path === getHomePath()
}

export function createHomeTab(): TabItem {
  return { path: getHomePath(), title: getHomeTitle(), closable: false }
}
