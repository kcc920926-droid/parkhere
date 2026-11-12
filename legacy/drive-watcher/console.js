(() => {
  const get = (id) => document.getElementById(id);
  const { history, scenes, framesFor } = DriveWatcherReplays;
  const course = DriveWatcherCourse;
  const city = scenes.find((scene) => scene.id === "city");
  const roadPoints = [[183, 333], [238, 268], [227, 231], [285, 183], [326, 199], [417, 127], [404, 99], [451, 55]];
  const windowPointIndices = [0, 1, 2, 3, 4, 5, 7];
  const windowNames = ["출발 구간", "첫 교차로", "생활도로", "큰길 진입 전", "큰길 진입 후", "도착 구간"];
  const path = (points) => points.length ? `M${points.map((point) => point.join(" ")).join(" L")}` : "";
  const roadLengths = [0];
  roadPoints.slice(1).forEach(([x, y], index) => {
    const [prevX, prevY] = roadPoints[index];
    roadLengths.push(roadLengths.at(-1) + Math.hypot(x - prevX, y - prevY));
  });

  function pointAt(distance) {
    if (distance <= 0) return roadPoints[0];
    const end = roadLengths.findIndex((length) => length >= distance);
    if (end < 1) return roadPoints.at(-1);
    const ratio = (distance - roadLengths[end - 1]) / (roadLengths[end] - roadLengths[end - 1]);
    return roadPoints[end - 1].map((value, axis) => Math.round(value + (roadPoints[end][axis] - value) * ratio));
  }

  function pointsBetween(start, end) {
    return [pointAt(start), ...roadPoints.filter((_, index) =>
      roadLengths[index] > start && roadLengths[index] < end), pointAt(end)];
  }

  for (const id of ["nav-route-back", "nav-route-front", "detail-route-base"]) {
    get(id).setAttribute("d", path(roadPoints));
  }

  let savedTrip = DriveWatcherTrips.load();
  let trip = savedTrip;
  let parked = false;
  let view = "nav";
  let layout = "wide";
  let report;
  let weeklyCard;

  function tripReport() {
    const interval = trip ? course.secondsPerWindow / 60 : DriveWatcher.windowMinutes;
    const margins = trip ? course.margins : undefined;
    const context = trip ? course.context : city.context;
    const past = trip ? course.history : history;
    const session = DriveWatcher.createSession(past, { interval, margins });
    const frames = trip ? trip.windows.map((window, index) => ({
      minute: (index + 1) * interval,
      context,
      gear: "D",
      speedKph: window.speedKph,
      driven: window.driven,
      values: { hardBrakes: window.hardBrakes },
    })) : framesFor(city, "changed").slice(0, -1);
    const windows = frames.map((frame, index) => ({
      index,
      end: frame.minute,
      reading: session.ingest(frame).readings.find(({ key }) => key === "hardBrakes"),
    }));
    const arrival = session.ingest({ minute: frames.at(-1).minute + interval, context, gear: "P", speedKph: 0 });
    const evidence = arrival.detection?.evidence.find(({ key }) => key === "hardBrakes");
    const end = windows.findIndex((window) => window.end === arrival.detection?.minute);
    const affected = evidence && end >= 0 ? windows.slice(end - evidence.streak + 1, end + 1) : [];
    return {
      parked: arrival.parked, interval, windows, affected,
      upper: DriveWatcher.baselineFor(past, context, margins).hardBrakes?.upper,
    };
  }

  function timeLabel(minute) {
    const seconds = Math.round(minute * 60);
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
  }

  function brakeDot(event, routeScale) {
    const [x, y] = pointAt(event.distance * routeScale);
    const dot = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    dot.setAttribute("cx", x);
    dot.setAttribute("cy", y);
    dot.setAttribute("r", "5");
    dot.setAttribute("fill", "#ffd383");
    dot.setAttribute("stroke", "#15231f");
    dot.setAttribute("stroke-width", "2");
    return dot;
  }

  function fillReport() {
    const { windows, affected, interval, upper } = report;
    const changed = affected.length > 0;
    const comparable = windows.filter(({ reading }) => reading.value !== null);
    get("summary-title").textContent = changed ? "평소와 달라요"
      : comparable.length < DriveWatcher.requiredWindows ? "비교할 구간이 더 필요해요" : "연속으로 달라진 구간은 없었어요";
    get("summary-copy").textContent = changed ? "두 구간 연속 강한 제동이 평소 기준보다 잦았어요."
      : comparable.length < DriveWatcher.requiredWindows ? "움직인 구간 두 개가 모이면 연속 변화를 확인할 수 있어요."
        : "강한 제동이 평소 기준을 연속으로 넘지 않았어요.";
    get("show-segments").textContent = changed ? "어느 구간이었나요?" : "이번 주행 구간 보기";
    get("comparison-window").textContent = trip ? `${course.secondsPerWindow}초 구간당 강한 제동` : "2분 구간당 강한 제동";
    get("car-comparison").hidden = !comparable.length || upper === undefined;
    if (comparable.length && upper !== undefined) {
      const peak = Math.max(...comparable.map(({ reading }) => reading.value));
      get("summary-baseline").textContent = `${upper}회 이하`;
      get("summary-actual").textContent = `${peak}회`;
      const scale = Math.max(1, upper, peak);
      get("baseline-bar").style.width = `${upper / scale * 100}%`;
      get("actual-bar").style.width = `${peak / scale * 100}%`;
    }

    const routeScale = trip?.windows.at(-1).distance ? roadLengths.at(-1) / trip.windows.at(-1).distance : 0;
    let highlight = "";
    if (changed) {
      const first = affected[0];
      const last = affected.at(-1);
      highlight = trip ? path(pointsBetween((trip.windows[first.index - 1]?.distance ?? 0) * routeScale,
        trip.windows[last.index].distance * routeScale))
        : path(roadPoints.slice(windowPointIndices[first.index], windowPointIndices[last.index + 1] + 1));
      get("segment-copy").textContent =
        `출발 후 ${timeLabel(first.end - interval)}–${timeLabel(last.end)}에 ${affected.length}구간 연속 평소 기준을 넘었어요.`;
    } else {
      get("segment-copy").textContent = "완료한 구간별 강한 제동 횟수예요. 정차한 구간은 비교에서 제외돼요.";
    }
    get("segment-title").textContent = changed ? "강한 제동이 이어진 구간" : "이번 주행 구간";
    get("segment-legend").textContent = trip ? "주행거리로 그린 가상 경로 · 점은 강한 제동 위치"
      : "가상 경로 · 주황색은 연속으로 기준을 넘은 구간";
    for (const id of ["detail-route-highlight", "nav-route-highlight"]) get(id).setAttribute("d", highlight);
    for (const id of ["detail-brake-events", "nav-brake-events"]) {
      get(id).replaceChildren(...(trip?.events ?? []).map((event) => brakeDot(event, routeScale)));
    }

    get("segment-list").replaceChildren(...(trip || !changed ? windows : affected).map(({ index, end, reading }) => {
      const item = document.createElement("li");
      item.dataset.detected = String(affected.some((window) => window.index === index));
      const name = document.createElement("strong");
      name.textContent = trip ? `${index + 1}번째 구간` : windowNames[index];
      const time = document.createElement("span");
      time.textContent = `${timeLabel(end - interval)}–${timeLabel(end)}`;
      const count = document.createElement("b");
      count.textContent = reading.value === null ? "비교 제외" : `${reading.value}회`;
      item.append(name, time, count);
      return item;
    }));
  }

  function render() {
    const appOpen = parked && view !== "nav";
    const split = layout === "split";
    get("car-display").dataset.layout = layout;
    get("display-wide").setAttribute("aria-pressed", String(!split));
    get("display-split").setAttribute("aria-pressed", String(split));
    get("car-nav").hidden = appOpen && !split;
    get("car-media").hidden = !split || appOpen;
    get("car-app").hidden = !appOpen;
    get("car-summary").hidden = view !== "summary";
    get("car-detail").hidden = view !== "detail";
    get("car-weekly").hidden = view !== "weekly";
    get("weekly-presenter").hidden = !appOpen || view !== "weekly";
    get("weekly-engine").hidden = !appOpen || view !== "weekly";
    if (view !== "weekly") weeklyCard?.stop();
    for (const id of ["nav-route-highlight", "nav-brake-events"]) {
      get(id).toggleAttribute("hidden", view !== "detail");
    }
    get("car-gear").textContent = parked ? "P" : "D";
    get("car-state").textContent = parked ? "주차" : "주행 중";
    get("car-time").textContent = parked ? "10:21" : "10:18";
    get("nav-turn").textContent = parked ? "✓" : "↱";
    get("nav-distance").textContent = parked ? "도착" : "450 m";
    get("nav-direction").textContent = parked ? "안내를 마쳤어요" : "앞 교차로에서 우회전";
    get("nav-eta").textContent = parked ? "주차 완료" : "도착까지 3분";
    get("map-car").setAttribute("transform", parked ? "translate(166 -128)" : "");
    get("open-watcher").disabled = !parked;
    get("open-watcher").setAttribute("aria-current", appOpen ? "page" : "false");
    get("open-nav").setAttribute("aria-current", appOpen ? "false" : "page");
    get("watcher-hint").hidden = parked;
    get("console-park").hidden = parked;
    get("console-reset").hidden = !parked;
    get("console-instruction").textContent = !parked
      ? "가상 주행 중이에요. 주차한 뒤 화면 아래에서 드라이브 와쳐를 열어 보세요."
      : appOpen ? "전체 화면과 분할 화면을 바꿔 같은 기록을 확인할 수 있어요."
        : "주차했어요. 화면 아래의 드라이브 와쳐를 열어 보세요.";
    get("trip-manual").disabled = !savedTrip;
    get("trip-manual").setAttribute("aria-pressed", String(Boolean(trip)));
    get("trip-example").setAttribute("aria-pressed", String(!trip));
    get("trip-status").textContent = trip
      ? `완료 구간 ${trip.windows.length}개 · ${trip.windows.length * course.secondsPerWindow}초`
      : savedTrip ? "저장한 직접 운전 기록도 열 수 있어요." : "판단 엔진에서 직접 운전하면 이 브라우저에 기록이 저장돼요.";
    get("car-trip-source").textContent = trip ? "내 직접 운전 · 시뮬레이터" : "시연 차량 · 예시 기록";
    get("nav-route-label").textContent = trip ? "직접 운전 · 가상 경로" : "생활도로 예시 경로";
    get("summary-source").textContent = trip ? "드라이브 와쳐 · 내 직접 운전" : "드라이브 와쳐 · 예시 기록";
  }

  function startDrive(selected) {
    parked = false;
    report = undefined;
    trip = selected;
    view = "nav";
    render();
  }

  get("display-wide").addEventListener("click", () => { layout = "wide"; render(); });
  get("display-split").addEventListener("click", () => { layout = "split"; render(); });
  get("trip-manual").addEventListener("click", () => { if (savedTrip) startDrive(savedTrip); });
  get("trip-example").addEventListener("click", () => startDrive(null));
  window.addEventListener("storage", (event) => {
    if (event.key !== DriveWatcherTrips.key) return;
    const hadManual = Boolean(savedTrip);
    savedTrip = DriveWatcherTrips.load();
    if (!parked && (!hadManual || trip)) trip = savedTrip;
    render();
  });
  get("console-park").addEventListener("click", () => {
    report = tripReport();
    parked = report.parked;
    if (parked) fillReport();
    render();
    if (parked) get("open-watcher").focus();
  });
  get("console-reset").addEventListener("click", () => {
    startDrive(trip ? savedTrip : null);
    get("console-park").focus();
  });
  get("open-nav").addEventListener("click", () => { view = "nav"; render(); });
  get("open-watcher").addEventListener("click", () => {
    if (!parked) return;
    view = "summary";
    render();
    get("summary-title").focus();
  });
  get("show-segments").addEventListener("click", () => {
    if (!parked) return;
    view = "detail";
    render();
    get("segment-title").focus();
  });
  get("show-weekly").addEventListener("click", () => {
    if (!parked) return;
    view = "weekly";
    render();
    get("weekly-title").focus();
  });
  get("weekly-back").addEventListener("click", () => {
    view = "summary";
    render();
    get("show-weekly").focus();
  });
  get("back-summary").addEventListener("click", () => {
    view = "summary";
    render();
    get("show-segments").focus();
  });
  weeklyCard = DriveWatcherWeeklyCard.mount({ get });
  render();
})();
