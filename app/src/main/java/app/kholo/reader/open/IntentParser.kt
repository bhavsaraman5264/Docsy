package app.kholo.reader.open

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object IntentParser {
    /** VIEW me URI `data` me aata hai, SEND me `EXTRA_STREAM` me. */
    fun parse(intent: Intent?): OpenRequest? {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        return uri?.let(::OpenRequest)
    }
}
