import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/app_snackbar.dart';

class ChangePasswordPage extends ConsumerStatefulWidget {
  const ChangePasswordPage({super.key});

  @override
  ConsumerState<ChangePasswordPage> createState() => _ChangePasswordPageState();
}

class _ChangePasswordPageState extends ConsumerState<ChangePasswordPage> {
  final _oldCtrl = TextEditingController();
  final _newCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  var _obscure = true;
  var _submitting = false;

  @override
  void dispose() {
    _oldCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final oldPwd = _oldCtrl.text;
    final newPwd = _newCtrl.text.trim();
    final confirm = _confirmCtrl.text.trim();
    if (oldPwd.isEmpty) {
      showAppSnackBar(context, '请输入原密码', isError: true);
      return;
    }
    if (newPwd.length < 6) {
      showAppSnackBar(context, '新密码至少 6 位', isError: true);
      return;
    }
    if (newPwd != confirm) {
      showAppSnackBar(context, '两次输入的新密码不一致', isError: true);
      return;
    }
    if (oldPwd == newPwd) {
      showAppSnackBar(context, '新密码不能与原密码相同', isError: true);
      return;
    }

    setState(() => _submitting = true);
    try {
      await ref.read(apiServiceProvider).postData('/system/users/me/change-password', {
        'oldPassword': oldPwd,
        'newPassword': newPwd,
      });
      if (!mounted) return;
      showAppSnackBar(context, '密码已修改');
      Navigator.pop(context);
    } on ApiException catch (e) {
      if (mounted) showAppSnackBar(context, e.message, isError: true);
    } catch (_) {
      if (mounted) showAppSnackBar(context, '修改失败', isError: true);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('修改密码')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.pageH),
        children: [
          TextField(
            controller: _oldCtrl,
            obscureText: _obscure,
            decoration: const InputDecoration(
              labelText: '原密码',
              prefixIcon: Icon(Icons.lock_outline),
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: _newCtrl,
            obscureText: _obscure,
            decoration: const InputDecoration(
              labelText: '新密码',
              hintText: '至少 6 位',
              prefixIcon: Icon(Icons.lock),
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: _confirmCtrl,
            obscureText: _obscure,
            decoration: InputDecoration(
              labelText: '确认新密码',
              prefixIcon: const Icon(Icons.lock),
              suffixIcon: IconButton(
                onPressed: () => setState(() => _obscure = !_obscure),
                icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
            child: _submitting
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Text('确认修改'),
          ),
        ],
      ),
    );
  }
}
