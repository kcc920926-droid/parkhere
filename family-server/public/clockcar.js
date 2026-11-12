/* 시계 차: DriveWatch 캐릭터. 옆모습 차의 창문이 계기판이고, 바늘 위치·칸 색·표정이 함께 상태를 말한다.
   좌표는 120 격자. 차량 앱의 ClockCar.kt, 아이콘 원본 app/design/brand/clockcar-icon.svg와 같은 값을 쓴다. */
(() => {
  const NS = "http://www.w3.org/2000/svg";
  const PURPLE = "#6B5CF5", INK = "#131417", FACE = "#2A2350";
  const BODY = "M20 84 Q13 84 13 76 Q13 61 28 58 L34 57 Q38 30 60 28 Q82 28 88 56 L94 57 Q107 60 107 74 Q107 84 100 84 Z";
  const WINDOW = "M40 57 Q42 38 60 37 Q78 38 80 57 Z";
  const CX = 60, CY = 55, R = 14;
  /* 칸: 초록(안정)·노랑(관찰)·주황(대화 권장). 각도는 화면 기준(오른쪽 0°, 시계 방향). */
  const BANDS = [["stable", 180, 54, "#02C265"], ["observe", 243, 54, "#F5A623"], ["talk", 306, 54, "#FE3D16"]];
  const NEEDLE = { learning: 270, stable: 207, observe: 270, talk: 333 };
  const MOUTH = {
    learning: "M96 75.5 Q99 77.5 102 75.5",
    stable: "M95 75 Q99 78.5 103 75",
    observe: "M96 76 H102",
    talk: "M95 77 Q99 73.5 103 77",
  };

  const el = (tag, attrs) => {
    const node = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
    return node;
  };
  const at = (deg, r = R) => [CX + r * Math.cos(deg * Math.PI / 180), CY + r * Math.sin(deg * Math.PI / 180)];
  const arc = (from, sweep) => {
    const [x1, y1] = at(from), [x2, y2] = at(from + sweep);
    return `M${x1.toFixed(2)} ${y1.toFixed(2)} A${R} ${R} 0 0 1 ${x2.toFixed(2)} ${y2.toFixed(2)}`;
  };

  /* status: learning·insufficient·stable·observe·talk. 보라 둥근 사각 배지 안에 그린다. */
  window.clockCar = (status, size = 28) => {
    const key = status === "insufficient" ? "learning" : (NEEDLE[status] === undefined ? "learning" : status);
    const svg = el("svg", { viewBox: "0 0 120 120", width: size, height: size, class: "clockcar", "aria-hidden": "true" });
    svg.append(el("rect", { width: 120, height: 120, rx: 31, fill: PURPLE }));
    const g = el("g", { transform: "translate(3 1) scale(0.95)" });
    g.append(el("path", { d: BODY, fill: "#fff" }));
    for (const x of [36, 84]) g.append(el("circle", { cx: x, cy: 84, r: 10, fill: "#fff", stroke: PURPLE, "stroke-width": 4 }));
    g.append(el("path", { d: WINDOW, fill: "#EDEEF2" }));
    if (key === "learning") {
      g.append(el("path", { d: arc(180, 180), fill: "none", stroke: "#B9BBC2", "stroke-width": 4.5, "stroke-dasharray": "3 3.5" }));
    } else {
      for (const [band, from, sweep, color] of BANDS) {
        g.append(el("path", { d: arc(from, sweep), fill: "none", stroke: color, "stroke-width": 4.5, opacity: band === key ? 1 : 0.28 }));
      }
    }
    const [nx, ny] = at(NEEDLE[key], 11);
    const needle = key === "learning" ? "#686A72" : INK;
    g.append(el("path", { d: `M${CX} ${CY} L${nx.toFixed(2)} ${ny.toFixed(2)}`, stroke: needle, "stroke-width": 3, "stroke-linecap": "round" }));
    g.append(el("circle", { cx: CX, cy: CY, r: 2.6, fill: needle }));
    g.append(el("circle", { cx: 99, cy: 67, r: 4.2, fill: FACE }));
    g.append(el("circle", { cx: 100.3, cy: 65.8, r: 1.3, fill: "#fff" }));
    if (key === "talk") g.append(el("path", { d: "M94 60.5 L101.5 62", stroke: FACE, "stroke-width": 1.8, "stroke-linecap": "round" }));
    g.append(el("path", { d: MOUTH[key], fill: "none", stroke: FACE, "stroke-width": 2, "stroke-linecap": "round" }));
    svg.append(g);
    return svg;
  };
})();
