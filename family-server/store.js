/* 가족 요약 서버의 규칙. HTTP와 무관하게 테스트할 수 있도록 분리했다.
   위치·경로는 받지 않는다. 요약은 아래에 적은 항목만 골라 저장한다. */
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const INVITE_MS = 10 * 60 * 1000;
const ATTEMPT_WINDOW_MS = 10 * 60 * 1000;
const MAX_FAILED_ATTEMPTS = 5;
const MAX_DRIVERS = 500;
const MAX_MEMBERS = 2000;
const STATUSES = ["learning", "insufficient", "stable", "observe", "talk"];
const SCOPES = ["status", "evidence"];
const MAX_TRIPS = 1000;

class StoreError extends Error {
  constructor(code, status) {
    super(code);
    this.code = code;
    this.status = status;
  }
}
const fail = (code, status = 400) => { throw new StoreError(code, status); };

const text = (value, max) => (typeof value === "string" ? value.trim().slice(0, max) : "");
const texts = (value, count, max) => (Array.isArray(value) ? value : [])
  .map((item) => text(item, max)).filter(Boolean).slice(0, count);
const number = (value) => (typeof value === "number" && Number.isFinite(value) ? Math.round(value * 10) / 10 : null);

function createStore({ now = () => Date.now(), file = null, maxDrivers = MAX_DRIVERS, maxMembers = MAX_MEMBERS } = {}) {
  let state = { drivers: {}, members: {}, invites: {} };
  if (file && fs.existsSync(file)) state = JSON.parse(fs.readFileSync(file, "utf8"));

  const save = () => {
    if (!file) return;
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(state));
  };
  const id = (prefix) => `${prefix}_${crypto.randomBytes(6).toString("hex")}`;
  const token = () => crypto.randomBytes(24).toString("base64url");
  const byToken = (group, key, value) => (typeof value === "string" && value
    ? Object.values(state[group]).find((entry) => entry[key] === value) ?? null : null);

  const latest = (driver) => {
    const weeks = Object.keys(driver.summaries).sort();
    return weeks.length ? driver.summaries[weeks.at(-1)] : null;
  };
  const linkOf = (member, driverId) => member.links.find((link) => link.driverId === driverId)
    ?? fail("not_linked", 403);

  /* 가족에게 보이는 요약. 운전자가 '상태만'을 골랐으면 숫자 근거는 내보내지 않는다. */
  const shown = (driver, summary, memberId) => summary && {
    week: summary.week,
    status: summary.status,
    headline: summary.headline,
    goodPoints: summary.goodPoints,
    promise: summary.promise,
    avoided: summary.avoided,
    changes: summary.changes,
    evidence: driver.shareScope === "evidence" ? summary.evidence : [],
    evidenceShared: driver.shareScope === "evidence",
    sentAt: summary.sentAt,
    acked: Boolean(summary.acks[memberId]),
  };

  return {
    /* ---- 차량 앱 ---- */
    registerDriver({ name, shareScope } = {}) {
      if (Object.keys(state.drivers).length >= maxDrivers) fail("capacity_reached", 503);
      const driver = {
        driverId: id("drv"), carToken: token(), name: text(name, 20) || fail("name_required"),
        shareScope: SCOPES.includes(shareScope) ? shareScope : "status", summaries: {},
      };
      state.drivers[driver.driverId] = driver;
      save();
      return { driverId: driver.driverId, carToken: driver.carToken, shareScope: driver.shareScope };
    },
    driverByToken: (value) => byToken("drivers", "carToken", value),

    setShareScope(driver, shareScope) {
      if (!SCOPES.includes(shareScope)) fail("invalid_scope");
      driver.shareScope = shareScope;
      save();
      return { shareScope };
    },

    /* 초대 코드는 운전자가 차에서 띄운다. 새 코드를 만들면 이전 코드는 못 쓴다. */
    createInvite(driver) {
      for (const [code, invite] of Object.entries(state.invites)) {
        if (invite.driverId === driver.driverId || invite.expiresAt <= now()) delete state.invites[code];
      }
      let code;
      do code = String(crypto.randomInt(0, 1000000)).padStart(6, "0"); while (state.invites[code]);
      state.invites[code] = { driverId: driver.driverId, expiresAt: now() + INVITE_MS };
      save();
      return { code, expiresAt: state.invites[code].expiresAt };
    },

    saveSummary(driver, body = {}) {
      const week = text(body.week, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(week) || Number.isNaN(Date.parse(`${week}T00:00:00Z`))) fail("invalid_week");
      if (!STATUSES.includes(body.status)) fail("invalid_status");
      const summary = {
        week,
        status: body.status,
        headline: text(body.headline, 120),
        goodPoints: texts(body.goodPoints, 3, 80),
        promise: text(body.promise, 80),
        avoided: text(body.avoided, 80),
        changes: texts(body.changes, 3, 120),
        evidence: [],
        sentAt: now(),
        acks: {},
      };
      if (driver.shareScope === "evidence") {
        summary.evidence = (Array.isArray(body.evidence) ? body.evidence : []).slice(0, 3).map((item) => ({
          label: text(item?.label, 40), unit: text(item?.unit, 20),
          typical: number(item?.typical), value: number(item?.value),
          weeks: Number.isInteger(item?.weeks) && item.weeks > 0 ? item.weeks : null,
        })).filter((item) => item.label && item.value !== null);
      }
      driver.summaries[week] = summary;
      save();
      return { week, status: summary.status, evidenceStored: summary.evidence.length };
    },

    /* ---- 주행 기록 보관 (운전자가 허락한 경우만) ----
       횟수만 받는다. 위치·경로·시각은 받지 않는다. 가족(폰)은 이 기록을 읽을 수 없다.
       보관을 끄면 서버에 있던 기록과 설정을 바로 지운다. */
    setBackup(driver, enabled) {
      if (typeof enabled !== "boolean") fail("invalid_backup");
      driver.backup = enabled;
      if (!enabled) { delete driver.trips; delete driver.profile; }
      save();
      return { enabled, trips: driver.trips?.length ?? 0 };
    },

    backupView(driver) {
      return { enabled: Boolean(driver.backup), profile: driver.profile ?? null, trips: driver.trips ?? [] };
    },

    saveTrip(driver, body = {}) {
      if (!driver.backup) fail("backup_off", 403);
      const week = text(body.week, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(week) || Number.isNaN(Date.parse(`${week}T00:00:00Z`))) fail("invalid_week");
      const count = (value) => (Number.isInteger(value) && value >= 0 && value <= 1000 ? value : fail("invalid_count"));
      const km = number(body.km);
      if (km === null || km < 0 || km > 2000) fail("invalid_km");
      const trip = {
        week,
        place: text(body.place, 20),
        km,
        night: Boolean(body.night),
        reroutes: count(body.reroutes),
        turns: count(body.turns),
        signals: count(body.signals),
        hardBrakes: count(body.hardBrakes),
      };
      if (trip.signals > trip.turns) fail("invalid_count");
      driver.trips = [...(driver.trips ?? []), trip].slice(-MAX_TRIPS);
      save();
      return { stored: driver.trips.length };
    },

    saveProfile(driver, body = {}) {
      if (!driver.backup) fail("backup_off", 403);
      const promises = (Array.isArray(body.promises) ? body.promises : [])
        .filter((value) => Number.isInteger(value) && value >= 0 && value < 10).slice(0, 10);
      const marked = (Array.isArray(body.marked) ? body.marked : []).slice(0, 20)
        .map((item) => ({ place: text(item?.place, 20), point: text(item?.point, 30) }))
        .filter((item) => item.place && item.point);
      driver.profile = { promises: [...new Set(promises)], marked };
      save();
      return driver.profile;
    },

    /* 차 화면의 가족 카드에 쓴다: 누가 연결됐는지, 최근 요약을 누가 확인했는지. */
    carView(driver) {
      const members = Object.values(state.members).filter((member) =>
        member.links.some((link) => link.driverId === driver.driverId));
      const summary = latest(driver);
      return {
        name: driver.name,
        shareScope: driver.shareScope,
        family: members.map((member) => ({ name: member.name })),
        latest: summary && {
          week: summary.week, status: summary.status, sentAt: summary.sentAt,
          ackedBy: members.filter((member) => summary.acks[member.memberId]).map((member) => member.name),
        },
      };
    },

    /* ---- 가족(폰) ---- */
    registerMember({ name } = {}) {
      if (Object.keys(state.members).length >= maxMembers) fail("capacity_reached", 503);
      const member = {
        memberId: id("mem"), token: token(), name: text(name, 20) || fail("name_required"),
        links: [], failures: [],
      };
      state.members[member.memberId] = member;
      save();
      return { memberId: member.memberId, token: member.token, name: member.name };
    },
    memberByToken: (value) => byToken("members", "token", value),

    link(member, { code, relation } = {}) {
      member.failures = member.failures.filter((at) => at > now() - ATTEMPT_WINDOW_MS);
      if (member.failures.length >= MAX_FAILED_ATTEMPTS) fail("too_many_attempts", 429);
      const key = text(String(code ?? ""), 12).replace(/\D/g, "");
      const invite = state.invites[key];
      if (!invite || invite.expiresAt <= now()) {
        if (invite) delete state.invites[key];
        member.failures.push(now());
        save();
        fail(invite ? "expired_code" : "invalid_code", 404);
      }
      const driver = state.drivers[invite.driverId];
      delete state.invites[key];
      const label = text(relation, 10) || "부모님";
      const existing = member.links.find((link) => link.driverId === driver.driverId);
      if (existing) existing.relation = label;
      else member.links.push({ driverId: driver.driverId, relation: label, linkedAt: now() });
      save();
      return { driverId: driver.driverId, name: driver.name, relation: label };
    },

    overview(member) {
      return member.links.map((link) => {
        const driver = state.drivers[link.driverId];
        const summary = latest(driver);
        return {
          driverId: driver.driverId, name: driver.name, relation: link.relation,
          latest: summary && {
            week: summary.week, status: summary.status, sentAt: summary.sentAt,
            acked: Boolean(summary.acks[member.memberId]),
          },
        };
      });
    },

    summaryFor(member, driverId) {
      const link = linkOf(member, driverId);
      const driver = state.drivers[driverId];
      return { driverId, name: driver.name, relation: link.relation, summary: shown(driver, latest(driver), member.memberId) };
    },

    /* 월별 화면: 운전자가 보낸 주간 요약들. 최근 26주까지, 최신 순. 보내는 범위 규칙은 한 주 요약과 같다. */
    summariesFor(member, driverId) {
      const link = linkOf(member, driverId);
      const driver = state.drivers[driverId];
      const weeks = Object.keys(driver.summaries).sort().reverse().slice(0, 26);
      return {
        driverId, name: driver.name, relation: link.relation,
        summaries: weeks.map((week) => shown(driver, driver.summaries[week], member.memberId)),
      };
    },

    ack(member, driverId, week) {
      linkOf(member, driverId);
      const summary = state.drivers[driverId].summaries[text(week, 10)] ?? fail("no_summary", 404);
      summary.acks[member.memberId] = now();
      save();
      return { week: summary.week, acked: true };
    },
  };
}

module.exports = { createStore, StoreError, INVITE_MS, MAX_FAILED_ATTEMPTS, STATUSES, SCOPES };
