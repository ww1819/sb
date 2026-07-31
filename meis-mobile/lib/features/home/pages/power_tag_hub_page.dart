import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/barcode_scan.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';
import 'power_readings_page.dart';
import 'power_tag_bind_page.dart';
import 'power_tag_form_page.dart';

/// MOB-PWR-01：电流标签 — 扫码 / 手输查询 / 新增入口
class PowerTagHubPage extends ConsumerStatefulWidget {
  const PowerTagHubPage({super.key});

  @override
  ConsumerState<PowerTagHubPage> createState() => _PowerTagHubPageState();
}

class _PowerTagHubPageState extends ConsumerState<PowerTagHubPage> {
  final keywordCtrl = TextEditingController();
  List<Map<String, dynamic>> results = [];
  var looking = false;

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void dispose() {
    keywordCtrl.dispose();
    super.dispose();
  }

  Future<void> _scan() async {
    if (!mounted) return;
    final code = await openBarcodeScanner(context);
    if (code == null) return;
    await _lookupExact(code);
  }

  Future<void> _lookupExact(String code) async {
    setState(() => looking = true);
    try {
      final data = await api.getData('/power/tag/by-code/${Uri.encodeComponent(code)}');
      final tag = Map<String, dynamic>.from(data as Map);
      if (!mounted) return;
      await _openActions(tag);
    } on ApiException catch (e) {
      if (e.statusCode == 404 ||
          e.message.contains('不存在') ||
          e.message.contains('标签不存在') ||
          e.message.contains('not found')) {
        if (!mounted) return;
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('未找到标签'),
            content: Text('编码「$code」不存在，是否新增电流监测标签？'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('新增')),
            ],
          ),
        );
        if (ok == true && mounted) {
          await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => PowerTagFormPage(initialTagCode: code, lockTagCode: true),
            ),
          );
        }
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> _search() async {
    final kw = keywordCtrl.text.trim();
    if (kw.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请输入标签编码或名称')),
      );
      return;
    }
    setState(() => looking = true);
    try {
      final page = await api.getPage('/power/tag/page', query: {
        'page': 1,
        'size': 50,
        'keyword': kw,
      });
      final list = (page['records'] as List? ?? [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      if (!mounted) return;
      if (list.isEmpty) {
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('未找到标签'),
            content: Text('未查询到「$kw」，是否新增？'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('新增')),
            ],
          ),
        );
        if (ok == true && mounted) {
          await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => PowerTagFormPage(initialTagCode: kw, lockTagCode: false),
            ),
          );
        }
        setState(() => results = []);
        return;
      }
      if (list.length == 1) {
        await _openActions(list.first);
        setState(() => results = list);
        return;
      }
      setState(() => results = list);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => looking = false);
    }
  }

  Future<void> _openActions(Map<String, dynamic> tag) async {
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text('${tag['tag_code'] ?? ''} · ${tag['tag_name'] ?? ''}'),
              subtitle: Text(
                '设备：${tag['device_code'] ?? '未绑定'} ${tag['device_name'] ?? ''}'.trim(),
              ),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('维护标签信息'),
              onTap: () {
                Navigator.pop(ctx);
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => PowerTagFormPage(tagId: tag['id']?.toString())),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.devices_outlined),
              title: const Text('查看绑定设备'),
              onTap: () {
                Navigator.pop(ctx);
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PowerTagBindPage(tagId: tag['id']!.toString(), viewOnly: true),
                  ),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.link_outlined),
              title: const Text('修改绑定设备'),
              onTap: () {
                Navigator.pop(ctx);
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PowerTagBindPage(tagId: tag['id']!.toString()),
                  ),
                );
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
                      title: '监测记录 · ${tag['tag_code'] ?? ''}',
                      listPath: '/power/tag/${tag['id']}/readings/page',
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

  Future<void> _createDirect() async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const PowerTagFormPage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(authProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('电流标签')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.pageH),
        children: [
          const MeisSectionLabel('查询'),
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: looking ? null : _scan,
                  icon: const Icon(Icons.qr_code_scanner),
                  label: const Text('扫码查询'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: looking ? null : _createDirect,
                  icon: const Icon(Icons.add),
                  label: const Text('直接新增'),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          TextField(
            controller: keywordCtrl,
            decoration: InputDecoration(
              hintText: '手工输入编码 / 名称',
              suffixIcon: IconButton(
                icon: looking
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.search),
                onPressed: looking ? null : _search,
              ),
            ),
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _search(),
          ),
          if (results.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            const MeisSectionLabel('查询结果（点选操作）'),
            ...results.map((t) {
              return MeisListCard(
                onTap: () => _openActions(t),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${t['tag_code'] ?? ''} · ${t['tag_name'] ?? ''}',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '设备：${t['device_code'] ?? '—'} ${t['device_name'] ?? ''}'.trim(),
                      style: const TextStyle(color: AppColors.textSecondary, fontSize: 13),
                    ),
                    Text(
                      '基站：${t['station_name'] ?? '—'} · ${t['is_active'] == true || t['is_active'] == 'true' ? '启用' : '停用'}',
                      style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
                    ),
                  ],
                ),
              );
            }),
          ],
        ],
      ),
    );
  }
}
