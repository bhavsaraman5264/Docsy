package app.kholo.reader.core.log

import android.os.SystemClock
import androidx.tracing.trace

/** Block ka time PERF log me likhta hai aur Perfetto/Macrobenchmark ke liye trace section banata hai. */
inline fun <T> timed(label: String, block: () -> T): T {
    val start = SystemClock.elapsedRealtime()
    val result = trace(label) { block() }
    val elapsed = SystemClock.elapsedRealtime() - start
    AppLog.i(LogTag.PERF) { "$label took=${elapsed}ms" }
    return result
}
