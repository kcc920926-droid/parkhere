function createDriveWatcherSound() {
  const AudioContext = window.AudioContext || window.webkitAudioContext;
  let context;
  let fundamental;
  let harmonics;
  let filter;
  let volume;

  async function start() {
    if (!AudioContext) return;
    if (!context) {
      context = new AudioContext();
      fundamental = context.createOscillator();
      fundamental.type = "triangle";
      fundamental.frequency.value = 48;
      harmonics = context.createOscillator();
      harmonics.type = "sawtooth";
      harmonics.frequency.value = 96;
      const harmonicLevel = context.createGain();
      harmonicLevel.gain.value = .24;

      filter = context.createBiquadFilter();
      filter.type = "lowpass";
      filter.frequency.value = 180;
      volume = context.createGain();
      volume.gain.value = 0;
      fundamental.connect(filter);
      harmonics.connect(harmonicLevel).connect(filter);
      filter.connect(volume).connect(context.destination);
      fundamental.start();
      harmonics.start();
    }
    if (context.state === "suspended" || context.state === "interrupted") await context.resume();
  }

  function update(state, audible) {
    if (!volume) return;
    const now = context.currentTime;
    if (!audible) {
      volume.gain.setTargetAtTime(0, now, .018);
      return;
    }

    const pitch = 48 + state.speed * 2.8 + state.throttle * 38;
    fundamental.frequency.setTargetAtTime(pitch, now, .08);
    harmonics.frequency.setTargetAtTime(pitch * 2, now, .08);
    filter.frequency.setTargetAtTime(180 + state.speed * 14 + state.throttle * 350, now, .08);
    volume.gain.setTargetAtTime(.013 + state.speed * .0007 + state.throttle * .07, now, .06);
  }

  return { start, update };
}
