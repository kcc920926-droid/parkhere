/* Six driving habits measured on the simulator course, the same way for scripted and manual drives. */
const DriveWatcherHabits = (() => {
  const course = typeof DriveWatcherCourse !== "undefined" ? DriveWatcherCourse : require("./course.js");
  const traits = [
    { key: "reactionSec", label: "반응 시간", unit: "초", digits: 2, up: "느려요", down: "빨라요" },
    { key: "headwaySec", label: "차간 시간", unit: "초", digits: 1, up: "길어요", down: "짧아요" },
    { key: "minGapM", label: "가장 가까운 거리", unit: "m", digits: 1, up: "멀어요", down: "가까워요" },
    { key: "brakeHoldSec", label: "제동 길이", unit: "초", digits: 2, up: "길어요", down: "짧아요" },
    { key: "throttleShare", label: "가속 페달 비율", unit: "%", digits: 0, up: "높아요", down: "낮아요" },
    { key: "speedSwing", label: "속도 변화", unit: "km/h", digits: 1, up: "커요", down: "작아요" },
  ];
  /* The lead car in lane 0 starts slowing 1.25 s into each braking window of the course. */
  const leadBrakeStarts = course.brakeWindows.map((window) => window * course.secondsPerWindow + 1.25);
  const reactionLimit = 3;
  const eventLength = 4;

  const meanOf = (values) => values.reduce((total, value) => total + value, 0) / values.length;

  /* Feed one sample per simulator tick; read six habits at the end. */
  function createMeter() {
    const events = leadBrakeStarts.map((start) => ({ start, sameLane: null, onset: null, minGap: Infinity, hold: 0 }));
    const speeds = [];
    const headways = [];
    let throttleTicks = 0;
    let ticks = 0;

    function add({ seconds, speed, gap, lane, brake, throttle }) {
      ticks += 1;
      speeds.push(speed * 3.6);
      if (throttle) throttleTicks += 1;
      if (Number.isFinite(gap) && gap !== null && speed > 2) headways.push(gap / speed);
      for (const event of events) {
        const since = seconds - event.start;
        if (since < 0 || since > eventLength) continue;
        if (event.sameLane === null) event.sameLane = lane === 0;
        if (!event.sameLane) continue;
        if (brake) {
          if (event.onset === null && since <= reactionLimit) event.onset = since;
          event.hold += course.tick;
        }
        if (Number.isFinite(gap) && gap !== null) event.minGap = Math.min(event.minGap, gap);
      }
    }

    function result() {
      const counted = events.filter((event) => event.sameLane);
      const braked = counted.filter((event) => event.onset !== null);
      const average = speeds.length ? meanOf(speeds) : 0;
      const swing = speeds.length ? Math.sqrt(meanOf(speeds.map((speed) => (speed - average) ** 2))) : null;
      const values = {
        reactionSec: counted.length ? meanOf(counted.map((event) => event.onset ?? reactionLimit)) : null,
        headwaySec: headways.length ? meanOf(headways) : null,
        minGapM: counted.some((event) => Number.isFinite(event.minGap))
          ? meanOf(counted.filter((event) => Number.isFinite(event.minGap)).map((event) => event.minGap)) : null,
        brakeHoldSec: counted.length ? meanOf(counted.map((event) => event.hold)) : null,
        throttleShare: ticks ? throttleTicks / ticks * 100 : null,
        speedSwing: swing,
      };
      const missing = traits.filter(({ key }) => values[key] === null).map(({ key }) => key);
      return {
        values, missing, events: counted.length, braked: braked.length,
        vector: missing.length ? null : traits.map(({ key }) => values[key]),
      };
    }

    /* Per lead-car slowdown: when it started, whether I was behind it, and when I braked. */
    const timeline = () => events.map(({ start, sameLane, onset, hold }) => ({ start, sameLane, onset, hold }));

    return { add, result, timeline };
  }

  /*
   * A scripted driver: brakes `reaction` s after the lead car starts slowing, holds the pedal longer when
   * the gap is short, and only accelerates below its cruise speed and above its preferred headway.
   */
  function createPolicy({ reaction, headway, cruise, hold, urgencyGain = 0.4 }) {
    let brakeUntil = -1;
    const pending = [...leadBrakeStarts];
    return (state) => {
      if (pending.length && state.seconds >= pending[0] + reaction) {
        pending.shift();
        const headwayNow = state.gap !== null && state.speed > 1 ? state.gap / state.speed : headway;
        const urgency = Math.max(0, Math.min(0.5, (headway - headwayNow) * urgencyGain));
        brakeUntil = state.seconds + hold + urgency;
      }
      const brake = state.seconds < brakeUntil;
      const roomy = state.gap === null || state.speed < 1 || state.gap / state.speed > headway;
      return { brake, throttle: !brake && state.speed < cruise && roomy, steer: 0 };
    }
  }

  /*
   * Five smooth scripted drives: short, early brake taps that stay under the hard-brake line.
   * The engine demo draws them as "평소 5회", and habits.test.js checks they log zero hard brakes,
   * matching the course's synthetic baseline.
   */
  const usualDrives = [
    { reaction: 0.85, headway: 2.6, cruise: 12.8, hold: 0.12, urgencyGain: 0 },
    { reaction: 0.95, headway: 2.4, cruise: 13.1, hold: 0.13, urgencyGain: 0 },
    { reaction: 0.9, headway: 2.8, cruise: 12.6, hold: 0.12, urgencyGain: 0 },
    { reaction: 1.0, headway: 2.5, cruise: 12.9, hold: 0.14, urgencyGain: 0 },
    { reaction: 0.88, headway: 2.7, cruise: 13.2, hold: 0.13, urgencyGain: 0 },
  ];

  /* Drive the whole course with a pedal source and measure it. */
  function drive(pedalsFor, { onStep } = {}) {
    const vehicle = course.createVehicle({ detail: false });
    const meter = createMeter();
    const sample = {};
    let state = vehicle.snapshot();
    while (!state.finished) {
      const pedals = pedalsFor(state);
      const next = vehicle.step(pedals);
      state = next.state;
      if (onStep) onStep(next);
      sample.seconds = state.seconds;
      sample.speed = state.speed;
      sample.gap = state.gap;
      sample.lane = state.lane;
      sample.brake = pedals.brake;
      sample.throttle = pedals.throttle;
      meter.add(sample);
    }
    return meter.result();
  }

  /* The latest manual drive's habits stay on this browser for the tendency page. */
  const storageKey = "drive-watcher.manual-habits.v1";

  function save(result) {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ version: 1, savedAt: Date.now(), ...result }));
      return true;
    } catch {
      return false;
    }
  }

  function load() {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey));
      const vectorOk = saved?.vector === null ||
        (Array.isArray(saved?.vector) && saved.vector.length === traits.length && saved.vector.every(Number.isFinite));
      return saved?.version === 1 && vectorOk && Array.isArray(saved.missing) ? saved : null;
    } catch {
      return null;
    }
  }

  return {
    traits, leadBrakeStarts, reactionLimit, storageKey, usualDrives, createMeter, createPolicy, drive, save, load,
  };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherHabits;
