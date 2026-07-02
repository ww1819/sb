import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

String apiBase = 'http://localhost:8080/api';

Future<Map<String, String>> authHeaders() async {
  final prefs = await SharedPreferences.getInstance();
  final token = prefs.getString('token') ?? '';
  final userRaw = prefs.getString('user');
  final headers = {'Authorization': 'Bearer $token', 'Content-Type': 'application/json'};
  if (userRaw != null) {
    final user = jsonDecode(userRaw) as Map<String, dynamic>;
    headers['X-Tenant-Id'] = user['tenantId']?.toString() ?? '';
    headers['X-Tenant-Schema'] = user['schemaName']?.toString() ?? '';
    headers['X-User-Id'] = user['userId']?.toString() ?? '';
  }
  return headers;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MeisApp());
}

class MeisApp extends StatelessWidget {
  const MeisApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MEIS Mobile',
      theme: ThemeData(colorSchemeSeed: Colors.blue, useMaterial3: true),
      home: const LoginPage(),
    );
  }
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final tenantCode = TextEditingController(text: 'demo');
  final username = TextEditingController(text: 'admin');
  final password = TextEditingController(text: 'admin123');
  final apiHost = TextEditingController(text: 'http://localhost:8080/api');

  Future<void> login() async {
    apiBase = apiHost.text.trim();
    final res = await http.post(
      Uri.parse('$apiBase/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'tenantCode': tenantCode.text,
        'username': username.text,
        'password': password.text,
      }),
    );
    final body = jsonDecode(res.body);
    if (body['code'] != 0) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(body['message'] ?? '登录失败')));
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('token', body['data']['token']);
    await prefs.setString('user', jsonEncode(body['data']));
    if (!mounted) return;
    Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => const HomePage()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('MEIS 登录')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          TextField(controller: apiHost, decoration: const InputDecoration(labelText: 'API 地址')),
          TextField(controller: tenantCode, decoration: const InputDecoration(labelText: '医院编码')),
          TextField(controller: username, decoration: const InputDecoration(labelText: '用户名')),
          TextField(controller: password, decoration: const InputDecoration(labelText: '密码'), obscureText: true),
          const SizedBox(height: 16),
          ElevatedButton(onPressed: login, child: const Text('登录')),
        ]),
      ),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('MEIS 移动端')),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.build),
            title: const Text('扫码报修'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RepairPage())),
          ),
          ListTile(
            leading: const Icon(Icons.inventory),
            title: const Text('移动盘点'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InventoryPage())),
          ),
          ListTile(
            leading: const Icon(Icons.engineering),
            title: const Text('移动保养'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MaintainPage())),
          ),
          ListTile(
            leading: const Icon(Icons.fact_check),
            title: const Text('移动巡检'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InspectionPage())),
          ),
          ListTile(
            leading: const Icon(Icons.notifications),
            title: const Text('消息中心'),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MessagePage())),
          ),
        ],
      ),
    );
  }
}

class RepairPage extends StatefulWidget {
  const RepairPage({super.key});
  @override
  State<RepairPage> createState() => _RepairPageState();
}

class _RepairPageState extends State<RepairPage> {
  List<dynamic> items = [];
  final desc = TextEditingController();

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final h = await authHeaders();
    final res = await http.get(Uri.parse('$apiBase/repair/repair_workorder/list?limit=50'), headers: h);
    setState(() => items = jsonDecode(res.body)['data'] ?? []);
  }

  Future<void> submit() async {
    final h = await authHeaders();
    await http.post(Uri.parse('$apiBase/repair/repair_workorder'), headers: h, body: jsonEncode({
      'fault_description': desc.text,
      'status': 'draft',
      'urgency': 'normal',
    }));
    desc.clear();
    load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('报修')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            Expanded(child: TextField(controller: desc, decoration: const InputDecoration(labelText: '故障描述'))),
            IconButton(onPressed: submit, icon: const Icon(Icons.send)),
          ]),
        ),
        Expanded(child: ListView.builder(
          itemCount: items.length,
          itemBuilder: (_, i) => ListTile(
            title: Text(items[i]['workorder_no']?.toString() ?? items[i]['id'].toString()),
            subtitle: Text(items[i]['status']?.toString() ?? ''),
          ),
        )),
      ]),
    );
  }
}

class InventoryPage extends StatefulWidget {
  const InventoryPage({super.key});
  @override
  State<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends State<InventoryPage> {
  List<dynamic> items = [];
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final h = await authHeaders();
    final res = await http.get(Uri.parse('$apiBase/asset/inventory_check/list?limit=50'), headers: h);
    setState(() => items = jsonDecode(res.body)['data'] ?? []);
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('盘点任务')),
      body: ListView.builder(
        itemCount: items.length,
        itemBuilder: (_, i) => ListTile(title: Text(items[i]['check_name']?.toString() ?? ''), subtitle: Text(items[i]['status']?.toString() ?? '')),
      ),
    );
  }
}

class MaintainPage extends StatefulWidget {
  const MaintainPage({super.key});
  @override
  State<MaintainPage> createState() => _MaintainPageState();
}

class _MaintainPageState extends State<MaintainPage> {
  List<dynamic> items = [];
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final h = await authHeaders();
    final res = await http.get(Uri.parse('$apiBase/maintain/maintenance_plan/list?limit=50'), headers: h);
    setState(() => items = jsonDecode(res.body)['data'] ?? []);
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('保养任务')),
      body: ListView.builder(
        itemCount: items.length,
        itemBuilder: (_, i) => ListTile(title: Text(items[i]['plan_name']?.toString() ?? ''), subtitle: Text(items[i]['status']?.toString() ?? '')),
      ),
    );
  }
}

class InspectionPage extends StatefulWidget {
  const InspectionPage({super.key});
  @override
  State<InspectionPage> createState() => _InspectionPageState();
}

class _InspectionPageState extends State<InspectionPage> {
  List<dynamic> items = [];
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final h = await authHeaders();
    final res = await http.get(Uri.parse('$apiBase/asset/inspection/plans'), headers: h);
    setState(() => items = jsonDecode(res.body)['data'] ?? []);
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('巡检任务')),
      body: ListView.builder(
        itemCount: items.length,
        itemBuilder: (_, i) => ListTile(title: Text(items[i]['plan_name']?.toString() ?? ''), subtitle: Text(items[i]['status']?.toString() ?? '')),
      ),
    );
  }
}

class MessagePage extends StatefulWidget {
  const MessagePage({super.key});
  @override
  State<MessagePage> createState() => _MessagePageState();
}

class _MessagePageState extends State<MessagePage> {
  List<dynamic> items = [];
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final h = await authHeaders();
    final res = await http.get(Uri.parse('$apiBase/notification/messages'), headers: h);
    setState(() => items = jsonDecode(res.body)['data'] ?? []);
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('消息中心')),
      body: ListView.builder(
        itemCount: items.length,
        itemBuilder: (_, i) => ListTile(title: Text(items[i]['title']?.toString() ?? ''), subtitle: Text(items[i]['content']?.toString() ?? '')),
      ),
    );
  }
}
