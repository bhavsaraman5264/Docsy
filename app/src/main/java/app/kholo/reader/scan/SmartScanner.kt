package app.kholo.reader.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Google ka document scanner: page ke kinare khud pakadta hai, kaat kar seedha karta hai, aur ungli ya daag hatata
 * hai. Scanner aur uska model Google Play Services me rehte hain, isliye app me sirf ~300 KB judta hai aur camera
 * ki permission nahi lagti. Pehli baar Play Services ek chhota hissa download karta hai, phir sab phone par chalta hai.
 *
 * Jahan Play Services nahi (kuch Huawei, custom ROM), wahan [start] `onUnavailable` bulata hai aur purana camera
 * chalta hai.
 */
class SmartScanner(private val activity: Activity) {
    fun start(pageLimit: Int, onReady: (IntentSender) -> Unit, onUnavailable: () -> Unit) {
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        if (playServices != ConnectionResult.SUCCESS) {
            onUnavailable()
            return
        }
        // Manifest se app-start wala init hataya hai, taaki app jaldi khule. Pehli baar yahin hota hai.
        MlKitContext.initializeIfNeeded(activity.applicationContext)
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit.coerceAtLeast(1))
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener(activity) { onReady(it) }
            .addOnFailureListener(activity) { onUnavailable() }
    }

    companion object {
        /** Scanner ke pages, kram se. User ne beech me band kiya to khaali. */
        fun pages(data: Intent?): List<Uri> =
            GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.map { it.imageUri }.orEmpty()
    }
}
