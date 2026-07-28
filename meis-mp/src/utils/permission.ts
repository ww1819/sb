/** 菜单权限：menus 含 `*` 或具体码 */

export type PermissionUser = {
  permissions?: {
    menus?: string[]
    [key: string]: unknown
  } | null
  [key: string]: unknown
} | null | undefined

export function hasMenu(user: PermissionUser, code: string): boolean {
  const menus = user?.permissions?.menus
  if (!menus) return true
  if (menus.includes('*')) return true
  return menus.includes(code)
}
