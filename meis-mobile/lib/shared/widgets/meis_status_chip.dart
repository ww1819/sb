import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

/// 列表行状态标签（扁平、无胶囊堆叠感）
class MeisStatusChip extends StatelessWidget {
  const MeisStatusChip(
    this.label, {
    super.key,
    this.emphasize = false,
  });

  final String label;
  final bool emphasize;

  @override
  Widget build(BuildContext context) {
    final bg = emphasize
        ? AppColors.primary.withValues(alpha: 0.12)
        : AppColors.pageBg;
    final fg = emphasize ? AppColors.primary : AppColors.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Text(
        label,
        style: TextStyle(fontSize: 12, height: 1.2, color: fg, fontWeight: FontWeight.w500),
      ),
    );
  }
}
