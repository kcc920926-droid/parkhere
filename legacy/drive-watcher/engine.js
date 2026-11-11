/* A watch session compares fixed windows against one driver's history. */
const DriveWatcher = (() => {
  const windowMinutes = 2;
  const requiredWindows = 2;
  const metrics = [
    { key: "hardBrakes", label: "강한 제동", unit: "회", margin: 2 },
    { key: "steeringCorrections", label: "조향 보정", unit: "회", margin: 2 },
    { key: "minutesSinceBreak", label: "휴식 후 누적 주행", unit: "분", margin: 8 },
  ];

  const valid = (value) => Number.isFinite(value) && value >= 0;

  function median(values) {
    const sorted = [...values].sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  }

  function baselineFor(history, context, margins = {}) {
    const matched = history.filter((frame) => frame.context === context);
    return Object.fromEntries(metrics.map(({ key, margin }) => {
      const values = matched.map((frame) => frame.values?.[key]).filter(valid);
      if (values.length < 5) return [key, null];
      const typical = median(values);
      const deviation = median(values.map((value) => Math.abs(value - typical)));
      return [key, {
        typical,
        upper: typical + Math.max(margins[key] ?? margin, 3 * deviation),
        count: values.length,
      }];
    }));
  }

  function createSession(history, { interval = windowMinutes, margins } = {}) {
    const profiles = new Map();
    let lastFrame;
    let streaks = {};
    let detection = null;

    return {
      ingest(frame) {
        if (!valid(frame.minute) || (lastFrame && frame.minute <= lastFrame.minute)) {
          throw new RangeError("주행 기록은 시간 순서대로 입력해야 합니다.");
        }
        if (!profiles.has(frame.context)) {
          profiles.set(frame.context, baselineFor(history, frame.context, margins));
        }
        const profile = profiles.get(frame.context);
        const drivingInput = frame.gear === "D" || (frame.gear == null && frame.driven === true);
        const moving = drivingInput && valid(frame.speedKph) && (frame.driven ?? frame.speedKph > 0);
        const parked = frame.gear === "P" && frame.speedKph === 0;
        const contiguous = moving && lastFrame?.moving && lastFrame.context === frame.context &&
          Math.abs(frame.minute - lastFrame.minute - interval) < 1e-9;
        if (!contiguous) streaks = {};

        const readings = metrics.map(({ key }) => {
          const value = moving && valid(frame.values?.[key]) ? frame.values?.[key] : null;
          const baseline = profile[key];
          const above = value !== null && baseline !== null && value > baseline.upper;
          const streak = above ? (streaks[key] ?? 0) + 1 : 0;
          streaks[key] = streak;
          return { key, value, baseline, above, streak };
        });
        const confirmed = readings.filter((reading) => reading.streak >= requiredWindows);
        const compared = readings.filter((reading) => reading.value !== null && reading.baseline);
        if (!detection && confirmed.length) {
          detection = { minute: frame.minute, context: frame.context, evidence: confirmed };
        }

        lastFrame = { minute: frame.minute, context: frame.context, moving };
        let observation = "idle";
        if (moving) {
          observation = compared.length === 0 ? "insufficient" : "typical";
          if (readings.some((reading) => reading.above)) observation = "watching";
          if (confirmed.length) observation = "change";
        }
        return { readings, observation, detection, parked, moving };
      },
    };
  }

  return { metrics, windowMinutes, requiredWindows, baselineFor, createSession };
})();

if (typeof module !== "undefined") module.exports = DriveWatcher;
