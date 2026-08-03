// 移动端日期/时间统一格式（PLT-DT-01 / 约定包 §5.12）
// 日期：yyyy-MM-dd；时间：yyyy-MM-dd HH:mm:ss（24h）

String _pad2(int n) => n.toString().padLeft(2, '0');

/// DateTime → yyyy-MM-dd
String formatDate(DateTime d) =>
    '${d.year}-${_pad2(d.month)}-${_pad2(d.day)}';

/// DateTime → yyyy-MM-dd HH:mm:ss
String formatDateTime(DateTime d) =>
    '${formatDate(d)} ${_pad2(d.hour)}:${_pad2(d.minute)}:${_pad2(d.second)}';

/// 展示：日期 → yyyy-MM-dd；空为 —
String formatDisplayDate(dynamic value) {
  if (value == null) return '—';
  final s = value.toString().trim();
  if (s.isEmpty) return '—';
  final m = RegExp(r'^(\d{4}-\d{2}-\d{2})').firstMatch(s);
  if (m != null) return m.group(1)!;
  final d = DateTime.tryParse(s);
  if (d != null) return formatDate(d);
  return s;
}

/// 展示：时间 → yyyy-MM-dd HH:mm:ss；空为 —
String formatDisplayDateTime(dynamic value) {
  if (value == null) return '—';
  final s = value.toString().trim();
  if (s.isEmpty) return '—';
  if (RegExp(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}').hasMatch(s)) {
    return s.length >= 19 ? s.substring(0, 19) : s;
  }
  final iso = RegExp(r'^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})').firstMatch(s);
  if (iso != null) return '${iso.group(1)} ${iso.group(2)}';
  final d = DateTime.tryParse(s);
  if (d != null) return formatDateTime(d);
  return s;
}

/// 入参：仅日期（空串表示无）
String toDateParam(dynamic value) {
  final s = formatDisplayDate(value);
  return s == '—' ? '' : s;
}

/// 入参：日期时间（空格分隔、无 T）
String toDateTimeParam(dynamic value) {
  final s = formatDisplayDateTime(value);
  return s == '—' ? '' : s;
}

DateTime? tryParseDate(dynamic value) {
  if (value == null) return null;
  final s = value.toString().trim();
  if (s.isEmpty) return null;
  if (s.length >= 10) {
    final d = DateTime.tryParse(s.substring(0, 10));
    if (d != null) return d;
  }
  return DateTime.tryParse(s);
}
