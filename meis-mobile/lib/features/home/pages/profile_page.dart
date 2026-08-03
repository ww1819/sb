import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../auth/providers/auth_provider.dart';

/// 个人资料（微信式列表；MOB-UI-02）
class ProfilePage extends ConsumerStatefulWidget {
  const ProfilePage({super.key});

  @override
  ConsumerState<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends ConsumerState<ProfilePage> {
  Map<String, dynamic>? profile;
  var loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final data = await ref.read(apiServiceProvider).getData('/system/users/me/profile');
      if (!mounted) return;
      setState(() {
        profile = data is Map ? Map<String, dynamic>.from(data) : null;
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => error = e.message);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String _text(Object? v) {
    final s = v?.toString().trim();
    if (s == null || s.isEmpty || s == 'null') return '未设置';
    return s;
  }

  String _genderLabel(Object? v) {
    final s = v?.toString().trim().toLowerCase() ?? '';
    return switch (s) {
      'male' || 'm' || '男' => '男',
      'female' || 'f' || '女' => '女',
      'unknown' || '保密' => '保密',
      '' => '未设置',
      _ => s,
    };
  }

  String _maskPhone(Object? v) {
    final s = v?.toString().trim() ?? '';
    if (s.isEmpty) return '未设置';
    if (s.length >= 7) {
      return '${s.substring(0, 3)}${'*' * (s.length - 5)}${s.substring(s.length - 2)}';
    }
    return s;
  }

  String get _displayName {
    final auth = ref.read(authProvider).user;
    final fromApi = profile?['real_name']?.toString().trim();
    if (fromApi != null && fromApi.isNotEmpty) return fromApi;
    if (auth?.realName?.trim().isNotEmpty == true) return auth!.realName!;
    return auth?.username ?? '用户';
  }

  String get _username {
    final fromApi = profile?['username']?.toString().trim();
    if (fromApi != null && fromApi.isNotEmpty) return fromApi;
    return ref.read(authProvider).user?.username ?? '—';
  }

  String get _tenantCode {
    return ref.read(authProvider).user?.tenantCode ?? '—';
  }

  String? get _avatarUrl {
    final u = profile?['avatar_url']?.toString().trim();
    if (u == null || u.isEmpty) return null;
    return u;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.pageBg,
      appBar: AppBar(
        title: const Text('个人资料'),
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : error != null && profile == null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(error!, style: const TextStyle(color: AppColors.danger)),
                      const SizedBox(height: 12),
                      TextButton(onPressed: load, child: const Text('重试')),
                    ],
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.only(top: 10, bottom: AppSpacing.xxl),
                  children: [
                    _ProfileGroup(
                      children: [
                        _ProfileTile(
                          label: '头像',
                          trailing: _AvatarThumb(name: _displayName, url: _avatarUrl),
                        ),
                        _ProfileTile(label: '账号', value: _username),
                        _ProfileTile(label: '租户', value: _tenantCode),
                        _ProfileTile(label: '名字', value: _displayName),
                        _ProfileTile(label: '性别', value: _genderLabel(profile?['gender'])),
                        _ProfileTile(label: '地区', value: _text(profile?['region'])),
                        _ProfileTile(label: '手机号', value: _maskPhone(profile?['phone'])),
                        _ProfileTile(
                          label: '微信号',
                          value: _text(profile?['wechat_id']),
                          showDivider: false,
                        ),
                      ],
                    ),
                  ],
                ),
    );
  }
}

class _AvatarThumb extends StatelessWidget {
  const _AvatarThumb({required this.name, this.url});

  final String name;
  final String? url;

  @override
  Widget build(BuildContext context) {
    final letter = name.isNotEmpty ? name.substring(0, 1) : '用';
    return ClipRRect(
      borderRadius: BorderRadius.circular(6),
      child: SizedBox(
        width: 56,
        height: 56,
        child: url != null
            ? Image.network(
                url!,
                fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => _letterBox(letter),
              )
            : _letterBox(letter),
      ),
    );
  }

  Widget _letterBox(String letter) {
    return ColoredBox(
      color: AppColors.primary.withValues(alpha: 0.12),
      child: Center(
        child: Text(
          letter,
          style: const TextStyle(
            color: AppColors.primary,
            fontWeight: FontWeight.w700,
            fontSize: 22,
          ),
        ),
      ),
    );
  }
}

class _ProfileGroup extends StatelessWidget {
  const _ProfileGroup({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.white,
      child: Column(children: children),
    );
  }
}

class _ProfileTile extends StatelessWidget {
  const _ProfileTile({
    required this.label,
    this.value,
    this.trailing,
    this.showDivider = true,
  });

  final String label;
  final String? value;
  final Widget? trailing;
  final bool showDivider;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SizedBox(
          height: trailing != null ? 72 : 54,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Text(
                  label,
                  style: const TextStyle(fontSize: 16, color: AppColors.textPrimary),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: trailing ??
                        Text(
                          value ?? '未设置',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 15,
                            color: (value == null || value == '未设置')
                                ? AppColors.textMuted
                                : AppColors.textSecondary,
                          ),
                        ),
                  ),
                ),
                const SizedBox(width: 4),
                Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.textSecondary.withValues(alpha: 0.45),
                ),
              ],
            ),
          ),
        ),
        if (showDivider)
          const Padding(
            padding: EdgeInsets.only(left: 16),
            child: Divider(height: 1, thickness: 0.5, color: AppColors.borderLight),
          ),
      ],
    );
  }
}
