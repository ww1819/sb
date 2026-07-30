import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

/// 申请相机权限；已授权返回 true。
/// 拒绝时弹出 SnackBar；永久拒绝提供「去设置」。
Future<bool> ensureCameraPermission(
  BuildContext context, {
  String usage = '使用相机',
}) async {
  var status = await Permission.camera.status;
  if (status.isGranted) return true;
  status = await Permission.camera.request();
  if (status.isGranted) return true;
  if (!context.mounted) return false;
  final permanent = status.isPermanentlyDenied;
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        permanent ? '相机权限已被拒绝，请在系统设置中开启' : '需要相机权限才能$usage',
      ),
      action: permanent
          ? SnackBarAction(label: '去设置', onPressed: openAppSettings)
          : null,
    ),
  );
  return false;
}
