import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/models/server_config.dart';
import '../../../core/storage/app_prefs.dart';
import '../../../shared/services/api_service.dart';

class SetupState {
  const SetupState({
    this.config = ServerConfig.empty,
    this.testing = false,
    this.testPassed = false,
    this.error,
  });

  final ServerConfig config;
  final bool testing;
  final bool testPassed;
  final String? error;

  SetupState copyWith({
    ServerConfig? config,
    bool? testing,
    bool? testPassed,
    String? error,
    bool clearError = false,
  }) {
    return SetupState(
      config: config ?? this.config,
      testing: testing ?? this.testing,
      testPassed: testPassed ?? this.testPassed,
      error: clearError ? null : (error ?? this.error),
    );
  }
}

class SetupNotifier extends StateNotifier<SetupState> {
  SetupNotifier(this._prefs, this._api) : super(const SetupState()) {
    Future.microtask(_load);
  }

  final AppPrefs _prefs;
  final ApiService _api;

  Future<void> _load() async {
    final config = await _prefs.loadServerConfig();
    state = state.copyWith(config: config, testPassed: config.completed);
  }

  void setMode(SetupMode mode) {
    state = state.copyWith(
      config: state.config.copyWith(mode: mode),
      testPassed: false,
      clearError: true,
    );
  }

  void updateHost(String host) {
    state = state.copyWith(
      config: state.config.copyWith(host: host.trim()),
      testPassed: false,
      clearError: true,
    );
  }

  void updatePort(String port) {
    state = state.copyWith(
      config: state.config.copyWith(port: port.trim()),
      testPassed: false,
      clearError: true,
    );
  }

  void updateHospitalName(String name) {
    state = state.copyWith(
      config: state.config.copyWith(hospitalName: name.trim()),
      clearError: true,
    );
  }

  Future<bool> testConnection() async {
    final host = state.config.host;
    final port = state.config.port;
    if (host.isEmpty) {
      state = state.copyWith(error: '请输入服务器 IP 地址');
      return false;
    }
    if (port.isEmpty) {
      state = state.copyWith(error: '请输入端口号');
      return false;
    }

    state = state.copyWith(testing: true, clearError: true);
    try {
      await _api.testConnection(host, port);
      state = state.copyWith(testing: false, testPassed: true);
      return true;
    } on ApiException catch (e) {
      state = state.copyWith(testing: false, testPassed: false, error: e.message);
      return false;
    } catch (e) {
      state = state.copyWith(testing: false, testPassed: false, error: '连接失败');
      return false;
    }
  }

  Future<void> completeSetup() async {
    if (!state.testPassed && state.config.mode == SetupMode.lan) {
      state = state.copyWith(error: '请先测试连接成功');
      return;
    }
    final completed = state.config.copyWith(completed: true);
    await _prefs.saveServerConfig(completed);
    _api.configure(completed.baseUrl);
    state = state.copyWith(config: completed, testPassed: true);
  }

  Future<void> saveEthernetPlaceholder() async {
    final name = state.config.hospitalName?.trim() ?? '';
    if (name.isEmpty) {
      state = state.copyWith(error: '请输入医院全称');
      return;
    }
    await _prefs.saveServerConfig(
      state.config.copyWith(hospitalName: name, completed: false),
    );
  }

  Future<void> resetSetup() async {
    await _prefs.clearSetup();
    state = const SetupState();
  }
}

final setupProvider = StateNotifierProvider<SetupNotifier, SetupState>((ref) {
  return SetupNotifier(ref.watch(appPrefsProvider), ref.watch(apiServiceProvider));
});
