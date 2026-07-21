import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

class EngineerWorkorderDetailPage extends ConsumerStatefulWidget {
  const EngineerWorkorderDetailPage({super.key, required this.workorderId});

  final String workorderId;

  @override
  ConsumerState<EngineerWorkorderDetailPage> createState() =>
      _EngineerWorkorderDetailPageState();
}

class _EngineerWorkorderDetailPageState
    extends ConsumerState<EngineerWorkorderDetailPage> {
  Map<String, dynamic>? wo;
  List<Map<String, dynamic>> segments = [];
  var loading = true;
  var busy = false;

  static const statusLabel = {
    'reported': '已报修',
    'dispatching': '派工中',
    'pending_accept': '待接单',
    'accepted': '已接单',
    'repairing': '维修中',
    'suspended': '挂起',
    'pending_verify': '待验收',
    'verify_rejected': '拒绝验收',
    'closed': '已关闭',
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
      final data = await api.getData('/repair/workorder/${widget.workorderId}');
      final segs = await api.getList('/repair/workorder/${widget.workorderId}/segments');
      setState(() {
        wo = data is Map ? Map<String, dynamic>.from(data) : null;
        segments = segs.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String get status => wo?['status']?.toString() ?? '';
  bool get unassigned {
    final v = wo?['assigned_user_id']?.toString();
    return v == null || v.isEmpty || v == 'null';
  }

  bool get canGrab => unassigned && (status == 'reported' || status == 'dispatching');
  bool get canAccept => !unassigned && (status == 'pending_accept' || status == 'dispatching');
  bool get canComplete => status == 'repairing' || status == 'verify_rejected';
  bool get canSegment =>
      canComplete ||
      status == 'accepted' ||
      status == 'suspended' ||
      canGrab ||
      canAccept;

  Future<void> grab() async {
    setState(() => busy = true);
    try {
      await api.postData('/repair/workorder/${widget.workorderId}/grab', {'client': 'app'});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('抢单成功')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> accept() async {
    setState(() => busy = true);
    try {
      await api.postData('/repair/workorder/${widget.workorderId}/accept', {
        'client': 'app',
        'startRepair': true,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已接单并开始维修')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> complete() async {
    final solutionCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('提交完工'),
        content: TextField(
          controller: solutionCtrl,
          decoration: const InputDecoration(labelText: '处理说明（可选）'),
          maxLines: 3,
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('提交验收')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => busy = true);
    try {
      await api.postData('/repair/workorder/${widget.workorderId}/complete', {
        'client': 'app',
        'solution_description': solutionCtrl.text.trim(),
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已提交验收')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> addSegment() async {
    List<Map<String, dynamic>> types = [];
    try {
      final raw = await api.getList('/repair/process-type/addable', query: {
        'workorderId': widget.workorderId,
        'status': status,
      });
      types = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (types.isEmpty) {
        final all = await api.getList('/repair/process-type/list');
        types = all.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      }
    } catch (_) {}
    if (!mounted) return;
    if (types.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('无可添加进程类型')));
      return;
    }

    Map<String, dynamic> selected = types.first;
    final remarkCtrl = TextEditingController();
    final qtyCtrl = TextEditingController(text: '1');
    String? sparePartId;
    String? sparePartName;
    final canParts = selected['can_add_parts'] == true || selected['canAddParts'] == true;

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('添加进程'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<String>(
                  initialValue: selected['id']?.toString(),
                  decoration: const InputDecoration(labelText: '进程类型'),
                  items: types
                      .map((t) => DropdownMenuItem(
                            value: t['id']?.toString(),
                            child: Text(t['type_name']?.toString() ?? t['id']?.toString() ?? ''),
                          ))
                      .toList(),
                  onChanged: (v) {
                    selected = types.firstWhere(
                      (t) => t['id']?.toString() == v,
                      orElse: () => types.first,
                    );
                    setLocal(() {});
                  },
                ),
                TextField(
                  controller: remarkCtrl,
                  decoration: const InputDecoration(labelText: '备注'),
                  maxLines: 2,
                ),
                if (selected['can_add_parts'] == true || selected['canAddParts'] == true || canParts) ...[
                  const SizedBox(height: 8),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(sparePartName ?? '选择配件（可选）'),
                    trailing: const Icon(Icons.search),
                    onTap: () async {
                      final picked = await _pickSparePart(ctx);
                      if (picked != null) {
                        sparePartId = picked['id']?.toString();
                        sparePartName = picked['part_name']?.toString() ?? picked['name']?.toString();
                        setLocal(() {});
                      }
                    },
                  ),
                  if (sparePartId != null)
                    TextField(
                      controller: qtyCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: '数量'),
                    ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('保存')),
          ],
        ),
      ),
    );
    if (ok != true) return;

    final parts = <Map<String, dynamic>>[];
    if (sparePartId != null) {
      final q = int.tryParse(qtyCtrl.text.trim()) ?? 1;
      parts.add({'spare_part_id': sparePartId, 'quantity': q < 1 ? 1 : q});
    }

    setState(() => busy = true);
    try {
      await api.postData('/repair/workorder/${widget.workorderId}/segments', {
        'client': 'app',
        'process_type_id': selected['id'],
        'remark': remarkCtrl.text.trim(),
        'parts': parts,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('进程已添加')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<Map<String, dynamic>?> _pickSparePart(BuildContext ctx) async {
    try {
      final list = await api.getList('/repair/spare_part/list', query: {'limit': 50});
      if (list.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无配件档案')));
        }
        return null;
      }
      final items = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (!ctx.mounted) return null;
      return showModalBottomSheet<Map<String, dynamic>>(
        context: ctx,
        builder: (c) => SafeArea(
          child: ListView(
            children: [
              const ListTile(title: Text('选择配件')),
              ...items.map((p) => ListTile(
                    title: Text(p['part_name']?.toString() ?? p['name']?.toString() ?? ''),
                    subtitle: Text(p['part_code']?.toString() ?? ''),
                    onTap: () => Navigator.pop(c, p),
                  )),
            ],
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e is ApiException ? e.message : '加载配件失败')),
        );
      }
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(wo?['wo_no']?.toString() ?? '工单处理')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  '${wo?['device_name'] ?? ''}（${wo?['device_code'] ?? ''}）',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                Text('状态：${statusLabel[status] ?? status}'),
                const SizedBox(height: 8),
                Text(wo?['fault_description']?.toString() ?? ''),
                const Divider(height: 32),
                if (canGrab)
                  FilledButton(onPressed: busy ? null : grab, child: const Text('抢单并开始')),
                if (canAccept) ...[
                  const SizedBox(height: 8),
                  FilledButton(onPressed: busy ? null : accept, child: const Text('接单并开始')),
                ],
                if (canSegment) ...[
                  const SizedBox(height: 8),
                  OutlinedButton(onPressed: busy ? null : addSegment, child: const Text('添加进程')),
                ],
                if (canComplete) ...[
                  const SizedBox(height: 8),
                  FilledButton.tonal(
                    onPressed: busy ? null : complete,
                    child: const Text('完工提交验收'),
                  ),
                ],
                const SizedBox(height: 24),
                Text('进程记录', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                if (segments.isEmpty)
                  const Text('暂无进程段')
                else
                  ...segments.map((s) {
                    final parts = s['parts'] is List ? s['parts'] as List : [];
                    return Card(
                      child: ListTile(
                        title: Text(s['type_name']?.toString() ?? s['process_type_name']?.toString() ?? '进程'),
                        subtitle: Text(
                          '${s['started_at'] ?? ''} ${s['remark'] ?? ''}\n'
                          '${parts.isEmpty ? '' : '配件 ${parts.length} 项'}',
                        ),
                        isThreeLine: parts.isNotEmpty,
                      ),
                    );
                  }),
              ],
            ),
    );
  }
}
