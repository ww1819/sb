import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
// Android/iOS 需 import sqflite 以注册默认 databaseFactory；桌面再切 ffi。
// ignore: unnecessary_import
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

/// MEIS 移动端本地 SQLite 工具类：通用增删改查 / 事务 / 批量。
///
/// - Android / iOS：`sqflite`
/// - Windows / Linux：`sqflite_common_ffi`（桌面调试 / 工控机）
/// - 业务表由 [onCreate] / [onUpgrade] 注册；默认仅建 `_meis_kv` 键值表
class SqliteHelper {
  SqliteHelper._(this._db);

  static const dbFileName = 'meis_mobile.db';
  static const schemaVersion = 1;
  static const kvTable = '_meis_kv';

  final Database _db;

  Database get database => _db;

  /// 打开数据库（应用启动时调用一次）。
  static Future<SqliteHelper> open({
    String fileName = dbFileName,
    int version = schemaVersion,
    OnDatabaseCreateFn? onCreate,
    OnDatabaseVersionChangeFn? onUpgrade,
  }) async {
    await _ensureFactory();
    final dir = await getApplicationDocumentsDirectory();
    final path = p.join(dir.path, fileName);
    final db = await openDatabase(
      path,
      version: version,
      onCreate: (database, v) async {
        await _createCoreTables(database);
        if (onCreate != null) {
          await onCreate(database, v);
        }
      },
      onUpgrade: (database, oldVersion, newVersion) async {
        if (onUpgrade != null) {
          await onUpgrade(database, oldVersion, newVersion);
        }
      },
    );
    return SqliteHelper._(db);
  }

  static Future<void> _ensureFactory() async {
    if (kIsWeb) {
      throw UnsupportedError('Web 端请用 IndexedDB / hive，不使用 sqflite');
    }
    if (Platform.isWindows || Platform.isLinux || Platform.isMacOS) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }
  }

  static Future<void> _createCoreTables(Database db) async {
    await db.execute('''
CREATE TABLE IF NOT EXISTS $kvTable (
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT,
  updated_at TEXT NOT NULL
)
''');
  }

  Future<void> close() => _db.close();

  // ─── 通用 CRUD ───────────────────────────────────────────

  /// 插入一行，返回 rowId。
  Future<int> insert(
    String table,
    Map<String, Object?> values, {
    ConflictAlgorithm conflictAlgorithm = ConflictAlgorithm.abort,
  }) {
    return _db.insert(table, values, conflictAlgorithm: conflictAlgorithm);
  }

  /// 批量插入（同一事务）。
  Future<void> insertAll(
    String table,
    List<Map<String, Object?>> rows, {
    ConflictAlgorithm conflictAlgorithm = ConflictAlgorithm.abort,
  }) async {
    if (rows.isEmpty) return;
    final batch = _db.batch();
    for (final row in rows) {
      batch.insert(table, row, conflictAlgorithm: conflictAlgorithm);
    }
    await batch.commit(noResult: true);
  }

  /// 条件更新，返回受影响行数。
  Future<int> update(
    String table,
    Map<String, Object?> values, {
    String? where,
    List<Object?>? whereArgs,
    ConflictAlgorithm? conflictAlgorithm,
  }) {
    return _db.update(
      table,
      values,
      where: where,
      whereArgs: whereArgs,
      conflictAlgorithm: conflictAlgorithm,
    );
  }

  /// 按主键/唯一键 upsert（冲突则替换整行）。
  Future<int> upsert(String table, Map<String, Object?> values) {
    return insert(table, values, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  /// 条件删除，返回受影响行数。
  Future<int> delete(
    String table, {
    String? where,
    List<Object?>? whereArgs,
  }) {
    return _db.delete(table, where: where, whereArgs: whereArgs);
  }

  /// 查询多行。
  Future<List<Map<String, Object?>>> query(
    String table, {
    bool? distinct,
    List<String>? columns,
    String? where,
    List<Object?>? whereArgs,
    String? groupBy,
    String? having,
    String? orderBy,
    int? limit,
    int? offset,
  }) {
    return _db.query(
      table,
      distinct: distinct,
      columns: columns,
      where: where,
      whereArgs: whereArgs,
      groupBy: groupBy,
      having: having,
      orderBy: orderBy,
      limit: limit,
      offset: offset,
    );
  }

  /// 查询首行，无则 null。
  Future<Map<String, Object?>?> queryFirst(
    String table, {
    List<String>? columns,
    String? where,
    List<Object?>? whereArgs,
    String? orderBy,
  }) async {
    final rows = await query(
      table,
      columns: columns,
      where: where,
      whereArgs: whereArgs,
      orderBy: orderBy,
      limit: 1,
    );
    return rows.isEmpty ? null : rows.first;
  }

  /// 计数。
  Future<int> count(
    String table, {
    String? where,
    List<Object?>? whereArgs,
  }) async {
    final rows = await _db.rawQuery(
      'SELECT COUNT(*) AS c FROM $table'
      '${where == null ? '' : ' WHERE $where'}',
      whereArgs,
    );
    final v = rows.first['c'];
    if (v is int) return v;
    if (v is num) return v.toInt();
    return int.tryParse('$v') ?? 0;
  }

  Future<List<Map<String, Object?>>> rawQuery(
    String sql, [
    List<Object?>? arguments,
  ]) {
    return _db.rawQuery(sql, arguments);
  }

  Future<int> rawUpdate(String sql, [List<Object?>? arguments]) {
    return _db.rawUpdate(sql, arguments);
  }

  Future<void> execute(String sql, [List<Object?>? arguments]) {
    return _db.execute(sql, arguments);
  }

  Future<T> transaction<T>(Future<T> Function(Transaction txn) action) {
    return _db.transaction(action);
  }

  /// 清空表（保留表结构）。
  Future<void> clearTable(String table) => delete(table);

  // ─── 内置 KV（小配置/标记，非大批业务数据）────────────────

  Future<void> kvSet(String key, String? value) {
    return upsert(kvTable, {
      'key': key,
      'value': value,
      'updated_at': DateTime.now().toIso8601String(),
    });
  }

  Future<String?> kvGet(String key) async {
    final row = await queryFirst(
      kvTable,
      columns: ['value'],
      where: 'key = ?',
      whereArgs: [key],
    );
    final v = row?['value'];
    return v?.toString();
  }

  Future<void> kvRemove(String key) {
    return delete(kvTable, where: 'key = ?', whereArgs: [key]);
  }
}

/// 启动时 [SqliteHelper.open] 后 override；业务侧 `ref.read(sqliteHelperProvider)`。
final sqliteHelperProvider = Provider<SqliteHelper>((ref) {
  throw UnimplementedError('SqliteHelper must be overridden in main()');
});
