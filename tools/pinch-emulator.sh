#!/system/bin/sh
# Emulator ke touchscreen par asli do-ungli pinch bhejta hai (adb root chahiye).
# Usage: pinch-emulator.sh <device> <maxX> <maxY> <start-span%> <end-span%>
# Isse release APK bina kisi test tool ke test hoti hai.
dev=$1; mx=$2; my=$3; s=$4; e=$5
cy=$((my / 2)); cx=$((mx / 2))
ev() { sendevent "$dev" "$1" "$2" "$3"; }
half=$((mx * s / 200))
ev 3 47 0; ev 3 57 101; ev 3 53 $((cx - half)); ev 3 54 $cy; ev 3 58 50
ev 3 47 1; ev 3 57 102; ev 3 53 $((cx + half)); ev 3 54 $cy; ev 3 58 50
ev 1 330 1; ev 0 0 0
i=1
while [ $i -le 24 ]; do
  span=$((s + (e - s) * i / 24))
  half=$((mx * span / 200))
  ev 3 47 0; ev 3 53 $((cx - half))
  ev 3 47 1; ev 3 53 $((cx + half))
  ev 0 0 0
  i=$((i + 1))
done
ev 3 47 0; ev 3 57 -1
ev 3 47 1; ev 3 57 -1
ev 1 330 0; ev 0 0 0
