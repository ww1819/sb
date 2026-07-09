import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_constants.dart';
import '../../../shared/widgets/app_snackbar.dart';
import '../providers/setup_provider.dart';

class LanSetupPage extends ConsumerStatefulWidget {
  const LanSetupPage({super.key});

  @override
  ConsumerState<LanSetupPage> createState() => _LanSetupPageState();
}

class _LanSetupPageState extends ConsumerState<LanSetupPage> {
  late final TextEditingController _hostCtrl;
  late final TextEditingController _portCtrl;

  @override
  void initState() {
    super.initState();
    final config = ref.read(setupProvider).config;
    _hostCtrl = TextEditingController(text: config.host);
    _portCtrl = TextEditingController(text: config.port.isEmpty ? AppConstants.defaultPort : config.port);
  }

  @override
  void dispose() {
    _hostCtrl.dispose();
    _portCtrl.dispose();
    super.dispose();
  }

  Future<void> _testConnection() async {
    ref.read(setupProvider.notifier)
      ..updateHost(_hostCtrl.text)
      ..updatePort(_portCtrl.text);

    final ok = await ref.read(setupProvider.notifier).testConnection();
    if (!mounted) return;

    if (ok) {
      showAppSnackBar(context, '连接成功');
      await _finishSetup(auto: true);
    } else {
      final err = ref.read(setupProvider).error;
      showAppSnackBar(context, err ?? '连接失败', isError: true);
    }
  }

  Future<void> _finishSetup({bool auto = false}) async {
    final setup = ref.read(setupProvider);
    if (!setup.testPassed) {
      showAppSnackBar(context, '请先测试连接成功', isError: true);
      return;
    }
    await ref.read(setupProvider.notifier).completeSetup();
    if (!mounted) return;
    if (!auto) showAppSnackBar(context, '设置已保存');
    context.go('/login');
  }

  @override
  Widget build(BuildContext context) {
    final setup = ref.watch(setupProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('局域网设置'),
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
                '配置服务器地址',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              Text(
                '请填写 MEIS 网关所在电脑的局域网 IP 和端口（默认 8080）',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.grey),
              ),
              const SizedBox(height: 24),
              TextField(
                controller: _hostCtrl,
                keyboardType: TextInputType.url,
                decoration: const InputDecoration(
                  labelText: '服务器 IP',
                  hintText: '例如 192.168.1.100',
                  prefixIcon: Icon(Icons.computer),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _portCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '端口',
                  hintText: AppConstants.defaultPort,
                  prefixIcon: Icon(Icons.numbers),
                ),
              ),
              if (setup.testPassed) ...[
                const SizedBox(height: 16),
                const Row(
                  children: [
                    Icon(Icons.check_circle, color: Colors.green, size: 20),
                    SizedBox(width: 8),
                    Text('连接测试已通过', style: TextStyle(color: Colors.green)),
                  ],
                ),
              ],
              const Spacer(),
              OutlinedButton(
                onPressed: setup.testing ? null : _testConnection,
                child: setup.testing
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('测试连接'),
              ),
              const SizedBox(height: 12),
              ElevatedButton(
                onPressed: setup.testPassed ? () => _finishSetup() : null,
                child: const Text('完成设置'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
