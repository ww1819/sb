import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/models/server_config.dart';
import '../../../shared/widgets/app_snackbar.dart';
import '../providers/setup_provider.dart';

class EthernetSetupPage extends ConsumerStatefulWidget {
  const EthernetSetupPage({super.key});

  @override
  ConsumerState<EthernetSetupPage> createState() => _EthernetSetupPageState();
}

class _EthernetSetupPageState extends ConsumerState<EthernetSetupPage> {
  late final TextEditingController _nameCtrl;

  @override
  void initState() {
    super.initState();
    _nameCtrl = TextEditingController(text: ref.read(setupProvider).config.hospitalName ?? '');
    ref.read(setupProvider.notifier).setMode(SetupMode.ethernet);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    ref.read(setupProvider.notifier).updateHospitalName(_nameCtrl.text);
    await ref.read(setupProvider.notifier).saveEthernetPlaceholder();
    if (!mounted) return;
    final err = ref.read(setupProvider).error;
    if (err != null) {
      showAppSnackBar(context, err, isError: true);
      return;
    }
    showAppSnackBar(context, '以太网模式开发中，请使用局域网方式连接');
    context.go('/setup/mode');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('以太网设置'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/setup/mode'),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '医院信息',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              Text(
                '以太网自动发现功能开发中，当前请返回选择局域网方式',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.grey),
              ),
              const SizedBox(height: 24),
              TextField(
                controller: _nameCtrl,
                decoration: const InputDecoration(
                  labelText: '医院全称',
                  hintText: '例如 XX市第一人民医院',
                  prefixIcon: Icon(Icons.local_hospital_outlined),
                ),
                onChanged: ref.read(setupProvider.notifier).updateHospitalName,
              ),
              const Spacer(),
              ElevatedButton(
                onPressed: _save,
                child: const Text('保存'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
