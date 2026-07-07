import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/services/api_service.dart';

class RepairPage extends ConsumerStatefulWidget {
  const RepairPage({super.key});

  @override
  ConsumerState<RepairPage> createState() => _RepairPageState();
}

class _RepairPageState extends ConsumerState<RepairPage> {
  List<dynamic> items = [];
  final desc = TextEditingController();
  var loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    desc.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final api = ref.read(apiServiceProvider);
      final data = await api.getList('/repair/repair_workorder/list', query: {'limit': 50});
      setState(() => items = data);
    } catch (_) {
      setState(() => items = []);
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> submit() async {
    if (desc.text.trim().isEmpty) return;
    final api = ref.read(apiServiceProvider);
    await api.post('/repair/repair_workorder', {
      'fault_description': desc.text,
      'status': 'draft',
      'urgency': 'normal',
    });
    desc.clear();
    load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('报修')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: desc,
                    decoration: const InputDecoration(labelText: '故障描述'),
                  ),
                ),
                IconButton(onPressed: submit, icon: const Icon(Icons.send)),
              ],
            ),
          ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    itemCount: items.length,
                    itemBuilder: (_, i) => ListTile(
                      title: Text(items[i]['workorder_no']?.toString() ?? items[i]['id'].toString()),
                      subtitle: Text(items[i]['status']?.toString() ?? ''),
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
