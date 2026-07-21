import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';
import 'engineer_workorder_detail_page.dart';

/// 工程师移动维修工作台（MOB-F-07 一期）
class EngineerHubPage extends ConsumerStatefulWidget {
  const EngineerHubPage({super.key});

  @override
  ConsumerState<EngineerHubPage> createState() => _EngineerHubPageState();
}

class _EngineerHubPageState extends ConsumerState<EngineerHubPage>
    with SingleTickerProviderStateMixin {
  late final TabController tabs;
  var isEngineer = false;
  var checking = true;

  @override
  void initState() {
    super.initState();
    tabs = TabController(length: 3, vsync: this);
    checkRole();
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  Future<void> checkRole() async {
    try {
      final api = ref.read(apiServiceProvider);
      final me = await api.getData('/repair/engineer/me');
      setState(() {
        isEngineer = me is Map && me['isRepairEngineer'] == true;
        checking = false;
      });
    } catch (_) {
      setState(() {
        isEngineer = false;
        checking = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (checking) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (!isEngineer) {
      return Scaffold(
        appBar: AppBar(title: const Text('工程师维修')),
        body: const Center(child: Text('当前账号不是维修工程师\n请在 Web「维修工程师管理」中勾选')),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('工程师维修'),
        bottom: TabBar(
          controller: tabs,
          tabs: const [
            Tab(text: '可抢单'),
            Tab(text: '待接单'),
            Tab(text: '我的工单'),
          ],
        ),
      ),
      body: TabBarView(
        controller: tabs,
        children: const [
          _InboxList(scope: 'grab'),
          _InboxList(scope: 'pending'),
          _InboxList(scope: 'mine'),
        ],
      ),
    );
  }
}

class _InboxList extends ConsumerStatefulWidget {
  const _InboxList({required this.scope});

  final String scope;

  @override
  ConsumerState<_InboxList> createState() => _InboxListState();
}

class _InboxListState extends ConsumerState<_InboxList> {
  List<Map<String, dynamic>> rows = [];
  var loading = true;

  static const statusLabel = {
    'reported': '已报修',
    'dispatching': '派工中',
    'pending_accept': '待接单',
    'accepted': '已接单',
    'repairing': '维修中',
    'suspended': '挂起',
    'pending_verify': '待验收',
    'verify_rejected': '拒绝验收',
    'closed': '已关闭',
  };

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final page = await ref.read(apiServiceProvider).getPage(
        '/repair/workorder/engineer-inbox',
        query: {'scope': widget.scope, 'page': 1, 'size': 50},
      );
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
    if (loading) return const Center(child: CircularProgressIndicator());
    return RefreshIndicator(
      onRefresh: load,
      child: rows.isEmpty
          ? ListView(children: const [SizedBox(height: 120), Center(child: Text('暂无工单'))])
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
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () async {
                      final id = r['id']?.toString();
                      if (id == null) return;
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => EngineerWorkorderDetailPage(workorderId: id),
                        ),
                      );
                      load();
                    },
                  ),
                );
              },
            ),
    );
  }
}
