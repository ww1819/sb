import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../auth/providers/auth_provider.dart';
import 'about_page.dart';
import 'change_password_page.dart';
import 'profile_page.dart';
import 'server_ip_page.dart';

/// 「我的」：微信式资料头 + 分组列表（MOB-UI-02）
class MinePage extends ConsumerWidget {
  const MinePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;
    final name = user?.realName?.trim().isNotEmpty == true
        ? user!.realName!
        : (user?.username ?? '用户');
    final accountLine = [
      if (user != null && user.username.trim().isNotEmpty) '账号: ${user.username}',
      if (user != null && user.tenantCode.trim().isNotEmpty) '租户: ${user.tenantCode}',
    ].join('  ');

    return Scaffold(
      backgroundColor: AppColors.pageBg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.only(bottom: AppSpacing.xxl),
          children: [
            const SizedBox(height: AppSpacing.md),
            // 资料头（参考微信「我」）
            Material(
              color: Colors.white,
              child: InkWell(
                onTap: () => _push(context, const ProfilePage()),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 20, 16, 20),
                  child: Row(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          width: 64,
                          height: 64,
                          color: AppColors.primary.withValues(alpha: 0.12),
                          alignment: Alignment.center,
                          child: Text(
                            name.isNotEmpty ? name.substring(0, 1) : '用',
                            style: const TextStyle(
                              color: AppColors.primary,
                              fontWeight: FontWeight.w700,
                              fontSize: 28,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              name,
                              style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.w600,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            if (accountLine.isNotEmpty) ...[
                              const SizedBox(height: 8),
                              Text(
                                accountLine,
                                style: const TextStyle(
                                  fontSize: 13,
                                  color: AppColors.textSecondary,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      Icon(
                        Icons.chevron_right,
                        color: AppColors.textSecondary.withValues(alpha: 0.55),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 10),
            // 设置分组
            _MenuGroup(
              children: [
                _MenuTile(
                  icon: Icons.lock_outline,
                  iconColor: const Color(0xFF1677FF),
                  label: '修改密码',
                  onTap: () => _push(context, const ChangePasswordPage()),
                ),
                _MenuTile(
                  icon: Icons.lan_outlined,
                  iconColor: const Color(0xFF52C41A),
                  label: '设置IP',
                  onTap: () => _push(context, const ServerIpPage()),
                ),
                _MenuTile(
                  icon: Icons.info_outline,
                  iconColor: const Color(0xFFFAAD14),
                  label: '关于',
                  showDivider: false,
                  onTap: () => _push(context, const AboutPage()),
                ),
              ],
            ),
            const SizedBox(height: 10),
            // 退出分组
            _MenuGroup(
              children: [
                _MenuTile(
                  icon: Icons.logout,
                  iconColor: AppColors.danger,
                  label: '退出登录',
                  labelColor: AppColors.danger,
                  showDivider: false,
                  onTap: () => _logout(context, ref),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _push(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));
  }

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认退出'),
        content: const Text('退出后需要重新登录'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('退出')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(authProvider.notifier).logout();
    if (context.mounted) context.go('/login');
  }
}

class _MenuGroup extends StatelessWidget {
  const _MenuGroup({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.white,
      child: Column(children: children),
    );
  }
}

class _MenuTile extends StatelessWidget {
  const _MenuTile({
    required this.icon,
    required this.iconColor,
    required this.label,
    required this.onTap,
    this.labelColor,
    this.showDivider = true,
  });

  final IconData icon;
  final Color iconColor;
  final String label;
  final Color? labelColor;
  final VoidCallback onTap;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Material(
          color: Colors.white,
          child: InkWell(
            onTap: onTap,
            child: SizedBox(
              height: 54,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Icon(icon, color: iconColor, size: 22),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Text(
                        label,
                        style: TextStyle(
                          fontSize: 16,
                          color: labelColor ?? AppColors.textPrimary,
                        ),
                      ),
                    ),
                    Icon(
                      Icons.chevron_right,
                      color: AppColors.textSecondary.withValues(alpha: 0.45),
                      size: 22,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        if (showDivider)
          const Padding(
            padding: EdgeInsets.only(left: 52),
            child: Divider(height: 1, thickness: 0.5, color: AppColors.borderLight),
          ),
      ],
    );
  }
}
