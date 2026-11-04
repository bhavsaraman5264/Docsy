package app.kholo.reader.debug

import android.os.StrictMode

/** Main thread par disk ya network kaam aur leaked resources yahin pakde jayenge. */
fun installStrictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build(),
    )
}
