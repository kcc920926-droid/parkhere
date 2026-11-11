const test = require("node:test");
const assert = require("node:assert/strict");
const { assess, weekOf, tripFromFrames } = require("./weekly.js");
const { scenes, tripsFor } = require("./weekly-history.js");

const statuses = (id) => assess(tripsFor(scenes.find((scene) => scene.id === id)), { context: "city" })
  .map(({ status }) => status);

test("weeks start on Monday", () => {
  assert.equal(weekOf("2026-11-08"), "2026-11-02");
  assert.equal(weekOf("2026-11-02"), "2026-11-02");
});

test("the first 14 days only learn, and one bad week does not change the status", () => {
  assert.deepEqual(statuses("steady"), [
    "learning", "learning", ...Array(10).fill("stable"),
  ]);
});

test("a change that continues moves from observe to talk", () => {
  const weeks = assess(tripsFor(scenes.find((scene) => scene.id === "changing")), { context: "city" });
  assert.deepEqual(weeks.slice(-4).map(({ status }) => status), ["stable", "observe", "observe", "talk"]);
  assert.deepEqual(weeks.at(-1).evidence.map(({ key, streak }) => [key, streak]),
    [["hardBrakesPer100km", 4], ["steeringPer100km", 2]]);
});

test("weeks above the line stay out of the baseline", () => {
  const weeks = assess(tripsFor(scenes.find((scene) => scene.id === "changing")), { context: "city" });
  const upper = (entry) => entry.readings.find(({ key }) => key === "hardBrakesPer100km").baseline.upper;
  assert.equal(upper(weeks.at(-1)), upper(weeks.at(-4)));
});

test("a week with too few trips is not judged and breaks the streak", () => {
  const trips = tripsFor(scenes.find((scene) => scene.id === "changing"));
  const thinned = trips.filter((trip) => !(trip.date >= "2026-10-19" && trip.date < "2026-10-24"));
  const weeks = assess(thinned, { context: "city" });
  assert.deepEqual(weeks.slice(-4).map(({ status }) => status), ["stable", "insufficient", "stable", "talk"]);
  assert.deepEqual(weeks.at(-1).evidence.map(({ key, streak }) => [key, streak]),
    [["hardBrakesPer100km", 2], ["steeringPer100km", 2]]);
});

test("other road contexts keep their own baseline", () => {
  const trips = tripsFor(scenes.find((scene) => scene.id === "changing"))
    .map((trip) => ({ ...trip, context: "highway" }));
  assert.equal(assess(trips, { context: "city" }).every(({ trips: count }) => count === 0), true);
});

test("a trip summary is built from the trip engine's 2-minute frames", () => {
  const frames = [
    { minute: 2, gear: "D", speedKph: 30, context: "city", values: { hardBrakes: 1, minutesSinceBreak: 2 } },
    { minute: 4, gear: "D", speedKph: 60, context: "city", values: { hardBrakes: 2, minutesSinceBreak: 4 } },
    { minute: 5, gear: "P", speedKph: 0, context: "city" },
  ];
  assert.deepEqual(tripFromFrames(frames, { date: "2026-11-08" }), {
    date: "2026-11-08", context: "city", km: 3,
    values: { hardBrakes: 3, steeringCorrections: undefined, minutesSinceBreak: 4 },
  });
});
