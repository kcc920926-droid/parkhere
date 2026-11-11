/* Chase view. The road and every car share the same perspective projection. */
function drawDriveWatcherRoad(canvas, state, deceleration, reducedMotion) {
  const ctx = canvas.getContext("2d");
  const { width, height } = canvas;
  const { laneWidth, vehicleWidth } = DriveWatcherCourse;
  const center = width / 2 - (reducedMotion ? 0 : state.lateralVelocity * 7);
  const cameraX = reducedMotion ? 0 : state.lateral * .75;
  const horizon = 112 + (reducedMotion ? 0 : Math.min(5, Math.max(0, deceleration)));
  const near = -5.5;
  const far = 210;

  function project(x, depth, elevation = 0) {
    const scale = 450 / (depth + 11);
    return [center + (x - cameraX) * scale, horizon + (4.65 - elevation) * scale];
  }

  function polygon(points, color) {
    ctx.beginPath();
    ctx.moveTo(...points[0]);
    for (const point of points.slice(1)) ctx.lineTo(...point);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
  }

  function strip(left, right, from, to, color) {
    polygon([project(left, from), project(right, from), project(right, to), project(left, to)], color);
  }

  const sky = ctx.createLinearGradient(0, 0, 0, horizon + 16);
  sky.addColorStop(0, "#060f20");
  sky.addColorStop(1, "#1b3550");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, width, height);
  polygon([[0, 95], [76, 74], [141, 102], [256, 70], [352, 104], [505, 110],
    [605, 98], [725, 73], [819, 100], [892, 77], [960, 100], [960, 143], [0, 143]], "#0c1b2e");
  ctx.fillStyle = "#07121f";
  ctx.fillRect(0, horizon + 5, width, height - horizon);
  strip(-laneWidth * 1.6, laneWidth * 1.6, near, far, "#22384c");
  strip(-laneWidth * 1.5, laneWidth * 1.5, near, far, "#101c2a");
  for (const side of [-1, 1]) {
    const edge = side * laneWidth * 1.49;
    strip(edge - .045, edge + .045, near, far, "#5ff2ff");
  }

  const phase = reducedMotion ? 0 : state.distance % 13;
  for (let depth = 208 - phase; depth >= near - 6; depth -= 13) {
    const start = Math.max(near, depth);
    if (depth + 5.6 <= start) continue;
    for (const boundary of [-laneWidth / 2, laneWidth / 2]) {
      strip(boundary - .045, boundary + .045, start, depth + 5.6, "#8fb3c7");
    }
  }

  function car({ lateral: x, depth, color, braking, player }) {
    const half = vehicleWidth / 2;
    const front = depth + 2.2;
    const rear = depth - 2.2;
    const point = (offset, z, y) => project(x + offset, z, y);
    const face = (points, paint) => polygon(points.map(([offset, z, y]) => point(offset, z, y)), paint);

    strip(x - half - .08, x + half + .08, rear - .12, front + .12, "#152522a8");
    if (player && state.onMarking) {
      strip(x - half - .1, x - half + .015, rear, front, "#ffb547");
      strip(x + half - .015, x + half + .1, rear, front, "#ffb547");
    }
    const visibleSide = cameraX <= x ? -half : half;
    face([[visibleSide, rear, .26], [visibleSide, front, .26], [visibleSide, front, .84],
      [visibleSide, rear, .84]], "#263137");
    face([[-half, rear, .84], [half, rear, .84], [half, front, .84], [-half, front, .84]], color);
    face([[-half, rear, .25], [half, rear, .25], [half, rear, .84], [-half, rear, .84]], color);
    face([[-half, rear - .02, .22], [half, rear - .02, .22],
      [half, rear - .02, .37], [-half, rear - .02, .37]], "#263137");
    face([[-.72, depth + .85, .84], [.72, depth + .85, .84],
      [.62, depth + .44, 1.45], [-.62, depth + .44, 1.45]], "#254651");
    face([[-.62, depth - .85, 1.45], [.62, depth - .85, 1.45],
      [.62, depth + .44, 1.45], [-.62, depth + .44, 1.45]], color);
    face([[-.75, depth - 1.5, .84], [.75, depth - 1.5, .84],
      [.62, depth - .85, 1.45], [-.62, depth - .85, 1.45]], "#24434c");
    for (const left of [-.83, .47]) {
      face([[left, rear - .03, .49], [left + .36, rear - .03, .49],
        [left + .36, rear - .03, .69], [left, rear - .03, .69]], braking ? "#ff7461" : "#943f46");
    }
    if (player) {
      face([[-.19, rear - .04, .43], [.19, rear - .04, .43],
        [.19, rear - .04, .56], [-.19, rear - .04, .56]], "#d9f1e9");
    }
  }

  const visible = state.traffic.filter(({ depth }) => depth >= -2.5 && depth <= far);
  visible.push({ lateral: state.lateral, depth: 0, color: "#5ff2ff", braking: state.brake > .12, player: true });
  visible.sort((a, b) => b.depth - a.depth).forEach(car);
}
