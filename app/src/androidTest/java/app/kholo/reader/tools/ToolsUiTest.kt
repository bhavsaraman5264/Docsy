package app.kholo.reader.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kholo.reader.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WAIT_MS = 60_000L
private const val TAG = "ToolsUiTest"

/** Asli ToolsActivity par poora kaam: file do, button dabao, nateeja dekho. Screenshots `files/shots` me. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ToolsUiTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val shots: File get() = File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun text(id: Int): String = app.getString(id)

    private fun shot(name: String) {
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().asAndroidBitmap()
        File(shots, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shared(file: File): Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)

    private fun fixture(name: String): Uri {
        val file = File(app.cacheDir, "open/$name").apply { parentFile?.mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return shared(file)
    }

    private fun photoPdf(): Uri {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 1200 step 3) {
            for (x in 0 until 1600 step 3) {
                bitmap.setPixel(x, y, Color.rgb(x % 255, y % 255, (x * y) % 255))
            }
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val file = File(app.cacheDir, "open/ui-photos.pdf").apply { parentFile?.mkdirs() }
        val tools = PdfTools(app, File(app.cacheDir, "ui-scratch"))
        tools.photosToPdf(List(3) { { ByteArrayInputStream(bytes) } }, file)
        return shared(file)
    }

    private fun run(kind: ToolKind, uri: Uri, block: () -> Unit) {
        ActivityScenario.launch<ToolsActivity>(ToolsActivity.intent(app, kind).setData(uri)).use { block() }
    }

    private fun awaitText(value: String) = rule.waitUntilAtLeastOneExists(hasText(value), WAIT_MS)

    /** Top bar me bhi tool ka naam hota hai, isliye sirf dabne wala node. */
    private fun tap(value: String) = rule.onNode(hasText(value) and hasClickAction()).performClick()

    private fun finishes(name: String) {
        awaitText(text(R.string.tool_done))
        shot("$name-done")
    }

    @Test
    fun compressRunsAndShowsTheSaving() {
        run(ToolKind.Compress, photoPdf()) {
            awaitText(text(R.string.tool_target_size))
            shot("compress-form")
            tap(text(R.string.compress_smallest))
            tap(text(R.string.tool_compress))
            finishes("compress")
            assertTrue(rule.onAllNodes(hasText("smaller", substring = true)).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun organizeSelectsRotatesRemovesAndSaves() {
        run(ToolKind.Organize, fixture("mixed-sizes.pdf")) {
            awaitText(text(R.string.action_move_ahead))
            rule.waitForIdle()
            shot("organize-form")
            tap(text(R.string.action_rotate))
            tap(text(R.string.action_move_ahead))
            rule.onNode(hasText(text(R.string.action_move_back)) and hasClickAction()).assertIsEnabled()
            tap(text(R.string.action_remove))
            rule.waitUntilDoesNotExist(hasText(app.getString(R.string.tool_page, 1)), WAIT_MS)
            shot("organize-edited")
            tap(text(R.string.action_save))
            finishes("organize")
        }
    }

    @Test
    fun splitMakesTwoFiles() {
        run(ToolKind.Split, fixture("long-500-pages.pdf")) {
            awaitText(text(R.string.tool_split_after))
            awaitText(app.getString(R.string.tool_split_preview, 1, 250, 251, 500))
            shot("split-form")
            tap(text(R.string.tool_split))
            finishes("split")
            rule.onNode(hasText("long-500-pages-part-1.pdf")).assertExists()
            rule.onNode(hasText("long-500-pages-part-2.pdf")).assertExists()
        }
    }

    @Test
    fun extractMakesOneFile() {
        run(ToolKind.Extract, fixture("long-500-pages.pdf")) {
            awaitText(text(R.string.tool_from_page))
            shot("extract-form")
            tap(text(R.string.tool_extract))
            finishes("extract")
        }
    }

    @Test
    fun removingAMissingPasswordSaysSo() {
        run(ToolKind.Unlock, fixture("simple.pdf")) {
            rule.waitUntilAtLeastOneExists(isEditable(), WAIT_MS)
            rule.onNode(isEditable()).performTextInput("anything")
            tap(text(R.string.tool_unlock))
            finishes("unlock")
            rule.onNode(hasText(text(R.string.unlock_not_locked))).assertExists()
        }
    }

    @Test
    fun shrinkLeavesASmallPhotoAlone() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val file = File(app.cacheDir, "open/tiny.jpg").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        run(ToolKind.ShrinkPhoto, shared(file)) {
            awaitText(text(R.string.tool_target_size))
            tap(text(R.string.tool_shrink_photo))
            finishes("shrink")
            rule.onNode(hasText(text(R.string.compress_already_small))).assertExists()
        }
    }

    @Test
    fun lockRuns() {
        run(ToolKind.Lock, fixture("simple.pdf")) {
            rule.waitUntilAtLeastOneExists(isEditable(), WAIT_MS)
            rule.onNode(isEditable()).performTextInput("secret1")
            shot("lock-form")
            tap(text(R.string.tool_lock))
            finishes("lock")
        }
    }

    @Test
    fun pdfToTextRuns() {
        run(ToolKind.Text, fixture("simple.pdf")) {
            awaitText(text(R.string.action_change))
            tap(text(R.string.tool_text))
            finishes("text")
        }
    }

    @Test
    fun pdfToJpgRuns() {
        run(ToolKind.PdfToImages, fixture("mixed-sizes.pdf")) {
            awaitText(text(R.string.action_change))
            tap(text(R.string.tool_pdf_to_jpg))
            finishes("jpg")
        }
    }

    @Test
    fun mergeWaitsForTwoFiles() {
        run(ToolKind.Merge, fixture("simple.pdf")) {
            awaitText(text(R.string.tool_merge_need_two))
            shot("merge-form")
        }
    }
}

/** Tools tab asli theme aur asli phone size par. */
@RunWith(AndroidJUnit4::class)
class ToolGridUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun everyToolIsReachableAndOpens() {
        var opened: ToolKind? = null
        rule.setContent { app.kholo.reader.ui.theme.KholoTheme(darkTheme = false) { ToolGrid { opened = it } } }
        val context = rule.activity
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        rule.waitForIdle()
        rule.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(dir, "grid-top.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (tool in ToolKind.entries) {
            val title = context.getString(tool.title)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(title))
            rule.onNode(hasText(title)).performClick()
            rule.runOnIdle { assertTrue(title, opened == tool) }
        }
        rule.waitForIdle()
        rule.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(dir, "grid-bottom.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun darkThemeGrid() {
        rule.setContent { app.kholo.reader.ui.theme.KholoTheme(darkTheme = true) { ToolGrid {} } }
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        rule.waitForIdle()
        rule.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(dir, "grid-dark.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/** Sirf naap: phone par kitna samay laga, logcat me `ToolSpeed`. */
@RunWith(AndroidJUnit4::class)
class ToolSpeedTest {
    private val app: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun timed(label: String, block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        val ms = (System.nanoTime() - start) / 1_000_000
        Log.i("ToolSpeed", "$label: $ms ms")
        return ms
    }

    @Test
    fun measure() {
        val dir = File(app.cacheDir, "speed")
        dir.deleteRecursively()
        dir.mkdirs()
        val tools = PdfTools(app, File(dir, "scratch"))
        val long = File(dir, "long.pdf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("long-500-pages.pdf").use { input ->
            long.outputStream().use { input.copyTo(it) }
        }
        tools.warmUp()
        timed("pageCount native 500p") { assertTrue(tools.pageCount(long) == 500) }
        timed("pageCount pdfbox 500p") { tools.read(long) { assertTrue(it.numberOfPages == 500) } }

        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(200, 180, 150))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        for (i in 0 until 3000 step 7) {
            paint.color = Color.rgb(i % 255, (i * 3) % 255, (i * 7) % 255)
            canvas.drawLine(0f, i.toFloat(), 4000f, (3000 - i).toFloat(), paint)
        }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        val photos = List(12) { { ByteArrayInputStream(bytes) } }
        val one = File(dir, "one.pdf")
        val many = File(dir, "many.pdf")
        val serial = timed("photos 12 serial") { tools.photosToPdf(photos, one, workers = 1) }
        val parallel = timed("photos 12 parallel") { tools.photosToPdf(photos, many) }
        Log.i("ToolSpeed", "photos speedup ${serial * 100 / parallel.coerceAtLeast(1)}%")

        timed("compress smallest") { tools.compress(many, 0L, File(dir, "c0.pdf")) }
        timed("compress to third") { tools.compress(many, many.length() / 3, File(dir, "c1.pdf")) }
        dir.deleteRecursively()
    }
}
