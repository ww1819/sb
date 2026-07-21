import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/storage/app_prefs.dart';
import 'core/storage/local_schema.dart';
import 'core/storage/sqlite_helper.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final sqlite = await SqliteHelper.open(
    version: 3,
    onCreate: (db, v) async {
      await ensureMob8Tables(db);
      await ensureMob12Tables(db);
    },
    onUpgrade: (db, oldV, newV) async {
      if (oldV < 2) await ensureMob8Tables(db);
      if (oldV < 3) await ensureMob12Tables(db);
    },
  );

  runApp(
    ProviderScope(
      overrides: [
        sharedPreferencesProvider.overrideWithValue(prefs),
        sqliteHelperProvider.overrideWithValue(sqlite),
      ],
      child: const MeisApp(),
    ),
  );
}
