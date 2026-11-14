package com.drivewatch.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.drivewatch.core.family.CarAccount
import com.drivewatch.core.family.CarView
import com.drivewatch.core.family.FamilyClient
import com.drivewatch.core.family.FamilySession
import com.drivewatch.core.family.FamilyStorage
import com.drivewatch.core.family.FamilySummary
import com.drivewatch.core.family.Invite
import com.drivewatch.core.family.ShareScope
import com.drivewatch.core.family.SummaryReceipt
import java.util.concurrent.Executors

/**
 * 차량 앱에서 가족 서버를 부르는 입구. 통신은 뒤에서 하고, 결과는 메인 스레드에서 [Result]로 돌려준다.
 * 실패하면 [Result.exceptionOrNull]이 [com.drivewatch.core.family.FamilyError]다.
 * 등록 정보는 앱의 SharedPreferences에 남는다. 앱 데이터를 지우면 새 차로 등록된다.
 */
class FamilyLink(context: Context, baseUrl: String = FamilyClient.PUBLIC_URL) {
    private val prefs = context.applicationContext.getSharedPreferences("drivewatch.family", Context.MODE_PRIVATE)
    private val session = FamilySession(FamilyClient(baseUrl), object : FamilyStorage {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit()
        }
    })
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 이 차의 등록 정보. 등록 전이면 null. */
    val account: CarAccount? get() = session.account

    /** 보내지 못해 남아 있는 요약이 있는지. */
    val hasPending: Boolean get() = session.hasPending

    private fun <T> run(done: (Result<T>) -> Unit, work: () -> T) {
        worker.execute {
            val result = runCatching(work)
            main.post { done(result) }
        }
    }

    /** 처음 한 번 등록한다. 이미 등록했으면 서버를 부르지 않는다. */
    fun register(name: String, shareScope: ShareScope = ShareScope.STATUS, done: (Result<CarAccount>) -> Unit) =
        run(done) { session.register(name, shareScope) }

    /** 가족이 폰에 넣을 코드를 새로 만든다. 10분 동안 한 번만 쓸 수 있다. */
    fun invite(done: (Result<Invite>) -> Unit) = run(done) { session.invite() }

    /** 가족이 폰으로 열 주소. QR에 담는다. */
    fun joinUrl(invite: Invite): String = session.joinUrl(invite)

    /** 누가 연결됐는지, 마지막 요약을 누가 확인했는지. */
    fun view(done: (Result<CarView>) -> Unit) = run(done) { session.view() }

    fun setShareScope(shareScope: ShareScope, done: (Result<ShareScope>) -> Unit) =
        run(done) { session.setShareScope(shareScope) }

    /** 운전자가 `가족에게 보내기`를 눌렀을 때 부른다. 서버에 닿지 못하면 남겨 뒀다가 [retryPending]으로 다시 보낸다. */
    fun send(summary: FamilySummary, done: (Result<SummaryReceipt>) -> Unit) = run(done) { session.send(summary) }

    /** 남아 있는 요약을 다시 보낸다. 남은 것이 없으면 null. 앱을 열 때 부른다. */
    fun retryPending(done: (Result<SummaryReceipt?>) -> Unit) = run(done) { session.retryPending() }

    /** 이 차의 등록 정보를 지운다. 서버의 기록은 지우지 않는다. */
    fun forget(done: (Result<Unit>) -> Unit = {}) = run(done) { session.forget() }

    fun close() {
        worker.shutdown()
    }
}
