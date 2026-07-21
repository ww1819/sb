import 'package:flutter/material.dart';

import 'ops_hub_page.dart';

class MaintainPage extends StatelessWidget {
  const MaintainPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const OpsHubPage(config: OpsModuleConfig.maintain);
  }
}
