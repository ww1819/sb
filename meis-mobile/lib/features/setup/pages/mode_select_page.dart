import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/models/server_config.dart';
import '../../../shared/widgets/meis_brand_header.dart';
import '../../../shared/widgets/mode_select_card.dart';
import '../providers/setup_provider.dart';

class ModeSelectPage extends ConsumerWidget {
  const ModeSelectPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 32),
              const MeisBrandHeader(subtitle: AppConstants.appSubtitle),
              const SizedBox(height: 40),
              Text(
                '选择连接方式',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              Text(
                '首次使用需配置与服务器的连接',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.grey),
              ),
              const SizedBox(height: 24),
              ModeSelectCard(
                icon: Icons.wifi,
                title: '局域网',
                description: '通过 IP 和端口连接院内服务器',
                onTap: () {
                  ref.read(setupProvider.notifier).setMode(SetupMode.lan);
                  context.push('/setup/lan');
                },
              ),
              const SizedBox(height: 12),
              ModeSelectCard(
                icon: Icons.settings_ethernet,
                title: '以太网',
                description: '输入医院全称自动发现服务（开发中）',
                onTap: () {
                  ref.read(setupProvider.notifier).setMode(SetupMode.ethernet);
                  context.push('/setup/ethernet');
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
