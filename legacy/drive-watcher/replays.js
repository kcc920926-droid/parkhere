/* Synthetic summaries, ordered as [hard brakes, steering corrections, driving minutes since rest]. */
const DriveWatcherReplays = (() => {
  const contexts = { city: "도심 생활도로", highway: "고속도로" };
  const valuesFor = ([hardBrakes, steeringCorrections, minutesSinceBreak]) => ({
    hardBrakes, steeringCorrections, minutesSinceBreak,
  });

  const history = [
    ...[[0, 2, 11], [1, 2, 12], [1, 3, 13], [2, 3, 14], [1, 4, 15]]
      .map((values) => ({ context: "city", values: valuesFor(values) })),
    ...[[0, 1, 68], [0, 2, 70], [1, 2, 72], [1, 3, 74], [2, 3, 76]]
      .map((values) => ({ context: "highway", values: valuesFor(values) })),
  ];

  const scenes = [
    {
      id: "city",
      title: "도심 기록",
      situation: "제동과 조향 비교",
      context: "city",
      start: 0,
      regular: [[1, 2, 2], [0, 3, 4], [4, 3, 6], [1, 2, 8], [1, 3, 10], [0, 2, 12]],
      changed: [[1, 2, 2], [0, 3, 4], [1, 3, 6], [4, 7, 8], [5, 8, 10], [1, 3, 12]],
    },
    {
      id: "highway",
      title: "고속도로 기록",
      situation: "누적 주행 시간 비교",
      context: "highway",
      start: 72,
      regular: [[1, 2, 64], [0, 2, 66], [1, 3, 68], [0, 2, 70], [1, 2, 72], [0, 2, 74]],
      changed: [[1, 2, 74], [0, 2, 76], [1, 3, 78], [0, 2, 80], [1, 2, 82], [0, 2, 84]],
    },
  ];

  function framesFor(scene, variant) {
    const frames = scene[variant].map((values, index) => ({
      minute: scene.start + (index + 1) * 2,
      context: scene.context,
      gear: "D",
      speedKph: scene.context === "highway" ? 86 : 37,
      values: valuesFor(values),
    }));
    return [...frames, {
      minute: frames.at(-1).minute + 1,
      context: scene.context,
      gear: "P",
      speedKph: 0,
    }];
  }

  return { contexts, history, scenes, framesFor };
})();
