/* 가족 초대 링크 문자. 번호는 보내는 데만 쓰고 저장하지 않는다(기록 파일·로그 모두).
   SMS_PROVIDER=solapi 와 키가 있으면 SOLAPI로 실제 발송하고, 없으면 보내지 않는 시연 모드(dry-run)다.
   SOLAPI 규격: https://solapi.com/developers/api/authentication-api-key ,
   https://nurigo.gitbook.io/solapi/api-reference/messages/sendmany */
const crypto = require("node:crypto");
const https = require("node:https");

/* 국내 휴대폰 번호만 받는다. 하이픈·공백·+82는 허용하고 숫자만 남긴다. */
function mobile(value) {
  const digits = String(value ?? "").replace(/^\s*\+82/, "0").replace(/\D/g, "");
  return /^01[016789]\d{7,8}$/.test(digits) ? digits : null;
}

/* 화면과 로그에는 가운데를 가린다: 010-****-5678 */
const mask = (digits) => `${digits.slice(0, 3)}-****-${digits.slice(-4)}`;

function inviteText(driverName, link) {
  return `[DriveWatch] ${driverName} 님이 가족 연결을 요청했어요. 10분 안에 눌러 주세요.\n${link}`;
}

/* SOLAPI 인증 헤더: HMAC-SHA256(date + salt, API Secret)을 16진수로. 요청마다 새 salt. */
function solapiAuthorization(apiKey, apiSecret, date = new Date().toISOString(), salt = crypto.randomBytes(16).toString("hex")) {
  const signature = crypto.createHmac("sha256", apiSecret).update(date + salt).digest("hex");
  return `HMAC-SHA256 apiKey=${apiKey}, date=${date}, salt=${salt}, signature=${signature}`;
}

function postJson(url, headers, body) {
  return new Promise((resolve, reject) => {
    const request = https.request(url, { method: "POST", headers: { "content-type": "application/json", ...headers }, timeout: 8000 }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        let data = null;
        try { data = JSON.parse(Buffer.concat(chunks).toString("utf8")); } catch { /* 본문이 JSON이 아니어도 상태 코드로 판단한다 */ }
        resolve({ status: response.statusCode, data });
      });
    });
    request.on("timeout", () => request.destroy(new Error("timeout")));
    request.on("error", reject);
    request.end(JSON.stringify(body));
  });
}

/* env: SMS_PROVIDER, SOLAPI_API_KEY, SOLAPI_API_SECRET, SMS_FROM(사전 등록한 발신번호).
   post는 테스트에서 바꿔 끼운다. */
function createSender(env = process.env, { post = postJson, log = console.log } = {}) {
  const from = mobile(env.SMS_FROM) ?? String(env.SMS_FROM ?? "").replace(/\D/g, "");
  const live = env.SMS_PROVIDER === "solapi" && env.SOLAPI_API_KEY && env.SOLAPI_API_SECRET && from;
  if (!live) {
    return {
      mode: "dry-run",
      async send(to, text) {
        log(`[sms dry-run] to ${mask(to)} · ${text.replace(/\n/g, " ")}`);
        return { sent: false, mode: "dry-run" };
      },
    };
  }
  return {
    mode: "solapi",
    async send(to, text) {
      const { status, data } = await post("https://api.solapi.com/messages/v4/send-many", {
        authorization: solapiAuthorization(env.SOLAPI_API_KEY, env.SOLAPI_API_SECRET),
      }, { messages: [{ to, from, text, autoTypeDetect: true }] });
      if (status < 200 || status >= 300) {
        log(`[sms solapi] failed for ${mask(to)}: ${status} ${data?.errorCode ?? ""}`);
        return { sent: false, mode: "solapi", failed: true };
      }
      return { sent: true, mode: "solapi" };
    },
  };
}

module.exports = { mobile, mask, inviteText, solapiAuthorization, createSender };
