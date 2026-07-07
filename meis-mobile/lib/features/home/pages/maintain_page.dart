import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

class MaintainPage extends ConsumerStatefulWidget {
  const MaintainPage({super.key});

  @override
  ConsumerState<MaintainPage> createState() => _MaintainPageState();
}

class _MaintainPageState extends ConsumerState<MaintainPage> {
  List<dynamic> items = [];
  var loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final api = ref.read(apiServiceProvider);
      final data = await api.getList('/maintain/maintenance_plan/list', query: {'limit': 50});
      setState(() => items = data);
    } catch (_) {
      setState(() => items = []);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('保养任务')),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : ListView.builder(
              itemCount: items.length,
              itemBuilder: (_, i) => ListTile(
                title: Text(items[i]['plan_name']?.toString() ?? ''),
                subtitle: Text(items[i]['status']?.toString() ?? ''),
              ),
            ),
    );
  }
}
