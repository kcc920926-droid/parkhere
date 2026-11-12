/* 시연용 시나리오. demo/drives/*.json은 주행 한 번의 신호 시간표(차량 앱의 ReplayLink가 재생),
   demo/histories/*.json은 몇 주치 합성 기록이다. 차량 앱도 같은 파일을 오프라인 예비본으로 담는다
   (app/build.gradle.kts의 assets.srcDir). 사람의 기록이 아니므로 인증 없이 읽을 수 있다. */
const fs = require("node:fs");
const path = require("node:path");
const { StoreError } = require("./store.js");

function loadDemo(dir = path.join(__dirname, "demo")) {
  const read = (kind) => Object.fromEntries(
    (fs.existsSync(path.join(dir, kind)) ? fs.readdirSync(path.join(dir, kind)) : [])
      .filter((name) => name.endsWith(".json"))
      .map((name) => JSON.parse(fs.readFileSync(path.join(dir, kind, name), "utf8")))
      .map((item) => [item.id, item]),
  );
  const drives = read("drives");
  const histories = read("histories");
  const seconds = (drive) => Math.max(...drive.events.map((event) => event.at));
  return {
    list: () => ({
      drives: Object.values(drives).map((d) => ({ id: d.id, title: d.title, place: d.place, comfortable: d.comfortable, seconds: seconds(d), expect: d.expect })),
      histories: Object.values(histories).map((h) => ({ id: h.id, title: h.title, expect: h.expect, trips: h.trips.length })),
    }),
    drive: (id) => drives[id] ?? (() => { throw new StoreError("no_scenario", 404); })(),
    history: (id) => histories[id] ?? (() => { throw new StoreError("no_scenario", 404); })(),
  };
}

module.exports = { loadDemo };
