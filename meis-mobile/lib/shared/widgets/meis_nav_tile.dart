import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

/// 首页/功能入口瓦片。
/// - 默认：横向列表行
/// - [grid]=true：四列紧凑宫格（上图标下标题，对齐微信生活服务）
class MeisNavTile extends StatelessWidget {
  const MeisNavTile({
    super.key,
    required this.title,
    this.subtitle,
    this.icon,
    this.primary = false,
    this.grid = false,
    required this.onTap,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool primary;
  final bool grid;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    if (grid) return _buildGrid();
    return _buildRow();
  }

  Widget _buildGrid() {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (icon != null)
              Icon(icon, color: AppColors.primary, size: 28),
            const SizedBox(height: 8),
            Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w500,
                color: AppColors.textPrimary,
                height: 1.2,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRow() {
    final fg = primary ? Colors.white : AppColors.textPrimary;
    final subFg = primary ? Colors.white.withValues(alpha: 0.82) : AppColors.textSecondary;
    final bg = primary ? AppColors.primary : Colors.white;
    final border = primary ? Colors.transparent : AppColors.borderLight;

    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm + 2),
      child: Material(
        color: bg,
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          child: Container(
            padding: EdgeInsets.symmetric(
              horizontal: AppSpacing.lg,
              vertical: primary ? AppSpacing.lg + 2 : AppSpacing.md + 2,
            ),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
              border: Border.all(color: border),
            ),
            child: Row(
              children: [
                if (icon != null) ...[
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: primary
                          ? Colors.white.withValues(alpha: 0.18)
                          : AppColors.primary.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(AppSpacing.radiusSm + 2),
                    ),
                    child: Icon(
                      icon,
                      color: primary ? Colors.white : AppColors.primary,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                ],
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontSize: primary ? 17 : 16,
                          fontWeight: FontWeight.w600,
                          color: fg,
                        ),
                      ),
                      if (subtitle != null && subtitle!.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          subtitle!,
                          style: TextStyle(fontSize: 13, height: 1.35, color: subFg),
                        ),
                      ],
                    ],
                  ),
                ),
                Icon(
                  Icons.chevron_right,
                  color: primary ? Colors.white70 : AppColors.textSecondary.withValues(alpha: 0.6),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
