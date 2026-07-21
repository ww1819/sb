import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

class MyRepairDetailPage extends ConsumerStatefulWidget {
  const MyRepairDetailPage({super.key, required this.workorderId});

  final String workorderId;

  @override
  ConsumerState<MyRepairDetailPage> createState() => _MyRepairDetailPageState();
}

class _MyRepairDetailPageState extends ConsumerState<MyRepairDetailPage> {
  Map<String, dynamic>? wo;
  Map<String, dynamic>? timeline;
  var loading = true;
  var submitting = false;

  static const statusLabel = {
    'draft': '草稿',
    'reported': '已报修',
    'dispatching': '派工中',
    'pending_accept': '待接单',
    'accepted': '已接单',
    'repairing': '维修中',
    'suspended': '挂起',
    'pending_verify': '待验收',
    'verify_rejected': '拒绝验收',
    'verified': '已验收',
    'closed': '已关闭',
    'cancelled': '已取消',
  };

  ApiService get api => ref.read(apiServiceProvider);

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final data = await api.getData('/repair/workorder/${widget.workorderId}');
      final tl = await api.getData('/repair/workorder/${widget.workorderId}/timeline');
      setState(() {
        wo = data is Map ? Map<String, dynamic>.from(data) : null;
        timeline = tl is Map ? Map<String, dynamic>.from(tl) : null;
      });
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  bool get canVerify => wo?['status']?.toString() == 'pending_verify';

  Future<void> doVerify({required bool pass}) async {
    String? comment;
    int? rating;
    if (!pass) {
      final ctrl = TextEditingController();
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('拒绝验收'),
          content: TextField(
            controller: ctrl,
            decoration: const InputDecoration(labelText: '拒绝原因（必填）'),
            maxLines: 3,
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('确认拒绝')),
          ],
        ),
      );
      if (ok != true) return;
      comment = ctrl.text.trim();
      if (comment.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写拒绝原因')));
        return;
      }
    } else {
      final commentCtrl = TextEditingController();
      int stars = 5;
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => StatefulBuilder(
          builder: (ctx, setLocal) => AlertDialog(
            title: const Text('验收通过'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: List.generate(
                    5,
                    (i) => IconButton(
                      onPressed: () => setLocal(() => stars = i + 1),
                      icon: Icon(i < stars ? Icons.star : Icons.star_border, color: Colors.amber),
                    ),
                  ),
                ),
                TextField(
                  controller: commentCtrl,
                  decoration: const InputDecoration(labelText: '评价（可选）'),
                  maxLines: 2,
                ),
              ],
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('确认通过')),
            ],
          ),
        ),
      );
      if (ok != true) return;
      comment = commentCtrl.text.trim();
      rating = stars;
    }

    setState(() => submitting = true);
    try {
      await api.postData('/repair/workorder/${widget.workorderId}/verify', {
        'client': 'app',
        'verify_result': pass ? 'pass' : 'fail',
        'verify_comment': comment,
        if (rating != null) 'satisfaction_rating': rating,
        if (comment != null && comment.isNotEmpty) 'satisfaction_comment': comment,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(pass ? '验收通过' : '已拒绝验收')),
        );
      }
      await load();
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
      }
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final st = wo?['status']?.toString() ?? '';
    final milestones = timeline?['milestones'] is List ? timeline!['milestones'] as List : [];
    final events = timeline?['events'] is List ? timeline!['events'] as List : [];

    return Scaffold(
      appBar: AppBar(title: Text(wo?['wo_no']?.toString() ?? '工单详情')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  '${wo?['device_name'] ?? ''}（${wo?['device_code'] ?? ''}）',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                Text('状态：${statusLabel[st] ?? st}'),
                const SizedBox(height: 8),
                Text(wo?['fault_description']?.toString() ?? ''),
                const Divider(height: 32),
                Text('进度', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                if (milestones.isNotEmpty)
                  ...milestones.map((m) {
                    final map = Map<String, dynamic>.from(m as Map);
                    return ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(
                        map['done'] == true || map['completed'] == true
                            ? Icons.check_circle
                            : Icons.radio_button_unchecked,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      title: Text(map['title']?.toString() ?? map['name']?.toString() ?? ''),
                      subtitle: Text(map['at']?.toString() ?? map['time']?.toString() ?? ''),
                    );
                  })
                else
                  ...events.map((e) {
                    final map = Map<String, dynamic>.from(e as Map);
                    return ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(map['event_type']?.toString() ?? map['remark']?.toString() ?? ''),
                      subtitle: Text(
                        '${map['to_status'] ?? ''} · ${map['created_at'] ?? ''}',
                      ),
                    );
                  }),
                if (canVerify) ...[
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: submitting ? null : () => doVerify(pass: true),
                    child: const Text('验收通过'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton(
                    onPressed: submitting ? null : () => doVerify(pass: false),
                    child: const Text('拒绝验收'),
                  ),
                ],
              ],
            ),
    );
  }
}
