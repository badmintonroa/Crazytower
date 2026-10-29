#!/usr/bin/env bash
# Renders the demo tower without a screen and saves a screenshot: tools/run-screenshot.sh out.png [minutes] [WIDTHxHEIGHT]
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :game:installDist -q
SIZE="${3:-1280x800}"
CRAZYTOWER_SCREENSHOT="$(realpath -m "$1")" CRAZYTOWER_MINUTES="${2:-90}" CRAZYTOWER_WINDOW="$SIZE" \
  xvfb-run -a -s "-screen 0 ${SIZE}x24" game/build/install/game/bin/game
