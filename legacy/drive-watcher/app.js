const { metrics, createSession, baselineFor, requiredWindows, windowMinutes } = DriveWatcher;
const { contexts, history, scenes, framesFor } = DriveWatcherReplays;
const element = (id) => document.getElementById(id);
const statusCopy = {
  idle: ["정차 중", "주행 기록을 기다리고 있어요."],
  typical: ["평소 범위 안이에요", "이번 구간에서 비교 가능한 신호를 확인했어요."],
  insufficient: ["비교할 기록이 부족해요", "같은 조건의 평소 기록과 이번 신호가 필요해요."],
  watching: ["다음 구간도 비교할게요", "평소 범위를 벗어난 신호가 한 구간에서 보였어요."],
};

const sceneNav = element("scenes");
const variantButtons = [...document.querySelectorAll("[data-variant]")];
const readingList = element("readings");
const outcome = document.querySelector(".outcome");

let scene = scenes[0];
let variant = "changed";
let frames;
let session;
let profile;
let steps;
let timer = null;

function formatClock(minutes) {
  const hours = Math.floor(minutes / 60);
  const rest = String(minutes % 60).padStart(2, "0");
  return `${String(hours).padStart(2, "0")}:${rest}`;
}

function stop() {
  if (timer !== null) clearInterval(timer);
  timer = null;
}

function restart() {
  stop();
  frames = framesFor(scene, variant);
  session = createSession(history);
  profile = baselineFor(history, scene.context);
  steps = [];
  render();
}

function advance() {
  if (steps.length < frames.length) steps.push(session.ingest(frames[steps.length]));
  if (steps.length === frames.length) stop();
  render();
}

function toggleReplay() {
  if (timer !== null) {
    stop();
    render();
    return;
  }
  if (steps.length === frames.length) restart();
  timer = setInterval(advance, 1150);
  advance();
}

function makeReading({ key, label, unit }, reading) {
  const row = document.createElement("div");
  const baseline = profile[key];
  const value = reading?.value ?? null;
  const scale = Math.max(1, (baseline?.upper ?? 0) * 1.25, value ?? 0);
  const percent = (number) => `${(number / scale) * 100}%`;
  row.className = "reading";
  row.dataset.above = String(reading?.above ?? false);
  row.innerHTML = `
    <div class="reading-head"><span>${label}</span><b>${value === null ? "—" : value + unit}</b></div>
    <div class="meter" aria-hidden="true">
      ${baseline ? `<span class="band" style="width:${percent(baseline.upper)}"></span>
        <span class="marker" style="left:${percent(baseline.typical)}"></span>` : ""}
      ${value !== null ? `<span class="pin" style="left:${percent(value)}"></span>` : ""}
    </div>
    <div class="reading-foot">
      <span>${baseline ? `평소 ${baseline.typical}${unit} · 상한 ${baseline.upper}${unit}` : "평소 기록 부족"}</span>
      <span class="streak">${reading?.above ? `상한 초과 ${reading.streak}구간` : ""}</span>
    </div>`;
  return row;
}

function renderOutcome(result) {
  const evidence = element("evidence");
  const detection = result?.detection;
  let title = "재생을 시작해 보세요";
  let detail = "주행 기록이 들어오면 평소 범위와 비교해요.";
  let reviewState = "재생 대기";

  if (result) {
    reviewState = result.parked ? "P · 주차 확인" : "주행 기록 재생 중";
    if (detection) {
      title = "평소와 다른 흐름을 기록했어요";
      detail = result.parked
        ? "주차 상태가 확인됐어요. 감지 시점의 비교값을 불러왔어요."
        : "연속 초과를 감지했어요. 운전자용 결과는 주차 후에 열 수 있어요.";
    } else if (result.parked) {
      title = "이번 주행 재생이 끝났어요";
      detail = "평소 범위를 벗어난 신호가 연속으로 확인되지 않았어요.";
    } else {
      [title, detail] = statusCopy[result.observation];
    }
  }

  outcome.dataset.tone = detection ? "change" : "idle";
  element("outcome-title").textContent = title;
  element("outcome-detail").textContent = detail;
  element("review-state").textContent = reviewState;
  evidence.hidden = !detection;
  evidence.textContent = detection
    ? `${formatClock(detection.minute)} 감지 · ${detection.evidence.map(({ key, value, baseline }) => {
      const { label, unit } = metrics.find((metric) => metric.key === key);
      return `${label} ${value}${unit} / 평소 상한 ${baseline.upper}${unit}`;
    }).join(" · ")}` : "";
}

function render() {
  const result = steps.at(-1);
  const frame = frames[steps.length - 1];
  const detectedStep = result?.detection
    ? steps.find((step, index) => frames[index].minute === result.detection.minute)
    : null;
  const displayed = result?.parked ? detectedStep ?? steps.at(-2) : result;
  const atEnd = steps.length === frames.length;
  element("trip-title").textContent = scene.situation;
  element("road-context").textContent = contexts[scene.context];
  element("time").textContent = frame ? formatClock(frame.minute) : "—";
  element("gear").textContent = frame?.gear ?? "—";
  element("speed").textContent = frame?.speedKph ?? "—";
  element("sample-count").textContent = result?.parked
    ? (detectedStep ? "감지 시점 기록" : "마지막 주행 기록")
    : `평소 표본 ${history.filter(({ context }) => context === scene.context).length}구간`;
  element("play").textContent = timer !== null ? "Ⅱ 일시정지" : (atEnd ? "↻ 다시 재생" : "▶ 재생");
  element("step").disabled = atEnd;
  element("reset").disabled = !steps.length;
  for (const button of sceneNav.children) {
    button.setAttribute("aria-pressed", String(button.dataset.scene === scene.id));
  }
  for (const button of variantButtons) {
    button.setAttribute("aria-pressed", String(button.dataset.variant === variant));
  }

  const stageText = {
    idle: "정차", typical: "비교 범위 안", insufficient: "비교 자료 부족",
    watching: "상한 초과 1구간", change: "연속 상한 초과", parked: "도착 후 주차",
  };
  element("trace").replaceChildren(...frames.map((sample, index) => {
    const cell = document.createElement("span");
    const step = steps[index];
    const state = step?.parked ? "parked" : step?.observation;
    cell.className = state ?? "";
    cell.textContent = sample.gear === "P" ? "P" : String(index + 1).padStart(2, "0");
    cell.title = stageText[state] ?? "재생 대기";
    cell.setAttribute("aria-label", `${cell.textContent} · ${cell.title}`);
    if (index === steps.length - 1) cell.setAttribute("aria-current", "step");
    return cell;
  }));
  readingList.replaceChildren(...metrics.map((metric, index) =>
    makeReading(metric, displayed?.readings[index])));
  renderOutcome(result);
}

scenes.forEach((candidate, index) => {
  const button = document.createElement("button");
  button.className = "scene";
  button.type = "button";
  button.dataset.scene = candidate.id;
  button.innerHTML = `<span>0${index + 1}</span>${candidate.title}`;
  button.addEventListener("click", () => {
    if (scene === candidate) return;
    scene = candidate;
    restart();
  });
  sceneNav.append(button);
});
for (const button of variantButtons) {
  button.addEventListener("click", () => {
    if (variant === button.dataset.variant) return;
    variant = button.dataset.variant;
    restart();
  });
}
element("play").addEventListener("click", toggleReplay);
element("step").addEventListener("click", () => { stop(); advance(); });
element("reset").addEventListener("click", restart);
element("rule").textContent = `같은 신호가 ${windowMinutes}분 구간 ${requiredWindows}개에서 연속으로 상한을 넘으면 변화로 기록해요.`;
restart();
