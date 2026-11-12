/* Weekly status compares one driver's weeks with that driver's own earlier weeks. */
const DriveWatcherWeekly = (() => {
  const dayMs = 86400000;
  const learning = { days: 14, trips: 10 };
  const baselineWeeks = 8;
  const minBaselineWeeks = 2;
  const minTripsPerWeek = 3;
  const observeWeeks = 2;
  const talkWeeks = 4;
  const metrics = [
    { key: "hardBrakesPer100km", label: "강한 제동", unit: "회/100km", source: "hardBrakes", kind: "rate", margin: 3 },
    { key: "steeringPer100km", label: "조향 보정", unit: "회/100km", source: "steeringCorrections", kind: "rate", margin: 6 },
    { key: "longestDriveMinutes", label: "쉬지 않고 운전한 시간", unit: "분", source: "minutesSinceBreak", kind: "max", margin: 15 },
  ];

  const valid = (value) => Number.isFinite(value) && value >= 0;
  const round = (value) => Math.round(value * 10) / 10;
  const dayOf = (date) => Date.parse(`${date}T00:00:00Z`);
  const isoDay = (ms) => new Date(ms).toISOString().slice(0, 10);

  function median(values) {
    const sorted = [...values].sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  }

  /* Monday of the week that contains the date, as YYYY-MM-DD. */
  function weekOf(date) {
    const ms = dayOf(date);
    if (!Number.isFinite(ms)) throw new RangeError(`날짜 형식이 올바르지 않습니다: ${date}`);
    return isoDay(ms - ((new Date(ms).getUTCDay() + 6) % 7) * dayMs);
  }

  /* One trip from the 2-minute frames the trip engine already reads. */
  function tripFromFrames(frames, { date, context, interval = 2 } = {}) {
    const moving = frames.filter((frame) => frame.gear === "D" && valid(frame.speedKph) && frame.speedKph > 0);
    const sum = (key) => {
      const values = moving.map((frame) => frame.values?.[key]).filter(valid);
      return values.length ? values.reduce((total, value) => total + value, 0) : undefined;
    };
    const breaks = moving.map((frame) => frame.values?.minutesSinceBreak).filter(valid);
    return {
      date,
      context: context ?? moving[0]?.context,
      km: round(moving.reduce((total, frame) => total + frame.speedKph * interval / 60, 0)),
      values: {
        hardBrakes: sum("hardBrakes"),
        steeringCorrections: sum("steeringCorrections"),
        minutesSinceBreak: breaks.length ? Math.max(...breaks) : undefined,
      },
    };
  }

  function weekValues(trips) {
    return Object.fromEntries(metrics.map(({ key, source, kind }) => {
      const used = trips.filter((trip) => valid(trip.values?.[source]) && (kind !== "rate" || trip.km > 0));
      if (!used.length) return [key, null];
      if (kind === "max") return [key, Math.max(...used.map((trip) => trip.values[source]))];
      const km = used.reduce((total, trip) => total + trip.km, 0);
      return [key, round(used.reduce((total, trip) => total + trip.values[source], 0) / km * 100)];
    }));
  }

  /* Judge every calendar week from the first trip to the last, one road context at a time. */
  function assess(trips, { context } = {}) {
    const sorted = [...trips].sort((a, b) => dayOf(a.date) - dayOf(b.date));
    if (!sorted.length) return [];
    const learnUntil = dayOf(sorted[0].date) + learning.days * dayMs;
    const first = dayOf(weekOf(sorted[0].date));
    const last = dayOf(weekOf(sorted.at(-1).date));
    const weeks = [];
    let streaks = {};
    let tripsSoFar = 0;

    for (let start = first; start <= last; start += 7 * dayMs) {
      const week = isoDay(start);
      const all = sorted.filter((trip) => weekOf(trip.date) === week);
      tripsSoFar += all.length;
      const own = all.filter((trip) => context == null || trip.context === context);
      const values = weekValues(own);
      const learningWeek = start + 7 * dayMs <= learnUntil || tripsSoFar < learning.trips;
      const enoughTrips = own.length >= minTripsPerWeek;

      const readings = metrics.map(({ key }) => {
        const past = weeks.slice(-baselineWeeks)
          .filter((entry) => entry.sampled && !entry.readings.find((reading) => reading.key === key)?.above)
          .map((entry) => entry.values[key]).filter(valid);
        let baseline = null;
        if (past.length >= minBaselineWeeks) {
          const typical = median(past);
          const deviation = median(past.map((value) => Math.abs(value - typical)));
          const margin = metrics.find((metric) => metric.key === key).margin;
          baseline = { typical: round(typical), upper: round(typical + Math.max(margin, 3 * deviation)), weeks: past.length };
        }
        const value = values[key];
        const compared = !learningWeek && enoughTrips && value !== null && baseline !== null;
        const above = compared && value > baseline.upper;
        const streak = above ? (streaks[key] ?? 0) + 1 : 0;
        return { key, value, baseline, compared, above, streak };
      });
      streaks = Object.fromEntries(readings.map(({ key, streak }) => [key, streak]));

      const worsening = readings.filter((reading) => reading.streak >= observeWeeks);
      let status = "stable";
      if (learningWeek) status = "learning";
      else if (!enoughTrips || !readings.some((reading) => reading.compared)) status = "insufficient";
      else if (worsening.length >= 2 || readings.some((reading) => reading.streak >= talkWeeks)) status = "talk";
      else if (worsening.length === 1) status = "observe";

      weeks.push({
        week, context: context ?? null, trips: own.length, km: round(own.reduce((total, trip) => total + trip.km, 0)),
        tripsSoFar, sampled: enoughTrips, values, readings, status,
        evidence: status === "observe" || status === "talk" ? worsening : [],
      });
    }
    return weeks;
  }

  return {
    metrics, learning, baselineWeeks, minBaselineWeeks, minTripsPerWeek, observeWeeks, talkWeeks,
    weekOf, tripFromFrames, assess,
  };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherWeekly;
