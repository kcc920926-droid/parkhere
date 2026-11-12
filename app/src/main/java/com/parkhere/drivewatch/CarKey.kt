package com.parkhere.drivewatch

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A key for "this car", so the family server can give a driver back the same server record after a
 * reinstall or a repeated consent (and so keep family links and kept drives).
 *
 * Source, in order: the VIN from the Pleos Vehicle SDK (`getCarInfo().getVINInfo()`, permissions
 * `pleos.car.permission.CAR_INFO` and `PRIVATE_CAR_INFO`, both normal), else ANDROID_ID (this app and
 * Android user on this head unit; survives reinstall with the same signing key, not a factory reset).
 * The Pleos emulator answers the VIN with "invalid", so there the fallback is used.
 *
 * Only an HMAC of the value leaves the car, never the VIN. This is pseudonymous, not anonymous: the
 * salt ships in the APK, so it keeps the VIN out of server data and logs but is not a secret.
 */
object CarKey {
    private const val SALT = "drivewatch-car-key-v1"
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Which source the current key came from: "vin" or "android_id". For logs and the README, not sent. */
    var source: String? = null
        private set

    private var cached: String? = null

    fun hash(kind: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(SALT.toByteArray(), "HmacSHA256")) }
        return mac.doFinal("$kind:$value".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    /** A VIN is 17 characters of letters and digits (no I, O, Q). Anything else, like the emulator's "invalid", is not one. */
    fun validVin(raw: String?): String? = raw?.trim()?.trim('"')?.uppercase()?.takeIf { Regex("^[A-HJ-NPR-Z0-9]{17}$").matches(it) }

    /** Calls back on the main thread, within about 3 s, with the key (null only if even ANDROID_ID is missing). */
    fun get(context: Context, done: (String?) -> Unit) {
        cached?.let { return done(it) }
        val app = context.applicationContext
        var answered = false
        fun answer(kind: String, value: String?) {
            if (answered) return
            answered = true
            val key = value?.let { hash(kind, it) }
            if (key != null) { cached = key; source = kind }
            android.util.Log.i("DriveWatch", "car key from ${if (key == null) "nothing" else kind}")
            done(key)
        }
        fun androidId() = answer("android_id", Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() })
        val vehicle = runCatching { Vehicle(app).also { it.initialize() } }.getOrNull() ?: return androidId()
        // The vehicle service binds a moment after initialize() (same wait as VehicleLink).
        main.postDelayed({
            runCatching {
                vehicle.getCarInfo().getVINInfo(
                    { vin -> main.post { validVin(vin)?.let { answer("vin", it) } ?: androidId(); runCatching { vehicle.release() } } },
                    { main.post { androidId(); runCatching { vehicle.release() } } },
                )
            }.onFailure { androidId() }
        }, 2_000)
        main.postDelayed({ androidId() }, 3_500) // no answer from the vehicle service
    }
}
