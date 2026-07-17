import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'inventory_detail_page.dart';

class InventoryPage extends ConsumerStatefulWidget {
  const InventoryPage({super.key});

  @override
  ConsumerState<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends ConsumerState<InventoryPage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  static const statusLabel = {
    'planning': '计划中',
    'in_progress': '进行中',
    'completed': '已完成',
    'audited': '已审核',
  };

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final page = await ref.read(apiServiceProvider).getPage('/asset/inventory/page', query: {
        'page': 1,
        'size': 50,
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      final list = raw is List
          ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];
      setState(() => items = list);
    } catch (_) {
      setState(() => items = []);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('加载盘点单失败')),
        );
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String _text(Map<String, dynamic> row, String key) {
    final v = row[key]?.toString().trim();
    if (v == null || v.isEmpty || v == 'null') return '—';
    return v;
  }

  Future<void> openDetail(String id) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => InventoryDetailPage(checkId: id)),
    );
    load();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('盘点任务')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: items.isEmpty
                  ? ListView(
                      children: const [
                        SizedBox(height: 120),
                        Center(child: Text('暂无盘点单')),
                      ],
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.all(12),
                      itemCount: items.length,
                      itemBuilder: (_, i) {
                        final row = items[i];
                        final status = row['status']?.toString() ?? '';
                        final label = statusLabel[status] ?? status;
                        return Card(
                          margin: const EdgeInsets.only(bottom: 10),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: () {
                              final id = row['id']?.toString();
                              if (id != null) openDetail(id);
                            },
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          _text(row, 'check_name'),
                                          style: const TextStyle(fontWeight: FontWeight.w600),
                                        ),
                                      ),
                                      Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: scheme.primaryContainer.withValues(alpha: 0.55),
                                          borderRadius: BorderRadius.circular(999),
                                        ),
                                        child: Text(label, style: TextStyle(fontSize: 12, color: scheme.onPrimaryContainer)),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 8),
                                  Text('单号：${_text(row, 'check_no')}'),
                                  Text('应盘/已盘：${_text(row, 'total_count')} / ${_text(row, 'checked_count')}'),
                                ],
                              ),
                            ),
                          ),
                        );
                      },
                    ),
            ),
    );
  }
}
