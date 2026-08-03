import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../auth/providers/auth_provider.dart';
import 'about_page.dart';
import 'change_password_page.dart';
import 'server_ip_page.dart';

/// 「我的」：账号摘要 + 改密 / 设置 IP / 关于 / 退出（MOB-UI-02）
class MinePage extends ConsumerWidget {
  const MinePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;
    final name = user?.realName?.trim().isNotEmpty == true
        ? user!.realName!
        : (user?.username ?? '用户');

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppColors.pageBgTop, AppColors.pageBg, AppColors.pageBg],
            stops: [0, 0.35, 1],
          ),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.pageH,
              AppSpacing.lg,
              AppSpacing.pageH,
              AppSpacing.xxl,
            ),
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'MEIS',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.2,
                        ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '医疗设备助手',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: AppColors.textSecondary,
                        ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xl),
              Text(
                '你好，$name',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                      fontSize: 22,
                    ),
              ),
              if (user != null) ...[
                const SizedBox(height: 6),
                Text(
                  '${user.tenantCode} · 扫码报修 / 运维 / 盘点',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: AppColors.textSecondary,
                      ),
                ),
              ],
              const SizedBox(height: AppSpacing.xl),
              Container(
                padding: const EdgeInsets.all(AppSpacing.lg),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.borderLight),
                ),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 28,
                      backgroundColor: AppColors.primary.withValues(alpha: 0.12),
                      child: Text(
                        name.isNotEmpty ? name.substring(0, 1) : '用',
                        style: const TextStyle(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w700,
                          fontSize: 22,
                        ),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            name,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                              color: AppColors.textPrimary,
                            ),
                          ),
                          if (user != null) ...[
                            const SizedBox(height: 6),
                            Text(
                              [
                                if (user.username.trim().isNotEmpty) user.username,
                                if (user.tenantCode.trim().isNotEmpty) user.tenantCode,
                              ].join(' · '),
                              style: const TextStyle(
                                fontSize: 13,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.xl),
              _MineActionButton(
                label: '修改密码',
                transparent: true,
                onPressed: () => _push(context, const ChangePasswordPage()),
              ),
              const SizedBox(height: 12),
              _MineActionButton(
                label: '设置IP',
                transparent: true,
                onPressed: () => _push(context, const ServerIpPage()),
              ),
              const SizedBox(height: 12),
              _MineActionButton(
                label: '关于',
                transparent: true,
                onPressed: () => _push(context, const AboutPage()),
              ),
              const SizedBox(height: 12),
              _MineActionButton(
                label: '退出登录',
                onPressed: () async {
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
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _push(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));
  }
}

class _MineActionButton extends StatelessWidget {
  const _MineActionButton({
    required this.label,
    required this.onPressed,
    this.transparent = false,
  });

  final String label;
  final VoidCallback onPressed;
  final bool transparent;

  @override
  Widget build(BuildContext context) {
    if (transparent) {
      return OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.textPrimary,
          backgroundColor: Colors.transparent,
          side: const BorderSide(color: AppColors.borderLight),
          padding: const EdgeInsets.symmetric(vertical: 14),
        ),
        child: Text(label),
      );
    }
    return FilledButton.tonal(
      onPressed: onPressed,
      style: FilledButton.styleFrom(
        foregroundColor: AppColors.danger,
        padding: const EdgeInsets.symmetric(vertical: 14),
      ),
      child: Text(label),
    );
  }
}
