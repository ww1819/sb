import 'package:flutter/material.dart';

import 'ops_hub_page.dart';

class InspectionPage extends StatelessWidget {
  const InspectionPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const OpsHubPage(config: OpsModuleConfig.inspect);
  }
}
