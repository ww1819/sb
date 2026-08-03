import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'camera_permission.dart';

/// 选图/拍照统一入口（MOB-SCAN-04）。
/// 相机源会先申请权限，并捕获打开失败（含占用/NPE 类异常）。
Future<XFile?> pickImageWithPermission(
  BuildContext context, {
  required ImageSource source,
  String usage = '拍照',
  int imageQuality = 85,
}) async {
  if (source == ImageSource.camera) {
    final ok = await ensureCameraPermission(context, usage: usage);
    if (!ok || !context.mounted) return null;
  }
  try {
    return await ImagePicker().pickImage(source: source, imageQuality: imageQuality);
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            source == ImageSource.camera
                ? '相机打开失败，请关闭占用相机的其它应用后重试'
                : '选图失败，请重试',
          ),
        ),
      );
    }
    return null;
  }
}
