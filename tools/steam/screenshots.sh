#!/usr/bin/env bash
# Store screenshots from the real game (1920 x 1080, with the interface):
#   tools/steam/screenshots.sh <out-folder> [de|en] [game-binary]   (ONLY="01_clownsagentur 04_mond" for some)
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(realpath -m "$1")"; LANG_="${2:-de}"; BIN="${3:-game/build/install/game/bin/game}"
mkdir -p "$OUT"
[ -x "$BIN" ] || ./gradlew :game:installDist -q

still() { # still <name> VAR=value ...
  local name="$1"; shift
  [ -n "${ONLY:-}" ] && [[ " $ONLY " != *" $name "* ]] && return 0
  echo "== $name"
  env CRAZYTOWER_FILM="$OUT/$name.png" CRAZYTOWER_SECONDS=2 CRAZYTOWER_WINDOW=1920x1080 CRAZYTOWER_LANG="$LANG_" CRAZYTOWER_TIDY=1 "$@" \
    xvfb-run -a -s "-screen 0 1920x1080x24" "$BIN" > "$OUT/$name.log" 2>&1 && rm -f "$OUT/$name.log"
}

still 01_clownsagentur CRAZYTOWER_TEST=3 CRAZYTOWER_HOUR=10.5 CRAZYTOWER_CAM="0:30:7:1.5" CRAZYTOWER_ACTIONS="0.1:morph:clown;0.2:morph:vampire;0.3:morph:alien;0.5:select:OFFICE"
still 02_nacht CRAZYTOWER_TEST=4 CRAZYTOWER_HOUR=21.7 CRAZYTOWER_CAM="0:32:9:0.62"
still 03_riesenkatze CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=11 CRAZYTOWER_ACTIONS="0.1:event:giantcat" CRAZYTOWER_CAM="0:50:68.6:0.95" CRAZYTOWER_SECONDS=3
still 04_mond CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=13 CRAZYTOWER_ACTIONS="0.1:event:moon" CRAZYTOWER_CAM="0:60:5:0.55"
still 05_bauen CRAZYTOWER_CONSTRUCT=0.08 CRAZYTOWER_HOUR=9 CRAZYTOWER_CATEGORY=OFFICES CRAZYTOWER_CAM="0:32:3:0.95" CRAZYTOWER_SECONDS=3
still 06_mittag CRAZYTOWER_TEST=3 CRAZYTOWER_HOUR=12.3 CRAZYTOWER_CAM="0:30:1.6:1.5"
still 07_finanzen CRAZYTOWER_TEST=4 CRAZYTOWER_HOUR=11 CRAZYTOWER_CAM="0:30:9:1.1" CRAZYTOWER_ACTIONS="0.1:open:finance"
still 08_extras CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=15 CRAZYTOWER_CAM="0:30:20:0.9" CRAZYTOWER_ACTIONS="0.1:open:extras"
still 09_mondmission CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=16 CRAZYTOWER_CAM="0:30:40:0.7" CRAZYTOWER_ACTIONS="0.1:open:mission"
still 10_stufe CRAZYTOWER_TEST=2 CRAZYTOWER_HOUR=12 CRAZYTOWER_CAM="0:32:3:0.8" CRAZYTOWER_ACTIONS="0.4:celebrate:stage" CRAZYTOWER_SECONDS=2.5
