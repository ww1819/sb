import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// 扫码页：识别成功返回纯文本编码；失败由调用方提示。
class RepairScanPage extends StatefulWidget {
  const RepairScanPage({super.key});

  @override
  State<RepairScanPage> createState() => _RepairScanPageState();
}

class _RepairScanPageState extends State<RepairScanPage> {
  final controller = MobileScannerController(detectionSpeed: DetectionSpeed.noDuplicates);
  var handled = false;

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('扫码锁定设备'),
        actions: [
          IconButton(
            icon: const Icon(Icons.flash_on),
            onPressed: () => controller.toggleTorch(),
          ),
        ],
      ),
      body: MobileScanner(controller: controller, onDetect: onDetect),
    );
  }
}
