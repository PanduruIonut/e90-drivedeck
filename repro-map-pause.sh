#!/bin/bash
# Does the Waze map keep drawing after you touch one of DriveDeck's own cards?
# Android 9 has one resumed activity: a tap on the dashboard gives it the focus and pauses Waze,
# whose map (a GLSurfaceView) then stops drawing. The judge is the effect, not the state: new frames
# that SurfaceFlinger presents for Waze's map layer while the car moves.
#
#   ./repro-map-pause.sh [serial]      (emulator: needs `adb emu geo fix`; default emulator-5554)
# Exit 0 = the map drew after every tap on the dashboard.
set -uo pipefail
cd "$(dirname "$0")"
S=${1:-emulator-5554}
a() { adb -s "$S" "$@" </dev/null; }
layer() { a shell dumpsys SurfaceFlinger --list | grep -m1 '^SurfaceView - com.waze/com.waze.MainActivity'; }
last_present() {   # newest presented frame of the map layer, ns
    a shell "dumpsys SurfaceFlinger --latency '$1'" | awk 'NF==3 && $2>0 && $2<9223372036854775807 {if ($2>m) m=$2} END {print m+0}'
}
new_frames() {     # frames presented after $2 (ns)
    a shell "dumpsys SurfaceFlinger --latency '$1'" | awk -v t="$2" 'NF==3 && $2>t && $2<9223372036854775807 {n++} END {print n+0}'
}

fails=0
for round in 1 2 3; do
    a shell input tap 1100 250          # the map: Waze gets the focus and draws
    sleep 3
    a shell input tap 1748 622          # the date card: the dashboard gets the focus
    sleep 3
    L=$(layer)
    [ -n "$L" ] || { echo "round $round: no Waze map layer"; fails=$((fails + 1)); continue; }
    t=$(last_present "$L")
    for i in 1 2 3 4; do a emu geo fix "26.0$((870 + round * 10 + i))" "44.4$((530 + round * 10 + i))" 80 8 14 >/dev/null; sleep 1.5; done
    n=$(new_frames "$L" "$t")
    resumed=$(a shell dumpsys activity activities | grep -m1 '^  ResumedActivity:' | grep -o '[a-z.]*/[A-Za-z.]*')
    if [ "$n" -ge 5 ]; then echo "round $round: PASS  $n new map frames while moving (resumed: $resumed)"
    else echo "round $round: FAIL  $n new map frames while moving (resumed: $resumed)"; fails=$((fails + 1)); fi
done
[ "$fails" = 0 ]
