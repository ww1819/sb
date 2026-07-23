import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'ops_exec_detail_page.dart';
import 'repair_scan_page.dart';

/// 保养 / 巡检 / PM 共用配置（MOB-F-03）
class OpsModuleConfig {
  const OpsModuleConfig({
    required this.title,
    required this.module,
    required this.executionBase,
    required this.planDuePath,
    required this.templateListPath,
    required this.levelField,
    required this.levelLabel,
  });

  final String title;
  final String module;
  final String executionBase;
  final String planDuePath;
  final String templateListPath;
  /// ad-hoc body field，如 maintenance_level / inspection_type / pm_category
  final String levelField;
  final String levelLabel;

  static const maintain = OpsModuleConfig(
    title: '移动保养',
    module: 'maintain',
    executionBase: '/maintain/execution',
    planDuePath: '/maintain/plan/due',
    templateListPath: '/maintain/maintenance_template/list',
    levelField: 'maintenance_level',
    levelLabel: '保养级别',
  );

  static const inspect = OpsModuleConfig(
    title: '移动巡检',
    module: 'inspect',
    executionBase: '/inspect/execution',
    planDuePath: '/inspect/plan/due',
    templateListPath: '/inspect/inspection_template/list',
    levelField: 'inspection_type',
    levelLabel: '巡检类型',
  );

  static const pm = OpsModuleConfig(
    title: '移动预防性维护',
    module: 'pm',
    executionBase: '/pm/execution',
    planDuePath: '/pm/plan/due',
    templateListPath: '/maintain/pm_template/list',
    levelField: 'pm_type',
    levelLabel: 'PM 类型',
  );
}

class OpsHubPage extends ConsumerStatefulWidget {
  const OpsHubPage({super.key, required this.config});

  final OpsModuleConfig config;

  @override
  ConsumerState<OpsHubPage> createState() => _OpsHubPageState();
}

class _OpsHubPageState extends ConsumerState<OpsHubPage> {
  List<Map<String, dynamic>> dueItems = [];
  var loading = true;

  OpsModuleConfig get cfg => widget.config;

  @override
  void initState() {
    super.initState();
    loadDue();
  }

  Future<void> loadDue() async {
    setState(() => loading = true);
    try {
      final api = ref.read(apiServiceProvider);
      final raw = await api.getList(cfg.planDuePath);
      setState(() {
        dueItems = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } catch (_) {
      setState(() => dueItems = []);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> scanAndExecute() async {
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const RepairScanPage()),
    );
    if (code == null || code.isEmpty || !mounted) return;
    await openByCode(code);
  }

  Future<void> openByCode(String code) async {
    final api = ref.read(apiServiceProvider);
    try {
      final list = await api.getList('/repair/workorder/devices/lookup', query: {'deviceCode': code});
      if (list.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未找到设备')));
        }
        return;
      }
      Map<String, dynamic> device;
      if (list.length == 1) {
        device = Map<String, dynamic>.from(list.first as Map);
      } else {
        final picked = await showModalBottomSheet<Map<String, dynamic>>(
          context: context,
          builder: (ctx) => SafeArea(
            child: ListView(
              shrinkWrap: true,
              children: [
                const ListTile(title: Text('选择设备')),
                ...list.map((e) {
                  final m = Map<String, dynamic>.from(e as Map);
                  return ListTile(
                    title: Text(m['device_name']?.toString() ?? ''),
                    subtitle: Text('${m['device_code'] ?? ''} · ${m['dept_name'] ?? ''}'),
                    onTap: () => Navigator.pop(ctx, m),
                  );
                }),
              ],
            ),
          ),
        );
        if (!mounted) return;
        if (picked == null) return;
        device = picked;
      }
      if (!mounted) return;
      await openDeviceTasks(device);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> openDeviceTasks(Map<String, dynamic> device) async {
    if (!mounted) return;
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text(device['device_name']?.toString() ?? '设备'),
              subtitle: Text(device['device_code']?.toString() ?? ''),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.add_box_outlined),
              title: const Text('新增执行单'),
              onTap: () => Navigator.pop(ctx, 'adhoc'),
            ),
            ListTile(
              leading: const Icon(Icons.playlist_add_check),
              title: const Text('申请纳入计划'),
              onTap: () => Navigator.pop(ctx, 'include'),
            ),
            ListTile(
              leading: const Icon(Icons.play_circle_outline),
              title: const Text('执行明细'),
              onTap: () => Navigator.pop(ctx, 'execute'),
            ),
            ListTile(
              leading: const Icon(Icons.verified_outlined),
              title: const Text('确认明细'),
              onTap: () => Navigator.pop(ctx, 'confirm'),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'adhoc') {
      await createAdHoc(device);
    } else if (action == 'include') {
      await applyInclude(device);
    } else if (action == 'execute') {
      await pickOpenItem(device, forConfirm: false);
    } else if (action == 'confirm') {
      await pickOpenItem(device, forConfirm: true);
    }
  }

  Future<void> applyInclude(Map<String, dynamic> device) async {
    final api = ref.read(apiServiceProvider);
    List<Map<String, dynamic>> plans = [];
    try {
      final raw = await api.getList('/${cfg.module}/plan/include-request/approved-plans', query: {
        'device_ids': device['id']?.toString(),
      });
      plans = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } catch (_) {}
    if (!mounted) return;
    if (plans.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无已审核计划可纳入')));
      return;
    }
    final picked = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => SafeArea(
        child: SizedBox(
          height: MediaQuery.of(ctx).size.height * 0.5,
          child: ListView(
            children: [
              const ListTile(title: Text('选择目标计划')),
              ...plans.map(
                (p) => ListTile(
                  title: Text(p['plan_name']?.toString() ?? p['plan_no']?.toString() ?? ''),
                  subtitle: Text('${p['plan_no'] ?? ''} · ${p['template_name'] ?? ''}'),
                  onTap: () => Navigator.pop(ctx, p),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (picked == null || !mounted) return;
    try {
      await api.postData('/${cfg.module}/plan/include-request', {
        'client': 'app',
        'plan_id': picked['id'],
        'device_id': device['id'],
        'device_code': device['device_code'],
        'device_name': device['device_name'],
        'dept_id': device['dept_id'],
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已提交纳入申请，待 Web 确认')));
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> pickOpenItem(Map<String, dynamic> device, {required bool forConfirm}) async {
    final api = ref.read(apiServiceProvider);
    final deviceId = device['id']?.toString();
    if (deviceId == null) return;
    try {
      final raw = await api.getList('${cfg.executionBase}/by-device/$deviceId', query: {'openOnly': true});
      var items = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (forConfirm) {
        items = items.where((it) {
          final s = it['status']?.toString() ?? '';
          return s != 'confirmed';
        }).toList();
      }
      if (!mounted) return;
      if (items.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(forConfirm ? '暂无可确认明细' : '暂无待执行明细，可先新增执行单')),
        );
        return;
      }
      if (forConfirm) {
        final picked = await showModalBottomSheet<Map<String, dynamic>>(
          context: context,
          builder: (ctx) => SafeArea(
            child: ListView(
              shrinkWrap: true,
              children: [
                const ListTile(title: Text('选择要确认的明细')),
                ...items.map(
                  (it) => ListTile(
                    title: Text(it['execution_no']?.toString() ?? ''),
                    subtitle: Text('${it['status'] ?? ''} · ${it['source_type'] ?? ''}'),
                    onTap: () => Navigator.pop(ctx, it),
                  ),
                ),
              ],
            ),
          ),
        );
        if (picked == null || !mounted) return;
        final itemId = picked['id']?.toString();
        if (itemId == null) return;
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('确认明细'),
            content: Text(
              (picked['status']?.toString() == 'completed')
                  ? '确认该设备执行结果？'
                  : '结果未填完也可确认，将自动记为已完成再确认。是否继续？',
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('确认')),
            ],
          ),
        );
        if (ok != true) return;
        await api.postData('${cfg.executionBase}/item/$itemId/confirm', {'client': 'app'});
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已确认')));
        }
        return;
      }
      await showModalBottomSheet<void>(
        context: context,
        builder: (ctx) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(title: Text('选择执行明细')),
              ...items.map((it) {
                final execId = it['execution_id']?.toString();
                final itemId = it['id']?.toString();
                return ListTile(
                  title: Text(it['execution_no']?.toString() ?? ''),
                  subtitle: Text('${it['execution_status'] ?? ''} · ${it['status'] ?? ''}'),
                  onTap: () {
                    Navigator.pop(ctx);
                    if (execId != null && itemId != null) openDetail(execId, itemId);
                  },
                );
              }),
            ],
          ),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> createAdHoc(Map<String, dynamic> device) async {
    final api = ref.read(apiServiceProvider);
    List<Map<String, dynamic>> templates = [];
    try {
      final raw = await api.getList(cfg.templateListPath, query: {'limit': 50});
      templates = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } catch (_) {}
    if (!mounted) return;
    if (templates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无可用模板，请先在 Web 配置')));
      return;
    }
    Map<String, dynamic>? selected = templates.first;
    final levelCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('无计划直开'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<String>(
                  initialValue: selected?['id']?.toString(),
                  decoration: const InputDecoration(labelText: '模板'),
                  items: templates
                      .map((t) => DropdownMenuItem(
                            value: t['id']?.toString(),
                            child: Text(t['template_name']?.toString() ?? t['id']?.toString() ?? ''),
                          ))
                      .toList(),
                  onChanged: (v) {
                    selected = templates.firstWhere((t) => t['id']?.toString() == v, orElse: () => templates.first);
                    setLocal(() {});
                  },
                ),
                TextField(
                  controller: levelCtrl,
                  decoration: InputDecoration(labelText: cfg.levelLabel),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('创建')),
          ],
        ),
      ),
    );
    if (ok != true || selected == null) return;
    if (levelCtrl.text.trim().isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('请填写${cfg.levelLabel}')));
      }
      return;
    }
    try {
      final body = <String, dynamic>{
        'template_id': selected!['id'],
        'template_name': selected!['template_name'],
        cfg.levelField: levelCtrl.text.trim(),
        'device_id': device['id'],
        'client': 'app',
      };
      final created = await api.postData('${cfg.executionBase}/ad-hoc', body);
      final execId = created is Map ? created['id']?.toString() : null;
      if (execId == null) return;
      final detail = await api.getData('${cfg.executionBase}/$execId');
      final items = detail is Map && detail['items'] is List ? detail['items'] as List : [];
      String? itemId;
      for (final it in items) {
        if (it is Map && it['device_id']?.toString() == device['id']?.toString()) {
          itemId = it['id']?.toString();
          break;
        }
      }
      itemId ??= items.isNotEmpty && items.first is Map ? (items.first as Map)['id']?.toString() : null;
      if (itemId != null && mounted) {
        await openDetail(execId, itemId);
      }
      loadDue();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> openDetail(String execId, String itemId) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => OpsExecDetailPage(config: cfg, executionId: execId, itemId: itemId),
      ),
    );
    loadDue();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(cfg.title),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: loadDue),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: scanAndExecute,
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('扫码执行'),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: loadDue,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text('近 7 日到期设备', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  if (dueItems.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 24),
                      child: Center(child: Text('暂无到期任务，可扫码执行或直开')),
                    )
                  else
                    ...dueItems.map((d) => Card(
                          margin: const EdgeInsets.only(bottom: 8),
                          child: ListTile(
                            title: Text(d['device_name']?.toString() ?? ''),
                            subtitle: Text(
                              '${d['device_code'] ?? ''} · 计划 ${d['plan_no'] ?? ''} · 到期 ${d['next_due_date'] ?? ''}',
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () async {
                              final id = d['device_id']?.toString();
                              if (id == null) return;
                              await openDeviceTasks({
                                'id': id,
                                'device_code': d['device_code'],
                                'device_name': d['device_name'],
                              });
                            },
                          ),
                        )),
                  const SizedBox(height: 72),
                ],
              ),
            ),
    );
  }
}
