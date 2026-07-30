import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';

/// 扫码页：识别成功返回纯文本编码；失败由调用方提示。
/// 进入页内自行申请相机权限后再启动预览（避免黑屏感叹号）。
class RepairScanPage extends StatefulWidget {
  const RepairScanPage({super.key});

  @override
  State<RepairScanPage> createState() => _RepairScanPageState();
}

class _RepairScanPageState extends State<RepairScanPage> with WidgetsBindingObserver {
  MobileScannerController? controller;
  var handled = false;
  var preparing = true;
  String? blockMessage;
  var permanentlyDenied = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    prepareCamera();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller?.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // 从系统设置返回后自动重试
    if (state == AppLifecycleState.resumed && blockMessage != null) {
      prepareCamera();
    }
  }

  Future<void> prepareCamera() async {
    setState(() {
      preparing = true;
      blockMessage = null;
      permanentlyDenied = false;
    });
    final status = await Permission.camera.request();
    if (!mounted) return;
    if (!status.isGranted) {
      controller?.dispose();
      controller = null;
      setState(() {
        preparing = false;
        permanentlyDenied = status.isPermanentlyDenied;
        blockMessage = status.isPermanentlyDenied
            ? '相机权限已被永久拒绝，请在系统设置中开启后返回重试'
            : '需要相机权限才能扫码';
      });
      return;
    }
    // 权限就绪后再创建控制器，避免未授权时启动预览 → 黑屏+感叹号
    controller?.dispose();
    final c = MobileScannerController(detectionSpeed: DetectionSpeed.noDuplicates);
    setState(() {
      controller = c;
      preparing = false;
      blockMessage = null;
    });
  }

  void onDetect(BarcodeCapture capture) {
    if (handled) return;
    final raw = capture.barcodes
        .map((b) => b.rawValue?.trim() ?? '')
        .firstWhere((s) => s.isNotEmpty, orElse: () => '');
    if (raw.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请扫描正确的设备条码或二维码')),
      );
      return;
    }
    handled = true;
    Navigator.pop(context, raw);
  }

  Widget buildBlocked() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.videocam_off_outlined, size: 48, color: Colors.white70),
            const SizedBox(height: 16),
            Text(
              blockMessage ?? '无法使用相机',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 16),
            ),
            const SizedBox(height: 20),
            if (permanentlyDenied)
              FilledButton.icon(
                onPressed: () => openAppSettings(),
                icon: const Icon(Icons.settings),
                label: const Text('打开系统设置'),
              ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: prepareCamera,
              child: const Text('重试', style: TextStyle(color: Colors.white)),
            ),
          ],
        ),
      ),
    );
  }

  Widget buildError(MobileScannerException error) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48, color: Colors.white70),
            const SizedBox(height: 16),
            Text(
              error.errorDetails?.message ?? error.errorCode.name,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 15),
            ),
            const SizedBox(height: 20),
            FilledButton(onPressed: prepareCamera, child: const Text('重新打开相机')),
            TextButton(
              onPressed: () => openAppSettings(),
              child: const Text('打开系统设置', style: TextStyle(color: Colors.white70)),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = controller;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('扫码锁定设备'),
        actions: [
          if (c != null)
            IconButton(
              icon: const Icon(Icons.flash_on),
              onPressed: () => c.toggleTorch(),
            ),
        ],
      ),
      body: preparing
          ? const Center(child: CircularProgressIndicator(color: Colors.white))
          : blockMessage != null
              ? buildBlocked()
              : c == null
                  ? buildBlocked()
                  : MobileScanner(
                      controller: c,
                      onDetect: onDetect,
                      errorBuilder: (context, error, child) => buildError(error),
                    ),
    );
  }
}
