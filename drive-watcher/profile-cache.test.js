const test = require("node:test");
const assert = require("node:assert/strict");
const cache = require("./profile-cache.js");
const { drivers, run, planHistory, planEvaluation } = require("./profile-model.js");

test("the pre-driven cache matches live drives on the same seeds", () => {
  for (const driver of drivers) {
    const history = planHistory(driver);
    const evaluation = planEvaluation(driver);
    assert.equal(cache[driver.id].history.length, history.length);
    assert.equal(cache[driver.id].evaluation.length, evaluation.length);
    for (const index of [0, 1, history.length - 1]) {
      assert.deepEqual(cache[driver.id].history[index], run(history[index]));
    }
    for (const index of [0, 299, 300, evaluation.length - 1]) {
      assert.deepEqual(cache[driver.id].evaluation[index], run(evaluation[index].params));
    }
  }
});
