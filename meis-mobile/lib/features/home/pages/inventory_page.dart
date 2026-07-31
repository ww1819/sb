import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/services/local_sync_service.dart';
import '../../../shared/utils/status_labels.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../../shared/widgets/meis_status_chip.dart';
import 'inventory_detail_page.dart';

class InventoryPage extends ConsumerStatefulWidget {
  const InventoryPage({super.key});

  @override
  ConsumerState<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends ConsumerState<InventoryPage> {
  List<Map<String, dynamic>> online = [];
  List<Map<String, Object?>> offline = [];
  var loading = true;
  var offlineMode = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    final sync = ref.read(localSyncServiceProvider);
    try {
      final page = await ref.read(apiServiceProvider).getPage('/asset/inventory/page', query: {
        'page': 1,
        'size': 50,
      });
      final raw = page['records'] ?? page['list'] ?? page['rows'] ?? [];
      final list = raw is List
          ? raw.map((e) => Map<String, dynamic>.from(e as Map)).toList()
          : <Map<String, dynamic>>[];
      final local = await sync.listLocalChecks();
      setState(() {
        online = list;
        offline = local;
        offlineMode = false;
      });
    } catch (_) {
      final local = await sync.listLocalChecks();
      setState(() {
        online = [];
        offline = local;
        offlineMode = true;
      });
      if (mounted && local.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('网络不可用，且无本地盘点单')),
        );
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> downloadOffline() async {
    try {
      final n = await ref.read(localSyncServiceProvider).downloadUnauditedChecks();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已下载 $n 张未审核盘点单')));
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  String _text(Map row, String key) {
    final v = row[key]?.toString().trim();
    if (v == null || v.isEmpty || v == 'null') return '—';
    return v;
  }

  Future<void> openDetail(String id, {required bool localOnly}) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => InventoryDetailPage(checkId: id, preferLocal: localOnly || offlineMode),
      ),
    );
    load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(offlineMode ? '盘点任务（离线）' : '盘点任务'),
        actions: [
          if (!offlineMode)
            IconButton(
              tooltip: '下载未审核到本地',
              icon: const Icon(Icons.download),
              onPressed: downloadOffline,
            ),
          IconButton(icon: const Icon(Icons.refresh), onPressed: load),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.pageH,
                  AppSpacing.md,
                  AppSpacing.pageH,
                  AppSpacing.xl,
                ),
                children: [
                  if (offline.isNotEmpty) ...[
                    const MeisSectionLabel('本地离线盘点单'),
                    ...offline.map((row) => _card(row, local: true)),
                  ],
                  if (!offlineMode) ...[
                    const MeisSectionLabel('服务器盘点单'),
                    if (online.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 24),
                        child: Center(
                          child: Text('暂无盘点单', style: TextStyle(color: AppColors.textMuted)),
                        ),
                      )
                    else
                      ...online.map((row) => _card(row, local: false)),
                  ],
                  if (offlineMode && offline.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 48),
                      child: Center(
                        child: Text(
                          '请联网后下载未审核盘点单',
                          style: TextStyle(color: AppColors.textMuted),
                        ),
                      ),
                    ),
                ],
              ),
            ),
    );
  }

  Widget _card(Map row, {required bool local}) {
    final status = row['status']?.toString() ?? '';
    final audit = row['audit_status']?.toString() ?? '';
    final label = resolveStatusLabel('check_status', status);
    final auditLabel = resolveStatusLabel('audit_status', audit);
    return MeisListCard(
      onTap: () {
        final id = row['id']?.toString();
        if (id != null) openDetail(id, localOnly: local);
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  _text(row, 'check_name'),
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
                ),
              ),
              if (local) ...[
                const Icon(Icons.cloud_off, size: 16, color: AppColors.textMuted),
                const SizedBox(width: 6),
              ],
              MeisStatusChip('$label / $auditLabel'),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${_text(row, 'check_no')} · ${_text(row, 'checked_count')} / ${_text(row, 'total_count')}',
            style: const TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
