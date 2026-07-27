import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

/// 业务列表卡片（白底细边，无阴影）
class MeisListCard extends StatelessWidget {
  const MeisListCard({
    super.key,
    required this.child,
    this.onTap,
    this.color,
    this.padding = const EdgeInsets.fromLTRB(14, 12, 14, 12),
    this.margin = const EdgeInsets.only(bottom: AppSpacing.sm + 2),
  });

  final Widget child;
  final VoidCallback? onTap;
  final Color? color;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry margin;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(AppSpacing.radiusMd);
    return Padding(
      padding: margin,
      child: Material(
        color: color ?? Colors.white,
        borderRadius: radius,
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Container(
            padding: padding,
            decoration: BoxDecoration(
              borderRadius: radius,
              border: Border.all(color: AppColors.borderLight),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}
