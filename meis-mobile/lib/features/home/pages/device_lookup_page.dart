import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/barcode_scan.dart';
import '../../../shared/utils/status_labels.dart';
import '../../../shared/widgets/meis_section_label.dart';

/// 扫码/编码查询台账轻量详情（对齐小程序 asset/detail；MOB-GAP-01）。
class DeviceLookupPage extends ConsumerStatefulWidget {
  const DeviceLookupPage({super.key, this.initialCode});

  final String? initialCode;

  @override
  ConsumerState<DeviceLookupPage> createState() => _DeviceLookupPageState();
}

class _DeviceLookupPageState extends ConsumerState<DeviceLookupPage> {
  final codeCtrl = TextEditingController();
  Map<String, dynamic>? device;
  List<Map<String, dynamic>> licenses = [];
  List<Map<String, dynamic>> trainings = [];
  var loading = false;
  String? error;

  @override
  void initState() {
    super.initState();
    final c = widget.initialCode?.trim();
    if (c != null && c.isNotEmpty) {
      codeCtrl.text = c;
      WidgetsBinding.instance.addPostFrameCallback((_) => lookupByCode(c));
    }
  }

  @override
  void dispose() {
    codeCtrl.dispose();
    super.dispose();
  }

  Future<void> scan() async {
    final code = await openBarcodeScanner(context);
    if (code == null) return;
    codeCtrl.text = code;
    await lookupByCode(code);
  }

  Future<void> lookupByCode(String raw) async {
    final code = raw.trim();
    if (code.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请输入设备编码')));
      return;
    }
    setState(() {
      loading = true;
      error = null;
      device = null;
      licenses = [];
      trainings = [];
    });
    try {
      final api = ref.read(apiServiceProvider);
      final head = await api.getData('/asset/device/by-code/${Uri.encodeComponent(code)}');
      if (head is! Map || head['id'] == null) {
        throw ApiException('未找到设备');
      }
      final id = head['id'].toString();
      final detail = await api.getData('/asset/device/$id/detail');
      final lic = await api.getData('/asset/device-license/by-device/$id');
      final tr = await api.getData('/asset/device-training-auth/by-device/$id');
      if (!mounted) return;
      setState(() {
        device = detail is Map ? Map<String, dynamic>.from(detail) : Map<String, dynamic>.from(head);
        licenses = lic is List
            ? lic.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : <Map<String, dynamic>>[];
        trainings = tr is List
            ? tr.map((e) => Map<String, dynamic>.from(e as Map)).toList()
            : <Map<String, dynamic>>[];
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => error = e.message);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  String _text(Object? v) {
    final s = v?.toString().trim();
    if (s == null || s.isEmpty || s == 'null') return '—';
    return s;
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 96,
            child: Text(label, style: const TextStyle(color: AppColors.textSecondary, fontSize: 13)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontSize: 14, height: 1.35)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('台账查询')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AppSpacing.pageH, AppSpacing.md, AppSpacing.pageH, AppSpacing.xxl),
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: codeCtrl,
                  decoration: const InputDecoration(
                    labelText: '设备编码',
                    hintText: '扫码或手输',
                  ),
                  textInputAction: TextInputAction.search,
                  onSubmitted: lookupByCode,
                ),
              ),
              const SizedBox(width: 8),
              IconButton(tooltip: '扫码', onPressed: scan, icon: const Icon(Icons.qr_code_scanner)),
              FilledButton(onPressed: () => lookupByCode(codeCtrl.text), child: const Text('查询')),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          if (loading) const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator())),
          if (error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(error!, style: const TextStyle(color: AppColors.danger)),
            ),
          if (device != null) ...[
            const MeisSectionLabel('基本信息'),
            _row('设备编码', _text(device!['device_code'])),
            _row('设备名称', _text(device!['device_name'])),
            _row('品牌', _text(device!['brand'])),
            _row('型号', _text(device!['model'])),
            _row('规格', _text(device!['specification'])),
            _row('序列号', _text(device!['serial_number'])),
            _row('使用科室', _text(device!['dept_name'])),
            _row('设备状态', resolveStatusLabel('device_status', device!['device_status'])),
            _row('位置', _text(device!['location'])),
            const SizedBox(height: AppSpacing.md),
            const MeisSectionLabel('标识与责任'),
            _row('UDI-DI', _text(device!['udi_di'])),
            _row('UDI-PI', _text(device!['udi_pi'])),
            _row('生产批号', _text(device!['lot_no'])),
            _row('资产责任人', _text(device!['asset_manager_name'])),
            _row('临床责任人', _text(device!['clinical_owner_name'])),
            _row('器械类别', resolveStatusLabel('eq_class', device!['eq_class'])),
            _row('关键等级', resolveStatusLabel('device_criticality', device!['criticality'])),
            _row('购置方式', resolveStatusLabel('acquisition_mode', device!['acquisition_mode'])),
            _row('折旧方法', resolveStatusLabel('depreciation_method', device!['depreciation_method'])),
            _row('IP', _text(device!['ip_address'])),
            _row('MAC', _text(device!['mac_address'])),
            const SizedBox(height: AppSpacing.md),
            const MeisSectionLabel('证照（只读）'),
            if (licenses.isEmpty)
              const Text('暂无证照', style: TextStyle(color: AppColors.textMuted))
            else
              ...licenses.map((r) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Text(
                      '${resolveStatusLabel('device_license_type', r['license_type'])} · ${_text(r['license_no'])}\n有效期 ${_text(r['expiry_date'])}',
                      style: const TextStyle(fontSize: 13, height: 1.4),
                    ),
                  )),
            const SizedBox(height: AppSpacing.md),
            const MeisSectionLabel('培训授权（只读）'),
            if (trainings.isEmpty)
              const Text('暂无授权记录', style: TextStyle(color: AppColors.textMuted))
            else
              ...trainings.map((r) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Text(
                      '${_text(r['user_name'])} · ${resolveStatusLabel('auth_scope', r['auth_scope'])}\n有效期至 ${_text(r['expiry_date'])}',
                      style: const TextStyle(fontSize: 13, height: 1.4),
                    ),
                  )),
          ],
        ],
      ),
    );
  }
}
