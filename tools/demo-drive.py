"""Drive the Pleos emulator through Virtual Console to match the app's scripted navigation (Demo.script).

Usage: adb forward tcp:8088 tcp:8088 && python3 tools/demo-drive.py [comfortable | park]
`park` only stops the car and shifts to P (use it when Pleos shows "주행 중에는 사용할 수 없습니다").
Fast route: turn signals at 2 s and 15 s (missed at 5 s), one hard brake near 12 s. Ends in P.
"""
import threading, time, urllib.request
B = "http://127.0.0.1:8088"
def req(m, p):
    try: urllib.request.urlopen(urllib.request.Request(B + p, method=m), timeout=5).read()
    except Exception as e: print("err", p, e)
alive = True
def beat():
    while alive: req("POST", "/browser/session?client=vdrive"); time.sleep(1)
threading.Thread(target=beat, daemon=True).start()
time.sleep(3)
t0 = None
def at(sec, path, note):
    while time.time() - t0 < sec: time.sleep(0.05)
    req("PUT", path); print(f"{sec:>5}s {note}", flush=True)
def ctl(speed, acc=0, brake=0, steer=0): return f"/vehicle/controls?speed={speed}&accelerator={acc}&brake={brake}&steering={steer}"
import sys
comfortable = len(sys.argv) > 1 and sys.argv[1] == "comfortable"
if len(sys.argv) > 1 and sys.argv[1] == "park":
    for v in (40, 30, 20, 10, 0):
        req("PUT", ctl(v, 0, 30)); time.sleep(0.3)
    req("PUT", "/vehicle/gear?gear=Park"); time.sleep(1); req("PUT", ctl(0)); print("parked"); alive = False; sys.exit(0)
req("PUT", ctl(0, 0, 40)); req("PUT", "/vehicle/gear?gear=Drive"); t0 = time.time(); print("    0s gear D", flush=True)
at(0.5, ctl(40, 30), "speed 40")
if comfortable:  # turns at 2, 6, 10 s, all signalled; arrive at 13 s
    for g in (2, 6, 10):
        at(g - 1.2, "/vehicle/turn-signal?mode=BlinkRight", f"blink right (turn at {g}s)")
        at(g + 0.8, "/vehicle/turn-signal?mode=Off", "signal off")
    end = 13.5
else:  # turns at 2 (signal), 5 (no signal), 15 (signal); hard brake ~12 s; arrive at 18 s
    at(0.6, "/vehicle/turn-signal?mode=BlinkRight", "blink right (turn at 2s)")
    at(1.3, "/vehicle/turn-signal?mode=Off", "signal off")
    at(4.0, ctl(50, 30), "speed 50 (left turn at 5s, no signal)")
    at(12.0, ctl(50, 0, 60), "brake pressed")
    at(12.6, ctl(20, 0, 60), "50->20 km/h while braking = hard brake")
    at(13.2, ctl(30, 20, 0), "brake released")
    at(13.5, "/vehicle/turn-signal?mode=BlinkRight", "blink right (turn at 15s)")
    at(16.0, "/vehicle/turn-signal?mode=Off", "signal off")
    end = 18.5
start = 40 if comfortable else 30  # gentle stop in 2 km/h steps (~2.2 m/s2); big steps read as hard braking
steps = start // 2
for i in range(1, steps + 1):
    at(end + i * 0.25, ctl(start - 2 * i, 0, 30), f"slow to {start - 2 * i}")
stopped = end + steps * 0.25
at(stopped + 1, "/vehicle/gear?gear=Park", "gear P")
at(stopped + 2, ctl(0, 0, 0), "release")
alive = False
