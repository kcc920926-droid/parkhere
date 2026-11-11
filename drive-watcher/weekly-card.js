/* Weekly card on the center screen: steps through 12 synthetic weeks so the status change is visible. */
const DriveWatcherWeeklyCard = (() => {
  const { metrics, assess, observeWeeks, talkWeeks, minTripsPerWeek, learning } = DriveWatcherWeekly;
  const { scenes, tripsFor } = DriveWatcherWeeklyHistory;
  const charted = metrics.filter(({ kind }) => kind === "rate");
  const chips = { learning: "학습 중", insufficient: "보류", stable: "안정", observe: "관찰", talk: "대화 권장" };
  const svgNs = "http://www.w3.org/2000/svg";

  function svg(tag, attributes) {
    const node = document.createElementNS(svgNs, tag);
    for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
    return node;
  }

  function weekLabel(week) {
    const [, month, day] = week.split("-").map(Number);
    return `${month}월 ${day}일 주`;
  }

  function particle(label) {
    const code = label.charCodeAt(label.length - 1) - 0xac00;
    return code >= 0 && code % 28 ? "이" : "가";
  }

  function copyFor(entry) {
    const labelOf = (key) => metrics.find((metric) => metric.key === key).label;
    const names = entry.evidence.map(({ key }) => labelOf(key));
    const longest = Math.max(0, ...entry.evidence.map(({ streak }) => streak));
    const singleAbove = entry.readings.find(({ above }) => above);
    switch (entry.status) {
      case "learning":
        return ["평소 운전을 익히는 중이에요",
          `처음 ${learning.days}일, 주행 ${learning.trips}회까지는 비교하지 않고 평소 범위만 모아요. 지금까지 ${entry.tripsSoFar}회예요.`];
      case "insufficient":
        return ["이번 주는 비교를 쉬었어요", `주행이 ${minTripsPerWeek}회보다 적은 주는 판정하지 않고, 이어지던 주 수도 처음부터 다시 세요.`];
      case "observe":
        return ["평소와 다른 주가 이어지고 있어요",
          `${names[0]}${particle(names[0])} ${longest}주 연속 평소 범위를 넘었어요. 다음 주도 함께 지켜볼게요.`];
      case "talk":
        return ["가족과 이야기해 볼 때예요",
          `${names.join("·")}${particle(names.at(-1))} 평소와 다른 주가 이어졌어요. 동의하신 범위의 요약을 가족에게 보낼 수 있어요.`];
      default:
        return ["이번 주도 평소처럼 운전하셨어요", singleAbove
          ? `${labelOf(singleAbove.key)}${particle(labelOf(singleAbove.key))} 평소보다 잦았지만, 한 주만으로는 상태를 바꾸지 않아요.`
          : "모든 지표가 평소 범위 안이었어요."];
    }
  }

  function amount(metric, value) {
    return metric.kind === "rate" ? `100km당 ${value}회` : `${value}${metric.unit}`;
  }

  function mount({ get }) {
    let sceneId = "changing";
    let weeks = [];
    let index = 0;
    let timer = null;

    function load(id) {
      sceneId = id;
      weeks = assess(tripsFor(scenes.find((scene) => scene.id === id)), { context: "city" });
      index = weeks.length - 1;
    }

    function chartRow(metric, top) {
      const height = 50;
      const step = 360 / weeks.length;
      const values = weeks.map((entry) => entry.values[metric.key] ?? 0);
      const uppers = weeks.map((entry) => entry.readings.find(({ key }) => key === metric.key).baseline?.upper)
        .filter(Number.isFinite);
      const scale = Math.max(...values, ...uppers, 1) * 1.1;
      const group = svg("g", { transform: `translate(0 ${top})` });
      const title = svg("text", { x: 0, y: -4, class: "weekly-axis" });
      title.textContent = `${metric.label} (${metric.unit})`;
      group.append(title);
      weeks.forEach((entry, position) => {
        const reading = entry.readings.find(({ key }) => key === metric.key);
        const barHeight = (entry.values[metric.key] ?? 0) / scale * height;
        group.append(svg("rect", {
          x: position * step + 4, y: height - barHeight, width: step - 8, height: barHeight, rx: 2,
          class: `weekly-bar${reading.above ? " is-above" : ""}${entry.status === "learning" ? " is-learning" : ""}` +
            `${position === index ? " is-selected" : ""}`,
        }));
        if (reading.baseline) {
          const y = height - reading.baseline.upper / scale * height;
          group.append(svg("line", { x1: position * step + 1, x2: (position + 1) * step - 1, y1: y, y2: y, class: "weekly-upper" }));
        }
      });
      return group;
    }

    function strip() {
      let month;
      return weeks.map((entry, position) => {
        const item = document.createElement("li");
        item.dataset.status = entry.status;
        item.dataset.selected = String(position === index);
        item.title = `${weekLabel(entry.week)} · ${chips[entry.status]}`;
        const label = document.createElement("span");
        const entryMonth = Number(entry.week.split("-")[1]);
        label.textContent = entryMonth !== month ? `${entryMonth}월` : "";
        month = entryMonth;
        item.append(label);
        return item;
      });
    }

    function comparison(reading) {
      const metric = metrics.find(({ key }) => key === reading.key);
      const box = document.createElement("div");
      box.className = "car-comparison";
      box.dataset.above = String(reading.above);
      const scale = Math.max(reading.value, reading.baseline.upper, 1);
      const title = document.createElement("span");
      title.textContent = reading.streak ? `${metric.label} · ${reading.streak}주째 평소보다 잦아요` : metric.label;
      const row = (name, value, width, today) => {
        const line = document.createElement("div");
        line.innerHTML = "<span></span><strong></strong>";
        line.children[0].textContent = name;
        line.children[1].textContent = value;
        const track = document.createElement("div");
        track.className = `compare-track${today ? " compare-today" : ""}`;
        const bar = document.createElement("i");
        bar.style.width = `${width / scale * 100}%`;
        track.append(bar);
        return [line, track];
      };
      box.append(title, ...row("평소", `${amount(metric, reading.baseline.upper)} 이하`, reading.baseline.upper, false),
        ...row("이번 주", amount(metric, reading.value), reading.value, reading.above));
      return box;
    }

    function evidence(entry) {
      if (entry.status === "learning") {
        const box = document.createElement("div");
        box.className = "car-comparison";
        box.innerHTML = "<span>평소 범위를 모으는 중</span><div><span>주행 기록</span><strong></strong></div>" +
          "<div class=\"compare-track\"><i></i></div>";
        box.querySelector("strong").textContent = `${Math.min(entry.tripsSoFar, learning.trips)} / ${learning.trips}회`;
        box.querySelector("i").style.width = `${Math.min(1, entry.tripsSoFar / learning.trips) * 100}%`;
        return [box];
      }
      const shown = entry.readings.filter(({ above, baseline }) => above && baseline);
      const calm = entry.readings.find(({ key, baseline, value }) => key === "hardBrakesPer100km" && baseline && value !== null);
      return (shown.length ? shown : calm ? [calm] : []).slice(0, 2).map(comparison);
    }

    function draw() {
      const entry = weeks[index];
      const [title, copy] = copyFor(entry);
      get("weekly-week").textContent = weekLabel(entry.week);
      get("weekly-title").textContent = title;
      get("weekly-copy").textContent = copy;
      get("weekly-chip").textContent = chips[entry.status];
      get("weekly-chip").dataset.status = entry.status;
      get("weekly-strip").replaceChildren(...strip());
      get("weekly-evidence").replaceChildren(...evidence(entry));
      get("weekly-prev").disabled = index === 0;
      get("weekly-next").disabled = index === weeks.length - 1;
      get("weekly-play").textContent = timer ? "정지" : "12주 재생";
      for (const scene of scenes) get(`weekly-scene-${scene.id}`).setAttribute("aria-pressed", String(scene.id === sceneId));
      get("weekly-chart").replaceChildren(...charted.map((metric, row) => chartRow(metric, 14 + row * 80)));
    }

    function stop() {
      clearInterval(timer);
      timer = null;
    }

    function go(next) {
      stop();
      index = Math.min(weeks.length - 1, Math.max(0, next));
      draw();
    }

    get("weekly-prev").addEventListener("click", () => go(index - 1));
    get("weekly-next").addEventListener("click", () => go(index + 1));
    get("weekly-play").addEventListener("click", () => {
      if (timer) { stop(); draw(); return; }
      if (index === weeks.length - 1) index = 0;
      timer = setInterval(() => {
        if (index >= weeks.length - 1) stop();
        else index += 1;
        draw();
      }, 900);
      draw();
    });
    for (const scene of scenes) {
      get(`weekly-scene-${scene.id}`).addEventListener("click", () => { stop(); load(scene.id); draw(); });
    }
    get("weekly-rule").textContent =
      `주행 중 판정과 같은 식(중앙값 + max(여유, 3×MAD))으로 그 주의 평소 상한을 정해요. 주 단위로는 ${observeWeeks}주 연속이면 관찰, ` +
      `${talkWeeks}주 연속이거나 두 지표가 함께 ${observeWeeks}주 연속이면 대화 권장이에요. 상한을 넘은 주는 다음 기준에서 빼요.`;
    load(sceneId);
    draw();
    return { stop() { if (timer) { stop(); draw(); } } };
  }

  return { mount };
})();
