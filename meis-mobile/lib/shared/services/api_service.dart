import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_constants.dart';
import '../../core/models/auth_user.dart';
import '../../core/storage/app_prefs.dart';

class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class ApiService {
  ApiService(this._prefs);

  final AppPrefs _prefs;
  Dio? _dio;
  String _baseUrl = '';

  String get baseUrl => _baseUrl;

  void configure(String baseUrl) {
    _baseUrl = baseUrl;
    _dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: AppConstants.connectTimeout,
        receiveTimeout: AppConstants.receiveTimeout,
        headers: {'Content-Type': 'application/json'},
      ),
    );
    _dio!.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final token = _prefs.token;
          final user = _prefs.user;
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          if (user != null) {
            options.headers['X-Tenant-Id'] = user.tenantId;
            options.headers['X-Tenant-Schema'] = user.schemaName;
            options.headers['X-User-Id'] = user.userId;
            if (user.permissions != null) {
              final p = user.permissions!;
              options.headers['X-Permissions'] = jsonEncode({
                'buttons': p['buttons'] ?? [],
                'dataScope': p['dataScope'] ?? 'self',
                'deptIds': p['deptIds'] ?? [],
                'warehouseIds': p['warehouseIds'] ?? [],
              });
            }
          }
          handler.next(options);
        },
      ),
    );
  }

  Dio get dio {
    if (_dio == null) {
      throw StateError('ApiService not configured. Complete server setup first.');
    }
    return _dio!;
  }

  Future<void> testConnection(String host, String port) async {
    final url = 'http://$host:$port${AppConstants.apiPrefix}${AppConstants.healthPath}';
    final client = Dio(
      BaseOptions(
        connectTimeout: AppConstants.connectTimeout,
        receiveTimeout: AppConstants.receiveTimeout,
      ),
    );
    try {
      final res = await client.get<dynamic>(url);
      final data = res.data;
      if (data is Map && data['code'] != null && data['code'] != 0) {
        throw ApiException(data['message']?.toString() ?? '服务返回异常');
      }
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e));
    }
  }

  Future<AuthUser> login({
    required String tenantCode,
    required String username,
    required String password,
  }) async {
    try {
      final res = await dio.post<Map<String, dynamic>>(
        AppConstants.loginPath,
        data: {
          'tenantCode': tenantCode,
          'username': username,
          'password': password,
        },
      );
      final body = res.data;
      if (body == null || body['code'] != 0) {
        throw ApiException(body?['message']?.toString() ?? '登录失败');
      }
      final data = body['data'] as Map<String, dynamic>;
      return AuthUser.fromJson(data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e));
    }
  }

  Future<List<dynamic>> getList(String path, {Map<String, dynamic>? query}) async {
    final data = await getData(path, query: query);
    if (data is List) return data;
    if (data is Map && data['records'] is List) return data['records'] as List<dynamic>;
    if (data is Map && data['list'] is List) return data['list'] as List<dynamic>;
    return [];
  }

  Future<Map<String, dynamic>> getPage(String path, {Map<String, dynamic>? query}) async {
    final data = await getData(path, query: query);
    if (data is Map<String, dynamic>) return data;
    return {'records': data is List ? data : [], 'total': 0};
  }

  Future<dynamic> getData(String path, {Map<String, dynamic>? query}) async {
    try {
      final res = await dio.get<Map<String, dynamic>>(path, queryParameters: query);
      return _unwrap(res.data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  Future<dynamic> postData(String path, [Map<String, dynamic>? data]) async {
    try {
      final res = await dio.post<Map<String, dynamic>>(path, data: data ?? {});
      return _unwrap(res.data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  Future<dynamic> putData(String path, Map<String, dynamic> data) async {
    try {
      final res = await dio.put<Map<String, dynamic>>(path, data: data);
      return _unwrap(res.data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  Future<dynamic> patchData(String path, Map<String, dynamic> data) async {
    try {
      final res = await dio.patch<Map<String, dynamic>>(path, data: data);
      return _unwrap(res.data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  Future<void> deleteData(String path) async {
    try {
      final res = await dio.delete<Map<String, dynamic>>(path);
      _unwrap(res.data);
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  Future<void> post(String path, Map<String, dynamic> data) async {
    await postData(path, data);
  }

  Future<String> uploadFile(String filePath, {String filename = 'photo.jpg'}) async {
    try {
      final form = FormData.fromMap({
        'file': await MultipartFile.fromFile(filePath, filename: filename),
      });
      final res = await dio.post<Map<String, dynamic>>(
        '/file/upload',
        data: form,
        options: Options(contentType: 'multipart/form-data'),
      );
      final data = _unwrap(res.data);
      if (data is Map && data['url'] != null) {
        return data['url'].toString();
      }
      throw ApiException('上传失败');
    } on DioException catch (e) {
      throw ApiException(_dioMessage(e), statusCode: e.response?.statusCode);
    }
  }

  dynamic _unwrap(Map<String, dynamic>? body) {
    if (body == null) throw ApiException('空响应');
    if (body['code'] != 0 && body['code'] != 200) {
      throw ApiException(body['message']?.toString() ?? '请求失败');
    }
    return body['data'];
  }

  String _dioMessage(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['message'] != null) {
      return data['message'].toString();
    }
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return '连接超时，请检查 IP 和端口';
      case DioExceptionType.connectionError:
        return '无法连接服务器，请确认手机和服务器在同一网络';
      default:
        return e.message ?? '网络请求失败';
    }
  }
}

final apiServiceProvider = Provider<ApiService>((ref) {
  return ApiService(ref.watch(appPrefsProvider));
});
