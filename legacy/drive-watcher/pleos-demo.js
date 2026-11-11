/* Replay a documented channel mapping with a synthetic drive, not a live SDK binding. */
(() => {
  const course = DriveWatcherCourse;
  const get = (id) => document.getElementById(id);
  const button = get("sdk-play");
  const svg = "http://www.w3.org/2000/svg";
  const sampleSeconds = .1;
  const samplesPerWindow = course.secondsPerWindow / sampleSeconds;
  const totalSamples = 3 * samplesPerWindow;
  const ticksPerSample = Math.round(sampleSeconds / course.tick);
  const graphX = (time) => 34 + time / 24 * 368;
  const baseline = DriveWatcher.baselineFor(course.history, course.context, course.margins).hardBrakes;

  let vehicle;
  let session;
  let sampleIndex;
  let last;
  let recent;
  let plot;
  let brakeZone;
  let brakeCount;
  let brakeArmed;
  let timer;

  function reset() {
    vehicle = course.createVehicle();
    session = DriveWatcher.createSession(course.history, {
      interval: course.secondsPerWindow / 60, margins: course.margins,
    });
    sampleIndex = 0;
    last = null;
    recent = [];
    plot = [];
    brakeZone = null;
    brakeCount = 0;
    brakeArmed = true;
    for (const id of ["sdk-windows", "sdk-log", "sdk-brake-zones"]) get(id).replaceChildren();
    get("sdk-speed-line").removeAttribute("points");
    get("sdk-verdict").textContent = "입력 대기";
    get("sdk-evidence").textContent = "재생하면 8초 구간을 모아 비교해요.";
  }

  function drawLog() {
    get("sdk-log").replaceChildren(...recent.slice(-6).reverse().map(({ time, label, value }) => {
      const item = document.createElement("li");
      for (const [tag, text] of [["span", `+${time.toFixed(2)} s`], ["span", label], ["b", value]]) {
        const cell = document.createElement(tag);
        cell.textContent = text;
        item.append(cell);
      }
      return item;
    }));
  }

  function appendWindow(time, speedKph) {
    const result = session.ingest({
      minute: time / 60, context: course.context, speedKph, gear: "D",
      values: { hardBrakes: brakeCount },
    });
    const window = document.createElement("li");
    window.textContent = `${time - course.secondsPerWindow}–${time}초 · ${brakeCount}회`;
    window.dataset.above = String(result.readings[0].above);
    get("sdk-windows").append(window);
    if (result.detection) {
      const end = Math.round(result.detection.minute * 60);
      const start = end - DriveWatcher.requiredWindows * course.secondsPerWindow;
      get("sdk-verdict").textContent = "평소와 달라요";
      get("sdk-evidence").textContent = `${start}–${end}초에 두 구간 연속 예시 기준 ${baseline.upper}회를 넘었어요.`;
    } else {
      get("sdk-verdict").textContent = result.observation === "watching" ? "다음 구간도 확인 중" : "연속 초과 없음";
      get("sdk-evidence").textContent = `완료 구간의 강한 제동 ${brakeCount}회 · 예시 기준 ${baseline.upper}회`;
    }
    brakeCount = 0;
  }

  function nextSample() {
    let state = vehicle.snapshot();
    for (let i = 0; i < ticksPerSample; i++) {
      state = vehicle.step(course.automaticPedals(state)).state;
    }
    sampleIndex++;
    const time = sampleIndex / 10;
    const speed = state.speed;
    const angle = Math.sin(time * 1.1) * 3.4 + Math.sin(time * .4) * 1.7;
    const pressed = state.brake > .16;
    const deceleration = last ? (last.speed - speed) / sampleSeconds : null;
    if (pressed && brakeArmed && deceleration !== null && deceleration >= course.hardBrakeThreshold) {
      brakeCount++;
      brakeArmed = false;
    }
    if (!pressed) brakeArmed = true;

    const speedKph = speed * 3.6;
    const speedText = `${speed.toFixed(2)} m/s`;
    const angleText = `${angle.toFixed(1)} °`;
    const brakeText = pressed ? "눌림" : "해제";
    if (!last || last.pressed !== pressed) {
      recent.push({ time, label: "브레이크 상태", value: brakeText });
    }
    recent.push({ time, label: "차량 속도", value: speedText }, { time, label: "조향각", value: angleText });
    recent = recent.slice(-8);
    get("sdk-speed").textContent = speedText;
    get("sdk-angle").textContent = angleText;
    get("sdk-brake").textContent = brakeText;
    get("sdk-brake").dataset.active = String(pressed);
    get("sdk-kph").textContent = `${speedKph.toFixed(1)} km/h`;
    get("sdk-deceleration").textContent = deceleration === null ? "—" : `${deceleration.toFixed(2)} m/s²`;
    get("sdk-count").textContent = `${brakeCount}회`;

    plot.push(`${graphX(time).toFixed(1)},${(105 - speedKph * 1.5).toFixed(1)}`);
    if (pressed && !last?.pressed) {
      const rect = document.createElementNS(svg, "rect");
      rect.setAttribute("x", graphX(time));
      rect.setAttribute("y", "15");
      rect.setAttribute("height", "90");
      rect.setAttribute("width", "0");
      get("sdk-brake-zones").append(rect);
      brakeZone = { rect, start: time };
    }
    if (brakeZone) {
      brakeZone.rect.setAttribute("width", graphX(time) - graphX(brakeZone.start));
      if (!pressed) brakeZone = null;
    }
    last = { speed, pressed };
    if (sampleIndex % samplesPerWindow === 0) appendWindow(time, speedKph);
  }

  function pause() {
    if (timer === undefined) return;
    clearInterval(timer);
    timer = undefined;
    button.textContent = sampleIndex >= totalSamples ? "↻ 예시 다시 재생" : "▶ 계속 재생";
  }

  button.addEventListener("click", () => {
    if (timer !== undefined) { pause(); return; }
    if (sampleIndex >= totalSamples) reset();
    button.textContent = "일시정지";
    timer = setInterval(() => {
      nextSample();
      nextSample();
      get("sdk-speed-line").setAttribute("points", plot.join(" "));
      get("sdk-clock").textContent = `${(sampleIndex / 10).toFixed(1)}초 · 속도 표본 ${sampleIndex}개 · 2배속`;
      drawLog();
      if (sampleIndex >= totalSamples) pause();
    }, 100);
  });
  for (const id of ["view-course", "view-can"]) get(id).addEventListener("click", pause);
  document.addEventListener("visibilitychange", () => { if (document.hidden) pause(); });
  reset();
})();
