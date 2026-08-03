import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/utils/permission_util.dart';
import '../../../shared/widgets/meis_nav_tile.dart';
import '../../../shared/widgets/meis_section_label.dart';
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

/// 「功能」页：原首页业务入口（MOB-UI-02）
class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;

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
              const MeisSectionLabel('常用功能'),
              MeisNavTile(
                icon: Icons.search,
                title: '扫码查台账',
                subtitle: 'UDI / 责任人 / 证照授权只读',
                onTap: () => _push(context, const DeviceLookupPage()),
              ),
              MeisNavTile(
                icon: Icons.qr_code_scanner,
                title: '扫码报修',
                subtitle: '扫码填报；可存草稿后提交',
                primary: true,
                onTap: () => _push(context, const RepairPage()),
              ),
              MeisNavTile(
                icon: Icons.assignment_outlined,
                title: '我的报修',
                subtitle: '申请单草稿·撤回；进度查询',
                onTap: () => _push(context, const MyRepairsPage()),
              ),
              MeisNavTile(
                icon: Icons.verified_user_outlined,
                title: '待我验收',
                subtitle: '科室/报修人验收确认',
                onTap: () => _push(context, const MyRepairsPage(pendingVerifyOnly: true)),
              ),
              MeisNavTile(
                icon: Icons.handyman_outlined,
                title: '工程师维修',
                subtitle: '抢单 / 接单 / 进程 / 完工',
                onTap: () => _push(context, const EngineerHubPage()),
              ),
              MeisNavTile(
                icon: Icons.inventory_2_outlined,
                title: '移动盘点',
                subtitle: '离线下载与回传',
                onTap: () => _push(context, const InventoryPage()),
              ),
              MeisNavTile(
                icon: Icons.print_outlined,
                title: '标签补打',
                subtitle: '现场补打记录与预览',
                onTap: () => _push(context, const LabelReprintPage()),
              ),
              if (hasAnyMenu(user, SharedHubPage.menuCodes))
                MeisNavTile(
                  icon: Icons.devices_other_outlined,
                  title: '设备调配',
                  subtitle: '公用设备借调 / 归还',
                  onTap: () => _push(context, const SharedHubPage()),
                ),
              if (hasMenu(user, 'power_tag'))
                MeisNavTile(
                  icon: Icons.electrical_services_outlined,
                  title: '电流标签',
                  subtitle: '扫码查询 / 维护 / 改绑',
                  onTap: () => _push(context, const PowerTagHubPage()),
                ),
              if (hasMenu(user, 'power_station'))
                MeisNavTile(
                  icon: Icons.router_outlined,
                  title: '电流基站',
                  subtitle: '查询维护 / 监测记录',
                  onTap: () => _push(context, const PowerStationHubPage()),
                ),
              const SizedBox(height: AppSpacing.md),
              const MeisSectionLabel('运维执行'),
              MeisNavTile(
                icon: Icons.engineering_outlined,
                title: '移动保养',
                subtitle: '扫码执行保养任务',
                onTap: () => _push(context, const MaintainPage()),
              ),
              MeisNavTile(
                icon: Icons.fact_check_outlined,
                title: '移动巡检',
                subtitle: '扫码执行巡检任务',
                onTap: () => _push(context, const InspectionPage()),
              ),
              MeisNavTile(
                icon: Icons.health_and_safety_outlined,
                title: '预防性维护',
                subtitle: '扫码执行 PM 任务',
                onTap: () => _push(context, const PmPage()),
              ),
              MeisNavTile(
                icon: Icons.science_outlined,
                title: '移动计量',
                subtitle: '扫码执行计量任务',
                onTap: () => _push(context, const MetrologyHubPage()),
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
