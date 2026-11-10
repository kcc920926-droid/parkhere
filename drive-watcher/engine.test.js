const test = require("node:test");
const assert = require("node:assert/strict");
const { createSession } = require("./engine.js");

const history = ["city", "highway"].flatMap((context) =>
  Array.from({ length: 5 }, () => ({
    context,
    values: {
      hardBrakes: context === "city" ? 1 : 4,
      steeringCorrections: 2,
      minutesSinceBreak: 15,
    },
  })));

function frame(minute, overrides = {}) {
  return { minute, context: "city", gear: "D", speedKph: 40, values: { hardBrakes: 5 }, ...overrides };
}

test("records the first sustained change with evidence and keeps it through parking", () => {
  const session = createSession(history);
  assert.equal(session.ingest(frame(2)).observation, "watching");
  assert.equal(session.ingest(frame(4, { values: { hardBrakes: 3 } })).detection, null);
  assert.equal(session.ingest(frame(6)).detection, null);
  const changed = session.ingest(frame(8));
  assert.equal(changed.detection.minute, 8);
  assert.equal(changed.parked, false);
  assert.deepEqual(changed.detection.evidence.map(({ key, value, streak }) => [key, value, streak]),
    [["hardBrakes", 5, 2]]);

  assert.equal(session.ingest(frame(9, { gear: "P" })).parked, false);
  const parked = session.ingest(frame(10, { gear: "P", speedKph: 0, values: {} }));
  assert.equal(parked.parked, true);
  assert.deepEqual(parked.detection, changed.detection);
});

test("a different road context, a gap, and missing signals break continuity", () => {
  const session = createSession(history);
  session.ingest(frame(2));
  assert.equal(session.ingest(frame(4, { context: "highway" })).observation, "typical");
  assert.equal(session.ingest(frame(6)).detection, null);
  assert.equal(session.ingest(frame(10)).detection, null);
  assert.equal(session.ingest(frame(12, { values: {} })).observation, "insufficient");
  assert.equal(session.ingest(frame(14)).detection, null);
  assert.equal(session.ingest(frame(16)).detection.minute, 16);
});

test("without enough same-condition history, no change is inferred", () => {
  const session = createSession(history.slice(0, 4));
  assert.equal(session.ingest(frame(2)).observation, "insufficient");
  assert.equal(session.ingest(frame(4)).detection, null);
  assert.throws(() => session.ingest(frame(4)), RangeError);
});

test("measured movement can be compared without inventing a gear or parked state", () => {
  const session = createSession(history);
  const first = session.ingest(frame(2, { gear: undefined, driven: true }));
  assert.equal(first.observation, "watching");
  assert.equal(first.parked, false);
  const second = session.ingest(frame(4, { gear: undefined, driven: true }));
  assert.equal(second.observation, "change");
  assert.equal(second.parked, false);
  assert.equal(session.ingest(frame(6, { gear: undefined, speedKph: 0 })).parked, false);
});
