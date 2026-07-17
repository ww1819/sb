import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../../core/storage/app_prefs.dart';
import '../../../shared/services/api_service.dart';
import 'repair_scan_page.dart';

class RepairFormPage extends ConsumerStatefulWidget {
  const RepairFormPage({super.key, this.workorderId});

  final String? workorderId;

  @override
  ConsumerState<RepairFormPage> createState() => _RepairFormPageState();
}

class _RepairFormPageState extends ConsumerState<RepairFormPage> {
  final codeCtrl = TextEditingController();
  final faultCtrl = TextEditingController();
  final remarkCtrl = TextEditingController();

  Map<String, dynamic>? device;
  List<Map<String, dynamic>> candidates = [];
  List<Map<String, dynamic>> faultTypes = [];
  List<String> photos = [];
  String? workorderId;
  String urgency = 'normal';
  String? faultTypeId;
  var loading = false;
  var looking = false;
  var readonly = false;

  static const urgencyOptions = [
    ('normal', '一般'),
    ('urgent', '紧急'),
    ('critical', '特急'),
  ];

  @override
  void initState() {
    super.initState();
    workorderId = widget.workorderId;
    Future.microtask(() async {
      await loadFaultTypes();
      if (workorderId != null) await loadWorkorder(workorderId!);
    });
  }

  @override
  void dispose() {
    codeCtrl.dispose();
    faultCtrl.dispose();
    remarkCtrl.dispose();
    super.dispose();
  }

  ApiService get api => ref.read(apiServiceProvider);

  Future<void> loadFaultTypes() async {
    try {
      final list = await api.getList('/repair/fault_type_dict/list', query: {'limit': 200});
      setState(() {
        faultTypes = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } catch (_) {
      /* 字典失败不阻塞 */
    }
  }

  Future<void> loadWorkorder(String id) async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/repair/workorder/$id');
      final m = Map<String, dynamic>.from(data as Map);
      final status = m['status']?.toString() ?? '';
      setState(() {
        workorderId = m['id']?.toString();
        readonly = status != 'draft';
        codeCtrl.text = m['device_code']?.toString() ?? '';
        faultCtrl.text = m['fault_description']?.toString() ?? '';
        remarkCtrl.text = m['remark']?.toString() ?? '';
        urgency = m['urgency_level']?.toString() ?? 'normal';
        faultTypeId = m['fault_type_id']?.toString();
        final fp = m['fault_photos'];
        photos = fp is List ? fp.map((e) => e.toString()).toList() : <String>[];
        if (m['device_id'] != null) {
          device = {
            'id': m['device_id'],
            'device_code': m['device_code'],
            'device_name': m['device_name'],
            'dept_id': m['report_dept_id'],
            'can_report': true,
          };
        }
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> lookupCode([String? raw]) async {
    final code = (raw ?? codeCtrl.text).trim();
    if (code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请输入设备编码')));
      return;
    }
    setState(() {
      looking = true;
      candidates = [];
      device = null;
    });
    try {
      final list = await api.getList('/repair/workorder/devices/lookup', query: {'deviceCode': code});
      final rows = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      if (rows.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('没有检索到该编码的设备')),
          );
        }
        return;
      }
      if (rows.length == 1) {
        await selectDevice(rows.first);
      } else {
        setState(() => candidates = rows);
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> selectDevice(Map<String, dynamic> d) async {
    final can = d['can_report'] != false;
    setState(() {
      device = d;
      candidates = [];
      codeCtrl.text = d['device_code']?.toString() ?? codeCtrl.text;
    });
    if (!can && mounted) {
      final reason = d['cannot_report_reason']?.toString()
          ?? '设备当前状态为「${d['device_status_label'] ?? d['device_status']}」，不可报修';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(reason)));
    }
  }

  Future<void> openScan() async {
    final cam = await Permission.camera.request();
    if (!cam.isGranted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('需要相机权限才能扫码')));
      }
      return;
    }
    if (!mounted) return;
    final code = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const RepairScanPage()),
    );
    if (code == null) return; // 用户取消
    if (code.trim().isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请扫描正确的设备条码或二维码')),
        );
      }
      return;
    }
    codeCtrl.text = code.trim();
    await lookupCode(code.trim());
  }

  Future<void> pickPhoto(ImageSource source) async {
    if (photos.length >= 3) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('故障图片最多 3 张')));
      return;
    }
    if (source == ImageSource.camera) {
      final cam = await Permission.camera.request();
      if (!cam.isGranted) return;
    }
    final picker = ImagePicker();
    final file = await picker.pickImage(source: source, imageQuality: 85);
    if (file == null) return;
    try {
      final url = await api.uploadFile(file.path, filename: file.name);
      setState(() => photos = [...photos, url]);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> saveAndAskSubmit() async {
    if (readonly) return;
    if (device == null || device!['id'] == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请先锁定报修设备')));
      return;
    }
    if (device!['can_report'] == false) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(device!['cannot_report_reason']?.toString() ?? '设备不可报修')),
      );
      return;
    }
    if (faultCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写故障描述')));
      return;
    }
    if (photos.length > 3) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('故障图片最多 3 张')));
      return;
    }

    final user = ref.read(appPrefsProvider).user;
    final body = <String, dynamic>{
      'device_id': device!['id'],
      'device_code': device!['device_code'],
      'device_name': device!['device_name'],
      'reporter_id': user?.userId,
      'report_dept_id': device!['dept_id'],
      'report_method': 'app',
      'fault_description': faultCtrl.text.trim(),
      'urgency_level': urgency,
      'fault_type_id': faultTypeId,
      'remark': remarkCtrl.text.trim().isEmpty ? null : remarkCtrl.text.trim(),
      'fault_photos': photos,
    };

    setState(() => loading = true);
    try {
      final data = workorderId == null
          ? await api.postData('/repair/workorder', body)
          : await api.putData('/repair/workorder/$workorderId', body);
      final saved = Map<String, dynamic>.from(data as Map);
      workorderId = saved['id']?.toString();
      if (!mounted) return;
      final submit = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('是否提交'),
          content: const Text('草稿已保存，是否立即提交报修？'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('否')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('是')),
          ],
        ),
      );
      if (submit == true && workorderId != null) {
        await api.postData('/repair/workorder/$workorderId/submit');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已提交')));
          Navigator.pop(context, true);
        }
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('草稿已保存')));
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

  bool get canContinue => device != null && device!['can_report'] != false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(workorderId == null ? '扫码报修' : (readonly ? '报修详情' : '编辑草稿'))),
      body: loading && workorderId != null && device == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: codeCtrl,
                        enabled: !readonly,
                        decoration: const InputDecoration(
                          labelText: '设备编码',
                          border: OutlineInputBorder(),
                        ),
                        onSubmitted: (_) => lookupCode(),
                      ),
                    ),
                    const SizedBox(width: 8),
                    if (!readonly) ...[
                      IconButton(
                        tooltip: '查询',
                        onPressed: looking ? null : () => lookupCode(),
                        icon: looking
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.search),
                      ),
                      IconButton(
                        tooltip: '扫码',
                        onPressed: openScan,
                        icon: const Icon(Icons.qr_code_scanner),
                      ),
                    ],
                  ],
                ),
                if (candidates.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text('检索到 ${candidates.length} 台设备，请选择', style: Theme.of(context).textTheme.titleSmall),
                  ...candidates.map(
                    (d) => Card(
                      child: ListTile(
                        title: Text(d['device_name']?.toString() ?? ''),
                        subtitle: Text(
                          '${d['device_code'] ?? ''} · ${d['dept_name'] ?? '—'} · ${d['device_status_label'] ?? d['device_status'] ?? ''}',
                        ),
                        trailing: d['can_report'] == false
                            ? Text('不可报修', style: TextStyle(color: Theme.of(context).colorScheme.error))
                            : const Icon(Icons.chevron_right),
                        onTap: d['can_report'] == false ? null : () => selectDevice(d),
                      ),
                    ),
                  ),
                ],
                if (device != null) ...[
                  const SizedBox(height: 12),
                  Card(
                    color: canContinue
                        ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.35)
                        : Theme.of(context).colorScheme.errorContainer.withValues(alpha: 0.45),
                    child: ListTile(
                      title: Text(device!['device_name']?.toString() ?? ''),
                      subtitle: Text(
                        canContinue
                            ? '${device!['device_code'] ?? ''} · ${device!['dept_name'] ?? '—'} · 可报修'
                            : (device!['cannot_report_reason']?.toString() ?? '不可报修'),
                      ),
                      trailing: readonly
                          ? null
                          : TextButton(onPressed: () => setState(() => device = null), child: const Text('更换')),
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: urgency,
                  decoration: const InputDecoration(labelText: '紧急程度', border: OutlineInputBorder()),
                  items: [
                    for (final o in urgencyOptions)
                      DropdownMenuItem(value: o.$1, child: Text(o.$2)),
                  ],
                  onChanged: readonly || !canContinue ? null : (v) => setState(() => urgency = v ?? 'normal'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String?>(
                  value: faultTypeId,
                  decoration: const InputDecoration(labelText: '故障类型', border: OutlineInputBorder()),
                  items: [
                    const DropdownMenuItem(value: null, child: Text('未选择')),
                    for (final t in faultTypes)
                      DropdownMenuItem(
                        value: t['id']?.toString(),
                        child: Text(t['fault_name']?.toString() ?? t['id']?.toString() ?? ''),
                      ),
                  ],
                  onChanged: readonly || !canContinue ? null : (v) => setState(() => faultTypeId = v),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: faultCtrl,
                  enabled: !readonly && canContinue,
                  maxLines: 4,
                  decoration: const InputDecoration(
                    labelText: '故障描述 *',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: remarkCtrl,
                  enabled: !readonly && canContinue,
                  maxLines: 2,
                  decoration: const InputDecoration(labelText: '备注', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 16),
                Text('故障图片（最多 3 张，非必传）', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (var i = 0; i < photos.length; i++)
                      Chip(
                        label: Text('图片${i + 1}'),
                        onDeleted: readonly
                            ? null
                            : () => setState(() {
                                  photos = [...photos]..removeAt(i);
                                }),
                      ),
                    if (!readonly && canContinue && photos.length < 3) ...[
                      ActionChip(
                        avatar: const Icon(Icons.photo_library, size: 18),
                        label: const Text('相册'),
                        onPressed: () => pickPhoto(ImageSource.gallery),
                      ),
                      ActionChip(
                        avatar: const Icon(Icons.camera_alt, size: 18),
                        label: const Text('拍照'),
                        onPressed: () => pickPhoto(ImageSource.camera),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 24),
                if (!readonly)
                  FilledButton(
                    onPressed: loading || !canContinue ? null : saveAndAskSubmit,
                    child: loading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Text('确定'),
                  ),
              ],
            ),
    );
  }
}
