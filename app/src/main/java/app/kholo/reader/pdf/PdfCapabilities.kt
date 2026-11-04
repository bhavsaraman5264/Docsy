package app.kholo.reader.pdf

import android.os.Build
import android.os.ext.SdkExtensions

private const val ANDROID_V = 35
private const val ANDROID_R = 30
private const val REQUIRED_EXTENSION = 13

/**
 * Kya phone par password, search, text copy aur links chalte hain?
 *
 * Yeh `androidx.pdf` ke apne niyam ki copy hai. Fallback engine par sahi password bhi
 * "Incorrect password" deta hai, isliye password maangne se pehle yahan dekhna zaroori hai.
 */
data class PdfCapabilities(val fullFeatures: Boolean) {
    companion object {
        fun from(sdkInt: Int, extensionS: Int): PdfCapabilities = PdfCapabilities(
            fullFeatures = sdkInt >= ANDROID_V || (sdkInt >= ANDROID_R && extensionS >= REQUIRED_EXTENSION),
        )

        fun current(): PdfCapabilities {
            val extension = if (Build.VERSION.SDK_INT >= ANDROID_R) {
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
            } else {
                0
            }
            return from(Build.VERSION.SDK_INT, extension)
        }
    }
}
