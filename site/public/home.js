/* DriveWatch 첫 페이지 동작. 시계 차 그리기, 첫마디 연출, 평소와 비교 스크롤, 화면 넘기기, APK 정보.
   시계 차 좌표는 120 격자로, 차량 앱 ClockCar.kt와 가족 화면 clockcar.js와 같은 값을 쓴다. */
(() => {
  const NS = "http://www.w3.org/2000/svg";
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const BODY = "M20 84 Q13 84 13 76 Q13 61 28 58 L34 57 Q38 30 60 28 Q82 28 88 56 L94 57 Q107 60 107 74 Q107 84 100 84 Z";
  const WINDOW = "M40 57 Q42 38 60 37 Q78 38 80 57 Z";
  const CX = 60, CY = 55, R = 14;
  const BANDS = [["stable", 180, 54, "#02A957"], ["observe", 243, 54, "#E89A0C"], ["talk", 306, 54, "#F0441B"]];
  const NEEDLE = { learning: 270, stable: 207, observe: 270, talk: 333 };
  const MOUTH = {
    learning: "M96 75.5 Q99 77.5 102 75.5",
    stable: "M95 75 Q99 78.5 103 75",
    observe: "M96 76 Q99 76 102 76",
    talk: "M95 77 Q99 73.5 103 77",
  };
  const LABEL = { learning: "학습 중", stable: "안정", observe: "관찰", talk: "대화 권장" };
  const THEMES = {
    purple: { body: "#6B5CF5", window: "#F3F1FF", wheel: "#2A2350", ring: "#F4F1EA", hub: "#CFC8FF", sclera: "#FFFFFF", pupil: "#2A2350", mouth: "#FFFFFF", shadow: "rgba(23,22,28,.13)", ink: "#17161C" },
    white: { body: "#FFFFFF", window: "#EDEAFF", wheel: "#2A2350", ring: "#6B5CF5", hub: "#CFC8FF", sclera: "#2A2350", pupil: null, mouth: "#2A2350", shadow: "rgba(20,10,80,.25)", ink: "#17161C" },
  };

  const el = (tag, attrs = {}, parent) => {
    const node = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
    if (parent) parent.append(node);
    return node;
  };
  const at = (deg, r = R) => [CX + r * Math.cos(deg * Math.PI / 180), CY + r * Math.sin(deg * Math.PI / 180)];
  const arc = (from, sweep) => {
    const [x1, y1] = at(from), [x2, y2] = at(from + sweep);
    return `M${x1.toFixed(2)} ${y1.toFixed(2)} A${R} ${R} 0 0 1 ${x2.toFixed(2)} ${y2.toFixed(2)}`;
  };

  function drawCar(stage) {
    const t = THEMES[stage.dataset.car === "end" ? "white" : "purple"];
    const svg = el("svg", { viewBox: "2 14 116 100", "aria-hidden": "true", focusable: "false" });
    el("ellipse", { cx: 60, cy: 96.5, rx: 46, ry: 3.4, fill: t.shadow }, svg);
    const g = el("g", { class: "car-body-g" }, svg);
    el("path", { d: BODY, fill: t.body }, g);
    for (const x of [36, 84]) {
      el("circle", { cx: x, cy: 84, r: 10, fill: t.wheel, stroke: t.ring, "stroke-width": 3 }, g);
      el("circle", { cx: x, cy: 84, r: 3.4, fill: t.hub }, g);
    }
    el("path", { d: WINDOW, fill: t.window }, g);
    const dashed = el("path", { d: arc(180, 180), fill: "none", stroke: "#B9B5C4", "stroke-width": 4.5, "stroke-dasharray": "3 3.5", class: "band" }, g);
    const bands = BANDS.map(([k, from, sweep, color]) =>
      [k, el("path", { d: arc(from, sweep), fill: "none", stroke: color, "stroke-width": 4.5, class: "band" }, g)]);
    const needle = el("path", { d: `M${CX} ${CY} L${CX + 11} ${CY}`, stroke: t.ink, "stroke-width": 3, "stroke-linecap": "round", class: "needle" }, g);
    const hub = el("circle", { cx: CX, cy: CY, r: 2.6, fill: t.ink }, g);
    el("circle", { cx: 99, cy: 67, r: t.pupil ? 4.8 : 4.2, fill: t.sclera }, g);
    if (t.pupil) el("circle", { cx: 99.6, cy: 67.4, r: 2.6, fill: t.pupil }, g);
    el("circle", { cx: 100.4, cy: 66.2, r: 1, fill: "#FFFFFF" }, g);
    const brow = el("path", { d: "M94 60.5 L101.5 62", stroke: t.mouth, "stroke-width": 1.8, "stroke-linecap": "round", class: "brow", opacity: 0 }, g);
    const mouth = el("path", { d: MOUTH.stable, fill: "none", stroke: t.mouth, "stroke-width": 2.2, "stroke-linecap": "round", class: "mouth" }, g);
    stage.append(svg);

    const set = (k) => {
      const learning = k === "learning";
      dashed.style.opacity = learning ? 1 : 0;
      for (const [band, path] of bands) path.style.opacity = learning ? 0 : band === k ? 1 : 0.2;
      needle.style.transform = `rotate(${NEEDLE[k]}deg)`;
      const color = learning ? "#8D899A" : t.ink;
      needle.setAttribute("stroke", color);
      hub.setAttribute("fill", color);
      mouth.setAttribute("d", MOUTH[k]);
      brow.setAttribute("opacity", k === "talk" ? 1 : 0);
      stage.dataset.status = k;
    };
    set(stage.dataset.status || "stable");
    return set;
  }

  const cars = {};
  document.querySelectorAll("[data-car]").forEach((stage) => { cars[stage.dataset.car] = drawCar(stage); });

  /* 머리 줄 */
  const nav = document.getElementById("nav");
  const onScroll = () => nav.classList.toggle("scrolled", scrollY > 8);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* 1. 첫마디: 먼저 하던 말을 긋고, 바꾼 말을 꺼낸다 */
  const hero = document.querySelector(".hero");
  const heroStrike = hero.querySelector(".strike");
  if (reduce) {
    heroStrike.classList.add("is-drawn");
    hero.classList.add("is-on");
  } else {
    setTimeout(() => heroStrike.classList.add("is-drawn"), 650);
    setTimeout(() => hero.classList.add("is-on"), 1250);
  }

  /* 첫 화면의 시계 차: 안정 → 관찰 → 대화 권장을 천천히 돈다 */
  const legend = document.querySelectorAll("[data-legend] i");
  const showLegend = (k) => legend.forEach((i) => i.classList.toggle("on", i.dataset.k === k));
  if (cars.hero) {
    const cycle = ["stable", "observe", "talk"];
    let n = 0, timer = null;
    cars.hero("learning");
    const tick = () => { const k = cycle[n++ % cycle.length]; cars.hero(k); showLegend(k); };
    const start = () => { if (!timer) { tick(); timer = setInterval(tick, 2600); } };
    const stop = () => { clearInterval(timer); timer = null; };
    if (reduce) { cars.hero("stable"); showLegend("stable"); }
    else {
      setTimeout(() => {
        new IntersectionObserver(([e]) => (e.isIntersecting ? start() : stop())).observe(document.querySelector(".hero-car"));
      }, 900);
    }
  }

  /* 3. 평소와 비교: 예시 19주. 1~12주 학습, 14주 한 번 튐, 16주부터 나빠짐 */
  const WEEKS = [0.3, 0.2, 0.4, 0.3, 0.2, 0.3, 0.3, 0.2, 0.4, 0.3, 0.2, 0.3, 0.3, 0.9, 0.3, 0.8, 0.9, 1.0, 1.0];
  const LIMIT = 0.45; // 평소(8주 평균 약 0.29)의 1.5배
  const chart = document.querySelector("[data-chart]");
  const bars = [];
  let spikeNote;
  if (chart) {
    const x0 = 30, x1 = 394, yTop = 14, yBase = 138, max = 1.15;
    const y = (v) => yBase - (v / max) * (yBase - yTop);
    const step = (x1 - x0) / WEEKS.length, w = step * 0.66;
    el("line", { x1: x0, x2: x1, y1: yBase, y2: yBase, stroke: "#CFC9BB", "stroke-width": 1 }, chart);
    el("line", { x1: x0, x2: x1, y1: y(LIMIT), y2: y(LIMIT), stroke: "#17161C", "stroke-width": 1, "stroke-dasharray": "3 4", opacity: 0.55 }, chart);
    el("text", { x: x0, y: y(LIMIT) - 6 }, chart).textContent = "평소의 1.5배";
    WEEKS.forEach((v, i) => {
      const week = i + 1;
      const rect = el("rect", { x: x0 + i * step + (step - w) / 2, y: y(v), width: w, height: yBase - y(v), rx: 3, class: "bar" }, chart);
      let fill = "#6F6B78";
      if (week <= 12) fill = "#CBC6D3";
      if (week === 14) { fill = "none"; rect.setAttribute("stroke", "#E89A0C"); rect.setAttribute("stroke-width", 1.6); rect.setAttribute("stroke-dasharray", "3 2.5"); }
      if (week === 16 || week === 17) fill = "#E89A0C";
      if (week >= 18) fill = "#F0441B";
      rect.setAttribute("fill", fill);
      rect.style.opacity = 0;
      bars.push(rect);
    });
    spikeNote = el("text", { x: x0 + 13 * step + step / 2, y: y(0.9) - 6, "text-anchor": "middle" }, chart);
    spikeNote.textContent = "넘김";
    spikeNote.style.opacity = 0;
    for (const [i, label] of [[0, "1주"], [11, "12주"], [18, "19주"]]) {
      el("text", { x: x0 + i * step + step / 2, y: yBase + 18, "text-anchor": "middle" }, chart).textContent = label;
    }
  }
  const stateLabel = document.querySelector("[data-state-label]");
  const steps = [...document.querySelectorAll(".step")];
  const activate = (s) => {
    steps.forEach((x) => x.classList.toggle("on", x === s));
    const k = s.dataset.step, upto = Number(s.dataset.upto);
    if (cars.usual) cars.usual(k);
    if (stateLabel) { stateLabel.textContent = LABEL[k] + (s.hasAttribute("data-spike") ? " · 한 주는 넘겨요" : ""); stateLabel.dataset.k = k; }
    bars.forEach((b, i) => { b.style.opacity = i < upto ? 1 : 0; });
    if (spikeNote) spikeNote.style.opacity = upto >= 14 ? 1 : 0;
  };
  if (steps.length) {
    activate(steps[0]);
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) activate(e.target);
    }, { rootMargin: "-48% 0px -48% 0px" });
    steps.forEach((s) => io.observe(s));
  }

  /* 지우는 선: 화면에 들어오면 긋는다 */
  const strikeIO = new IntersectionObserver((entries) => {
    for (const e of entries) if (e.isIntersecting) { e.target.classList.add("is-drawn"); strikeIO.unobserve(e.target); }
  }, { threshold: 0.9 });
  document.querySelectorAll(".struck .strike").forEach((s, i) => {
    s.style.transitionDelay = reduce ? "0s" : `${i * 0.22}s`;
    strikeIO.observe(s);
  });

  /* 5. 하루: 옆으로 넘기기 */
  const reel = document.querySelector("[data-reel]");
  const prev = document.querySelector("[data-reel-prev]");
  const next = document.querySelector("[data-reel-next]");
  if (reel && prev && next) {
    const by = () => { const f = reel.querySelector(".frame"); return f ? f.getBoundingClientRect().width + 24 : 600; };
    const sync = () => {
      prev.disabled = reel.scrollLeft < 8;
      next.disabled = reel.scrollLeft + reel.clientWidth > reel.scrollWidth - 8;
    };
    prev.addEventListener("click", () => reel.scrollBy({ left: -by(), behavior: reduce ? "auto" : "smooth" }));
    next.addEventListener("click", () => reel.scrollBy({ left: by(), behavior: reduce ? "auto" : "smooth" }));
    reel.addEventListener("scroll", sync, { passive: true });
    addEventListener("resize", sync);
    sync();
  }

  /* 스크롤 등장 */
  if (!reduce && "IntersectionObserver" in window) {
    const targets = document.querySelectorAll(".sec-head, .figures, .why-gap, .ledger, .handoff, .family-notes, .ask-grid, .graduate, .pack-row, .principle, .plans, .status-cols, .end-grid");
    const rio = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("in"); rio.unobserve(e.target); }
    }, { rootMargin: "0px 0px -8% 0px" });
    targets.forEach((t) => { t.classList.add("reveal"); rio.observe(t); });
  }

  /* 차량 앱 APK: 게시된 버전과 크기 */
  fetch("/api/app/latest", { cache: "no-store" })
    .then((r) => (r.ok ? r.json() : null))
    .then((app) => {
      if (!app || !app.versionCode) return;
      const mb = app.size ? ` · ${(app.size / 1e6).toFixed(1)}MB` : "";
      document.querySelectorAll("[data-apk-meta]").forEach((n) => { n.textContent = `버전 ${app.versionCode}${mb} · Pleos 에뮬레이터용`; });
      if (app.download) document.querySelectorAll("[data-apk]").forEach((a) => { a.href = app.download; });
    })
    .catch(() => {});
})();
