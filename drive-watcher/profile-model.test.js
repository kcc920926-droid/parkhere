const test = require("node:test");
const assert = require("node:assert/strict");
const { drivers, days, run, createProfile, evaluate } = require("./profile-model.js");

const trained = Object.fromEntries(drivers.map((driver) => {
  const profile = createProfile(driver);
  profile.train(1500);
  return [driver.id, profile];
}));
const dayFor = (id) => days.find((day) => day.id === id);
const driverFor = (id) => drivers.find((driver) => driver.id === id);

test("training lowers the reconstruction loss", () => {
  const profile = createProfile(drivers[0], trained.calm.trips);
  const first = profile.train(1);
  const later = profile.train(300);
  assert.ok(later < first / 3, `${first} → ${later}`);
});

test("a hurried day still fits the driver's own tendency", () => {
  for (const profile of Object.values(trained)) {
    assert.equal(profile.assess(run(dayFor("usual").params(profile.driver))).unusual, false);
    assert.equal(profile.assess(run(dayFor("hurried").params(profile.driver))).unusual, false);
  }
});

test("a slow-reaction day is flagged with reaction time as the first reason", () => {
  for (const profile of Object.values(trained)) {
    const result = profile.assess(run(dayFor("tired").params(profile.driver)));
    assert.equal(result.unusual, true);
    assert.equal(result.reasons[0].key, "reactionSec");
    assert.ok(result.reasons[0].value > result.reasons[0].expected);
  }
});

test("the same drive is ordinary for one driver and unusual for the other", () => {
  const briskDay = run(dayFor("usual").params(driverFor("brisk")));
  assert.equal(trained.brisk.assess(briskDay).unusual, false);
  assert.equal(trained.calm.assess(briskDay).unusual, true);
});

test("the batch check is reproducible and reports both kinds of mistakes", () => {
  const options = { normalDays: 150, perLevel: 20 };
  const first = evaluate(trained.calm, options);
  const again = evaluate(trained.calm, options);
  assert.equal(first.falseAlarmRate, again.falseAlarmRate);
  assert.deepEqual(first.sweep, again.sweep);
  assert.ok(first.falseAlarmRate < 0.05, `false alarms ${first.falseAlarmRate}`);
  assert.ok(first.misses.length > 0, "some small delays should be missed so they can be replayed");
  assert.equal(first.sweep.at(-1).rate, 1);
});
