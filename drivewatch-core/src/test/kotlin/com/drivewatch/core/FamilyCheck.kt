package com.drivewatch.core

import com.drivewatch.core.card.Cards
import com.drivewatch.core.demo.DemoWeeklyHistory
import com.drivewatch.core.family.FamilyClient
import com.drivewatch.core.family.FamilyError
import com.drivewatch.core.family.FamilySession
import com.drivewatch.core.family.FamilySummary
import com.drivewatch.core.family.MemoryStorage
import com.drivewatch.core.family.ShareScope
import java.time.LocalDate

/**
 * 켜져 있는 가족 서버에 차량 쪽 흐름을 실제로 보내 본다: 등록 → 초대 코드 → 요약 보내기 → 가족 확인 기다리기.
 * 서버에 운전자 한 명이 새로 생긴다. 공개 서버가 아니라 로컬 서버에 쓴다.
 *   ./gradlew familyCheck -Pserver=http://127.0.0.1:8795 -Pwait=30
 * 공개 서버에는 닿는지만 본다(아무것도 쓰지 않는다):
 *   ./gradlew familyCheck -Pserver=https://drive.olvend.com -Pping=true
 */
object FamilyCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = args.getOrNull(0) ?: "http://127.0.0.1:8795"
        val waitSeconds = args.getOrNull(1)?.toIntOrNull() ?: 0
        if (args.getOrNull(2) == "true") return ping(server)
        val session = FamilySession(FamilyClient(server), MemoryStorage())

        val account = session.register("시연 운전자", ShareScope.EVIDENCE)
        println("registered ${account.driverId} scope=${account.shareScope.key}")
        val invite = session.invite()
        println("code ${invite.code}")
        println("join ${session.joinUrl(invite)}")

        // 합성 12주 기록('변화가 이어진 기록')이 오늘까지 끝나 있을 때의 주간 카드를 그대로 보낸다.
        val scene = DemoWeeklyHistory.scenes.first { it.id == "changing" }
        val trips = DemoWeeklyHistory.tripsFor(scene, DemoWeeklyHistory.firstMondayFor(LocalDate.now()))
        val card = Cards.weekly(WeeklyEngine.assess(trips, context = "city"))!!
        val receipt = session.send(FamilySummary.from(card, promise = "해 지면 운전 안 하기 · 이번 주 지킴"))
        println("sent week=${receipt.week} status=${receipt.status?.key} evidenceStored=${receipt.evidenceStored}")

        var view = session.view()
        repeat(waitSeconds) {
            if (view.latest?.ackedBy?.isNotEmpty() == true) return@repeat
            Thread.sleep(1000)
            view = session.view()
        }
        println("family ${view.family}")
        println("ackedBy ${view.latest?.ackedBy}")
    }

    /** 아무것도 쓰지 않고 서버에 닿는지만 본다. 없는 토큰으로 물어서 `unauthorized`가 오면 닿은 것이다. */
    private fun ping(server: String) {
        val error = runCatching { FamilyClient(server).me("no-such-token") }.exceptionOrNull() as? FamilyError
        println("ping $server -> ${error?.code} (${error?.status})")
        check(error?.code == "unauthorized") { "서버가 기대한 답을 주지 않았다" }
    }
}
