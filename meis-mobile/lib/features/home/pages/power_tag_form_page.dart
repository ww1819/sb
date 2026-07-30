import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/datetime_format.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';

/// MOB-PWR-01：维护 / 新增电流标签信息
class PowerTagFormPage extends ConsumerStatefulWidget {
  const PowerTagFormPage({
    super.key,
    this.tagId,
    this.initialTagCode,
    this.lockTagCode = false,
  });

  final String? tagId;
  final String? initialTagCode;
  final bool lockTagCode;

  @override
  ConsumerState<PowerTagFormPage> createState() => _PowerTagFormPageState();
}

class _PowerTagFormPageState extends ConsumerState<PowerTagFormPage> {
  final tagCodeCtrl = TextEditingController();
  final tagNameCtrl = TextEditingController();
  final ratedPowerCtrl = TextEditingController();
  final remarkCtrl = TextEditingController();

  String? tagId;
  String? stationId;
  String? stationName;
  DateTime? installDate;
  var isActive = true;
  var lockCode = false;
  var loading = false;
  var saving = false;
  List<Map<String, dynamic>> stations = [];

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    tagId = widget.tagId;
    lockCode = widget.lockTagCode || (widget.tagId != null);
    if (widget.initialTagCode != null) {
      tagCodeCtrl.text = widget.initialTagCode!;
    }
    Future.microtask(() async {
      await _loadStations();
      if (tagId != null) {
        await _loadTag(tagId!);
      }
    });
  }

  @override
  void dispose() {
    tagCodeCtrl.dispose();
    tagNameCtrl.dispose();
    ratedPowerCtrl.dispose();
    remarkCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadStations() async {
    try {
      final page = await api.getPage('/power/station/page', query: {
        'page': 1,
        'size': 200,
        'activeOnly': true,
      });
      final list = (page['records'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      if (mounted) setState(() => stations = list);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _loadTag(String id) async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/power/tag/$id');
      final m = Map<String, dynamic>.from(data as Map);
      setState(() {
        tagId = m['id']?.toString();
        tagCodeCtrl.text = m['tag_code']?.toString() ?? '';
        tagNameCtrl.text = m['tag_name']?.toString() ?? '';
        stationId = m['station_id']?.toString();
        stationName = m['station_name']?.toString();
        ratedPowerCtrl.text = m['rated_power']?.toString() ?? '';
        remarkCtrl.text = m['remark']?.toString() ?? '';
        isActive = m['is_active'] == true || m['is_active']?.toString() == 'true';
        installDate = _parseDate(m['install_date']);
        lockCode = true;
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

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: installDate ?? now,
      firstDate: DateTime(2000),
      lastDate: DateTime(now.year + 5),
    );
    if (d != null) setState(() => installDate = d);
  }

  Future<void> _pickStation() async {
    if (stations.isEmpty) {
      await _loadStations();
    }
    if (!mounted) return;
    final selected = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        builder: (_, controller) => ListView.builder(
          controller: controller,
          itemCount: stations.length + 1,
          itemBuilder: (_, i) {
            if (i == 0) {
              return ListTile(
                title: const Text('清除基站'),
                onTap: () => Navigator.pop(ctx, <String, dynamic>{}),
              );
            }
            final s = stations[i - 1];
            return ListTile(
              title: Text('${s['station_code'] ?? ''} · ${s['station_name'] ?? ''}'),
              onTap: () => Navigator.pop(ctx, s),
            );
          },
        ),
      ),
    );
    if (selected == null) return;
    setState(() {
      if (selected.isEmpty) {
        stationId = null;
        stationName = null;
      } else {
        stationId = selected['id']?.toString();
        stationName = selected['station_name']?.toString();
      }
    });
  }

  Future<void> _save() async {
    final code = tagCodeCtrl.text.trim();
    final name = tagNameCtrl.text.trim();
    if (code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写标签编码')));
      return;
    }
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写标签名称')));
      return;
    }
    if (name == code) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('标签名称请勿与编码相同')),
      );
      return;
    }
    setState(() => saving = true);
    try {
      final body = <String, dynamic>{
        if (tagId != null) 'id': tagId,
        'tag_code': code,
        'tag_name': name,
        'station_id': stationId,
        'rated_power': ratedPowerCtrl.text.trim().isEmpty
            ? null
            : num.tryParse(ratedPowerCtrl.text.trim()),
        'install_date': installDate == null ? null : formatDate(installDate!),
        'is_active': isActive,
        'remark': remarkCtrl.text.trim().isEmpty ? null : remarkCtrl.text.trim(),
        'client': 'app',
      };
      // 编辑时保留原绑定设备（本页不改绑）
      if (tagId != null) {
        final cur = await api.getData('/power/tag/$tagId');
        final m = Map<String, dynamic>.from(cur as Map);
        body['device_id'] = m['device_id'];
      }
      final data = await api.postData('/power/tag', body);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('保存成功')));
      Navigator.pop(context, Map<String, dynamic>.from(data as Map));
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
    return Scaffold(
      appBar: AppBar(title: Text(tagId == null ? '新增标签' : '维护标签信息')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(AppSpacing.pageH),
              children: [
                const MeisSectionLabel('基本信息'),
                TextField(
                  controller: tagCodeCtrl,
                  enabled: !lockCode,
                  decoration: InputDecoration(
                    labelText: '标签编码',
                    helperText: lockCode ? '编码不可修改' : '条码无法识别时可手工填写',
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: tagNameCtrl,
                  decoration: const InputDecoration(
                    labelText: '标签名称',
                    hintText: '便于识别，勿与编码相同',
                  ),
                ),
                const SizedBox(height: 12),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('所属基站'),
                  subtitle: Text(stationName ?? '未选择', style: const TextStyle(color: AppColors.textSecondary)),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: _pickStation,
                ),
                TextField(
                  controller: ratedPowerCtrl,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: '额定功率(W)'),
                ),
                const SizedBox(height: 8),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('安装日期'),
                  subtitle: Text(
                    installDate == null ? '未设置' : formatDate(installDate!),
                    style: const TextStyle(color: AppColors.textSecondary),
                  ),
                  trailing: const Icon(Icons.calendar_today_outlined),
                  onTap: _pickDate,
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('启用'),
                  value: isActive,
                  onChanged: (v) => setState(() => isActive = v),
                ),
                TextField(
                  controller: remarkCtrl,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: '备注'),
                ),
                const SizedBox(height: AppSpacing.xl),
                FilledButton(
                  onPressed: saving ? null : _save,
                  child: saving
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('保存'),
                ),
              ],
            ),
    );
  }
}
