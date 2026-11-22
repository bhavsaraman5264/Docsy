package app.kholo.reader.tools

import app.kholo.reader.R
import app.kholo.reader.core.file.COPY_BUFFER
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ID card copy me aage aur peeche, bas do. */
internal const val ID_CARD_SIDES = 2

/** Form photo par date isi tarah likhi jati hai, phone ki bhasha kuch bhi ho. */
private const val CAPTION_DATE = "dd-MM-yyyy"

/** Photos to PDF aur Scan: har photo ek page. */
internal class PhotosRunner(override val suffix: String) : ToolRunner {
    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        progress(0, 1)
        job.pdf.photosToPdf(job.inputs.map(job::opener), out, progress)
        return ToolOutput(out)
    }
}

internal object IdCardRunner : ToolRunner {
    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        progress(0, 1)
        job.pdf.idCardSheet(job.inputs.take(ID_CARD_SIDES).map(job::opener), out, progress)
        return ToolOutput(out)
    }
}

/** [name] ho to photo ke neeche naam aur aaj ki date. */
internal class ExamPhotoRunner(private val spec: PhotoSpec, private val name: String?) : ToolRunner {
    override val suffix = "small"
    override val mime = JPEG_MIME
    override val extension = ".jpg"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val input = job.inputs.firstOrNull() ?: throw IOException("no input")
        val out = job.newFile(extension)
        val caption = name?.trim()?.takeIf { it.isNotEmpty() }?.let {
            "$it  ${SimpleDateFormat(CAPTION_DATE, Locale.US).format(Date())}"
        }
        // Pehle se seema me aane wali JPEG dobara banane se kharab hi hoti hai.
        val untouched = !spec.hasSize && caption == null && job.mimeOf(input) == JPEG_MIME
        if (untouched && input.size in maxOf(1L, spec.minBytes)..spec.maxBytes) {
            job.open(input).use { stream -> out.outputStream().use { stream.copyTo(it, COPY_BUFFER) } }
            return ToolOutput(out, R.string.compress_already_small)
        }
        val result = examPhoto(job.opener(input), spec, caption, out)
        val note = when {
            !result.fits -> R.string.compress_missed
            result.padded -> R.string.photo_padded
            else -> null
        }
        return ToolOutput(out, note)
    }
}
