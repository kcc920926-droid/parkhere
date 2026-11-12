package com.parkhere.drivewatch

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Black-on-white QR code. Always dark modules on white, whatever the theme, so phone cameras read it. */
fun qrBitmap(text: String, size: Int = 480): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF131417.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The phone page with the code filled in (family-server/public/app.js reads ?code=). */
fun inviteLink(code: String) = "${FamilyApi.BASE}/family/?code=$code"

private val DIGITS = listOf("공", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")

/** "526375" read one digit at a time, so the voice does not say "오십이만…". */
fun spokenCode(code: String) =
    "가족 연결 코드는 " + code.map { DIGITS[it.digitToInt()] }.joinToString(", ") + " 이에요. 다시 말씀드릴게요. " +
        code.map { DIGITS[it.digitToInt()] }.joinToString(", ") + "."
