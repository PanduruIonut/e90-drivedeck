#!/bin/bash
# Frame-rate probe, the same instrument for DriveDeck and for CarPlay.
#
# On the unit, detached (it survives the Wi-Fi drop that wireless CarPlay can cause), it pans the map
# with the same scripted swipes and, after each swipe, saves SurfaceFlinger's present timestamps
# (dumpsys SurfaceFlinger --latency, the last 128 frames) for every layer whose name matches
# carplay|drivedeck|waze. It swipes only while DriveDeck, Waze or CarPlay has the focus (never onto a
# dialog or the vendor home); skipped swipes are logged. fps_stats.py turns the timestamps into frames
# per second and frame intervals.
#
#   ./fps-probe.sh run   <label> [seconds]           start, wait, pull, print stats (DriveDeck)
#   ./fps-probe.sh start <label> [seconds] [delay]   start and return; swipes begin after <delay> s
#   ./fps-probe.sh pull  <label>                     fetch the results and print stats
#
# CarPlay: open Waze in CarPlay on the map, then  ./fps-probe.sh start carplay 60 30 ; when adb is
# back:  ./fps-probe.sh pull carplay
set -uo pipefail
cd "$(dirname "$0")"
[ -s .unit ] || { echo "run ./connect.sh first"; exit 1; }
D=$(cat .unit)
a() { adb -s "$D" "$@" </dev/null; }

cmd=${1:-}; label=${2:-}
[ -n "$cmd" ] && [ -n "$label" ] || { sed -n '2,15p' "$0"; exit 1; }
case "$label" in *[!A-Za-z0-9_-]*) echo "label: letters, digits, _ and - only"; exit 1 ;; esac
secs=${3:-30}
delay=${4:-0}
REMOTE=/sdcard/drivedeck-fps/$label

start() {
    a shell "rm -rf $REMOTE; mkdir -p $REMOTE"
    cat > "${TMPDIR:-/tmp}/drivedeck-fps.sh" <<'EOF'
#!/system/bin/sh
# $1 = output dir, $2 = seconds of swiping, $3 = delay before starting
out=$1; secs=$2; sleep "$3"
end=$(( $(date +%s) + secs )); i=0
while [ "$(date +%s)" -lt "$end" ]; do
    # Swipe only onto the map: a blind swipe once opened "App info" from a crash dialog. Anything
    # else focused (vendor home, a dialog) is skipped and logged.
    focus=$(dumpsys window | grep -m1 mCurrentFocus)
    case "$focus" in
        *"Application Error"*|*"Not Responding"*|*"isn't responding"*) ok=0 ;;
        *drivedeck*|*waze*|*carplay*) ok=1 ;;
        *) ok=0 ;;
    esac
    if [ "$ok" = 1 ]; then
        # The map area of the DriveDeck slot and of the CarPlay screen (logical 1920x720 coordinates).
        if [ $((i % 2)) = 0 ]; then input swipe 1050 360 550 360 700; else input swipe 550 360 1050 360 700; fi
    else
        echo "$i $focus" >> "$out/skipped"; sleep 1
    fi
    dumpsys SurfaceFlinger --list | grep -i -E 'carplay|drivedeck|waze' | while IFS= read -r layer; do
        f=$out/$(printf '%05d' $i)-$(echo "$layer" | sed 's/[^A-Za-z0-9._-]/_/g').txt
        { echo "layer: $layer"; dumpsys SurfaceFlinger --latency "$layer"; } > "$f"
    done
    i=$((i + 1))
done
echo "$i" > "$out/DONE"
EOF
    a push "${TMPDIR:-/tmp}/drivedeck-fps.sh" /sdcard/drivedeck-fps.sh >/dev/null
    a shell "setsid nohup sh /sdcard/drivedeck-fps.sh $REMOTE $secs $delay >/dev/null 2>&1 &"
    echo "probe '$label' running on the unit: ${delay}s delay, ${secs}s of swipes"
}

pull() {
    a shell "test -e $REMOTE/DONE" || echo "WARN: probe '$label' has not finished (no DONE); pulling what is there"
    rm -rf "shots/fps-$label"; mkdir -p shots
    a pull "$REMOTE" "shots/fps-$label" >/dev/null || { echo "nothing to pull for '$label'"; exit 1; }
    python3 -I fps_stats.py "shots/fps-$label"
}

case "$cmd" in
    start) start ;;
    pull) pull ;;
    run) start; sleep $((delay + secs + 5)); pull ;;
    *) sed -n '2,15p' "$0"; exit 1 ;;
esac
