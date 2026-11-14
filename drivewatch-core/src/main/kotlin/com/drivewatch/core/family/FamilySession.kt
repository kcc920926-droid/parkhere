package com.drivewatch.core.family

/** 차에 남겨 둘 값을 읽고 쓰는 곳. Android에서는 SharedPreferences가 맡는다. */
interface FamilyStorage {
    fun get(key: String): String?

    /** null을 주면 지운다. */
    fun put(key: String, value: String?)
}

class MemoryStorage : FamilyStorage {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/**
 * 차 한 대와 가족 서버 사이의 연결. 등록 정보를 차에 저장하고, 보내지 못한 요약을 남겨 뒀다가 다시 보낸다.
 * 모든 호출은 답이 올 때까지 기다린다.
 */
class FamilySession(private val client: FamilyClient, private val storage: FamilyStorage) {
    /** 이 서버에 등록한 정보. 등록 전이거나 다른 서버에 등록했으면 null. */
    val account: CarAccount?
        get() {
            if (storage.get(SERVER) != client.baseUrl) return null
            val id = storage.get(DRIVER_ID) ?: return null
            val token = storage.get(CAR_TOKEN) ?: return null
            return CarAccount(id, token, ShareScope.of(storage.get(SHARE_SCOPE)))
        }

    /** 보내지 못해 남아 있는 요약이 있는지. */
    val hasPending: Boolean get() = account != null && storage.get(PENDING) != null

    private fun token(): String = account?.carToken ?: throw FamilyError(FamilyError.NOT_REGISTERED)

    /** 처음 한 번 등록한다. 이미 등록했으면 서버를 부르지 않고 저장된 값을 돌려준다. */
    fun register(name: String, shareScope: ShareScope = ShareScope.STATUS): CarAccount {
        account?.let { return it }
        val created = client.registerDriver(name, shareScope)
        storage.put(PENDING, null)
        storage.put(SERVER, client.baseUrl)
        storage.put(DRIVER_ID, created.driverId)
        storage.put(CAR_TOKEN, created.carToken)
        storage.put(SHARE_SCOPE, created.shareScope.key)
        return created
    }

    fun invite(): Invite = client.createInvite(token())

    fun joinUrl(invite: Invite): String = client.joinUrl(invite)

    fun view(): CarView = client.me(token())

    fun setShareScope(shareScope: ShareScope): ShareScope =
        client.setShareScope(token(), shareScope).also { storage.put(SHARE_SCOPE, it.key) }

    /**
     * 요약을 보낸다. 서버에 닿지 못했거나 서버가 잠시 못 받으면 요약을 남겨 두고 [FamilyError]를 던진다
     * ([FamilyError.retryable]). 서버가 거절한 요약은 남기지 않는다.
     */
    fun send(summary: FamilySummary): SummaryReceipt {
        val token = token()
        val json = summary.toJson()
        storage.put(PENDING, json)
        return deliver(token, json)
    }

    /** 남아 있는 요약을 다시 보낸다. 남은 것이 없으면 null. 통신이 돌아왔을 때나 앱을 열 때 부른다. */
    fun retryPending(): SummaryReceipt? {
        val token = token()
        val json = storage.get(PENDING) ?: return null
        return deliver(token, json)
    }

    private fun deliver(token: String, json: String): SummaryReceipt = try {
        client.sendSummaryJson(token, json).also { storage.put(PENDING, null) }
    } catch (error: FamilyError) {
        if (!error.retryable) storage.put(PENDING, null)
        throw error
    }

    /** 이 차의 등록 정보를 지운다. 서버의 기록은 지우지 않는다. */
    fun forget() = KEYS.forEach { storage.put(it, null) }

    private companion object {
        const val SERVER = "server"
        const val DRIVER_ID = "driverId"
        const val CAR_TOKEN = "carToken"
        const val SHARE_SCOPE = "shareScope"
        const val PENDING = "pendingSummary"
        val KEYS = listOf(SERVER, DRIVER_ID, CAR_TOKEN, SHARE_SCOPE, PENDING)
    }
}
