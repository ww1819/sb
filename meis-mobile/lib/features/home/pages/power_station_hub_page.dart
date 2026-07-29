import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';
import 'power_readings_page.dart';
import 'power_station_form_page.dart';

/// MOB-PWR-02：电流基站列表 / 关键字查询
class PowerStationHubPage extends ConsumerStatefulWidget {
  const PowerStationHubPage({super.key});

  @override
  ConsumerState<PowerStationHubPage> createState() => _PowerStationHubPageState();
}

class _PowerStationHubPageState extends ConsumerState<PowerStationHubPage> {
  final keywordCtrl = TextEditingController();
  List<Map<String, dynamic>> rows = [];
  var looking = false;
  var page = 1;
  var total = 0;
  final size = 30;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    Future.microtask(_search);
  }

  @override
  void dispose() {
    keywordCtrl.dispose();
    super.dispose();
  }

  Future<void> _search({bool reset = true}) async {
    if (reset) page = 1;
    setState(() => looking = true);
    try {
      final data = await api.getPage('/power/station/page', query: {
        'page': page,
        'size': size,
        if (keywordCtrl.text.trim().isNotEmpty) 'keyword': keywordCtrl.text.trim(),
      });
      final list = (data['records'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      setState(() {
        if (reset || page == 1) {
          rows = list;
        } else {
          rows = [...rows, ...list];
        }
        total = (data['total'] is num) ? (data['total'] as num).toInt() : list.length;
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> _openActions(Map<String, dynamic> s) async {
    await showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text('${s['station_code'] ?? ''} · ${s['station_name'] ?? ''}'),
              subtitle: Text('位置：${s['location'] ?? '—'} · ${s['status'] ?? ''}'),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('修改基站'),
              onTap: () async {
                Navigator.pop(ctx);
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PowerStationFormPage(stationId: s['id']?.toString()),
                  ),
                );
                _search(reset: false);
              },
            ),
            ListTile(
              leading: const Icon(Icons.timeline_outlined),
              title: const Text('监测记录'),
              onTap: () {
                Navigator.pop(ctx);
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PowerReadingsPage(
                      title: '监测记录 · ${s['station_code'] ?? ''}',
                      listPath: '/power/station/${s['id']}/readings/page',
                      showStationCode: true,
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(authProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('电流基站'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const PowerStationFormPage()),
              );
              _search();
            },
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(AppSpacing.pageH),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const MeisSectionLabel('查询（无扫码）'),
                TextField(
                  controller: keywordCtrl,
                  decoration: InputDecoration(
                    hintText: '编码 / 名称 / 位置',
                    suffixIcon: IconButton(
                      icon: looking
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.search),
                      onPressed: looking ? null : () => _search(),
                    ),
                  ),
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _search(),
                ),
              ],
            ),
          ),
          Expanded(
            child: looking && rows.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : rows.isEmpty
                    ? const Center(child: Text('暂无基站', style: TextStyle(color: AppColors.textSecondary)))
                    : ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.pageH),
                        itemCount: rows.length,
                        itemBuilder: (_, i) {
                          final s = rows[i];
                          return MeisListCard(
                            onTap: () => _openActions(s),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '${s['station_code'] ?? ''} · ${s['station_name'] ?? ''}',
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                                Text(
                                  '位置：${s['location'] ?? '—'} · ${s['campus_name'] ?? ''}',
                                  style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                                ),
                                Text(
                                  '${s['status'] ?? ''} · ${s['is_active'] == true || s['is_active'] == 'true' ? '启用' : '停用'}',
                                  style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
          ),
          if (total > size)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: TextButton(
                onPressed: looking
                    ? null
                    : () {
                        page++;
                        _search(reset: false);
                      },
                child: Text('加载更多（共 $total）'),
              ),
            ),
        ],
      ),
    );
  }
}
