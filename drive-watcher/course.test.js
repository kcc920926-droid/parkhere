const test = require("node:test");
const assert = require("node:assert/strict");
const { createSession } = require("./engine.js");
const course = require("./course.js");

function drive(pedalsAt) {
  const vehicle = course.createVehicle();
  const engine = createSession(course.history, {
    interval: course.secondsPerWindow / 60,
    margins: course.margins,
  });
  let state = vehicle.snapshot();
  const windows = [];
  let brakeEvents = 0;
  while (!state.finished) {
    const result = vehicle.step(pedalsAt(state));
    state = result.state;
    if (result.hardBrake) brakeEvents++;
    if (result.frame) windows.push(engine.ingest(result.frame));
  }
  return {
    brakeEvents,
    counts: windows.map((window) => window.readings[0].value),
    detection: windows.at(-1).detection,
  };
}

test("automatic and manual inputs use the same course and produce different detections", () => {
  const automatic = drive(course.automaticPedals);
  assert.deepEqual(automatic.counts, [1, 1, 0, 1, 0, 1]);
  assert.equal(automatic.detection?.evidence[0].streak, 2);
  assert.equal(automatic.detection?.minute * 60, 16);

  const oneHardBrake = drive(({ seconds }) => ({ brake: seconds >= 2.2 && seconds < 2.95 }));
  assert.deepEqual(oneHardBrake.counts, [1, 0, 0, null, null, null]);
  assert.equal(oneHardBrake.detection, null);

  const gentleBrakes = drive(({ seconds }) => ({ brake: seconds % 8 >= 2.2 && seconds % 8 < 2.3 }));
  assert.deepEqual(gentleBrakes.counts, [0, 0, 0, 0, 0, null]);
  assert.equal(gentleBrakes.detection, null);
});

test("holding one brake input through a window boundary records just one event", () => {
  const sustained = drive(({ seconds }) => ({ brake: seconds >= 7.4 && seconds < 8.8 }));
  assert.deepEqual(sustained.counts, [1, 0, null, null, null, null]);
  assert.equal(sustained.brakeEvents, 1);
});

test("the car footprint crosses a line between lane centers and settles inside the next lane", () => {
  const vehicle = course.createVehicle();
  const start = vehicle.snapshot();
  assert.equal(new Set(start.traffic.map((car) => car.lateral)).size, 3);
  assert(start.traffic.length > 1);
  assert.equal(start.onMarking, false);

  let state = start;
  while (state.seconds < .45) state = vehicle.step({ steer: -1 }).state;
  assert(state.laneOffset < 0);
  assert.equal(state.lane, 0);
  assert.equal(state.onMarking, true);
  while (state.seconds < 1.4) state = vehicle.step({ steer: -1 }).state;
  assert.equal(state.lane, -1);
  assert.equal(state.onMarking, false);
  assert.equal(state.laneOffset, 0);
  assert.equal(state.hardBrakes, 0);
});
