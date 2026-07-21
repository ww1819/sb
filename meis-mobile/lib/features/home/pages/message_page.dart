import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'my_repairs_page.dart';
import 'ops_hub_page.dart';

class MessagePage extends ConsumerStatefulWidget {
  const MessagePage({super.key});

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
    return Scaffold(
      appBar: AppBar(
        title: const Text('消息中心'),
        actions: [
          IconButton(onPressed: load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: items.isEmpty
                  ? ListView(
                      children: const [
                        SizedBox(height: 120),
                        Center(child: Text('暂无消息')),
                      ],
                    )
                  : ListView.builder(
                      itemCount: items.length,
                      itemBuilder: (_, i) {
                        final m = items[i];
                        final unread = m['is_read'] != true;
                        return ListTile(
                          leading: Icon(
                            unread ? Icons.mark_email_unread : Icons.mark_email_read,
                            color: unread ? Theme.of(context).colorScheme.primary : null,
                          ),
                          title: Text(
                            m['title']?.toString() ?? '',
                            style: TextStyle(fontWeight: unread ? FontWeight.w600 : FontWeight.normal),
                          ),
                          subtitle: Text(
                            '${m['content'] ?? ''}\n${m['created_at'] ?? ''}',
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                          ),
                          isThreeLine: true,
                          onTap: () => onTap(m),
                        );
                      },
                    ),
            ),
    );
  }
}
