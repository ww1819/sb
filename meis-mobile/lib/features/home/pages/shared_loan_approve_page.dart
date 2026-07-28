import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_status_chip.dart';

/// 借调审核（pendingOnly）（MOB-SHR-01）
class SharedLoanApprovePage extends ConsumerStatefulWidget {
  const SharedLoanApprovePage({super.key});

  @override
  ConsumerState<SharedLoanApprovePage> createState() => _SharedLoanApprovePageState();
}

class _SharedLoanApprovePageState extends ConsumerState<SharedLoanApprovePage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final page = await api.getPage('/shared/loan/page', query: {
        'page': 1,
        'size': 50,
        'pendingOnly': true,
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      setState(() {
        items = raw is List
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

  Future<void> approve(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('通过借调'),
        content: Text('确认通过「${row['loan_no'] ?? ''}」？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('通过')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await api.postData('/shared/loan/$id/approve');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已通过')));
      }
      load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> reject(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('驳回借调'),
        content: Text('确认驳回「${row['loan_no'] ?? ''}」？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
            child: const Text('驳回'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await api.postData('/shared/loan/$id/reject');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已驳回')));
      }
      load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  String _text(Map<String, dynamic> row, String key) {
    final v = row[key]?.toString().trim();
    if (v == null || v.isEmpty || v == 'null') return '—';
    return v;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('借调审核'),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: load)],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : items.isEmpty
              ? const Center(
                  child: Text('暂无待审借调单', style: TextStyle(color: AppColors.textMuted)),
                )
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
                      for (final row in items)
                        MeisListCard(
                          padding: const EdgeInsets.fromLTRB(14, 12, 14, 6),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      _text(row, 'loan_no'),
                                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                                    ),
                                  ),
                                  const MeisStatusChip('待审批', emphasize: true),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                '${_text(row, 'device_name')} · ${_text(row, 'device_code')}',
                                style: const TextStyle(fontSize: 13, height: 1.35),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '借入：${_text(row, 'to_dept_name')} · 申请人：${_text(row, 'applicant_name')}',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.textSecondary,
                                  height: 1.35,
                                ),
                              ),
                              if (_text(row, 'reason') != '—') ...[
                                const SizedBox(height: 4),
                                Text(
                                  _text(row, 'reason'),
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                    fontSize: 13,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                              ],
                              const Divider(height: 16),
                              Wrap(
                                spacing: 4,
                                children: [
                                  TextButton(
                                    onPressed: () => approve(row),
                                    child: const Text('通过'),
                                  ),
                                  TextButton(
                                    onPressed: () => reject(row),
                                    style: TextButton.styleFrom(foregroundColor: AppColors.danger),
                                    child: const Text('驳回'),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
    );
  }
}
