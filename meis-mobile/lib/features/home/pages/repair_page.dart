import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'repair_form_page.dart';

class RepairPage extends ConsumerStatefulWidget {
  const RepairPage({super.key});

  @override
  ConsumerState<RepairPage> createState() => _RepairPageState();
}

class _RepairPageState extends ConsumerState<RepairPage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  static const statusLabel = {
    'draft': '未提交',
    'reported': '已报修',
    'dispatching': '派工中',
    'pending_accept': '待接单',
    'accepted': '已接单',
    'repairing': '维修中',
    'pending_verify': '待验收',
    'verify_rejected': '验收驳回',
    'verified': '已验收',
    'suspended': '挂起',
    'closed': '已关闭',
    'cancelled': '已取消',
  };

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final api = ref.read(apiServiceProvider);
      final page = await api.getPage('/repair/workorder/page', query: {
        'page': 1,
        'size': 50,
        'mode': 'apply',
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      final list = raw is List
          ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];
      setState(() => items = list);
    } catch (_) {
      setState(() => items = []);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('加载报修单失败')),
        );
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> openForm({String? id}) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => RepairFormPage(workorderId: id)),
    );
    if (changed == true) load();
  }

  Future<void> submit(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('提交报修'),
        content: const Text('提交后将进入维修流程，是否继续？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('提交')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(apiServiceProvider).postData('/repair/workorder/$id/submit');
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

  Future<void> withdraw(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('撤回报修'),
        content: const Text('撤回后将回到草稿，可再次修改并提交。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('撤回')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(apiServiceProvider).postData('/repair/workorder/$id/withdraw', {'remark': '用户撤回'});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已撤回为草稿')));
      }
      load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> deleteDraft(Map<String, dynamic> row) async {
    final id = row['id']?.toString();
    if (id == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除草稿'),
        content: const Text('确认删除该草稿报修单？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('删除')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(apiServiceProvider).deleteData('/repair/workorder/$id');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已删除')));
      }
      load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('扫码报修'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: load),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => openForm(),
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('新建报修'),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : items.isEmpty
              ? const Center(child: Text('暂无报修单，点击右下角新建'))
              : RefreshIndicator(
                  onRefresh: load,
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
                    itemCount: items.length,
                    itemBuilder: (_, i) {
                      final row = items[i];
                      final status = row['status']?.toString() ?? '';
                      final label = statusLabel[status] ?? status;
                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          title: Text(row['wo_no']?.toString() ?? row['id']?.toString() ?? ''),
                          subtitle: Text(
                            '${row['device_name'] ?? row['device_code'] ?? '—'}\n'
                            '$label · ${row['fault_description'] ?? ''}',
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                          ),
                          isThreeLine: true,
                          onTap: () => openForm(id: row['id']?.toString()),
                          trailing: PopupMenuButton<String>(
                            onSelected: (v) {
                              if (v == 'edit') {
                                openForm(id: row['id']?.toString());
                              } else if (v == 'submit') {
                                submit(row);
                              } else if (v == 'withdraw') {
                                withdraw(row);
                              } else if (v == 'delete') {
                                deleteDraft(row);
                              }
                            },
                            itemBuilder: (_) => [
                              if (status == 'draft') ...[
                                const PopupMenuItem(value: 'edit', child: Text('编辑')),
                                const PopupMenuItem(value: 'submit', child: Text('提交')),
                                const PopupMenuItem(value: 'delete', child: Text('删除')),
                              ],
                              if (status == 'reported')
                                const PopupMenuItem(value: 'withdraw', child: Text('撤回')),
                              if (status != 'draft')
                                const PopupMenuItem(value: 'edit', child: Text('查看')),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}
