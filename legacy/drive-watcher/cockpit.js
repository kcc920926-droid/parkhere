(() => {
  const course = DriveWatcherCourse;
  const get = (id) => document.getElementById(id);
  const canvas = get("road");
  const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
  const speedLabel = get("course-speed");
  const gapLabel = get("course-gap");
  const laneLabel = get("course-lane");
  const laneCheck = get("lane-check");
  const laneMarker = get("lane-gauge-marker");
  const lanePosition = get("lane-position");
  const laneStatus = get("lane-status");
  const clockLabel = get("course-clock");
  const throttleMeter = get("throttle");
  const brakeMeter = get("brake");
  const autoButton = get("course-auto");
  const manualButton = get("course-manual");
  const pauseButton = get("course-pause");
  const resetButton = get("course-reset");
  const soundButton = get("course-sound");
  const sound = createDriveWatcherSound();
  const resultCard = get("course-result");
  const baseline = DriveWatcher.baselineFor(course.history, course.context, course.margins).hardBrakes;
  get("course-baseline").textContent = `${baseline.upper}회`;
  get("course-duration").textContent = `${course.duration}초 · 가상 코스`;

  const labels = Array.from({ length: course.windowCount + 1 }, (_, index) => {
    const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    const seconds = index * course.secondsPerWindow;
    label.setAttribute("x", plotPoint(seconds, 0)[0]);
    label.setAttribute("y", 138);
    label.textContent = `${seconds}초`;
    return label;
  });
  get("course-time-labels").append(...labels);
  get("course-window-grid").setAttribute("d", labels.slice(1, -1).map((label) =>
    `M${label.getAttribute("x")} 14V118`).join(" "));

  const windowCards = Array.from({ length: course.windowCount }, (_, index) => {
    const card = document.createElement("div");
    const start = index * course.secondsPerWindow;
    card.innerHTML = `<span>${start}–${start + course.secondsPerWindow}초</span><b>—</b>`;
    get("course-windows").append(card);
    return { card, count: card.querySelector("b") };
  });

  /* Live habit chips: the same six habits the tendency model reads. */
  const habits = DriveWatcherHabits;
  const habitChips = Object.fromEntries(habits.traits.map((trait) => {
    const chip = document.createElement("div");
    chip.innerHTML = "<span></span><b>—</b>";
    chip.querySelector("span").textContent = trait.label;
    chip.title = trait.label;
    get("habit-chips").append(chip);
    return [trait.key, { chip, value: chip.querySelector("b"), trait }];
  }));

  /* "평소 5회": five smooth scripted drives on this course, drawn behind today's line. */
  get("course-ghosts").append(...habits.usualDrives.map((params) => {
    const points = [];
    let ticks = 0;
    habits.drive(habits.createPolicy(params), {
      onStep: ({ state: sample }) => {
        if (ticks++ % 6 === 0) points.push(plotPoint(sample.seconds, sample.speed).map((value) => value.toFixed(1)).join(","));
      },
    });
    const line = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
    line.setAttribute("class", "plot-ghost");
    line.setAttribute("points", points.join(" "));
    return line;
  }));

  const throttleKeys = ["KeyW", "ArrowUp"];
  const brakeKeys = ["KeyS", "ArrowDown"];
  const leftKeys = ["KeyA", "ArrowLeft"];
  const rightKeys = ["KeyD", "ArrowRight"];
  const drivingKeys = new Set([...throttleKeys, ...brakeKeys, ...leftKeys, ...rightKeys]);
  const heldKeys = new Set();
  let vehicle;
  let engine;
  let state;
  let readings;
  let tripWindows;
  let tripEvents;
  let habitMeter;
  let announced;
  let detectedAt = null;
  let slowUntil = 0;
  let paintCount = 0;
  let trace;
  let lastSample;
  let mode = null;
  let animation = null;
  let previousTime = null;
  let remaining = 0;
  let running = false;
  let soundEnabled = true;

  /* Decision path: each node is one real step of the rule-based judgment. */
  const pathNode = (id) => get(`dp-${id}`);
  const pathEdge = (id) => get(`dp-e-${id}`);
  const verdictLabels = { idle: "대기", insufficient: "비교 불가", typical: "평소", watching: "관찰", change: "변화" };

  function fireEdges(...ids) {
    if (reducedMotion.matches) return;
    for (const id of ids) {
      const edge = pathEdge(id);
      edge.classList.remove("is-firing");
      void edge.getBBox();
      edge.classList.add("is-firing");
      setTimeout(() => edge.classList.remove("is-firing"), 1600);
    }
  }

  function glow(id, strength) {
    const node = pathNode(id);
    node.style.setProperty("--glow", Math.max(0, Math.min(1, strength)).toFixed(2));
    node.classList.toggle("is-live", strength > 0.15);
    pathEdge(id).classList.toggle("is-live", strength > 0.15);
  }

  function setNode(id, value, tone) {
    const node = pathNode(id);
    node.classList.toggle("is-odd", tone === "odd");
    node.classList.toggle("is-warn", tone === "warn");
    node.classList.toggle("is-live", tone === "live");
    if (value !== undefined) get(`dp-${id}-v`).textContent = value;
  }

  function resetPath() {
    for (const id of ["speed", "brake", "lead"]) glow(id, 0);
    setNode("feature", "—");
    setNode("compare", "—");
    setNode("streak", `0/${DriveWatcher.requiredWindows}`);
    setNode("verdict", verdictLabels.idle);
  }

  /* After each 8-second window: compare with the usual line, count the streak, update the verdict. */
  function judgeOnPath(result) {
    const reading = result.readings[0];
    const streak = result.detection ? DriveWatcher.requiredWindows : Math.min(reading.streak, DriveWatcher.requiredWindows);
    setNode("compare", reading.value === null ? "제외" : `${reading.value} ${reading.above ? ">" : "≤"} ${baseline.upper}`, reading.above ? "odd" : "live");
    setNode("streak", `${streak}/${DriveWatcher.requiredWindows}`, streak ? "odd" : undefined);
    setNode("verdict", verdictLabels[result.observation], result.observation === "change" || result.observation === "watching" ? "warn" : "live");
    fireEdges(...(reading.above ? ["compare"] : []), ...(streak >= DriveWatcher.requiredWindows ? ["streak"] : []));
  }

  function plotPoint(seconds, speed) {
    return [34 + seconds / course.duration * 370, 118 - speed * 3.6 / 80 * 104];
  }

  function drawGraph() {
    const points = (field) => trace.map((sample) =>
      plotPoint(sample.seconds, sample[field]).map((value) => value.toFixed(1)).join(",")).join(" ");
    get("course-speed-line").setAttribute("points", points("speed"));
    get("course-lead-line").setAttribute("points", points("leadSpeed"));
  }

  function markBrake() {
    if (mode === "manual") tripEvents.push({ seconds: state.seconds, distance: state.distance });
    const point = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    const [x, y] = plotPoint(state.seconds, state.speed);
    point.setAttribute("cx", x);
    point.setAttribute("cy", y);
    point.setAttribute("r", "4");
    get("course-brake-points").append(point);
    setNode("feature", undefined, "odd");
    fireEdges("brake", "feature");
    setTimeout(() => { if (mode) setNode("feature", undefined); }, 900);
  }

  function paint(deceleration = 0) {
    drawDriveWatcherRoad(canvas, state, deceleration, reducedMotion.matches);
    speedLabel.textContent = Math.round(state.speed * 3.6);
    gapLabel.textContent = state.gap === null ? "없음" : `${Math.round(state.gap)} m`;
    laneLabel.textContent = `${state.lane + 2} / 3`;
    const offsetCm = Math.round(Math.abs(state.laneOffset) * 100);
    lanePosition.textContent = offsetCm < 5 ? "차로 중앙"
      : `중앙에서 ${state.laneOffset < 0 ? "왼쪽" : "오른쪽"} ${offsetCm}cm`;
    laneStatus.textContent = state.onMarking ? "차선 걸침" : "차로 안";
    laneCheck.dataset.crossing = String(state.onMarking);
    laneMarker.style.left = `${50 + state.laneOffset / course.laneWidth * 100}%`;
    clockLabel.textContent = `${state.seconds.toFixed(1)} / ${course.duration}초`;
    throttleMeter.value = state.throttle;
    brakeMeter.value = state.brake;
    get("speed-arc").setAttribute("stroke-dasharray", `${Math.min(100, state.speed * 3.6 / 80 * 100).toFixed(1)} 100`);
    if (mode) {
      glow("speed", state.speed / 20);
      glow("brake", state.brake);
      glow("lead", state.leadSpeed < 11.9 ? 1 : 0);
      get("dp-feature-v").textContent = `${state.hardBrakes}회`;
    }
    const current = windowCards[readings.length];
    if (mode && current) {
      current.count.textContent = `${state.hardBrakes}회`;
    }
  }

  function renderResult() {
    const latest = readings.at(-1);
    let title = "주행을 시작해 보세요";
    let detail = "두 모드에서 같은 코스와 개인 기준을 사용해요.";
    let tone = "idle";

    if (latest?.detection) {
      title = "평소와 달라요";
      const end = readings.findIndex((entry) => entry.detection) + 1;
      const start = end - DriveWatcher.requiredWindows;
      const counts = readings.slice(start, end).map((entry) => `${entry.readings[0].value}회`).join(" → ");
      detail = `${start * course.secondsPerWindow}–${end * course.secondsPerWindow}초에 두 구간 연속 평소 상한 ${baseline.upper}회를 넘었어요. 제동 ${counts}`;
      tone = "change";
    } else if (state.finished) {
      title = "주행 기록을 확인했어요";
      detail = "평소 상한을 두 구간 연속으로 넘은 기록이 없어요.";
    } else if (latest?.readings[0].streak === 1) {
      title = "다음 구간도 볼게요";
      detail = "이번 구간에서 평소보다 강한 제동이 많았어요.";
      tone = "watching";
    } else if (mode) {
      title = "평소 기록과 비교 중";
      detail = "8초 구간이 끝날 때마다 강한 제동 횟수를 비교해요.";
    }
    resultCard.dataset.tone = tone;
    const streak = latest?.detection ? DriveWatcher.requiredWindows : Math.min(latest?.readings[0].streak ?? 0, DriveWatcher.requiredWindows);
    get("streak-arc").setAttribute("stroke-dasharray", `${streak / DriveWatcher.requiredWindows * 100} 100`);
    get("streak-text").textContent = `${streak}/${DriveWatcher.requiredWindows}`;
    get("course-status").textContent = title;
    get("course-detail").textContent = detail;
  }

  function renderControls() {
    pauseButton.hidden = !mode || state.finished;
    pauseButton.textContent = running ? "일시정지" : "계속하기";
    resetButton.hidden = !mode;
    get("trip-handoff").hidden = mode !== "manual" || !tripWindows.length;
    autoButton.setAttribute("aria-pressed", String(mode === "auto"));
    manualButton.setAttribute("aria-pressed", String(mode === "manual"));
  }

  function syncSound() {
    sound.update(state, running && soundEnabled && !document.hidden);
  }

  function startSound() {
    if (soundEnabled) sound.start().then(syncSound).catch(() => {});
  }

  function pause() {
    running = false;
    syncSound();
    heldKeys.clear();
    if (animation !== null) cancelAnimationFrame(animation);
    animation = null;
    previousTime = null;
  }

  function reset() {
    pause();
    mode = null;
    vehicle = course.createVehicle();
    engine = DriveWatcher.createSession(course.history, {
      interval: course.secondsPerWindow / 60,
      margins: course.margins,
    });
    state = vehicle.snapshot();
    readings = [];
    tripWindows = [];
    tripEvents = [];
    habitMeter = habits.createMeter();
    announced = new Set();
    detectedAt = null;
    slowUntil = 0;
    get("course-handoff-status").textContent = "";
    get("course-tendency").hidden = true;
    get("detect-banner").hidden = true;
    get("speed-badge").hidden = true;
    get("road-toasts").replaceChildren();
    resetPath();
    get("course-detect-zone").replaceChildren();
    get("drive-course").querySelector(".windshield").classList.remove("is-detected");
    for (const { chip, value } of Object.values(habitChips)) {
      chip.classList.remove("is-fresh");
      value.textContent = "—";
    }
    trace = [state];
    lastSample = 0;
    remaining = 0;
    get("course-brake-points").replaceChildren();
    for (const { card, count } of windowCards) {
      card.removeAttribute("data-state");
      count.textContent = "—";
    }
    paint();
    drawGraph();
    renderResult();
    renderControls();
  }

  /* Auto replay slows down for the detection, then skips through the rest at 2.5×. Manual driving keeps real time. */
  function timeScale(now) {
    if (mode !== "auto" || detectedAt === null || reducedMotion.matches) return 1;
    return now < slowUntil ? 0.3 : 2.5;
  }

  function onDetect(detection, now) {
    detectedAt = now;
    slowUntil = now + 1600;
    const end = detection.minute * 60;
    const start = end - DriveWatcher.requiredWindows * course.secondsPerWindow;
    const zone = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    const [x1] = plotPoint(start, 0);
    const [x2] = plotPoint(end, 0);
    zone.setAttribute("x", x1);
    zone.setAttribute("y", 14);
    zone.setAttribute("width", x2 - x1);
    zone.setAttribute("height", 104);
    get("course-detect-zone").replaceChildren(zone);
    get("detect-banner-detail").textContent =
      `${start}–${end}초 · 강한 제동이 ${DriveWatcher.requiredWindows}구간 연속 평소 5회의 상한 ${baseline.upper}회를 넘었어요`;
    get("detect-banner").hidden = false;
    const windshield = get("drive-course").querySelector(".windshield");
    windshield.classList.remove("is-detected");
    void windshield.offsetWidth;
    windshield.classList.add("is-detected");
    resultCard.classList.remove("is-pop");
    void resultCard.offsetWidth;
    resultCard.classList.add("is-pop");
    setTimeout(() => { if (detectedAt === now) get("detect-banner").hidden = true; }, 3600);
  }

  function toast(text, tone) {
    const item = document.createElement("span");
    item.textContent = text;
    if (tone) item.dataset.tone = tone;
    get("road-toasts").append(item);
    setTimeout(() => item.remove(), 2400);
  }

  /* Announce each lead-car slowdown and my reaction once, and refresh the habit chips a few times a second. */
  function updateHabits() {
    for (const [index, event] of habitMeter.timeline().entries()) {
      if (state.seconds >= event.start && !announced.has(`lead-${index}`)) {
        announced.add(`lead-${index}`);
        toast("앞차 감속 시작", "lead");
      }
      if (event.sameLane && event.onset !== null && !announced.has(`react-${index}`)) {
        announced.add(`react-${index}`);
        toast(`반응 ${event.onset.toFixed(2)}초`);
        habitChips.reactionSec.chip.classList.add("is-fresh");
        setTimeout(() => habitChips.reactionSec.chip.classList.remove("is-fresh"), 900);
      }
      if (event.sameLane && event.onset === null && state.seconds - event.start > habits.reactionLimit &&
          !announced.has(`react-${index}`)) {
        announced.add(`react-${index}`);
        toast(`${habits.reactionLimit}초 안에 제동 없음`, "late");
      }
      if (event.sameLane === false && !announced.has(`lane-${index}`)) {
        announced.add(`lane-${index}`);
        toast("다른 차로 · 이번 반응은 재지 않아요", "late");
      }
    }
    if (paintCount++ % 6 !== 0 && !state.finished) return;
    const { values } = habitMeter.result();
    for (const { value, trait } of Object.values(habitChips)) {
      const reading = values[trait.key];
      value.textContent = reading === null ? "—" : `${reading.toFixed(trait.digits)}${trait.unit}`;
    }
  }

  function animate(now) {
    const scale = timeScale(now);
    if (previousTime !== null) remaining += Math.min(.1, (now - previousTime) / 1000) * scale;
    previousTime = now;
    const badge = scale === 1 ? "" : scale < 1 ? "감지 순간 · 슬로모션" : `▶▶ ${scale}배 · 남은 구간`;
    get("speed-badge").hidden = !badge;
    get("speed-badge").textContent = badge;
    let deceleration = 0;
    let graphChanged = false;
    const isHeld = (keys) => keys.some((key) => heldKeys.has(key));

    while (remaining >= course.tick && !state.finished) {
      remaining -= course.tick;
      const pedals = mode === "auto" ? course.automaticPedals(state) : {
        throttle: isHeld(throttleKeys),
        brake: isHeld(brakeKeys),
        steer: Number(isHeld(rightKeys)) - Number(isHeld(leftKeys)),
      };
      const next = vehicle.step(pedals);
      state = next.state;
      habitMeter.add({ ...state, brake: pedals.brake, throttle: pedals.throttle });
      deceleration = next.deceleration;
      if (next.hardBrake) markBrake();
      if (state.seconds - lastSample >= .1 || next.frame || next.hardBrake) {
        trace.push(state);
        lastSample = state.seconds;
        graphChanged = true;
      }
      if (next.frame) {
        const result = engine.ingest(next.frame);
        if (mode === "manual") {
          tripWindows.push({
            distance: state.distance,
            speedKph: next.frame.speedKph,
            driven: next.frame.driven,
            hardBrakes: next.frame.values.hardBrakes,
          });
          get("trip-handoff").hidden = false;
          get("course-handoff-status").textContent = `${tripWindows.length}개 구간을 마쳤어요.`;
        }
        const { card, count } = windowCards[readings.length];
        const value = result.readings[0].value;
        count.textContent = value === null ? "—" : `${value}회`;
        card.dataset.state = result.observation;
        readings.push(result);
        renderResult();
        judgeOnPath(result);
        if (result.detection && detectedAt === null) onDetect(result.detection, now);
      }
    }

    paint(deceleration);
    if (mode) updateHabits();
    syncSound();
    if (graphChanged) drawGraph();
    if (state.finished) {
      if (mode === "manual" && DriveWatcherTrips.save(tripWindows, tripEvents)) {
        get("course-handoff-status").textContent = "이번 직접 운전 기록을 저장했어요.";
      }
      if (mode === "manual") {
        const habits = habitMeter.result();
        const saved = DriveWatcherHabits.save(habits);
        get("course-tendency").hidden = !saved;
        get("course-tendency-status").textContent = !saved ? ""
          : habits.vector ? "습관 6개를 쟀어요. 개인 경향 모델에서 판단을 받아 보세요."
            : "앞차가 감속할 때 같은 차로(가운데)에 있어야 반응 시간을 잴 수 있어요. 다시 달려 보세요.";
      }
      pause();
      renderControls();
    } else {
      animation = requestAnimationFrame(animate);
    }
  }

  function resume() {
    running = true;
    startSound();
    renderControls();
    animation = requestAnimationFrame(animate);
  }

  function start(nextMode) {
    reset();
    mode = nextMode;
    renderResult();
    resume();
  }

  for (const id of ["view-can", "view-pleos"]) {
    get(id).addEventListener("click", () => {
      if (!running) return;
      pause();
      renderControls();
    });
  }
  autoButton.addEventListener("click", () => start("auto"));
  manualButton.addEventListener("click", () => start("manual"));
  resetButton.addEventListener("click", reset);
  get("course-handoff").addEventListener("click", () => {
    if (mode !== "manual" || !tripWindows.length) return;
    if (DriveWatcherTrips.save(tripWindows, tripEvents)) {
      window.location.href = "console.html";
    } else {
      get("course-handoff-status").textContent = "이 브라우저에 기록을 저장할 수 없어요.";
    }
  });
  soundButton.addEventListener("click", () => {
    soundEnabled = !soundEnabled;
    soundButton.setAttribute("aria-pressed", String(soundEnabled));
    soundButton.textContent = soundEnabled ? "엔진음 켜짐" : "엔진음 꺼짐";
    if (running) startSound();
    syncSound();
  });
  pauseButton.addEventListener("click", () => {
    if (running) {
      pause();
      renderControls();
    } else {
      resume();
    }
  });

  window.addEventListener("keydown", (event) => {
    if (!running || mode !== "manual" || !drivingKeys.has(event.code) ||
      event.metaKey || event.ctrlKey || event.altKey) return;
    event.preventDefault();
    if (!event.repeat) heldKeys.add(event.code);
  });
  window.addEventListener("keyup", (event) => heldKeys.delete(event.code));

  function pauseManual() {
    if (running && mode === "manual") {
      pause();
      renderControls();
    }
  }

  window.addEventListener("blur", pauseManual);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) pauseManual();
    syncSound();
  });
  reducedMotion.addEventListener("change", () => paint());
  reset();
})();
