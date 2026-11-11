/* DriveWatch 자녀 폰 웹 화면: 가입 → 부모님 차와 연결 → 가족 목록 → 주간 요약. */
(() => {
  const app = document.getElementById("app");
  const KEY = "dw.family";
  const STATUS = {
    learning: { chip: "", label: "평소 운전을 배우는 중" },
    insufficient: { chip: "", label: "이번 주는 주행이 적었어요" },
    stable: { chip: "stable", label: "안정" },
    observe: { chip: "observe", label: "관찰" },
    talk: { chip: "talk", label: "대화 권장" },
  };
  const ERRORS = {
    name_required: "이름을 입력해 주세요.",
    invalid_code: "코드가 맞지 않아요. 차 화면의 코드를 다시 확인해 주세요.",
    expired_code: "코드 시간이 지났어요. 차에서 새 코드를 띄워 주세요.",
    too_many_attempts: "여러 번 틀렸어요. 10분 뒤에 다시 해 주세요.",
    unauthorized: "다시 가입해 주세요.",
    rate_limited: "요청이 너무 많아요. 잠시 뒤에 다시 해 주세요.",
    capacity_reached: "시연 서버가 가득 찼어요. 운영자에게 알려 주세요.",
  };

  let session = null;
  try { session = JSON.parse(localStorage.getItem(KEY) || "null"); } catch { session = null; }
  const remember = (value) => {
    session = value;
    try { value ? localStorage.setItem(KEY, JSON.stringify(value)) : localStorage.removeItem(KEY); } catch { /* 저장이 막힌 브라우저에서도 이번 화면은 동작한다 */ }
  };

  /* 차 화면의 QR로 들어오면 코드가 주소에 붙어 온다. 읽은 뒤 주소에서 지운다. */
  const query = new URLSearchParams(location.search);
  let pendingCode = (query.get("code") || "").replace(/\D/g, "").slice(0, 6);
  if (query.has("code")) history.replaceState(null, "", location.pathname);

  let timer = 0;
  const every = (ms, task) => { clearInterval(timer); timer = setInterval(task, ms); };

  async function api(method, path, body) {
    const response = await fetch(path, {
      method,
      headers: { ...(body ? { "content-type": "application/json" } : {}), ...(session ? { authorization: `Bearer ${session.token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401) { remember(null); show(join); }
      throw new Error(ERRORS[data.error] || "잠시 뒤에 다시 해 주세요.");
    }
    return data;
  }

  function h(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
      if (key === "class") node.className = value;
      else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
      else if (value !== false && value != null) node.setAttribute(key, value === true ? "" : value);
    }
    for (const child of children.flat()) if (child != null && child !== false) node.append(child);
    return node;
  }
  const card = (label, body, extra = "") => h("div", { class: `card ${extra}` }, h("span", { class: "label" }, label), body);
  const dayOf = (ms) => new Date(ms).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });

  function show(screen, ...args) {
    clearInterval(timer);
    app.replaceChildren(...screen(...args));
    app.querySelector("[autofocus]")?.focus();
    window.scrollTo(0, 0);
  }

  /* P1 가입 */
  function join() {
    const name = h("input", { name: "name", autocomplete: "name", maxlength: 20, autofocus: true });
    const error = h("p", { class: "error", role: "alert" });
    const submit = async (event) => {
      event.preventDefault();
      try {
        remember(await api("POST", "/api/family/members", { name: name.value }));
        show(connect);
      } catch (problem) { error.textContent = problem.message; }
    };
    return [h("form", { class: "form", onsubmit: submit, style: "display:contents" },
      h("h1", {}, "가족으로 가입"),
      h("p", { class: "muted" }, "부모님의 운전 요약을 받아보세요"),
      h("label", { class: "field" }, h("span", {}, "이름"), name),
      error,
      h("div", { class: "spacer" }),
      h("p", { class: "demo" }, "시연용 화면이에요. 휴대폰 인증은 없고, 이름은 별명을 써도 돼요."),
      h("button", { class: "btn primary", type: "submit" }, "다음"))];
  }

  /* P2 부모님 차와 연결 */
  function connect() {
    let relation = "부모님";
    const code = h("input", { name: "code", inputmode: "numeric", autocomplete: "one-time-code", maxlength: 6, pattern: "[0-9]{6}", value: pendingCode, autofocus: true, "aria-label": "6자리 코드" });
    pendingCode = "";
    code.addEventListener("input", () => { code.value = code.value.replace(/\D/g, "").slice(0, 6); });
    const error = h("p", { class: "error", role: "alert" });
    const chips = ["엄마", "아빠", "부모님"].map((label) => h("button", {
      type: "button", "aria-pressed": String(label === relation),
      onclick: (event) => {
        relation = label;
        chips.forEach((chip) => chip.setAttribute("aria-pressed", String(chip === event.currentTarget)));
      },
    }, label));
    const submit = async (event) => {
      event.preventDefault();
      if (code.value.length !== 6) { error.textContent = "6자리 코드를 입력해 주세요."; return; }
      try { show(linked, await api("POST", "/api/family/links", { code: code.value, relation })); }
      catch (problem) { error.textContent = problem.message; }
    };
    return [h("form", { onsubmit: submit, style: "display:contents" },
      h("h1", {}, "부모님 차와 연결"),
      h("p", { class: "muted" }, "부모님 차 화면의 6자리 코드를 입력하세요"),
      h("label", { class: "field code" }, code),
      h("p", { class: "muted" }, "어떻게 부르시나요?"),
      h("div", { class: "chips" }, chips),
      error,
      h("div", { class: "spacer" }),
      session?.linked ? h("button", { class: "btn", type: "button", onclick: () => show(home) }, "돌아가기") : null,
      h("button", { class: "btn primary", type: "submit" }, "연결하기"))];
  }

  /* P3 연결 완료 */
  function linked(link) {
    remember({ ...session, linked: true });
    return [
      h("div", { class: "center" },
        h("h1", {}, `${link.relation}(${link.name}) 차와`, h("br"), "연결됐어요"),
        h("p", { class: "muted" }, "매주 금요일에 운전 요약이 와요."),
        h("p", { class: "muted" }, "처음 2주는 평소 운전을 배우는 기간이에요."),
        h("p", { class: "muted" }, `${link.relation}가 보내기로 한 것만 보여요.`)),
      h("div", { class: "spacer" }),
      h("button", { class: "btn primary", autofocus: true, onclick: () => show(home) }, "시작하기"),
    ];
  }

  /* P4 가족 목록 */
  function home() {
    const banner = h("div");
    const list = h("div", { style: "display:contents" });
    const refresh = async () => {
      let data;
      try { data = await api("GET", "/api/family/overview"); } catch { return; }
      if (!data.drivers.length) { show(connect); return; }
      const fresh = data.drivers.find((driver) => driver.latest && !driver.latest.acked);
      banner.replaceChildren(fresh ? h("div", { class: "banner" }, `${fresh.relation}의 이번 주 운전 요약이 도착했어요`) : "");
      list.replaceChildren(...data.drivers.map((driver) => {
        const status = driver.latest ? STATUS[driver.latest.status] : null;
        const note = !driver.latest ? "아직 요약이 없어요" : driver.latest.acked ? "확인함" : "요약 도착";
        return h("button", { class: "card", onclick: () => show(summary, driver.driverId) },
          h("span", { class: "label" }, `${driver.relation} · ${driver.name}`),
          h("span", {}, status ? h("span", { class: `state ${status.chip}` }, status.label) : "", status ? " · " : "", note));
      }));
    };
    refresh();
    every(4000, refresh);
    return [
      h("h1", {}, "가족 운전"),
      banner, list,
      h("div", { class: "spacer" }),
      h("button", { class: "link", onclick: () => show(connect) }, "+ 부모님 차 추가"),
    ];
  }

  /* P5 주간 요약 */
  function summary(driverId) {
    const body = h("div", { style: "display:contents" });
    const title = h("button", { class: "back", onclick: () => show(home) }, "← 가족 운전");
    let shownKey = "";
    const render = (data) => {
      title.textContent = `← ${data.relation} 운전 · 이번 주`;
      const item = data.summary;
      if (!item) {
        body.replaceChildren(h("p", { class: "muted" }, "아직 도착한 요약이 없어요. 요약은 매주 금요일에 와요."));
        return;
      }
      const status = STATUS[item.status];
      const chipText = item.status === "stable" ? "안정 · 평소와 같아요"
        : item.status === "observe" ? `관찰 중인 변화 ${Math.max(item.changes.length, 1)}개` : status.label;
      const numbers = item.evidence.flatMap((entry) => {
        const [unit, per] = entry.unit.split("/");
        const from = entry.typical === null ? "" : `${entry.typical}${unit} → `;
        return [h("span", { class: "label" }, entry.label), h("strong", {},
          `${per ? `${per}당 ` : ""}${from}${entry.value}${unit}${entry.weeks ? ` · ${entry.weeks}주 연속` : ""}`)];
      });
      const changes = item.changes.map((line) => h("span", {}, line));
      const hidden = !item.evidenceShared && (item.status === "observe" || item.status === "talk")
        ? h("span", { class: "muted" }, `자세한 숫자는 ${data.relation}가 보내기로 한 경우에만 보여요`) : null;
      const cards = [];
      if (item.status === "talk") {
        cards.push(card("근거", [...changes, ...numbers, hidden]));
        cards.push(card("이렇게 물어보세요", [
          h("strong", {}, `"${data.relation === "부모님" ? "" : `${data.relation}, `}요즘 운전하다가 깜짝 놀란 적 있어?"`),
          h("span", { class: "muted" }, '"나이", "위험해서", "그만"은 피해 주세요'),
        ], "tint"));
        cards.push(card("함께 할 일", h("span", {}, "한 번 동승하기 · 약속 같이 정하기 · 치매안심센터 선별검사 같이 가기")));
      } else if (item.status === "observe") {
        cards.push(card("달라진 점", [...changes, ...numbers, hidden]));
        if (item.goodPoints.length) cards.push(card("잘하신 점", h("span", {}, item.goodPoints.join(" · "))));
        cards.push(card("지금은", h("span", {}, "따로 하실 일은 없어요. 다음 주 요약을 같이 봐요."), "tint"));
      } else {
        if (item.headline) cards.push(card("한 줄 요약", h("span", {}, item.headline)));
        if (item.goodPoints.length) cards.push(card("잘하신 점", h("span", {}, item.goodPoints.join(" · "))));
      }
      if (item.promise) cards.push(card("약속", h("span", {}, item.promise)));
      if (item.avoided) cards.push(card("피한 구간", h("span", {}, item.avoided)));
      const ack = h("button", { class: "btn primary", disabled: item.acked }, item.acked ? "확인했어요 ✓" : "확인했어요");
      ack.addEventListener("click", async () => {
        ack.disabled = true;
        try { await api("POST", `/api/family/drivers/${driverId}/ack`, { week: item.week }); shownKey = ""; refresh(); }
        catch { ack.disabled = false; }
      });
      body.replaceChildren(
        h("span", { class: `chip ${status.chip}` }, chipText),
        ...cards,
        h("p", { class: "muted" }, `${dayOf(item.sentAt)}에 ${data.relation}가 보낸 요약이에요`),
        h("button", { class: "link", onclick: () => show(monthly, driverId) }, "📅 월별 기록 보기 ›"),
        h("div", { class: "spacer" }),
        h("div", { class: "row" }, h("a", { class: "btn", href: "tel:" }, "전화하기"), ack));
    };
    const refresh = async () => {
      let data;
      try { data = await api("GET", `/api/family/drivers/${driverId}/summary`); } catch { return; }
      const key = JSON.stringify(data);
      if (key !== shownKey) { shownKey = key; render(data); }
    };
    refresh();
    every(4000, refresh);
    return [title, body];
  }

  /* 월별 기록: 부모님이 보낸 주간 요약을 달별로 모은다. 주행 하나하나의 기록은 받지 않는다. */
  function monthly(driverId) {
    const body = h("div", { style: "display:contents" });
    const title = h("button", { class: "back", onclick: () => show(summary, driverId) }, "← 월별 기록");
    let months = [];
    let index = 0;
    let relation = "";
    const label = (key) => `${Number(key.slice(0, 4))}년 ${Number(key.slice(5, 7))}월`;
    const render = () => {
      if (!months.length) {
        body.replaceChildren(h("p", { class: "muted" }, `아직 ${relation}가 보낸 요약이 없어요.`));
        return;
      }
      const [key, weeks] = months[index];
      const flow = weeks.slice().reverse().map((w) => STATUS[w.status]?.label ?? w.status)
        .filter((name, i, all) => i === 0 || all[i - 1] !== name);
      const nav = h("div", { class: "row month" },
        h("button", { class: "btn", disabled: index >= months.length - 1, onclick: () => { index++; render(); } }, "◀"),
        h("strong", { class: "month-title" }, label(key)),
        h("button", { class: "btn", disabled: index === 0, onclick: () => { index--; render(); } }, "▶"));
      const rows = weeks.map((w) => {
        const status = STATUS[w.status] ?? { chip: "", label: w.status };
        const [, m, d] = w.week.split("-").map(Number);
        return h("div", { class: "card" },
          h("span", { class: "label" }, `${m}/${d} 주 · ${w.acked ? "확인함 ✓" : "아직 확인 안 함"}`),
          h("span", {}, h("span", { class: `state ${status.chip}` }, status.label), w.headline ? ` · ${w.headline}` : ""),
          w.avoided ? h("span", { class: "muted" }, `피한 구간 · ${w.avoided}`) : null);
      });
      body.replaceChildren(
        nav,
        h("div", { class: "card tint" }, h("span", { class: "label" }, "이 달 흐름"), h("strong", {}, flow.join(" → ")),
          h("span", { class: "muted" }, `요약 ${weeks.length}주 · ${relation}가 보내기로 한 것만 보여요`)),
        ...rows);
    };
    (async () => {
      let data;
      try { data = await api("GET", `/api/family/drivers/${driverId}/summaries`); } catch { return; }
      relation = data.relation;
      title.textContent = `← ${data.relation} 운전 · 월별 기록`;
      const byMonth = new Map();
      for (const item of data.summaries) {
        const key = item.week.slice(0, 7);
        byMonth.set(key, [...(byMonth.get(key) ?? []), item]);
      }
      months = [...byMonth.entries()];
      render();
    })();
    every(60000, () => {});
    return [title, body];
  }

  show(!session ? join : session.linked && !pendingCode ? home : connect);
})();
