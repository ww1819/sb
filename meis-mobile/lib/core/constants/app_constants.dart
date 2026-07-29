class AppConstants {
  AppConstants._();

  static const appName = 'MEIS';
  static const appSubtitle = '医院设备管理系统';

  /// Nginx 对外入口端口（反代到 Gateway）。直连网关时可手动改为 8080。
  static const defaultPort = '5174';
  static const defaultTenantCode = 'demo';

  static const connectTimeout = Duration(seconds: 5);
  static const receiveTimeout = Duration(seconds: 10);

  static const apiPrefix = '/api';
  static const healthPath = '/auth/health';
  static const loginPath = '/auth/login';
}
