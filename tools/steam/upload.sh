#!/usr/bin/env bash
# Unpacks the builds from GitHub and uploads them to Steam, Windows and Linux in one build.
# Run it on Linux or in WSL on Windows: an upload from Windows would lose the executable bit
# of the Linux launcher, and the game would not start on the Steam Deck.
#
#   tools/steam/upload.sh demo <steam-user> CrazyTowerDemo-windows-1.0.0.zip CrazyTowerDemo-linux-1.0.0.tar.gz.zip
#   tools/steam/upload.sh full <steam-user> CrazyTower-windows-1.0.0.zip CrazyTower-linux-1.0.0.tar.gz.zip
#
# The two files are the downloads from GitHub Actions ("Steam build"), as they come.
# Needs steamcmd (https://developer.valvesoftware.com/wiki/SteamCMD) and unzip.
set -euo pipefail
kind="$1"; user="$2"; windows="$(realpath "$3")"; linux="$(realpath "$4")"
cd "$(dirname "$0")"
name=$([ "$kind" = demo ] && echo CrazyTowerDemo || echo CrazyTower)
rm -rf "content/$name-windows" "content/$name-linux" output/tmp
mkdir -p "content/$name-windows" "content/$name-linux" output/tmp
unzip -q "$windows" -d "content/$name-windows"
# GitHub wraps the tar in a zip
case "$linux" in
  *.zip) unzip -q "$linux" -d output/tmp && linux="$(ls output/tmp/*.tar.gz)" ;;
esac
tar xzf "$linux" -C "content/$name-linux" --strip-components=1
test -x "content/$name-linux/bin/$name" || { echo "Linux launcher missing or not executable"; exit 1; }
test -f "content/$name-windows/$name.exe" || { echo "$name.exe missing"; exit 1; }
if grep -q "_ID\"" "app_build_$kind.vdf"; then echo "First enter the app and depot IDs in tools/steam/app_build_$kind.vdf"; exit 1; fi
steamcmd +login "$user" +run_app_build "$PWD/app_build_$kind.vdf" +quit
