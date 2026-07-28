import '../../core/models/auth_user.dart';

/// 菜单权限判断（与 Web `usePermission.hasMenu` 对齐）
bool hasMenu(AuthUser? user, String code) {
  final menus = user?.permissions?['menus'];
  if (menus is! List) return true;
  final list = menus.map((e) => e.toString()).toList();
  if (list.contains('*')) return true;
  return list.contains(code);
}

/// 任一菜单码有权限即为 true
bool hasAnyMenu(AuthUser? user, Iterable<String> codes) {
  for (final c in codes) {
    if (hasMenu(user, c)) return true;
  }
  return false;
}
