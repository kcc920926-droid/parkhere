const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const engine = require("./engine.js");
const { summarize } = require("./landing-data.js");

const replays = new Function(`${fs.readFileSync(path.join(__dirname, "replays.js"), "utf8")}\nreturn DriveWatcherReplays;`)();

test("the landing overview shows what the engine decides for the city demo trip", () => {
  const view = summarize(engine, replays);
  assert.deepEqual(view.windows.map(({ value }) => value), [1, 0, 1, 4, 5, 1]);
  assert.deepEqual(view.windows.map(({ observation }) => observation), ["typical", "typical", "typical", "watching", "change", "typical"]);
  assert.equal(view.upper, 3);
  assert.equal(view.detectionMinute, 10);
  assert.equal(view.parked, true);
  assert.deepEqual(view.card, {
    title: "평소와 달라요",
    evidence: "강한 제동 · 2분 구간당 평소 3회 이하, 이번 최고 5회",
    where: "출발 후 6–8분 4회, 8–10분 5회",
  });
});

test("the landing page loads the scripts it needs and links every demo", () => {
  const page = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
  for (const script of ["engine.js", "replays.js", "landing-data.js", "landing-evidence.js", "landing.js"]) {
    assert.ok(page.includes(`src="${script}"`), script);
    assert.ok(fs.existsSync(path.join(__dirname, script)), `${script} exists`);
  }
  for (const link of ["engine-demo.html", "console.html", "profile-demo.html", "PLAYBOOK.MD", "https://drive.olvend.com/family/", "https://drive.olvend.com/api/docs"]) {
    assert.ok(page.includes(`href="${link}"`), link);
  }
});
