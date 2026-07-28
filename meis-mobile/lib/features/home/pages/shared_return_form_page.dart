import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';

/// 归还申请表单（MOB-SHR-01）
class SharedReturnFormPage extends ConsumerStatefulWidget {
  const SharedReturnFormPage({super.key});

  @override
  ConsumerState<SharedReturnFormPage> createState() => _SharedReturnFormPageState();
}

class _SharedReturnFormPageState extends ConsumerState<SharedReturnFormPage> {
  final conditionCtrl = TextEditingController();

  Map<String, dynamic>? loan;
  List<Map<String, dynamic>> onLoanList = [];
  DateTime? returnDate;
  var loading = false;
  var loadingLoans = true;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    returnDate = DateTime(now.year, now.month, now.day);
    loadOnLoan();
  }

  @override
  void dispose() {
    conditionCtrl.dispose();
    super.dispose();
  }

  String _fmtDate(DateTime? d) {
    if (d == null) return '';
    final m = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '${d.year}-$m-$day';
  }

  Future<void> loadOnLoan() async {
    setState(() => loadingLoans = true);
    try {
      final page = await api.getPage('/shared/loan/page', query: {
        'page': 1,
        'size': 100,
        'status': 'on_loan',
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      setState(() {
        onLoanList = raw is List
            ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : [];
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loadingLoans = false);
    }
  }

  Future<void> pickLoan() async {
    if (onLoanList.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无借出中的借调单')));
      return;
    }
    final selected = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        return SafeArea(
          child: SizedBox(
            height: MediaQuery.of(ctx).size.height * 0.65,
            child: Column(
              children: [
                const Padding(
                  padding: EdgeInsets.all(AppSpacing.md),
                  child: Text('选择借出中借调单', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                ),
                Expanded(
                  child: ListView.builder(
                    itemCount: onLoanList.length,
                    itemBuilder: (_, i) {
                      final row = onLoanList[i];
                      return ListTile(
                        title: Text(row['loan_no']?.toString() ?? '—'),
                        subtitle: Text(
                          '${row['device_name'] ?? '—'} · ${row['device_code'] ?? '—'} · ${row['to_dept_name'] ?? ''}',
                        ),
                        onTap: () => Navigator.pop(ctx, row),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
    if (selected != null) setState(() => loan = selected);
  }

  Future<void> pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: returnDate ?? DateTime.now(),
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (picked != null) setState(() => returnDate = picked);
  }

  Future<void> submit() async {
    if (loan == null || loan!['id'] == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请选择借调单')));
      return;
    }
    if (returnDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请选择归还日期')));
      return;
    }
    final userId = ref.read(authProvider).user?.userId;
    if (userId == null || userId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未登录')));
      return;
    }

    setState(() => loading = true);
    try {
      await api.postData('/shared/return', {
        'loan_id': loan!['id'],
        'return_date': _fmtDate(returnDate),
        'condition_desc': conditionCtrl.text.trim(),
        'applicant_id': userId,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('归还申请已提交')));
        Navigator.pop(context, true);
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('新建归还')),
      body: loadingLoans
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.pageH,
                AppSpacing.lg,
                AppSpacing.pageH,
                AppSpacing.xxl,
              ),
              children: [
                const MeisSectionLabel('借出中借调单'),
                MeisListCard(
                  onTap: pickLoan,
                  child: loan == null
                      ? const Text('点击选择借调单', style: TextStyle(color: AppColors.textMuted))
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              loan!['loan_no']?.toString() ?? '—',
                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${loan!['device_name'] ?? '—'} · ${loan!['device_code'] ?? '—'}',
                              style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '借入科室：${loan!['to_dept_name'] ?? '—'}',
                              style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                            ),
                          ],
                        ),
                ),
                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('归还日期'),
                MeisListCard(
                  onTap: pickDate,
                  child: Text(returnDate == null ? '选择日期' : _fmtDate(returnDate)),
                ),
                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('设备状况说明'),
                TextField(
                  controller: conditionCtrl,
                  maxLines: 3,
                  decoration: const InputDecoration(hintText: '选填，描述归还时设备状况'),
                ),
                const SizedBox(height: AppSpacing.xl),
                FilledButton(
                  onPressed: loading ? null : submit,
                  child: loading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('提交归还申请'),
                ),
              ],
            ),
    );
  }
}
