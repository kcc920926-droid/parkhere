package com.parkhere.drivewatch

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import kotlinx.coroutines.launch
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
import androidx.compose.ui.draw.clip
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

// Colour tokens from the current theme (Theme.kt).
private val Bg: Color @Composable get() = LocalPalette.current.bg
private val Panel: Color @Composable get() = LocalPalette.current.panel
private val Raised: Color @Composable get() = LocalPalette.current.raised
private val Ink: Color @Composable get() = LocalPalette.current.ink
private val Dim: Color @Composable get() = LocalPalette.current.dim
private val Ok: Color @Composable get() = LocalPalette.current.ok
private val Warn: Color @Composable get() = LocalPalette.current.warn
private val Talk: Color @Composable get() = LocalPalette.current.talk
private val Accent: Color @Composable get() = LocalPalette.current.accent
private val Primary: Color @Composable get() = LocalPalette.current.primary
private val OnPrimary: Color @Composable get() = LocalPalette.current.onPrimary
private val OkFill: Color @Composable get() = LocalPalette.current.okFill
private val WarnFill: Color @Composable get() = LocalPalette.current.warnFill
private val TalkFill: Color @Composable get() = LocalPalette.current.talkFill
private val OnChip: Color @Composable get() = LocalPalette.current.onChip
private val Demo_: Color @Composable get() = LocalPalette.current.demo
private val DemoBg: Color @Composable get() = LocalPalette.current.demoBg
private val DemoButton: Color @Composable get() = LocalPalette.current.demoButton
private val DemoBorder: Color @Composable get() = LocalPalette.current.demoBorder
private val Track: Color @Composable get() = LocalPalette.current.track
private val Danger: Color @Composable get() = LocalPalette.current.danger
private val Chosen: Color @Composable get() = LocalPalette.current.chosen
private val AlertBg: Color @Composable get() = LocalPalette.current.alertBg

class MainActivity : ComponentActivity() {
    private var tts: Speaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motion.scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
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

    // A newer build for this car? Only a notice with a download QR; the app never installs itself
    // (a car app changing under the driver is not acceptable, and real releases go through the app market).
    LaunchedEffect(Unit) {
        FamilyApi.latestApp { r -> if (r != null && r.versionCode > BuildConfig.VERSION_CODE) s.update = r }
    }

    // Keep the family card current: who linked, who acknowledged.
    LaunchedEffect(s.cur, p.carToken, s.screen) {
        val token = p.carToken ?: return@LaunchedEffect
        while (true) {
            val id = s.profile.id
            FamilyApi.me(token) { family, acked, week ->
                s.updateId(id) { it.copy(family = family, ackedBy = if (week == it.sentWeek) acked else emptyList()) }
            }
            delay(4_000)
        }
    }

    // System back follows the screen hierarchy instead of closing the app. Onboarding past consent has no
    // way back (the driver is already registered), and a drive in progress is ended by the gear, not by back.
    BackHandler(enabled = s.askBackup || backTarget(s.screen) != null) {
        if (s.askBackup) return@BackHandler
        val to = backTarget(s.screen) ?: return@BackHandler
        if (s.screen == Screen.ARRIVED || s.screen == Screen.ROUTE || s.screen == Screen.OTHER_WAY) s.drive = null
        s.screen = to
    }

    // Night theme when the app's clock says night (시연 패널 "밤 9시로") or the car's display is in night mode.
    val night = s.night || isSystemInDarkTheme()
    val palette = animatedPalette(night)
    CompositionLocalProvider(LocalPalette provides palette) {
    MaterialTheme(
        colorScheme = if (night) darkColorScheme(primary = palette.accent) else lightColorScheme(primary = palette.accent),
        typography = androidx.compose.material3.Typography().inFamily(AstaSans), // Pleos system UI font
    ) {
        Column(
            Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopBar(locked = s.screen == Screen.DRIVE && s.drive?.finished == false, demoOpen = demoOpen, onDemo = { demoOpen = !demoOpen })
            AnimatedVisibility(demoOpen, enter = expandVertically(tween(Motion.ms(Motion.SCREEN_MS))) + fadeIn(), exit = shrinkVertically(tween(Motion.ms(Motion.SCREEN_MS))) + fadeOut()) { DemoPanel() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(s.screen, transitionSpec = { screenTransition(initialState, targetState) }, label = "screen") { screen ->
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            Screen.SIGNUP -> Signup()
                            Screen.CONSENT -> Consent(speak)
                            Screen.PROMISES -> Promises()
                            Screen.INVITE -> Invite(speak)
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
                    }
                }
                androidx.compose.animation.AnimatedVisibility(s.askBackup, enter = fadeIn(tween(Motion.ms(Motion.SCREEN_MS))) + scaleIn(tween(Motion.ms(Motion.SCREEN_MS)), initialScale = 0.96f), exit = fadeOut(tween(Motion.ms(Motion.SCREEN_MS)))) { BackupPrompt() }
                androidx.compose.animation.AnimatedVisibility(s.showUpdate && s.update != null, enter = fadeIn(tween(Motion.ms(Motion.SCREEN_MS))), exit = fadeOut(tween(Motion.ms(Motion.SCREEN_MS)))) {
                    s.update?.let { UpdatePrompt(it) }
                }
            }
        }
    }
    }
}

/** How deep a screen sits. Going deeper slides in from the right, coming back slides in from the left. */
private fun depth(s: Screen) = when (s) {
    Screen.SIGNUP -> 0; Screen.CONSENT -> 1; Screen.PROMISES -> 2; Screen.INVITE -> 3; Screen.HOME -> 4
    Screen.ROUTE, Screen.WEEKLY, Screen.DETAIL -> 5
    Screen.OTHER_WAY, Screen.DRIVE, Screen.MONTHLY -> 6
    Screen.ARRIVED, Screen.GRADUATE -> 7
}

private fun backTarget(s: Screen): Screen? = when (s) {
    Screen.CONSENT -> Screen.SIGNUP.takeIf { AppState.profile.carToken == null }
    Screen.INVITE -> Screen.HOME.takeIf { AppState.profile.onboarded }
    Screen.ROUTE, Screen.OTHER_WAY, Screen.ARRIVED, Screen.WEEKLY, Screen.DETAIL, Screen.MONTHLY -> Screen.HOME
    Screen.GRADUATE -> Screen.MONTHLY
    else -> null
}

private fun screenTransition(from: Screen, to: Screen): ContentTransform {
    val ms = Motion.ms(Motion.SCREEN_MS)
    val ease = tween<Float>(ms, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(ms, easing = FastOutSlowInEasing)
    val dir = depth(to).compareTo(depth(from))
    // Short slide (a sixth of the width) plus fade: enough to show direction without sweeping motion.
    return if (dir == 0) fadeIn(ease) togetherWith fadeOut(ease)
    else (slideInHorizontally(slide) { dir * it / 6 } + fadeIn(ease)) togetherWith (slideOutHorizontally(slide) { -dir * it / 6 } + fadeOut(ease))
}

// ---------- C0 top bar and demo panel ----------

@Composable
private fun TopBar(locked: Boolean, demoOpen: Boolean, onDemo: () -> Unit) {
    val s = AppState
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DriveWatch", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        s.profiles.forEachIndexed { i, p ->
            if (p.name.isNotBlank() || i == s.cur) Chip(p.name.ifBlank { "새 운전자" }, selected = i == s.cur, enabled = !locked, icon = Ic.PERSON) { s.pick(i) }
        }
        if (locked) Text("주행 중에는 운전자를 바꿀 수 없어요", color = Dim, fontSize = 12.sp)
        else Chip("+ 운전자 추가", selected = false) { s.addDriver() }
        Spacer(Modifier.weight(1f))
        Text(s.vehicleNote, color = Dim, fontSize = 12.sp)
        s.update?.let { u ->
            if (!locked) IconLabel(
                Ic.CHEVRON_UP, "새 버전 ${u.versionName.substringBefore('+')}", Accent, 12.sp, FontWeight.Bold,
                Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Accent, RoundedCornerShape(8.dp)).clickable { s.showUpdate = true }.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        if (!locked) Row(
            Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, DemoBorder, RoundedCornerShape(8.dp)).clickable(onClick = onDemo).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("시연 패널", color = Demo_, fontSize = 12.sp)
            Glyph(if (demoOpen) Ic.CHEVRON_UP else Ic.CHEVRON_DOWN, Demo_, 16.dp)
        }
    }
}

@Composable
private fun DemoPanel() {
    val s = AppState
    Row(
        Modifier.fillMaxWidth().background(DemoBg, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("시연용", color = Demo_, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Button(if (s.night) "낮으로" else "밤 9시로", { s.night = !s.night }, DemoButton, icon = if (s.night) Ic.SUN else Ic.MOON)
        Button("다음 주로", { s.nextWeek() }, DemoButton)
        Button("4주 뒤로", { s.fourWeeksLater() }, DemoButton)
        // Past weeks from the server's history scenarios: 평소대로 → 관찰 → 대화 권장 → … (Replay.kt).
        val ctx = LocalContext.current
        val label = Scenarios.HISTORIES.first { it.first == s.historyId }.second
        Button("기록: $label", {
            val next = Scenarios.HISTORIES[(Scenarios.HISTORIES.indexOfFirst { it.first == s.historyId } + 1) % Scenarios.HISTORIES.size].first
            val id = s.profile.id
            Scenarios.history(ctx, next) { trips, _ ->
                if (trips == null) return@history
                s.historyId = next
                s.updateId(id) { p -> p.copy(trips = p.trips.filterNot { it.demo } + trips) }
            }
        }, DemoButton, icon = Ic.CALENDAR)
        // Fills the family page's monthly view in one go; each week is still a summary the parent chose to send.
        Button("최근 4주 요약 보내기", {
            val p = s.profile
            val token = p.carToken ?: return@Button
            if (p.family.isEmpty()) return@Button
            (0 until minOf(4, weeksRecorded(p))).forEach { k ->
                FamilyApi.sendSummary(token, mondayOf(k).toString(), weekly(p, reportAt(p.trips, k))) {}
            }
            s.update { it.copy(sentWeek = s.week(), ackedBy = emptyList()) }
        }, DemoButton)
        Spacer(Modifier.weight(1f))
        Text("${s.week()} 주 · 자녀 폰: ${FamilyApi.FAMILY_PAGE} · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Demo_, fontSize = 12.sp)
        Button("처음부터", { s.reset() }, DemoButton, icon = Ic.RESET)
    }
}

// ---------- C1–C4 onboarding ----------

@Composable
private fun Signup() {
    val s = AppState
    var name by remember(s.cur) { mutableStateOf(s.profile.name.ifBlank { if (s.cur == 0) Demo.PARENT else "" }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text("운전자 등록", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth(0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Name only: nothing else is used, and a phone number would be personal data kept for no reason.
            Field("이름", name, { name = it })
            IconLabel(Ic.LOCK, "기록은 연결한 가족에게만 보여요. 위치는 보내지 않아요.", Dim, 12.sp)
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
                }, if (name.isNotBlank()) Primary else Raised, Modifier.weight(1f))
            }
        }
    }
}

private const val CONSENT_SPEECH = "DriveWatch는 익숙한 길에서 길 안내를 다시 받은 횟수, 회전할 때 깜빡이, 급제동, 밤 운전을 기록해요. 위치와 지나간 경로, 카메라 영상, 음주 여부는 기록하지 않아요. 언제든 끌 수 있어요."

@Composable
private fun Consent(speak: (String) -> Unit) {
    val s = AppState
    val ctx = LocalContext.current
    var scope by remember { mutableStateOf(s.profile.shareScope) }
    var busy by remember { mutableStateOf(false) }
    val co = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DriveWatch가 기록하는 것", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                IconLabel(Ic.CHECK, "기록해요", Ok, 17.sp, FontWeight.Bold)
                listOf("익숙한 길에서 길 안내를 다시 받은 횟수", "회전할 때 깜빡이", "급제동", "밤 운전").forEach { Text(it, color = Ink, fontSize = 15.sp) }
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                IconLabel(Ic.CLOSE, "기록하지 않아요", Danger, 17.sp, FontWeight.Bold)
                listOf("위치와 지나간 경로", "카메라 영상", "음주 여부").forEach { Text(it, color = Ink, fontSize = 15.sp) }
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("가족에게 보낼 내용", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Option("상태만", "안정 · 관찰 · 대화 권장", scope == "status") { scope = "status" }
                Option("상태와 근거", "\"재탐색 0.2 → 0.9회\" 같은 숫자 포함", scope == "evidence") { scope = "evidence" }
            }
        }
        // Primary action on the left, nearest the driver's seat (Fitts); the rest to its right.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MorphButton("동의하고 시작", if (busy) Phase.BUSY else Phase.IDLE, "", {
                busy = true
                val started = System.currentTimeMillis()
                val id = s.profile.id
                s.update { it.copy(shareScope = scope) }
                // Already registered (consent shown again, e.g. after leaving before the promises step):
                // keep the same server driver and its family links, only update what may be shared.
                val existing = s.profile.carToken
                if (existing != null) {
                    FamilyApi.setShareScope(existing, scope) {
                        settle(started) {
                            s.askBackup = s.byId(id)?.backup == null
                            s.screen = Screen.PROMISES
                        }
                    }
                    return@MorphButton
                }
                val name = s.profile.name
                CarKey.get(ctx) { key ->
                    FamilyApi.registerDriver(name, scope, "consent", id, key) { r ->
                        settle(started) {
                            s.updateId(id) { it.copy(carToken = r?.token, backup = r?.backup ?: it.backup) }
                            // Same driver of this car as before (reinstall): bring back what the server kept.
                            if (r?.reused == true && r.backup == true) Backup.restoreFromServer(id)
                            s.askBackup = r != null && r.backup == null // nothing to ask if offline or already decided
                            s.screen = Screen.PROMISES
                        }
                    }
                }
            }, Modifier.weight(1f), wide = true)
            Button("읽어 주기", { speak(CONSENT_SPEECH) }, Raised, icon = Ic.SPEAKER)
            Text("언제든 설정에서 끌 수 있어요", color = Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Promises() {
    val s = AppState
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("지키고 싶은 약속을 골라 주세요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                            Glyph(PROMISES[i].first, if (on) Accent else Dim, 28.dp)
                            Text(PROMISES[i].second, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Toggle(on)
                        }
                    }
                }
            }
        }
        Row { Button("다음 · 가족 연결", { s.update { it.copy(onboarded = true) }; s.screen = Screen.INVITE }, modifier = Modifier.weight(1f)); Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Invite(speak: (String) -> Unit) {
    val s = AppState
    val p = s.profile
    val ctx = LocalContext.current
    var code by remember(s.cur) { mutableStateOf<String?>(null) }
    var failed by remember(s.cur) { mutableStateOf(false) }
    var demo by remember(s.cur) { mutableStateOf(Phase.IDLE) }
    var phone by remember(s.cur) { mutableStateOf("") }
    var sms by remember(s.cur) { mutableStateOf(Phase.IDLE) }
    var smsDryRun by remember(s.cur) { mutableStateOf(false) }
    var smsNote by remember(s.cur) { mutableStateOf<String?>(null) }
    var agreed by remember(s.cur) { mutableStateOf(false) }
    var picked by remember(s.cur) { mutableStateOf<String?>(null) } // name of the contact the number came from
    val canPickContact = remember {
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI).resolveActivity(ctx.packageManager) != null
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    phone = c.getString(0).orEmpty().filter { ch -> ch.isDigit() || ch == '-' }.replace(Regex("^\\+?82-?"), "0").take(13)
                    picked = c.getString(1)
                    sms = Phase.IDLE; smsNote = null
                }
            }
        }
    }
    val co = rememberCoroutineScope()
    LaunchedEffect(p.id) {
        val id = p.id
        fun ask(token: String) = FamilyApi.invite(token) { code = it; failed = it == null }
        val token = p.carToken
        if (token != null) ask(token)
        else CarKey.get(ctx) { key ->
            FamilyApi.registerDriver(p.name, p.shareScope, "invite without token", id, key) { r ->
                if (r == null) failed = true else { s.updateId(id) { it.copy(carToken = r.token, backup = r.backup ?: it.backup) }; ask(r.token) }
            }
        }
    }
    if (p.family.isNotEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            IconLabel(Ic.CHECK, "${p.family.joinToString()} 님과 연결됐어요", Ok, 24.sp, FontWeight.Bold)
            Text("${p.name} 님이 보내기로 한 요약만 가족이 볼 수 있어요", color = Dim, fontSize = 15.sp)
            Button("홈으로", { s.screen = Screen.HOME }, modifier = Modifier.width(240.dp))
        }
        return
    }
    // Three ways to hand the code over, because the child is often standing next to the car setting it up:
    // scan the QR, type the code from the screen, or hear it read aloud. A demo shortcut plays the child.
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val c = code
            // With consent, the child's number rides in the QR after '#' (never sent to the server), so a parent who
            // scans it with their own phone gets a text to the child with the recipient already filled in.
            val digits = phone.filter(Char::isDigit)
            val withPhone = agreed && Regex("^01[016789]\\d{7,8}$").matches(digits)
            val qrText = c?.let { inviteLink(it) + if (withPhone) "#to=$digits" else "" }
            val qr = remember(qrText) { qrText?.let { qrBitmap(it) } }
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                if (qr != null) androidx.compose.foundation.Image(qr, "가족 연결 QR 코드", Modifier.fillMaxSize())
                else if (failed) Text("연결할 수 없어요", color = Color(0xFF131417), fontSize = 15.sp)
                else ClockCarLoader(96.dp)
            }
            Text("자녀분 폰으로 찍어 주세요", color = Color(0xFF131417), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (withPhone) Text("자녀 번호가 담겼어요 · 부모님 폰으로 찍으면\n받는 사람이 채워진 문자가 열려요", color = Color(0xFF44464E), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("자녀분 폰과 연결해요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("자녀분 폰으로 QR을 찍거나, 폰 브라우저에서 ${FamilyApi.FAMILY_PAGE}를 열고 이 코드를 넣으면 돼요.", color = Dim, fontSize = 14.sp)
            // Parents often scan it with their own phone; the page then offers to send the link on to the child.
            Text("부모님 폰으로 찍으셨다면, 열린 화면에서 자녀분께 바로 보낼 수 있어요.", color = Dim, fontSize = 14.sp)
            Text(code?.chunked(3)?.joinToString(" ") ?: if (failed) "연결할 수 없어요" else "코드 만드는 중…",
                color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text("10분 동안 유효 · 한 번만 쓸 수 있어요 · 연결을 기다리는 중…", color = Dim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("코드 읽어 주기", { code?.let { speak(spokenCode(it)) } }, Raised, icon = Ic.SPEAKER)
                Button("나중에 연결", { s.screen = Screen.HOME }, Raised)
            }
            // Or text the link to the child's phone. The number is used for this one message and not kept.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("자녀 휴대폰 번호", phone, { phone = it.filter { ch -> ch.isDigit() || ch == '-' }.take(13); sms = Phase.IDLE; smsNote = null; picked = null }, number = true) }
                // The car's contacts (synced from the parent's phone over Bluetooth). The system picker hands back only
                // the one number chosen, so the app needs no permission to read the address book.
                if (canPickContact) Button("연락처", { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, Raised, icon = Ic.PERSON)
                MorphButton("문자로 링크 보내기", sms, if (smsDryRun) "시연 모드" else "보냈어요", enabled = agreed && phone.count(Char::isDigit) >= 10, onClick = {
                    val c = code ?: return@MorphButton
                    val token = p.carToken ?: return@MorphButton
                    sms = Phase.BUSY
                    val started = System.currentTimeMillis()
                    FamilyApi.sendInviteSms(token, c, phone, consent = agreed) { r ->
                        settle(started) {
                            smsDryRun = r.dryRun
                            sms = if (r.error == null) Phase.DONE else Phase.IDLE
                            smsNote = when (r.error) {
                                null -> if (r.dryRun) "시연 서버라 실제 문자는 보내지 않았어요 · ${r.to}" else "${r.to}로 링크를 보냈어요"
                                "invalid_phone" -> "휴대폰 번호를 확인해 주세요"
                                "consent_required" -> "번호 수집·이용에 동의해야 보낼 수 있어요"
                                "invalid_code" -> "코드가 만료됐어요. 이 화면을 다시 열어 주세요"
                                "rate_limited" -> "문자는 한 시간에 5통까지 보낼 수 있어요"
                                else -> "보내지 못했어요. 인터넷 연결을 확인해 주세요"
                            }
                            // Do not keep the number on screen or in memory; the next message needs a fresh consent.
                            if (r.error == null) { phone = ""; agreed = false; picked = null }
                        }
                    }
                })
            }
            // The number is the child's personal information, so its use needs the owner's consent (개인정보 보호법 제15조):
            // what is collected, why, how long it is kept, and that refusing has no penalty (QR and code still work).
            ConsentBox(
                agreed, { agreed = it },
                "[필수] 자녀 휴대폰 번호 수집·이용에 동의해요",
                "항목 휴대폰 번호 · 목적 연결 링크 문자 1통 · 보관 저장하지 않고 보낸 뒤 바로 지움 · 번호 주인(자녀)이 동의한 경우에만 체크해 주세요. 동의하지 않아도 QR이나 코드로 연결할 수 있어요.",
            )
            picked?.let { Text("연락처에서 고름 · $it", color = Dim, fontSize = 13.sp) }
            smsNote?.let { note ->
                Text(note, color = when { sms == Phase.DONE && smsDryRun -> Demo_; sms == Phase.DONE -> Ok; else -> Talk }, fontSize = 13.sp)
            }
            // 시연용: no second phone on stage. Signs up a demo child on the same server and enters this code.
            IconLabel(
                Ic.FAMILY,
                when (demo) { Phase.IDLE -> "시연용 · 가상 자녀(김민수)로 연결하기"; Phase.BUSY -> "가상 자녀가 코드를 넣는 중…"; Phase.DONE -> "연결됐어요" },
                Demo_, 15.sp, FontWeight.Bold,
                Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, DemoBorder, RoundedCornerShape(10.dp))
                    .clickable(enabled = code != null && demo == Phase.IDLE) {
                        val c = code ?: return@clickable
                        val token = p.carToken ?: return@clickable
                        val id = s.profile.id
                        demo = Phase.BUSY
                        FamilyApi.demoChildLinks(c, "김민수", "엄마") { ok ->
                            if (!ok) { demo = Phase.IDLE; return@demoChildLinks }
                            demo = Phase.DONE
                            // Don't wait for the 4-second poll: show "연결됐어요" right away.
                            FamilyApi.me(token) { family, acked, week ->
                                s.updateId(id) { it.copy(family = family, ackedBy = if (week == it.sentWeek) acked else emptyList()) }
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
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
            Text("${p.name} 님, 안녕하세요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (p.name == Demo.PARENT) Text("과거 기록은 시연용  ", color = Demo_, fontSize = 12.sp)
            Chip("월별 기록", selected = false, icon = Ic.CALENDAR) { s.screen = Screen.MONTHLY }
            Spacer(Modifier.width(8.dp))
            StateChip(r.state, weeksRecorded(p))
        }
        if (nightPromise) Banner(Ic.SPEAKER, NIGHT_LINE, "${if (s.night) "21:04" else ""} · 약속 \"해 지면 운전 안 하기\"", Warn) {
            Button("다른 방법으로 가기", { s.screen = Screen.OTHER_WAY })
        } else IconLabel(if (s.night) Ic.MOON else Ic.SUN, "안녕하세요, ${p.name} 님. 오늘도 천천히 다녀오세요.", Ink, 15.sp, iconTint = Warn,
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1.3f).fillMaxHeight()) {
                Text("오늘 어디 가세요?", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Demo.PLACES.forEach { place ->
                        val marked = p.marked.any { it.first == place.first }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Raised).clickable {
                                if (marked) { s.drive = Drive(place.first, false); s.screen = Screen.ROUTE }
                                else DriveService.start(ctx, place.first, comfortable = false)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Glyph(placeIcon(place.first), Accent, 44.dp, place.first)
                            Spacer(Modifier.height(6.dp))
                            Text(place.first, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (marked) IconLabel(Ic.PIN, "표시한 구간 있음", Warn, 12.sp)
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
                SmallCard("이번 주 운전", drive, Modifier.weight(1f)) { s.screen = Screen.DETAIL }
                SmallCard("약속", if (0 in p.promises) "해 지면 안 하기 · 이번 주 밤 운전 ${r.nightDrivesThisWeek}회" else "${p.promises.size}개", Modifier.weight(1f))
                val family = when {
                    p.family.isEmpty() -> "가족 초대하기"
                    p.ackedBy.isNotEmpty() -> "${p.ackedBy.joinToString()} 님이 이번 주 요약을 확인했어요"
                    p.sentWeek == s.week() -> "${p.family.joinToString()} 님에게 보냈어요"
                    else -> "${p.family.joinToString()} 님과 연결됨"
                }
                SmallCard("가족", family, Modifier.weight(1f), bodyIcon = Ic.THUMBS_UP.takeIf { p.ackedBy.isNotEmpty() && p.family.isNotEmpty() }, onClick = { s.screen = Screen.INVITE }.takeIf { p.family.isEmpty() })
                SmallCard("이번 주 카드", if (p.sentWeek == s.week()) "보냈어요" else "부모님이 먼저 봐요", Modifier.weight(1f)) { s.screen = Screen.WEEKLY }
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
        Text("${place}까지 어떤 길로 갈까요?", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f).fillMaxWidth(0.85f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().background(Raised, RoundedCornerShape(14.dp)).clickable { DriveService.start(ctx, place, false) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("빠른 길 · 24분", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("표시한 $junction 구간을 지나요", color = Dim, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("지난번 이곳에서 길 안내를 다시 받았어요", color = Dim, fontSize = 13.sp)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().background(Chosen, RoundedCornerShape(14.dp))
                    .border(2.dp, if (promised) Accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { DriveService.start(ctx, place, true) }.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("편한 길 · 28분", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (promised) Text("약속: 표시한 구간 피하기", color = Demo_, fontSize = 12.sp,
                        modifier = Modifier.background(DemoButton, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("그 구간을 피해요 (+4분)", color = Ink, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("큰길 위주 · 좌회전이 적어요", color = Dim, fontSize = 13.sp)
            }
        }
        // These used to be small grey links; they are real choices, so they get real buttons.
        Row(Modifier.fillMaxWidth(0.85f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("홈으로", { s.drive = null; s.screen = Screen.HOME }, Raised, icon = Ic.ARROW_LEFT)
            Button("오늘은 다른 방법으로 가기", { s.screen = Screen.OTHER_WAY }, Raised, icon = Ic.TAXI)
            Spacer(Modifier.weight(1f))
            Text("두 길의 시간과 경로는 시연용이에요", color = Dim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OtherWay() {
    val s = AppState
    var done by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text(s.drive?.place?.let { "${it}까지 다른 방법으로 가요" } ?: "오늘은 다른 방법으로 가요", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple(Ic.TAXI, "택시 부르기", "가까운 택시를 불러요"), Triple(Ic.CAR, "대리운전", "내 차로 데려다줘요"),
                Triple(Ic.FAMILY, "가족에게 부탁", s.profile.family.firstOrNull()?.let { "$it 님에게 알려요" } ?: "연결된 가족에게 알려요")).forEach { (icon, title, sub) ->
                Column(
                    Modifier.weight(1f).background(Raised, RoundedCornerShape(12.dp)).clickable { done = title }.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Glyph(icon, Accent, 36.dp)
                    Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Panel, RoundedCornerShape(14.dp))) {
        IconLabel(Ic.MAP, "내비게이션 · ${d.place} (${if (d.comfortable) "편한 길" else "빠른 길"})", Dim, 16.sp, modifier = Modifier.padding(16.dp))
        if (d.vehicle && !d.started && d.replay == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("기어를 D로 넣으면 안내를 시작해요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // 시연용: drives the Pleos emulator's Virtual Console (same as tools/demo-drive.py).
            var sent by remember { mutableStateOf(false) }
            if (sent || DemoDriver.running) Text("시연 주행 중 · 곧 기어가 D로 바뀌어요", color = Demo_, fontSize = 14.sp)
            else IconLabel(
                Ic.PLAY, "시연 주행 시작 (Virtual Console)", Demo_, 16.sp, FontWeight.Bold,
                Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, DemoBorder, RoundedCornerShape(10.dp))
                    .clickable { sent = true; DemoDriver.drive(d.comfortable) }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            // 시연용: plays the server's scenario for this trip through the same signal handlers (Replay.kt).
            // Works anywhere the car is parked: the Pleos emulator, a phone emulator or a parked car.
            if (!sent && !DemoDriver.running) IconLabel(
                Ic.PLAY, "시연 재생 (서버 시나리오)", Demo_, 16.sp, FontWeight.Bold,
                Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, DemoBorder, RoundedCornerShape(10.dp))
                    .clickable { DriveService.replay(ctx) }.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            Text("또는 Virtual Console에서 직접 기어를 D로 바꿔도 돼요", color = Dim, fontSize = 12.sp)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp).width(330.dp).background(Bg, RoundedCornerShape(10.dp)).padding(10.dp)) {
            Text(d.replay?.let { "시연 재생 · $it" } ?: "주행 기록 · 화면 조작 없음", color = if (d.replay != null) Demo_ else Dim, fontSize = 12.sp)
            d.log.takeLast(6).forEach { Text(it, color = Ink, fontSize = 12.sp) }
        }
        d.banner?.let {
            Banner(Ic.SPEAKER, it, "한 주행에 최대 2번 · 조작 없음", Accent, Modifier.align(Alignment.BottomCenter).padding(12.dp))
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
            Text("${d.place}에 도착했어요 (P)", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("회전 안내 ${d.turns}번 · 깜빡이 ${d.signals}번 · 재탐색 ${d.reroutes.size}번 · 급제동 ${d.hardBrakes}번" + when { d.replay != null -> " · 시연 재생"; d.vehicle -> " · 차량 신호"; else -> " · 시연 주행" }, color = Dim, fontSize = 13.sp)
        }
        d.banner?.let { line ->
            val c = r?.changed?.firstOrNull()
            Banner(Ic.SPEAKER, line, c?.evidenceLine(), if (c != null) Warn else Accent)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cand != null) Column(
                Modifier.weight(1f).fillMaxHeight().background(AlertBg, RoundedCornerShape(12.dp)).border(1.dp, Warn, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("$cand 전후로 길 안내를 다시 받았어요.\n표시해 둘까요?", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("재탐색 ${d.reroutes.count { it == cand }}번 · 표시하면 다음에 피할 길을 보여 드려요", color = Warn, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("괜찮아요", { dismissed = true }, Raised)
                    Button("표시하기", { s.update { it.copy(marked = it.marked + (d.place to cand)) } }, Primary, Modifier.weight(1f), icon = Ic.PIN)
                }
            } else Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("이번 주행", color = Dim, fontSize = 13.sp)
                Text("회전 안내 ${d.turns}번 · 깜빡이 ${d.signals}번 · 재탐색 ${d.reroutes.size}번 · 급제동 ${d.hardBrakes}번", color = Ink, fontSize = 15.sp)
                if (mine.isNotEmpty()) IconLabel(Ic.PIN, "표시한 구간: ${mine.joinToString(" · ")}", Warn, 13.sp)
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("다른 불편했던 곳이 있었나요?", color = Dim, fontSize = 13.sp)
                points.forEach { pt ->
                    val marked = pt in mine
                    IconLabel(if (marked) Ic.PIN else null, pt, Ink, 15.sp, iconTint = Warn,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Raised)
                            .clickable { s.update { it.copy(marked = if (marked) it.marked - (d.place to pt) else it.marked + (d.place to pt)) } }.padding(12.dp))
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
            BackButton { s.screen = Screen.HOME }
            Text("이번 주 운전 카드 · ${s.week()} 주", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StateChip(r.state, weeksRecorded(p))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClockCar(r.state, 64.dp)
                    Text(w.headline, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                w.evidence.forEach { m ->
                    Text(m.evidenceLine(), color = tone, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                if (r.state == State.TALK) Text("가족과 이야기해 보실래요?", color = Ink, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallCard("잘하신 점", w.goodPoints.joinToString(" · ").ifEmpty { "—" }, Modifier.weight(1f))
                SmallCard("약속", w.promise.ifEmpty { "고른 약속이 없어요" }, Modifier.weight(1f))
                SmallCard("피한 구간", w.avoided.ifEmpty { "표시한 구간이 없어요" }, Modifier.weight(1f))
            }
        }
        val co = rememberCoroutineScope()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                p.family.isEmpty() -> Button("가족 연결하기", { s.screen = Screen.INVITE }, modifier = Modifier.weight(1f))
                else -> {
                    // One button from "send" to spinner to "sent" to "confirmed", in the same place.
                    val phase = when { sending -> Phase.BUSY; sent -> Phase.DONE; else -> Phase.IDLE }
                    MorphButton(
                        "가족에게 보내기", phase,
                        if (p.ackedBy.isNotEmpty()) "${p.ackedBy.joinToString()} 님이 확인했어요" else "보냈어요 · 확인 기다리는 중",
                        {
                            val token = p.carToken ?: return@MorphButton
                            sending = true; error = false
                            val started = System.currentTimeMillis()
                            val id = s.profile.id
                            val week = s.week()
                            FamilyApi.sendSummary(token, week, w) { ok ->
                                settle(started) {
                                    sending = false
                                    if (ok) s.updateId(id) { it.copy(sentWeek = week, ackedBy = emptyList()) } else error = true
                                }
                            }
                        },
                        Modifier.weight(1f), wide = true,
                    )
                    AnimatedVisibility(phase == Phase.IDLE, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Button("이번 주는 안 보내기", { s.screen = Screen.HOME }, Raised)
                    }
                }
            }
            Text(
                when {
                    error -> "보내지 못했어요. 인터넷 연결을 확인해 주세요"
                    p.family.isEmpty() -> "가족을 연결하면 보낼 수 있어요"
                    else -> "보낼 내용: ${if (p.shareScope == "evidence") "상태와 근거" else "상태만"} · ${p.family.joinToString()} 님이 폰으로 봐요"
                },
                color = if (error) Talk else Dim, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Detail(r: Report) {
    val s = AppState
    val p = s.profile
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton { s.screen = Screen.HOME }
            Text("${p.name} 님의 운전 · 이번 주 vs 평소(8주)", color = Dim, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chip("월별 기록", selected = false, icon = Ic.CALENDAR) { s.screen = Screen.MONTHLY }
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
                p.marked.forEach { IconLabel(Ic.PIN, "${it.second} (${it.first})", Warn, 13.sp) }
                if (p.avoidedThisWeek > 0) Text("이번 주 ${p.avoidedThisWeek}번 피함", color = Dim, fontSize = 12.sp)
            }
        }
        p.carToken?.let { token ->
            var busy by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconLabel(
                    if (p.backup == true) Ic.CLOUD else Ic.CAR,
                    if (p.backup == true) "주행 기록을 서버에 보관하고 있어요 · 횟수만, 위치·경로 없음, 가족은 볼 수 없음" else "주행 기록은 이 차에만 있어요",
                    Ink, 14.sp, modifier = Modifier.weight(1f), iconTint = Accent,
                )
                Button(
                    when { busy -> "바꾸는 중…"; p.backup == true -> "보관 끄고 서버 기록 지우기"; else -> "서버에 보관하기" },
                    {
                        if (busy) return@Button
                        busy = true
                        val id = s.profile.id
                        val on = p.backup != true
                        FamilyApi.setBackup(token, on) { ok -> busy = false; if (ok) s.updateId(id) { it.copy(backup = on) } }
                    },
                    Raised,
                )
            }
        }
    }
}

/** A newer build is on the family server: what changed and a QR to download it on a laptop or phone. */
@Composable
private fun UpdatePrompt(u: FamilyApi.AppRelease) {
    val s = AppState
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth(0.66f).background(Panel, RoundedCornerShape(16.dp)).border(1.dp, Accent, RoundedCornerShape(16.dp)).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val qr = remember(u.download) { qrBitmap(u.download) }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                androidx.compose.foundation.Image(qr, "새 버전 내려받기 QR", Modifier.size(170.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("새 버전 ${u.versionName.substringBefore('+')}이 있어요", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (u.notes.isNotBlank()) Text(u.notes, color = Ink, fontSize = 15.sp)
                Text("지금 버전 ${BuildConfig.VERSION_NAME} · 새 버전 ${u.versionName}", color = Dim, fontSize = 13.sp)
                Text("노트북이나 폰으로 QR을 찍어 받은 뒤 설치해 주세요. 차 앱이 스스로 설치하지는 않아요.", color = Dim, fontSize = 13.sp)
                Text(u.download, color = Dim, fontSize = 12.sp)
                Button("닫기", { s.showUpdate = false }, Raised, Modifier.width(160.dp))
            }
        }
    }
}

/** Asked once, right after consent: may this driver's drive counts be kept on the server? */
@Composable
private fun BackupPrompt() {
    val s = AppState
    val p = s.profile
    var phase by remember { mutableStateOf(Phase.IDLE) } // of the 보관하기 button only
    var declining by remember { mutableStateOf(false) }
    val co = rememberCoroutineScope()
    fun choose(on: Boolean) {
        val token = p.carToken ?: run { s.askBackup = false; return }
        if (phase != Phase.IDLE || declining) return
        if (on) phase = Phase.BUSY else declining = true
        val started = System.currentTimeMillis()
        val id = s.profile.id
        FamilyApi.setBackup(token, on) { ok ->
            settle(started) {
                s.updateId(id) { it.copy(backup = on && ok) }
                if (on && ok) co.launch { phase = Phase.DONE; delay(700); s.askBackup = false } // let "보관했어요" be seen
                else s.askBackup = false
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.62f).background(Panel, RoundedCornerShape(16.dp)).border(1.dp, Accent, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("주행 기록을 서버에 보관할까요?", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("보관하면 앱을 다시 설치하거나 차를 바꿔도 ${p.name} 님의 평소 운전 기록이 이어져요.", color = Ink, fontSize = 15.sp)
            Column(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconLabel(Ic.CHECK, "보관하는 것: 주행마다 회전 안내·깜빡이·재탐색·급제동 횟수, 거리, 목적지 이름", Ok, 13.sp)
                IconLabel(Ic.CLOSE, "보관하지 않는 것: 위치, 지나간 경로, 시각", Danger, 13.sp)
                Text("가족은 이 기록을 볼 수 없어요. 가족에게는 보내기로 한 주간 요약만 가요.", color = Dim, fontSize = 13.sp)
                Text("언제든 운전 기록 화면에서 끌 수 있고, 끄면 서버 기록을 바로 지워요.", color = Dim, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Both choices the same size: keeping records on the server must not be the easier tap.
                Button("차에만 두기", { choose(false) }, Raised, Modifier.weight(1f))
                MorphButton("보관하기", phase, "보관했어요", { choose(true) }, Modifier.weight(1f), wide = true)
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
                    .background(if (recentWorse && worse) Warn else Track, RoundedCornerShape(2.dp)))
            }
        }
        Text("12주 전 → 이번 주", color = Dim, fontSize = 12.sp)
    }
}

// ---------- shared bits ----------

@Composable
private fun Banner(icon: Ic, line: String, sub: String?, tone: Color, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) =
    Row(
        modifier.fillMaxWidth().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Glyph(icon, tone, 28.dp)
        Column(Modifier.weight(1f)) {
            Text(line, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Dim, fontSize = 12.sp)
    }

/** A checkbox the size of a finger: the whole row toggles it, not just the small box. */
@Composable
private fun ConsentBox(checked: Boolean, onChange: (Boolean) -> Unit, title: String, detail: String) =
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (checked) Chosen else Raised)
            .clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) Accent else Color.Transparent)
                .border(2.dp, if (checked) Accent else Dim, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Glyph(Ic.CHECK, Color.White, 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Dim, fontSize = 12.sp)
        }
    }

@Composable
private fun Toggle(on: Boolean) =
    Box(Modifier.size(40.dp, 22.dp).background(if (on) Accent else Track, RoundedCornerShape(99.dp)).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(16.dp).background(Color.White, RoundedCornerShape(99.dp)))
    }

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false) =
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Phone else KeyboardType.Text),
    )

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean = true, icon: Ic? = null, onClick: () -> Unit) =
    IconLabel(
        icon, label, if (selected) Ink else Dim, 14.sp, if (selected) FontWeight.Bold else FontWeight.Normal,
        Modifier.clip(RoundedCornerShape(99.dp)).background(if (selected) Raised else Color.Transparent)
            .border(1.dp, if (selected) Accent else Raised, RoundedCornerShape(99.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )

@Composable
private fun SmallCard(title: String, body: String, modifier: Modifier, bodyIcon: Ic? = null, onClick: (() -> Unit)? = null) =
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = Dim, fontSize = 12.sp)
            IconLabel(bodyIcon, body, Ink, 14.sp, maxLines = 2)
        }
        // A card you can open says so with a chevron, instead of a "›" typed into its title.
        if (onClick != null) Glyph(Ic.CHEVRON_RIGHT, Dim, 20.dp)
    }

@Composable
private fun Tile(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)

@Composable
private fun StateChip(s: State, weeks: Int) {
    val c = stateFill(s)
    val sub = if (s == State.LEARNING) "$weeks/12주" else "${weeks}주째 기록"
    // The character's gauge first, then the words: the label stays the reading, the needle and face back it up.
    Row(Modifier.background(c, RoundedCornerShape(99.dp)).padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClockCar(s, 30.dp, description = null)
        Text("${s.label} · $sub", color = OnChip, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Smallest thing a finger should have to hit on the car display (on the 1000 dp canvas, about 80 px on Pleos). */
private val TOUCH = 48.dp

@Composable
private fun Button(label: String, onClick: () -> Unit, bg: Color = Primary, modifier: Modifier = Modifier, icon: Ic? = null) =
    Box(
        modifier.heightIn(min = TOUCH).clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconLabel(icon, label, if (bg == Primary) OnPrimary else Ink, 16.sp, FontWeight.Bold)
    }

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(Modifier.size(TOUCH).clip(RoundedCornerShape(TOUCH / 2)).background(Raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(Ic.ARROW_LEFT, Ink, 24.dp, "뒤로")
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun IconTap(icon: Ic, enabled: Boolean, desc: String, onClick: () -> Unit) =
    Box(Modifier.size(TOUCH).clip(RoundedCornerShape(TOUCH / 2)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, if (enabled) Ink else Raised, 26.dp, desc)
    }

/** One icon from the app's set (Icons.kt), tinted with a theme colour. */
@Composable
private fun Glyph(icon: Ic, tint: Color, size: Dp = 20.dp, desc: String? = null) =
    Icon(painterResource(icon.res), desc, Modifier.size(size), tint = tint)

/** Text with an optional leading icon sized to the text. Replaces the emoji that used to start these lines. */
@Composable
private fun IconLabel(
    icon: Ic?, text: String, color: Color, fontSize: TextUnit, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, iconTint: Color = color, maxLines: Int = Int.MAX_VALUE,
) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) {
        Glyph(icon, iconTint, (fontSize.value * 1.2f).dp)
        Spacer(Modifier.width((fontSize.value * 0.4f).dp))
    }
    Text(text, color = color, fontSize = fontSize, fontWeight = weight, maxLines = maxLines)
}

enum class Phase { IDLE, BUSY, DONE }

/**
 * A button that turns into its own progress and result: the label shrinks into a spinner while the car
 * talks to the server, then opens into a ✓ line. The finger stays on the same spot the whole time
 * (Doherty: something answers at once even when the network takes seconds).
 */
@Composable
private fun MorphButton(label: String, phase: Phase, doneLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false, enabled: Boolean = true) {
    val ms = Motion.ms(Motion.BUTTON_MS)
    val bg by animateColorAsState(when { phase == Phase.DONE -> OkFill.copy(alpha = 0.16f); !enabled -> Raised; else -> Primary }, tween(ms), label = "morph-bg")
    val corner by animateDpAsState(if (phase == Phase.BUSY) TOUCH / 2 else 10.dp, tween(ms), label = "morph-corner")
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            // Shrinking waits until the label has faded (ms / 2), so the label is never cut mid-word.
            Modifier.animateContentSize(tween(ms, delayMillis = if (phase == Phase.BUSY) ms / 2 else 0, easing = FastOutSlowInEasing))
                .then(if (phase == Phase.BUSY) Modifier.size(TOUCH) else if (wide) Modifier.fillMaxWidth().heightIn(min = TOUCH) else Modifier.heightIn(min = TOUCH))
                .clip(RoundedCornerShape(corner))
                .background(bg)
                .clickable(enabled = enabled && phase == Phase.IDLE, onClick = onClick)
                .padding(horizontal = if (phase == Phase.BUSY) 0.dp else 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(phase, transitionSpec = {
                (fadeIn(tween(ms)) + scaleIn(tween(ms), initialScale = 0.8f)) togetherWith (fadeOut(tween(ms / 2)) + scaleOut(tween(ms / 2), targetScale = 0.8f))
            }, label = "morph") { ph ->
                when (ph) {
                    Phase.IDLE -> Text(label, color = if (enabled) OnPrimary else Dim, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                    Phase.BUSY -> ClockCarLoader(40.dp)
                    Phase.DONE -> IconLabel(Ic.CHECK, doneLabel, Ink, 16.sp, FontWeight.Bold, iconTint = Ok)
                }
            }
        }
    }
}

/**
 * Runs [block] no sooner than [Motion.MIN_BUSY_MS] after [startedAt], so a fast answer does not flicker.
 * Posted to the main thread rather than a composition's scope: leaving the screen must not cancel
 * saving what the server already did (a sent summary, a new token).
 */
private val settleHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
private fun settle(startedAt: Long, block: () -> Unit) {
    settleHandler.postDelayed(block, (Motion.MIN_BUSY_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
}

// ---------- 월별 운전 기록, 운전 졸업 ----------

@Composable
private fun stateColor(st: State) = when (st) { State.STABLE -> Ok; State.WATCH -> Warn; State.TALK -> Talk; State.LEARNING -> Dim }

/** Fill for chips and bars: the Pleos informative colours, with dark text on top. */
@Composable
private fun stateFill(st: State) = when (st) { State.STABLE -> OkFill; State.WATCH -> WarnFill; State.TALK -> TalkFill; State.LEARNING -> Raised }

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
            BackButton { s.screen = Screen.HOME }
            Text("${p.name} 님의 월별 운전 기록", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconTap(Ic.CHEVRON_LEFT, enabled = true, "이전 달") { offset++ }
            Text("${month.year}년 ${month.monthValue}월", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconTap(Ic.CHEVRON_RIGHT, enabled = offset > 0, "다음 달") { offset-- }
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
                        { Text("${row.monday.monthValue}/${row.monday.dayOfMonth} 주" + if (row.k == 0) " (이번 주)" else "", color = Ink, fontSize = 14.sp, modifier = it) },
                        { Text(st.label, color = stateColor(st), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = it) },
                        { Text("${row.trips}회 · ${"%.0f".format(row.km)}km", color = Ink, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.REROUTE), color = Ink, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.SIGNAL), color = Ink, fontSize = 13.sp, modifier = it) },
                        { Text(cell(Metric.BRAKE), color = Ink, fontSize = 13.sp, modifier = it) },
                        { Text("${row.night}회", color = if (row.night > 0) Warn else Ink, fontSize = 13.sp, modifier = it) },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (rows.isNotEmpty()) {
                    val flow = rows.reversed().map { it.report.state.label }.fold(listOf<String>()) { acc, x -> if (acc.lastOrNull() == x) acc else acc + x }
                    Text(
                        "이 달 주행 ${rows.sumOf { it.trips }}회 · ${"%.0f".format(rows.sumOf { it.km })}km · 밤 운전 ${rows.sumOf { it.night }}회 · 상태 ${flow.joinToString(" → ")}",
                        color = Dim, fontSize = 13.sp,
                    )
                    if (p.name == Demo.PARENT || p.name == "박영호") Text("지난 기록은 시연용이에요", color = Demo_, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(tone.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).border(1.dp, tone, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("지금 운전 상태", color = Dim, fontSize = 12.sp)
                    Text(step.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(step.body, color = Ink, fontSize = 14.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("다음에 해 볼 수 있는 것", color = Dim, fontSize = 12.sp)
                    step.steps.forEach { Text("· $it", color = tone, fontSize = 14.sp) }
                }
                Text("DriveWatch는 운전 능력을 진단하지 않아요. 평소와 달라진 점을 알려 드릴 뿐이에요.", color = Dim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = TOUCH).clip(RoundedCornerShape(10.dp)).background(Panel).clickable { s.screen = Screen.GRADUATE }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("운전을 그만두기로 하셨나요?  운전 졸업 안내", color = Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Glyph(Ic.CHEVRON_RIGHT, Dim, 20.dp)
                }
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
            BackButton { s.screen = Screen.MONTHLY }
            Text("운전 졸업", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("오랫동안 안전하게 운전해 오셨어요. 계속할지, 여기서 마칠지는 ${p.name} 님이 정하시는 일이에요.", color = Ink, fontSize = 16.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Text("면허를 반납하려면", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("가까운 경찰서나 운전면허시험장에서 반납할 수 있어요.", color = Ink, fontSize = 14.sp)
                Text("지자체마다 교통비 지원 같은 반납 혜택이 있어요. 주소지 주민센터에서 확인해 보세요.", color = Dim, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("마음이 바뀌면 언제든 다시 운전 기록을 이어 갈 수 있어요.", color = Dim, fontSize = 13.sp)
            }
            Tile(Modifier.weight(1.3f).fillMaxHeight()) {
                Text("자주 가시던 곳은 이렇게 다녀요", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Demo.PLACES.forEach { (place, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconLabel(placeIcon(place), place, Ink, 16.sp, modifier = Modifier.weight(1f), iconTint = Accent)
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
