import 'package:flutter/material.dart';

import '../../features/home/pages/repair_scan_page.dart';

/// 统一打开扫码页（MOB-SCAN-04）。
/// 全部扫码入口须走此方法，禁止页面内再直接 `MobileScanner`。
Future<String?> openBarcodeScanner(BuildContext context) async {
  if (!context.mounted) return null;
  final code = await Navigator.push<String>(
    context,
    MaterialPageRoute(builder: (_) => const RepairScanPage()),
  );
  final t = code?.trim() ?? '';
  return t.isEmpty ? null : t;
}
