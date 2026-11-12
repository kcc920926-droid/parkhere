/* The latest manual drive stays on this browser and is shared by both demo pages. */
const DriveWatcherTrips = (() => {
  const key = "drive-watcher.manual-trip.v1";
  const nonnegative = (value) => Number.isFinite(value) && value >= 0;

  function valid(trip) {
    const course = DriveWatcherCourse;
    if (!trip || trip.version !== 1 || !Array.isArray(trip.windows) || !trip.windows.length ||
        trip.windows.length > course.windowCount || !Array.isArray(trip.events)) return false;
    if (!trip.windows.every((window, index) => window &&
      nonnegative(window.distance) && window.distance >= (trip.windows[index - 1]?.distance ?? 0) &&
      nonnegative(window.speedKph) && typeof window.driven === "boolean" &&
      Number.isSafeInteger(window.hardBrakes) && window.hardBrakes >= 0)) return false;
    const seconds = trip.windows.length * course.secondsPerWindow;
    const distance = trip.windows.at(-1).distance;
    return trip.events.every((event) => event && nonnegative(event.seconds) && event.seconds <= seconds &&
      nonnegative(event.distance) && event.distance <= distance);
  }

  function load() {
    try {
      const trip = JSON.parse(localStorage.getItem(key));
      return valid(trip) ? trip : null;
    } catch {
      return null;
    }
  }

  function save(windows, events) {
    const limit = windows.length * DriveWatcherCourse.secondsPerWindow;
    const trip = { version: 1, windows, events: events.filter((event) => event.seconds <= limit) };
    if (!valid(trip)) return false;
    try {
      localStorage.setItem(key, JSON.stringify(trip));
      return true;
    } catch {
      return false;
    }
  }

  return { key, load, save };
})();
