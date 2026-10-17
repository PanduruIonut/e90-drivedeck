#!/bin/bash
# Finds the head unit on the iPhone hotspot, connects adb, and keeps a Wi-Fi log on the unit that
# survives the next disconnect. On every run it first pulls the log left by the previous run, so
# after a drop:  ./connect.sh  →  shots/wifi-<time>.log  shows why the unit lost Wi-Fi.
set -uo pipefail
cd "$(dirname "$0")"
mkdir -p shots

mac=$(ifconfig en0 | awk '/inet /{print $2}')
case "$mac" in
    172.20.10.*) ;;
    *) echo "FAIL: this Mac is on $mac, not on the iPhone hotspot (172.20.10.x)"; exit 1 ;;
esac

ip=""
for i in $(seq 2 14); do
    nc -z -G 1 "172.20.10.$i" 5555 2>/dev/null && { ip="172.20.10.$i"; break; }
done
[ -n "$ip" ] || { echo "FAIL: no head unit with adb on 172.20.10.2-14 (Wi-Fi off? AutoLink took it? ADB off?)"; exit 1; }

adb disconnect >/dev/null 2>&1
adb connect "$ip:5555" >/dev/null
adb -s "$ip:5555" get-state >/dev/null 2>&1 || { echo "FAIL: $ip answers but adb is not usable"; exit 1; }
D="$ip:5555"
echo "connected: $D  (uptime: $(adb -s "$D" shell uptime | sed 's/^ *//'))"

# Pull the previous run's Wi-Fi log, if any.
if adb -s "$D" shell 'test -s /sdcard/drivedeck-wifi.log'; then
    out="shots/wifi-$(date +%H%M%S).log"
    adb -s "$D" pull /sdcard/drivedeck-wifi.log "$out" >/dev/null
    echo "previous Wi-Fi log: $out"
    grep -E 'CTRL-EVENT-DISCONNECTED|reason=|locally_generated|DISASSOC|DEAUTH|beacon|AP-STA' "$out" | tail -8 | sed 's/^/  /'
fi

# Start a fresh logger that outlives this adb session (setsid + nohup).
adb -s "$D" shell 'pkill -f drivedeck-wifi.log 2>/dev/null; rm -f /sdcard/drivedeck-wifi.log'
adb -s "$D" shell 'setsid nohup logcat -v time -b main -b system -f /sdcard/drivedeck-wifi.log -r 4096 -n 2 wpa_supplicant:V WifiStateMachine:V WifiService:V ConnectivityService:I *:S >/dev/null 2>&1 &'
sleep 1
adb -s "$D" shell 'pgrep -f drivedeck-wifi.log >/dev/null' && echo "Wi-Fi logger running on the unit" || echo "WARN: Wi-Fi logger did not start"
echo "$D" > .unit
