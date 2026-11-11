(() => {
  const {
    traits, drivers, days, sizes, reasonGap, delayLevels, run: drive, planHistory, planEvaluation, createProfile, summarize,
  } = DriveWatcherProfile;
  const get = (id) => document.getElementById(id);
  const svgNs = "http://www.w3.org/2000/svg";
  const totalEpochs = 1500;
  const epochsPerFrame = 40;
  const layerX = [118, 240, 330, 420, 542];
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  let profile;
  let driverId = "calm";
  let dayId = location.hash === "#mine" ? "mine" : "tired";
  let mine = DriveWatcherHabits.load();
  const cache = typeof DriveWatcherProfileCache !== "undefined" ? DriveWatcherProfileCache : null;
  let current = null;
  let losses = [];
  let scanTimer = null;
  let fireTimers = [];
  let nodes = [];
  let edges = [];

  function svg(tag, attributes = {}, text) {
    const node = document.createElementNS(svgNs, tag);
    for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
    if (text !== undefined) node.textContent = text;
    return node;
  }

  const particle = (word) => {
    const code = word.charCodeAt(word.length - 1) - 0xac00;
    return code >= 0 && code % 28 ? "이" : "가";
  };
  const format = (trait, value) => `${value.toFixed(trait.digits)}${trait.unit}`;
  const odd = (reading) => Math.abs(reading.gap) >= reasonGap;
  /* Run scripted drives a few at a time so the page stays responsive and shows progress. */
  function inChunks(items, work, onProgress, done, size = 12) {
    const out = [];
    const step = () => {
      for (const item of items.slice(out.length, out.length + size)) out.push(work(item));
      onProgress(out.length, items.length);
      if (out.length < items.length) setTimeout(step, 0);
      else done(out);
    };
    setTimeout(step, 0);
  }
  const nodeY = (count, index) => 165 + (index - (count - 1) / 2) * (count > 2 ? 46 : 70);

  function buttonsFor(container, items, selected, onPick) {
    container.replaceChildren(...items.map((item) => {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = item.label;
      button.setAttribute("aria-pressed", String(item.id === selected));
      button.addEventListener("click", () => onPick(item.id));
      return button;
    }));
  }

  /* Network skeleton: edges first, nodes on top, labels on the outer layers. */
  function buildNetwork() {
    const root = get("network");
    root.replaceChildren();
    const edgeGroup = svg("g", { class: "edges" });
    const nodeGroup = svg("g");
    root.append(edgeGroup, nodeGroup);
    edges = profile.network.layers.map((layer, index) => layer.weights.map((row, to) => row.map((weight, from) => {
      const line = svg("line", {
        x1: layerX[index], y1: nodeY(sizes[index], from), x2: layerX[index + 1], y2: nodeY(sizes[index + 1], to),
      });
      edgeGroup.append(line);
      return line;
    })));
    nodes = sizes.map((count, layer) => Array.from({ length: count }, (_, index) => {
      const group = svg("g", { class: `node layer-${layer}` });
      const y = nodeY(count, index);
      const halo = svg("circle", { cx: layerX[layer], cy: y, r: 15, class: "halo" });
      const core = svg("circle", { cx: layerX[layer], cy: y, r: layer === 2 ? 11 : 9, class: "core" });
      group.append(halo, core);
      if (layer === 0 || layer === 4) {
        const trait = traits[index];
        const left = layer === 0;
        group.append(
          svg("text", { x: left ? layerX[0] - 20 : layerX[4] + 20, y: y - 3, class: "node-label", "text-anchor": left ? "end" : "start" }, trait.label),
          svg("text", { x: left ? layerX[0] - 20 : layerX[4] + 20, y: y + 11, class: "node-value", "text-anchor": left ? "end" : "start" }, ""),
        );
      }
      nodeGroup.append(group);
      return group;
    }));
    root.append(
      svg("text", { x: layerX[2], y: 30, class: "layer-title", "text-anchor": "middle" }, "경향 좌표"),
      svg("text", { x: layerX[0], y: 30, class: "layer-title", "text-anchor": "middle" }, "오늘"),
      svg("text", { x: layerX[4], y: 30, class: "layer-title", "text-anchor": "middle" }, "기대값"),
    );
    paintWeights();
  }

  /* Idle look: edge thickness and colour show the learned weights. */
  function paintWeights() {
    profile.network.layers.forEach((layer, index) => layer.weights.forEach((row, to) => row.forEach((weight, from) => {
      const line = edges[index][to][from];
      line.setAttribute("stroke-width", (0.4 + Math.min(Math.abs(weight), 1.6) * 1.4).toFixed(2));
      line.setAttribute("class", weight >= 0 ? "edge positive" : "edge negative");
      line.style.opacity = (0.08 + Math.min(Math.abs(weight), 1.5) * 0.18).toFixed(2);
    })));
  }

  /* Fire layer by layer: edges glow by |weight × incoming activation|, nodes by |activation|. */
  function fire(result) {
    fireTimers.forEach(clearTimeout);
    fireTimers = [];
    const { activations, readings } = result;
    paintWeights();
    nodes.flat().forEach((node) => node.classList.remove("is-active", "is-odd"));
    get("verdict-title").textContent = "판단 중…";
    get("verdict-title").dataset.unusual = "false";
    const step = reduceMotion ? 0 : 230;
    activations.forEach((values, layer) => {
      fireTimers.push(setTimeout(() => {
        if (layer > 0) {
          profile.network.layers[layer - 1].weights.forEach((row, to) => row.forEach((weight, from) => {
            const line = edges[layer - 1][to][from];
            const signal = Math.min(1, Math.abs(weight * activations[layer - 1][from]) / 1.2);
            line.style.opacity = (0.06 + signal * 0.9).toFixed(2);
            line.classList.remove("is-firing");
            void line.getBBox();
            if (signal > 0.25 && !reduceMotion) line.classList.add("is-firing");
          }));
        }
        values.forEach((value, index) => {
          const node = nodes[layer][index];
          const strength = Math.min(1, Math.abs(value) / (layer === 0 || layer === 4 ? 2.5 : 1));
          node.style.setProperty("--glow", strength.toFixed(2));
          node.classList.add("is-active");
          if ((layer === 0 || layer === 4) && odd(readings[index])) node.classList.add("is-odd");
          if (layer === 0 || layer === 4) {
            const reading = readings[index];
            node.querySelector(".node-value").textContent = format(reading, layer === 0 ? reading.value : reading.expected);
          }
        });
        if (layer === activations.length - 1) showVerdict(result);
      }, layer * step));
    });
  }

  function gaugePosition(error, limit) {
    const ratio = Math.log10(error / limit);
    return Math.max(2, Math.min(100, 50 + ratio * 38));
  }

  function showVerdict(result) {
    const title = result.unusual ? "평소 경향과 달라요" : "평소 경향 안이에요";
    get("verdict-title").textContent = title;
    get("verdict-title").dataset.unusual = String(result.unusual);
    const reasons = result.reasons.map((reading) =>
      `${reading.label}${particle(reading.label)} ${reading.gap > 0 ? reading.up : reading.down}` +
      `(기대 ${format(reading, reading.expected)} → 오늘 ${format(reading, reading.value)})`);
    get("verdict-copy").textContent = result.unusual
      ? reasons.length ? `${reasons.join(", ")}. 이 운전자의 다른 습관으로 보면 나오기 어려운 조합이에요.`
        : "한 습관이 크게 튀지는 않았지만, 여러 습관의 조합이 이 운전자의 평소 경향과 조금씩 어긋났어요."
      : "값이 평소와 달라도 이 운전자의 습관들이 함께 움직이는 방식 안에 있어요.";
    get("gauge-fill").style.width = `${gaugePosition(result.error, result.threshold)}%`;
    get("gauge-fill").dataset.unusual = String(result.unusual);
    get("gauge-value").textContent = `어긋남 ${result.error.toFixed(2)} · 경계 ${result.threshold.toFixed(2)}`;
    drawLatent(result.latent, result.unusual);
    get("readings").replaceChildren(...[...result.readings]
      .sort((a, b) => Math.abs(b.gap) - Math.abs(a.gap)).slice(0, 3).map((reading) => {
        const item = document.createElement("li");
        item.dataset.odd = String(odd(reading));
        item.innerHTML = "<strong></strong><span></span><div class=\"gap-track\"><i></i></div>";
        item.querySelector("strong").textContent = reading.label;
        item.querySelector("span").textContent =
          `오늘 ${format(reading, reading.value)} · 기대 ${format(reading, reading.expected)} · 평소 ${format(reading, reading.typical)}`;
        const bar = item.querySelector("i");
        const width = Math.min(50, Math.abs(reading.gap) / 4 * 50);
        bar.style.width = `${width}%`;
        bar.style.left = reading.gap >= 0 ? "50%" : `${50 - width}%`;
        return item;
      }));
  }

  function drawLatent(today, unusual) {
    const root = get("latent");
    const past = profile.historyLatent();
    const sizes90 = past.flat().map(Math.abs).sort((a, b) => a - b);
    const spread = Math.max(0.05, sizes90[Math.floor(sizes90.length * 0.9)]);
    const clamp = (value) => Math.max(-1.12, Math.min(1.12, value / spread));
    const place = ([x, y]) => [100 + clamp(x) * 62, 100 - clamp(y) * 62];
    root.replaceChildren(
      svg("line", { x1: 100, y1: 12, x2: 100, y2: 188, class: "axis" }),
      svg("line", { x1: 12, y1: 100, x2: 188, y2: 100, class: "axis" }),
      ...past.map((point) => {
        const [cx, cy] = place(point);
        return svg("circle", { cx, cy, r: 2.6, class: "past" });
      }),
    );
    const [cx, cy] = place(today);
    root.append(svg("circle", { cx, cy, r: 13, class: `today-ring${unusual ? " is-unusual" : ""}` }),
      svg("circle", { cx, cy, r: 5.5, class: `today${unusual ? " is-unusual" : ""}` }));
  }

  function run(vector, label) {
    current = profile.assess(vector);
    get("network-title").textContent = label;
    fire(current);
  }

  const dayButtons = () => [
    ...days.map(({ id: key, title }) => ({ id: key, label: title })),
    { id: "mine", label: mine?.vector ? "내 직접 운전" : "내 직접 운전 (기록 없음)" },
  ];

  function drawDays(selected) {
    buttonsFor(get("day-picker"), dayButtons(), selected, pickDay);
    const mineButton = get("day-picker").lastElementChild;
    mineButton.disabled = !mine?.vector;
    mineButton.title = mine?.vector ? "판단 엔진에서 직접 운전한 최근 기록"
      : "판단 엔진에서 직접 운전을 끝까지 달리면 이 브라우저에 습관 6개가 저장돼요.";
  }

  function runDay(id) {
    if (id === "mine" && !mine?.vector) id = "tired";
    dayId = id;
    drawDays(dayId);
    if (id === "mine") {
      run(mine.vector, "내 직접 운전 · 판단 엔진에서 달린 최근 기록");
      return;
    }
    const day = days.find((entry) => entry.id === id);
    run(drive(day.params(profile.driver)), `${day.title} · 오늘 주행이 경향 모델을 지나는 모습`);
  }

  function pickDay(id) {
    stopScan();
    runDay(id);
  }

  function drawLoss() {
    const max = Math.max(...losses);
    const min = Math.min(...losses);
    get("loss-path").setAttribute("d", losses.map((loss, index) => {
      const x = (index / Math.max(1, losses.length - 1)) * 118 + 1;
      const y = 26 - ((loss - min) / (max - min || 1)) * 24;
      return `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(" "));
  }

  function train(id) {
    stopScan();
    driverId = id;
    const driver = drivers.find((entry) => entry.id === id);
    buttonsFor(get("driver-picker"), drivers.map(({ id: key, name }) => ({ id: key, label: name })), driverId, train);
    get("validation-body").hidden = true;
    get("rerun").hidden = true;
    get("validate").disabled = true;
    if (cache?.[id]) {
      get("training-status").textContent = `${driver.name}의 평소 주행 ${cache[id].history.length}회 불러옴`;
      setTimeout(() => { if (driverId === id) learn(driver, cache[id].history); }, 0);
      return;
    }
    inChunks(planHistory(driver), drive, (count, total) => {
      if (driverId === id) get("training-status").textContent = `${driver.name}의 평소 주행 기록 만드는 중 · ${count}/${total}회`;
    }, (trips) => {
      if (driverId === id) learn(driver, trips);
    });
  }

  function learn(driver, trips) {
    const id = driver.id;
    profile = createProfile(driver, trips);
    losses = [];
    buildNetwork();
    const tick = () => {
      if (profile.driver.id !== id || driverId !== id) return;
      losses.push(profile.train(epochsPerFrame));
      drawLoss();
      paintWeights();
      get("training-status").textContent =
        `평소 주행 ${profile.trips.length}회로 경향 학습 · ${profile.epochs}/${totalEpochs}회 · 오차 ${losses.at(-1).toFixed(3)}`;
      if (profile.epochs < totalEpochs) requestAnimationFrame(tick);
      else {
        get("training-status").textContent =
          `${profile.driver.name} 경향 학습 완료 · 평소 ${profile.trips.length}회 · 오차 ${losses.at(-1).toFixed(3)}`;
        get("validate").disabled = false;
        runDay(dayId);
      }
    };
    requestAnimationFrame(tick);
  }

  function stopScan() {
    clearInterval(scanTimer);
    scanTimer = null;
    get("scan").textContent = "상황 스캔";
    get("scan-status").textContent = "네 가지 날을 연달아 판단해요.";
  }

  get("scan").addEventListener("click", () => {
    if (scanTimer) { stopScan(); return; }
    let index = 0;
    const next = () => {
      const day = days[index % days.length];
      runDay(day.id);
      get("scan-status").textContent = `${index % days.length + 1}/${days.length} · ${day.title}`;
      index += 1;
      if (index > days.length * 2 - 1) stopScan();
    };
    get("scan").textContent = "스캔 정지";
    next();
    scanTimer = setInterval(next, 2200);
  });

  function caseItem(entry) {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.type = "button";
    const label = entry.kind === "평소" ? "평소 날" : `반응 +${entry.delay.toFixed(2)}초`;
    button.textContent = `${label} · 어긋남 ${entry.result.error.toFixed(2)}`;
    button.addEventListener("click", () => {
      stopScan();
      drawDays(null);
      run(entry.vector, `검증 사례 재생 · ${label}`);
      get("network").scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "center" });
    });
    item.append(button);
    return item;
  }

  function drawSweep(result) {
    const root = get("sweep");
    const x = (delay) => 34 + delay / delayLevels.at(-1) * 252;
    const y = (rate) => 112 - rate * 96;
    const points = result.sweep.map(({ delay, rate }) => [x(delay), y(rate)]);
    root.replaceChildren(
      svg("rect", { x: x(result.missFrom), y: 16, width: 286 - x(result.missFrom), height: 96, class: "sweep-zone" }),
      svg("line", { x1: 34, y1: 112, x2: 286, y2: 112, class: "axis" }),
      svg("line", { x1: 34, y1: 16, x2: 34, y2: 112, class: "axis" }),
      svg("text", { x: 30, y: 20, class: "sweep-label", "text-anchor": "end" }, "100%"),
      svg("text", { x: 30, y: 115, class: "sweep-label", "text-anchor": "end" }, "0%"),
      svg("text", { x: 160, y: 134, class: "sweep-label", "text-anchor": "middle" }, "반응이 늦어진 정도(초) →  감지율"),
      svg("polyline", { points: points.map((point) => point.join(",")).join(" "), class: "sweep-line" }),
      ...result.sweep.map(({ delay, rate }) => {
        const dot = svg("circle", { cx: x(delay), cy: y(rate), r: 3.5, class: "sweep-dot" });
        dot.append(svg("title", {}, `+${delay}초 · ${Math.round(rate * 100)}%`));
        return dot;
      }),
      ...result.sweep.filter((_, index) => index % 2 === 0 || index === result.sweep.length - 1).map(({ delay }) =>
        svg("text", { x: x(delay), y: 124, class: "sweep-label", "text-anchor": "middle" }, delay.toFixed(2))),
    );
  }

  /* Fixed-seed days were pre-driven into profile-cache.js (a test keeps it equal to live drives). */
  get("validate").addEventListener("click", () => {
    const judged = profile;
    const plan = planEvaluation(judged.driver);
    const saved = cache?.[judged.driver.id]?.evaluation;
    if (!saved || saved.length !== plan.length) { rerun(); return; }
    showValidation(summarize(judged, plan.map((entry, index) => ({ ...entry, vector: saved[index] }))));
    get("validation-source").textContent = "고정 시드로 미리 달린 결과예요.";
    get("rerun").hidden = false;
  });

  get("rerun").addEventListener("click", rerun);

  function rerun() {
    const button = get("rerun");
    const label = button.textContent;
    const judged = profile;
    button.hidden = false;
    button.disabled = true;
    get("validate").disabled = true;
    inChunks(planEvaluation(judged.driver), (entry) => ({ ...entry, vector: drive(entry.params) }), (count, total) => {
      button.textContent = `${count}/${total}일 달려 보는 중`;
    }, (cases) => {
      button.textContent = label;
      button.disabled = false;
      get("validate").disabled = false;
      if (judged !== profile) return;
      showValidation(summarize(judged, cases));
      get("validation-source").textContent = "이 브라우저에서 방금 다시 달린 결과예요.";
    }, 20);
  }

  function showValidation(result) {
    const percent = (rate) => `${(rate * 100).toFixed(1)}%`;
    get("false-alarm-rate").textContent = `${percent(result.falseAlarmRate)} · ${result.falseAlarms.length}/${result.normalDays}일`;
    const { due } = result;
    get("miss-label").textContent = `미탐 · ${result.missFrom}초 이상 늦었는데 평소라고 판단`;
    get("miss-rate").textContent = `${percent(result.missRate)} · ${result.misses.length}/${due}일`;
    drawSweep(result);
    get("false-alarms").replaceChildren(...result.falseAlarms.slice(0, 8).map(caseItem));
    get("misses").replaceChildren(...result.misses.sort((a, b) => b.delay - a.delay).slice(0, 8).map(caseItem));
    get("validation-body").hidden = false;
  }

  window.addEventListener("storage", (event) => {
    if (event.key !== DriveWatcherHabits.storageKey) return;
    mine = DriveWatcherHabits.load();
    if (profile) drawDays(dayId);
  });

  train(driverId);
})();
