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
    final api = ref.read(apiServiceProvider);
    final deviceId = device['id']?.toString();
    if (deviceId == null) return;
    try {
      final raw = await api.getList('${cfg.executionBase}/by-device/$deviceId', query: {'openOnly': true});
      final items = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (!mounted) return;
      if (items.isEmpty) {
        final adHoc = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('无待执行任务'),
            content: Text('设备 ${device['device_name'] ?? ''} 暂无未审核执行单，是否直开？'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('直开')),
            ],
          ),
        );
        if (adHoc == true) await createAdHoc(device);
        return;
      }
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (ctx) => SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: Text(device['device_name']?.toString() ?? '待执行'),
                subtitle: Text('${items.length} 条未审核执行明细'),
                trailing: TextButton(
                  onPressed: () {
                    Navigator.pop(ctx);
                    createAdHoc(device);
                  },
                  child: const Text('直开'),
                ),
              ),
              ...items.map((it) {
                final execId = it['execution_id']?.toString();
                final itemId = it['id']?.toString();
                return ListTile(
                  title: Text(it['execution_no']?.toString() ?? ''),
                  subtitle: Text('${it['execution_status'] ?? ''} · ${it['source_type'] ?? ''}'),
                  onTap: () {
                    Navigator.pop(ctx);
                    if (execId != null && itemId != null) {
                      openDetail(execId, itemId);
                    }
                  },
                );
              }),
              const SizedBox(height: 12),
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
