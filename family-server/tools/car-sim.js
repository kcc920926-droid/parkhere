#!/usr/bin/env node
/* 차량 앱이 나오기 전에 차량 역할을 대신한다. 서버가 켜져 있어야 한다.
   사용: node tools/car-sim.js setup [이름] [status|evidence]   운전자를 등록하고 초대 코드를 띄운다
         node tools/car-sim.js invite                            초대 코드를 새로 띄운다
         node tools/car-sim.js send stable|observe|talk          이번 주 요약을 보낸다
         node tools/car-sim.js scope status|evidence             가족에게 보내는 범위를 바꾼다
         node tools/car-sim.js me                                연결된 가족과 확인 여부를 본다 */
const fs = require("node:fs");
const path = require("node:path");

const BASE = process.env.FAMILY_SERVER ?? "http://127.0.0.1:8795";
const FILE = path.join(__dirname, "..", "data", "car-sim.json");

/* 수치는 drivewatch-core의 합성 12주 기록('변화가 이어진 기록')을 주간 판정에 넣은 결과다. */
const SAMPLES = {
  stable: {
    headline: "이번 주도 평소처럼 운전하셨어요.",
    goodPoints: ["강한 제동이 평소 범위였어요", "밤 운전 0회"],
    promise: "해 지면 운전 안 하기 · 이번 주 지킴",
  },
  observe: {
    changes: ["강한 제동이 2주째 평소보다 많았어요"],
    evidence: [{ label: "강한 제동", unit: "회/100km", typical: 6.3, value: 15.6, weeks: 2 }],
    goodPoints: ["밤 운전 0회"],
    promise: "해 지면 운전 안 하기 · 이번 주 지킴",
  },
  talk: {
    changes: ["강한 제동이 4주째, 조향 보정이 2주째 평소보다 많았어요"],
    evidence: [
      { label: "강한 제동", unit: "회/100km", typical: 6.3, value: 21.9, weeks: 4 },
      { label: "조향 보정", unit: "회/100km", typical: 37.5, value: 59.4, weeks: 2 },
    ],
    goodPoints: ["밤 운전 0회"],
    promise: "해 지면 운전 안 하기 · 이번 주 지킴",
  },
};

const monday = () => {
  const today = new Date();
  const utc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  return new Date(utc - ((new Date(utc).getUTCDay() + 6) % 7) * 86400000).toISOString().slice(0, 10);
};

async function call(method, route, { token, body } = {}) {
  const response = await fetch(BASE + route, {
    method,
    headers: { ...(body ? { "content-type": "application/json" } : {}), ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  }).catch(() => { throw new Error(`서버에 연결할 수 없어요: ${BASE} (npm start로 먼저 켜세요)`); });
  const data = await response.json();
  if (!response.ok) throw new Error(`${method} ${route} → ${response.status} ${data.error}`);
  return data;
}

const saved = () => {
  if (!fs.existsSync(FILE)) throw new Error("먼저 setup을 실행하세요.");
  return JSON.parse(fs.readFileSync(FILE, "utf8"));
};
const showCode = (name, { code, expiresAt }) => {
  console.log(`${name} 님 차의 초대 코드: ${code.slice(0, 3)} ${code.slice(3)}  (${new Date(expiresAt).toLocaleTimeString("ko-KR")}까지)`);
  console.log(`폰에서 열 주소: ${BASE}/?code=${code}`);
};

async function main([command, ...args]) {
  if (command === "setup") {
    const name = args[0] ?? "김정숙";
    const car = await call("POST", "/api/car/drivers", { body: { name, shareScope: args[1] ?? "evidence" } });
    fs.mkdirSync(path.dirname(FILE), { recursive: true });
    fs.writeFileSync(FILE, JSON.stringify({ ...car, name }));
    showCode(name, await call("POST", "/api/car/invites", { token: car.carToken }));
  } else if (command === "invite") {
    const car = saved();
    showCode(car.name, await call("POST", "/api/car/invites", { token: car.carToken }));
  } else if (command === "send" && SAMPLES[args[0]]) {
    const body = { week: monday(), status: args[0], ...SAMPLES[args[0]] };
    console.log(await call("POST", "/api/car/summaries", { token: saved().carToken, body }));
  } else if (command === "scope") {
    console.log(await call("PUT", "/api/car/share-scope", { token: saved().carToken, body: { shareScope: args[0] } }));
  } else if (command === "me") {
    console.log(JSON.stringify(await call("GET", "/api/car/me", { token: saved().carToken }), null, 2));
  } else {
    console.log(fs.readFileSync(__filename, "utf8").split("\n").slice(2, 7).join("\n"));
    process.exitCode = 1;
  }
}

main(process.argv.slice(2)).catch((error) => { console.error(error.message); process.exitCode = 1; });
