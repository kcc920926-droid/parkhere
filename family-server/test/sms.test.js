const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { createApp } = require("../server.js");
const { createStore } = require("../store.js");
const { mobile, mask, solapiAuthorization, createSender } = require("../sms.js");

/* A fake sender that remembers what it was asked to send. */
function recorder(result = { sent: true, mode: "test" }) {
  const sent = [];
  return { sent, sender: { mode: "test", async send(to, text) { sent.push({ to, text }); return result; } } };
}

async function withServer(options, run) {
  const server = createApp(options.store ?? createStore(), { publicBase: "https://drive.example", ...options });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, route, { token, body } = {}) => {
    const response = await fetch(base + route, {
      method,
      headers: { ...(body ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, data: await response.json().catch(() => null) };
  };
  try { await run(call); } finally { await new Promise((resolve) => server.close(resolve)); }
}

test("only Korean mobile numbers are accepted, in any common spelling", () => {
  assert.equal(mobile("010-1234-5678"), "01012345678");
  assert.equal(mobile("+82 10 1234 5678"), "01012345678");
  assert.equal(mobile("011 123 4567"), "0111234567");
  assert.equal(mobile("02-123-4567"), null);
  assert.equal(mobile("1234"), null);
  assert.equal(mask("01012345678"), "010-****-5678");
});

test("the SOLAPI header signs date + salt with the secret, in hex", () => {
  const header = solapiAuthorization("KEY", "SECRET", "2019-07-01T00:41:48Z", "jqsba2jxjnrjor");
  const signature = crypto.createHmac("sha256", "SECRET").update("2019-07-01T00:41:48Zjqsba2jxjnrjor").digest("hex");
  assert.equal(header, `HMAC-SHA256 apiKey=KEY, date=2019-07-01T00:41:48Z, salt=jqsba2jxjnrjor, signature=${signature}`);
});

test("without SOLAPI keys nothing is sent and the log shows only a masked number", async () => {
  const lines = [];
  const sender = createSender({}, { log: (line) => lines.push(line) });
  assert.equal(sender.mode, "dry-run");
  assert.deepEqual(await sender.send("01012345678", "hello"), { sent: false, mode: "dry-run" });
  assert.match(lines[0], /010-\*\*\*\*-5678/);
  assert.doesNotMatch(lines.join("\n"), /01012345678/);
});

test("with SOLAPI keys one message goes to send-many with the registered sender", async () => {
  const calls = [];
  const post = async (url, headers, body) => { calls.push({ url, headers, body }); return { status: 200, data: { groupId: "G" } }; };
  const sender = createSender({ SMS_PROVIDER: "solapi", SOLAPI_API_KEY: "K", SOLAPI_API_SECRET: "S", SMS_FROM: "02-930-2266" }, { post });
  assert.equal(sender.mode, "solapi");
  assert.deepEqual(await sender.send("01012345678", "hi"), { sent: true, mode: "solapi" });
  assert.equal(calls[0].url, "https://api.solapi.com/messages/v4/send-many");
  assert.match(calls[0].headers.authorization, /^HMAC-SHA256 apiKey=K, date=\S+, salt=[0-9a-f]{32}, signature=[0-9a-f]{64}$/);
  assert.deepEqual(calls[0].body, { messages: [{ to: "01012345678", from: "029302266", text: "hi", autoTypeDetect: true }] });
});

test("the car texts its own live invite link, and the number is not stored", async () => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "dw-sms-")), "store.json");
  const { sent, sender } = recorder();
  await withServer({ store: createStore({ file }), sms: sender }, async (call) => {
    const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
    const { code } = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
    const ok = await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "010-1234-5678", consent: true } });
    assert.equal(ok.status, 200);
    assert.deepEqual(ok.data, { sent: true, mode: "test", to: "010-****-5678" });
    assert.equal(sent[0].to, "01012345678");
    assert.match(sent[0].text, /김정숙 님이 가족 연결을 요청했어요/);
    assert.ok(sent[0].text.endsWith(`https://drive.example/family/?code=${code}`));
  });
  assert.doesNotMatch(fs.readFileSync(file, "utf8"), /1234.?5678/);
});

test("a bad number, someone else's code or an expired code sends nothing", async () => {
  let clock = Date.now();
  const store = createStore({ now: () => clock });
  const { sent, sender } = recorder();
  await withServer({ store, sms: sender, now: () => clock }, async (call) => {
    const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
    const other = (await call("POST", "/api/car/drivers", { body: { name: "박영호" } })).data;
    const { code } = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
    assert.equal((await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "02-123-4567", consent: true } })).data.error, "invalid_phone");
    assert.equal((await call("POST", "/api/car/invites/sms", { token: other.carToken, body: { code, phone: "01012345678", consent: true } })).status, 404);
    assert.equal((await call("POST", "/api/car/invites/sms", { body: { code, phone: "01012345678", consent: true } })).status, 401);
    clock += 11 * 60 * 1000;
    assert.equal((await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "01012345678", consent: true } })).data.error, "invalid_code");
  });
  assert.equal(sent.length, 0);
});

test("texts are limited to five an hour, and a provider failure is reported", async () => {
  const { sender } = recorder();
  await withServer({ sms: sender }, async (call) => {
    const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
    const { code } = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
    const statuses = [];
    for (let i = 0; i < 6; i++) statuses.push((await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "01012345678", consent: true } })).status);
    assert.deepEqual(statuses, [200, 200, 200, 200, 200, 429]);
  });
  await withServer({ sms: recorder({ sent: false, mode: "solapi", failed: true }).sender }, async (call) => {
    const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
    const { code } = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
    const failed = await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "01012345678", consent: true } });
    assert.deepEqual([failed.status, failed.data.error], [502, "sms_failed"]);
  });
});

test("no consent, no text", async () => {
  const { sent, sender } = recorder();
  await withServer({ sms: sender }, async (call) => {
    const car = (await call("POST", "/api/car/drivers", { body: { name: "김정숙" } })).data;
    const { code } = (await call("POST", "/api/car/invites", { token: car.carToken })).data;
    for (const consent of [undefined, false, "true"]) {
      const answer = await call("POST", "/api/car/invites/sms", { token: car.carToken, body: { code, phone: "01012345678", consent } });
      assert.deepEqual([answer.status, answer.data.error], [400, "consent_required"]);
    }
  });
  assert.equal(sent.length, 0);
});
