const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { createApp, ROUTES } = require("../server.js");
const { createStore } = require("../store.js");

async function withServer(run) {
  const server = createApp();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, path, { token, body } = {}) => {
    const response = await fetch(base + path, {
      method,
      headers: { ...(body ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, data: await response.json().catch(() => null), type: response.headers.get("content-type") };
  };
  try { await run(call, base); } finally { await new Promise((resolve) => server.close(resolve)); }
}

test("car sends a weekly summary and the family page reads and confirms it", () => withServer(async (call) => {
  const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙", shareScope: "evidence" } })).data;
  const invite = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
  const child = (await call("POST", "/api/family/members", { body: { name: "김민수" } })).data;

  const linked = await call("POST", "/api/family/links", { token: child.token, body: { code: invite.code, relation: "엄마" } });
  assert.equal(linked.status, 200);
  assert.equal(linked.data.name, "김정숙");

  const sent = await call("POST", "/api/car/summaries", {
    token: car.carToken,
    body: { week: "2026-11-09", status: "talk", changes: ["강한 제동이 4주째 평소보다 많았어요"], evidence: [{ label: "강한 제동", unit: "회/100km", typical: 5.5, value: 21.9, weeks: 4 }] },
  });
  assert.deepEqual(sent.data, { week: "2026-11-09", status: "talk", evidenceStored: 1 });

  const overview = (await call("GET", "/api/family/overview", { token: child.token })).data;
  assert.equal(overview.drivers[0].latest.status, "talk");
  const seen = (await call("GET", `/api/family/drivers/${car.driverId}/summary`, { token: child.token })).data;
  assert.equal(seen.relation, "엄마");
  assert.equal(seen.summary.evidence[0].value, 21.9);

  await call("POST", `/api/family/drivers/${car.driverId}/ack`, { token: child.token, body: { week: "2026-11-09" } });
  assert.deepEqual((await call("GET", "/api/car/me", { token: car.carToken })).data.latest.ackedBy, ["김민수"]);
}));

test("requests without a valid token, wrong codes, and unknown paths are refused", () => withServer(async (call) => {
  assert.equal((await call("GET", "/api/family/overview")).status, 401);
  assert.equal((await call("POST", "/api/car/invites", { token: "nope" })).status, 401);
  const child = (await call("POST", "/api/family/members", { body: { name: "김민수" } })).data;
  const wrong = await call("POST", "/api/family/links", { token: child.token, body: { code: "123456" } });
  assert.equal(wrong.status, 404);
  assert.equal(wrong.data.error, "invalid_code");
  assert.equal((await call("GET", "/api/nothing")).status, 404);
  assert.equal((await call("POST", "/api/family/members", { body: {} })).data.error, "name_required");
}));

test("the phone page is served under /family/ and files outside public are not", () => withServer(async (call, base) => {
  for (const address of ["/family/", "/"]) {
    const response = await fetch(base + address);
    assert.equal(response.status, 200, address);
    assert.match(await response.text(), /DriveWatch 가족/);
  }
  assert.equal((await fetch(`${base}/family/app.js`)).status, 200);
  assert.equal((await fetch(`${base}/family`, { redirect: "manual" })).headers.get("location"), "/family/");
  assert.equal((await fetch(`${base}/family/..%2F..%2Fserver.js`)).status, 404);
  const page = await fetch(`${base}/`);
  assert.equal(page.status, 200);
  assert.match(page.headers.get("content-type"), /text\/html/);
  assert.match(await page.text(), /DriveWatch 가족/);
  assert.equal((await fetch(`${base}/app.js`)).status, 200);
  assert.equal((await fetch(`${base}/..%2Fserver.js`)).status, 404);
  assert.equal((await fetch(`${base}/%2e%2e/store.js`)).status, 404);
}));

test("the published spec lists exactly the addresses the server answers", () => withServer(async (call, base) => {
  const spec = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "openapi.json"), "utf8"));
  const documented = Object.entries(spec.paths).flatMap(([route, methods]) => Object.keys(methods).map((method) => `${method.toUpperCase()} ${route}`));
  assert.deepEqual(documented.sort(), ROUTES.map(({ method, route }) => `${method} ${route}`).sort());
  for (const [route, methods] of Object.entries(spec.paths)) {
    for (const [method, operation] of Object.entries(methods)) {
      const auth = ROUTES.find((entry) => entry.route === route && entry.method === method.toUpperCase()).auth;
      const expected = auth === "none" ? [] : [{ [auth === "car" ? "carToken" : "familyToken"]: [] }];
      assert.deepEqual(operation.security, expected, `${method} ${route} security`);
    }
  }
  const served = await fetch(`${base}/api/openapi.json`);
  assert.equal(served.status, 200);
  assert.deepEqual(await served.json(), spec);
  const docs = await fetch(`${base}/api/docs`);
  assert.equal(docs.status, 200);
  assert.match(docs.headers.get("content-type"), /text\/html/);
}));

test("one address cannot keep registering or guessing codes", async () => {
  const limits = { register: { max: 2, windowMs: 60000, routes: ["/api/car/drivers", "/api/family/members"] }, link: { max: 3, windowMs: 60000, routes: ["/api/family/links"] }, write: { max: 100, windowMs: 60000, routes: null } };
  const server = createApp(createStore(), { limits, trustProxy: true });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (route, body, headers = {}) => fetch(base + route, { method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(body) });
  try {
    const visitor = { "cf-connecting-ip": "203.0.113.7" };
    const member = await (await post("/api/family/members", { name: "가" }, visitor)).json();
    assert.equal((await post("/api/family/members", { name: "나" }, visitor)).status, 200);
    const blocked = await post("/api/family/members", { name: "다" }, visitor);
    assert.equal(blocked.status, 429);
    assert.equal((await blocked.json()).error, "rate_limited");
    assert.equal((await post("/api/family/members", { name: "라" }, { "cf-connecting-ip": "203.0.113.8" })).status, 200);

    const guess = () => post("/api/family/links", { code: "000000" }, { ...visitor, authorization: `Bearer ${member.token}` });
    for (let i = 0; i < 3; i += 1) assert.equal((await guess()).status, 404);
    assert.equal((await guess()).status, 429);
  } finally { await new Promise((resolve) => server.close(resolve)); }
});

test("trips are kept only while the driver allows backup, never shown to family, and deleted when backup is turned off", () => withServer(async (call) => {
  const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
  const trip = { week: "2026-11-09", place: "병원", km: 10, night: false, reroutes: 2, turns: 3, signals: 2, hardBrakes: 1 };

  assert.equal((await call("POST", "/api/car/trips", { token: car.carToken, body: trip })).status, 403);
  assert.deepEqual((await call("GET", "/api/car/backup", { token: car.carToken })).data, { enabled: false, profile: null, trips: [] });

  assert.deepEqual((await call("PUT", "/api/car/backup", { token: car.carToken, body: { enabled: true } })).data, { enabled: true, trips: 0 });
  assert.deepEqual((await call("POST", "/api/car/trips", { token: car.carToken, body: { ...trip, latitude: 37.5, route: "x" } })).data, { stored: 1 });
  await call("PUT", "/api/car/profile", { token: car.carToken, body: { promises: [0, 1, 1, 99], marked: [{ place: "병원", point: "중앙대로 합류" }] } });

  const restored = (await call("GET", "/api/car/backup", { token: car.carToken })).data;
  assert.deepEqual(restored.trips, [trip]); // extra fields such as location are dropped
  assert.deepEqual(restored.profile, { promises: [0, 1], marked: [{ place: "병원", point: "중앙대로 합류" }] });

  assert.equal((await call("POST", "/api/car/trips", { token: car.carToken, body: { ...trip, signals: 4 } })).status, 400);
  assert.equal((await call("POST", "/api/car/trips", { token: car.carToken, body: { ...trip, week: "soon" } })).status, 400);
  assert.equal((await call("GET", "/api/car/backup")).status, 401);

  const child = (await call("POST", "/api/family/members", { body: { name: "김민수" } })).data;
  assert.equal((await call("GET", "/api/car/backup", { token: child.token })).status, 401);

  assert.deepEqual((await call("PUT", "/api/car/backup", { token: car.carToken, body: { enabled: false } })).data, { enabled: false, trips: 0 });
  assert.deepEqual((await call("GET", "/api/car/backup", { token: car.carToken })).data, { enabled: false, profile: null, trips: [] });
}));

test("the family monthly view lists every week the driver sent, newest first, with the same sharing rule", () => withServer(async (call) => {
  const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙", shareScope: "status" } })).data;
  const invite = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
  const child = (await call("POST", "/api/family/members", { body: { name: "김민수" } })).data;
  const stranger = (await call("POST", "/api/family/members", { body: { name: "남" } })).data;
  await call("POST", "/api/family/links", { token: child.token, body: { code: invite.code, relation: "엄마" } });
  for (const [week, status] of [["2026-11-02", "stable"], ["2026-11-16", "observe"], ["2026-11-09", "stable"]]) {
    await call("POST", "/api/car/summaries", { token: car.carToken, body: { week, status, evidence: [{ label: "재탐색", unit: "회/10km", typical: 0.2, value: 0.7, weeks: 2 }] } });
  }
  const list = (await call("GET", `/api/family/drivers/${car.driverId}/summaries`, { token: child.token })).data;
  assert.deepEqual(list.summaries.map((s) => [s.week, s.status]), [["2026-11-16", "observe"], ["2026-11-09", "stable"], ["2026-11-02", "stable"]]);
  assert.ok(list.summaries.every((s) => s.evidence.length === 0 && s.evidenceShared === false)); // status only
  assert.equal((await call("GET", `/api/family/drivers/${car.driverId}/summaries`, { token: stranger.token })).status, 403);
}));

test("demo scenarios are listed and served without a login, and unknown ones are 404", () => withServer(async (call) => {
  const list = (await call("GET", "/api/demo/scenarios")).data;
  assert.equal(list.drives.length, 6);
  assert.deepEqual(list.histories.map((h) => h.id).sort(), ["steady", "talk", "watch"]);
  const drive = (await call("GET", "/api/demo/drives/hospital-fast")).data;
  assert.equal(drive.place, "병원");
  assert.deepEqual(drive.events.filter((e) => e.gear).map((e) => e.gear), ["D", "P"]);
  assert.ok(drive.events.every((e, i, all) => i === 0 || all[i - 1].at <= e.at), "events are in time order");
  const history = (await call("GET", "/api/demo/histories/talk")).data;
  assert.equal(history.expect, "대화 권장");
  assert.equal((await call("GET", "/api/demo/drives/nope")).status, 404);
}));

test("the latest app build is described and downloadable, and missing builds are 404", async () => {
  const os = require("node:os");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "dw-app-"));
  const { loadRelease } = require("../release.js");
  const none = createApp(createStore(), { release: loadRelease(null) });
  await new Promise((resolve) => none.listen(0, "127.0.0.1", resolve));
  assert.equal((await fetch(`http://127.0.0.1:${none.address().port}/api/app/latest`)).status, 404);
  none.close();
  fs.writeFileSync(path.join(dir, "latest.json"), JSON.stringify({ versionCode: 7, versionName: "0.2.0+abc1234", commit: "abc1234", notes: "QR", sha256: "x", size: 3 }));
  fs.writeFileSync(path.join(dir, "DriveWatch.apk"), "APK");
  const server = createApp(createStore(), { release: loadRelease(dir), publicBase: "https://drive.example" });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const latest = await (await fetch(`${base}/api/app/latest`)).json();
  assert.deepEqual([latest.versionCode, latest.download], [7, "https://drive.example/api/app/download"]);
  const apk = await fetch(`${base}/api/app/download`);
  assert.equal(apk.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.equal(await apk.text(), "APK");
  server.close();
});
