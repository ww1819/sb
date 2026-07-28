import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_status_chip.dart';
import 'shared_return_form_page.dart';

/// 归还单列表（MOB-SHR-01）
class SharedReturnListPage extends ConsumerStatefulWidget {
  const SharedReturnListPage({super.key});

  @override
  ConsumerState<SharedReturnListPage> createState() => _SharedReturnListPageState();
}

class _SharedReturnListPageState extends ConsumerState<SharedReturnListPage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  static const statusLabel = {
    'draft': '草稿',
    'pending': '待审批',
    'approved': '已审批',
    'rejected': '已驳回',
  };

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final page = await api.getPage('/shared/return/page', query: {
        'page': 1,
        'size': 50,
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

  Future<void> openForm() async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const SharedReturnFormPage()),
    );
    if (changed == true) load();
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
        title: const Text('归还申请'),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: load)],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: openForm,
        icon: const Icon(Icons.add),
        label: const Text('新建归还'),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : items.isEmpty
              ? const Center(
                  child: Text('暂无归还单，点击右下角新建', style: TextStyle(color: AppColors.textMuted)),
                )
              : RefreshIndicator(
                  onRefresh: load,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(
                      AppSpacing.pageH,
                      AppSpacing.md,
                      AppSpacing.pageH,
                      88,
                    ),
                    children: [
                      for (final row in items)
                        MeisListCard(
                          padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      _text(row, 'return_no'),
                                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                                    ),
                                  ),
                                  MeisStatusChip(
                                    statusLabel[row['status']?.toString()] ??
                                        (row['status']?.toString() ?? ''),
                                    emphasize: row['status']?.toString() == 'pending',
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                '${_text(row, 'device_name')} · ${_text(row, 'device_code')}',
                                style: const TextStyle(fontSize: 13, height: 1.35),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '借调单：${_text(row, 'loan_no')} · 归还日：${_text(row, 'return_date')}',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.textSecondary,
                                  height: 1.35,
                                ),
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
