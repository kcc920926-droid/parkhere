/* Read the two CAN channels from the pinned comma2k19 example. */
const DriveWatcherCAN = (() => {
  const revision = "4c7f1a6e1957745beadc1def0e7225f559b09a2a";
  const source = `https://raw.githubusercontent.com/commaai/comma2k19/${revision}/Example_1/b0c9d2329ad1606b%7C2018-08-02--08-34-47/40/processed_log/CAN`;
  const windowSeconds = 5;
  const baselineWindows = 5;
  const decelThreshold = 4;
  const context = "comma2k19-example";
  const requests = ["speed", "steering_angle"].flatMap((channel) =>
    ["t", "value"].map((column) => ({ channel, column, url: `${source}/${channel}/${column}` })));

  function decodeFloat64(buffer) {
    const view = new DataView(buffer);
    if (view.byteLength < 10 || view.getUint8(0) !== 0x93 ||
        new TextDecoder().decode(new Uint8Array(buffer, 1, 5)) !== "NUMPY" ||
        view.getUint8(6) !== 1) throw new Error("지원하지 않는 CAN 배열 형식");
    const headerLength = view.getUint16(8, true);
    const offset = 10 + headerLength;
    if (offset >= view.byteLength || (view.byteLength - offset) % 8 !== 0) {
      throw new Error("CAN 배열 길이 오류");
    }
    const header = new TextDecoder().decode(new Uint8Array(buffer, 10, headerLength));
    if (!/'descr':\s*'<f8'/.test(header) || !/'fortran_order':\s*False/.test(header)) {
      throw new Error("지원하지 않는 CAN 배열 자료형");
    }
    return Array.from({ length: (view.byteLength - offset) / 8 }, (_, index) =>
      view.getFloat64(offset + index * 8, true));
  }

  async function fetchChannel(name, signal, onRequest) {
    const columns = await Promise.all(requests.filter(({ channel }) => channel === name).map(async (request) => {
      const start = performance.now();
      onRequest({ ...request, state: "요청 중" });
      let status;
      try {
        const response = await fetch(request.url, { signal, cache: "no-store" });
        status = response.status;
        if (!response.ok) throw new Error(`CAN 원본 HTTP ${status}`);
        const buffer = await response.arrayBuffer();
        const values = decodeFloat64(buffer);
        onRequest({ ...request, state: "수신", status, bytes: buffer.byteLength,
          milliseconds: Math.round(performance.now() - start) });
        return values;
      } catch (error) {
        onRequest({ ...request, state: "실패", status });
        throw error;
      }
    }));
    const [times, values] = columns;
    if (times.length !== values.length || times.length < 2 ||
        times.some((time, index) => !Number.isFinite(time) || (index > 0 && time <= times[index - 1])) ||
        values.some((value) => !Number.isFinite(value))) throw new Error("CAN 채널 시각 또는 값 오류");
    return { times, values };
  }

  function toSamples(speed, steering) {
    const start = speed.times[0];
    let previous = 0;
    let angle = -1;
    return speed.times.map((time, index) => {
      while (angle + 1 < steering.times.length && steering.times[angle + 1] <= time) angle++;
      while (previous + 1 < index && speed.times[previous + 1] <= time - .5) previous++;
      const interval = time - speed.times[previous];
      const deceleration = interval >= .5 && interval <= .65
        ? (speed.values[previous] - speed.values[index]) / interval : null;
      const angleDeg = angle >= 0 && time - steering.times[angle] <= .25 ? steering.values[angle] : null;
      return { seconds: time - start, speedMs: speed.values[index], angleDeg, deceleration };
    });
  }

  function makeWindows(samples) {
    const windows = [];
    let index = 0;
    let strong = false;
    const end = samples.at(-1).seconds;
    for (let boundary = windowSeconds; boundary <= end; boundary += windowSeconds) {
      let count = 0;
      let driven = false;
      let validChanges = 0;
      const startIndex = index;
      while (index < samples.length && samples[index].seconds < boundary) {
        const { speedMs, deceleration } = samples[index++];
        driven ||= speedMs > 0;
        if (deceleration === null) {
          strong = false;
        } else {
          validChanges++;
          if (!strong && deceleration >= decelThreshold) {
            count++;
            strong = true;
          }
          if (deceleration < 3) strong = false;
        }
      }
      const speedKph = index > startIndex ? samples[index - 1].speedMs * 3.6 : null;
      windows.push({
        minute: boundary / 60, context, speedKph, driven,
        values: validChanges > 0 ? { hardBrakes: count } : {},
      });
    }
    return windows;
  }

  async function load(signal, onRequest = () => {}) {
    const [speed, steering] = await Promise.all([
      fetchChannel("speed", signal, onRequest), fetchChannel("steering_angle", signal, onRequest),
    ]);
    const samples = toSamples(speed, steering);
    const windows = makeWindows(samples);
    if (windows.length <= baselineWindows) throw new Error("CAN 기록 길이 부족");
    return { samples, windows, angleCount: steering.values.length };
  }

  return { load, requests, windowSeconds, baselineWindows, decelThreshold };
})();
