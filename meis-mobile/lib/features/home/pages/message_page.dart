import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/services/api_service.dart';
import '../../../shared/utils/datetime_format.dart';
import '../../../shared/widgets/meis_list_card.dart';
import '../../../shared/widgets/meis_status_chip.dart';
import 'my_repairs_page.dart';
import 'ops_hub_page.dart';

class MessagePage extends ConsumerStatefulWidget {
  const MessagePage({super.key, this.embedded = false});

  /// 作为底栏 Tab 内嵌时隐藏独立 AppBar 返回语义，改用页内标题栏
  final bool embedded;

  @override
  ConsumerState<MessagePage> createState() => _MessagePageState();
}

class _MessagePageState extends ConsumerState<MessagePage> {
  List<Map<String, dynamic>> items = [];
  var loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final api = ref.read(apiServiceProvider);
      final data = await api.getList('/notification/messages');
      setState(() {
        items = data.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } catch (_) {
      setState(() => items = []);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> onTap(Map<String, dynamic> msg) async {
    final id = msg['id']?.toString();
    final type = msg['message_type']?.toString() ?? msg['notification_type']?.toString() ?? '';
    try {
      if (id != null && msg['is_read'] != true) {
        await ref.read(apiServiceProvider).postData('/notification/messages/$id/read', {});
        setState(() => msg['is_read'] = true);
      }
    } catch (_) {}

    if (!mounted) return;
    if (type.contains('ops') || type.contains('maintain') || type.contains('inspect') || type.contains('pm')) {
      final cfg = type.contains('inspect')
          ? OpsModuleConfig.inspect
          : type.contains('pm')
              ? OpsModuleConfig.pm
              : OpsModuleConfig.maintain;
      await Navigator.push(context, MaterialPageRoute(builder: (_) => OpsHubPage(config: cfg)));
      return;
    }
    if (type.contains('repair') || type.contains('workorder')) {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => const MyRepairsPage()));
    }
  }

  @override
  Widget build(BuildContext context) {
    final body = loading
        ? const Center(child: CircularProgressIndicator())
        : RefreshIndicator(
            onRefresh: load,
            child: items.isEmpty
                ? ListView(
                    children: const [
                      SizedBox(height: 120),
                      Center(child: Text('暂无消息', style: TextStyle(color: AppColors.textMuted))),
                    ],
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(
                      AppSpacing.pageH,
                      AppSpacing.md,
                      AppSpacing.pageH,
                      AppSpacing.xl,
                    ),
                    itemCount: items.length,
                    itemBuilder: (_, i) {
                      final m = items[i];
                      final unread = m['is_read'] != true;
                      final content = m['content']?.toString() ?? '';
                      return MeisListCard(
                        onTap: () => onTap(m),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              unread ? Icons.mark_email_unread_outlined : Icons.mark_email_read_outlined,
                              color: unread ? AppColors.primary : AppColors.textMuted,
                              size: 22,
                            ),
                            const SizedBox(width: AppSpacing.md),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          m['title']?.toString() ?? '',
                                          style: TextStyle(
                                            fontWeight: unread ? FontWeight.w600 : FontWeight.w500,
                                            fontSize: 15,
                                            color: AppColors.textPrimary,
                                          ),
                                        ),
                                      ),
                                      if (unread) const MeisStatusChip('未读', emphasize: true),
                                    ],
                                  ),
                                  if (content.isNotEmpty) ...[
                                    const SizedBox(height: 6),
                                    Text(
                                      content,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 13,
                                        color: AppColors.textSecondary,
                                        height: 1.35,
                                      ),
                                    ),
                                  ],
                                  const SizedBox(height: 4),
                                  Text(
                                    formatDisplayDateTime(m['created_at']),
                                    style: const TextStyle(fontSize: 12, color: AppColors.textMuted),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          );

    if (widget.embedded) {
      return Scaffold(
        body: Container(
          color: AppColors.pageBg,
          child: SafeArea(
            bottom: false,
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.pageH,
                    AppSpacing.md,
                    AppSpacing.sm,
                    AppSpacing.sm,
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          '消息',
                          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: AppColors.textPrimary,
                              ),
                        ),
                      ),
                      IconButton(onPressed: load, icon: const Icon(Icons.refresh)),
                    ],
                  ),
                ),
                Expanded(child: body),
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('消息中心'),
        actions: [
          IconButton(onPressed: load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: body,
    );
  }
}
