import '../constants/app_constants.dart';

enum SetupMode { lan, ethernet }

class ServerConfig {
  const ServerConfig({
    required this.mode,
    required this.host,
    required this.port,
    this.hospitalName,
    this.completed = false,
  });

  final SetupMode mode;
  final String host;
  final String port;
  final String? hospitalName;
  final bool completed;

  String get baseUrl => 'http://$host:$port${AppConstants.apiPrefix}';

  ServerConfig copyWith({
    SetupMode? mode,
    String? host,
    String? port,
    String? hospitalName,
    bool? completed,
  }) {
    return ServerConfig(
      mode: mode ?? this.mode,
      host: host ?? this.host,
      port: port ?? this.port,
      hospitalName: hospitalName ?? this.hospitalName,
      completed: completed ?? this.completed,
    );
  }

  static const empty = ServerConfig(
    mode: SetupMode.lan,
    host: '',
    port: AppConstants.defaultPort,
  );
}
