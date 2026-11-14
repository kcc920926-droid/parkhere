package com.drivewatch.core.family

import com.drivewatch.core.WeeklyStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate

/** 가족에게 보내는 범위. STATUS는 상태와 문장만, EVIDENCE는 숫자 근거까지. */
enum class ShareScope(val key: String) {
    STATUS("status"),
    EVIDENCE("evidence");

    companion object {
        fun of(key: String?): ShareScope = entries.firstOrNull { it.key == key } ?: STATUS
    }
}

/**
 * 서버가 거절했거나 서버에 닿지 못했다.
 * [code]는 서버의 오류 코드(`unauthorized`, `rate_limited`, `invalid_week` …)이고,
 * 닿지 못했으면 `offline`, 답을 읽지 못했으면 `bad_response`, 등록 전이면 `not_registered`다.
 */
class FamilyError(val code: String, val status: Int = 0, cause: Throwable? = null) : Exception("$code ($status)", cause) {
    /** 서버에 닿지 못했다. */
    val offline: Boolean get() = code == OFFLINE

    /** 같은 요청을 나중에 다시 보내면 될 수 있다: 닿지 못했거나, 서버가 잠시 못 받았거나, 요청이 너무 잦았다. */
    val retryable: Boolean get() = offline || status >= 500 || status == 429

    companion object {
        const val OFFLINE = "offline"
        const val BAD_RESPONSE = "bad_response"
        const val NOT_REGISTERED = "not_registered"
    }
}

/** 차 한 대의 등록 정보. [carToken]은 이 차만 알고 있어야 한다. */
data class CarAccount(val driverId: String, val carToken: String, val shareScope: ShareScope)

/** 가족이 폰에 넣을 초대 코드. 10분 동안 한 번만 쓸 수 있다. */
data class Invite(val code: String, val expiresAtMs: Long) {
    /** 화면에 띄울 모양. 예: "123 456" */
    val display: String get() = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
}

/** 마지막으로 보낸 요약과 그것을 확인한 가족. */
data class SentSummary(val week: LocalDate, val status: WeeklyStatus?, val sentAtMs: Long, val ackedBy: List<String>)

/** 차 화면의 가족 카드에 쓸 값. */
data class CarView(val name: String, val shareScope: ShareScope, val family: List<String>, val latest: SentSummary?)

data class SummaryReceipt(val week: LocalDate, val status: WeeklyStatus?, val evidenceStored: Int)

data class FamilyRequest(val method: String, val url: String, val token: String?, val body: String?)
data class FamilyResponse(val status: Int, val body: String)

/** 요청을 실제로 보내는 부분. 테스트에서는 바꿔 끼운다. */
fun interface FamilyTransport {
    @Throws(IOException::class)
    fun send(request: FamilyRequest): FamilyResponse
}

/** JDK와 Android에 모두 있는 HttpURLConnection으로 보낸다. */
class HttpTransport(private val connectTimeoutMs: Int = 5_000, private val readTimeoutMs: Int = 10_000) : FamilyTransport {
    override fun send(request: FamilyRequest): FamilyResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            request.token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            request.body?.let { body ->
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // 스트리밍 모드는 쓰지 않는다. JDK는 그 모드에서 401 답의 본문을 읽지 못해 오류 코드를 잃는다.
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return FamilyResponse(status, stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * 가족 요약 서버의 차량 쪽 API. 명세는 `<서버 주소>/api/docs`에 있다.
 * 모든 호출은 답이 올 때까지 기다린다. Android에서는 메인 스레드 밖에서 불러야 한다.
 */
class FamilyClient(baseUrl: String = PUBLIC_URL, private val transport: FamilyTransport = HttpTransport()) {
    val baseUrl: String = baseUrl.trimEnd('/')

    /** 운전자를 등록한다. 한 차에서 한 번만 부르고, 받은 값을 차에 저장해 둔다. */
    fun registerDriver(name: String, shareScope: ShareScope = ShareScope.STATUS): CarAccount {
        val data = call("POST", "/api/car/drivers", null, mapOf("name" to name, "shareScope" to shareScope.key))
        return CarAccount(data.text("driverId"), data.text("carToken"), ShareScope.of(data["shareScope"] as? String))
    }

    /** 가족이 폰에 넣을 코드를 새로 만든다. 이전 코드는 못 쓰게 된다. */
    fun createInvite(carToken: String): Invite {
        val data = call("POST", "/api/car/invites", carToken, emptyMap<String, Any?>())
        return Invite(data.text("code"), data.long("expiresAt"))
    }

    /** 가족이 폰으로 열 주소. QR에 담으면 코드가 미리 채워진다. */
    fun joinUrl(invite: Invite): String = "$baseUrl/family/?code=${invite.code}"

    fun setShareScope(carToken: String, shareScope: ShareScope): ShareScope {
        val data = call("PUT", "/api/car/share-scope", carToken, mapOf("shareScope" to shareScope.key))
        return ShareScope.of(data["shareScope"] as? String)
    }

    /** 같은 주에 다시 보내면 덮어쓰고, 가족의 확인 표시는 지워진다. */
    fun sendSummary(carToken: String, summary: FamilySummary): SummaryReceipt = sendSummaryJson(carToken, summary.toJson())

    /** 보내지 못해 저장해 둔 요약([FamilySummary.toJson])을 다시 보낼 때 쓴다. */
    fun sendSummaryJson(carToken: String, json: String): SummaryReceipt {
        val data = parse(request("POST", "/api/car/summaries", carToken, json))
        return SummaryReceipt(data.date("week"), data.status(), (data["evidenceStored"] as? Double)?.toInt() ?: 0)
    }

    /** 누가 연결됐는지, 마지막 요약을 누가 확인했는지. */
    fun me(carToken: String): CarView {
        val data = call("GET", "/api/car/me", carToken, null)
        val latest = (data["latest"] as? Map<*, *>)?.let {
            SentSummary(it.date("week"), it.status(), it.long("sentAt"), it.names("ackedBy") { name -> name as? String })
        }
        return CarView(
            name = data.text("name"),
            shareScope = ShareScope.of(data["shareScope"] as? String),
            family = data.names("family") { (it as? Map<*, *>)?.get("name") as? String },
            latest = latest,
        )
    }

    private fun call(method: String, path: String, token: String?, body: Map<String, Any?>?): Map<*, *> =
        parse(request(method, path, token, body?.let(Json::write)))

    private fun request(method: String, path: String, token: String?, body: String?): FamilyResponse {
        val response = try {
            transport.send(FamilyRequest(method, baseUrl + path, token, body))
        } catch (error: IOException) {
            throw FamilyError(FamilyError.OFFLINE, 0, error)
        }
        if (response.status !in 200..299) {
            val code = runCatching { (Json.parse(response.body) as? Map<*, *>)?.get("error") as? String }.getOrNull()
            throw FamilyError(code ?: FamilyError.BAD_RESPONSE, response.status)
        }
        return response
    }

    private fun parse(response: FamilyResponse): Map<*, *> = try {
        Json.parse(response.body) as Map<*, *>
    } catch (error: RuntimeException) {
        throw FamilyError(FamilyError.BAD_RESPONSE, response.status, error)
    }

    private fun missing(key: String): Nothing = throw FamilyError(FamilyError.BAD_RESPONSE, 200, IllegalStateException("$key 없음"))
    private fun Map<*, *>.text(key: String): String = this[key] as? String ?: missing(key)
    private fun Map<*, *>.long(key: String): Long = (this[key] as? Double)?.toLong() ?: missing(key)
    private fun Map<*, *>.date(key: String): LocalDate = runCatching { LocalDate.parse(text(key)) }.getOrElse { missing(key) }
    private fun Map<*, *>.status(): WeeklyStatus? = WeeklyStatus.entries.firstOrNull { it.key == this["status"] }
    private fun Map<*, *>.names(key: String, pick: (Any?) -> String?): List<String> =
        (this[key] as? List<*>).orEmpty().mapNotNull(pick)

    companion object {
        const val PUBLIC_URL = "https://drive.olvend.com"
    }
}
