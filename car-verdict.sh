#!/bin/bash
# Car session in one command, resumable. Run ./connect.sh first (it writes .unit).
#
#   ./car-verdict.sh           # runs the steps not done yet in the current session
#   ./car-verdict.sh --fresh   # starts a new session
#
# Each step leaves a PASS/FAIL/SKIP/INFO line in shots/<session>/verdict.md plus its evidence
# (screenshots, SurfaceFlinger dumps). If the Wi-Fi drops, reconnect, run ./connect.sh, and
# run this again: finished steps are skipped.
set -uo pipefail
cd "$(dirname "$0")"

[ -s .unit ] || { echo "run ./connect.sh first"; exit 1; }
D=$(cat .unit)
mkdir -p .verdict
if [ "${1:-}" = "--fresh" ] || [ ! -s .verdict/current ]; then
    echo "s-$(date +%m%d-%H%M%S)" > .verdict/current
fi
S=$(cat .verdict/current)
OUT="shots/$S"
mkdir -p "$OUT" ".verdict/$S"
V="$OUT/verdict.md"
[ -s "$V" ] || printf '# Car verdict %s\n\n| step | result | detail |\n|---|---|---|\n' "$S" > "$V"

alive() { adb -s "$D" get-state >/dev/null 2>&1; }
need_unit() { alive || { echo; echo "LOST the unit. Reconnect Wi-Fi, run ./connect.sh, then ./car-verdict.sh again."; exit 2; }; }
done_step() { [ -e ".verdict/$S/$1" ]; }
record() { echo "| $1 | $2 | $3 |" >> "$V"; touch ".verdict/$S/$1"; echo "  -> $2  $3"; }

need_unit
LOGICAL_W=$(adb -s "$D" shell wm size 2>/dev/null | awk -F'[ x]' '/Override/{print $3}' | head -1)
[ -n "$LOGICAL_W" ] || LOGICAL_W=$(adb -s "$D" shell wm size | awk -F'[ x]' '/Physical/{print $3}')

# Starts DriveDeck with the given extras, waits, screenshots, judges the map slot.
run_and_judge() {
    local tag=$1; shift
    adb -s "$D" shell am force-stop dev.drivedeck
    adb -s "$D" logcat -c
    adb -s "$D" shell am start -n dev.drivedeck/.MainActivity "$@" >/dev/null
    adb -s "$D" shell 'sleep 10'
    need_unit
    adb -s "$D" exec-out screencap -p > "$OUT/$tag.png"
    adb -s "$D" shell dumpsys SurfaceFlinger > "$OUT/$tag.sf.txt"
    # If the HWC isn't the cause, these show how Android itself sized the slot's display and windows.
    adb -s "$D" shell dumpsys display > "$OUT/$tag.display.txt"
    adb -s "$D" shell dumpsys window windows > "$OUT/$tag.windows.txt"
    adb -s "$D" logcat -d -s 'DriveDeck:*' > "$OUT/$tag.log"
    local rect
    rect=$(grep 'slot-rect Waze' "$OUT/$tag.log" | tail -1 | sed 's/.*slot-rect Waze //')
    [ -n "$rect" ] || { echo "no slot-rect in log"; JUDGE="FAIL no slot-rect logged"; return; }
    JUDGE=$(python3 -I judge.py "$OUT/$tag.png" --rect "$rect" --logical-width "$LOGICAL_W")
}

step() { done_step "$1" && { echo "[$1] done earlier"; return 1; }; need_unit; echo "[$1] $2"; return 0; }

if step facts "unit facts"; then
    feat=$(adb -s "$D" shell pm list features | grep -c activities_on_secondary_displays)
    pk=$(adb -s "$D" shell pm list packages | grep -o -E 'com\.waze$|com\.spotify\.music$|com\.google\.android\.youtube$|com\.google\.android\.apps\.youtube\.music$' | tr '\n' ' ')
    record facts INFO "model $(adb -s "$D" shell getprop ro.product.model); secondary-display feature=$feat; logical width $LOGICAL_W; apps: ${pk:-none}"
fi

if step install "install DriveDeck"; then
    r=$(adb -s "$D" install -r build/drivedeck.apk 2>&1 | tail -1)
    adb -s "$D" shell pm grant dev.drivedeck android.permission.ACCESS_FINE_LOCATION
    adb -s "$D" shell cmd notification allow_listener dev.drivedeck/dev.drivedeck.DeckNotificationListener
    g=$(adb -s "$D" shell dumpsys package dev.drivedeck | grep -c -E '(ACTIVITY_EMBEDDING|INTERNAL_SYSTEM_WINDOW|HARDWARE_TEST|FORCE_STOP_PACKAGES|MEDIA_CONTENT_CONTROL): granted=true')
    [ "$r" = "Success" ] && [ "$g" = 5 ] && record install PASS "installed, 5/5 system permissions" \
                                       || record install FAIL "install=$r, granted=$g/5"
fi

if step activityview "stock ActivityView (expected to FAIL on this unit: the known bug)"; then
    run_and_judge activityview --es left com.android.settings --ez activityview true
    record activityview "${JUDGE%% *}" "${JUDGE#* } ($OUT/activityview.png)"
fi

if step hwc_off "stock ActivityView with HW overlays OFF (is the HWC the cause?)"; then
    trap 'adb -s "$D" shell am broadcast -n dev.drivedeck/.DebugReceiver --ei hwoverlays 1 >/dev/null 2>&1' EXIT
    adb -s "$D" shell am broadcast -n dev.drivedeck/.DebugReceiver --ei hwoverlays 0 >/dev/null
    state=$(adb -s "$D" shell dumpsys SurfaceFlinger | grep -A1 'h/w composer state' | tail -1 | sed 's/^ *//')
    run_and_judge hwc_off --es left com.android.settings --ez activityview true
    adb -s "$D" shell am broadcast -n dev.drivedeck/.DebugReceiver --ei hwoverlays 1 >/dev/null
    trap - EXIT
    record hwc_off "${JUDGE%% *}" "${JUDGE#* }; during test: '$state' ($OUT/hwc_off.png)"
fi

if step texture "TextureView embedder without the vdfix (expected ~vdW/1920 wide on this unit)"; then
    run_and_judge texture --es left com.android.settings --ez vdfix false
    record texture "${JUDGE%% *}" "${JUDGE#* } ($OUT/texture.png)"
fi

if step texture_fix "TextureView embedder WITH the vdfix (decisive: must PASS and look undistorted)"; then
    run_and_judge texture_fix --es left com.android.settings --ez vdfix true
    grep -o 'vdfix main .*' "$OUT/texture_fix.log" | tail -1 | sed 's/^/    /'
    record texture_fix "${JUDGE%% *}" "${JUDGE#* } — check $OUT/texture_fix.png looks undistorted"
fi

if step vdscale "virtual display scale knob (only matters if texture failed)"; then
    if grep -q -E "^\| texture(_fix)? \| PASS" "$V"; then
        record vdscale SKIP "texture passed"
    else
        best=""
        for s in 0.6667 0.5 1.5; do
            run_and_judge "vdscale-$s" --es left com.android.settings --ef vdscale "$s"
            echo "    vdscale $s: $JUDGE"
            best="$best $s:${JUDGE%% *}"
        done
        record vdscale INFO "$best"
    fi
fi

if step real "real apps (Waze in the map slot)"; then
    if adb -s "$D" shell pm list packages | grep -qx 'package:com.waze'; then
        run_and_judge real
        record real "${JUDGE%% *}" "${JUDGE#* } ($OUT/real.png)"
    else
        adb -s "$D" shell am force-stop dev.drivedeck
        adb -s "$D" shell am start -n dev.drivedeck/.MainActivity >/dev/null
        adb -s "$D" shell 'sleep 8'; adb -s "$D" exec-out screencap -p > "$OUT/real.png"
        record real SKIP "Waze not installed yet ($OUT/real.png)"
    fi
fi

if step gps "GPS: Waze location permission, location mode, last fix"; then
    perm=$(adb -s "$D" shell dumpsys package com.waze | grep -m1 'ACCESS_FINE_LOCATION: granted' | sed 's/^ *//')
    mode=$(adb -s "$D" shell settings get secure location_mode)
    fix=$(adb -s "$D" shell dumpsys location | grep -m1 -E 'gps: Location\[' | sed -E 's/.*(acc=[0-9.]+).*(et=[^ ]+).*/\1 \2/')
    record gps INFO "waze: ${perm:-no fine-location grant}; location_mode=$mode (3=high accuracy); last gps fix: ${fix:-none}"
fi

if step media "media sessions for Now Playing"; then
    adb -s "$D" shell dumpsys media_session > "$OUT/media_session.txt"
    s=$(grep -o -E 'package=[a-z.]+' "$OUT/media_session.txt" | sort -u | tr '\n' ' ')
    record media INFO "sessions: ${s:-none} ($OUT/media_session.txt)"
fi

if step temp "outside temperature source"; then
    adb -s "$D" shell 'getprop | grep -i -E "temp|wendu"; settings list system | grep -i temp; settings list global | grep -i temp' > "$OUT/temp.txt"
    record temp INFO "$(wc -l < "$OUT/temp.txt" | tr -d ' ') candidate lines ($OUT/temp.txt)"
fi

if step pull_apps "copy the Spotify/Waze/YouTube APKs for testing at home on the emulator"; then
    got=""
    for p in com.spotify.music com.waze com.google.android.youtube; do
        paths=$(adb -s "$D" shell pm path "$p" 2>/dev/null | sed 's/^package://' | tr -d '\r')
        [ -n "$paths" ] || continue
        mkdir -p "thirdparty/$p"
        for f in $paths; do adb -s "$D" pull "$f" "thirdparty/$p/" >/dev/null; done
        got="$got $p($(ls "thirdparty/$p" | wc -l | tr -d ' ') apk)"
    done
    record pull_apps INFO "${got:-nothing installed yet} → thirdparty/ (emulator: adb install-multiple thirdparty/<pkg>/*.apk)"
fi

echo
cat "$V"
