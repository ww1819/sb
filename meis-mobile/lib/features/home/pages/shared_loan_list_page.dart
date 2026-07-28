import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_status_chip.dart';
import '../../auth/providers/auth_provider.dart';
import 'shared_loan_form_page.dart';

/// 借调单列表（MOB-SHR-01）
class SharedLoanListPage extends ConsumerStatefulWidget {
  const SharedLoanListPage({super.key});

  @override
  ConsumerState<SharedLoanListPage> createState() => _SharedLoanListPageState();
}

class _SharedLoanListPageState extends ConsumerState<SharedLoanListPage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  static const statusLabel = {
    'draft': '草稿',
    'pending': '待审批',
    'approved': '已审批',
    'on_loan': '借出中',
    'returned': '已归还',
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
      final page = await api.getPage('/shared/loan/page', query: {
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

  Future<void> openForm({String? id}) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => SharedLoanFormPage(loanId: id)),
    );
    if (changed == true) load();
  }

  Future<void> submit(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final userId = ref.read(authProvider).user?.userId;
    if (userId == null || userId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未登录')));
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('提交借调'),
        content: const Text('提交后进入审批，是否继续？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('提交')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await api.postData('/shared/loan/$id/submit', {'applicantId': userId});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已提交')));
      }
      load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> lend(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认借出'),
        content: Text('确认将「${row['device_name'] ?? row['loan_no'] ?? ''}」借出？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('借出')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await api.postData('/shared/loan/$id/lend');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已借出')));
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
        title: const Text('借调申请'),
        actions: [IconButton(icon: const Icon(Icons.refresh), onPressed: load)],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => openForm(),
        icon: const Icon(Icons.add),
        label: const Text('新建借调'),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : items.isEmpty
              ? const Center(
                  child: Text('暂无借调单，点击右下角新建', style: TextStyle(color: AppColors.textMuted)),
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
                      for (final row in items) _buildCard(row),
                    ],
                  ),
                ),
    );
  }

  Widget _buildCard(Map<String, dynamic> row) {
    final status = row['status']?.toString() ?? '';
    final label = statusLabel[status] ?? status;
    return MeisListCard(
      onTap: status == 'draft' || status == 'pending'
          ? () => openForm(id: row['id']?.toString())
          : null,
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
              MeisStatusChip(label, emphasize: status == 'draft' || status == 'pending' || status == 'approved'),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${_text(row, 'device_name')} · ${_text(row, 'device_code')}',
            style: const TextStyle(fontSize: 13, color: AppColors.textPrimary, height: 1.35),
          ),
          const SizedBox(height: 4),
          Text(
            '借入科室：${_text(row, 'to_dept_name')} · ${_text(row, 'loan_start')} ~ ${_text(row, 'loan_end')}',
            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.35),
          ),
          const Divider(height: 16),
          Wrap(
            spacing: 4,
            children: [
              if (status == 'draft') ...[
                TextButton(
                  onPressed: () => openForm(id: row['id']?.toString()),
                  child: const Text('编辑'),
                ),
                TextButton(onPressed: () => submit(row), child: const Text('提交')),
              ],
              if (status == 'approved')
                TextButton(onPressed: () => lend(row), child: const Text('确认借出')),
            ],
          ),
        ],
      ),
    );
  }
}
