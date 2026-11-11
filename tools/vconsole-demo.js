#!/usr/bin/env node
/* Pleos Virtual Console로 시연 주행을 재생한다. drivewatch-core의 DemoDrive와 같은 주행이다:
   8초 구간마다 강한 제동이 1·0·1·4·5·1회 나오고, 끝나면 멈춰서 P단에 넣는다.
   먼저: adb forward tcp:8088 tcp:8088
   사용: node tools/vconsole-demo.js [구간별 제동 횟수, 예: 1,0,1,4,5,1] */
const BASE = process.env.VCONSOLE_URL ?? "http://127.0.0.1:8088";
const WINDOW_MS = Number(process.env.WINDOW_MS ?? 8000);
const brakesPerWindow = (process.argv[2] ?? "1,0,1,4,5,1").split(",").map(Number);
const CRUISE = 37;
const BRAKED = 17;
const client = `demo-${process.pid}`;
const state = { speed: 0, accelerator: 0, brake: 0, steering: 0 };

const request = async (method, route) => {
  const response = await fetch(BASE + route, { method }).catch(() => null);
  if (!response) throw new Error(`Virtual Console에 연결할 수 없어요: ${BASE} (adb forward tcp:8088 tcp:8088)`);
  return response.status;
};
const controls = (change) => {
  Object.assign(state, change);
  return request("PUT", `/vehicle/controls?${new URLSearchParams(Object.entries(state).map(([key, value]) => [key, String(value)]))}`);
};
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const beat = setInterval(() => request("POST", `/browser/session?client=${client}`).catch(() => {}), 800);
  await request("POST", `/browser/session?client=${client}`);
  await sleep(3000); // 브라우저 신호가 몇 번 간 뒤에야 에뮬레이터가 활성화된다
  await controls({ speed: 0, accelerator: 0, brake: 0, steering: 0 });
  await request("PUT", "/vehicle/gear?gear=Park");
  await sleep(1500);

  const steps = [[150, () => controls({ speed: CRUISE, accelerator: 20 })]];
  brakesPerWindow.forEach((count, window) => {
    const spacing = WINDOW_MS / (count + 1);
    for (let i = 1; i <= count; i += 1) {
      const at = window * WINDOW_MS + i * spacing - 400;
      steps.push([at, () => controls({ speed: BRAKED, accelerator: 0, brake: 60 })]);
      steps.push([at + 700, () => controls({ speed: CRUISE, accelerator: 20, brake: 0 })]);
    }
    steps.push([window * WINDOW_MS + 300, () => controls({ steering: 8 })]);
    steps.push([window * WINDOW_MS + WINDOW_MS / 2 + 100, () => controls({ steering: -8 })]);
  });
  const end = brakesPerWindow.length * WINDOW_MS;
  steps.push([end + 500, () => controls({ speed: 0, accelerator: 0, brake: 40, steering: 0 })]);
  steps.push([end + 2000, () => request("PUT", "/vehicle/gear?gear=Park")]);
  steps.push([end + 2300, () => controls({ brake: 0 })]);
  steps.sort((a, b) => a[0] - b[0]);

  const status = await request("PUT", "/vehicle/gear?gear=Drive");
  if (status !== 200) throw new Error(`기어를 바꾸지 못했어요 (HTTP ${status})`);
  const started = performance.now();
  console.log(`출발. 구간 ${WINDOW_MS / 1000}초 × ${brakesPerWindow.length}, 구간별 강한 제동 ${brakesPerWindow.join("·")}회`);
  let failures = 0;
  for (const [at, action] of steps) {
    await sleep(Math.max(0, at - (performance.now() - started)));
    if ((await action()) !== 200) failures += 1;
  }
  await sleep(1000);
  clearInterval(beat);
  await request("POST", `/browser/session?client=${client}&closing=1`);
  console.log(failures ? `끝. 실패한 요청 ${failures}건` : "끝. 모든 요청 성공");
  if (failures) process.exitCode = 1;
}

main().catch((error) => { console.error(error.message); process.exitCode = 1; });
