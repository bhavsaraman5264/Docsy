package app.kholo.reader.tools

import android.content.ContentResolver
import android.graphics.Bitmap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal const val PDF_MIME = "application/pdf"
internal const val TEXT_MIME = "text/plain"
internal const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
internal const val JPEG_MIME = "image/jpeg"

/**
 * Ek tool ka asli kaam. ViewModel sirf screen ki state, files aur galti ka message sambhalta hai, isliye naya tool
 * jodne ke liye ViewModel nahi badalta: bas ek naya runner.
 *
 * [run] IO thread par chalta hai aur jaan-boojh kar `suspend` nahi hai: PDFBox aur Bitmap ka kaam rok-tok wala hai.
 * Cancel [ToolProgress] se aata hai, jo har kadam par jaanchta hai.
 */
internal interface ToolRunner {
    /** Nateeje ke naam me judta hai, jaise `bill-small.pdf`. Khaali ho to sirf asli naam. */
    val suffix: String get() = ""
    val mime: String get() = PDF_MIME
    val extension: String get() = ".pdf"

    fun run(job: ToolJob, progress: ToolProgress): ToolOutput
}

/**
 * Ek file wale nateeje ka naam ViewModel rakhta hai. Kai files (split, PDF se JPG) apna naam khud rakhti hain,
 * aur [name] poore set ka naam hai.
 */
internal class ToolOutput(val files: List<File>, val note: Int? = null, val name: String? = null) {
    constructor(file: File, note: Int? = null) : this(listOf(file), note)
}

/** Kaam shuru hote waqt ki state aur woh sab jo runner ko chahiye. Runner ko Android ki screen se kuch nahi lena. */
internal class ToolJob(
    val state: ToolUiState,
    /** Nateeje ke naam ka pehla hissa: asli file ka surakshit naam, ya camera wale tools me "scan". */
    val baseName: String,
    val pdf: PdfTools,
    private val resolver: ContentResolver,
    private val source: File?,
    private val files: ToolFiles,
    private val pageImage: (page: Int, dpi: Int) -> Bitmap?,
) {
    val inputs: List<ToolInput> get() = state.inputs

    /** Ek file wale tool ki file, jo chunte hi temp me copy ho gayi thi. */
    fun source(): File = source ?: throw IOException("no input")

    fun newFile(extension: String = ".pdf"): File = files.newFile(extension)

    fun copy(input: ToolInput, extension: String): File = files.copy(input, extension)

    /** Temp file ko user ko dikhne wala naam deta hai. Naam na badle to temp naam hi sahi, kaam nahi rukta. */
    fun named(file: File, name: String): File {
        val named = File(file.parentFile, name)
        if (!file.renameTo(named)) return file
        files.track(named)
        return named
    }

    fun open(input: ToolInput): InputStream = resolver.openInputStream(input.uri) ?: throw FileNotFoundException()

    fun opener(input: ToolInput): () -> InputStream? = { resolver.openInputStream(input.uri) }

    fun mimeOf(input: ToolInput): String? = resolver.getType(input.uri)

    fun pageImage(page: Int, dpi: Int): Bitmap? = pageImage.invoke(page, dpi)
}

/** Runner ki banayi har file yahin se aati hai, taaki Cancel ya screen band hone par sab mit jaye. */
internal interface ToolFiles {
    fun newFile(extension: String): File

    fun copy(input: ToolInput, extension: String): File

    /** Naam badalne se bani nayi file bhi mitni chahiye. */
    fun track(file: File)
}
