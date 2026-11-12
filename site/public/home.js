/* DriveWatch 첫 페이지 동작. 시계 차 그리기, 첫마디 연출, 평소와 비교 스크롤, 화면 넘기기, APK 정보.
   시계 차 좌표는 120 격자로, 차량 앱 ClockCar.kt와 가족 화면 clockcar.js와 같은 값을 쓴다. */
(() => {
  const NS = "http://www.w3.org/2000/svg";
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const LABEL = DWCar.LABEL;
  const el = DWCar.el;
  const drawCar = (stage) => DWCar.draw(stage, { theme: stage.dataset.car === "end" ? "white" : "purple" });

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

  /* 1-1. 영상: 표지의 재생 버튼과 첫 화면의 "영상 보기"는 누를 때만 영상을 받는다 */
  const film = document.querySelector(".film");
  const video = document.querySelector("[data-film]");
  if (film && video) {
    const play = () => { film.classList.add("playing"); video.play().catch(() => {}); };
    document.querySelector("[data-film-play]").addEventListener("click", play);
    video.addEventListener("play", () => film.classList.add("playing"));
    document.querySelectorAll("[data-film-link]").forEach((a) => a.addEventListener("click", (e) => {
      e.preventDefault();
      film.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "center" });
      play();
    }));
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
