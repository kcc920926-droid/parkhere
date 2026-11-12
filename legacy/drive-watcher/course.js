/* One synthetic course, with the same traffic and vehicle model for both modes. */
const DriveWatcherCourse = (() => {
  const secondsPerWindow = 8;
  const windowCount = 6;
  const duration = secondsPerWindow * windowCount;
  const tick = 1 / 60;
  const ticksPerWindow = Math.round(secondsPerWindow / tick);
  const context = "demo-course";
  const maxSpeed = 80 / 3.6;
  const hardBrakeThreshold = 4;
  const initialSpeed = 12;
  const laneWidth = 3.6;
  const vehicleWidth = 1.9;
  const clearance = 5.2;
  const brakeWindows = new Set([0, 1, 3, 5]);
  const trafficLayout = [
    { lane: 0, distance: 38, speed: 12, color: "#d9c5a5", reference: true },
    { lane: -1, distance: 23, speed: 10.2, color: "#879dbf" },
    { lane: 1, distance: 49, speed: 10.9, color: "#a67961" },
    { lane: -1, distance: 62, speed: 10.7, color: "#bbaa89" },
    { lane: 0, distance: 82, speed: 11.5, color: "#8e9eaa" },
    { lane: 1, distance: 87, speed: 11.5, color: "#a98ca5" },
    { lane: -1, distance: 151, speed: 11.8, color: "#92a89f" },
    { lane: 0, distance: 220, speed: 12.1, color: "#b2a384" },
    { lane: 1, distance: 231, speed: 12, color: "#7b92ae" },
    { lane: -1, distance: 305, speed: 12.2, color: "#8c9b9e" },
  ];
  const history = [0, 0, 0, 0, 0].map((hardBrakes) => ({ context, values: { hardBrakes } }));
  const margins = { hardBrakes: 0 };

  function hasBrakeEvent(seconds) {
    return brakeWindows.has(Math.floor(seconds / secondsPerWindow));
  }

  function leadSpeedAt(seconds) {
    if (!hasBrakeEvent(seconds)) return 12;
    const phase = seconds % secondsPerWindow;
    if (phase < 1.25) return 12;
    if (phase < 3.25) return 12 - (phase - 1.25) * 2.5;
    if (phase < 4.25) return 7;
    return Math.min(12, 7 + (phase - 4.25) * 1.7);
  }

  function automaticPedals(state) {
    const phase = state.seconds % secondsPerWindow;
    const brakingWindow = hasBrakeEvent(state.seconds);
    const brake = brakingWindow && phase >= 2.2 && phase < 2.95;
    return { brake, throttle: (!brakingWindow || phase >= 4.2) && state.speed < initialSpeed };
  }

  function approach(value, target, rate) {
    if (value < target) return Math.min(target, value + rate * tick);
    return Math.max(target, value - rate * tick);
  }

  /* `detail: false` skips the per-tick traffic list that only the road view draws. */
  function createVehicle({ detail = true } = {}) {
    let ticks = 0;
    let speed = initialSpeed;
    let distance = 0;
    const traffic = trafficLayout.map((car) => ({
      ...car, lateral: car.lane * laneWidth, actualSpeed: car.speed, braking: false,
    })).sort((a, b) => b.distance - a.distance);
    const reference = traffic.find((car) => car.reference);
    let gap = trafficLayout[0].distance;
    let lateral = 0;
    let lateralVelocity = 0;
    let throttle = 0;
    let brake = 0;
    let armed = true;
    let hardBrakes = 0;
    let driven = false;

    function snapshot() {
      const seconds = ticks * tick;
      const lane = Math.round(lateral / laneWidth) || 0;
      const laneOffset = lateral - lane * laneWidth;
      const onMarking = Math.abs(laneOffset) + vehicleWidth / 2 >= laneWidth / 2;
      return {
        seconds, speed, distance, gap, throttle, brake, lateral, lateralVelocity,
        lane, laneOffset, onMarking,
        leadSpeed: reference.actualSpeed,
        traffic: detail ? traffic.map((car) => ({
          lateral: car.lateral, depth: car.distance - distance, color: car.color,
          braking: car.braking,
        })) : null,
        hardBrakes,
        finished: ticks >= ticksPerWindow * windowCount,
      };
    }

    function step(pedals) {
      if (ticks >= ticksPerWindow * windowCount) return { state: snapshot() };
      throttle = approach(throttle, pedals.throttle && !pedals.brake ? 1 : 0, 4);
      brake = approach(brake, pedals.brake ? 1 : 0, pedals.brake ? 3 : 6);
      const nextSpeed = Math.max(0, Math.min(maxSpeed, speed + (2.9 * throttle - 7.2 * brake - .32) * tick));
      const deceleration = (speed - nextSpeed) / tick;
      const hardBrake = armed && deceleration >= hardBrakeThreshold;
      if (hardBrake) {
        armed = false;
        hardBrakes++;
      }
      if (!pedals.brake && brake <= .1) armed = true;

      const targetLateralSpeed = (pedals.steer ?? 0) * Math.min(4, speed * .34);
      lateralVelocity = approach(lateralVelocity, targetLateralSpeed, 16);
      const nextLateral = Math.max(-laneWidth, Math.min(laneWidth, lateral + lateralVelocity * tick));
      const overlaps = (position, car) => Math.abs(position - car.lateral) < vehicleWidth;
      const enteringOccupiedSpace = traffic.some((car) =>
        !overlaps(lateral, car) && overlaps(nextLateral, car) && Math.abs(car.distance - distance) < clearance);
      if (enteringOccupiedSpace) lateralVelocity = 0;
      else lateral = nextLateral;
      if (Math.abs(lateral) === laneWidth) lateralVelocity = 0;

      // Move the scripted traffic front to back and leave room for cars stopped in its path.
      const frontByLane = new Map();
      for (const car of traffic) {
        const cruiseSpeed = car.reference ? leadSpeedAt(ticks * tick) : car.speed;
        const ahead = frontByLane.get(car.lane) ?? Infinity;
        const playerAhead = overlaps(lateral, car) && distance > car.distance ? distance : Infinity;
        const nextDistance = Math.min(car.distance + cruiseSpeed * tick,
          Math.max(car.distance, Math.min(ahead, playerAhead) - clearance));
        const actualSpeed = (nextDistance - car.distance) / tick;
        car.braking = actualSpeed < cruiseSpeed - .05 || actualSpeed < car.actualSpeed - .02;
        car.actualSpeed = actualSpeed;
        car.distance = nextDistance;
        frontByLane.set(car.lane, nextDistance);
      }

      const nearest = traffic.reduce((nearestGap, car) => car.distance >= distance && overlaps(lateral, car)
        ? Math.min(nearestGap, car.distance - distance) : nearestGap, Infinity);
      const travel = Math.min(nextSpeed * tick, Math.max(0, nearest - clearance));
      speed = travel / tick;
      distance += travel;
      gap = Number.isFinite(nearest) ? nearest - travel : null;
      driven ||= travel > 0;
      ticks++;

      let frame;
      if (ticks % ticksPerWindow === 0) {
        frame = {
          minute: (ticks * tick) / 60,
          context,
          gear: "D",
          speedKph: speed * 3.6,
          driven,
          values: { hardBrakes },
        };
        hardBrakes = 0;
        driven = false;
      }
      return { state: snapshot(), deceleration, hardBrake, frame };
    }

    return { snapshot, step };
  }

  return { secondsPerWindow, windowCount, duration, tick, context, history, margins,
    hardBrakeThreshold, laneWidth, vehicleWidth, brakeWindows: [...brakeWindows], createVehicle, automaticPedals };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherCourse;
