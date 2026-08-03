import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/barcode_scan.dart';
import '../../../shared/utils/status_labels.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';

/// 台账查询：顶部搜索条件 + 下方资产列表；点选查看详情（MOB-GAP-01 / MOB-UI-02）。
class DeviceLookupPage extends ConsumerStatefulWidget {
  const DeviceLookupPage({super.key, this.initialCode});

  final String? initialCode;

  @override
  ConsumerState<DeviceLookupPage> createState() => _DeviceLookupPageState();
}

class _DeviceLookupPageState extends ConsumerState<DeviceLookupPage> {
  final keywordCtrl = TextEditingController();
  List<Map<String, dynamic>> rows = [];
  var page = 1;
  var total = 0;
  var loading = false;
  var loadingMore = false;
  String? error;
  var searched = false;

  static const _pageSize = 20;

  bool get hasMore => rows.length < total;

  @override
  void initState() {
    super.initState();
    final c = widget.initialCode?.trim();
    if (c != null && c.isNotEmpty) {
      keywordCtrl.text = c;
      WidgetsBinding.instance.addPostFrameCallback((_) => search(reset: true));
    }
  }

  @override
  void dispose() {
    keywordCtrl.dispose();
    super.dispose();
  }

  Future<void> scan() async {
    final code = await openBarcodeScanner(context);
    if (code == null) return;
    keywordCtrl.text = code;
    await search(reset: true);
  }

  Future<void> search({bool reset = true}) async {
    if (reset) {
      page = 1;
      setState(() {
        loading = true;
        error = null;
        searched = true;
      });
    } else {
      setState(() => loadingMore = true);
    }
    try {
      final api = ref.read(apiServiceProvider);
      final kw = keywordCtrl.text.trim();
      final data = await api.getPage('/asset/device/page', query: {
        'page': page,
        'size': _pageSize,
        if (kw.isNotEmpty) 'keyword': kw,
      });
      final records = data['records'];
      final list = records is List
          ? records.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];
      final t = data['total'];
      if (!mounted) return;
      setState(() {
        total = t is int ? t : int.tryParse('$t') ?? list.length;
        rows = reset ? list : [...rows, ...list];
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => error = e.message);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) {
        setState(() {
          loading = false;
          loadingMore = false;
        });
      }
    }
  }

  Future<void> loadMore() async {
    if (!hasMore || loading || loadingMore) return;
    page += 1;
    await search(reset: false);
  }

  void openDetail(Map<String, dynamic> row) {
    final id = row['id']?.toString();
    if (id == null || id.isEmpty) return;
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _DeviceLookupDetailPage(deviceId: id)),
    );
  }

  String _text(Object? v) {
    final s = v?.toString().trim();
    if (s == null || s.isEmpty || s == 'null') return '—';
    return s;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.pageBg,
      appBar: AppBar(title: const Text('台账查询')),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            color: Colors.white,
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.pageH,
              AppSpacing.md,
              AppSpacing.pageH,
              AppSpacing.md,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '搜索条件',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: keywordCtrl,
                        decoration: const InputDecoration(
                          labelText: '关键字',
                          hintText: '设备编码 / 名称 / 序列号',
                          isDense: true,
                          border: OutlineInputBorder(),
                        ),
                        textInputAction: TextInputAction.search,
                        onSubmitted: (_) => search(reset: true),
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton.filledTonal(
                      tooltip: '扫码',
                      onPressed: scan,
                      icon: const Icon(Icons.qr_code_scanner),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: loading ? null : () => search(reset: true),
                    child: const Text('查询'),
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(child: _buildList()),
        ],
      ),
    );
  }

  Widget _buildList() {
    if (loading && rows.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && rows.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Text(error!, style: const TextStyle(color: AppColors.danger)),
        ),
      );
    }
    if (!searched) {
      return const Center(
        child: Text(
          '请输入条件后查询，或点击扫码',
          style: TextStyle(color: AppColors.textMuted),
        ),
      );
    }
    if (rows.isEmpty) {
      return const Center(
        child: Text('暂无资产台账', style: TextStyle(color: AppColors.textMuted)),
      );
    }

    return RefreshIndicator(
      onRefresh: () => search(reset: true),
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.pageH,
          AppSpacing.md,
          AppSpacing.pageH,
          AppSpacing.xxl,
        ),
        itemCount: rows.length + 1,
        itemBuilder: (_, i) {
          if (i == rows.length) {
            if (!hasMore) {
              return const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(
                  child: Text('没有更多了', style: TextStyle(fontSize: 12, color: AppColors.textMuted)),
                ),
              );
            }
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Center(
                child: TextButton(
                  onPressed: loadingMore ? null : loadMore,
                  child: Text(loadingMore ? '加载中…' : '加载更多'),
                ),
              ),
            );
          }
          final row = rows[i];
          return MeisListCard(
            onTap: () => openDetail(row),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        _text(row['device_code']),
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                          color: AppColors.primary,
                        ),
                      ),
                    ),
                    Text(
                      resolveStatusLabel('device_status', row['device_status']),
                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  _text(row['device_name']),
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 4),
                Text(
                  [
                    if (_text(row['model']) != '—') _text(row['model']),
                    if (_text(row['dept_name']) != '—') _text(row['dept_name']),
                    if (_text(row['serial_number']) != '—') 'SN ${_text(row['serial_number'])}',
                  ].join(' · '),
                  style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _DeviceLookupDetailPage extends ConsumerStatefulWidget {
  const _DeviceLookupDetailPage({required this.deviceId});

  final String deviceId;

  @override
  ConsumerState<_DeviceLookupDetailPage> createState() => _DeviceLookupDetailPageState();
}

class _DeviceLookupDetailPageState extends ConsumerState<_DeviceLookupDetailPage> {
  Map<String, dynamic>? device;
  List<Map<String, dynamic>> licenses = [];
  List<Map<String, dynamic>> trainings = [];
  var loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final api = ref.read(apiServiceProvider);
      final id = widget.deviceId;
      final detail = await api.getData('/asset/device/$id/detail');
      final lic = await api.getData('/asset/device-license/by-device/$id');
      final tr = await api.getData('/asset/device-training-auth/by-device/$id');
      if (!mounted) return;
      setState(() {
        device = detail is Map ? Map<String, dynamic>.from(detail) : null;
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
      appBar: AppBar(title: const Text('台账详情')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : error != null
              ? Center(child: Text(error!, style: const TextStyle(color: AppColors.danger)))
              : device == null
                  ? const Center(child: Text('未找到设备', style: TextStyle(color: AppColors.textMuted)))
                  : ListView(
                      padding: const EdgeInsets.fromLTRB(
                        AppSpacing.pageH,
                        AppSpacing.md,
                        AppSpacing.pageH,
                        AppSpacing.xxl,
                      ),
                      children: [
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
                          ...licenses.map(
                            (r) => Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: Text(
                                '${resolveStatusLabel('device_license_type', r['license_type'])} · ${_text(r['license_no'])}\n有效期 ${_text(r['expiry_date'])}',
                                style: const TextStyle(fontSize: 13, height: 1.4),
                              ),
                            ),
                          ),
                        const SizedBox(height: AppSpacing.md),
                        const MeisSectionLabel('培训授权（只读）'),
                        if (trainings.isEmpty)
                          const Text('暂无授权记录', style: TextStyle(color: AppColors.textMuted))
                        else
                          ...trainings.map(
                            (r) => Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: Text(
                                '${_text(r['user_name'])} · ${resolveStatusLabel('auth_scope', r['auth_scope'])}\n有效期至 ${_text(r['expiry_date'])}',
                                style: const TextStyle(fontSize: 13, height: 1.4),
                              ),
                            ),
                          ),
                      ],
                    ),
    );
  }
}
