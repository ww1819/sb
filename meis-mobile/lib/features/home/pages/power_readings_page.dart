import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/datetime_format.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';

/// MOB-PWR-02：监测记录（基站/标签共用）；默认近 1 小时、逆序、可筛可排序、分页
class PowerReadingsPage extends ConsumerStatefulWidget {
  const PowerReadingsPage({
    super.key,
    required this.title,
    required this.listPath,
    this.showStationCode = false,
  });

  final String title;
  final String listPath;
  final bool showStationCode;

  @override
  ConsumerState<PowerReadingsPage> createState() => _PowerReadingsPageState();
}

class _PowerReadingsPageState extends ConsumerState<PowerReadingsPage> {
  var sortOrder = 'desc';
  var page = 1;
  final size = 20;
  var total = 0;
  var loading = false;
  List<Map<String, dynamic>> rows = [];
  late DateTime from;
  late DateTime to;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    to = DateTime.now();
    from = to.subtract(const Duration(hours: 1));
    Future.microtask(_load);
  }

  String _fmt(DateTime d) => formatDateTime(d);

  Future<void> _pickFrom() async {
    final d = await showDatePicker(
      context: context,
      initialDate: from,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(from));
    if (t == null) return;
    setState(() => from = DateTime(d.year, d.month, d.day, t.hour, t.minute));
  }

  Future<void> _pickTo() async {
    final d = await showDatePicker(
      context: context,
      initialDate: to,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(to));
    if (t == null) return;
    setState(() => to = DateTime(d.year, d.month, d.day, t.hour, t.minute));
  }

  Future<void> _load() async {
    setState(() => loading = true);
    try {
      final data = await api.getPage(widget.listPath, query: {
        'page': page,
        'size': size,
        'readAtFrom': _fmt(from),
        'readAtTo': _fmt(to),
        'sortOrder': sortOrder,
      });
      final list = (data['records'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      setState(() {
        rows = list;
        total = (data['total'] is num) ? (data['total'] as num).toInt() : list.length;
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(authProvider);
    final maxPage = total == 0 ? 1 : ((total + size - 1) / size).ceil();
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(AppSpacing.pageH),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const MeisSectionLabel('筛选（默认近 1 小时，人工确认采集）'),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('开始'),
                  subtitle: Text(_fmt(from)),
                  trailing: const Icon(Icons.schedule),
                  onTap: _pickFrom,
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('结束'),
                  subtitle: Text(_fmt(to)),
                  trailing: const Icon(Icons.schedule),
                  onTap: _pickTo,
                ),
                Row(
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        value: sortOrder,
                        decoration: const InputDecoration(labelText: '排序'),
                        items: const [
                          DropdownMenuItem(value: 'desc', child: Text('读取时间↓')),
                          DropdownMenuItem(value: 'asc', child: Text('读取时间↑')),
                        ],
                        onChanged: (v) {
                          if (v != null) setState(() => sortOrder = v);
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: loading
                          ? null
                          : () {
                              page = 1;
                              _load();
                            },
                      child: const Text('查询'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : rows.isEmpty
                    ? const Center(child: Text('暂无读数', style: TextStyle(color: AppColors.textSecondary)))
                    : ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.pageH),
                        itemCount: rows.length,
                        itemBuilder: (_, i) {
                          final r = rows[i];
                          return MeisListCard(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '${r['tag_code'] ?? '—'} · ${r['current_ma'] ?? '—'} mA',
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  '设备：${r['device_code'] ?? '—'}'
                                  '${widget.showStationCode ? ' · 基站：${r['station_code'] ?? '—'}' : ''}',
                                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                                ),
                                Text(
                                  '读取 ${formatDisplayDateTime(r['read_at'])} · 入库 ${formatDisplayDateTime(r['created_at'])}',
                                  style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
          ),
          if (total > 0)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('共 $total 条 · $page / $maxPage'),
                  Row(
                    children: [
                      TextButton(
                        onPressed: page <= 1 || loading
                            ? null
                            : () {
                                page--;
                                _load();
                              },
                        child: const Text('上一页'),
                      ),
                      TextButton(
                        onPressed: page >= maxPage || loading
                            ? null
                            : () {
                                page++;
                                _load();
                              },
                        child: const Text('下一页'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
