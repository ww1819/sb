import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../auth/providers/auth_provider.dart';
import 'repair_page.dart';
import 'inventory_page.dart';
import 'maintain_page.dart';
import 'inspection_page.dart';
import 'pm_page.dart';
import 'message_page.dart';
import 'my_repairs_page.dart';
import 'engineer_hub_page.dart';
import 'metrology_hub_page.dart';
import 'label_reprint_page.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;

    return Scaffold(
      appBar: AppBar(
        title: const Text('MEIS 移动端'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: '退出登录',
            onPressed: () async {
              await ref.read(authProvider.notifier).logout();
              if (context.mounted) context.go('/login');
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (user != null)
            Card(
              child: ListTile(
                leading: const CircleAvatar(child: Icon(Icons.person)),
                title: Text(user.realName ?? user.username),
                subtitle: Text('${user.tenantCode} · ${user.username}'),
              ),
            ),
          const SizedBox(height: 8),
          _HomeTile(
            icon: Icons.build,
            title: '扫码报修',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RepairPage())),
          ),
          _HomeTile(
            icon: Icons.assignment,
            title: '我的报修',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MyRepairsPage())),
          ),
          _HomeTile(
            icon: Icons.verified_user,
            title: '待我验收',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const MyRepairsPage(pendingVerifyOnly: true)),
            ),
          ),
          _HomeTile(
            icon: Icons.handyman,
            title: '工程师维修',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const EngineerHubPage())),
          ),
          _HomeTile(
            icon: Icons.inventory,
            title: '移动盘点',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InventoryPage())),
          ),
          _HomeTile(
            icon: Icons.print,
            title: '标签补打',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const LabelReprintPage())),
          ),
          _HomeTile(
            icon: Icons.engineering,
            title: '移动保养',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MaintainPage())),
          ),
          _HomeTile(
            icon: Icons.fact_check,
            title: '移动巡检',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InspectionPage())),
          ),
          _HomeTile(
            icon: Icons.health_and_safety,
            title: '移动预防性维护',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PmPage())),
          ),
          _HomeTile(
            icon: Icons.science,
            title: '移动计量',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MetrologyHubPage())),
          ),
          _HomeTile(
            icon: Icons.notifications,
            title: '消息中心',
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MessagePage())),
          ),
        ],
      ),
    );
  }
}

class _HomeTile extends StatelessWidget {
  const _HomeTile({
    required this.icon,
    required this.title,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
        title: Text(title),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
