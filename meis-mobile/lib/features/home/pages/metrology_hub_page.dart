import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'metrology_exec_detail_page.dart';

/// 计量移动执行（MOB.12 / BACKLOG-MOB-11）
class MetrologyHubPage extends ConsumerStatefulWidget {
  const MetrologyHubPage({super.key});

  @override
  ConsumerState<MetrologyHubPage> createState() => _MetrologyHubPageState();
}

class _MetrologyHubPageState extends ConsumerState<MetrologyHubPage> {
  List<Map<String, dynamic>> duePlans = [];
  List<Map<String, dynamic>> openExecs = [];
  var loading = true;
  var generating = false;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final due = await api.getList('/metrology/plan/due');
      final page = await api.getPage('/metrology/execution/page', query: {
        'page': 1,
        'size': 30,
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      final execs = raw is List
          ? raw
              .map((e) => Map<String, dynamic>.from(e as Map))
              .where((e) {
                final st = e['status']?.toString() ?? '';
                return st == 'draft' || st == 'pending' || st == 'in_progress';
              })
              .toList()
          : <Map<String, dynamic>>[];
      setState(() {
        duePlans = due.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        openExecs = execs;
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> generateFromPlan(Map<String, dynamic> plan) async {
    final planId = plan['id']?.toString();
    if (planId == null) return;
    setState(() => generating = true);
    try {
      final data = await api.postData('/metrology/plan/$planId/generate-execution', {});
      if (data is! Map) throw ApiException('生成执行单失败');
      final execId = data['id']?.toString() ?? data['execution_id']?.toString();
      if (execId == null) throw ApiException('未返回执行单 id');
      await openExecution(execId);
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => generating = false);
    }
  }

  Future<void> openExecution(String execId) async {
    final data = await api.getData('/metrology/execution/$execId');
    if (data is! Map) return;
    final items = data['items'] is List ? data['items'] as List : [];
    Map<String, dynamic>? firstOpen;
    for (final it in items) {
      if (it is! Map) continue;
      final st = it['status']?.toString() ?? '';
      if (st != 'completed') {
        firstOpen = Map<String, dynamic>.from(it);
        break;
      }
    }
    if (firstOpen == null && items.isNotEmpty && items.first is Map) {
      firstOpen = Map<String, dynamic>.from(items.first as Map);
    }
    if (firstOpen == null || firstOpen['id'] == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('执行单无设备明细')));
      }
      return;
    }
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MetrologyExecDetailPage(
          executionId: execId,
          itemId: firstOpen!['id'].toString(),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('移动计量'),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: load)],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: ListView(
                padding: const EdgeInsets.all(12),
                children: [
                  Text('到期计划（30 天内）', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 8),
                  if (duePlans.isEmpty)
                    const Card(child: ListTile(title: Text('暂无到期计划')))
                  else
                    for (final p in duePlans)
                      Card(
                        child: ListTile(
                          title: Text(p['device_name']?.toString() ?? p['plan_name']?.toString() ?? '计划'),
                          subtitle: Text(
                            '${p['device_code'] ?? '—'} · 到期 ${p['next_due_date'] ?? '—'}',
                          ),
                          trailing: generating
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.play_arrow),
                          onTap: generating ? null : () => generateFromPlan(p),
                        ),
                      ),
                  const SizedBox(height: 16),
                  Text('进行中执行单', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: 8),
                  if (openExecs.isEmpty)
                    const Card(child: ListTile(title: Text('暂无进行中执行单')))
                  else
                    for (final e in openExecs)
                      Card(
                        child: ListTile(
                          title: Text(e['execution_no']?.toString() ?? e['id']?.toString() ?? ''),
                          subtitle: Text('状态：${e['status'] ?? '—'} · ${e['template_name'] ?? ''}'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () async {
                            final id = e['id']?.toString();
                            if (id == null) return;
                            try {
                              await openExecution(id);
                              await load();
                            } on ApiException catch (ex) {
                              if (mounted) {
                                ScaffoldMessenger.of(context)
                                    .showSnackBar(SnackBar(content: Text(ex.message)));
                              }
                            }
                          },
                        ),
                      ),
                ],
              ),
            ),
    );
  }
}
