/* Personal driving tendency: a small autoencoder learns how one driver's habits move together. */
const DriveWatcherProfile = (() => {
  const habits = typeof DriveWatcherHabits !== "undefined" ? DriveWatcherHabits : require("./habits.js");
  const { traits } = habits;
  /* Scripted drivers on the same course. Each trip draws alertness and hurry, which move several habits at once. */
  const drivers = [
    { id: "calm", name: "차분한 운전자", base: { reaction: 0.9, headway: 2.6, cruise: 13, hold: 0.4 }, seed: 11 },
    { id: "brisk", name: "빠른 운전자", base: { reaction: 0.62, headway: 1.4, cruise: 14.5, hold: 0.55 }, seed: 23 },
  ];
  const sizes = [6, 5, 2, 5, 6];
  const historyTrips = 80;
  const limitFactor = 1.25;
  /* A habit counts as a reason when today is this many of the driver's own standard deviations off the model. */
  const reasonGap = 0.75;

  function random(seed) {
    let state = seed >>> 0;
    return () => {
      state = (state + 0x6d2b79f5) >>> 0;
      let value = state;
      value = Math.imul(value ^ (value >>> 15), value | 1);
      value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
      return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
    };
  }

  function gaussian(rng) {
    return Math.sqrt(-2 * Math.log(1 - rng())) * Math.cos(2 * Math.PI * rng());
  }

  /* Policy parameters for one trip. Without rng the trip is the driver's noiseless typical day. */
  function tripParams(driver, alertness, hurry, rng) {
    const jitter = (size) => (rng ? size * gaussian(rng) : 0);
    const { base } = driver;
    return {
      reaction: Math.max(0.2, base.reaction - 0.1 * alertness + jitter(0.02)),
      headway: Math.max(0.8, base.headway + 0.25 * alertness - 0.25 * hurry + jitter(0.04)),
      cruise: Math.max(12.3, base.cruise + 0.6 * hurry + jitter(0.15)),
      hold: Math.max(0.15, base.hold + 0.06 * hurry - 0.06 * alertness + jitter(0.02)),
    };
  }

  const run = (params) => habits.drive(habits.createPolicy(params)).vector;

  function planHistory(driver, count = historyTrips) {
    const rng = random(driver.seed);
    return Array.from({ length: count }, () => tripParams(driver, gaussian(rng), gaussian(rng), rng));
  }

  const otherDriver = (driver) => drivers.find((other) => other.id !== driver.id);
  const days = [
    { id: "usual", title: "평소 같은 날", params: (driver) => tripParams(driver, 0.3, -0.2) },
    { id: "hurried", title: "서두른 날", params: (driver) => tripParams(driver, 0, 1.8) },
    {
      id: "tired", title: "반응이 늦은 날",
      params: (driver) => { const params = tripParams(driver, -0.4, 0); return { ...params, reaction: params.reaction + 0.3 }; },
    },
    { id: "other", title: "다른 사람이 운전한 날", params: (driver) => tripParams(otherDriver(driver), 0, 0) },
  ];

  function createNetwork(seed) {
    const rng = random(seed);
    const layers = sizes.slice(1).map((out, index) => {
      const inputs = sizes[index];
      const limit = Math.sqrt(6 / (inputs + out));
      return {
        weights: Array.from({ length: out }, () => Array.from({ length: inputs }, () => (rng() * 2 - 1) * limit)),
        bias: Array(out).fill(0),
      };
    });
    return { layers };
  }

  /* Returns every layer's activations: input, hidden, latent, hidden, reconstruction. */
  function forward(network, input) {
    const activations = [input];
    network.layers.forEach((layer, index) => {
      const last = index === network.layers.length - 1;
      const previous = activations.at(-1);
      activations.push(layer.weights.map((row, unit) => {
        const sum = row.reduce((total, weight, from) => total + weight * previous[from], layer.bias[unit]);
        return last ? sum : Math.tanh(sum);
      }));
    });
    return activations;
  }

  function createTrainer(network, data, { rate = 0.01 } = {}) {
    const moments = network.layers.map((layer) => ({
      mw: layer.weights.map((row) => row.map(() => 0)), vw: layer.weights.map((row) => row.map(() => 0)),
      mb: layer.bias.map(() => 0), vb: layer.bias.map(() => 0),
    }));
    let steps = 0;

    function epoch() {
      const grads = network.layers.map((layer) => ({
        w: layer.weights.map((row) => row.map(() => 0)), b: layer.bias.map(() => 0),
      }));
      let loss = 0;
      for (const sample of data) {
        const activations = forward(network, sample);
        const output = activations.at(-1);
        let delta = output.map((value, index) => {
          loss += (value - sample[index]) ** 2;
          return 2 * (value - sample[index]) / (data.length * sample.length);
        });
        for (let index = network.layers.length - 1; index >= 0; index -= 1) {
          const input = activations[index];
          const layer = network.layers[index];
          delta.forEach((value, unit) => {
            grads[index].b[unit] += value;
            input.forEach((activation, from) => { grads[index].w[unit][from] += value * activation; });
          });
          if (index > 0) {
            delta = input.map((activation, from) =>
              (1 - activation ** 2) * delta.reduce((total, value, unit) => total + value * layer.weights[unit][from], 0));
          }
        }
      }
      steps += 1;
      const correction1 = 1 - 0.9 ** steps;
      const correction2 = 1 - 0.999 ** steps;
      const update = (value, grad, m, v) => {
        const mean = 0.9 * m + 0.1 * grad;
        const variance = 0.999 * v + 0.001 * grad ** 2;
        return [value - rate * (mean / correction1) / (Math.sqrt(variance / correction2) + 1e-8), mean, variance];
      };
      network.layers.forEach((layer, index) => {
        const moment = moments[index];
        layer.weights.forEach((row, unit) => row.forEach((weight, from) => {
          [row[from], moment.mw[unit][from], moment.vw[unit][from]] =
            update(weight, grads[index].w[unit][from], moment.mw[unit][from], moment.vw[unit][from]);
        }));
        layer.bias.forEach((bias, unit) => {
          [layer.bias[unit], moment.mb[unit], moment.vb[unit]] =
            update(bias, grads[index].b[unit], moment.mb[unit], moment.vb[unit]);
        });
      });
      return loss / (data.length * data[0].length);
    }

    return { epoch, get steps() { return steps; } };
  }

  const meanOf = (values) => values.reduce((total, value) => total + value, 0) / values.length;

  /* One driver's profile: own history, own scale, own network. */
  function createProfile(driver, trips = planHistory(driver).map(run)) {
    const mean = traits.map((_, index) => meanOf(trips.map((trip) => trip[index])));
    const std = traits.map((_, index) =>
      Math.sqrt(meanOf(trips.map((trip) => (trip[index] - mean[index]) ** 2))) || 1);
    const scale = (raw) => raw.map((value, index) => (value - mean[index]) / std[index]);
    const data = trips.map(scale);
    const network = createNetwork(driver.seed * 7 + 1);
    const trainer = createTrainer(network, data);
    let threshold = null;

    const errorOf = (z) => {
      const output = forward(network, z).at(-1);
      return meanOf(z.map((value, index) => (value - output[index]) ** 2));
    };

    function train(epochs) {
      let loss;
      for (let count = 0; count < epochs; count += 1) loss = trainer.epoch();
      threshold = null;
      return loss;
    }

    /* The line above which a day no longer fits: 1.25 × the largest error among the driver's own past trips. */
    function limit() {
      if (threshold === null) threshold = Math.max(...data.map(errorOf)) * limitFactor;
      return threshold;
    }

    function assess(raw) {
      const z = scale(raw);
      const activations = forward(network, z);
      const output = activations.at(-1);
      const error = meanOf(z.map((value, index) => (value - output[index]) ** 2));
      const readings = traits.map((trait, index) => ({
        ...trait,
        value: raw[index],
        expected: output[index] * std[index] + mean[index],
        typical: mean[index],
        gap: z[index] - output[index],
      }));
      const reasons = readings.filter(({ gap }) => Math.abs(gap) >= reasonGap)
        .sort((a, b) => Math.abs(b.gap) - Math.abs(a.gap)).slice(0, 2);
      return { activations, error, threshold: limit(), unusual: error > limit(), readings, reasons, latent: activations[2] };
    }

    return {
      driver, trips, mean, std, network, train, assess, limit,
      get epochs() { return trainer.steps; },
      historyLatent: () => data.map((z) => forward(network, z)[2]),
    };
  }

  /*
   * Batch check on days the model never saw: ordinary days (false alarms) and days with a growing
   * reaction delay (misses). Each case is a full scripted drive. Fixed seed, so every reviewer
   * gets the same cases to replay.
   */
  const delayLevels = [0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4];
  const missFrom = 0.1;

  function planEvaluation(driver, { seed = 2026, normalDays = 300, perLevel = 40 } = {}) {
    const rng = random(seed + driver.seed);
    const day = () => tripParams(driver, gaussian(rng), gaussian(rng), rng);
    return [
      ...Array.from({ length: normalDays }, () => ({ kind: "평소", delay: 0, params: day() })),
      ...delayLevels.slice(1).flatMap((delay) => Array.from({ length: perLevel }, () => {
        const params = day();
        return { kind: "반응 지연", delay, params: { ...params, reaction: params.reaction + delay } };
      })),
    ];
  }

  function summarize(profile, cases) {
    const judged = cases.map((entry) => ({ ...entry, result: profile.assess(entry.vector) }));
    const normals = judged.filter(({ kind }) => kind === "평소");
    const delayed = judged.filter(({ kind }) => kind !== "평소");
    const falseAlarms = normals.filter(({ result }) => result.unusual);
    const due = delayed.filter(({ delay }) => delay >= missFrom);
    const misses = due.filter(({ result }) => !result.unusual);
    const sweep = delayLevels.map((delay) => {
      const group = delay === 0 ? normals : delayed.filter((entry) => entry.delay === delay);
      return { delay, rate: group.filter(({ result }) => result.unusual).length / group.length };
    });
    return {
      normalDays: normals.length, due: due.length, missFrom, sweep, falseAlarms, misses,
      falseAlarmRate: falseAlarms.length / normals.length,
      missRate: misses.length / due.length,
    };
  }

  function evaluate(profile, options) {
    return summarize(profile, planEvaluation(profile.driver, options)
      .map((entry) => ({ ...entry, vector: run(entry.params) })));
  }

  return {
    traits, drivers, days, sizes, historyTrips, delayLevels, reasonGap, run, planHistory, planEvaluation,
    createProfile, summarize, evaluate, forward,
  };
})();

if (typeof module !== "undefined") module.exports = DriveWatcherProfile;
