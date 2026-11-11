/* 01 개요 장면: 도심 시연 기록을 판정 엔진에 넣은 결과를 9초 동안 차례로 보여 준다. 누르지 않아도 재생된다. */
(() => {
  const get = (id) => document.getElementById(id);
  const stage = get("overview");
  const view = DriveWatcherLanding.summarize(DriveWatcher, DriveWatcherReplays);
  const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
  const WINDOW_MS = 1000;
  const PARK_MS = view.windows.length * WINDOW_MS;
  const CARD_MS = PARK_MS + 1000;
  const TOTAL_MS = 9000;
  const scaleTop = Math.max(view.upper, ...view.windows.map(({ value }) => value)) + 1;
  const captions = {
    typical: "평소 범위 안이에요. 화면에는 아무것도 띄우지 않아요",
    watching: "평소 범위를 넘었어요. 한 번은 지켜봐요",
    change: "두 구간 연속으로 넘었어요. 변화로 기록해요",
  };
  let timers = [];
  let frame = 0;

  get("evidence-image").src = DriveWatcherLandingEvidence;

  const plot = get("overview-plot");
  const upper = get("overview-upper");
  upper.style.setProperty("--upper", String(view.upper / scaleTop));
  upper.firstElementChild.textContent = `평소 ${view.upper}${view.unit} 이하`;
  const bars = view.windows.map(({ from, to, value, above }) => {
    const bar = document.createElement("div");
    bar.className = "bar";
    bar.style.setProperty("--h", `${value / scaleTop * 100}%`);
    bar.dataset.above = String(above);
    const fill = document.createElement("i");
    const count = document.createElement("b");
    count.textContent = `${value}${view.unit}`;
    const label = document.createElement("span");
    label.textContent = `${from}–${to}분`;
    bar.append(count, fill, label);
    plot.insertBefore(bar, upper);
    return bar;
  });
  get("overview-chart").setAttribute("aria-label",
    `${view.windowMinutes}분 구간별 ${view.label}: ${view.windows.map(({ from, to, value }) => `${from}–${to}분 ${value}${view.unit}`).join(", ")}. 평소 ${view.upper}${view.unit} 이하`);
  if (view.card) {
    get("overview-card-title").textContent = view.card.title;
    get("overview-card-evidence").textContent = view.card.evidence;
    get("overview-card-where").textContent = view.card.where;
  }

  function showWindow(index) {
    const bar = bars[index];
    bar.classList.add("is-shown");
    bar.classList.toggle("is-above", bar.dataset.above === "true");
    get("overview-caption").textContent = captions[view.windows[index].observation] ?? captions.typical;
  }
  function park() {
    stage.dataset.phase = "parked";
    get("overview-gear").textContent = "P";
    get("overview-caption").textContent = "주차했어요";
  }
  function showCard() {
    stage.dataset.phase = "card";
    get("overview-caption").textContent = "주차한 뒤에 어느 구간이 달랐는지 보여 줘요";
  }
  function stop() {
    timers.forEach(clearTimeout);
    timers = [];
    cancelAnimationFrame(frame);
  }
  function finish() {
    stop();
    bars.forEach((_, index) => showWindow(index));
    park();
    showCard();
    get("overview-progress").style.width = "100%";
  }
  function play() {
    stop();
    if (reducedMotion.matches) { finish(); return; }
    stage.dataset.phase = "driving";
    get("overview-gear").textContent = "D";
    get("overview-caption").textContent = "달리는 동안에는 조용히 기록만 해요";
    bars.forEach((bar) => bar.classList.remove("is-shown", "is-above"));
    view.windows.forEach((_, index) => timers.push(setTimeout(() => showWindow(index), (index + 1) * WINDOW_MS - 400)));
    timers.push(setTimeout(park, PARK_MS + 300), setTimeout(showCard, CARD_MS + 300));
    const started = performance.now();
    const tick = (now) => {
      const ratio = Math.min(1, (now - started) / TOTAL_MS);
      get("overview-progress").style.width = `${ratio * 100}%`;
      if (ratio < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
  }

  get("overview-replay").addEventListener("click", play);
  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) { observer.disconnect(); play(); }
    }, { threshold: .35 });
    observer.observe(stage);
  } else {
    play();
  }
})();
