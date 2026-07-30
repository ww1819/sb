import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/datetime_format.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';
import 'repair_scan_page.dart';

/// 借调申请表单（MOB-SHR-01）
class SharedLoanFormPage extends ConsumerStatefulWidget {
  const SharedLoanFormPage({super.key, this.loanId});

  final String? loanId;

  @override
  ConsumerState<SharedLoanFormPage> createState() => _SharedLoanFormPageState();
}

class _SharedLoanFormPageState extends ConsumerState<SharedLoanFormPage> {
  final keywordCtrl = TextEditingController();
  final reasonCtrl = TextEditingController();
  final remarkCtrl = TextEditingController();

  String? loanId;
  Map<String, dynamic>? device;
  Map<String, dynamic>? toDept;
  List<Map<String, dynamic>> deviceCandidates = [];
  List<Map<String, dynamic>> departments = [];
  DateTime? loanStart;
  DateTime? loanEnd;
  var loading = false;
  var looking = false;
  var readonly = false;
  String? status;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    loanId = widget.loanId;
    Future.microtask(() async {
      await loadDepartments();
      if (loanId != null) {
        await loadLoan(loanId!);
      } else {
        final now = DateTime.now();
        setState(() {
          loanStart = DateTime(now.year, now.month, now.day);
          loanEnd = loanStart!.add(const Duration(days: 7));
        });
      }
    });
  }

  @override
  void dispose() {
    keywordCtrl.dispose();
    reasonCtrl.dispose();
    remarkCtrl.dispose();
    super.dispose();
  }

  Future<void> loadDepartments() async {
    try {
      final list = await api.getList('/system/departments');
      setState(() {
        departments = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> loadLoan(String id) async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/shared/loan/$id');
      final m = Map<String, dynamic>.from(data as Map);
      final st = m['status']?.toString() ?? '';
      setState(() {
        loanId = m['id']?.toString();
        status = st;
        readonly = st != 'draft' && st != 'pending';
        reasonCtrl.text = m['reason']?.toString() ?? '';
        remarkCtrl.text = m['remark']?.toString() ?? '';
        if (m['device_id'] != null) {
          device = {
            'id': m['device_id'],
            'device_code': m['device_code'],
            'device_name': m['device_name'],
            'dept_id': m['from_dept_id'],
          };
        }
        if (m['to_dept_id'] != null) {
          toDept = {
            'id': m['to_dept_id'],
            'dept_name': m['to_dept_name'],
          };
        }
        loanStart = _parseDate(m['loan_start']);
        loanEnd = _parseDate(m['loan_end']);
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  DateTime? _parseDate(dynamic v) => tryParseDate(v);

  String _fmtDate(DateTime? d) => d == null ? '' : formatDate(d);

  bool _isSharedFlag(dynamic v) {
    if (v == true || v == 1) return true;
    final s = v?.toString().toLowerCase();
    return s == 'true' || s == '1' || s == 't' || s == 'yes';
  }

  Future<void> searchDevices() async {
    final kw = keywordCtrl.text.trim();
    setState(() => looking = true);
    try {
      final page = await api.getPage('/shared/device/page', query: {
        'page': 1,
        'size': 30,
        if (kw.isNotEmpty) 'keyword': kw,
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      final list = raw is List
          ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];
      list.sort((a, b) {
        final sa = a['shared_loan_status']?.toString() == 'in_stock' ? 0 : 1;
        final sb = b['shared_loan_status']?.toString() == 'in_stock' ? 0 : 1;
        return sa.compareTo(sb);
      });
      setState(() => deviceCandidates = list);
      if (list.isEmpty && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未找到公用设备')));
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> openScan() async {
    if (!mounted) return;
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const RepairScanPage()),
    );
    if (code == null || code.trim().isEmpty) return;
    await resolveByCode(code.trim());
  }

  Future<void> resolveByCode(String code) async {
    setState(() => looking = true);
    try {
      final data = await api.getData('/asset/device/by-code/${Uri.encodeComponent(code)}');
      final d = Map<String, dynamic>.from(data as Map);
      if (!_isSharedFlag(d['is_shared_device'])) {
        throw ApiException('该设备不是公用设备');
      }
      selectDevice(d);
    } on ApiException catch (e) {
      // 回退：按 keyword 查公用列表
      try {
        final page = await api.getPage('/shared/device/page', query: {
          'page': 1,
          'size': 10,
          'keyword': code,
        });
        final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
        final list = raw is List
            ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : <Map<String, dynamic>>[];
        if (list.isEmpty) rethrow;
        final exact = list.where((e) => e['device_code']?.toString() == code).toList();
        selectDevice(exact.isNotEmpty ? exact.first : list.first);
      } on ApiException {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
        }
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  void selectDevice(Map<String, dynamic> d) {
    final loanStatus = d['shared_loan_status']?.toString();
    setState(() {
      device = d;
      deviceCandidates = [];
      keywordCtrl.text = d['device_code']?.toString() ?? '';
    });
    if (loanStatus != null && loanStatus != 'in_stock' && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('该设备当前状态为「${_deviceLoanStatusLabel(loanStatus)}」，建议优先选在库设备')),
      );
    }
  }

  String _deviceLoanStatusLabel(String s) {
    const map = {
      'in_stock': '在库',
      'loan_pending': '借调申请中',
      'on_loan': '已借用',
      'return_pending': '归还申请中',
    };
    return map[s] ?? s;
  }

  Future<void> pickDept() async {
    if (readonly) return;
    final selected = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) {
        final filter = TextEditingController();
        var filtered = List<Map<String, dynamic>>.from(departments);
        return StatefulBuilder(
          builder: (ctx, setModal) {
            return SafeArea(
              child: SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.7,
                child: Column(
                  children: [
                    const Padding(
                      padding: EdgeInsets.all(AppSpacing.md),
                      child: Text('选择借入科室', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.pageH),
                      child: TextField(
                        controller: filter,
                        decoration: const InputDecoration(
                          hintText: '搜索科室',
                          prefixIcon: Icon(Icons.search),
                          isDense: true,
                        ),
                        onChanged: (v) {
                          final kw = v.trim().toLowerCase();
                          setModal(() {
                            filtered = departments.where((d) {
                              final name = d['dept_name']?.toString().toLowerCase() ?? '';
                              final code = d['dept_code']?.toString().toLowerCase() ?? '';
                              return kw.isEmpty || name.contains(kw) || code.contains(kw);
                            }).toList();
                          });
                        },
                      ),
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Expanded(
                      child: ListView.builder(
                        itemCount: filtered.length,
                        itemBuilder: (_, i) {
                          final d = filtered[i];
                          return ListTile(
                            title: Text(d['dept_name']?.toString() ?? '—'),
                            subtitle: Text(d['dept_code']?.toString() ?? ''),
                            onTap: () => Navigator.pop(ctx, d),
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
      },
    );
    if (selected != null) setState(() => toDept = selected);
  }

  Future<void> pickDate({required bool isStart}) async {
    if (readonly) return;
    final initial = (isStart ? loanStart : loanEnd) ?? DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (picked == null) return;
    setState(() {
      if (isStart) {
        loanStart = picked;
        if (loanEnd != null && loanEnd!.isBefore(picked)) loanEnd = picked;
      } else {
        loanEnd = picked;
      }
    });
  }

  Future<void> save({bool andSubmit = false}) async {
    if (readonly) return;
    if (device == null || device!['id'] == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请选择公用设备')));
      return;
    }
    if (toDept == null || toDept!['id'] == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请选择借入科室')));
      return;
    }
    if (loanStart == null || loanEnd == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请选择借调起止日期')));
      return;
    }
    if (loanEnd!.isBefore(loanStart!)) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('结束日期不能早于开始日期')));
      return;
    }
    if (reasonCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写借调原因')));
      return;
    }
    final userId = ref.read(authProvider).user?.userId;
    if (userId == null || userId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未登录')));
      return;
    }

    final body = <String, dynamic>{
      if (loanId != null) 'id': loanId,
      'device_id': device!['id'],
      'to_dept_id': toDept!['id'],
      'loan_start': _fmtDate(loanStart),
      'loan_end': _fmtDate(loanEnd),
      'reason': reasonCtrl.text.trim(),
      'applicant_id': userId,
      'remark': remarkCtrl.text.trim(),
      'status': status ?? 'draft',
      'client': 'app',
    };

    setState(() => loading = true);
    try {
      final data = await api.postData('/shared/loan', body);
      final saved = Map<String, dynamic>.from(data as Map);
      loanId = saved['id']?.toString();
      status = saved['status']?.toString() ?? status;

      if (andSubmit && loanId != null) {
        await api.postData('/shared/loan/$loanId/submit', {'applicantId': userId, 'client': 'app'});
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已保存并提交')));
          Navigator.pop(context, true);
        }
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已保存')));
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
      appBar: AppBar(
        title: Text(loanId == null ? '新建借调' : (readonly ? '借调详情' : '编辑借调')),
      ),
      body: loading && loanId != null && device == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.pageH,
                AppSpacing.lg,
                AppSpacing.pageH,
                AppSpacing.xxl,
              ),
              children: [
                const MeisSectionLabel('公用设备'),
                if (!readonly) ...[
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: keywordCtrl,
                          decoration: const InputDecoration(
                            hintText: '编码/名称检索',
                            isDense: true,
                          ),
                          onSubmitted: (_) => searchDevices(),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      IconButton(
                        onPressed: looking ? null : searchDevices,
                        icon: looking
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.search),
                      ),
                      IconButton(
                        onPressed: looking ? null : openScan,
                        icon: const Icon(Icons.qr_code_scanner),
                      ),
                    ],
                  ),
                  if (deviceCandidates.isNotEmpty) ...[
                    const SizedBox(height: AppSpacing.sm),
                    for (final d in deviceCandidates)
                      MeisListCard(
                        onTap: () => selectDevice(d),
                        child: Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    d['device_name']?.toString() ?? '—',
                                    style: const TextStyle(fontWeight: FontWeight.w600),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${d['device_code'] ?? '—'} · ${_deviceLoanStatusLabel(d['shared_loan_status']?.toString() ?? '')}',
                                    style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                                  ),
                                ],
                              ),
                            ),
                            if (d['shared_loan_status']?.toString() == 'in_stock')
                              const Text('在库', style: TextStyle(color: AppColors.success, fontSize: 12)),
                          ],
                        ),
                      ),
                  ],
                ],
                if (device != null)
                  MeisListCard(
                    color: AppColors.pageBgTop,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          device!['device_name']?.toString() ?? '—',
                          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          device!['device_code']?.toString() ?? '—',
                          style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
                        ),
                      ],
                    ),
                  )
                else if (readonly)
                  const Text('未选择设备', style: TextStyle(color: AppColors.textMuted)),

                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('借入科室'),
                MeisListCard(
                  onTap: readonly ? null : pickDept,
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          toDept?['dept_name']?.toString() ?? '点击选择科室',
                          style: TextStyle(
                            color: toDept == null ? AppColors.textMuted : AppColors.textPrimary,
                          ),
                        ),
                      ),
                      if (!readonly) const Icon(Icons.chevron_right, color: AppColors.textSecondary),
                    ],
                  ),
                ),

                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('借调期间'),
                Row(
                  children: [
                    Expanded(
                      child: MeisListCard(
                        onTap: readonly ? null : () => pickDate(isStart: true),
                        child: Text(loanStart == null ? '开始日期' : _fmtDate(loanStart)),
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 4),
                      child: Text('~'),
                    ),
                    Expanded(
                      child: MeisListCard(
                        onTap: readonly ? null : () => pickDate(isStart: false),
                        child: Text(loanEnd == null ? '结束日期' : _fmtDate(loanEnd)),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('借调原因'),
                TextField(
                  controller: reasonCtrl,
                  readOnly: readonly,
                  maxLines: 3,
                  decoration: const InputDecoration(hintText: '请填写借调原因'),
                ),
                const SizedBox(height: AppSpacing.md),
                const MeisSectionLabel('备注'),
                TextField(
                  controller: remarkCtrl,
                  readOnly: readonly,
                  maxLines: 2,
                  decoration: const InputDecoration(hintText: '选填'),
                ),

                if (!readonly) ...[
                  const SizedBox(height: AppSpacing.xl),
                  FilledButton(
                    onPressed: loading ? null : () => save(andSubmit: false),
                    child: const Text('保存草稿'),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  OutlinedButton(
                    onPressed: loading ? null : () => save(andSubmit: true),
                    child: const Text('保存并提交'),
                  ),
                ],
              ],
            ),
    );
  }
}
