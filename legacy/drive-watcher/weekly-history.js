/* Synthetic 12-week city history for one driver: four 8 km trips a week. Demo data only. */
const DriveWatcherWeeklyHistory = (() => {
  const firstMonday = Date.parse("2026-08-17T00:00:00Z");
  const tripDays = [0, 2, 4, 5];
  const km = 8;
  const usual = {
    hardBrakes: [1, 2, 2, 1, 2, 2, 1, 2],
    steeringCorrections: [12, 11, 13, 12, 12, 13, 11, 12],
  };
  const scenes = [
    {
      id: "steady",
      title: "한 주만 달랐던 기록",
      hardBrakes: [...usual.hardBrakes, 6, 2, 1, 2],
      steeringCorrections: [...usual.steeringCorrections, 12, 13, 12, 11],
    },
    {
      id: "changing",
      title: "변화가 이어진 기록",
      hardBrakes: [...usual.hardBrakes, 6, 5, 6, 7],
      steeringCorrections: [...usual.steeringCorrections, 12, 13, 18, 19],
    },
  ];

  /* Spread a weekly total over the week's trips without exceeding the per-trip cap. */
  function spread(total, cap) {
    return tripDays.map((_, index) => Math.min(cap, Math.max(0, total - index * cap)));
  }

  function even(total) {
    const base = Math.floor(total / tripDays.length);
    return tripDays.map((_, index) => base + (index < total % tripDays.length ? 1 : 0));
  }

  function tripsFor(scene) {
    return scene.hardBrakes.flatMap((brakes, week) => {
      const brakesByTrip = spread(brakes, 2);
      const steeringByTrip = even(scene.steeringCorrections[week]);
      return tripDays.map((offset, index) => ({
        date: new Date(firstMonday + (week * 7 + offset) * 86400000).toISOString().slice(0, 10),
        context: "city",
        km,
        values: {
          hardBrakes: brakesByTrip[index],
          steeringCorrections: steeringByTrip[index],
          minutesSinceBreak: 20 + ((week + index) % 4) * 2,
        },
      }));
    });
  }

  return { scenes, tripsFor };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherWeeklyHistory;
