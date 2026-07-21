import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

/// 计量执行明细填写（一期：结果状态 + 证书号）
class MetrologyExecDetailPage extends ConsumerStatefulWidget {
  const MetrologyExecDetailPage({
    super.key,
    required this.executionId,
    required this.itemId,
  });

  final String executionId;
  final String itemId;

  @override
  ConsumerState<MetrologyExecDetailPage> createState() => _MetrologyExecDetailPageState();
}

class _MetrologyExecDetailPageState extends ConsumerState<MetrologyExecDetailPage> {
  Map<String, dynamic>? exec;
  Map<String, dynamic>? item;
  List<_MetroResult> results = [];
  final certCtrl = TextEditingController();
  final remarkCtrl = TextEditingController();
  String overall = 'pass';
  var loading = true;
  var saving = false;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    certCtrl.dispose();
    remarkCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/metrology/execution/${widget.executionId}');
      if (data is! Map) throw ApiException('执行单无效');
      final map = Map<String, dynamic>.from(data);
      final items = map['items'] is List ? map['items'] as List : [];
      Map<String, dynamic>? found;
      for (final it in items) {
        if (it is Map && it['id']?.toString() == widget.itemId) {
          found = Map<String, dynamic>.from(it);
          break;
        }
      }
      if (found == null) throw ApiException('明细不存在');
      final itemMap = found;
      final rawResults = itemMap['results'] is List ? itemMap['results'] as List : [];
      setState(() {
        exec = map;
        item = itemMap;
        certCtrl.text = itemMap['certificate_no']?.toString() ?? '';
        remarkCtrl.text = itemMap['remark']?.toString() ?? '';
        overall = itemMap['overall_result']?.toString() ?? 'pass';
        results = rawResults.map((e) {
          final m = Map<String, dynamic>.from(e as Map);
          return _MetroResult(
            id: m['id']?.toString() ?? '',
            name: m['item_name']?.toString() ?? '检定项',
            status: m['result_status']?.toString() ?? 'pending',
            value: m['result_value']?.toString() ?? '',
            remark: m['remark']?.toString() ?? '',
          );
        }).toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  bool get editable {
    final st = exec?['status']?.toString();
    final ist = item?['status']?.toString();
    return (st == 'draft' || st == 'in_progress' || st == 'pending') && ist != 'completed';
  }

  Future<void> complete() async {
    if (!editable) return;
    setState(() => saving = true);
    try {
      final st = exec?['status']?.toString();
      if (st == 'draft' || st == 'pending') {
        await api.postData('/metrology/execution/${widget.executionId}/start', {});
      }
      await api.postData('/metrology/execution/item/${widget.itemId}/complete', {
        'overall_result': overall,
        'certificate_no': certCtrl.text.trim().isEmpty ? null : certCtrl.text.trim(),
        'remark': remarkCtrl.text.trim().isEmpty ? null : remarkCtrl.text.trim(),
        'results': results
            .map((r) => {
                  'id': r.id,
                  'result_status': r.status,
                  'result_value': r.value,
                  'remark': r.remark,
                })
            .toList(),
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已完成该项')));
        Navigator.pop(context, true);
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(item?['device_name']?.toString() ?? '计量执行')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text('执行单：${exec?['execution_no'] ?? widget.executionId}'),
                Text('设备：${item?['device_code'] ?? '—'} · ${item?['device_name'] ?? ''}'),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: overall,
                  decoration: const InputDecoration(labelText: '总体结果', border: OutlineInputBorder()),
                  items: const [
                    DropdownMenuItem(value: 'pass', child: Text('合格')),
                    DropdownMenuItem(value: 'fail', child: Text('不合格')),
                    DropdownMenuItem(value: 'na', child: Text('不适用')),
                  ],
                  onChanged: editable ? (v) => setState(() => overall = v ?? 'pass') : null,
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: certCtrl,
                  enabled: editable,
                  decoration: const InputDecoration(labelText: '证书编号', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: remarkCtrl,
                  enabled: editable,
                  maxLines: 2,
                  decoration: const InputDecoration(labelText: '备注', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 16),
                Text('检定项', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                if (results.isEmpty)
                  const Text('无检定项明细（可直接填总体结果完成）')
                else
                  for (final r in results)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(r.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                            const SizedBox(height: 8),
                            DropdownButtonFormField<String>(
                              value: r.status == 'pending' ? 'pass' : r.status,
                              decoration: const InputDecoration(
                                labelText: '结果',
                                border: OutlineInputBorder(),
                                isDense: true,
                              ),
                              items: const [
                                DropdownMenuItem(value: 'pass', child: Text('合格')),
                                DropdownMenuItem(value: 'fail', child: Text('不合格')),
                                DropdownMenuItem(value: 'na', child: Text('不适用')),
                              ],
                              onChanged: editable
                                  ? (v) => setState(() => r.status = v ?? 'pass')
                                  : null,
                            ),
                            const SizedBox(height: 8),
                            TextFormField(
                              initialValue: r.value,
                              enabled: editable,
                              decoration: const InputDecoration(
                                labelText: '测定值',
                                border: OutlineInputBorder(),
                                isDense: true,
                              ),
                              onChanged: (v) => r.value = v,
                            ),
                          ],
                        ),
                      ),
                    ),
                const SizedBox(height: 24),
                if (editable)
                  FilledButton(
                    onPressed: saving ? null : complete,
                    child: saving
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Text('完成该项'),
                  ),
              ],
            ),
    );
  }
}

class _MetroResult {
  _MetroResult({
    required this.id,
    required this.name,
    required this.status,
    required this.value,
    required this.remark,
  });

  final String id;
  final String name;
  String status;
  String value;
  String remark;
}
