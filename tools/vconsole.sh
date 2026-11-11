#!/bin/sh
# Pleos Virtual Console을 스크립트로 조작한다. 먼저: adb forward tcp:8088 tcp:8088
# 사용: tools/vconsole.sh gear Drive | controls <kph> <accel%> <brake%> <steer°> | turn BlinkLeft | state | drive
# 자세한 설명: docs/pleos-signal-check.md
BASE="${VCONSOLE_URL:-http://127.0.0.1:8088}"
CLIENT="vconsole-$$"

beat() { curl -s -m 5 -X POST -o /dev/null "$BASE/browser/session?client=$CLIENT"; }
put() { beat; curl -s -m 10 -X PUT -o /dev/null -w "%{http_code} $1\n" "$BASE$1"; }
hold() { n=0; while [ "$n" -lt "$1" ]; do beat; sleep 1; n=$((n + 1)); done; }
wake() { hold 3; }   # 브라우저 신호가 몇 번 간 뒤에야 에뮬레이터가 활성화된다
bye() { curl -s -m 5 -X POST -o /dev/null "$BASE/browser/session?client=$CLIENT&closing=1"; }

case "$1" in
  gear) wake; put "/vehicle/gear?gear=$2"; bye ;;
  controls) wake; put "/vehicle/controls?speed=$2&accelerator=$3&brake=$4&steering=$5"; bye ;;
  turn) wake; put "/vehicle/turn-signal?mode=$2"; bye ;;
  state) curl -s -m 10 "$BASE/vehicle/state" ;;
  drive)
    wake
    put "/vehicle/gear?gear=Drive"; hold 1
    put "/vehicle/controls?speed=50&accelerator=30&brake=0&steering=0"; hold 3
    put "/vehicle/controls?speed=30&accelerator=0&brake=60&steering=0"; hold 2
    put "/vehicle/controls?speed=30&accelerator=10&brake=0&steering=35"; hold 2
    put "/vehicle/turn-signal?mode=BlinkLeft"; hold 2
    put "/vehicle/turn-signal?mode=Off"; hold 1
    put "/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0"; hold 2
    put "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0"; hold 1
    put "/vehicle/gear?gear=Park"
    bye ;;
  *) sed -n '2,4p' "$0"; exit 1 ;;
esac
