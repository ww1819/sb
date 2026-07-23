import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sqflite/sqflite.dart';

import '../../core/models/auth_user.dart';
import '../../core/storage/sqlite_helper.dart';
import 'api_service.dart';

/// MOB.8：权限缓存 / 台账增量 / 离线盘点
class LocalSyncService {
  LocalSyncService(this._db, this._api);

  final SqliteHelper _db;
  final ApiService _api;

  static const kvDeviceSyncAt = 'device_sync_at';
  static const kvPermissions = 'permissions_json';

  Future<void> onLogin(AuthUser user) async {
    if (user.permissions != null) {
      final json = jsonEncode(user.permissions);
      await _db.kvSet(kvPermissions, json);
      await _db.upsert('local_permission', {
        'user_id': user.userId,
        'permissions_json': json,
        'cached_at': DateTime.now().toIso8601String(),
      });
    }
    try {
      await syncDevices();
    } catch (_) {
      // 离线或失败不阻断登录
    }
  }

  Future<Map<String, dynamic>?> cachedPermissions() async {
    final raw = await _db.kvGet(kvPermissions);
    if (raw == null || raw.isEmpty) return null;
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      return null;
    }
  }

  Future<int> syncDevices({int maxPages = 20}) async {
    final after = await _db.kvGet(kvDeviceSyncAt);
    var page = 1;
    var totalSynced = 0;
    String? watermark = after;
    while (page <= maxPages) {
      final data = await _api.getData('/asset/device/sync', query: {
        if (after != null && after.isNotEmpty) 'updatedAfter': after,
        'page': page,
        'size': 500,
      });
      if (data is! Map) break;
      final records = data['records'];
      if (records is! List || records.isEmpty) {
        if (data['watermark'] != null) watermark = data['watermark'].toString();
        break;
      }
      await _db.transaction((txn) async {
        final batch = txn.batch();
        for (final e in records) {
          final m = Map<String, dynamic>.from(e as Map);
          batch.insert(
            'local_device',
            {
              'id': m['id']?.toString(),
              'device_code': m['device_code']?.toString(),
              'device_name': m['device_name']?.toString(),
              'specification': m['specification']?.toString(),
              'serial_number': m['serial_number']?.toString(),
              'dept_id': m['dept_id']?.toString(),
              'dept_name': m['dept_name']?.toString(),
              'device_status': m['device_status']?.toString(),
              'updated_at': m['updated_at']?.toString(),
            },
            conflictAlgorithm: ConflictAlgorithm.replace,
          );
        }
        await batch.commit(noResult: true);
      });
      totalSynced += records.length;
      watermark = data['watermark']?.toString() ?? watermark;
      final total = (data['total'] as num?)?.toInt() ?? 0;
      if (page * 500 >= total) break;
      page++;
    }
    if (watermark != null && watermark.isNotEmpty) {
      await _db.kvSet(kvDeviceSyncAt, watermark);
    } else {
      await _db.kvSet(kvDeviceSyncAt, DateTime.now().toUtc().toIso8601String());
    }
    return totalSynced;
  }

  Future<Map<String, dynamic>?> findLocalDeviceByCode(String code) async {
    final rows = await _db.query(
      'local_device',
      where: 'device_code = ?',
      whereArgs: [code.trim()],
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first;
  }

  Future<int> downloadUnauditedChecks() async {
    final page = await _api.getPage('/asset/inventory/page', query: {
      'page': 1,
      'size': 50,
      'audit_status': 'pending',
    });
    final raw = page['records'] ?? page['list'] ?? [];
    final list = raw is List ? raw : [];
    var n = 0;
    for (final e in list) {
      final id = (e as Map)['id']?.toString();
      if (id == null) continue;
      final detail = await _api.getData('/asset/inventory/$id');
      if (detail is! Map) continue;
      await saveCheckLocal(Map<String, dynamic>.from(detail));
      n++;
    }
    return n;
  }

  Future<void> saveCheckLocal(Map<String, dynamic> detail) async {
    final id = detail['id']?.toString();
    if (id == null) return;
    final items = detail['items'] is List ? detail['items'] as List : [];
    await _db.upsert('local_inventory_check', {
      'id': id,
      'check_no': detail['check_no']?.toString(),
      'check_name': detail['check_name']?.toString(),
      'status': detail['status']?.toString(),
      'audit_status': detail['audit_status']?.toString() ?? 'pending',
      'total_count': detail['total_count'] is num ? (detail['total_count'] as num).toInt() : items.length,
      'checked_count': detail['checked_count'] is num ? (detail['checked_count'] as num).toInt() : 0,
      'payload_json': jsonEncode(detail),
      'downloaded_at': DateTime.now().toIso8601String(),
    });
    for (final it in items) {
      final m = Map<String, dynamic>.from(it as Map);
      final itemId = m['id']?.toString();
      if (itemId == null) continue;
      final existing = await _db.queryFirst(
        'local_inventory_item',
        where: 'id = ?',
        whereArgs: [itemId],
      );
      if (existing != null && (existing['dirty'] == 1 || existing['dirty'] == true)) {
        continue; // 保留本地未回传修改
      }
      await _db.upsert('local_inventory_item', {
        'id': itemId,
        'check_id': id,
        'device_id': m['device_id']?.toString(),
        'device_code': m['device_code']?.toString(),
        'device_name': m['device_name']?.toString(),
        'is_found': _boolInt(m['is_found']),
        'need_reprint_label': _boolInt(m['need_reprint_label']),
        'actual_location': m['actual_location']?.toString(),
        'row_version': (m['row_version'] as num?)?.toInt() ?? 1,
        'dirty': 0,
        'payload_json': jsonEncode(m),
      });
    }
  }

  int _boolInt(dynamic v) => (v == true || v == 1 || v?.toString() == 'true') ? 1 : 0;

  Future<List<Map<String, Object?>>> listLocalChecks() {
    return _db.query('local_inventory_check', orderBy: 'downloaded_at DESC');
  }

  Future<Map<String, Object?>?> getLocalCheck(String id) {
    return _db.queryFirst('local_inventory_check', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<Map<String, Object?>>> listLocalItems(String checkId) {
    return _db.query('local_inventory_item', where: 'check_id = ?', whereArgs: [checkId], orderBy: 'device_code');
  }

  Future<void> patchLocalItem(String itemId, Map<String, dynamic> patch) async {
    final row = await _db.queryFirst('local_inventory_item', where: 'id = ?', whereArgs: [itemId]);
    if (row == null) return;
    final next = Map<String, Object?>.from(row);
    if (patch.containsKey('is_found')) next['is_found'] = _boolInt(patch['is_found']);
    if (patch.containsKey('need_reprint_label')) {
      next['need_reprint_label'] = _boolInt(patch['need_reprint_label']);
    }
    if (patch.containsKey('actual_location')) next['actual_location'] = patch['actual_location']?.toString();
    next['dirty'] = 1;
    await _db.upsert('local_inventory_item', next);
  }

  Future<Map<String, dynamic>> uploadDirty(String checkId) async {
    final dirty = await _db.query(
      'local_inventory_item',
      where: 'check_id = ? AND dirty = 1',
      whereArgs: [checkId],
    );
    if (dirty.isEmpty) return {'synced': 0, 'conflicts': []};
    final items = dirty
        .map((r) => {
              'id': r['id'],
              'is_found': r['is_found'] == 1,
              'need_reprint_label': r['need_reprint_label'] == 1,
              'actual_location': r['actual_location'],
              'row_version': r['row_version'] ?? 1,
            })
        .toList();
    final data = await _api.postData('/asset/inventory/$checkId/offline-sync', {'items': items});
    if (data is Map) {
      // 成功后清 dirty：以服务端详情为准重存
      try {
        final detail = await _api.getData('/asset/inventory/$checkId');
        if (detail is Map) await saveCheckLocal(Map<String, dynamic>.from(detail));
      } catch (_) {
        for (final r in dirty) {
          await _db.update(
            'local_inventory_item',
            {'dirty': 0},
            where: 'id = ?',
            whereArgs: [r['id']],
          );
        }
      }
      return Map<String, dynamic>.from(data);
    }
    return {'synced': items.length, 'conflicts': []};
  }
}

final localSyncServiceProvider = Provider<LocalSyncService>((ref) {
  return LocalSyncService(ref.watch(sqliteHelperProvider), ref.watch(apiServiceProvider));
});
