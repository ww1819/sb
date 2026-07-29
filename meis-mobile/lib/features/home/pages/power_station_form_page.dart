import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';

/// MOB-PWR-02：新增 / 修改基站
class PowerStationFormPage extends ConsumerStatefulWidget {
  const PowerStationFormPage({super.key, this.stationId});

  final String? stationId;

  @override
  ConsumerState<PowerStationFormPage> createState() => _PowerStationFormPageState();
}

class _PowerStationFormPageState extends ConsumerState<PowerStationFormPage> {
  final codeCtrl = TextEditingController();
  final nameCtrl = TextEditingController();
  final locationCtrl = TextEditingController();
  final ipCtrl = TextEditingController();
  final remarkCtrl = TextEditingController();

  String? stationId;
  String? campusId;
  String? campusName;
  var protocolType = 'mqtt';
  var status = 'online';
  var isActive = true;
  var lockCode = false;
  var loading = false;
  var saving = false;
  List<Map<String, dynamic>> campuses = [];

  static const protocols = ['mqtt', 'modbus', 'http', 'other'];
  static const statuses = ['online', 'offline', 'fault', 'maintenance'];

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    stationId = widget.stationId;
    lockCode = widget.stationId != null;
    Future.microtask(() async {
      await _loadCampuses();
      if (stationId != null) await _load(stationId!);
    });
  }

  @override
  void dispose() {
    codeCtrl.dispose();
    nameCtrl.dispose();
    locationCtrl.dispose();
    ipCtrl.dispose();
    remarkCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadCampuses() async {
    try {
      final list = await api.getList('/system/campuses');
      if (mounted) {
        setState(() {
          campuses = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
        });
      }
    } on ApiException {
      /* optional */
    }
  }

  Future<void> _load(String id) async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/power/station/$id');
      final m = Map<String, dynamic>.from(data as Map);
      setState(() {
        stationId = m['id']?.toString();
        codeCtrl.text = m['station_code']?.toString() ?? '';
        nameCtrl.text = m['station_name']?.toString() ?? '';
        locationCtrl.text = m['location']?.toString() ?? '';
        ipCtrl.text = m['ip_address']?.toString() ?? '';
        remarkCtrl.text = m['remark']?.toString() ?? '';
        campusId = m['campus_id']?.toString();
        campusName = m['campus_name']?.toString();
        protocolType = m['protocol_type']?.toString() ?? 'mqtt';
        status = m['status']?.toString() ?? 'online';
        isActive = m['is_active'] == true || m['is_active']?.toString() == 'true';
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

  Future<void> _pickCampus() async {
    final selected = await showModalBottomSheet<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => SafeArea(
        child: ListView(
          children: [
            ListTile(
              title: const Text('清除院区'),
              onTap: () => Navigator.pop(ctx, <String, dynamic>{}),
            ),
            ...campuses.map(
              (c) => ListTile(
                title: Text('${c['campus_code'] ?? ''} · ${c['campus_name'] ?? ''}'),
                onTap: () => Navigator.pop(ctx, c),
              ),
            ),
          ],
        ),
      ),
    );
    if (selected == null) return;
    setState(() {
      if (selected.isEmpty) {
        campusId = null;
        campusName = null;
      } else {
        campusId = selected['id']?.toString();
        campusName = selected['campus_name']?.toString();
      }
    });
  }

  Future<void> _save() async {
    final code = codeCtrl.text.trim();
    final name = nameCtrl.text.trim();
    if (code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写基站编码')));
      return;
    }
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写基站名称')));
      return;
    }
    setState(() => saving = true);
    try {
      await api.postData('/power/station', {
        if (stationId != null) 'id': stationId,
        'station_code': code,
        'station_name': name,
        'campus_id': campusId,
        'location': locationCtrl.text.trim().isEmpty ? null : locationCtrl.text.trim(),
        'ip_address': ipCtrl.text.trim().isEmpty ? null : ipCtrl.text.trim(),
        'protocol_type': protocolType,
        'status': status,
        'is_active': isActive,
        'remark': remarkCtrl.text.trim().isEmpty ? null : remarkCtrl.text.trim(),
        'client': 'app',
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('保存成功')));
      Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.message.isNotEmpty ? e.message : '请核对基站信息，可到 Web 端维护')),
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
      appBar: AppBar(title: Text(stationId == null ? '新增基站' : '修改基站')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(AppSpacing.pageH),
              children: [
                const MeisSectionLabel('基本信息'),
                TextField(
                  controller: codeCtrl,
                  enabled: !lockCode,
                  decoration: InputDecoration(
                    labelText: '基站编码',
                    helperText: lockCode ? '编码不可修改' : null,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: '基站名称')),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('院区'),
                  subtitle: Text(campusName ?? '未选择', style: const TextStyle(color: AppColors.textSecondary)),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: _pickCampus,
                ),
                TextField(controller: locationCtrl, decoration: const InputDecoration(labelText: '安装位置')),
                TextField(controller: ipCtrl, decoration: const InputDecoration(labelText: 'IP地址')),
                DropdownButtonFormField<String>(
                  value: protocols.contains(protocolType) ? protocolType : 'mqtt',
                  decoration: const InputDecoration(labelText: '协议类型'),
                  items: protocols
                      .map((p) => DropdownMenuItem(value: p, child: Text(p)))
                      .toList(),
                  onChanged: (v) {
                    if (v != null) setState(() => protocolType = v);
                  },
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  value: statuses.contains(status) ? status : 'online',
                  decoration: const InputDecoration(labelText: '状态'),
                  items: statuses
                      .map((p) => DropdownMenuItem(value: p, child: Text(p)))
                      .toList(),
                  onChanged: (v) {
                    if (v != null) setState(() => status = v);
                  },
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
