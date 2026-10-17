#!/bin/bash
# Reproduces the 17 oct car failure: the vendor launcher ends up on top of DriveDeck when the app
# for the slot already has a task. Same sequence as car-verdict.sh: force-stop DriveDeck, start it
# with an app in the map slot, look at what is on top of the main display. Three rounds.
#
#   ./repro-return-home.sh [serial] [package]     (defaults: emulator-5554, com.android.settings)
#
# On the emulator, first make the stock launcher Home, as on the head unit:
#   adb -e shell cmd package set-home-activity com.android.launcher3/.Launcher
# Exit 0 = DriveDeck on top every round; 1 = some round lost it to another task.
set -uo pipefail
cd "$(dirname "$0")"
S=${1:-emulator-5554}
APP=${2:-com.android.settings}
a() { adb -s "$S" "$@" </dev/null; }
top0() {
    a shell dumpsys activity activities \
        | awk '/^ *Display #0/{d=1;next} /^ *Display #/{d=0} d&&/realActivity=/{sub(/.*realActivity=/,""); print; exit}'
}

out="shots/repro-return-home-$(date +%H%M%S)"
mkdir -p "$out"

# The slot's app already has a task, started from Home. FLAGS=0x10004000 (NEW_TASK|TASK_ON_HOME)
# makes it a task that "returns to home", which is what the car's log showed (returnToHome).
FLAGS=${FLAGS:-0x10000000}
comp=$(a shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$APP" | tail -1 | tr -d '\r')
a shell am force-stop "$APP"
a shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f "$FLAGS" -n "$comp" >/dev/null
echo "pre-existing task: $comp flags=$FLAGS"
sleep 3
a shell input keyevent KEYCODE_HOME
sleep 2

fails=0
for round in 1 2 3; do
    a shell am force-stop dev.drivedeck
    # The old virtual display goes away asynchronously; starting before it has would drop the app
    # on top of the new dashboard (a harness race, not the bug this script is about).
    for _ in 1 2 3 4 5 6 7 8 9 10; do a shell dumpsys display | grep -q 'DriveDeck-' || break; sleep 0.5; done
    a shell am start -n dev.drivedeck/.MainActivity --es left "$APP" >/dev/null
    sleep 8
    top=$(top0)
    a exec-out screencap -p > "$out/round-$round.png"
    case "$top" in
        dev.drivedeck/*) echo "round $round: PASS  top of display 0 = $top" ;;
        *) echo "round $round: FAIL  top of display 0 = ${top:-nothing}"; fails=$((fails + 1)) ;;
    esac
done
a shell dumpsys activity activities | grep -E 'Display #|realActivity' > "$out/activities.txt"
echo "evidence: $out"
[ "$fails" = 0 ]
