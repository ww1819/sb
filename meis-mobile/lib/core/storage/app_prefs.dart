import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/auth_user.dart';
import '../models/server_config.dart';

class PrefsKeys {
  PrefsKeys._();

  static const setupCompleted = 'setup_completed';
  static const setupMode = 'setup_mode';
  static const serverHost = 'server_host';
  static const serverPort = 'server_port';
  static const hospitalName = 'hospital_name';
  static const token = 'token';
  static const user = 'user';
}

class AppPrefs {
  AppPrefs(this._prefs);

  final SharedPreferences _prefs;

  Future<bool> isSetupCompleted() async =>
      _prefs.getBool(PrefsKeys.setupCompleted) ?? false;

  Future<ServerConfig> loadServerConfig() async {
    final modeRaw = _prefs.getString(PrefsKeys.setupMode) ?? 'lan';
    return ServerConfig(
      mode: modeRaw == 'ethernet' ? SetupMode.ethernet : SetupMode.lan,
      host: _prefs.getString(PrefsKeys.serverHost) ?? '',
      port: _prefs.getString(PrefsKeys.serverPort) ?? '8080',
      hospitalName: _prefs.getString(PrefsKeys.hospitalName),
      completed: _prefs.getBool(PrefsKeys.setupCompleted) ?? false,
    );
  }

  Future<void> saveServerConfig(ServerConfig config) async {
    await _prefs.setString(
      PrefsKeys.setupMode,
      config.mode == SetupMode.ethernet ? 'ethernet' : 'lan',
    );
    await _prefs.setString(PrefsKeys.serverHost, config.host);
    await _prefs.setString(PrefsKeys.serverPort, config.port);
    if (config.hospitalName != null) {
      await _prefs.setString(PrefsKeys.hospitalName, config.hospitalName!);
    }
    await _prefs.setBool(PrefsKeys.setupCompleted, config.completed);
  }

  Future<void> clearSetup() async {
    await _prefs.remove(PrefsKeys.setupCompleted);
    await _prefs.remove(PrefsKeys.setupMode);
    await _prefs.remove(PrefsKeys.serverHost);
    await _prefs.remove(PrefsKeys.serverPort);
    await _prefs.remove(PrefsKeys.hospitalName);
  }

  String? get token => _prefs.getString(PrefsKeys.token);

  AuthUser? get user {
    final raw = _prefs.getString(PrefsKeys.user);
    if (raw == null || raw.isEmpty) return null;
    return AuthUser.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  bool get isLoggedIn => token != null && token!.isNotEmpty && user != null;

  Future<void> saveAuth(AuthUser authUser) async {
    await _prefs.setString(PrefsKeys.token, authUser.token);
    await _prefs.setString(PrefsKeys.user, jsonEncode(authUser.toJson()));
  }

  Future<void> clearAuth() async {
    await _prefs.remove(PrefsKeys.token);
    await _prefs.remove(PrefsKeys.user);
  }
}

final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError('SharedPreferences must be overridden in main()');
});

final appPrefsProvider = Provider<AppPrefs>((ref) {
  return AppPrefs(ref.watch(sharedPreferencesProvider));
});
