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
    required this.levelListPath,
    required this.levelField,
    required this.levelIdField,
    required this.levelLabel,
    required this.levelOptionLabelKey,
    required this.levelOptionCodeKey,
  });

  final String title;
  final String module;
  final String executionBase;
  final String planDuePath;
  final String templateListPath;
  /// 级别/类型主数据 list
  final String levelListPath;
  /// ad-hoc body 文本字段，如 maintenance_level / inspection_type / pm_type
  final String levelField;
  /// ad-hoc body id 字段，如 maintenance_level_id
  final String levelIdField;
  final String levelLabel;
  final String levelOptionLabelKey;
  final String levelOptionCodeKey;

  static const maintain = OpsModuleConfig(
    title: '移动保养',
    module: 'maintain',
    executionBase: '/maintain/execution',
    planDuePath: '/maintain/plan/due',
    templateListPath: '/maintain/maintenance_template/list',
    levelListPath: '/maintain/maintenance_level/list',
    levelField: 'maintenance_level',
    levelIdField: 'maintenance_level_id',
    levelLabel: '保养级别',
    levelOptionLabelKey: 'level_name',
    levelOptionCodeKey: 'level_code',
  );

  static const inspect = OpsModuleConfig(
    title: '移动巡检',
    module: 'inspect',
    executionBase: '/inspect/execution',
    planDuePath: '/inspect/plan/due',
    templateListPath: '/inspect/inspection_template/list',
    levelListPath: '/inspect/inspection_type/list',
    levelField: 'inspection_type',
    levelIdField: 'inspection_type_id',
    levelLabel: '巡检类型',
    levelOptionLabelKey: 'type_name',
    levelOptionCodeKey: 'type_code',
  );

  static const pm = OpsModuleConfig(
    title: '移动预防性维护',
    module: 'pm',
    executionBase: '/pm/execution',
    planDuePath: '/pm/plan/due',
    templateListPath: '/maintain/pm_template/list',
    levelListPath: '/maintain/pm_type/list',
    levelField: 'pm_type',
    levelIdField: 'pm_type_id',
    levelLabel: 'PM 类型',
    levelOptionLabelKey: 'type_name',
    levelOptionCodeKey: 'type_code',
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
    await openByQuery(code);
  }

  Future<void> searchDevice() async {
    final ctrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('搜索设备'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: '编码 / 名称 / 首拼',
            hintText: '如 XYB 或 血压计',
          ),
          onSubmitted: (_) => Navigator.pop(ctx, true),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('搜索')),
        ],
      ),
    );
    final q = ctrl.text.trim();
    ctrl.dispose();
    if (ok != true || q.isEmpty || !mounted) return;
    await openByQuery(q);
  }

  Future<void> openByQuery(String query) async {
    final api = ref.read(apiServiceProvider);
    try {
      final list = await api.getList('/repair/workorder/devices/lookup', query: {'q': query});
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
          isScrollControlled: true,
          builder: (ctx) => SafeArea(
            child: SizedBox(
              height: MediaQuery.of(ctx).size.height * 0.6,
              child: ListView(
                children: [
                  const ListTile(title: Text('选择设备')),
                  ...list.map((e) {
                    final m = Map<String, dynamic>.from(e as Map);
                    return ListTile(
                      title: Text(m['device_name']?.toString() ?? ''),
                      subtitle: Text(
                        '${m['device_code'] ?? ''} · ${m['dept_name'] ?? ''} · ${m['pinyin_code'] ?? ''}',
                      ),
                      onTap: () => Navigator.pop(ctx, m),
                    );
                  }),
                ],
              ),
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
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无可申请计划（已含该设备或有待确认申请）')));
      return;
    }
    final picked = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => SafeArea(
        child: SizedBox(
          height: MediaQuery.of(ctx).size.height * 0.7,
          child: Column(
            children: [
              const ListTile(
                title: Text('选择目标计划'),
                subtitle: Text('仅显示明细中无该设备且无待确认申请的已审核计划'),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView.builder(
                  itemCount: plans.length,
                  itemBuilder: (_, i) {
                    final p = plans[i];
                    final due = p['next_due_date']?.toString() ?? '—';
                    final dept = p['dept_name']?.toString() ?? '';
                    final cycle = p['cycle_days'] != null ? '${p['cycle_days']}天' : '';
                    return ListTile(
                      title: Text(p['plan_name']?.toString() ?? p['plan_no']?.toString() ?? ''),
                      subtitle: Text(
                        '${p['plan_no'] ?? ''} · ${p['template_name'] ?? ''} · ${p['type_label'] ?? ''}\n'
                        '$dept · $cycle · 到期 $due · 明细 ${p['item_count'] ?? 0}',
                      ),
                      isThreeLine: true,
                      onTap: () => Navigator.pop(ctx, p),
                    );
                  },
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
      // OPS.16.17：执行入口排除已确认；确认入口排除已确认
      items = items.where((it) {
        final s = it['status']?.toString() ?? '';
        return s != 'confirmed';
      }).toList();
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
          isScrollControlled: true,
          builder: (ctx) => SafeArea(
            child: SizedBox(
              height: MediaQuery.of(ctx).size.height * 0.55,
              child: ListView(
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
        isScrollControlled: true,
        builder: (ctx) => SafeArea(
          child: SizedBox(
            height: MediaQuery.of(ctx).size.height * 0.55,
            child: ListView(
              children: [
                const ListTile(title: Text('选择执行明细')),
                ...items.map((it) {
                  final execId = it['execution_id']?.toString();
                  final itemId = it['id']?.toString();
                  final st = it['status']?.toString() ?? '';
                  return ListTile(
                    title: Text(it['execution_no']?.toString() ?? ''),
                    subtitle: Text('${it['execution_status'] ?? ''} · $st${st == 'completed' ? '（可修改）' : ''}'),
                    onTap: () {
                      Navigator.pop(ctx);
                      if (execId != null && itemId != null) openDetail(execId, itemId);
                    },
                  );
                }),
              ],
            ),
          ),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  int? _calcCycleDays(String? type, int? value) {
    if (type == null || value == null || value <= 0) return null;
    const unit = {'day': 1, 'week': 7, 'month': 30, 'year': 365};
    final u = unit[type];
    if (u == null) return null;
    return u * value;
  }

  String _today() {
    final n = DateTime.now();
    return '${n.year.toString().padLeft(4, '0')}-${n.month.toString().padLeft(2, '0')}-${n.day.toString().padLeft(2, '0')}';
  }

  Future<void> createAdHoc(Map<String, dynamic> device) async {
    final api = ref.read(apiServiceProvider);
    List<Map<String, dynamic>> templates = [];
    List<Map<String, dynamic>> levelOptions = [];
    try {
      final raw = await api.getList(cfg.templateListPath, query: {'limit': 50});
      templates = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } catch (_) {}
    try {
      final raw = await api.getList(cfg.levelListPath, query: {'limit': 100});
      levelOptions = raw.map((e) => Map<String, dynamic>.from(e as Map)).toList();
    } catch (_) {}
    if (!mounted) return;
    if (templates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('暂无可用模板，请先在 Web 配置')));
      return;
    }
    if (levelOptions.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('暂无${cfg.levelLabel}主数据，请先在 Web 配置')));
      return;
    }

    String? levelOptionLabel(Map<String, dynamic> o) {
      final name = o[cfg.levelOptionLabelKey]?.toString();
      final code = o[cfg.levelOptionCodeKey]?.toString();
      if (name != null && name.isNotEmpty && code != null && code.isNotEmpty) return '$name（$code）';
      return name ?? code ?? o['id']?.toString();
    }

    String? matchLevelId(Map<String, dynamic>? t) {
      if (t == null) return null;
      final fromId = t[cfg.levelIdField]?.toString();
      if (fromId != null && fromId.isNotEmpty) {
        final hit = levelOptions.where((o) => o['id']?.toString() == fromId);
        if (hit.isNotEmpty) return fromId;
      }
      final codeOrName = t[cfg.levelField]?.toString() ??
          t['maintenance_level']?.toString() ??
          t['inspection_type']?.toString() ??
          t['inspection_type_name']?.toString() ??
          t['pm_type']?.toString() ??
          t['type_name']?.toString();
      if (codeOrName == null || codeOrName.isEmpty) return null;
      for (final o in levelOptions) {
        if (o[cfg.levelOptionCodeKey]?.toString() == codeOrName ||
            o[cfg.levelOptionLabelKey]?.toString() == codeOrName) {
          return o['id']?.toString();
        }
      }
      return null;
    }

    Map<String, dynamic>? selected = templates.first;
    String? selectedLevelId = matchLevelId(selected) ?? levelOptions.first['id']?.toString();
    var cycleType = selected?['cycle_type']?.toString() ?? 'month';
    final cycleValueCtrl = TextEditingController(text: selected?['cycle_value']?.toString() ?? '1');
    var plannedDate = _today();

    void applyTemplate(Map<String, dynamic> t, void Function(void Function()) setLocal) {
      selected = t;
      selectedLevelId = matchLevelId(t) ?? selectedLevelId;
      if (t['cycle_type'] != null) cycleType = t['cycle_type'].toString();
      if (t['cycle_value'] != null) cycleValueCtrl.text = t['cycle_value'].toString();
      setLocal(() {});
    }

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) {
          final days = _calcCycleDays(cycleType, int.tryParse(cycleValueCtrl.text.trim()));
          final levelIds = levelOptions.map((o) => o['id']?.toString()).whereType<String>().toSet();
          final levelValue = levelIds.contains(selectedLevelId) ? selectedLevelId : null;
          return AlertDialog(
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
                      final t = templates.firstWhere((x) => x['id']?.toString() == v, orElse: () => templates.first);
                      applyTemplate(t, setLocal);
                    },
                  ),
                  DropdownButtonFormField<String>(
                    value: levelValue,
                    decoration: InputDecoration(labelText: cfg.levelLabel),
                    items: levelOptions
                        .map((o) => DropdownMenuItem(
                              value: o['id']?.toString(),
                              child: Text(levelOptionLabel(o) ?? ''),
                            ))
                        .toList(),
                    onChanged: (v) => setLocal(() => selectedLevelId = v),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: cycleType,
                    decoration: const InputDecoration(labelText: '周期类型'),
                    items: const [
                      DropdownMenuItem(value: 'day', child: Text('天')),
                      DropdownMenuItem(value: 'week', child: Text('周')),
                      DropdownMenuItem(value: 'month', child: Text('月')),
                      DropdownMenuItem(value: 'year', child: Text('年')),
                    ],
                    onChanged: (v) {
                      if (v != null) setLocal(() => cycleType = v);
                    },
                  ),
                  TextField(
                    controller: cycleValueCtrl,
                    keyboardType: TextInputType.number,
                    decoration: InputDecoration(labelText: '周期值${days != null ? '（$days 天）' : ''}'),
                    onChanged: (_) => setLocal(() {}),
                  ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('执行日期'),
                    subtitle: Text(plannedDate),
                    trailing: const Icon(Icons.calendar_today),
                    onTap: () async {
                      final initial = DateTime.tryParse(plannedDate) ?? DateTime.now();
                      final picked = await showDatePicker(
                        context: ctx,
                        initialDate: initial,
                        firstDate: DateTime(2020),
                        lastDate: DateTime(2040),
                      );
                      if (picked != null) {
                        setLocal(() {
                          plannedDate =
                              '${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
                        });
                      }
                    },
                  ),
                  Text('起止默认 $plannedDate 00:00:00 ～ 23:59:59', style: Theme.of(ctx).textTheme.bodySmall),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('创建')),
            ],
          );
        },
      ),
    );
    if (ok != true || selected == null) return;
    if (selectedLevelId == null || selectedLevelId!.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('请选择${cfg.levelLabel}')));
      }
      return;
    }
    final levelRow = levelOptions.firstWhere(
      (o) => o['id']?.toString() == selectedLevelId,
      orElse: () => <String, dynamic>{},
    );
    if (levelRow.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('请选择${cfg.levelLabel}')));
      }
      return;
    }
    final cycleValue = int.tryParse(cycleValueCtrl.text.trim());
    final cycleDays = _calcCycleDays(cycleType, cycleValue);
    if (cycleValue == null || cycleValue <= 0 || cycleDays == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写有效周期')));
      }
      return;
    }
    try {
      final levelCode = levelRow[cfg.levelOptionCodeKey]?.toString() ?? '';
      final levelName = levelRow[cfg.levelOptionLabelKey]?.toString() ?? levelCode;
      final body = <String, dynamic>{
        'template_id': selected!['id'],
        'template_name': selected!['template_name'],
        cfg.levelIdField: selectedLevelId,
        cfg.levelField: levelCode.isNotEmpty ? levelCode : levelName,
        'device_id': device['id'],
        'cycle_type': cycleType,
        'cycle_value': cycleValue,
        'cycle_days': cycleDays,
        'planned_date': plannedDate,
        'execute_start_time': '$plannedDate 00:00:00',
        'execute_end_time': '$plannedDate 23:59:59',
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
          IconButton(icon: const Icon(Icons.search), tooltip: '搜索设备', onPressed: searchDevice),
          IconButton(icon: const Icon(Icons.refresh), onPressed: loadDue),
        ],
      ),
      floatingActionButton: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          FloatingActionButton.extended(
            heroTag: 'ops_search',
            onPressed: searchDevice,
            icon: const Icon(Icons.search),
            label: const Text('搜索设备'),
          ),
          const SizedBox(height: 12),
          FloatingActionButton.extended(
            heroTag: 'ops_scan',
            onPressed: scanAndExecute,
            icon: const Icon(Icons.qr_code_scanner),
            label: const Text('扫码执行'),
          ),
        ],
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
                      child: Center(child: Text('暂无到期任务，可扫码/搜索设备后执行')),
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
