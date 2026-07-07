class AppConstants {
  AppConstants._();

  static const appName = 'MEIS';
  static const appSubtitle = '医院设备管理系统';

  static const defaultPort = '8080';
  static const defaultTenantCode = 'demo';

  static const connectTimeout = Duration(seconds: 5);
  static const receiveTimeout = Duration(seconds: 10);

  static const apiPrefix = '/api';
  static const healthPath = '/auth/health';
  static const loginPath = '/auth/login';
}
