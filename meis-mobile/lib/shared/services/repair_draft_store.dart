import 'dart:convert';
import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/storage/sqlite_helper.dart';

/// App 报修草稿本地暂存（MOB.12 / BACKLOG-MOB-09）
class RepairDraftStore {
  RepairDraftStore(this._db);

  final SqliteHelper _db;
  static const _table = 'local_repair_draft';

  String _newId() =>
      'ld_${DateTime.now().millisecondsSinceEpoch}_${Random().nextInt(1 << 20)}';

  Future<List<Map<String, dynamic>>> listByUser(String userId) async {
    final rows = await _db.query(
      _table,
      where: 'user_id = ?',
      whereArgs: [userId],
      orderBy: 'updated_at DESC',
    );
    return rows.map((r) {
      final m = Map<String, dynamic>.from(r);
      final raw = m['payload_json']?.toString();
      if (raw != null && raw.isNotEmpty) {
        try {
          m['payload'] = jsonDecode(raw);
        } catch (_) {
          m['payload'] = <String, dynamic>{};
        }
      }
      return m;
    }).toList();
  }

  Future<Map<String, dynamic>?> get(String id) async {
    final rows = await _db.query(_table, where: 'id = ?', whereArgs: [id], limit: 1);
    if (rows.isEmpty) return null;
    final m = Map<String, dynamic>.from(rows.first);
    final raw = m['payload_json']?.toString();
    if (raw != null && raw.isNotEmpty) {
      try {
        m['payload'] = jsonDecode(raw);
      } catch (_) {
        m['payload'] = <String, dynamic>{};
      }
    }
    return m;
  }

  Future<String> upsert({
    String? id,
    required String userId,
    String? deviceId,
    String? deviceCode,
    String? deviceName,
    required Map<String, dynamic> payload,
  }) async {
    final draftId = id ?? _newId();
    final now = DateTime.now().toIso8601String();
    final row = <String, Object?>{
      'id': draftId,
      'user_id': userId,
      'device_id': deviceId,
      'device_code': deviceCode,
      'device_name': deviceName,
      'payload_json': jsonEncode(payload),
      'updated_at': now,
    };
    await _db.upsert(_table, row);
    return draftId;
  }

  Future<void> delete(String id) async {
    await _db.delete(_table, where: 'id = ?', whereArgs: [id]);
  }
}

final repairDraftStoreProvider = Provider<RepairDraftStore>((ref) {
  return RepairDraftStore(ref.watch(sqliteHelperProvider));
});
