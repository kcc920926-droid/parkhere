const test = require("node:test");
const assert = require("node:assert/strict");
const { createStore, INVITE_MS, MAX_FAILED_ATTEMPTS } = require("../store.js");

function setup(shareScope = "status") {
  let clock = Date.parse("2026-11-13T09:00:00Z");
  const store = createStore({ now: () => clock });
  const car = store.registerDriver({ name: "김정숙", shareScope });
  const driver = store.driverByToken(car.carToken);
  const child = store.memberByToken(store.registerMember({ name: "김민수" }).token);
  return { store, driver, child, tick: (ms) => { clock += ms; } };
}
const summary = (overrides = {}) => ({
  week: "2026-11-09", status: "talk", headline: "이번 주는 평소와 달랐어요",
  goodPoints: ["밤 운전 0회"], changes: ["강한 제동이 4주째 평소보다 많았어요"],
  evidence: [{ label: "강한 제동", unit: "회/100km", typical: 5.5, value: 21.9, weeks: 4 }],
  ...overrides,
});

test("a code from the car links a family member once, for ten minutes", () => {
  const { store, driver, child, tick } = setup();
  const { code } = store.createInvite(driver);
  assert.match(code, /^\d{6}$/);
  assert.deepEqual(store.link(child, { code, relation: "엄마" }), { driverId: driver.driverId, name: "김정숙", relation: "엄마" });
  assert.throws(() => store.link(child, { code }), { code: "invalid_code" });

  const late = store.createInvite(driver).code;
  tick(INVITE_MS);
  assert.throws(() => store.link(child, { code: late }), { code: "expired_code" });
});

test("a new code replaces the previous one for the same driver", () => {
  const { store, driver, child } = setup();
  const first = store.createInvite(driver).code;
  const second = store.createInvite(driver).code;
  if (first !== second) assert.throws(() => store.link(child, { code: first }), { code: "invalid_code" });
  assert.equal(store.link(child, { code: second }).relation, "부모님");
});

test("wrong codes are limited", () => {
  const { store, driver, child } = setup();
  const { code } = store.createInvite(driver);
  const wrong = code === "000000" ? "111111" : "000000";
  for (let i = 0; i < MAX_FAILED_ATTEMPTS; i += 1) assert.throws(() => store.link(child, { code: wrong }), { code: "invalid_code" });
  assert.throws(() => store.link(child, { code }), { code: "too_many_attempts" });
});

test("numbers leave the car only when the driver chose to share evidence", () => {
  const { store, driver, child } = setup("status");
  store.link(child, { code: store.createInvite(driver).code, relation: "엄마" });
  assert.equal(store.saveSummary(driver, summary()).evidenceStored, 0);
  let seen = store.summaryFor(child, driver.driverId).summary;
  assert.equal(seen.status, "talk");
  assert.deepEqual(seen.changes, ["강한 제동이 4주째 평소보다 많았어요"]);
  assert.deepEqual(seen.evidence, []);
  assert.equal(seen.evidenceShared, false);

  store.setShareScope(driver, "evidence");
  store.saveSummary(driver, summary());
  seen = store.summaryFor(child, driver.driverId).summary;
  assert.deepEqual(seen.evidence, [{ label: "강한 제동", unit: "회/100km", typical: 5.5, value: 21.9, weeks: 4 }]);

  store.setShareScope(driver, "status");
  assert.deepEqual(store.summaryFor(child, driver.driverId).summary.evidence, []);
});

test("only listed fields are kept, so location or routes are never stored", () => {
  const { store, driver, child } = setup("evidence");
  store.link(child, { code: store.createInvite(driver).code });
  store.saveSummary(driver, summary({ route: [[37.5, 127.0]], location: "중앙대로", evidence: [{ label: "강한 제동", value: 3, lat: 37.5 }] }));
  const seen = store.summaryFor(child, driver.driverId).summary;
  assert.equal("route" in seen || "location" in seen, false);
  assert.deepEqual(Object.keys(seen.evidence[0]).sort(), ["label", "typical", "unit", "value", "weeks"]);
});

test("someone who is not linked cannot read or confirm a summary", () => {
  const { store, driver } = setup();
  store.saveSummary(driver, summary());
  const stranger = store.memberByToken(store.registerMember({ name: "다른 사람" }).token);
  assert.throws(() => store.summaryFor(stranger, driver.driverId), { code: "not_linked" });
  assert.throws(() => store.ack(stranger, driver.driverId, "2026-11-09"), { code: "not_linked" });
  assert.deepEqual(store.overview(stranger), []);
});

test("the car sees who is linked and who confirmed the latest summary", () => {
  const { store, driver, child } = setup();
  assert.deepEqual(store.carView(driver).family, []);
  store.link(child, { code: store.createInvite(driver).code, relation: "엄마" });
  store.saveSummary(driver, summary({ week: "2026-11-02", status: "observe" }));
  store.saveSummary(driver, summary());
  assert.equal(store.overview(child)[0].latest.week, "2026-11-09");
  assert.deepEqual(store.carView(driver).latest.ackedBy, []);
  store.ack(child, driver.driverId, "2026-11-09");
  const view = store.carView(driver);
  assert.deepEqual(view.family, [{ name: "김민수" }]);
  assert.deepEqual(view.latest.ackedBy, ["김민수"]);
  assert.equal(store.overview(child)[0].latest.acked, true);
});

test("bad summaries are rejected", () => {
  const { store, driver } = setup();
  assert.throws(() => store.saveSummary(driver, summary({ week: "11/09" })), { code: "invalid_week" });
  assert.throws(() => store.saveSummary(driver, summary({ status: "danger" })), { code: "invalid_status" });
});

test("the same driver of the same car gets the same server driver back, so family links survive", () => {
  const store = createStore();
  const key = "a".repeat(32);
  const first = store.registerDriver({ name: "김정숙", carKey: key, profileId: "p1" });
  assert.equal(first.reused, false);
  const again = store.registerDriver({ name: "김정숙", carKey: key, profileId: "p1", shareScope: "evidence" });
  assert.deepEqual([again.driverId, again.carToken, again.reused, again.shareScope], [first.driverId, first.carToken, true, "evidence"]);
  // Reinstalled app: new local id, same car and name.
  const reinstalled = store.registerDriver({ name: "김정숙", carKey: key, profileId: "p2" });
  assert.equal(reinstalled.driverId, first.driverId);
  // Another driver in the same car, and the same name in another car, are different drivers.
  assert.notEqual(store.registerDriver({ name: "박영호", carKey: key, profileId: "p3" }).driverId, first.driverId);
  assert.notEqual(store.registerDriver({ name: "김정숙", carKey: "b".repeat(32), profileId: "p1" }).driverId, first.driverId);
  // Without a car key nothing is matched (older apps).
  assert.notEqual(store.registerDriver({ name: "김정숙" }).driverId, first.driverId);
  // A malformed key is ignored rather than trusted.
  assert.equal(store.registerDriver({ name: "김정숙", carKey: "VIN-KMH12345" }).reused, false);
});
