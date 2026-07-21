import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'my_repair_detail_page.dart';

/// 我的报修与待验收（MOB-F-06 / MOB-F-04）
class MyRepairsPage extends ConsumerStatefulWidget {
  const MyRepairsPage({super.key, this.pendingVerifyOnly = false});

  final bool pendingVerifyOnly;

  @override
  ConsumerState<MyRepairsPage> createState() => _MyRepairsPageState();
}

class _MyRepairsPageState extends ConsumerState<MyRepairsPage> {
  List<Map<String, dynamic>> rows = [];
  var loading = true;
  var pendingOnly = false;

  static const statusLabel = {
    'draft': '草稿',
    'reported': '已报修',
    'dispatching': '派工中',
    'pending_accept': '待接单',
    'accepted': '已接单',
    'repairing': '维修中',
    'suspended': '挂起',
    'pending_verify': '待验收',
    'verify_rejected': '拒绝验收',
    'verified': '已验收',
    'closed': '已关闭',
    'cancelled': '已取消',
  };

  @override
  void initState() {
    super.initState();
    pendingOnly = widget.pendingVerifyOnly;
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final api = ref.read(apiServiceProvider);
      final page = await api.getPage('/repair/workorder/mine', query: {
        'page': 1,
        'size': 50,
        if (pendingOnly) 'pendingVerifyOnly': true,
      });
      final list = page['records'] is List ? page['records'] as List : [];
      setState(() {
        rows = list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
      setState(() => rows = []);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(pendingOnly ? '待我验收' : '我的报修'),
        actions: [
          TextButton(
            onPressed: () {
              setState(() => pendingOnly = !pendingOnly);
              load();
            },
            child: Text(pendingOnly ? '全部' : '仅待验收'),
          ),
        ],
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: rows.isEmpty
                  ? ListView(
                      children: const [
                        SizedBox(height: 120),
                        Center(child: Text('暂无工单')),
                      ],
                    )
                  : ListView.builder(
                      itemCount: rows.length,
                      itemBuilder: (_, i) {
                        final r = rows[i];
                        final st = r['status']?.toString() ?? '';
                        return Card(
                          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          child: ListTile(
                            title: Text(r['wo_no']?.toString() ?? ''),
                            subtitle: Text(
                              '${r['device_name'] ?? ''} · ${statusLabel[st] ?? st}\n'
                              '${r['fault_description'] ?? ''}',
                              maxLines: 3,
                              overflow: TextOverflow.ellipsis,
                            ),
                            isThreeLine: true,
                            trailing: st == 'pending_verify'
                                ? Chip(
                                    label: const Text('验收'),
                                    backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                                  )
                                : const Icon(Icons.chevron_right),
                            onTap: () async {
                              final id = r['id']?.toString();
                              if (id == null) return;
                              await Navigator.push(
                                context,
                                MaterialPageRoute(builder: (_) => MyRepairDetailPage(workorderId: id)),
                              );
                              load();
                            },
                          ),
                        );
                      },
                    ),
            ),
    );
  }
}
