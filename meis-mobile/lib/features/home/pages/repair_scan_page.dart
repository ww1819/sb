import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';

/// 扫码页：识别成功返回纯文本编码；失败由调用方提示。
///
/// 安卓：先申请相机权限 → 首帧后再 `start()`，避免 CameraX NPE
/// （`getClass()` on null）与黑屏感叹号。见 MOB-SCAN-01 / MOB-SCAN-03。
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
  var starting = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    prepareCamera();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    final c = controller;
    controller = null;
    unawaited(c?.dispose() ?? Future<void>.value());
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final c = controller;
    if (c == null) {
      if (state == AppLifecycleState.resumed && blockMessage != null) {
        prepareCamera();
      }
      return;
    }
    switch (state) {
      case AppLifecycleState.detached:
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
        return;
      case AppLifecycleState.resumed:
        // 从后台/设置返回后重启
        if (c.value.isInitialized) {
          unawaited(_safeStart(c));
        } else if (blockMessage != null) {
          prepareCamera();
        }
      case AppLifecycleState.inactive:
        if (c.value.isRunning) {
          unawaited(c.stop());
        }
    }
  }

  Future<void> prepareCamera() async {
    if (!mounted) return;
    setState(() {
      preparing = true;
      blockMessage = null;
      permanentlyDenied = false;
    });

    await _tearDownController();

    final status = await Permission.camera.request();
    if (!mounted) return;
    if (!status.isGranted) {
      setState(() {
        preparing = false;
        permanentlyDenied = status.isPermanentlyDenied;
        blockMessage = status.isPermanentlyDenied
            ? '相机权限已被永久拒绝，请在系统设置中开启后返回重试'
            : '需要相机权限才能扫码';
      });
      return;
    }

    // autoStart=false：等 MobileScanner 挂载后再手动 start，降低 CameraX NPE
    final c = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      autoStart: false,
      facing: CameraFacing.back,
    );
    setState(() {
      controller = c;
      preparing = false;
      blockMessage = null;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_safeStart(c));
    });
  }

  Future<void> _tearDownController() async {
    final old = controller;
    controller = null;
    if (old == null) return;
    try {
      if (old.value.isRunning) await old.stop();
    } catch (_) {}
    await old.dispose();
  }

  Future<void> _safeStart(MobileScannerController c) async {
    if (!mounted || starting || !identical(controller, c)) return;
    if (c.value.isRunning) return;
    starting = true;
    try {
      // 短延迟：部分机型 Surface/CameraX 未就绪时 start 会 NPE（getClass on null）
      await Future<void>.delayed(const Duration(milliseconds: 160));
      if (!mounted || !identical(controller, c) || c.value.isRunning) return;
      await c.start();
      if (mounted) setState(() => blockMessage = null);
    } on MobileScannerException catch (e) {
      if (!mounted) return;
      setState(() => blockMessage = _friendlyError(e));
    } catch (e) {
      if (!mounted) return;
      setState(() {
        blockMessage = '相机打开失败，请重试或检查是否被其它应用占用';
      });
    } finally {
      starting = false;
    }
  }

  String _friendlyError(MobileScannerException error) {
    final raw = error.errorDetails?.message ?? error.errorCode.name;
    if (raw.contains('getClass()') ||
        raw.contains('null object reference') ||
        raw.contains('NullPointerException')) {
      return '相机初始化失败。请点「重新打开相机」；'
          '若仍失败，请关闭占用相机的其它应用后重试，或重启手机。';
    }
    if (error.errorCode == MobileScannerErrorCode.permissionDenied) {
      return '需要相机权限才能扫码';
    }
    return raw;
  }

  void onDetect(BarcodeCapture capture) {
    if (handled || !mounted) return;
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
            FilledButton(onPressed: prepareCamera, child: const Text('重新打开相机')),
            const SizedBox(height: 8),
            if (permanentlyDenied)
              FilledButton.icon(
                onPressed: () => openAppSettings(),
                icon: const Icon(Icons.settings),
                label: const Text('打开系统设置'),
              )
            else
              TextButton(
                onPressed: () => openAppSettings(),
                child: const Text('打开系统设置', style: TextStyle(color: Colors.white70)),
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
              _friendlyError(error),
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
