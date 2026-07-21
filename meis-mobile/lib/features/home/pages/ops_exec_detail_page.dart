import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../../shared/services/api_service.dart';
import 'ops_hub_page.dart';
import 'signature_pad_page.dart';

class OpsExecDetailPage extends ConsumerStatefulWidget {
  const OpsExecDetailPage({
    super.key,
    required this.config,
    required this.executionId,
    required this.itemId,
  });

  final OpsModuleConfig config;
  final String executionId;
  final String itemId;

  @override
  ConsumerState<OpsExecDetailPage> createState() => _OpsExecDetailPageState();
}

class _OpsExecDetailPageState extends ConsumerState<OpsExecDetailPage> {
  Map<String, dynamic>? exec;
  Map<String, dynamic>? item;
  List<_ResultRow> results = [];
  List<String> itemPhotos = [];
  String? signatureUrl;
  var loading = true;
  var saving = false;

  OpsModuleConfig get cfg => widget.config;
  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final data = await api.getData('${cfg.executionBase}/${widget.executionId}');
      if (data is! Map) throw ApiException('执行单无效');
      final map = Map<String, dynamic>.from(data);
      final items = map['items'] is List ? map['items'] as List : [];
      Map<String, dynamic>? found;
      for (final it in items) {
        if (it is Map && it['id']?.toString() == widget.itemId) {
          found = Map<String, dynamic>.from(it);
          break;
        }
      }
      if (found == null) throw ApiException('明细不存在');
      final rawResults = found['results'] is List ? found['results'] as List : [];
      setState(() {
        exec = map;
        item = found;
        itemPhotos = _asUrlList(found['photos']);
        signatureUrl = found['signature_url']?.toString();
        results = rawResults.map((e) {
          final m = Map<String, dynamic>.from(e as Map);
          return _ResultRow(
            id: m['id']?.toString() ?? '',
            name: m['item_name']?.toString() ?? '检查项',
            content: m['item_content']?.toString() ?? '',
            status: m['result_status']?.toString() ?? 'pending',
            value: m['result_value']?.toString() ?? '',
            remark: m['remark']?.toString() ?? '',
            photos: _asUrlList(m['photos']),
            rowVersion: (m['row_version'] as num?)?.toInt() ?? 1,
          );
        }).toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  List<String> _asUrlList(dynamic raw) {
    if (raw is List) {
      return raw.map((e) => e.toString()).where((s) => s.isNotEmpty && s != 'null').toList();
    }
    return [];
  }

  bool get editable {
    final st = exec?['status']?.toString();
    return st == 'draft' || st == 'in_progress' || st == 'pending';
  }

  Future<void> ensureStarted() async {
    final st = exec?['status']?.toString();
    if (st == 'draft' || st == 'pending') {
      await api.postData('${cfg.executionBase}/${widget.executionId}/start', {'client': 'app'});
    }
  }

  Future<String?> uploadImage(ImageSource source) async {
    if (source == ImageSource.camera) {
      final cam = await Permission.camera.request();
      if (!cam.isGranted) return null;
    }
    final file = await ImagePicker().pickImage(source: source, imageQuality: 85);
    if (file == null) return null;
    return api.uploadFile(file.path, filename: file.name);
  }

  Future<void> addResultPhoto(_ResultRow r) async {
    if (!editable || r.photos.length >= 6) return;
    try {
      final url = await uploadImage(ImageSource.camera);
      if (url == null) return;
      setState(() => r.photos = [...r.photos, url]);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> addItemPhoto() async {
    if (!editable || itemPhotos.length >= 6) return;
    try {
      final url = await uploadImage(ImageSource.camera);
      if (url == null) return;
      setState(() => itemPhotos = [...itemPhotos, url]);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> sign() async {
    if (!editable) return;
    final path = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const SignaturePadPage()),
    );
    if (path == null) return;
    try {
      final url = await api.uploadFile(path, filename: 'signature.png');
      setState(() => signatureUrl = url);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> complete() async {
    if (!editable) return;
    final failWithoutPhoto = results.any((r) => r.status == 'fail' && r.photos.isEmpty);
    if (failWithoutPhoto) {
      final cont = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('异常项未拍照'),
          content: const Text('存在异常检查项尚未拍照，是否仍完成？'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('去拍照')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('仍完成')),
          ],
        ),
      );
      if (cont != true) return;
    }
    setState(() => saving = true);
    try {
      await ensureStarted();
      final hasFail = results.any((r) => r.status == 'fail');
      await api.postData('${cfg.executionBase}/item/${widget.itemId}/complete', {
        'client': 'app',
        'overall_result': hasFail ? 'fail' : 'pass',
        'photos': itemPhotos,
        'signature_url': signatureUrl,
        'results': results
            .map((r) => {
                  'id': r.id,
                  'result_status': r.status == 'pending' ? 'pass' : r.status,
                  'result_value': r.value.isEmpty ? (r.status == 'fail' ? '不合格' : '合格') : r.value,
                  'remark': r.remark,
                  'photos': r.photos,
                })
            .toList(),
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已完成本设备项')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> submit() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('提交执行单'),
        content: const Text('提交后不可修改（可撤回）。是否继续？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('提交')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => saving = true);
    try {
      await api.postData('${cfg.executionBase}/${widget.executionId}/submit', {'client': 'app'});
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已提交')));
        Navigator.pop(context);
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Widget photoRow(List<String> urls, {VoidCallback? onAdd, void Function(int)? onRemove}) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        ...urls.asMap().entries.map((e) => Stack(
              children: [
                Image.network(e.value, width: 64, height: 64, fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const SizedBox(width: 64, height: 64, child: Icon(Icons.broken_image))),
                if (onRemove != null && editable)
                  Positioned(
                    right: 0,
                    top: 0,
                    child: InkWell(
                      onTap: () => onRemove(e.key),
                      child: const CircleAvatar(radius: 10, child: Icon(Icons.close, size: 12)),
                    ),
                  ),
              ],
            )),
        if (onAdd != null && editable)
          OutlinedButton.icon(onPressed: onAdd, icon: const Icon(Icons.camera_alt), label: const Text('拍照')),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(exec?['execution_no']?.toString() ?? '执行明细'),
        actions: [
          if (editable)
            TextButton(onPressed: saving ? null : submit, child: const Text('提交')),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  '${item?['device_name'] ?? ''} · ${item?['device_code'] ?? ''}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text('状态：${exec?['status'] ?? ''} / 明细：${item?['status'] ?? ''}'),
                const SizedBox(height: 12),
                ...results.map((r) => Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(r.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                            if (r.content.isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 4),
                                child: Text(r.content, style: Theme.of(context).textTheme.bodySmall),
                              ),
                            const SizedBox(height: 8),
                            SegmentedButton<String>(
                              segments: const [
                                ButtonSegment(value: 'pass', label: Text('合格')),
                                ButtonSegment(value: 'fail', label: Text('异常')),
                                ButtonSegment(value: 'na', label: Text('不适用')),
                              ],
                              selected: {r.status == 'pending' ? 'pass' : r.status},
                              onSelectionChanged: !editable ? null : (s) => setState(() => r.status = s.first),
                            ),
                            if (editable) ...[
                              TextFormField(
                                initialValue: r.value,
                                decoration: const InputDecoration(labelText: '结果值'),
                                onChanged: (v) => r.value = v,
                              ),
                              TextFormField(
                                initialValue: r.remark,
                                decoration: const InputDecoration(labelText: '备注'),
                                onChanged: (v) => r.remark = v,
                              ),
                            ],
                            const SizedBox(height: 8),
                            const Text('检查项照片', style: TextStyle(fontSize: 12)),
                            photoRow(
                              r.photos,
                              onAdd: () => addResultPhoto(r),
                              onRemove: (i) => setState(() => r.photos = [...r.photos]..removeAt(i)),
                            ),
                          ],
                        ),
                      ),
                    )),
                const SizedBox(height: 8),
                Text('设备现场照片', style: Theme.of(context).textTheme.titleSmall),
                photoRow(
                  itemPhotos,
                  onAdd: addItemPhoto,
                  onRemove: (i) => setState(() => itemPhotos = [...itemPhotos]..removeAt(i)),
                ),
                const SizedBox(height: 12),
                Text('执行签名', style: Theme.of(context).textTheme.titleSmall),
                if (signatureUrl != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Image.network(signatureUrl!, height: 80, fit: BoxFit.contain,
                        errorBuilder: (_, __, ___) => const Text('签名已上传')),
                  ),
                if (editable)
                  OutlinedButton.icon(
                    onPressed: sign,
                    icon: const Icon(Icons.draw),
                    label: Text(signatureUrl == null ? '手写签名' : '重签'),
                  ),
                if (editable) ...[
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: saving ? null : complete,
                    child: saving
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('完成本设备检查'),
                  ),
                ],
              ],
            ),
    );
  }
}

class _ResultRow {
  _ResultRow({
    required this.id,
    required this.name,
    required this.content,
    required this.status,
    required this.value,
    required this.remark,
    required this.photos,
    required this.rowVersion,
  });

  final String id;
  final String name;
  final String content;
  String status;
  String value;
  String remark;
  List<String> photos;
  final int rowVersion;
}
