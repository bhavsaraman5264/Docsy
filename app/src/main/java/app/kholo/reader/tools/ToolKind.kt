package app.kholo.reader.tools

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kholo.reader.R
import app.kholo.reader.ui.theme.ToolTone

private const val PDF_MIME = "application/pdf"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val DOC_MIME = "application/msword"

/** Kuch file managers Word file ko sirf "binary" batate hain. Asli pehchan file ke bytes se hoti hai. */
private const val ANY_BINARY_MIME = "application/octet-stream"

enum class ToolInputType(val suffix: String, val label: String, val mimes: Array<String>) {
    Pdf(".pdf", "PDF", arrayOf(PDF_MIME)),
    Word(".docx", "DOC", arrayOf(DOCX_MIME, DOC_MIME, ANY_BINARY_MIME)),
    Text(".txt", "TXT", arrayOf("text/plain")),
    Image(".jpg", "JPG", arrayOf("image/*")),
}

enum class ToolGroup(@param:StringRes val title: Int) {
    Pdf(R.string.tools_group_pdf),
    Convert(R.string.tools_group_convert),
    Photo(R.string.tools_group_photo),
}

/** Yahan ka order hi Tools tab ka order hai. Row ka akela aakhri tool poori chaudai leta hai. */
enum class ToolKind(
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    @param:DrawableRes val icon: Int,
    val group: ToolGroup,
    val input: ToolInputType = ToolInputType.Pdf,
) {
    Compress(R.string.tool_compress, R.string.tool_compress_hint, R.drawable.ic_compress, ToolGroup.Pdf),
    Merge(R.string.tool_merge, R.string.tool_merge_hint, R.drawable.ic_merge, ToolGroup.Pdf),
    Split(R.string.tool_split, R.string.tool_split_hint, R.drawable.ic_cut, ToolGroup.Pdf),
    Extract(R.string.tool_extract, R.string.tool_extract_hint, R.drawable.ic_extract, ToolGroup.Pdf),
    Lock(R.string.tool_lock, R.string.tool_lock_hint, R.drawable.ic_lock, ToolGroup.Pdf),
    Unlock(R.string.tool_unlock, R.string.tool_unlock_hint, R.drawable.ic_lock_open, ToolGroup.Pdf),
    Organize(R.string.tool_organize, R.string.tool_organize_hint, R.drawable.ic_swap, ToolGroup.Pdf),
    PdfToWord(R.string.tool_pdf_to_word, R.string.tool_pdf_to_word_hint, R.drawable.ic_description, ToolGroup.Convert),
    WordToPdf(
        R.string.tool_word_to_pdf,
        R.string.tool_word_to_pdf_hint,
        R.drawable.ic_description,
        ToolGroup.Convert,
        ToolInputType.Word,
    ),
    TextToPdf(
        R.string.tool_text_to_pdf,
        R.string.tool_text_to_pdf_hint,
        R.drawable.ic_description,
        ToolGroup.Convert,
        ToolInputType.Text,
    ),
    Text(R.string.tool_text, R.string.tool_text_hint, R.drawable.ic_description, ToolGroup.Convert),
    Scan(R.string.tool_scan, R.string.tool_scan_hint, R.drawable.ic_camera, ToolGroup.Photo, ToolInputType.Image),
    Photos(
        R.string.tool_photos,
        R.string.tool_photos_hint,
        R.drawable.ic_image,
        ToolGroup.Photo,
        ToolInputType.Image,
    ),
    PdfToImages(R.string.tool_pdf_to_jpg, R.string.tool_pdf_to_jpg_hint, R.drawable.ic_image, ToolGroup.Photo),
    ShrinkPhoto(
        R.string.tool_shrink_photo,
        R.string.tool_shrink_photo_hint,
        R.drawable.ic_compress,
        ToolGroup.Photo,
        ToolInputType.Image,
    ),
    ;

    val multipleInputs: Boolean get() = this == Photos || this == Scan || this == Merge

    /** Sirf chhota karne wale tools me "pehle aur baad ka size" ka matlab hai. Baaki me file hi doosri hai. */
    val comparesSize: Boolean get() = this == Compress || this == ShrinkPhoto

    /** Badalne wale tools ka badge batata hai kya kismein badlega. Baaki ka badge icon hai. */
    val flow: Pair<String, String>?
        get() = when (this) {
            PdfToWord -> "PDF" to "DOC"
            WordToPdf -> "DOC" to "PDF"
            TextToPdf -> "TXT" to "PDF"
            Text -> "PDF" to "TXT"
            Photos -> "JPG" to "PDF"
            PdfToImages -> "PDF" to "JPG"
            else -> null
        }

    val tone: ToolTone
        get() = when (this) {
            Compress, Extract -> ToolTone.Orange
            Merge -> ToolTone.Blue
            Organize -> ToolTone.Plum
            Lock -> ToolTone.Red
            Unlock, Scan -> ToolTone.Green
            else -> ToolTone.Teal
        }
}

/** [after] page ke baad kaatne par bane do hisse, 0-based. Koi page chhootta nahi. Galat jagah par null. */
internal fun splitParts(pageCount: Int, after: Int): List<IntRange>? =
    if (after in 1 until pageCount) listOf(0 until after, after until pageCount) else null

/** Kitne percent chhoti hui. Farq na ho ya file badi ho gayi ho to null, tab kuch dikhana nahi. */
internal fun sizeSavedPercent(before: Long, after: Long): Int? {
    if (before <= 0L || after >= before) return null
    return ((before - after) * PERCENT / before).toInt().takeIf { it > 0 }
}

private const val PERCENT = 100
private const val BYTES_50_KB = 50L * 1024
private const val BYTES_100_KB = 100L * 1024
private const val BYTES_200_KB = 200L * 1024
private const val BYTES_500_KB = 500L * 1024
private const val BYTES_1_MB = 1024L * 1024

enum class CompressTarget(val bytes: Long, @param:StringRes val label: Int) {
    Smallest(0L, R.string.compress_smallest),
    Kb100(BYTES_100_KB, R.string.compress_100),
    Kb200(BYTES_200_KB, R.string.compress_200),
    Kb500(BYTES_500_KB, R.string.compress_500),
    Mb1(BYTES_1_MB, R.string.compress_1000),
}

/** Exam aur job forms aksar photo aur signature par 50 se 200 KB ki limit rakhte hain. */
enum class PhotoTarget(val bytes: Long, @param:StringRes val label: Int) {
    Kb50(BYTES_50_KB, R.string.compress_50),
    Kb100(BYTES_100_KB, R.string.compress_100),
    Kb200(BYTES_200_KB, R.string.compress_200),
}
