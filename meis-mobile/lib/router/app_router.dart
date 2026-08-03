import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/storage/app_prefs.dart';
import '../features/auth/pages/login_page.dart';
import '../features/auth/providers/auth_provider.dart';
import '../features/home/pages/main_shell_page.dart';
import '../features/setup/pages/ethernet_setup_page.dart';
import '../features/setup/pages/lan_setup_page.dart';
import '../features/setup/pages/mode_select_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final prefs = ref.watch(appPrefsProvider);
  final refresh = ValueNotifier<int>(0);
  ref.listen(authProvider, (_, __) => refresh.value++);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/setup/mode',
    refreshListenable: refresh,
    redirect: (context, state) async {
      final loc = state.matchedLocation;
      final setupDone = await prefs.isSetupCompleted();
      final loggedIn = prefs.isLoggedIn;

      if (!setupDone) {
        if (loc.startsWith('/setup')) return null;
        return '/setup/mode';
      }

      if (!loggedIn) {
        if (loc == '/login' || loc.startsWith('/setup')) return null;
        return '/login';
      }

      if (loggedIn && (loc == '/login' || loc.startsWith('/setup'))) {
        return '/home';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/setup/mode',
        builder: (_, __) => const ModeSelectPage(),
      ),
      GoRoute(
        path: '/setup/lan',
        builder: (_, __) => const LanSetupPage(),
      ),
      GoRoute(
        path: '/setup/ethernet',
        builder: (_, __) => const EthernetSetupPage(),
      ),
      GoRoute(
        path: '/login',
        builder: (_, __) => const LoginPage(),
      ),
      GoRoute(
        path: '/home',
        builder: (_, __) => const MainShellPage(),
      ),
    ],
  );
});
