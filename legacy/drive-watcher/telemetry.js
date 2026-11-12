(() => {
  const source = DriveWatcherCAN;
  const get = (id) => document.getElementById(id);
  const courseButton = get("view-course");
  const canButton = get("view-can");
  const pleosButton = get("view-pleos");
  const loadButton = get("can-load");
  const playButton = get("can-play");
  const clock = get("can-clock");
  const fetchStatus = get("can-fetch-status");
  const sourceStatus = get("can-source-status");
  const verdict = get("can-verdict");
  const evidence = get("can-evidence");
  const playbackRate = 4;
  const referenceSeconds = source.baselineWindows * source.windowSeconds;
  const graphX = (seconds) => 40 + seconds * 675 / 60;
  const receipts = new Map(source.requests.map((request) => [request.url, request]));
  let data;
  let engine;
  let baseline;
  let elapsed = 0;
  let sampleIndex = 0;
  let windowIndex = source.baselineWindows;
  let latest;
  let animation = null;
  let lastPaint = -Infinity;
  const number = (value, digits, unit) => value === null ? "—" : `${value.toFixed(digits)} ${unit}`;

  function drawReceipts() {
    const entries = [...receipts.values()];
    const received = entries.filter(({ state }) => state === "수신").length;
    get("can-receipt-progress").textContent = `HTTP ${received} / ${entries.length} 수신`;
    get("can-receipts").replaceChildren(...entries.map(({ channel, column, url, state, status, bytes, milliseconds }) => {
      const row = document.createElement("li");
      const link = document.createElement("a");
      link.href = url;
      link.target = "_blank";
      link.rel = "noopener";
      link.textContent = `${channel}/${column}`;
      const result = document.createElement("span");
      result.textContent = bytes === undefined ? [status && `HTTP ${status}`, state ?? "요청 대기"].filter(Boolean).join(" · ")
        : `HTTP ${status} · ${bytes.toLocaleString("ko-KR")} B · ${milliseconds} ms`;
      row.dataset.received = String(bytes !== undefined);
      row.append(link, result);
      return row;
    }));
  }

  drawReceipts();

  function setView(view) {
    const can = view === "can";
    const pleos = view === "pleos";
    for (const [name, button] of [["course", courseButton], ["can", canButton], ["pleos", pleosButton]]) {
      get(`drive-${name}`).hidden = view !== name;
      button.setAttribute("aria-pressed", String(view === name));
    }
    get("drive-source-badge").lastChild.textContent = can ? " 공개 실측 기록" : pleos ? " SDK 예시 신호" : " 시뮬레이션";
    get("drive-mode-label").textContent = can ? "DRIVE WATCHER / SIGNAL REPLAY"
      : pleos ? "DRIVE WATCHER / PLEOS SDK DEMO" : "DRIVE WATCHER / SIMULATOR";
    get("drive-intro").textContent = can
      ? "도로에서 기록된 값이 드라이브 와쳐의 판정으로 이어지는 과정을 확인해 보세요."
      : pleos ? "차량 앱에서 속도와 조향각, 브레이크 신호가 들어오는 흐름을 재현했어요."
        : "차량이 오가는 세 차로를 달리면서 내 차의 위치와 제동 기록을 확인해 보세요.";
    if (!can) pause();
  }

  function drawTraces(samples) {
    const stride = Math.max(1, Math.floor(samples.length / 470));
    const graphSamples = samples.filter((_, index) => index % stride === 0);
    if (graphSamples.at(-1) !== samples.at(-1)) graphSamples.push(samples.at(-1));
    const speed = graphSamples.map(({ seconds, speedMs }) =>
      `${graphX(seconds).toFixed(2)},${(76 - speedMs * 3.6 * .8).toFixed(2)}`).join(" ");
    const angle = graphSamples.filter((sample) => sample.angleDeg !== null).map(({ seconds, angleDeg }) =>
      `${graphX(seconds).toFixed(2)},${(113.5 - angleDeg * 4.1).toFixed(2)}`).join(" ");
    for (const id of ["can-speed-full", "can-speed-line"]) get(id).setAttribute("points", speed);
    for (const id of ["can-angle-full", "can-angle-line"]) get(id).setAttribute("points", angle);
    get("can-playhead").hidden = false;
  }

  function renderVerdict() {
    let label;
    let detail;
    if (latest?.detection) {
      label = "평소와 달라요";
      detail = `두 구간 연속 이 기록의 비교 상한 ${baseline.upper}회를 넘었어요.`;
    } else if (latest) {
      const reading = latest.readings.find((entry) => entry.key === "hardBrakes");
      const end = data.windows[windowIndex - 1].minute * 60;
      label = latest.observation === "watching" ? "다음 구간도 확인 중"
        : reading.value === null ? "비교할 값 부족" : "연속 초과 없음";
      detail = `${end - source.windowSeconds}–${end}초 · 강한 감속 ${reading.value === null ? "—" : `${reading.value}회`} / 비교 상한 ${baseline?.upper ?? "—"}회`;
    } else if (elapsed >= referenceSeconds) {
      label = "비교 기준 확보";
      detail = "5초 구간이 끝날 때마다 강한 감속 횟수를 비교해요.";
    } else {
      label = "비교 기준 읽는 중";
      const used = Math.floor(elapsed / source.windowSeconds);
      detail = `같은 기록의 앞부분 ${used} / ${source.baselineWindows}구간`;
    }
    if (verdict.textContent !== label) verdict.textContent = label;
    if (evidence.textContent !== detail) evidence.textContent = detail;
  }

  function renderSample() {
    while (sampleIndex + 1 < data.samples.length && data.samples[sampleIndex + 1].seconds <= elapsed) {
      sampleIndex++;
    }
    const sample = data.samples[sampleIndex];
    get("can-raw-speed").textContent = number(sample.speedMs, 3, "m/s");
    get("can-raw-angle").textContent = number(sample.angleDeg, 1, "°");
    get("can-kph").textContent = number(sample.speedMs * 3.6, 1, "km/h");
    get("can-decel").textContent = number(sample.deceleration, 2, "m/s²");
    get("can-sample-index").textContent = `속도 표본 ${(sampleIndex + 1).toLocaleString("ko-KR")} / ${data.samples.length.toLocaleString("ko-KR")}`;
    clock.textContent = `${elapsed.toFixed(1)} / ${data.samples.at(-1).seconds.toFixed(1)}초`;
    const playheadX = graphX(elapsed);
    get("can-playhead").setAttribute("x1", playheadX);
    get("can-playhead").setAttribute("x2", playheadX);
    get("can-elapsed-rect").setAttribute("width", playheadX - 40);
    while (windowIndex < data.windows.length && data.windows[windowIndex].minute * 60 <= elapsed) {
      latest = engine.ingest(data.windows[windowIndex++]);
    }
    renderVerdict();
  }

  function reset() {
    elapsed = 0;
    sampleIndex = 0;
    windowIndex = source.baselineWindows;
    latest = undefined;
    const history = data.windows.slice(0, source.baselineWindows);
    const margins = { hardBrakes: 0 };
    engine = DriveWatcher.createSession(history, { interval: source.windowSeconds / 60, margins });
    baseline = DriveWatcher.baselineFor(history, history[0].context, margins).hardBrakes;
    renderSample();
  }

  function pause() {
    if (animation === null) return;
    cancelAnimationFrame(animation);
    animation = null;
    playButton.textContent = elapsed >= data.samples.at(-1).seconds ? "↻ 다시 재생" : "▶ 계속 재생";
  }

  function toggleReplay() {
    if (animation !== null) {
      pause();
      return;
    }
    const end = data.samples.at(-1).seconds;
    if (elapsed >= end) reset();
    playButton.textContent = "일시정지";
    const start = performance.now() - elapsed * 1000 / playbackRate;
    lastPaint = -Infinity;
    function tick(now) {
      elapsed = Math.max(0, Math.min(end, (now - start) * playbackRate / 1000));
      if (now - lastPaint >= 66 || elapsed === end) {
        renderSample();
        lastPaint = now;
      }
      if (elapsed === end) pause();
      else animation = requestAnimationFrame(tick);
    }
    animation = requestAnimationFrame(tick);
  }

  courseButton.addEventListener("click", () => setView("course"));
  canButton.addEventListener("click", () => setView("can"));
  pleosButton.addEventListener("click", () => setView("pleos"));
  playButton.addEventListener("click", toggleReplay);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) pause();
  });
  loadButton.addEventListener("click", async () => {
    loadButton.disabled = true;
    sourceStatus.textContent = "원본 요청 중";
    fetchStatus.textContent = "속도와 조향각의 시각·값 배열 4개를 요청하고 있어요.";
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      data = await source.load(controller.signal, (request) => {
        if (controller.signal.aborted && request.state !== "실패") return;
        receipts.set(request.url, request);
        drawReceipts();
      });
      drawTraces(data.samples);
      reset();
      sourceStatus.textContent = "원본 수신 완료";
      fetchStatus.textContent = `속도 ${data.samples.length.toLocaleString("ko-KR")}개 · 조향각 ${data.angleCount.toLocaleString("ko-KR")}개 수신 · 변속 신호 미수신`;
      loadButton.hidden = true;
      playButton.hidden = false;
    } catch {
      controller.abort();
      sourceStatus.textContent = "원본 수신 실패";
      fetchStatus.textContent = "공개 원본을 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.";
      loadButton.disabled = false;
      loadButton.textContent = "다시 불러오기";
    } finally {
      clearTimeout(timeout);
    }
  });
})();
