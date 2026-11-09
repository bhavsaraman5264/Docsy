#!/usr/bin/env bash
# Fixture ko emulator par kholta hai aur screenshot leta hai. Usage: open-on-emulator.sh <file> <mime> <out.png> [wait]
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"; E=emulator-5554
f=$1; mime=$2; out=$3; wait=${4:-6}
"$ADB" -s $E push "fixtures/generated/$f" /sdcard/Download/ >/dev/null 2>&1
"$ADB" -s $E shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$f >/dev/null; sleep 2"
id=$("$ADB" -s $E shell content query --uri content://media/external/file --projection _id:_display_name | grep "_display_name=$f" | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1 | tr -d '\r')
"$ADB" -s $E shell am force-stop app.kholo.reader
"$ADB" -s $E shell am start -a android.intent.action.VIEW -t "$mime" -d "content://media/external/file/$id" --grant-read-uri-permission -n app.docsy.reader/app.kholo.reader.ViewerActivity >/dev/null
"$ADB" -s $E shell "sleep $wait; uiautomator dump /sdcard/u.xml >/dev/null 2>&1; grep -q 'isn.t responding' /sdcard/u.xml && input tap 320 1365; sleep 1; screencap -p /sdcard/s.png"
"$ADB" -s $E pull /sdcard/s.png "$out" >/dev/null 2>&1
echo "$f id=$id"
