import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

/// 标签补打一期：记流水 + 展示编码（蓝牙 SDK 后置，MOB.12）
class LabelReprintPage extends ConsumerStatefulWidget {
  const LabelReprintPage({super.key});

  @override
  ConsumerState<LabelReprintPage> createState() => _LabelReprintPageState();
}

class _LabelReprintPageState extends ConsumerState<LabelReprintPage> {
  List<Map<String, dynamic>> checks = [];
  List<Map<String, dynamic>> reprintItems = [];
  final Set<String> selected = {};
  String? checkId;
  String? checkNo;
  var loading = true;
  var printing = false;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    loadChecks();
  }

  Future<void> loadChecks() async {
    setState(() => loading = true);
    try {
      final page = await api.getPage('/asset/inventory/page', query: {'page': 1, 'size': 50});
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      setState(() {
        checks = raw is List
            ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : [];
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> selectCheck(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    setState(() {
      loading = true;
      checkId = id;
      checkNo = row['check_no']?.toString();
      selected.clear();
    });
    try {
      final list = await api.getList('/asset/inventory/$id/reprint-items');
      setState(() {
        reprintItems = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        for (final it in reprintItems) {
          final iid = it['id']?.toString();
          if (iid != null) selected.add(iid);
        }
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> printSelected() async {
    if (checkId == null || selected.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请勾选要补打的明细')));
      return;
    }
    setState(() => printing = true);
    try {
      final data = await api.postData('/asset/inventory/$checkId/label/print', {
        'item_ids': selected.toList(),
        'remark': 'app_reprint',
      });
      final n = data is Map ? data['printed'] : selected.length;
      if (!mounted) return;
      final codes = reprintItems
          .where((e) => selected.contains(e['id']?.toString()))
          .map((e) => e['device_code']?.toString() ?? '')
          .where((c) => c.isNotEmpty)
          .join('\n');
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text('已记打印流水（$n 张）'),
          content: SingleChildScrollView(
            child: Text(
              '蓝牙打印机对接待院方确认型号后接入。\n\n本次设备编码：\n$codes',
            ),
          ),
          actions: [
            FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('知道了')),
          ],
        ),
      );
      await selectCheck({'id': checkId, 'check_no': checkNo});
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => printing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(checkId == null ? '标签补打' : '补打 · ${checkNo ?? checkId}'),
        leading: checkId != null
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => setState(() {
                  checkId = null;
                  checkNo = null;
                  reprintItems = [];
                  selected.clear();
                }),
              )
            : null,
        actions: [
          if (checkId != null)
            TextButton(
              onPressed: printing || selected.isEmpty ? null : printSelected,
              child: printing
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text('确认补打(${selected.length})'),
            ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : checkId == null
              ? ListView(
                  padding: const EdgeInsets.all(12),
                  children: [
                    const Text('选择盘点单，查看需补打明细'),
                    const SizedBox(height: 8),
                    for (final c in checks)
                      Card(
                        child: ListTile(
                          title: Text(c['check_no']?.toString() ?? c['check_name']?.toString() ?? ''),
                          subtitle: Text('状态：${c['status'] ?? '—'} / 审核：${c['audit_status'] ?? '—'}'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => selectCheck(c),
                        ),
                      ),
                  ],
                )
              : reprintItems.isEmpty
                  ? const Center(child: Text('该单暂无需补打明细'))
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
                      itemCount: reprintItems.length,
                      itemBuilder: (_, i) {
                        final it = reprintItems[i];
                        final id = it['id']?.toString() ?? '';
                        return CheckboxListTile(
                          value: selected.contains(id),
                          onChanged: (v) {
                            setState(() {
                              if (v == true) {
                                selected.add(id);
                              } else {
                                selected.remove(id);
                              }
                            });
                          },
                          title: Text(it['device_name']?.toString() ?? ''),
                          subtitle: Text(
                            '${it['device_code'] ?? ''} · 已打 ${it['label_print_count'] ?? 0} 次',
                          ),
                        );
                      },
                    ),
    );
  }
}
