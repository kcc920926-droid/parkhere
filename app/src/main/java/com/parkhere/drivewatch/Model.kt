package com.parkhere.drivewatch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Promises a driver can choose (C3). Only the first two change app behaviour in the MVP. */
val PROMISES = listOf(
    Ic.MOON to "해 지면 운전 안 하기",
    Ic.PIN to "표시한 구간 피하기",
    Ic.HIGHWAY to "고속도로 줄이기",
    Ic.TIMER to "1시간 넘게 연속 운전 안 하기",
    Ic.RAIN to "비 오는 날은 가까운 곳만",
)

/** One driver of the car. Records, promises and family links are per profile. */
data class Profile(
    val name: String,
    val trips: List<Trip>,
    val onboarded: Boolean = true,
    val shareScope: String = "status", // "status" | "evidence"
    val promises: Set<Int> = setOf(0, 1),
    val marked: Set<Pair<String, String>> = emptySet(), // destination to the point marked on the way there
    val avoidedThisWeek: Int = 0,
    val carToken: String? = null,
    val family: List<String> = emptyList(),
    val sentWeek: String? = null,
    val ackedBy: List<String> = emptyList(),
    val backup: Boolean? = null, // keep drive counts on the server? null = not asked yet
    /** Stable identity. Async callbacks find their driver by this, never by list position, which shifts. */
    val id: String = java.util.UUID.randomUUID().toString(),
)

/** A drive in progress or just finished. */
data class Drive(
    val place: String,
    val comfortable: Boolean,
    val started: Boolean = false,
    val finished: Boolean = false,
    val vehicle: Boolean = false,
    val log: List<String> = emptyList(),
    val reroutes: List<String> = emptyList(),
    val turns: Int = 0,
    val signals: Int = 0,
    val hardBrakes: Int = 0,
    val km: Double = 0.0,
    val banner: String? = null,
    val report: Report? = null,
    /** Set while a 시연 재생 plays this drive: the scenario title and where it came from. */
    val replay: String? = null,
)

enum class Screen { SIGNUP, CONSENT, PROMISES, INVITE, HOME, ROUTE, OTHER_WAY, DRIVE, ARRIVED, WEEKLY, DETAIL, MONTHLY, GRADUATE }

/** Single source of truth shared by the activity and the drive service (both on the main thread). */
object AppState {
    val profiles = mutableStateListOf(
        Profile(Demo.PARENT, emptyList(), onboarded = false),
        Profile("박영호", Demo.seed(), promises = setOf(0)),
    )
    var cur by mutableIntStateOf(0)
    var screen by mutableStateOf(Screen.SIGNUP)
    var drive by mutableStateOf<Drive?>(null)
    var night by mutableStateOf(false)
    var weekShift by mutableIntStateOf(0)
    var vehicleNote by mutableStateOf("차량 신호 확인 중")
    /** Show the "keep drive records on the server?" prompt (after consent). */
    var askBackup by mutableStateOf(false)
    /** Which history scenario the demo panel last applied (Scenarios.HISTORIES). */
    var historyId by mutableStateOf("steady")

    val profile get() = profiles[cur]
    fun update(i: Int = cur, f: (Profile) -> Profile) {
        val old = profiles[i]
        val new = f(old)
        profiles[i] = new
        Backup.changed(old, new)
    }

    /**
     * For callbacks that finish later (server answers, a drive ending): the driver they started for,
     * wherever it now sits in the list. If that driver was removed meanwhile, the change is dropped
     * instead of landing on whoever took its place.
     */
    fun updateId(id: String, f: (Profile) -> Profile) {
        val i = profiles.indexOfFirst { it.id == id }
        if (i >= 0) update(i, f) else runCatching { android.util.Log.w("DriveWatch", "dropped an update for a driver that is gone") }
    }

    fun byId(id: String) = profiles.firstOrNull { it.id == id }

    private fun pending(p: Profile) = !p.onboarded && p.name.isBlank()

    /** Switch driver. An unfinished "+ 운전자 추가" (no name yet) is dropped so it cannot hide the add button. */
    fun pick(i: Int) {
        val target = profiles[i]
        profiles.removeAll { it !== target && pending(it) }
        cur = profiles.indexOf(target)
        screen = if (target.onboarded) Screen.HOME else Screen.SIGNUP
        Backup.persist()
    }

    /** Start signing up a new driver, reusing an unfinished one if there is one. */
    fun addDriver() {
        val i = profiles.indexOfFirst(::pending).takeIf { it >= 0 }
            ?: run { profiles += Profile("", emptyList(), onboarded = false); profiles.lastIndex }
        cur = i
        screen = Screen.SIGNUP
    }

    /** Monday of the demo week, as the family server expects. */
    fun week(): String = LocalDate.now().plusWeeks(weekShift.toLong())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /** 시연 패널: move every profile one week on. */
    fun nextWeek() {
        weekShift++
        for (i in profiles.indices) update(i) { p ->
            p.copy(trips = p.trips.map { it.copy(weekAgo = it.weekAgo + 1) }, avoidedThisWeek = 0, ackedBy = emptyList())
        }
        Backup.persist()
    }

    /** 시연 패널: four weeks pass in which the current driver keeps re-routing on the 성당 route. */
    fun fourWeeksLater() {
        repeat(4) {
            nextWeek()
            if (profile.trips.isNotEmpty()) update { it.copy(trips = it.trips + Demo.strugglingWeek()) }
        }
    }

    /** 처음부터: forget everything, including what the server kept for these drivers. */
    fun reset() {
        profiles.forEach { p -> val t = p.carToken; if (p.backup == true && t != null) FamilyApi.setBackup(t, false) {} }
        Backup.clear()
        askBackup = false
        profiles.clear()
        profiles.addAll(listOf(Profile(Demo.PARENT, emptyList(), onboarded = false), Profile("박영호", Demo.seed(), promises = setOf(0))))
        cur = 0; screen = Screen.SIGNUP; drive = null; night = false; weekShift = 0
    }
}

fun weeksRecorded(p: Profile) = p.trips.maxOfOrNull { it.weekAgo + 1 } ?: 0

/** Server status key for a report state. */
fun State.key() = when (this) { State.LEARNING -> "learning"; State.STABLE -> "stable"; State.WATCH -> "observe"; State.TALK -> "talk" }

/** Short line said while driving. One per cause, at most two per drive. */
const val DRIVING_LINE = "천천히 가셔도 괜찮아요. 길 다시 안내할게요."

fun changeLine(m: Metric) = when (m) {
    Metric.REROUTE -> "요즘 익숙한 길에서 길 안내를 다시 받는 일이 늘었어요."
    Metric.SIGNAL -> "요즘 회전할 때 깜빡이를 놓치는 일이 늘었어요."
    Metric.BRAKE -> "요즘 급하게 멈추는 일이 늘었어요."
}

/** What the car says when parking. A change wins over praise; praise is the default. */
fun arrivalLine(d: Drive, r: Report): String {
    r.changed.firstOrNull()?.let { return changeLine(it.metric) + " 천천히 가셔도 괜찮아요." }
    return if (d.turns > 0 && d.signals == d.turns) "잘 다녀오셨어요. 오늘 깜빡이 다 잘 켜셨어요." else "잘 다녀오셨어요."
}

/** The junction the car suggests marking: where re-routes piled up in this drive. */
fun candidate(d: Drive): String? = d.reroutes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** Weekly card content (C10), also what is sent to the family server. */
data class Weekly(
    val state: State,
    val headline: String,
    val goodPoints: List<String>,
    val promise: String,
    val avoided: String,
    val changes: List<String>,
    val evidence: List<MetricReport>,
)

fun weekly(p: Profile, r: Report): Weekly {
    val good = r.metrics.filter { m -> m.weekly.first().let { it != null && m.baseline != null && it <= m.baseline } }.map {
        when (it.metric) {
            Metric.SIGNAL -> "깜빡이 다 잘 켜셨어요"
            Metric.BRAKE -> "급제동이 평소보다 적어요"
            Metric.REROUTE -> "익숙한 길을 잘 다니셨어요"
        }
    } + listOfNotNull("밤 운전 0회".takeIf { r.nightDrivesThisWeek == 0 })
    val promise = if (0 in p.promises) "해 지면 안 하기 · " + (if (r.nightDrivesThisWeek == 0) "이번 주 지킴" else "이번 주 ${r.nightDrivesThisWeek}번 밤 운전") else ""
    val avoided = p.marked.map { it.second }.distinct().joinToString(" · ").let {
        if (it.isEmpty()) "" else if (p.avoidedThisWeek > 0) "$it ${p.avoidedThisWeek}번 피했어요" else "$it 표시해 둠"
    }
    val changes = r.changed.map { changeLine(it.metric) }
    val headline = when (r.state) {
        State.LEARNING -> "평소 운전을 알아가는 중이에요"
        State.STABLE -> "이번 주도 평소처럼 운전하셨어요"
        else -> changes.first()
    }
    return Weekly(r.state, headline, good.take(3), promise, avoided, changes, r.changed)
}

// ---------- monthly record (월별 운전 기록) ----------

/** The report as it stood k weeks ago: trips from then and earlier, re-aged so that week is "this week". */
fun reportAt(trips: List<Trip>, k: Int): Report = analyze(trips.filter { it.weekAgo >= k }.map { it.copy(weekAgo = it.weekAgo - k) })

/** Monday of the week k weeks before the demo week. */
fun mondayOf(k: Int): LocalDate = LocalDate.parse(AppState.week()).minusWeeks(k.toLong())

data class WeekRow(val k: Int, val monday: LocalDate, val trips: Int, val km: Double, val night: Int, val report: Report)

/** Recorded weeks whose Monday falls in the given month, newest first. */
fun monthRows(p: Profile, month: YearMonth): List<WeekRow> =
    (0 until weeksRecorded(p)).filter { YearMonth.from(mondayOf(it)) == month }.map { k ->
        val week = p.trips.filter { it.weekAgo == k }
        WeekRow(k, mondayOf(k), week.size, week.sumOf { it.km }, week.count { it.night }, reportAt(p.trips, k))
    }

/** How many weeks in a row, up to now, the driver has been stable. */
fun stableWeeks(p: Profile): Int = (0 until weeksRecorded(p)).takeWhile { reportAt(p.trips, it).state == State.STABLE }.size

/**
 * What the car says about the driver's state and what could come next. The app never says
 * "give up your licence"; it says what it saw and offers steps. Stopping is the driver's choice (졸업).
 */
data class NextStep(val title: String, val body: String, val steps: List<String>)

fun nextStep(p: Profile, r: Report): NextStep = when (r.state) {
    State.LEARNING -> NextStep(
        "평소 운전을 알아가는 중이에요",
        "기록이 ${weeksRecorded(p)}주 쌓였어요. 12주가 되면 ${p.name} 님의 평소와 비교해 알려 드려요.",
        listOf("평소처럼 다니시면 돼요"),
    )
    State.STABLE -> {
        val weeks = stableWeeks(p)
        NextStep(
            "지난 ${weeks}주 동안 평소대로 잘 운전하고 계세요",
            "익숙한 길, 깜빡이, 급제동 모두 ${p.name} 님의 평소 범위 안이에요. 지금처럼 운전하셔도 좋아요.",
            listOf("약속 하나 더 정해 보기", "불편했던 곳이 있으면 표시해 두기"),
        )
    }
    State.WATCH -> NextStep(
        "${r.changed.first().metric.label}이 조금 달라졌어요",
        "2주째 평소와 다른 모습이 보여요. 큰일은 아니에요. 불편했던 길은 피해 가는 방법이 있어요.",
        listOf("표시한 구간은 편한 길로 다니기", "약속 하나 더 정해 보기"),
    )
    State.TALK -> NextStep(
        "몇 주째 평소와 다른 모습이 이어지고 있어요",
        "가족과 한번 이야기해 보시고, 공식 점검을 받아 보시는 걸 권해요. 결과가 괜찮으면 약속을 정하고 계속 운전하시면 돼요.",
        listOf("운전 적성검사 · 고령 운전자 교육 알아보기", "치매안심센터 선별검사 받아 보기", "가족과 한 번 같이 타 보기"),
    )
}
