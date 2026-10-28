#!/usr/bin/env bash
# Renders the demo tower without a screen and saves a screenshot: tools/run-screenshot.sh out.png [minutes]
set -euo pipefail
cd "$(dirname "$0")/.."
gradle :game:installDist -q
CRAZYTOWER_SCREENSHOT="$(realpath -m "$1")" CRAZYTOWER_MINUTES="${2:-90}" \
  xvfb-run -a -s "-screen 0 1280x800x24" game/build/install/game/bin/game
