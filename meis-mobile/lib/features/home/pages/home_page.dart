import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/utils/permission_util.dart';
import '../../../shared/widgets/meis_nav_tile.dart';
import '../../auth/providers/auth_provider.dart';
import 'device_lookup_page.dart';
import 'engineer_hub_page.dart';
import 'inspection_page.dart';
import 'inventory_page.dart';
import 'label_reprint_page.dart';
import 'maintain_page.dart';
import 'metrology_hub_page.dart';
import 'my_repairs_page.dart';
import 'pm_page.dart';
import 'power_station_hub_page.dart';
import 'power_tag_hub_page.dart';
import 'repair_page.dart';
import 'shared_hub_page.dart';

class _NavItem {
  const _NavItem({
    required this.icon,
    required this.title,
    required this.builder,
  });

  final IconData icon;
  final String title;
  final Widget Function() builder;
}

/// 「工作台」页：四列紧凑宫格（对齐微信生活服务风格，MOB-UI-02）
class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;

    final common = <_NavItem>[
      _NavItem(icon: Icons.search, title: '台账查询', builder: () => const DeviceLookupPage()),
      _NavItem(icon: Icons.qr_code_scanner, title: '扫码报修', builder: () => const RepairPage()),
      _NavItem(icon: Icons.assignment_outlined, title: '我的报修', builder: () => const MyRepairsPage()),
      _NavItem(
        icon: Icons.verified_user_outlined,
        title: '待我验收',
        builder: () => const MyRepairsPage(pendingVerifyOnly: true),
      ),
      _NavItem(icon: Icons.handyman_outlined, title: '工程师维修', builder: () => const EngineerHubPage()),
      _NavItem(icon: Icons.inventory_2_outlined, title: '移动盘点', builder: () => const InventoryPage()),
      _NavItem(icon: Icons.print_outlined, title: '标签补打', builder: () => const LabelReprintPage()),
      if (hasAnyMenu(user, SharedHubPage.menuCodes))
        _NavItem(icon: Icons.devices_other_outlined, title: '设备调配', builder: () => const SharedHubPage()),
      if (hasMenu(user, 'power_tag'))
        _NavItem(icon: Icons.electrical_services_outlined, title: '电流标签', builder: () => const PowerTagHubPage()),
      if (hasMenu(user, 'power_station'))
        _NavItem(icon: Icons.router_outlined, title: '电流基站', builder: () => const PowerStationHubPage()),
    ];

    final ops = <_NavItem>[
      _NavItem(icon: Icons.engineering_outlined, title: '移动保养', builder: () => const MaintainPage()),
      _NavItem(icon: Icons.fact_check_outlined, title: '移动巡检', builder: () => const InspectionPage()),
      _NavItem(icon: Icons.health_and_safety_outlined, title: '预防性维护', builder: () => const PmPage()),
      _NavItem(icon: Icons.science_outlined, title: '移动计量', builder: () => const MetrologyHubPage()),
    ];

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppColors.pageBgTop, AppColors.pageBg, AppColors.pageBg],
            stops: [0, 0.35, 1],
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.pageH,
              AppSpacing.lg,
              AppSpacing.pageH,
              AppSpacing.xxl,
            ),
            children: [
              _ServicePanel(
                title: '常用功能',
                items: common,
                onTap: (item) => _push(context, item.builder()),
              ),
              const SizedBox(height: AppSpacing.md),
              _ServicePanel(
                title: '运维执行',
                items: ops,
                onTap: (item) => _push(context, item.builder()),
              ),
              const SizedBox(height: AppSpacing.lg),
              Text(
                '与 Web / 小程序同 API、同权限；盘点支持离线。',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: AppColors.textMuted,
                      height: 1.45,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _push(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));
  }
}

/// 白底分组面板 + 四列图标菜单（参考微信生活服务）
class _ServicePanel extends StatelessWidget {
  const _ServicePanel({
    required this.title,
    required this.items,
    required this.onTap,
  });

  final String title;
  final List<_NavItem> items;
  final void Function(_NavItem item) onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        border: Border.all(color: AppColors.borderLight),
      ),
      padding: const EdgeInsets.fromLTRB(8, 14, 8, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 8, bottom: 6),
            child: Text(
              title,
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: items.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 4,
              mainAxisSpacing: 4,
              crossAxisSpacing: 0,
              childAspectRatio: 0.92,
            ),
            itemBuilder: (_, i) {
              final item = items[i];
              return MeisNavTile(
                grid: true,
                icon: item.icon,
                title: item.title,
                onTap: () => onTap(item),
              );
            },
          ),
        ],
      ),
    );
  }
}
