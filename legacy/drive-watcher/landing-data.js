/* 랜딩의 개요 장면이 보여 줄 값. 도심 시연 기록을 판정 엔진에 넣어 구한다. 화면에 숫자를 직접 적지 않는다. */
const DriveWatcherLanding = (() => {
  function summarize(engine, replays) {
    const scene = replays.scenes.find(({ id }) => id === "city");
    const session = engine.createSession(replays.history);
    const steps = replays.framesFor(scene, "changed").map((frame) => ({ frame, result: session.ingest(frame) }));
    const driving = steps.filter(({ frame }) => frame.gear === "D");
    const metric = engine.metrics.find(({ key }) => key === "hardBrakes");
    const reading = ({ result }) => result.readings.find(({ key }) => key === metric.key);
    const upper = reading(driving[0]).baseline.upper;
    const windows = driving.map((step) => ({
      from: step.frame.minute - engine.windowMinutes,
      to: step.frame.minute,
      value: reading(step).value,
      above: reading(step).above,
      observation: step.result.observation,
    }));
    const detection = steps.at(-1).result.detection;
    const above = windows.filter((window) => window.above);
    return {
      label: metric.label,
      unit: metric.unit,
      windowMinutes: engine.windowMinutes,
      upper,
      windows,
      detectionMinute: detection ? detection.minute : null,
      parked: steps.at(-1).result.parked,
      card: detection ? {
        title: "평소와 달라요",
        evidence: `${metric.label} · ${engine.windowMinutes}분 구간당 평소 ${upper}${metric.unit} 이하, 이번 최고 ${Math.max(...windows.map(({ value }) => value))}${metric.unit}`,
        where: `출발 후 ${above.map(({ from, to, value }) => `${from}–${to}분 ${value}${metric.unit}`).join(", ")}`,
      } : null,
    };
  }

  return { summarize };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherLanding;
