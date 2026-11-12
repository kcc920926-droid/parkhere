/* 시계 차: DriveWatch 캐릭터를 그린다. 좌표는 120 격자로, 차량 앱 ClockCar.kt와 가족 화면 clockcar.js와 같은 값을 쓴다.
   DWCar.draw(stage, { theme: "purple" | "white" }) → set(status). status: learning·stable·observe·talk */
window.DWCar = (() => {
  const NS = "http://www.w3.org/2000/svg";
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

  function draw(stage, { theme = "purple" } = {}) {
    const t = THEMES[theme];
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

  return { draw, LABEL, el };
})();
