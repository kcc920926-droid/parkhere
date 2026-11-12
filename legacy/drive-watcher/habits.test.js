const test = require("node:test");
const assert = require("node:assert/strict");
const course = require("./course.js");
const { createPolicy, drive, leadBrakeStarts, reactionLimit } = require("./habits.js");

const calm = { reaction: 0.9, headway: 2.6, cruise: 13, hold: 0.4 };

test("reaction time is measured from the lead car's slowdown to the first brake press", () => {
  const result = drive(createPolicy(calm));
  assert.equal(result.events, leadBrakeStarts.length);
  assert.ok(Math.abs(result.values.reactionSec - calm.reaction) <= 2 * course.tick, result.values.reactionSec);
  assert.ok(result.vector.every(Number.isFinite));
});

test("a quicker, closer driver measures differently on every habit that policy changes", () => {
  const brisk = drive(createPolicy({ reaction: 0.62, headway: 1.4, cruise: 14.5, hold: 0.55 })).values;
  const easy = drive(createPolicy(calm)).values;
  assert.ok(brisk.reactionSec < easy.reactionSec);
  assert.ok(brisk.headwaySec < easy.headwaySec);
  assert.ok(brisk.minGapM < easy.minGapM);
  assert.ok(brisk.brakeHoldSec > easy.brakeHoldSec);
});

test("never braking counts as the reaction limit", () => {
  const result = drive(() => ({ brake: false, throttle: false, steer: 0 }));
  assert.equal(result.values.reactionSec, reactionLimit);
  assert.equal(result.values.brakeHoldSec, 0);
});

test("driving in another lane leaves reaction unmeasured instead of guessing", () => {
  const result = drive((state) => ({ brake: false, throttle: state.speed < 12, steer: state.lateral < 3.5 ? 1 : 0 }));
  assert.ok(result.missing.includes("reactionSec"));
  assert.equal(result.vector, null);
});

test("the five usual drives brake smoothly, matching the course's zero-hard-brake baseline", () => {
  const { usualDrives } = require("./habits.js");
  for (const params of usualDrives) {
    const counts = [];
    const result = drive(createPolicy(params), { onStep: ({ frame }) => { if (frame) counts.push(frame.values.hardBrakes); } });
    assert.deepEqual(counts, Array(course.windowCount).fill(0), JSON.stringify(params));
    assert.equal(result.braked, leadBrakeStarts.length);
  }
  assert.ok(course.history.every(({ values }) => values.hardBrakes === 0));
});
