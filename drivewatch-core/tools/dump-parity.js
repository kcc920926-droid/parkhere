/* 웹 MVP(JS)의 판정 결과를 줄 단위 텍스트로 뽑는다. Kotlin 쪽 ParityDump.kt 와 형식·난수 순서가 같아야 한다.
   사용: node drivewatch-core/tools/dump-parity.js > drivewatch-core/src/test/resources/parity/expected.txt */
const fs = require("fs");
const path = require("path");
const dir = path.join(__dirname, "..", "..", "drive-watcher");
const engine = require(path.join(dir, "engine.js"));
const weekly = require(path.join(dir, "weekly.js"));
const weeklyHistory = require(path.join(dir, "weekly-history.js"));
const replays = new Function(`${fs.readFileSync(path.join(dir, "replays.js"), "utf8")}\nreturn DriveWatcherReplays;`)();

const num = (value) => (value === null || value === undefined ? "-" : Number(value).toFixed(3));
const lines = [];

function frameLine(tag, frame, result) {
  const readings = result.readings
    .map((r) => `${r.key}:${num(r.value)}:${num(r.baseline?.upper)}:${r.above}:${r.streak}`).join(" ");
  const detection = result.detection
    ? `${num(result.detection.minute)}/${result.detection.context}/${result.detection.evidence.map((e) => `${e.key}x${e.streak}`).join(",")}`
    : "-";
  return `${tag} ${num(frame.minute)} ${result.observation} moving=${result.moving} parked=${result.parked} detection=${detection} | ${readings}`;
}

function weekLine(tag, week) {
  const readings = week.readings
    .map((r) => `${r.key}:${num(r.value)}:${num(r.baseline?.typical)}:${num(r.baseline?.upper)}:${r.baseline?.weeks ?? "-"}:${r.compared}:${r.above}:${r.streak}`)
    .join(" ");
  const evidence = week.evidence.map((e) => e.key).join(",") || "-";
  return `${tag} ${week.week} ${week.status} trips=${week.trips} km=${num(week.km)} soFar=${week.tripsSoFar} sampled=${week.sampled} evidence=${evidence} | ${readings}`;
}

/* 1. 시연 기록 */
for (const context of ["city", "highway"]) {
  const baseline = engine.baselineFor(replays.history, context);
  for (const { key } of engine.metrics) {
    lines.push(`baseline ${context} ${key} ${num(baseline[key]?.typical)} ${num(baseline[key]?.upper)} ${baseline[key]?.count ?? "-"}`);
  }
}
for (const scene of replays.scenes) {
  for (const variant of ["regular", "changed"]) {
    const session = engine.createSession(replays.history);
    const frames = replays.framesFor(scene, variant);
    for (const frame of frames) lines.push(frameLine(`frame ${scene.id}/${variant}`, frame, session.ingest(frame)));
    const trip = weekly.tripFromFrames(frames, { date: "2026-11-08" });
    lines.push(`trip ${scene.id}/${variant} ${trip.context} ${num(trip.km)} ${num(trip.values.hardBrakes)} ${num(trip.values.steeringCorrections)} ${num(trip.values.minutesSinceBreak)}`);
  }
}
for (const scene of weeklyHistory.scenes) {
  for (const week of weekly.assess(weeklyHistory.tripsFor(scene), { context: "city" })) lines.push(weekLine(`week ${scene.id}`, week));
}

/* 2. 고정 시드 난수 기록: 공백, 누락, 음수, 기어·도로 조건 변화를 섞는다. */
function rng(seed) {
  let state = seed;
  return (n) => {
    state = (state * 48271) % 2147483647;
    return state % n;
  };
}
const signalKeys = ["hardBrakes", "steeringCorrections", "minutesSinceBreak"];

{
  const pick = rng(20261110);
  const history = [];
  for (let i = 0; i < 40; i += 1) {
    const context = pick(2) === 0 ? "city" : "highway";
    const values = {};
    for (const key of signalKeys) if (pick(10) !== 0) values[key] = pick(8);
    history.push({ context, values });
  }
  const session = engine.createSession(history);
  let minute = 0;
  let context = "city";
  for (let i = 0; i < 300; i += 1) {
    const step = pick(20);
    minute += step < 17 ? 2 : step < 19 ? 4 : 1;
    if (pick(25) === 0) context = context === "city" ? "highway" : "city";
    const g = pick(20);
    const gear = g < 17 ? "D" : g === 17 ? "P" : g === 18 ? "N" : undefined;
    const s = pick(20);
    const speedKph = s === 0 ? undefined : s === 1 ? 0 : 1 + pick(100);
    const d = pick(16);
    const driven = d === 0 ? true : d === 1 ? false : undefined;
    const values = {};
    for (const key of signalKeys) {
      const p = pick(20);
      if (p === 1) values[key] = -1;
      else if (p !== 0) values[key] = pick(12);
    }
    const frame = { minute, context, gear, speedKph, driven, values };
    lines.push(frameLine(`rframe ${i}`, frame, session.ingest(frame)));
  }
}

{
  const pick = rng(777);
  const ranges = [6, 30, 90];
  const bumps = [4, 20, 40];
  const start = Date.UTC(2026, 2, 2);
  const trips = [];
  let day = 0;
  for (let i = 0; i < 150; i += 1) {
    day += pick(3);
    const context = pick(5) === 0 ? "highway" : "city";
    const km = pick(201) / 10;
    const values = {};
    signalKeys.forEach((key, index) => {
      if (pick(10) !== 0) values[key] = pick(ranges[index]) + (i >= 90 ? bumps[index] : 0);
    });
    trips.push({ date: new Date(start + day * 86400000).toISOString().slice(0, 10), context, km, values });
  }
  for (const week of weekly.assess(trips, { context: "city" })) lines.push(weekLine("rweek city", week));
  for (const week of weekly.assess(trips)) lines.push(weekLine("rweek all", week));
}

process.stdout.write(`${lines.join("\n")}\n`);
