#!/bin/bash
# Size vs density experiment for the embedded app's width on the head unit.
# Each run: TextureView embedder with Settings in the map slot, given vdscale and vddpi;
# prints the virtual display actually created and the pixel judge's coverage.
set -uo pipefail
cd "$(dirname "$0")"
U=$(cat .unit)
OUT="shots/exp-density-$(date +%H%M%S)"
mkdir -p "$OUT"
LW=$(adb -s "$U" shell wm size </dev/null | awk -F'[ x]' '/Override/{print $3}')
for combo in "1 240" "1 160" "1 360" "1.5 240" "0.6667 240"; do
    read -r s d <<< "$combo"
    tag="s$s-d$d"
    adb -s "$U" shell am force-stop dev.drivedeck </dev/null
    adb -s "$U" logcat -c </dev/null
    adb -s "$U" shell am start -n dev.drivedeck/.MainActivity --es left com.android.settings \
        --ef vdscale "$s" --ei vddpi "$d" </dev/null >/dev/null
    adb -s "$U" shell 'sleep 9' </dev/null
    adb -s "$U" exec-out screencap -p </dev/null > "$OUT/$tag.png"
    adb -s "$U" logcat -d -s 'DriveDeck:*' </dev/null > "$OUT/$tag.log"
    rect=$(grep 'slot-rect Waze' "$OUT/$tag.log" | tail -1 | sed 's/.*slot-rect Waze //')
    vd=$(grep -o 'virtual display [0-9]* [0-9x]* @[0-9]*' "$OUT/$tag.log" | tail -1 | sed 's/virtual display [0-9]* //')
    printf '%-12s vd=%-16s ' "$tag" "$vd"
    python3 -I judge.py "$OUT/$tag.png" --rect "$rect" --logical-width "$LW"
done
echo "out=$OUT"
