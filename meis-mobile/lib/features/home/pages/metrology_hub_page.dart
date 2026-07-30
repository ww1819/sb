import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/datetime_format.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
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
      final data = await api.postData('/metrology/plan/$planId/generate-execution', {'client': 'app'});
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
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.pageH,
                  AppSpacing.md,
                  AppSpacing.pageH,
                  AppSpacing.xl,
                ),
                children: [
                  const MeisSectionLabel('到期计划（30 天内）'),
                  if (duePlans.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 16),
                      child: Text('暂无到期计划', style: TextStyle(color: AppColors.textMuted)),
                    )
                  else
                    for (final p in duePlans)
                      MeisListCard(
                        onTap: generating ? null : () => generateFromPlan(p),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    p['device_name']?.toString() ?? p['plan_name']?.toString() ?? '计划',
                                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${p['device_code'] ?? '—'} · ${formatDisplayDate(p['next_due_date'])}',
                                    style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                                  ),
                                ],
                              ),
                            ),
                            if (generating)
                              const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            else
                              const Icon(Icons.play_arrow, color: AppColors.primary),
                          ],
                        ),
                      ),
                  const MeisSectionLabel('进行中执行单'),
                  if (openExecs.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 16),
                      child: Text('暂无进行中执行单', style: TextStyle(color: AppColors.textMuted)),
                    )
                  else
                    for (final e in openExecs)
                      MeisListCard(
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
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    e['execution_no']?.toString() ?? e['id']?.toString() ?? '',
                                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${e['status'] ?? '—'} · ${e['template_name'] ?? ''}',
                                    style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                                  ),
                                ],
                              ),
                            ),
                            const Icon(Icons.chevron_right, color: AppColors.textMuted),
                          ],
                        ),
                      ),
                ],
              ),
            ),
    );
  }
}
