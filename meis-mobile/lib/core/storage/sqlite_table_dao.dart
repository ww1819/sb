/// 业务表 DAO 示例：盘点明细本地缓存可仿照本类扩展。
///
/// 真正业务表请在 [SqliteHelper.open] 的 `onCreate` / `onUpgrade` 中建表，
/// 不要把大批量主数据塞进 `_meis_kv`。
library;

import 'sqlite_helper.dart';

/// 通用「按 id 的行表」读写封装，减少业务页直接拼 SQL。
class SqliteTableDao {
  SqliteTableDao(this.db, this.table, {this.idColumn = 'id'});

  final SqliteHelper db;
  final String table;
  final String idColumn;

  Future<int> insert(Map<String, Object?> row) => db.insert(table, row);

  Future<void> insertAll(List<Map<String, Object?>> rows) =>
      db.insertAll(table, rows);

  Future<int> upsert(Map<String, Object?> row) => db.upsert(table, row);

  Future<Map<String, Object?>?> findById(Object id) {
    return db.queryFirst(
      table,
      where: '$idColumn = ?',
      whereArgs: [id],
    );
  }

  Future<List<Map<String, Object?>>> findAll({
    String? where,
    List<Object?>? whereArgs,
    String? orderBy,
    int? limit,
    int? offset,
  }) {
    return db.query(
      table,
      where: where,
      whereArgs: whereArgs,
      orderBy: orderBy,
      limit: limit,
      offset: offset,
    );
  }

  Future<int> updateById(Object id, Map<String, Object?> values) {
    return db.update(
      table,
      values,
      where: '$idColumn = ?',
      whereArgs: [id],
    );
  }

  Future<int> deleteById(Object id) {
    return db.delete(table, where: '$idColumn = ?', whereArgs: [id]);
  }

  Future<int> count({String? where, List<Object?>? whereArgs}) {
    return db.count(table, where: where, whereArgs: whereArgs);
  }

  Future<void> clear() => db.clearTable(table);
}
