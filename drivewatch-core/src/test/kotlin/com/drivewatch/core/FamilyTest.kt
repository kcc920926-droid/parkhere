package com.drivewatch.core

import com.drivewatch.core.card.Cards
import com.drivewatch.core.card.EvidenceLine
import com.drivewatch.core.demo.DemoWeeklyHistory
import com.drivewatch.core.family.FamilyClient
import com.drivewatch.core.family.FamilyError
import com.drivewatch.core.family.FamilyRequest
import com.drivewatch.core.family.FamilyResponse
import com.drivewatch.core.family.FamilySession
import com.drivewatch.core.family.FamilySummary
import com.drivewatch.core.family.FamilyTransport
import com.drivewatch.core.family.HttpTransport
import com.drivewatch.core.family.Invite
import com.drivewatch.core.family.MemoryStorage
import com.drivewatch.core.family.ShareScope
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyTest {
    private val today = LocalDate.parse("2026-12-03")
    private fun summaryAt(clock: LocalDate): FamilySummary {
        val scene = DemoWeeklyHistory.scenes.first { it.id == "changing" }
        val trips = DemoWeeklyHistory.tripsFor(scene, DemoWeeklyHistory.firstMondayFor(today, completedWeeks = 8))
        val card = Cards.weekly(WeeklyEngine.assess(trips.filter { !it.date.isAfter(clock) }, context = "city"))!!
        return FamilySummary.from(card, promise = "해 지면 운전 안 하기 · 이번 주 지킴")
    }

    /** 요청을 기록하고, 준비해 둔 답을 차례로 돌려준다. */
    private class FakeServer(vararg replies: Any) : FamilyTransport {
        val requests = mutableListOf<FamilyRequest>()
        private val replies = replies.toMutableList()
        override fun send(request: FamilyRequest): FamilyResponse {
            requests += request
            return when (val reply = replies.removeAt(0)) {
                is IOException -> throw reply
                is FamilyResponse -> reply
                else -> FamilyResponse(200, reply as String)
            }
        }
    }

    private val registered = """{"driverId":"drv_1","carToken":"tok-1","shareScope":"evidence"}"""
    private val saved = """{"week":"2026-11-30","status":"talk","evidenceStored":2}"""

    @Test
    fun `a stable week is sent as one line without numbers`() {
        val summary = summaryAt(today)
        assertEquals(WeeklyStatus.STABLE, summary.status)
        assertEquals("이번 주도 평소처럼 운전하셨어요", summary.headline)
        assertEquals(emptyList(), summary.changes)
        assertEquals(emptyList(), summary.evidence)
    }

    @Test
    fun `a talk week carries the same sentence and numbers the car showed`() {
        val summary = summaryAt(today.plusWeeks(4))
        assertEquals(WeeklyStatus.TALK, summary.status)
        assertEquals("", summary.headline)
        assertEquals(listOf("강한 제동이 4주째, 조향 보정이 2주째 평소보다 많았어요"), summary.changes)
        assertEquals(
            listOf(EvidenceLine("강한 제동", "회/100km", 6.3, 21.9, 4), EvidenceLine("조향 보정", "회/100km", 37.5, 59.4, 2)),
            summary.evidence,
        )
        assertEquals(DayOfWeekCheck.monday(summary.week), summary.week)
        assertEquals(
            """{"week":"${summary.week}","status":"talk","headline":"","goodPoints":["쉬지 않고 운전한 시간은 평소 범위였어요"],""" +
                """"promise":"해 지면 운전 안 하기 · 이번 주 지킴","avoided":"",""" +
                """"changes":["강한 제동이 4주째, 조향 보정이 2주째 평소보다 많았어요"],""" +
                """"evidence":[{"label":"강한 제동","unit":"회/100km","typical":6.3,"value":21.9,"weeks":4},""" +
                """{"label":"조향 보정","unit":"회/100km","typical":37.5,"value":59.4,"weeks":2}]}""",
            summary.toJson(),
        )
    }

    private object DayOfWeekCheck {
        fun monday(date: LocalDate): LocalDate = WeeklyEngine.weekOf(date)
    }

    @Test
    fun `the summary has no place for location or routes`() {
        val keys = Regex("\"(\\w+)\":").findAll(summaryAt(today.plusWeeks(4)).toJson()).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("week", "status", "headline", "goodPoints", "promise", "avoided", "changes", "evidence", "label", "unit", "typical", "value", "weeks"),
            keys,
        )
    }

    @Test
    fun `text with quotes and line breaks survives the trip`() {
        val server = FakeServer(saved)
        val summary = FamilySummary(LocalDate.parse("2026-11-30"), WeeklyStatus.STABLE, headline = "\"괜찮았어요\"\n다음 주에 봬요 \\ 🚗")
        FamilyClient("http://car.test", server).sendSummary("tok-1", summary)
        assertTrue(server.requests.single().body!!.contains(""""headline":"\"괜찮았어요\"\n다음 주에 봬요 \\ 🚗""""))
    }

    @Test
    fun `car calls go to the documented routes with the car token`() {
        val server = FakeServer(
            registered,
            """{"code":"042917","expiresAt":1796256000000}""",
            """{"shareScope":"status"}""",
            saved,
            """{"name":"김정숙","shareScope":"status","family":[{"name":"민지"},{"name":"준호"}],
               "latest":{"week":"2026-11-30","status":"talk","sentAt":1796256001234,"ackedBy":["민지"]}}""",
        )
        val client = FamilyClient("http://car.test/", server)

        val account = client.registerDriver("김정숙", ShareScope.EVIDENCE)
        assertEquals("tok-1", account.carToken)
        assertEquals(ShareScope.EVIDENCE, account.shareScope)

        val invite = client.createInvite(account.carToken)
        assertEquals(Invite("042917", 1796256000000), invite)
        assertEquals("042 917", invite.display)
        assertEquals("http://car.test/family/?code=042917", client.joinUrl(invite))

        assertEquals(ShareScope.STATUS, client.setShareScope(account.carToken, ShareScope.STATUS))
        assertEquals(2, client.sendSummary(account.carToken, summaryAt(today.plusWeeks(4))).evidenceStored)

        val view = client.me(account.carToken)
        assertEquals(listOf("민지", "준호"), view.family)
        assertEquals(listOf("민지"), view.latest!!.ackedBy)
        assertEquals(WeeklyStatus.TALK, view.latest!!.status)
        assertEquals(1796256001234, view.latest!!.sentAtMs)

        assertEquals(
            listOf(
                "POST http://car.test/api/car/drivers -",
                "POST http://car.test/api/car/invites tok-1",
                "PUT http://car.test/api/car/share-scope tok-1",
                "POST http://car.test/api/car/summaries tok-1",
                "GET http://car.test/api/car/me tok-1",
            ),
            server.requests.map { "${it.method} ${it.url} ${it.token ?: "-"}" },
        )
        assertEquals("""{"name":"김정숙","shareScope":"evidence"}""", server.requests[0].body)
        assertNull(server.requests[4].body)
    }

    @Test
    fun `a car with no family and no summary yet reads as empty`() {
        val view = FamilyClient("http://car.test", FakeServer("""{"name":"김정숙","shareScope":"status","family":[],"latest":null}""")).me("tok-1")
        assertEquals(emptyList(), view.family)
        assertNull(view.latest)
    }

    @Test
    fun `server refusals, unreachable servers and unreadable answers are told apart`() {
        fun failure(reply: Any) = assertFailsWith<FamilyError> { FamilyClient("http://car.test", FakeServer(reply)).me("tok-1") }

        val refused = failure(FamilyResponse(401, """{"error":"unauthorized"}"""))
        assertEquals("unauthorized" to 401, refused.code to refused.status)
        assertFalse(refused.retryable)

        val unreachable = failure(IOException("no route"))
        assertTrue(unreachable.offline)
        assertTrue(unreachable.retryable)

        val gateway = failure(FamilyResponse(502, "<html>Bad gateway</html>"))
        assertEquals(FamilyError.BAD_RESPONSE to 502, gateway.code to gateway.status)
        assertTrue(gateway.retryable)

        assertTrue(failure(FamilyResponse(429, """{"error":"rate_limited"}""")).retryable)
        assertEquals(FamilyError.BAD_RESPONSE, failure("not json").code)
        assertEquals(FamilyError.BAD_RESPONSE, failure("""{"shareScope":"status"}""").code)
    }

    @Test
    fun `requests really go out over HTTP as UTF-8 JSON`() {
        val seen = mutableListOf<String>()
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            seen += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")} " +
                "${exchange.requestHeaders.getFirst("Content-Type")} $body"
            val (status, reply) = if (exchange.requestURI.path.endsWith("/invites")) 401 to """{"error":"unauthorized"}""" else 200 to registered
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        try {
            val client = FamilyClient("http://127.0.0.1:${http.address.port}", HttpTransport())
            assertEquals("drv_1", client.registerDriver("김정숙").driverId)
            assertEquals("unauthorized", assertFailsWith<FamilyError> { client.createInvite("wrong") }.code)
        } finally {
            http.stop(0)
        }
        assertEquals(
            listOf(
                """POST /api/car/drivers null application/json; charset=utf-8 {"name":"김정숙","shareScope":"status"}""",
                """POST /api/car/invites Bearer wrong application/json; charset=utf-8 {}""",
            ),
            seen,
        )
        val closed = FamilyClient("http://127.0.0.1:${http.address.port}", HttpTransport(connectTimeoutMs = 500))
        assertTrue(assertFailsWith<FamilyError> { closed.me("tok-1") }.offline)
    }

    @Test
    fun `the car registers once and remembers it`() {
        val server = FakeServer(registered)
        val storage = MemoryStorage()
        val session = FamilySession(FamilyClient("http://car.test", server), storage)
        assertNull(session.account)
        assertEquals(FamilyError.NOT_REGISTERED, assertFailsWith<FamilyError> { session.invite() }.code)

        session.register("김정숙", ShareScope.EVIDENCE)
        session.register("김정숙", ShareScope.EVIDENCE)
        assertEquals(1, server.requests.size)

        val reopened = FamilySession(FamilyClient("http://car.test", FakeServer()), storage)
        assertEquals("tok-1", reopened.account!!.carToken)
        assertEquals(ShareScope.EVIDENCE, reopened.account!!.shareScope)

        assertNull(FamilySession(FamilyClient("http://other.test", FakeServer()), storage).account)

        reopened.forget()
        assertNull(reopened.account)
    }

    @Test
    fun `a changed share scope is remembered`() {
        val storage = MemoryStorage()
        val session = FamilySession(FamilyClient("http://car.test", FakeServer(registered, """{"shareScope":"status"}""")), storage)
        session.register("김정숙", ShareScope.EVIDENCE)
        session.setShareScope(ShareScope.STATUS)
        assertEquals(ShareScope.STATUS, session.account!!.shareScope)
    }

    @Test
    fun `a summary that could not be sent waits and goes out later`() {
        val server = FakeServer(registered, IOException("tunnel"), FamilyResponse(502, "<html>"), saved)
        val session = FamilySession(FamilyClient("http://car.test", server), MemoryStorage())
        session.register("김정숙", ShareScope.EVIDENCE)
        assertFalse(session.hasPending)
        assertNull(session.retryPending())

        val summary = summaryAt(today.plusWeeks(4))
        assertTrue(assertFailsWith<FamilyError> { session.send(summary) }.offline)
        assertTrue(session.hasPending)

        assertEquals(502, assertFailsWith<FamilyError> { session.retryPending() }.status)
        assertTrue(session.hasPending)

        assertEquals(2, session.retryPending()!!.evidenceStored)
        assertFalse(session.hasPending)
        assertEquals(summary.toJson(), server.requests.last().body)
        assertNull(session.retryPending())
    }

    @Test
    fun `a summary the server refuses is not kept`() {
        val server = FakeServer(registered, FamilyResponse(400, """{"error":"invalid_week"}"""))
        val session = FamilySession(FamilyClient("http://car.test", server), MemoryStorage())
        session.register("김정숙")
        assertEquals("invalid_week", assertFailsWith<FamilyError> { session.send(summaryAt(today)) }.code)
        assertFalse(session.hasPending)
    }
}
