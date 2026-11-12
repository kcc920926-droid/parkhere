/* DriveWatch 가족 요약 서버. 의존성 없이 node 20 이상에서 실행한다.
   차량 앱은 /api/car/*, 자녀 폰 웹 화면은 /api/family/* 를 쓴다. 시연용이며 운영 환경용 인증·보관 설계는 아니다.
   주소 목록은 ROUTES 한 곳에 두고, openapi.json과 같은지 테스트가 확인한다. */
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { createStore, StoreError } = require("./store.js");
const { createSender, mobile, mask, inviteText } = require("./sms.js");
const { loadDemo } = require("./demo.js");

const PUBLIC = path.join(__dirname, "public");
const SPEC = path.join(__dirname, "openapi.json");
const TYPES = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8" };
const MAX_BODY = 16 * 1024;
const MINUTE = 60 * 1000;

/* [방법, 경로, 누가 부르나, 처리] — 경로의 {이름}은 주소에서 값을 꺼낸다. */
const ROUTES = [
  ["POST", "/api/car/drivers", "none", (store, who, body) => store.registerDriver(body)],
  ["GET", "/api/car/me", "car", (store, who) => store.carView(who)],
  ["PUT", "/api/car/share-scope", "car", (store, who, body) => store.setShareScope(who, body.shareScope)],
  ["POST", "/api/car/invites", "car", (store, who) => store.createInvite(who)],
  ["POST", "/api/car/invites/sms", "car", async (store, who, body, params, { sms, publicBase }) => {
    // The number belongs to the child: the car must show that it was given with consent for this one message.
    if (body.consent !== true) throw new StoreError("consent_required", 400);
    const { code } = store.activeInvite(who, body.code);
    const to = mobile(body.phone) ?? (() => { throw new StoreError("invalid_phone", 400); })();
    const result = await sms.send(to, inviteText(who.name, `${publicBase}/family/?code=${code}`));
    if (result.failed) throw new StoreError("sms_failed", 502);
    return { sent: result.sent, mode: result.mode, to: mask(to) }; // the number itself is never stored
  }],
  ["POST", "/api/car/summaries", "car", (store, who, body) => store.saveSummary(who, body)],
  ["GET", "/api/car/backup", "car", (store, who) => store.backupView(who)],
  ["PUT", "/api/car/backup", "car", (store, who, body) => store.setBackup(who, body.enabled)],
  ["POST", "/api/car/trips", "car", (store, who, body) => store.saveTrip(who, body)],
  ["PUT", "/api/car/profile", "car", (store, who, body) => store.saveProfile(who, body)],
  ["GET", "/api/demo/scenarios", "none", (store, who, body, params, { demo }) => demo.list()],
  ["GET", "/api/demo/drives/{scenarioId}", "none", (store, who, body, [id], { demo }) => demo.drive(id)],
  ["GET", "/api/demo/histories/{scenarioId}", "none", (store, who, body, [id], { demo }) => demo.history(id)],
  ["POST", "/api/family/members", "none", (store, who, body) => store.registerMember(body)],
  ["POST", "/api/family/links", "family", (store, who, body) => store.link(who, body)],
  ["GET", "/api/family/overview", "family", (store, who) => ({ name: who.name, drivers: store.overview(who) })],
  ["GET", "/api/family/drivers/{driverId}/summary", "family", (store, who, body, [id]) => store.summaryFor(who, id)],
  ["GET", "/api/family/drivers/{driverId}/summaries", "family", (store, who, body, [id]) => store.summariesFor(who, id)],
  ["POST", "/api/family/drivers/{driverId}/ack", "family", (store, who, body, [id]) => store.ack(who, id, body.week)],
].map(([method, route, auth, handler]) => ({
  method, route, auth, handler,
  pattern: new RegExp(`^${route.replace(/\{\w+\}/g, "([\\w-]+)")}$`),
}));

/* 한 주소(IP)가 정해진 시간 안에 보낼 수 있는 요청 수. 공개 주소에서 코드 맞히기와 무한 등록을 막는다. */
const LIMITS = {
  register: { max: 10, windowMs: 60 * MINUTE, routes: ["/api/car/drivers", "/api/family/members"] },
  /* 문자는 돈이 들고 남에게 스팸이 될 수 있어서 한 주소에서 한 시간에 5통까지. */
  sms: { max: 5, windowMs: 60 * MINUTE, routes: ["/api/car/invites/sms"] },
  link: { max: 10, windowMs: 10 * MINUTE, routes: ["/api/family/links"] },
  write: { max: 120, windowMs: MINUTE, routes: null },
};

function createLimiter(limits, now) {
  const hits = new Map();
  return (ip, route) => {
    for (const [name, limit] of Object.entries(limits)) {
      if (limit.routes && !limit.routes.includes(route)) continue;
      const key = `${name}:${ip}`;
      const recent = (hits.get(key) ?? []).filter((at) => at > now() - limit.windowMs);
      if (recent.length >= limit.max) { hits.set(key, recent); throw new StoreError("rate_limited", 429); }
      recent.push(now());
      hits.set(key, recent);
    }
    if (hits.size > 5000) for (const [key, list] of hits) if (!list.some((at) => at > now() - 60 * MINUTE)) hits.delete(key);
  };
}

function readJson(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY) { reject(new StoreError("body_too_large", 413)); request.destroy(); return; }
      chunks.push(chunk);
    });
    request.on("end", () => {
      if (!chunks.length) return resolve({});
      try {
        const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
        resolve(body && typeof body === "object" && !Array.isArray(body) ? body : {});
      } catch { reject(new StoreError("invalid_json", 400)); }
    });
    request.on("error", reject);
  });
}

function createApp(store = createStore(), { limits = LIMITS, now = () => Date.now(), trustProxy = false, sms = createSender(), publicBase = "https://drive.olvend.com", demo = loadDemo() } = {}) {
  const limit = createLimiter(limits, now);
  const bearer = (request) => (request.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
  const identify = { none: () => null, car: (request) => store.driverByToken(bearer(request)), family: (request) => store.memberByToken(bearer(request)) };
  /* 터널 뒤에서는 접속한 쪽 주소가 항상 127.0.0.1이라, 터널이 알려 주는 주소를 쓴다. */
  const clientIp = (request) => (trustProxy && request.headers["cf-connecting-ip"]) || request.socket.remoteAddress || "unknown";

  const send = (response, status, body, headers = {}) => {
    response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers });
    response.end(JSON.stringify(body));
  };

  function sendFile(response, target, method) {
    if (!fs.existsSync(target) || !fs.statSync(target).isFile()) return send(response, 404, { error: "not_found" });
    response.writeHead(200, { "content-type": TYPES[path.extname(target)] ?? "application/octet-stream", "cache-control": "no-cache" });
    if (method === "HEAD") return response.end();
    fs.createReadStream(target).pipe(response);
  }

  /* 폰 화면은 /family/ 아래에 둔다. 같은 도메인의 다른 사이트와 겹치지 않게 하려는 것이다. '/'로도 열린다. */
  function serveStatic(pathname, response, method) {
    if (pathname === "/family") { response.writeHead(301, { location: "/family/" }); return response.end(); }
    if (pathname === "/api/docs") return sendFile(response, path.join(PUBLIC, "api-docs.html"), method);
    if (pathname === "/api/openapi.json") return sendFile(response, SPEC, method);
    const relative = pathname.replace(/^\/family\//, "/").slice(1) || "index.html";
    const target = path.normalize(path.join(PUBLIC, relative));
    if (!target.startsWith(PUBLIC + path.sep)) return send(response, 404, { error: "not_found" });
    sendFile(response, target, method);
  }

  return http.createServer(async (request, response) => {
    response.setHeader("x-content-type-options", "nosniff");
    response.setHeader("referrer-policy", "no-referrer");
    let pathname = "/";
    try {
      pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
      const reading = request.method === "GET" || request.method === "HEAD";
      if (reading && (!pathname.startsWith("/api/") || pathname === "/api/docs" || pathname === "/api/openapi.json")) {
        return serveStatic(pathname, response, request.method);
      }
      if (!pathname.startsWith("/api/")) return send(response, 405, { error: "method_not_allowed" });
      const route = ROUTES.find((entry) => entry.method === request.method && entry.pattern.test(pathname));
      if (!route) return send(response, 404, { error: "not_found" });
      if (request.method !== "GET") limit(clientIp(request), route.route);
      const who = identify[route.auth](request);
      if (route.auth !== "none" && !who) throw new StoreError("unauthorized", 401);
      const body = request.method === "GET" ? {} : await readJson(request);
      send(response, 200, await route.handler(store, who, body, route.pattern.exec(pathname).slice(1), { sms, publicBase, demo }));
    } catch (error) {
      if (error instanceof StoreError) return send(response, error.status ?? 400, { error: error.code });
      if (error instanceof URIError) return send(response, 400, { error: "bad_request" });
      console.error(error);
      send(response, 500, { error: "server_error" });
    }
  });
}

if (require.main === module) {
  const port = Number(process.env.PORT ?? 8795);
  const host = process.env.HOST ?? "127.0.0.1";
  const file = process.env.STORE_FILE ?? path.join(__dirname, "data", "store.json");
  const trustProxy = process.env.TRUST_PROXY === "1";
  const sms = createSender(process.env);
  const publicBase = (process.env.PUBLIC_BASE ?? "https://drive.olvend.com").replace(/\/$/, "");
  createApp(createStore({ file }), { trustProxy, sms, publicBase }).listen(port, host, () => {
    console.log(`DriveWatch 가족 요약 서버: http://${host}:${port}/family/  명세: /api/docs  (기록 파일: ${file})`);
    console.log(`초대 문자: ${sms.mode === "dry-run" ? "보내지 않음(시연 모드, SMS_PROVIDER=solapi와 키를 넣으면 발송)" : "SOLAPI로 발송"} · 링크 ${publicBase}/family/`);
  });
}

module.exports = { createApp, ROUTES, LIMITS };
