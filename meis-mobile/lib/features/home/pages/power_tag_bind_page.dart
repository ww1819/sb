import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../../shared/utils/barcode_scan.dart';
import '../../auth/providers/auth_provider.dart';

/// MOB-PWR-01：查看 / 修改标签绑定设备
class PowerTagBindPage extends ConsumerStatefulWidget {
  const PowerTagBindPage({super.key, required this.tagId, this.viewOnly = false});

  final String tagId;
  final bool viewOnly;

  @override
  ConsumerState<PowerTagBindPage> createState() => _PowerTagBindPageState();
}

class _PowerTagBindPageState extends ConsumerState<PowerTagBindPage> {
  final keywordCtrl = TextEditingController();
  Map<String, dynamic>? tag;
  Map<String, dynamic>? device;
  List<Map<String, dynamic>> candidates = [];
  var loading = false;
  var looking = false;
  var saving = false;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    Future.microtask(() => _loadTag());
  }

  @override
  void dispose() {
    keywordCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadTag() async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/power/tag/${widget.tagId}');
      final m = Map<String, dynamic>.from(data as Map);
      setState(() {
        tag = m;
        if (m['device_id'] != null) {
          device = {
            'id': m['device_id'],
            'device_code': m['device_code'],
            'device_name': m['device_name'],
          };
        } else {
          device = null;
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

  Future<void> _scanDevice() async {
    if (!mounted) return;
    final code = await openBarcodeScanner(context);
    if (code == null) return;
    keywordCtrl.text = code;
    await _lookupDevices(code, preferExact: true);
  }

  Future<void> _lookupDevices(String q, {bool preferExact = false}) async {
    if (q.isEmpty) return;
    setState(() => looking = true);
    try {
      if (preferExact) {
        try {
          final data = await api.getData('/asset/device/by-code/${Uri.encodeComponent(q)}');
          final m = Map<String, dynamic>.from(data as Map);
          setState(() {
            device = m;
            candidates = [m];
          });
          return;
        } on ApiException {
          // fallthrough fuzzy
        }
      }
      final list = await api.getList('/repair/workorder/devices/lookup', query: {'q': q});
      final mapped = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      setState(() {
        candidates = mapped;
        if (mapped.length == 1) device = mapped.first;
      });
      if (mapped.isEmpty && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('未找到设备')));
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> _save({bool unbind = false}) async {
    if (tag == null) return;
    if (!unbind && device == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请先选择设备')));
      return;
    }
    setState(() => saving = true);
    try {
      final body = <String, dynamic>{
        'id': tag!['id'],
        'tag_code': tag!['tag_code'],
        'tag_name': tag!['tag_name'],
        'station_id': tag!['station_id'],
        'rated_power': tag!['rated_power'],
        'install_date': tag!['install_date'],
        'is_active': tag!['is_active'] ?? true,
        'remark': tag!['remark'],
        'device_id': unbind ? null : (device!['id'] ?? device!['device_id']),
        'client': 'app',
      };
      await api.postData('/power/tag', body);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(unbind ? '已解绑' : '绑定已更新')),
      );
      await _loadTag();
      if (unbind) setState(() => device = null);
    } on ApiException catch (e) {
      if (mounted) {
        final tip = e.message.isNotEmpty
            ? e.message
            : '请核对标签信息，可到 Web 端维护信息';
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tip)));
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请核对标签信息，可到 Web 端维护信息')),
        );
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(authProvider);
    final title = widget.viewOnly ? '查看绑定设备' : '修改绑定设备';
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(AppSpacing.pageH),
              children: [
                if (tag != null) ...[
                  Text(
                    '${tag!['tag_code'] ?? ''} · ${tag!['tag_name'] ?? ''}',
                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
                  ),
                  const SizedBox(height: AppSpacing.md),
                ],
                const MeisSectionLabel('当前绑定'),
                MeisListCard(
                  child: device == null
                      ? const Text('未绑定设备', style: TextStyle(color: AppColors.textSecondary))
                      : Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${device!['device_code'] ?? ''} · ${device!['device_name'] ?? ''}',
                              style: const TextStyle(fontWeight: FontWeight.w600),
                            ),
                          ],
                        ),
                ),
                if (!widget.viewOnly) ...[
                  const SizedBox(height: AppSpacing.lg),
                  const MeisSectionLabel('选择设备'),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: looking ? null : _scanDevice,
                          icon: const Icon(Icons.qr_code_scanner),
                          label: const Text('扫设备码'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: FilledButton(
                          onPressed: looking
                              ? null
                              : () => _lookupDevices(keywordCtrl.text.trim()),
                          child: looking
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const Text('搜索'),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: keywordCtrl,
                    decoration: const InputDecoration(hintText: '设备编码 / 名称'),
                    textInputAction: TextInputAction.search,
                    onSubmitted: (v) => _lookupDevices(v.trim()),
                  ),
                  ...candidates.map((d) {
                    final selected = (device?['id']?.toString() ?? device?['device_id']?.toString()) ==
                        (d['id']?.toString() ?? d['device_id']?.toString());
                    return MeisListCard(
                      color: selected ? AppColors.primary.withValues(alpha: 0.08) : null,
                      onTap: () => setState(() => device = d),
                      child: Text('${d['device_code'] ?? ''} · ${d['device_name'] ?? ''}'),
                    );
                  }),
                  const SizedBox(height: AppSpacing.xl),
                  FilledButton(
                    onPressed: saving ? null : () => _save(),
                    child: const Text('保存绑定'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: saving || device == null ? null : () => _save(unbind: true),
                    child: const Text('解绑设备'),
                  ),
                ],
              ],
            ),
    );
  }
}
