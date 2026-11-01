#!/usr/bin/env bash
# Records the trailer clips from the real game without a screen (see game/.../Film.kt).
#   tools/steam/film.sh <out-folder> [de|en] [WIDTHxHEIGHT] [shot ...]
# Without shots it records all of them. Each shot is one mp4 without sound; tools/steam/trailer.py
# cuts them into the trailer. FILM_SECONDS=<n> shortens every shot (for a quick look).
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(realpath -m "$1")"; LANG_="${2:-de}"; SIZE="${3:-1920x1080}"; shift $(( $# >= 3 ? 3 : $# ))
mkdir -p "$OUT"
[ -x game/build/install/game/bin/game ] || ./gradlew :game:installDist -q

shot() { # shot <name> <seconds> VAR=value ...
  local name="$1" seconds="${FILM_SECONDS:-$2}"; shift 2
  echo "== $name ($seconds s)"
  env CRAZYTOWER_TIDY="${FILM_TIDY:-1}" CRAZYTOWER_FILM="$OUT/$name.mp4" CRAZYTOWER_SECONDS="$seconds" CRAZYTOWER_WINDOW="$SIZE" CRAZYTOWER_LANG="$LANG_" "$@" \
    xvfb-run -a -s "-screen 0 ${SIZE}x24" game/build/install/game/bin/game > "$OUT/$name.log" 2>&1 || { echo "failed, see $OUT/$name.log"; return 1; }
}

# x in fields (a test tower is 64 wide, 32 is the middle), floor, zoom (0.46 shows the whole width).
# Test towers have 5, 18, 40, 55 and 70 floors for stages 1 to 5.
title()     { shot title 7 CRAZYTOWER_TITLE=5; }
build()     { shot build 10 CRAZYTOWER_CONSTRUCT=0.3 CRAZYTOWER_HOUR=10 CRAZYTOWER_CAM="0:32:2.2:1.0;10:32:3.2:0.8"; }
morph()     { shot morph 9 CRAZYTOWER_TEST=3 CRAZYTOWER_HOUR=10 CRAZYTOWER_CAM="0:12:6:1.6;9:44:6:1.6" \
                CRAZYTOWER_ACTIONS="0.8:morph:clown;3.0:morph:vampire;5.2:morph:alien;7.4:morph:supervillain"; }
rush()      { shot rush 7 CRAZYTOWER_TEST=3 CRAZYTOWER_HOUR=12.2 CRAZYTOWER_PREROLL=1 CRAZYTOWER_CAM="0:24:1.2:1.8;7:36:1.6:1.5"; }
night()     { shot night 9 CRAZYTOWER_TEST=4 CRAZYTOWER_HOUR=17.6 CRAZYTOWER_PREROLL=1 CRAZYTOWER_ACTIONS="0:speed:8" \
                CRAZYTOWER_CAM="0:32:5:0.62;9:32:7:0.55"; }
cat_()      { shot giantcat 7 CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=11 CRAZYTOWER_ACTIONS="0.3:event:giantcat" CRAZYTOWER_CAM="0:44:69:0.8;7:52:70.6:1.0"; }
moon()      { shot moon 7 CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=13 CRAZYTOWER_ACTIONS="1.2:event:moon" CRAZYTOWER_CAM="0:58:8:0.5;1.8:58:8:0.5;7:116:10:0.28"; }
celebrate() { shot celebrate 6 CRAZYTOWER_TEST=2 CRAZYTOWER_HOUR=12 CRAZYTOWER_ACTIONS="0.4:celebrate:stage" CRAZYTOWER_CAM="0:32:2.5:0.8;6:32:3:0.75"; }
inspect()   { shot inspect 7 CRAZYTOWER_TEST=4 CRAZYTOWER_HOUR=11 CRAZYTOWER_ACTIONS="0.8:select:OFFICE;4.2:open:finance" CRAZYTOWER_CAM="0:30:9:1.3;7:32:9:1.25"; }
play()      { shot play 24 CRAZYTOWER_CONSTRUCT=0.8 CRAZYTOWER_HOUR=8 CRAZYTOWER_CATEGORY=OFFICES CRAZYTOWER_CAM="0:30:1.5:1.2;24:32:3.2:0.8"; }
tall()      { shot tall 10 CRAZYTOWER_TEST=5 CRAZYTOWER_HOUR=15 CRAZYTOWER_NOHUD=1 CRAZYTOWER_CAM="0:32:2:0.75;1:32:2:0.75;9.5:32:66:0.55;10:32:66:0.55"; }

SHOTS=("$@"); [ ${#SHOTS[@]} -eq 0 ] && SHOTS=(title build morph rush night cat_ moon celebrate inspect tall play)
for s in "${SHOTS[@]}"; do "$s"; done
