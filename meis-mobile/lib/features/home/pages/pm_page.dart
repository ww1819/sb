import 'package:flutter/material.dart';

import 'ops_hub_page.dart';

class PmPage extends StatelessWidget {
  const PmPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const OpsHubPage(config: OpsModuleConfig.pm);
  }
}
