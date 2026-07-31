MEIS scripts layout
===================

  common\   Shared helpers (meis-root.ps1, mobile-env.ps1, size-report.ps1)
  bs\       Browser/Server: Java microservices + meis-web (start/stop/build/panels/DB)
  app\      Flutter meis-mobile (setup-mobile, run-mobile, Developer Mode, mirrors)

Root *.bat / *.ps1 are thin shims that forward to bs\ or app\ (old paths still work).

Examples
--------
  scripts\bs\start.bat          Start backend
  scripts\bs\dev-panel.ps1      Dev panel :5099
  scripts\app\setup-mobile.bat  First-time Flutter platforms
  scripts\app\run-mobile.bat    Run Windows desktop app

  Or via shim: scripts\start.bat / scripts\setup-mobile.bat
