import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/widgets/app_snackbar.dart';
import '../../../shared/widgets/meis_brand_header.dart';
import '../providers/auth_provider.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _tenantCtrl = TextEditingController(text: AppConstants.defaultTenantCode);
  final _userCtrl = TextEditingController(text: 'admin');
  final _passCtrl = TextEditingController(text: 'admin123');

  @override
  void dispose() {
    _tenantCtrl.dispose();
    _userCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final ok = await ref.read(authProvider.notifier).login(
          tenantCode: _tenantCtrl.text.trim(),
          username: _userCtrl.text.trim(),
          password: _passCtrl.text,
        );
    if (!mounted) return;
    if (ok) {
      context.go('/home');
    } else {
      final err = ref.read(authProvider).error;
      showAppSnackBar(context, err ?? '登录失败', isError: true);
    }
  }

  Future<void> _reconfigure() async {
    await ref.read(authProvider.notifier).reconfigure();
    if (!mounted) return;
    context.go('/setup/mode');
  }

  @override
  Widget build(BuildContext context) {
    final auth = ref.watch(authProvider);

    return Scaffold(
      backgroundColor: AppColors.pageBg,
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppColors.pageBgTop, AppColors.pageBg, AppColors.pageBg],
            stops: [0, 0.4, 1],
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.xl,
              AppSpacing.xl,
              AppSpacing.xl,
              AppSpacing.xxl,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: AppSpacing.xl),
                const MeisBrandHeader(subtitle: '用户登录'),
                const SizedBox(height: AppSpacing.xxl),
                TextField(
                  controller: _tenantCtrl,
                  decoration: const InputDecoration(
                    labelText: '医院编码',
                    prefixIcon: Icon(Icons.business_outlined),
                  ),
                ),
                const SizedBox(height: AppSpacing.lg),
                TextField(
                  controller: _userCtrl,
                  decoration: const InputDecoration(
                    labelText: '用户名',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                ),
                const SizedBox(height: AppSpacing.lg),
                TextField(
                  controller: _passCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: '密码',
                    prefixIcon: Icon(Icons.lock_outline),
                  ),
                  onSubmitted: (_) => _login(),
                ),
                const SizedBox(height: AppSpacing.xl),
                FilledButton(
                  onPressed: auth.loading ? null : _login,
                  child: auth.loading
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('登录'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextButton(
                  onPressed: _reconfigure,
                  child: const Text('重新配置服务器连接'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
