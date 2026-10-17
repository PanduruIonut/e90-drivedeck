#!/bin/bash
# One-shot DriveDeck test on the head unit.   Usage: ./car-test.sh <unit-ip>
# Prints the one fact that decides embedding (the secondary-display feature), installs the
# app, starts it with Waze + Spotify, and saves a screenshot and the logs under shots/.
set -uo pipefail
cd "$(dirname "$0")"
IP=${1:?usage: ./car-test.sh <unit-ip>}
D="$IP:5555"
STAMP=$(date +%H%M%S)

adb connect "$D" >/dev/null
adb -s "$D" get-state >/dev/null 2>&1 || { echo "FAIL: $D not reachable over adb"; exit 1; }
echo "unit: $(adb -s "$D" shell getprop ro.product.model)"

echo "== decisive check"
if adb -s "$D" shell pm list features | grep -q 'android.software.activities_on_secondary_displays'; then
    echo "FEATURE PRESENT: embedding should work"
else
    echo "FEATURE MISSING: expect fallback cards (embedding needs this feature)"
fi

echo "== apps"
for p in com.waze com.spotify.music; do
    adb -s "$D" shell pm list packages | grep -qx "package:$p" && echo "  $p installed" || echo "  $p MISSING"
done

echo "== install"
adb -s "$D" install -r build/drivedeck.apk | tail -1
adb -s "$D" shell dumpsys package dev.drivedeck \
    | grep -E '(ACTIVITY_EMBEDDING|INTERNAL_SYSTEM_WINDOW|MEDIA_CONTENT_CONTROL): granted' | sed 's/^ */  /'
adb -s "$D" shell pm grant dev.drivedeck android.permission.ACCESS_FINE_LOCATION
adb -s "$D" shell cmd notification allow_listener dev.drivedeck/dev.drivedeck.DeckNotificationListener

echo "== run"
adb -s "$D" shell am force-stop com.waze
adb -s "$D" shell am force-stop com.spotify.music
adb -s "$D" logcat -c
adb -s "$D" shell am start -n dev.drivedeck/.MainActivity | tail -1
adb -s "$D" shell 'sleep 10'
mkdir -p shots
adb -s "$D" exec-out screencap -p > "shots/car-$STAMP.png"
echo "screenshot: shots/car-$STAMP.png"

echo "== where the apps landed (display 0 = full screen, others = inside DriveDeck)"
adb -s "$D" shell dumpsys activity activities | grep -E '^Display #|realActivity=' | awk '!seen[$0]++'
adb -s "$D" logcat -d -s 'DriveDeck:*' > "shots/car-$STAMP.log"
grep -E 'virtual display|started inside|fallback card|unavailable|moved|forwarder' "shots/car-$STAMP.log" | sed 's/^/  /'
echo "log: shots/car-$STAMP.log"

echo "== media sessions (what Now Playing can read; start something in Spotify/YouTube first)"
adb -s "$D" shell dumpsys media_session | grep -E 'package=|state=PlaybackState|description=' | sed 's/^ */  /' | head -20

echo "== outside temperature: where might the status bar's value come from?"
adb -s "$D" shell 'getprop | grep -i -E "temp|wendu"; settings list system | grep -i temp; settings list global | grep -i temp' | sed 's/^/  /'

echo "== comparison: stock ActivityView (the one that rendered at half width on this unit)"
adb -s "$D" shell am force-stop dev.drivedeck
adb -s "$D" shell am start -n dev.drivedeck/.MainActivity --ez activityview true | tail -1
adb -s "$D" shell 'sleep 10'
adb -s "$D" exec-out screencap -p > "shots/car-$STAMP-activityview.png"
echo "screenshot: shots/car-$STAMP-activityview.png"
adb -s "$D" shell am force-stop dev.drivedeck
adb -s "$D" shell am start -n dev.drivedeck/.MainActivity | tail -1
echo
echo "Remove with: adb -s $D uninstall dev.drivedeck"
