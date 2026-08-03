import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/app_constants.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../shared/widgets/app_snackbar.dart';
import '../../setup/providers/setup_provider.dart';

/// 登录后修改服务器 IP/端口（复用 setup 配置）
class ServerIpPage extends ConsumerStatefulWidget {
  const ServerIpPage({super.key});

  @override
  ConsumerState<ServerIpPage> createState() => _ServerIpPageState();
}

class _ServerIpPageState extends ConsumerState<ServerIpPage> {
  final _hostCtrl = TextEditingController();
  final _portCtrl = TextEditingController();
  var _hydrating = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _hydrate());
  }

  Future<void> _hydrate() async {
    final config = await ref.read(setupProvider.notifier).reloadFromPrefs();
    if (!mounted) return;
    _hostCtrl.text = config.host;
    _portCtrl.text = config.port.isEmpty ? AppConstants.defaultPort : config.port;
    setState(() => _hydrating = false);
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
    } else {
      final err = ref.read(setupProvider).error;
      showAppSnackBar(context, err ?? '连接失败', isError: true);
    }
  }

  Future<void> _save() async {
    ref.read(setupProvider.notifier)
      ..updateHost(_hostCtrl.text)
      ..updatePort(_portCtrl.text);

    final setup = ref.read(setupProvider);
    if (!setup.testPassed) {
      showAppSnackBar(context, '请先测试连接成功', isError: true);
      return;
    }
    await ref.read(setupProvider.notifier).completeSetup();
    if (!mounted) return;
    showAppSnackBar(context, '服务器地址已保存');
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final setup = ref.watch(setupProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('设置 IP')),
      body: SafeArea(
        child: _hydrating
            ? const Center(child: CircularProgressIndicator())
            : Padding(
                padding: const EdgeInsets.all(AppSpacing.pageH),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      '配置服务器地址',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '以下为当前维护的服务器地址。修改后请先测试连接再保存（默认端口 ${AppConstants.defaultPort}）。',
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
                    const SizedBox(height: AppSpacing.md),
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
                      const SizedBox(height: AppSpacing.md),
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
                    FilledButton(
                      onPressed: setup.testPassed ? _save : null,
                      style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                      child: const Text('保存'),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}
