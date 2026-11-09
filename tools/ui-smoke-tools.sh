#!/usr/bin/env bash
# Release APK par Tools tab ke kaam asli UI se chalata hai. Fixtures emulator ke Download me hone chahiye.
# Usage: ADB=<adb> OUT=<dir> bash tools/ui-smoke-tools.sh
export MSYS_NO_PATHCONV=1
E=emulator-5554
dlg(){ "$ADB" -s $E shell "uiautomator dump /sdcard/u.xml >/dev/null 2>&1; grep -q 'isn.t responding' /sdcard/u.xml && input tap 320 1365; sleep 1"; }
dump(){ dlg; "$ADB" -s $E shell "uiautomator dump /sdcard/u.xml >/dev/null 2>&1; cat /sdcard/u.xml" | tr '>' '\n'; }
tapt(){ dump | grep -E "text=\"$1\"|content-desc=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' > "$OUT/b.txt"; read -r a b c d < "$OUT/b.txt"; if [ -n "$a" ]; then "$ADB" -s $E shell input tap $(( (a+c)/2 )) $(( (b+d)/2 )); else echo "NOT FOUND: $1"; fi; }
txt(){ dump | grep -oE 'text="[^"]+"' | head -"${1:-5}" | tr '\n' ' '; echo; }
shot(){ "$ADB" -s $E shell "sleep ${2:-1}; screencap -p /sdcard/s.png"; "$ADB" -s $E pull /sdcard/s.png "$OUT/$1.png" >/dev/null 2>&1; }
w(){ "$ADB" -s $E shell sleep "${1:-3}"; }
fresh(){ "$ADB" -s $E shell "touch /sdcard/Download/$1; am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$1 >/dev/null; sleep 2"; }
tool(){ "$ADB" -s $E shell am force-stop app.docsy.reader; "$ADB" -s $E shell am start -n app.docsy.reader/app.kholo.reader.MainActivity >/dev/null; w 4; tapt "Tools"; w 2; tapt "$1"; w 4; }

echo "== add password"
fresh simple.pdf; tool "Add password"; tapt "simple.pdf"; w 5
dump | grep -E 'EditText' | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' > "$OUT/b.txt"; read -r a b c d < "$OUT/b.txt"
"$ADB" -s $E shell "input tap $(( (a+c)/2 )) $(( (b+d)/2 )); sleep 2; input text secret1; sleep 1; input keyevent KEYCODE_BACK; sleep 2"
tapt "Add password"; w 8; txt 5

echo "== pdf to text"
fresh simple.pdf; tool "PDF to text"; tapt "simple.pdf"; w 5; tapt "PDF to text"; w 7; txt 5

echo "== split"
fresh long-500-pages.pdf; tool "Split PDF"; tapt "long-500-pages.pdf"; w 7; txt 5; shot t-split 0
tapt "Split PDF"; w 9; txt 5

echo "== organize"
fresh simple.pdf; tool "Organize pages"; tapt "simple.pdf"; w 6; txt 5; shot t-organize 1
tapt "Save"; w 7; txt 5

echo "== merge"
fresh simple.pdf; tool "Merge PDFs"; tapt "simple.pdf"; w 2; tapt "Select"; w 3; txt 5; shot t-merge 0

echo "crash: $("$ADB" -s $E logcat -d -b crash | grep -c FATAL)"
"$ADB" -s $E logcat -d -b crash | grep -E 'Exception|Caused' | head -5 | cut -c1-170
