import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_spacing.dart';
import '../../../shared/utils/permission_util.dart';
import '../../../shared/widgets/meis_nav_tile.dart';
import '../../../shared/widgets/meis_section_label.dart';
import '../../auth/providers/auth_provider.dart';
import 'shared_loan_approve_page.dart';
import 'shared_loan_list_page.dart';
import 'shared_return_approve_page.dart';
import 'shared_return_list_page.dart';

/// 设备调配入口（MOB-SHR-01）
class SharedHubPage extends ConsumerWidget {
  const SharedHubPage({super.key});

  static const menuCodes = [
    'shared_loan',
    'shared_loan_approve',
    'shared_return',
    'shared_return_approve',
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider).user;

    return Scaffold(
      appBar: AppBar(title: const Text('设备调配')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.pageH,
          AppSpacing.md,
          AppSpacing.pageH,
          AppSpacing.xl,
        ),
        children: [
          const MeisSectionLabel('公用设备借调'),
          if (hasMenu(user, 'shared_loan'))
            MeisNavTile(
              icon: Icons.swap_horiz,
              title: '借调申请',
              subtitle: '选公用设备、提交借调；确认借出',
              onTap: () => _push(context, const SharedLoanListPage()),
            ),
          if (hasMenu(user, 'shared_loan_approve'))
            MeisNavTile(
              icon: Icons.fact_check_outlined,
              title: '借调审核',
              subtitle: '待审借调单通过 / 驳回',
              onTap: () => _push(context, const SharedLoanApprovePage()),
            ),
          if (hasMenu(user, 'shared_return'))
            MeisNavTile(
              icon: Icons.assignment_return_outlined,
              title: '归还申请',
              subtitle: '对借出中单据发起归还',
              onTap: () => _push(context, const SharedReturnListPage()),
            ),
          if (hasMenu(user, 'shared_return_approve'))
            MeisNavTile(
              icon: Icons.task_alt_outlined,
              title: '归还审核',
              subtitle: '待审归还单通过 / 驳回',
              onTap: () => _push(context, const SharedReturnApprovePage()),
            ),
        ],
      ),
    );
  }

  void _push(BuildContext context, Widget page) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => page));
  }
}
