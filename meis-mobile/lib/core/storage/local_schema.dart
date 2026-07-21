import 'package:sqflite/sqflite.dart';

/// MOB.8 本地业务表（schema v2）
Future<void> ensureMob8Tables(Database db) async {
  await db.execute('''
CREATE TABLE IF NOT EXISTS local_permission (
  user_id TEXT PRIMARY KEY NOT NULL,
  permissions_json TEXT NOT NULL,
  cached_at TEXT NOT NULL
)
''');
  await db.execute('''
CREATE TABLE IF NOT EXISTS local_device (
  id TEXT PRIMARY KEY NOT NULL,
  device_code TEXT,
  device_name TEXT,
  specification TEXT,
  serial_number TEXT,
  dept_id TEXT,
  dept_name TEXT,
  device_status TEXT,
  updated_at TEXT
)
''');
  await db.execute('''
CREATE INDEX IF NOT EXISTS idx_local_device_code ON local_device(device_code)
''');
  await db.execute('''
CREATE TABLE IF NOT EXISTS local_inventory_check (
  id TEXT PRIMARY KEY NOT NULL,
  check_no TEXT,
  check_name TEXT,
  status TEXT,
  audit_status TEXT,
  total_count INTEGER,
  checked_count INTEGER,
  payload_json TEXT,
  downloaded_at TEXT NOT NULL
)
''');
  await db.execute('''
CREATE TABLE IF NOT EXISTS local_inventory_item (
  id TEXT PRIMARY KEY NOT NULL,
  check_id TEXT NOT NULL,
  device_id TEXT,
  device_code TEXT,
  device_name TEXT,
  is_found INTEGER,
  need_reprint_label INTEGER,
  actual_location TEXT,
  row_version INTEGER DEFAULT 1,
  dirty INTEGER NOT NULL DEFAULT 0,
  payload_json TEXT
)
''');
  await db.execute('''
CREATE INDEX IF NOT EXISTS idx_local_inv_item_check ON local_inventory_item(check_id)
''');
}

/// MOB.12 报修本地草稿（schema v3）
Future<void> ensureMob12Tables(Database db) async {
  await db.execute('''
CREATE TABLE IF NOT EXISTS local_repair_draft (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL,
  device_id TEXT,
  device_code TEXT,
  device_name TEXT,
  payload_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
)
''');
  await db.execute('''
CREATE INDEX IF NOT EXISTS idx_local_repair_user ON local_repair_draft(user_id)
''');
}
