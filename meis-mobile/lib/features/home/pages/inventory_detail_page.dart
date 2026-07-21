import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import '../../../shared/services/local_sync_service.dart';
import 'repair_scan_page.dart';

class InventoryDetailPage extends ConsumerStatefulWidget {
  const InventoryDetailPage({
    super.key,
    required this.checkId,
    this.preferLocal = false,
  });

  final String checkId;
  final bool preferLocal;

  @override
  ConsumerState<InventoryDetailPage> createState() => _InventoryDetailPageState();
}

class _InventoryDetailPageState extends ConsumerState<InventoryDetailPage>
    with SingleTickerProviderStateMixin {
  Map<String, dynamic>? master;
  List<Map<String, dynamic>> items = [];
  var loading = true;
  var localMode = false;
  late final TabController tabs;

  static const statusLabel = {
    'planning': '计划中',
    'in_progress': '进行中',
    'completed': '已完成',
    'pending': '未审核',
    'approved': '已审核',
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
    final sync = ref.read(localSyncServiceProvider);
    try {
      if (widget.preferLocal) {
        await _loadLocal(sync);
      } else {
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
            localMode = false;
          });
          // 后台刷新本地缓存（不覆盖 dirty）
          try {
            await sync.saveCheckLocal(map);
          } catch (_) {}
        }
      }
    } on ApiException catch (_) {
      await _loadLocal(sync);
    } catch (_) {
      await _loadLocal(sync);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _loadLocal(LocalSyncService sync) async {
    final check = await sync.getLocalCheck(widget.checkId);
    final localItems = await sync.listLocalItems(widget.checkId);
    if (check == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('本地无此盘点单')));
      }
      setState(() {
        master = null;
        items = [];
        localMode = true;
      });
      return;
    }
    setState(() {
      master = {
        'id': check['id'],
        'check_no': check['check_no'],
        'check_name': check['check_name'],
        'status': check['status'],
        'audit_status': check['audit_status'],
      };
      items = localItems
          .map((e) => {
                'id': e['id'],
                'device_id': e['device_id'],
                'device_code': e['device_code'],
                'device_name': e['device_name'],
                'is_found': e['is_found'] == 1,
                'need_reprint_label': e['need_reprint_label'] == 1,
                'actual_location': e['actual_location'],
                'row_version': e['row_version'],
                'dirty': e['dirty'] == 1,
                'expected_location': null,
              })
          .toList();
      localMode = true;
    });
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
    final sync = ref.read(localSyncServiceProvider);
    if (localMode) {
      await sync.patchLocalItem(itemId, body);
      await load();
      return;
    }
    try {
      final payload = Map<String, dynamic>.from(body);
      if (row['row_version'] != null) payload['row_version'] = row['row_version'];
      await ref.read(apiServiceProvider).patchData(
            '/asset/inventory/${widget.checkId}/items/$itemId',
            payload,
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
    if (localMode) {
      final match = items.where((e) => e['device_code']?.toString() == code.trim()).toList();
      if (match.isEmpty) {
        // 尝试本地台账确认编码存在
        final dev = await ref.read(localSyncServiceProvider).findLocalDeviceByCode(code);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(dev == null ? '本地清单无此设备：$code' : '该设备不在本盘点单明细中'),
          ));
        }
        return;
      }
      await patchItem(match.first, {'is_found': true});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已盘到（本地）：$code')));
      }
      return;
    }
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

  Future<void> upload() async {
    try {
      final r = await ref.read(localSyncServiceProvider).uploadDirty(widget.checkId);
      final synced = r['synced'] ?? 0;
      final conflicts = r['conflicts'] is List ? (r['conflicts'] as List).length : 0;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已回传 $synced 条${conflicts > 0 ? '，冲突 $conflicts 条' : ''}')),
        );
      }
      setState(() => localMode = false);
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Widget buildItemCard(Map<String, dynamic> row) {
    final dirty = row['dirty'] == true || row['dirty'] == 1;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(_text(row, 'device_code'), style: const TextStyle(fontWeight: FontWeight.w600)),
                ),
                if (dirty)
                  const Text('未同步', style: TextStyle(fontSize: 12, color: Colors.orange)),
              ],
            ),
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
    final audit = master?['audit_status']?.toString() ?? '';
    final statusText = '${statusLabel[status] ?? status} / ${statusLabel[audit] ?? audit}';
    return Scaffold(
      appBar: AppBar(
        title: Text(localMode ? '$title（离线）' : title),
        bottom: TabBar(
          controller: tabs,
          tabs: [
            Tab(text: '未盘到(${filtered(0).length})'),
            Tab(text: '已盘到(${filtered(1).length})'),
            Tab(text: '全部(${items.length})'),
          ],
        ),
        actions: [
          if (localMode)
            TextButton(onPressed: upload, child: const Text('回传')),
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
