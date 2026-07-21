import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/storage/app_prefs.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/services/repair_draft_store.dart';
import 'repair_form_page.dart';

class RepairPage extends ConsumerStatefulWidget {
  const RepairPage({super.key});

  @override
  ConsumerState<RepairPage> createState() => _RepairPageState();
}

class _RepairPageState extends ConsumerState<RepairPage> {
  List<Map<String, dynamic>> items = [];
  List<Map<String, dynamic>> localDrafts = [];
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

  Future<void> loadLocal() async {
    final user = ref.read(appPrefsProvider).user;
    if (user == null || user.userId.isEmpty) {
      setState(() => localDrafts = []);
      return;
    }
    final list = await ref.read(repairDraftStoreProvider).listByUser(user.userId);
    setState(() => localDrafts = list);
  }

  Future<void> load() async {
    setState(() => loading = true);
    await loadLocal();
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
      if (mounted && localDrafts.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('加载报修单失败（可先用本地草稿）')),
        );
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> openForm({String? id, String? localDraftId}) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => RepairFormPage(workorderId: id, localDraftId: localDraftId),
      ),
    );
    if (changed == true || localDraftId != null) load();
  }

  Future<void> deleteLocalDraft(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除本地草稿'),
        content: const Text('仅删除本机暂存，不影响服务器。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('删除')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(repairDraftStoreProvider).delete(id);
    loadLocal();
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

  String _text(Map<String, dynamic> row, String key) {
    final v = row[key]?.toString().trim();
    if (v == null || v.isEmpty || v == 'null') return '—';
    return v;
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
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
          : items.isEmpty && localDrafts.isEmpty
              ? const Center(child: Text('暂无报修单，点击右下角新建'))
              : RefreshIndicator(
                  onRefresh: load,
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
                    children: [
                      if (localDrafts.isNotEmpty) ...[
                        Text('本地草稿（未上传）', style: Theme.of(context).textTheme.titleSmall),
                        const SizedBox(height: 8),
                        for (final d in localDrafts)
                          Card(
                            color: scheme.secondaryContainer.withValues(alpha: 0.35),
                            margin: const EdgeInsets.only(bottom: 10),
                            child: ListTile(
                              title: Text(d['device_name']?.toString() ?? d['device_code']?.toString() ?? '未选设备'),
                              subtitle: Text('更新：${d['updated_at'] ?? '—'}'),
                              trailing: IconButton(
                                icon: Icon(Icons.delete_outline, color: scheme.error),
                                onPressed: () => deleteLocalDraft(d['id'].toString()),
                              ),
                              onTap: () => openForm(localDraftId: d['id']?.toString()),
                            ),
                          ),
                        const SizedBox(height: 8),
                        Text('服务器单据', style: Theme.of(context).textTheme.titleSmall),
                        const SizedBox(height: 8),
                      ],
                      if (items.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(24),
                          child: Center(child: Text('暂无服务器报修单')),
                        )
                      else
                        for (final row in items) _buildServerCard(context, scheme, row),
                    ],
                  ),
                ),
    );
  }

  Widget _buildServerCard(BuildContext context, ColorScheme scheme, Map<String, dynamic> row) {
    final status = row['status']?.toString() ?? '';
    final label = statusLabel[status] ?? status;
    final fault = _text(row, 'fault_description');
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: InkWell(
        onTap: () => openForm(id: row['id']?.toString()),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _text(row, 'wo_no'),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: scheme.primaryContainer.withValues(alpha: 0.55),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(label, style: TextStyle(fontSize: 12, color: scheme.onPrimaryContainer)),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text('设备名称：${_text(row, 'device_name')}'),
              Text('设备编码：${_text(row, 'device_code')}'),
              Text('规格：${_text(row, 'specification')}'),
              Text('序列号：${_text(row, 'serial_number')}'),
              if (fault != '—') ...[
                const SizedBox(height: 4),
                Text(
                  '故障：$fault',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: scheme.onSurfaceVariant, fontSize: 13),
                ),
              ],
              const SizedBox(height: 4),
              const Divider(height: 12),
              Wrap(
                spacing: 4,
                children: [
                  if (status == 'draft') ...[
                    TextButton(
                      onPressed: () => openForm(id: row['id']?.toString()),
                      child: const Text('编辑'),
                    ),
                    TextButton(
                      onPressed: () => submit(row),
                      child: const Text('提交'),
                    ),
                    TextButton(
                      onPressed: () => deleteDraft(row),
                      style: TextButton.styleFrom(foregroundColor: scheme.error),
                      child: const Text('删除'),
                    ),
                  ] else if (status == 'reported') ...[
                    TextButton(
                      onPressed: () => openForm(id: row['id']?.toString()),
                      child: const Text('查看'),
                    ),
                    TextButton(
                      onPressed: () => withdraw(row),
                      child: const Text('撤回'),
                    ),
                  ] else
                    TextButton(
                      onPressed: () => openForm(id: row['id']?.toString()),
                      child: const Text('查看'),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
