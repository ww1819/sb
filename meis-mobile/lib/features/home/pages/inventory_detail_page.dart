import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'repair_scan_page.dart';

class InventoryDetailPage extends ConsumerStatefulWidget {
  const InventoryDetailPage({super.key, required this.checkId});

  final String checkId;

  @override
  ConsumerState<InventoryDetailPage> createState() => _InventoryDetailPageState();
}

class _InventoryDetailPageState extends ConsumerState<InventoryDetailPage>
    with SingleTickerProviderStateMixin {
  Map<String, dynamic>? master;
  List<Map<String, dynamic>> items = [];
  var loading = true;
  late final TabController tabs;

  static const statusLabel = {
    'planning': '计划中',
    'in_progress': '进行中',
    'completed': '已完成',
    'audited': '已审核',
  };

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 3, vsync: this);
    load();
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final data = await ref.read(apiServiceProvider).getData('/asset/inventory/${widget.checkId}');
      if (data is Map) {
        final map = Map<String, dynamic>.from(data);
        final raw = map['items'];
        final list = raw is List
            ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : <Map<String, dynamic>>[];
        setState(() {
          master = map;
          items = list;
        });
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  bool _isFound(Map<String, dynamic> row) {
    final v = row['is_found'];
    return v == true || v == 1 || v?.toString() == 'true';
  }

  bool _needReprint(Map<String, dynamic> row) {
    final v = row['need_reprint_label'];
    return v == true || v == 1 || v?.toString() == 'true';
  }

  List<Map<String, dynamic>> filtered(int tab) {
    if (tab == 0) return items.where((e) => !_isFound(e)).toList();
    if (tab == 1) return items.where(_isFound).toList();
    return items;
  }

  String _text(Map<String, dynamic>? row, String key) {
    final v = row?[key]?.toString().trim();
    if (v == null || v.isEmpty || v == 'null') return '—';
    return v;
  }

  Future<void> patchItem(Map<String, dynamic> row, Map<String, dynamic> body) async {
    final itemId = row['id']?.toString();
    if (itemId == null) return;
    try {
      await ref.read(apiServiceProvider).patchData(
            '/asset/inventory/${widget.checkId}/items/$itemId',
            body,
          );
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> scanAndMark() async {
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const RepairScanPage()),
    );
    if (code == null || code.isEmpty) return;
    try {
      await ref.read(apiServiceProvider).postData(
        '/asset/inventory/${widget.checkId}/scan',
        {'device_code': code},
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已盘到：$code')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Widget buildItemCard(Map<String, dynamic> row) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_text(row, 'device_code'), style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 4),
            Text('名称：${_text(row, 'device_name')}'),
            Text('账面位置：${_text(row, 'expected_location')}'),
            Text('实盘位置：${_text(row, 'actual_location')}'),
            const Divider(height: 16),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('已找到'),
              value: _isFound(row),
              onChanged: (v) => patchItem(row, {'is_found': v}),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('需补打条码'),
              value: _needReprint(row),
              onChanged: (v) => patchItem(row, {'need_reprint_label': v}),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = master?['check_name']?.toString() ?? '盘点明细';
    final status = master?['status']?.toString() ?? '';
    final statusText = statusLabel[status] ?? status;
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        bottom: TabBar(
          controller: tabs,
          tabs: [
            Tab(text: '未盘到(${filtered(0).length})'),
            Tab(text: '已盘到(${filtered(1).length})'),
            Tab(text: '全部(${items.length})'),
          ],
        ),
        actions: [
          IconButton(
            tooltip: '扫码盘点',
            onPressed: scanAndMark,
            icon: const Icon(Icons.qr_code_scanner),
          ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                if (statusText.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text('状态：$statusText · 单号：${_text(master, 'check_no')}'),
                    ),
                  ),
                Expanded(
                  child: TabBarView(
                    controller: tabs,
                    children: List.generate(3, (tab) {
                      final list = filtered(tab);
                      if (list.isEmpty) {
                        return const Center(child: Text('暂无明细'));
                      }
                      return RefreshIndicator(
                        onRefresh: load,
                        child: ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: list.length,
                          itemBuilder: (_, i) => buildItemCard(list[i]),
                        ),
                      );
                    }),
                  ),
                ),
              ],
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: scanAndMark,
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('扫码盘点'),
      ),
    );
  }
}
