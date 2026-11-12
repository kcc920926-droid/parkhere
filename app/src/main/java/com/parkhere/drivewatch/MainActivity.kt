package com.parkhere.drivewatch

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

private val Bg = Color(0xFF0F1720)
private val Panel = Color(0xFF18232F)
private val Raised = Color(0xFF223040)
private val Dim = Color(0xFF8AA0B5)
private val Ok = Color(0xFF5FD38D)
private val Warn = Color(0xFFF5B85C)
private val Talk = Color(0xFFFF8A5C)
private val Accent = Color(0xFF5FA8FF)
private val Demo_ = Color(0xFFD9C7FF)

class MainActivity : ComponentActivity() {
    private var tts: Speaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = Speaker(this)
        Backup.init(this)
        val wanted = listOfNotNull(VehicleLink.SPEED_PERMISSION, "android.permission.POST_NOTIFICATIONS".takeIf { Build.VERSION.SDK_INT >= 33 })
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
        setContent { Scaled { App(speak = { tts?.speak(it) }) } }
    }

    override fun onDestroy() {
        tts?.release()
        super.onDestroy()
    }
}

/** Same layout on a phone emulator (≈900 dp wide) and the Pleos display (2560 dp): scale to a 1000 dp canvas. */
@Composable
private fun Scaled(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val scale = LocalConfiguration.current.screenWidthDp / 1000f
    CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) { content() }
}

@Composable
private fun App(speak: (String) -> Unit) {
    val s = AppState
    val p = s.profile
    val report = remember(p.trips) { analyze(p.trips) }
    var demoOpen by remember { mutableStateOf(false) }

    // Keep the family card current: who linked, who acknowledged.
    LaunchedEffect(s.cur, p.carToken, s.screen) {
        val token = p.carToken ?: return@LaunchedEffect
        while (true) {
            val i = s.cur
            FamilyApi.me(token) { family, acked, week ->
                s.update(i) { it.copy(family = family, ackedBy = if (week == it.sentWeek) acked else emptyList()) }
            }
            delay(4_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopBar(locked = s.screen == Screen.DRIVE && s.drive?.finished == false, demoOpen = demoOpen, onDemo = { demoOpen = !demoOpen })
            if (demoOpen) DemoPanel()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (s.screen) {
                    Screen.SIGNUP -> Signup()
                    Screen.CONSENT -> Consent(speak)
                    Screen.PROMISES -> Promises()
                    Screen.INVITE -> Invite()
                    Screen.HOME -> Home(report, speak)
                    Screen.ROUTE -> Route()
                    Screen.OTHER_WAY -> OtherWay()
                    Screen.DRIVE -> Driving()
                    Screen.ARRIVED -> Arrived()
                    Screen.WEEKLY -> WeeklyCard(report)
                    Screen.DETAIL -> Detail(report)
                    Screen.MONTHLY -> Monthly(report)
                    Screen.GRADUATE -> Graduate()
                }
                if (s.askBackup) BackupPrompt()
            }
        }
    }
}

// ---------- C0 top bar and demo panel ----------

@Composable
private fun TopBar(locked: Boolean, demoOpen: Boolean, onDemo: () -> Unit) {
    val s = AppState
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DriveWatch", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        s.profiles.forEachIndexed { i, p ->
            if (p.name.isNotBlank() || i == s.cur) Chip("👤 ${p.name.ifBlank { "새 운전자" }}", selected = i == s.cur, enabled = !locked) { s.pick(i) }
        }
        if (locked) Text("주행 중에는 운전자를 바꿀 수 없어요", color = Dim, fontSize = 12.sp)
        else Chip("+ 운전자 추가", selected = false) { s.addDriver() }
        Spacer(Modifier.weight(1f))
        Text(s.vehicleNote, color = Dim, fontSize = 11.sp)
        if (!locked) Text(
            if (demoOpen) "시연 패널 ▴" else "시연 패널 ▾", color = Demo_, fontSize = 12.sp,
            modifier = Modifier.border(1.dp, Color(0xFF8C6BD9), RoundedCornerShape(8.dp)).clickable(onClick = onDemo).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DemoPanel() {
    val s = AppState
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2A2040), RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("시연용", color = Demo_, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Button(if (s.night) "☀️ 낮으로" else "🌙 밤 9시로", { s.night = !s.night }, Color(0xFF3B2E5C))
        Button("다음 주로", { s.nextWeek() }, Color(0xFF3B2E5C))
        Button("4주 뒤로", { s.fourWeeksLater() }, Color(0xFF3B2E5C))
        // Fills the family page's monthly view in one go; each week is still a summary the parent chose to send.
        Button("최근 4주 요약 보내기", {
            val p = s.profile
            val token = p.carToken ?: return@Button
            if (p.family.isEmpty()) return@Button
            (0 until minOf(4, weeksRecorded(p))).forEach { k ->
                FamilyApi.sendSummary(token, mondayOf(k).toString(), weekly(p, reportAt(p.trips, k))) {}
            }
            s.update { it.copy(sentWeek = s.week(), ackedBy = emptyList()) }
        }, Color(0xFF3B2E5C))
        Spacer(Modifier.weight(1f))
        Text("${s.week()} 주 · 자녀 폰: ${FamilyApi.FAMILY_PAGE}", color = Demo_, fontSize = 12.sp)
        Button("↺ 처음부터", { s.reset() }, Color(0xFF3B2E5C))
    }
}

// ---------- C1–C4 onboarding ----------

@Composable
private fun Signup() {
    val s = AppState
    var name by remember(s.cur) { mutableStateOf(s.profile.name.ifBlank { if (s.cur == 0) Demo.PARENT else "" }) }
    var tel by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text("운전자 등록", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth(0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("이름", name, { name = it })
            Field("휴대폰 번호", tel, { tel = it }, number = true)
            Text("🔒 기록은 연결한 가족에게만 보여요. 위치는 보내지 않아요.", color = Dim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.profile.name.isBlank() && s.profiles.any { it.onboarded }) Button("취소", { // only for "+ 운전자 추가"
                    s.profiles.removeAt(s.cur); s.cur = s.profiles.indexOfFirst { it.onboarded }; s.screen = Screen.HOME
                }, Raised)
                Button("다음", {
                    if (name.isBlank()) return@Button
                    val n = name.trim()
                    // 시연용: the demo parent comes with 11 weeks of synthetic history so later scenes have a baseline.
                    s.update { it.copy(name = n, trips = if (n == Demo.PARENT) Demo.seed() else emptyList()) }
                    s.screen = Screen.CONSENT
                }, if (name.isNotBlank()) Accent else Raised, Modifier.weight(1f))
            }
        }
    }
}

private const val CONSENT_SPEECH = "DriveWatch는 익숙한 길에서 길 안내를 다시 받은 횟수, 회전할 때 깜빡이, 급제동, 밤 운전을 기록해요. 위치와 지나간 경로, 카메라 영상, 음주 여부는 기록하지 않아요. 언제든 끌 수 있어요."

@Composable
private fun Consent(speak: (String) -> Unit) {
    val s = AppState
    var scope by remember { mutableStateOf(s.profile.shareScope) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DriveWatch가 기록하는 것", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("✓ 기록해요", color = Ok, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                listOf("익숙한 길에서 길 안내를 다시 받은 횟수", "회전할 때 깜빡이", "급제동", "밤 운전").forEach { Text(it, color = Color.White, fontSize = 15.sp) }
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("✗ 기록하지 않아요", color = Color(0xFFFF8A8A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                listOf("위치와 지나간 경로", "카메라 영상", "음주 여부").forEach { Text(it, color = Color.White, fontSize = 15.sp) }
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("가족에게 보낼 내용", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Option("상태만", "안정 · 관찰 · 대화 권장", scope == "status") { scope = "status" }
                Option("상태와 근거", "\"재탐색 0.2 → 0.9회\" 같은 숫자 포함", scope == "evidence") { scope = "evidence" }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("언제든 설정에서 끌 수 있어요", color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Button("🔊 읽어 주기", { speak(CONSENT_SPEECH) }, Raised)
            Button(if (busy) "연결 중…" else "동의하고 시작", {
                if (busy) return@Button
                busy = true
                val i = s.cur
                s.update { it.copy(shareScope = scope) }
                FamilyApi.registerDriver(s.profile.name, scope) { token ->
                    s.update(i) { it.copy(carToken = token) }
                    s.askBackup = token != null // no server reachable: nothing to ask, records stay in the car
                    s.screen = Screen.PROMISES
                }
            })
        }
    }
}

@Composable
private fun Promises() {
    val s = AppState
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("지키고 싶은 약속을 골라 주세요", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("0개도 괜찮아요", color = Dim, fontSize = 14.sp)
        }
        val rows = (PROMISES.indices.toList() + -1).chunked(3)
        rows.forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    if (i < 0) Column(Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("어겨도 막지 않아요.\n기록하고 다음에 다시 물어봐요.", color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                    } else {
                        val on = i in s.profile.promises
                        Row(
                            Modifier.weight(1f).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp))
                                .border(1.dp, if (on) Accent else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { s.update { it.copy(promises = if (on) it.promises - i else it.promises + i) } }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(PROMISES[i].first, fontSize = 24.sp)
                            Text(PROMISES[i].second, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Toggle(on)
                        }
                    }
                }
            }
        }
        Row { Spacer(Modifier.weight(1f)); Button("다음 · 가족 연결", { s.update { it.copy(onboarded = true) }; s.screen = Screen.INVITE }) }
    }
}

@Composable
private fun Invite() {
    val s = AppState
    val p = s.profile
    var code by remember(s.cur) { mutableStateOf<String?>(null) }
    var failed by remember(s.cur) { mutableStateOf(false) }
    LaunchedEffect(s.cur) {
        val i = s.cur
        fun ask(token: String) = FamilyApi.invite(token) { code = it; failed = it == null }
        val token = p.carToken
        if (token != null) ask(token)
        else FamilyApi.registerDriver(p.name, p.shareScope) { t -> if (t == null) failed = true else { s.update(i) { it.copy(carToken = t) }; ask(t) } }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        if (p.family.isNotEmpty()) {
            Text("✓ ${p.family.joinToString()} 님과 연결됐어요", color = Ok, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${p.name} 님이 보내기로 한 요약만 가족이 볼 수 있어요", color = Dim)
        } else {
            Text("자녀분 폰에서 이 코드를 입력하세요", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(code?.chunked(3)?.joinToString(" ") ?: if (failed) "연결할 수 없어요" else "코드 만드는 중…",
                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text("폰 브라우저에서 ${FamilyApi.FAMILY_PAGE} · 10분 동안 유효 · 연결을 기다리는 중…", color = Dim, fontSize = 13.sp)
        }
        Button(if (p.family.isNotEmpty()) "홈으로" else "나중에 연결", { s.screen = Screen.HOME }, Raised)
    }
}

// ---------- C5 home ----------

@Composable
private fun Home(r: Report, speak: (String) -> Unit) {
    val s = AppState
    val p = s.profile
    val ctx = LocalContext.current
    val nightPromise = s.night && 0 in p.promises
    LaunchedEffect(nightPromise, s.cur) { if (nightPromise) speak(NIGHT_LINE) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${p.name} 님, 안녕하세요", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (p.name == Demo.PARENT) Text("과거 기록은 시연용  ", color = Demo_, fontSize = 11.sp)
            Chip("📅 월별 기록", selected = false) { s.screen = Screen.MONTHLY }
            Spacer(Modifier.width(8.dp))
            StateChip(r.state, weeksRecorded(p))
        }
        if (nightPromise) Banner("🔊", NIGHT_LINE, "${if (s.night) "21:04" else ""} · 약속 \"해 지면 운전 안 하기\"", Warn) {
            Button("다른 방법으로 가기", { s.screen = Screen.OTHER_WAY })
        } else Text("☀️ 안녕하세요, ${p.name} 님. 오늘도 천천히 다녀오세요.", color = Color.White, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1.3f).fillMaxHeight()) {
                Text("오늘 어디 가세요?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🏥", "⛪", "🛒").zip(Demo.PLACES).forEach { (icon, place) ->
                        val marked = p.marked.any { it.first == place.first }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().background(Raised, RoundedCornerShape(12.dp)).clickable {
                                if (marked) { s.drive = Drive(place.first, false); s.screen = Screen.ROUTE }
                                else DriveService.start(ctx, place.first, comfortable = false)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(icon, fontSize = 30.sp)
                            Text(place.first, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (marked) Text("📍 표시한 구간 있음", color = Warn, fontSize = 11.sp)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val drive = when {
                    r.state == State.LEARNING -> "평소 운전을 알아가는 중이에요"
                    r.changed.isNotEmpty() -> "${r.changed.first().metric.label}이 늘었어요"
                    else -> "모두 평소와 같아요"
                }
                SmallCard("이번 주 운전  ›", drive, Modifier.weight(1f)) { s.screen = Screen.DETAIL }
                SmallCard("약속", if (0 in p.promises) "해 지면 안 하기 · 이번 주 밤 운전 ${r.nightDrivesThisWeek}회" else "${p.promises.size}개", Modifier.weight(1f))
                val family = when {
                    p.family.isEmpty() -> "가족 초대하기  ›"
                    p.ackedBy.isNotEmpty() -> "${p.ackedBy.joinToString()} 님이 이번 주 요약을 확인했어요 👍"
                    p.sentWeek == s.week() -> "${p.family.joinToString()} 님에게 보냈어요"
                    else -> "${p.family.joinToString()} 님과 연결됨"
                }
                SmallCard("가족", family, Modifier.weight(1f), { s.screen = Screen.INVITE }.takeIf { p.family.isEmpty() })
                SmallCard("이번 주 카드  ›", if (p.sentWeek == s.week()) "보냈어요" else "부모님이 먼저 봐요", Modifier.weight(1f)) { s.screen = Screen.WEEKLY }
            }
        }
    }
}

private const val NIGHT_LINE = "오늘은 늦었네요. 꼭 가셔야 하면 천천히 가요."

// ---------- C6 route, C7 other way ----------

@Composable
private fun Route() {
    val s = AppState
    val place = s.drive?.place ?: return
    val junction = s.profile.marked.filter { it.first == place }.joinToString(" · ") { it.second }
    val promised = 1 in s.profile.promises
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${place}까지 어떤 길로 갈까요?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f).fillMaxWidth(0.85f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().background(Raised, RoundedCornerShape(14.dp)).clickable { DriveService.start(ctx, place, false) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("빠른 길 · 24분", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("표시한 $junction 구간을 지나요", color = Dim, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("지난번 이곳에서 길 안내를 다시 받았어요", color = Dim, fontSize = 13.sp)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1B3A5C), RoundedCornerShape(14.dp))
                    .border(2.dp, if (promised) Accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { DriveService.start(ctx, place, true) }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("편한 길 · 28분", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (promised) Text("약속: 표시한 구간 피하기", color = Demo_, fontSize = 11.sp,
                        modifier = Modifier.background(Color(0xFF3A2A55), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("그 구간을 피해요 (+4분)", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("큰길 위주 · 좌회전이 적어요", color = Dim, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(0.85f)) {
            Text("고르면 내비 경로가 바뀌어요", color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("← 홈으로", color = Dim, fontSize = 13.sp, modifier = Modifier.clickable { s.screen = Screen.HOME }.padding(horizontal = 12.dp))
            Text("오늘은 다른 방법으로 가기 ›", color = Dim, fontSize = 13.sp, modifier = Modifier.clickable { s.screen = Screen.OTHER_WAY })
        }
    }
}

@Composable
private fun OtherWay() {
    val s = AppState
    var done by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text(s.drive?.place?.let { "${it}까지 다른 방법으로 가요" } ?: "오늘은 다른 방법으로 가요", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("🚕", "택시 부르기", "가까운 택시를 불러요"), Triple("🚗", "대리운전", "내 차로 데려다줘요"),
                Triple("👨‍👩‍👦", "가족에게 부탁", s.profile.family.firstOrNull()?.let { "$it 님에게 알려요" } ?: "연결된 가족에게 알려요")).forEach { (icon, title, sub) ->
                Column(
                    Modifier.weight(1f).background(Raised, RoundedCornerShape(12.dp)).clickable { done = title }.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(icon, fontSize = 30.sp)
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(sub, color = Dim, fontSize = 12.sp)
                }
            }
        }
        Text(done?.let { "$it 연결했어요 (시연용)" } ?: "이유는 묻지 않고, 저장하지 않아요", color = if (done != null) Ok else Dim, fontSize = 14.sp)
        Button("돌아가기", { s.screen = Screen.HOME }, Raised)
    }
}

// ---------- C8 driving, C9 arrival ----------

@Composable
private fun Driving() {
    val d = AppState.drive ?: return
    Box(Modifier.fillMaxSize().background(Color(0xFF16222E), RoundedCornerShape(14.dp))) {
        Text("🗺  내비게이션 · ${d.place} (${if (d.comfortable) "편한 길" else "빠른 길"})", color = Dim, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
        if (d.vehicle && !d.started) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("기어를 D로 넣으면 안내를 시작해요", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // 시연용: drives the Pleos emulator's Virtual Console (same as tools/demo-drive.py).
            var sent by remember { mutableStateOf(false) }
            if (sent || DemoDriver.running) Text("시연 주행 중 · 곧 기어가 D로 바뀌어요", color = Demo_, fontSize = 14.sp)
            else Text(
                "▶ 시연 주행 시작 (Virtual Console)", color = Demo_, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, Color(0xFF8C6BD9), RoundedCornerShape(10.dp))
                    .clickable { sent = true; DemoDriver.drive(d.comfortable) }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
            Text("또는 Virtual Console에서 직접 기어를 D로 바꿔도 돼요", color = Dim, fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp).width(330.dp).background(Bg, RoundedCornerShape(10.dp)).padding(10.dp)) {
            Text("주행 기록 · 화면 조작 없음", color = Dim, fontSize = 11.sp)
            d.log.takeLast(6).forEach { Text(it, color = Color.White, fontSize = 12.sp) }
        }
        d.banner?.let {
            Banner("🔊", it, "한 주행에 최대 2번 · 조작 없음", Accent, Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable
private fun Arrived() {
    val s = AppState
    val d = s.drive ?: return
    val r = d.report
    var dismissed by remember { mutableStateOf(false) }
    val mine = s.profile.marked.filter { it.first == d.place }.map { it.second }
    val cand = candidate(d)?.takeIf { !dismissed && it !in mine }
    val points = Demo.script(d.place, d.comfortable).mapNotNull { (_, e) ->
        when (e) { is NavEvent.Turn -> e.point; is NavEvent.Reroute -> e.point; else -> null }
    }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${d.place}에 도착했어요 (P)", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("회전 안내 ${d.turns}번 · 깜빡이 ${d.signals}번 · 재탐색 ${d.reroutes.size}번 · 급제동 ${d.hardBrakes}번" + if (d.vehicle) " · 차량 신호" else " · 시연 주행", color = Dim, fontSize = 13.sp)
        }
        d.banner?.let { line ->
            val c = r?.changed?.firstOrNull()
            Banner("🔊", line, c?.evidenceLine(), if (c != null) Warn else Accent)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cand != null) Column(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFF2B2110), RoundedCornerShape(12.dp)).border(1.dp, Warn, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("$cand 전후로 길 안내를 다시 받았어요.\n표시해 둘까요?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("재탐색 ${d.reroutes.count { it == cand }}번 · 표시하면 다음에 피할 길을 보여 드려요", color = Warn, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("괜찮아요", { dismissed = true }, Raised)
                    Button("📍 표시하기", { s.update { it.copy(marked = it.marked + (d.place to cand)) } }, Accent, Modifier.weight(1f))
                }
            } else Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("이번 주행", color = Dim, fontSize = 13.sp)
                Text("회전 안내 ${d.turns}번 · 깜빡이 ${d.signals}번 · 재탐색 ${d.reroutes.size}번 · 급제동 ${d.hardBrakes}번", color = Color.White, fontSize = 15.sp)
                if (mine.isNotEmpty()) Text("📍 표시한 구간: ${mine.joinToString(" · ")}", color = Warn, fontSize = 13.sp)
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("다른 불편했던 곳이 있었나요?", color = Dim, fontSize = 13.sp)
                points.forEach { pt ->
                    val marked = pt in mine
                    Text((if (marked) "📍 " else "") + pt, color = Color.White, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(10.dp))
                            .clickable { s.update { it.copy(marked = if (marked) it.marked - (d.place to pt) else it.marked + (d.place to pt)) } }.padding(10.dp))
                }
            }
        }
        Row { Spacer(Modifier.weight(1f)); Button("홈으로", { s.drive = null; s.screen = Screen.HOME }, Raised) }
    }
}

// ---------- C10 weekly card, C11 detail ----------

@Composable
private fun WeeklyCard(r: Report) {
    val s = AppState
    val p = s.profile
    val w = weekly(p, r)
    val sent = p.sentWeek == s.week()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val tone = when (r.state) { State.TALK -> Talk; State.WATCH -> Warn; else -> Ok }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { s.screen = Screen.HOME }.padding(end = 12.dp))
            Text("이번 주 운전 카드 · ${s.week()} 주", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StateChip(r.state, weeksRecorded(p))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(w.headline, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                w.evidence.forEach { m ->
                    Text(m.evidenceLine(), color = tone, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                if (r.state == State.TALK) Text("가족과 이야기해 보실래요?", color = Color.White, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallCard("잘하신 점", w.goodPoints.joinToString(" · ").ifEmpty { "—" }, Modifier.weight(1f))
                SmallCard("약속", w.promise.ifEmpty { "고른 약속이 없어요" }, Modifier.weight(1f))
                SmallCard("피한 구간", w.avoided.ifEmpty { "표시한 구간이 없어요" }, Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    error -> "보내지 못했어요. 인터넷 연결을 확인해 주세요"
                    p.family.isEmpty() -> "가족을 연결하면 보낼 수 있어요"
                    else -> "보낼 내용: ${if (p.shareScope == "evidence") "상태와 근거" else "상태만"} · ${p.family.joinToString()} 님이 폰으로 봐요"
                },
                color = if (error) Talk else Dim, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            when {
                p.family.isEmpty() -> Button("가족 연결하기", { s.screen = Screen.INVITE })
                sent -> Text(if (p.ackedBy.isNotEmpty()) "${p.ackedBy.joinToString()} 님이 확인했어요 👍" else "보냈어요 · 확인 기다리는 중", color = Ok, fontSize = 15.sp)
                else -> {
                    Button("이번 주는 안 보내기", { s.screen = Screen.HOME }, Raised)
                    Button(if (sending) "보내는 중…" else "가족에게 보내기", {
                        val token = p.carToken ?: return@Button
                        if (sending) return@Button
                        sending = true; error = false
                        val i = s.cur
                        val week = s.week()
                        FamilyApi.sendSummary(token, week, w) { ok ->
                            sending = false
                            if (ok) s.update(i) { it.copy(sentWeek = week, ackedBy = emptyList()) } else error = true
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun Detail(r: Report) {
    val s = AppState
    val p = s.profile
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { s.screen = Screen.HOME }.padding(end = 12.dp))
            Text("${p.name} 님의 운전 · 이번 주 vs 평소(8주)", color = Dim, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chip("📅 월별 기록", selected = false) { s.screen = Screen.MONTHLY }
            Spacer(Modifier.width(8.dp))
            StateChip(r.state, weeksRecorded(p))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            r.metrics.forEach { MetricTile(it, Modifier.weight(1f).fillMaxHeight()) }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("밤 운전", color = Dim, fontSize = 13.sp)
                Text("${r.nightDrivesThisWeek}회", color = if (r.nightDrivesThisWeek == 0) Ok else Warn, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (0 in p.promises) Text("약속: 해 지면 안 하기", color = Dim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                p.marked.forEach { Text("📍 ${it.second} (${it.first})", color = Warn, fontSize = 13.sp) }
                if (p.avoidedThisWeek > 0) Text("이번 주 ${p.avoidedThisWeek}번 피함", color = Dim, fontSize = 12.sp)
            }
        }
        p.carToken?.let { token ->
            var busy by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (p.backup == true) "☁️ 주행 기록을 서버에 보관하고 있어요 · 횟수만, 위치·경로 없음, 가족은 볼 수 없음"
                    else "🚗 주행 기록은 이 차에만 있어요",
                    color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Button(
                    when { busy -> "바꾸는 중…"; p.backup == true -> "보관 끄고 서버 기록 지우기"; else -> "서버에 보관하기" },
                    {
                        if (busy) return@Button
                        busy = true
                        val i = s.cur
                        val on = p.backup != true
                        FamilyApi.setBackup(token, on) { ok -> busy = false; if (ok) s.update(i) { it.copy(backup = on) } }
                    },
                    Raised,
                )
            }
        }
    }
}

/** Asked once, right after consent: may this driver's drive counts be kept on the server? */
@Composable
private fun BackupPrompt() {
    val s = AppState
    val p = s.profile
    var busy by remember { mutableStateOf(false) }
    fun choose(on: Boolean) {
        val token = p.carToken ?: run { s.askBackup = false; return }
        if (busy) return
        busy = true
        val i = s.cur
        FamilyApi.setBackup(token, on) { ok -> busy = false; s.update(i) { it.copy(backup = on && ok) }; s.askBackup = false }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.62f).background(Panel, RoundedCornerShape(16.dp)).border(1.dp, Accent, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("주행 기록을 서버에 보관할까요?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("보관하면 앱을 다시 설치하거나 차를 바꿔도 ${p.name} 님의 평소 운전 기록이 이어져요.", color = Color.White, fontSize = 15.sp)
            Column(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✓ 보관하는 것: 주행마다 회전 안내·깜빡이·재탐색·급제동 횟수, 거리, 목적지 이름", color = Ok, fontSize = 13.sp)
                Text("✗ 보관하지 않는 것: 위치, 지나간 경로, 시각", color = Color(0xFFFF8A8A), fontSize = 13.sp)
                Text("가족은 이 기록을 볼 수 없어요. 가족에게는 보내기로 한 주간 요약만 가요.", color = Dim, fontSize = 13.sp)
                Text("언제든 운전 기록 화면에서 끌 수 있고, 끄면 서버 기록을 바로 지워요.", color = Dim, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("차에만 두기", { choose(false) }, Raised, Modifier.weight(1f))
                Button(if (busy) "저장 중…" else "보관하기", { choose(true) }, Accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(m: MetricReport, modifier: Modifier) {
    val now = m.weekly.first()
    val worse = m.worseStreak >= 2
    Tile(modifier) {
        Text(m.metric.label, color = Dim, fontSize = 13.sp)
        Text(now?.let(m.metric::display) ?: "—", color = if (worse) Warn else Ok, fontSize = 20.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        Text("평소 ${m.baseline?.let(m.metric::display) ?: "—"}", color = Dim, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        val max = m.weekly.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            m.weekly.reversed().forEachIndexed { i, v ->
                val week = m.weekly.lastIndex - i // 0 = this week
                val recentWorse = week >= m.judged && week < m.judged + m.worseStreak
                // Signal is a miss rate internally; show compliance so taller = better there.
                val h = if (m.metric == Metric.SIGNAL) (100 - (v ?: 0.0)) / 100 else (v ?: 0.0) / max
                Box(Modifier.weight(1f).fillMaxHeight(h.toFloat().coerceIn(0.04f, 1f))
                    .background(if (recentWorse && worse) Warn else Color(0xFF3A5875), RoundedCornerShape(2.dp)))
            }
        }
        Text("12주 전 → 이번 주", color = Dim, fontSize = 11.sp)
    }
}

// ---------- shared bits ----------

@Composable
private fun Banner(icon: String, line: String, sub: String?, tone: Color, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) =
    Row(
        modifier.fillMaxWidth().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.weight(1f)) {
            Text(line, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            sub?.let { Text(it, color = tone, fontSize = 13.sp) }
        }
        action()
    }

@Composable
private fun Option(title: String, sub: String, selected: Boolean, onClick: () -> Unit) =
    Column(
        Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(10.dp)).border(2.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Dim, fontSize = 12.sp)
    }

@Composable
private fun Toggle(on: Boolean) =
    Box(Modifier.size(40.dp, 22.dp).background(if (on) Accent else Color(0xFF3A5875), RoundedCornerShape(99.dp)).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(16.dp).background(Color.White, RoundedCornerShape(99.dp)))
    }

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false) =
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
    Text(
        label, color = if (selected) Color.White else Dim, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.background(if (selected) Raised else Color.Transparent, RoundedCornerShape(99.dp))
            .border(1.dp, if (selected) Accent else Raised, RoundedCornerShape(99.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )

@Composable
private fun SmallCard(title: String, body: String, modifier: Modifier, onClick: (() -> Unit)? = null) =
    Column(
        modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Dim, fontSize = 12.sp)
        Text(body, color = Color.White, fontSize = 14.sp, maxLines = 2)
    }

@Composable
private fun Tile(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)

@Composable
private fun StateChip(s: State, weeks: Int) {
    val c = when (s) { State.STABLE -> Ok; State.WATCH -> Warn; State.TALK -> Talk; State.LEARNING -> Dim }
    val sub = if (s == State.LEARNING) "$weeks/12주" else "${weeks}주째 기록"
    Text("${s.label} · $sub", color = Bg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.background(c, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
}

@Composable
private fun Button(label: String, onClick: () -> Unit, bg: Color = Accent, modifier: Modifier = Modifier) =
    Text(
        label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = modifier.background(bg, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )

// ---------- 월별 운전 기록, 운전 졸업 ----------

private fun stateColor(st: State) = when (st) { State.STABLE -> Ok; State.WATCH -> Warn; State.TALK -> Talk; State.LEARNING -> Dim }

@Composable
private fun Monthly(r: Report) {
    val s = AppState
    val p = s.profile
    var offset by remember(s.cur) { mutableIntStateOf(0) }
    val month = YearMonth.from(LocalDate.parse(s.week())).minusMonths(offset.toLong())
    val rows = remember(p.trips, month, s.weekShift) { monthRows(p, month) }
    val step = nextStep(p, r)
    val tone = stateColor(r.state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("←", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { s.screen = Screen.HOME }.padding(end = 4.dp))
            Text("${p.name} 님의 월별 운전 기록", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("◀", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { offset++ }.padding(8.dp))
            Text("${month.year}년 ${month.monthValue}월", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("▶", color = if (offset > 0) Color.White else Raised, fontSize = 18.sp, modifier = Modifier.clickable(enabled = offset > 0) { offset-- }.padding(8.dp))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val cols = listOf(1.1f, 1f, 1.3f, 1.3f, 1f, 1.3f, 0.7f)
                @Composable fun Cells(vararg cells: @Composable RowScope.(Modifier) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
                    cells.forEachIndexed { i, c -> c(Modifier.weight(cols[i])) }
                }
                Cells(
                    { Text("주", color = Dim, fontSize = 12.sp, modifier = it) }, { Text("상태", color = Dim, fontSize = 12.sp, modifier = it) },
                    { Text("주행", color = Dim, fontSize = 12.sp, modifier = it) }, { Text("재탐색", color = Dim, fontSize = 12.sp, modifier = it) },
                    { Text("깜빡이", color = Dim, fontSize = 12.sp, modifier = it) }, { Text("급제동", color = Dim, fontSize = 12.sp, modifier = it) },
                    { Text("밤", color = Dim, fontSize = 12.sp, modifier = it) },
                )
                if (rows.isEmpty()) Text("이 달에는 기록이 없어요", color = Dim, fontSize = 15.sp)
                rows.forEach { row ->
                    fun cell(m: Metric) = row.report.metrics.first { it.metric == m }.let { mr -> mr.weekly.first()?.let(m::display) ?: "—" }
                    val st = row.report.state
                    Cells(
                        { Text("${row.monday.monthValue}/${row.monday.dayOfMonth} 주" + if (row.k == 0) " (이번 주)" else "", color = Color.White, fontSize = 14.sp, modifier = it) },
                        { Text(st.label, color = stateColor(st), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = it) },
                        { Text("${row.trips}회 · ${"%.0f".format(row.km)}km", color = Color.White, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.REROUTE), color = Color.White, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.SIGNAL), color = Color.White, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.BRAKE), color = Color.White, fontSize = 13.sp, modifier = it) },
                        { Text("${row.night}회", color = if (row.night > 0) Warn else Color.White, fontSize = 13.sp, modifier = it) },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (rows.isNotEmpty()) {
                    val flow = rows.reversed().map { it.report.state.label }.fold(listOf<String>()) { acc, x -> if (acc.lastOrNull() == x) acc else acc + x }
                    Text(
                        "이 달 주행 ${rows.sumOf { it.trips }}회 · ${"%.0f".format(rows.sumOf { it.km })}km · 밤 운전 ${rows.sumOf { it.night }}회 · 상태 ${flow.joinToString(" → ")}",
                        color = Dim, fontSize = 13.sp,
                    )
                    if (p.name == Demo.PARENT || p.name == "박영호") Text("지난 기록은 시연용이에요", color = Demo_, fontSize = 11.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("지금 운전 상태", color = Dim, fontSize = 12.sp)
                    Text(step.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(step.body, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("다음에 해 볼 수 있는 것", color = Dim, fontSize = 12.sp)
                    step.steps.forEach { Text("· $it", color = tone, fontSize = 14.sp) }
                }
                Text("DriveWatch는 운전 능력을 진단하지 않아요. 평소와 달라진 점을 알려 드릴 뿐이에요.", color = Dim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "운전을 그만두기로 하셨나요?  운전 졸업 안내 ›", color = Dim, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).clickable { s.screen = Screen.GRADUATE }.padding(12.dp),
                )
            }
        }
    }
}

/** C13. Reached only when the driver chooses it. Respects the decision and offers other ways to the usual places. */
@Composable
private fun Graduate() {
    val s = AppState
    val p = s.profile
    var done by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { s.screen = Screen.MONTHLY }.padding(end = 12.dp))
            Text("운전 졸업", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("오랫동안 안전하게 운전해 오셨어요. 계속할지, 여기서 마칠지는 ${p.name} 님이 정하시는 일이에요.", color = Color.White, fontSize = 16.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("면허를 반납하려면", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("가까운 경찰서나 운전면허시험장에서 반납할 수 있어요.", color = Color.White, fontSize = 14.sp)
                Text("지자체마다 교통비 지원 같은 반납 혜택이 있어요. 주소지 주민센터에서 확인해 보세요.", color = Dim, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("마음이 바뀌면 언제든 다시 운전 기록을 이어 갈 수 있어요.", color = Dim, fontSize = 13.sp)
            }
            Tile(Modifier.weight(1.3f).fillMaxHeight()) {
                Text("자주 가시던 곳은 이렇게 다녀요", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Demo.PLACES.forEach { (place, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(place, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Button("택시 부르기", { done = "$place 택시 부르기" }, Raised)
                        Button("가족에게 부탁", { done = "$place 가는 길 가족에게 부탁" }, Raised)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(done?.let { "$it 연결했어요 (시연용)" } ?: "택시 호출과 가족 알림은 시연용이에요", color = if (done != null) Ok else Dim, fontSize = 13.sp)
            }
        }
    }
}
