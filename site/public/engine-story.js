/* 판단 엔진 페이지 동작. 실제 판정 코드(engine.js)에 합성 기록(replays.js)과 공개 실측 CAN(can-source.js)을 넣어 보여 준다. */
(() => {
  const E = DriveWatcher, R = DriveWatcherReplays, CAN = DriveWatcherCAN;
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const h = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };
  const NAME = { hardBrakes: "강한 제동", steeringCorrections: "조향 보정", minutesSinceBreak: "쉬지 않고 운전한 시간" };
  const UNIT = { hardBrakes: "회", steeringCorrections: "회", minutesSinceBreak: "분" };
  const SHOWN = { city: ["hardBrakes", "steeringCorrections"], highway: ["minutesSinceBreak"] };
  const ig = (word) => { const c = word.charCodeAt(word.length - 1) - 0xAC00; return c >= 0 && c <= 11171 && c % 28 ? "이" : "가"; };
  const fmt = (v, unit) => `${Number.isInteger(v) ? v : v.toFixed(1)}${unit}`;

  /* 머리 줄 */
  const nav = $("#nav");
  const onScroll = () => nav.classList.toggle("scrolled", scrollY > 8);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* 1. 한눈에: 화면에 들어오면 연필 선을 긋는다 */
  const glance = $(".glance");
  new IntersectionObserver(([e], io) => { if (e.isIntersecting) { glance.classList.add("in"); io.disconnect(); } }, { threshold: 0.5 }).observe(glance);

  /* 2. 같은 길, 두 번 */
  const car = DWCar.draw($("[data-car=run]"), { theme: "purple" });
  const stateEl = $("[data-run-state]"), clockEl = $("[data-run-clock]"), board = $("[data-metrics]"), summary = $("[data-run-summary]");
  let scene = "city", variant = "changed", timer = null, started = false;
  let frames, session, base, step, cells, history, park;

  const setState = (k, text) => { car(k); stateEl.dataset.k = k; stateEl.textContent = text; };
  const range = (minute) => `${minute - 2}–${minute}분`;

  function build() {
    clearInterval(timer); timer = null;
    const sc = R.scenes.find((s) => s.id === scene);
    frames = R.framesFor(sc, variant);
    session = E.createSession(R.history);
    base = E.baselineFor(R.history, sc.context);
    step = 0; history = {}; cells = {};
    board.replaceChildren();
    for (const key of SHOWN[scene]) {
      const b = base[key];
      const box = h("div", "metric");
      box.append(h("div", "metric-head", `<h3>${NAME[key]}</h3><span>평소 ${fmt(b.typical, UNIT[key])} · 상한 <b>${fmt(b.upper, UNIT[key])}</b></span>`));
      const row = h("div", "wins");
      cells[key] = frames.filter((f) => f.gear === "D").map((f) => {
        const c = h("div", "win empty", `<b>0</b><small>${range(f.minute)}</small>`);
        row.append(c);
        return c;
      });
      const p = h("div", "win park", `<b>P</b><small>주차</small>`);
      row.append(p);
      cells[key].park = p;
      history[key] = [];
      box.append(row);
      board.append(box);
    }
    setState("learning", "준비");
    clockEl.textContent = "출발 전";
    summary.className = "run-summary";
    summary.textContent = "2분 구간을 하나씩 엔진에 넣어요.";
  }

  function tick() {
    if (step >= frames.length) { clearInterval(timer); timer = null; return; }
    const frame = frames[step];
    const result = session.ingest(frame);
    $$(".win.now", board).forEach((n) => n.classList.remove("now"));
    if (frame.gear === "P") {
      for (const key of SHOWN[scene]) cells[key].park.classList.add("now");
      clockEl.textContent = "주차(P)";
      finish(result);
    } else {
      for (const key of SHOWN[scene]) {
        const reading = result.readings.find((r) => r.key === key);
        const c = cells[key][step];
        history[key].push({ ...reading, minute: frame.minute });
        c.classList.remove("empty");
        c.classList.add("now");
        c.querySelector("b").innerHTML = `${reading.value}<small>${UNIT[key]}</small>`;
        if (reading.above) c.classList.add("over");
        if (reading.streak >= E.requiredWindows) {
          for (let i = step - reading.streak + 1; i <= step; i++) cells[key][i].classList.add("confirmed");
        }
      }
      clockEl.textContent = `출발 후 ${frame.minute}분`;
      if (result.detection) setState("talk", "두 구간 연속 · 적어 둬요");
      else if (result.observation === "watching") setState("observe", "평소보다 높은 구간 · 지켜봐요");
      else setState("stable", "평소와 같아요");
    }
    step++;
  }

  function finish(result) {
    const det = result.detection;
    if (det) {
      const parts = det.evidence.filter((ev) => SHOWN[scene].includes(ev.key)).map((ev) => {
        const rows = history[ev.key];
        const end = rows.findIndex((r) => r.minute === det.minute);
        const run = rows.slice(end - ev.streak + 1, end + 1).map((r) => `${range(r.minute)} ${fmt(r.value, UNIT[ev.key])}`).join(", ");
        return `<b>${NAME[ev.key]}</b> ${run} (평소 상한 ${fmt(ev.baseline.upper, UNIT[ev.key])})`;
      });
      setState("talk", "변화로 적었어요");
      summary.className = "run-summary talk";
      summary.innerHTML = `${parts.join(" · ")}. 두 구간 이어져 <b>변화로 적었어요.</b> 주차하면 이 구간을 근거로 보여 드려요.`;
      return;
    }
    const once = SHOWN[scene].flatMap((key) => history[key].filter((r) => r.above).map((r) => ({ key, ...r })));
    setState("stable", "평소와 같았어요");
    summary.className = "run-summary calm";
    if (once.length) {
      const r = once[0];
      summary.innerHTML = `<b>${NAME[r.key]}</b>${ig(NAME[r.key])} ${range(r.minute)}에 ${fmt(r.value, UNIT[r.key])}로 한 번 높았지만 다음 구간에 평소로 돌아왔어요. <b>적지 않고 넘겨요.</b>`;
    } else {
      summary.innerHTML = `모든 구간이 평소 범위 안이었어요. <b>아무 말도 하지 않아요.</b>`;
    }
  }

  function play() {
    build();
    started = true;
    if (reduce) { while (step < frames.length) tick(); return; }
    tick();
    timer = setInterval(tick, 1000);
  }

  $$("[data-scene]").forEach((b) => b.addEventListener("click", () => {
    scene = b.dataset.scene;
    $$("[data-scene]").forEach((x) => x.setAttribute("aria-selected", String(x === b)));
    play();
  }));
  $$("[data-variant]").forEach((b) => b.addEventListener("click", () => {
    variant = b.dataset.variant;
    $$("[data-variant]").forEach((x) => x.setAttribute("aria-selected", String(x === b)));
    play();
  }));
  $("[data-replay]").addEventListener("click", play);
  build();
  new IntersectionObserver(([e]) => {
    if (e.isIntersecting && !started) play();
    if (!e.isIntersecting && timer) { clearInterval(timer); timer = null; }
    else if (e.isIntersecting && started && !timer && step < frames.length) timer = setInterval(tick, 1000);
  }, { threshold: 0.35 }).observe($(".run-panel"));

  /* 3. 기준: 지난 기록 → 가운데 값 + 여유 = 평소 상한 */
  const formula = $("[data-formula]");
  for (const [context, key] of [["city", "hardBrakes"], ["city", "steeringCorrections"], ["highway", "minutesSinceBreak"]]) {
    const past = R.history.filter((f) => f.context === context).map((f) => f.values[key]);
    const b = E.baselineFor(R.history, context)[key];
    const mid = past.indexOf(b.typical);
    const row = h("div", "f-row");
    row.append(h("h3", null, `${NAME[key]}<small>${R.contexts[context]} · 지난 ${past.length}번</small>`));
    const chips = h("div", "past");
    past.forEach((v, i) => chips.append(h("i", i === mid ? "mid" : null, fmt(v, UNIT[key]))));
    row.append(chips,
      h("span", "op", "→"), h("div", "val", `<b>${fmt(b.typical, UNIT[key])}</b><small>가운데 값</small>`),
      h("span", "op", "+"), h("div", "val", `<b>${fmt(b.upper - b.typical, UNIT[key])}</b><small>여유</small>`),
      h("span", "op", "="), h("div", "val limit", `<b>${fmt(b.upper, UNIT[key])}</b><small>평소 상한</small>`));
    formula.append(row);
  }

  /* 4. 공개 실측 CAN */
  const canBox = $("[data-can]");
  const loadBtn = $("[data-can-load]", canBox), trace = $("[data-can-trace]", canBox), canCells = $("[data-can-cells]", canBox), canResult = $("[data-can-result]", canBox);
  const NS = "http://www.w3.org/2000/svg";
  const svgEl = (tag, attrs, parent) => { const n = document.createElementNS(NS, tag); for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v); parent.append(n); return n; };

  function drawTrace(samples) {
    trace.replaceChildren();
    const end = samples.at(-1).seconds, x0 = 6, x1 = 394, yTop = 8, yBase = 70;
    const max = Math.max(...samples.map((s) => s.speedMs * 3.6)) || 1;
    const x = (s) => x0 + (s / end) * (x1 - x0), y = (kph) => yBase - (kph / max) * (yBase - yTop);
    const baseEnd = CAN.baselineWindows * CAN.windowSeconds;
    svgEl("rect", { x: x0, y: yTop - 4, width: x(baseEnd) - x0, height: yBase - yTop + 4, fill: "rgba(255,255,255,.05)", rx: 6 }, trace);
    const step = Math.max(1, Math.floor(samples.length / 400));
    const pts = samples.filter((_, i) => i % step === 0).map((s) => `${x(s.seconds).toFixed(1)},${y(s.speedMs * 3.6).toFixed(1)}`).join(" ");
    svgEl("polyline", { points: pts, fill: "none", stroke: "#9D93FF", "stroke-width": 2, "stroke-linejoin": "round" }, trace);
    svgEl("text", { x: x0 + 6, y: yTop + 10 }, trace).textContent = "기준";
    svgEl("text", { x: x0, y: 86 }, trace).textContent = "0초";
    svgEl("text", { x: x1, y: 86, "text-anchor": "end" }, trace).textContent = `${Math.round(end)}초 · 속도 최고 ${Math.round(max)}km/h`;
  }

  loadBtn.addEventListener("click", async () => {
    loadBtn.disabled = true;
    loadBtn.textContent = "원본 받는 중…";
    canResult.textContent = "속도와 조향각 배열 4개를 요청하고 있어요.";
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      const data = await CAN.load(controller.signal);
      drawTrace(data.samples);
      trace.hidden = false;
      canCells.hidden = false;
      const W = data.windows, B = CAN.baselineWindows;
      const historyWindows = W.slice(0, B);
      const s = E.createSession(historyWindows, { interval: CAN.windowSeconds / 60, margins: { hardBrakes: 0 } });
      canCells.replaceChildren();
      let detected = null, total = 0;
      const items = W.map((w, i) => {
        const li = h("li", i < B ? "base" : "wait", `${w.values.hardBrakes ?? "–"}<small>${Math.round(w.minute * 60)}초</small>`);
        canCells.append(li);
        return li;
      });
      for (let i = B; i < W.length; i++) {
        const r = s.ingest(W[i]);
        total += W[i].values.hardBrakes ?? 0;
        detected ||= r.detection;
        const li = items[i];
        const show = () => { li.className = r.readings[0].above ? "over" : "calm"; };
        if (reduce) show(); else setTimeout(show, (i - B + 1) * 350);
      }
      const say = () => {
        canResult.textContent = detected
          ? `${Math.round(detected.minute * 60)}초에 두 구간 이어진 변화를 적었어요.`
          : `비교한 30초 동안 강한 제동 ${total}회. 모든 구간이 이 주행의 앞부분과 같았어요. 아무 일 없으면 아무 말도 하지 않아요.`;
      };
      if (reduce) say(); else setTimeout(say, (W.length - B + 1) * 350);
      loadBtn.hidden = true;
      $(".fine", canBox).textContent = `속도 ${data.samples.length.toLocaleString("ko-KR")}개, 조향각 ${data.angleCount.toLocaleString("ko-KR")}개를 받았어요. 앞 5칸(25초)이 기준이고, 칸의 숫자는 5초마다 센 강한 제동(4m/s² 이상)이에요. 이 기록에는 기어 신호가 없어 속도로 주행 여부를 판단해요.`;
    } catch {
      canResult.textContent = "공개 원본을 불러오지 못했어요. 잠시 뒤 다시 눌러 주세요.";
      loadBtn.disabled = false;
      loadBtn.textContent = "다시 불러오기";
    } finally {
      clearTimeout(timeout);
    }
  });

  /* 스크롤 등장 */
  if (!reduce && "IntersectionObserver" in window) {
    const rio = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); rio.unobserve(e.target); }
    }, { rootMargin: "0px 0px -8% 0px" });
    $$(".sec-head, .formula, .real-grid, .status-cols, .lab-link").forEach((t) => { t.classList.add("reveal"); rio.observe(t); });
  }
})();
