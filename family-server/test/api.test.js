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
