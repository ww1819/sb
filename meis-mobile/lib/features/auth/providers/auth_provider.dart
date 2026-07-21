import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/models/auth_user.dart';
import '../../../core/storage/app_prefs.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/services/local_sync_service.dart';
import '../../setup/providers/setup_provider.dart';

class AuthState {
  const AuthState({
    this.loading = false,
    this.user,
    this.error,
  });

  final bool loading;
  final AuthUser? user;
  final String? error;

  AuthState copyWith({
    bool? loading,
    AuthUser? user,
    String? error,
    bool clearError = false,
    bool clearUser = false,
  }) {
    return AuthState(
      loading: loading ?? this.loading,
      user: clearUser ? null : (user ?? this.user),
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._prefs, this._api, this._ref) : super(const AuthState()) {
    _restore();
  }

  final AppPrefs _prefs;
  final ApiService _api;
  final Ref _ref;

  Future<void> _restore() async {
    final user = _prefs.user;
    if (user == null) return;
    final config = await _prefs.loadServerConfig();
    if (config.completed && config.host.isNotEmpty) {
      _api.configure(config.baseUrl);
      state = state.copyWith(user: user);
    }
  }

  Future<bool> login({
    required String tenantCode,
    required String username,
    required String password,
  }) async {
    state = state.copyWith(loading: true, clearError: true);
    try {
      final config = await _prefs.loadServerConfig();
      if (!config.completed || config.host.isEmpty) {
        throw ApiException('请先完成服务器连接设置');
      }
      _api.configure(config.baseUrl);
      final user = await _api.login(
        tenantCode: tenantCode,
        username: username,
        password: password,
      );
      await _prefs.saveAuth(user);
      try {
        await _ref.read(localSyncServiceProvider).onLogin(user);
      } catch (_) {}
      state = state.copyWith(loading: false, user: user);
      return true;
    } on ApiException catch (e) {
      state = state.copyWith(loading: false, error: e.message);
      return false;
    } catch (e) {
      state = state.copyWith(loading: false, error: '登录失败');
      return false;
    }
  }

  Future<void> logout() async {
    await _prefs.clearAuth();
    state = state.copyWith(clearUser: true);
  }

  Future<void> reconfigure() async {
    await _prefs.clearAuth();
    await _ref.read(setupProvider.notifier).resetSetup();
    state = state.copyWith(clearUser: true);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.watch(appPrefsProvider), ref.watch(apiServiceProvider), ref);
});
