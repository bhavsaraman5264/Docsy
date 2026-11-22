package app.kholo.reader.tools

import android.graphics.Bitmap
import app.kholo.reader.R
import java.io.File
import java.io.IOException

private const val IMAGE_DPI = 200
private const val JPEG_QUALITY = 88

/** Render aur JPEG saath chalte hain. Har page ki tasveer ~15 MB hai, isliye do se zyada nahi. */
private const val IMAGE_WORKERS = 2

internal object MergeRunner : ToolRunner {
    override val suffix = "merged"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val inputs = job.inputs
        val total = inputs.size * 2
        val files = inputs.mapIndexed { index, input ->
            progress(index, total)
            job.copy(input, ".pdf")
        }
        val out = job.newFile()
        job.pdf.merge(files, out) { done, _ -> progress(inputs.size + done, total) }
        return ToolOutput(out)
    }
}

/** [after] page ke baad do hisse. Har hissa apni file hai, naam `bill-part-1.pdf`. */
internal class SplitRunner(private val after: Int) : ToolRunner {
    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val parts = splitParts(job.state.pageCount, after) ?: throw IOException("bad split")
        val made = parts.map { job.newFile() }
        progress(0, parts.size)
        job.pdf.split(job.source(), parts.map(IntRange::toList), made, progress)
        val files = made.mapIndexed { index, file -> job.named(file, "${job.baseName}-part-${index + 1}.pdf") }
        return ToolOutput(files, name = "${job.baseName}-split")
    }
}

/** [from] aur [to] 1 se shuru hote hain, jaise user ne likhe. */
internal class ExtractRunner(private val from: Int, private val to: Int) : ToolRunner {
    override val suffix = "pages-$from-$to"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        job.pdf.rearrange(job.source(), (from - 1 until to).toList(), out)
        return ToolOutput(out)
    }
}

internal object OrganizeRunner : ToolRunner {
    override val suffix = "organized"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        job.pdf.rearrange(job.source(), job.state.pageOrder, out, job.state.pageTurns)
        return ToolOutput(out)
    }
}

internal class CompressRunner(private val target: CompressTarget) : ToolRunner {
    override val suffix = "small"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        progress(0, 1)
        val result = job.pdf.compress(job.source(), target.bytes, out, progress)
        val note = when {
            result.alreadySmall -> R.string.compress_already_small
            !result.hadImages -> R.string.compress_no_images
            target != CompressTarget.Smallest && !result.reachedTarget -> R.string.compress_missed
            else -> null
        }
        return ToolOutput(out, note)
    }
}

internal class LockRunner(private val password: String) : ToolRunner {
    override val suffix = "locked"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        job.pdf.lock(job.source(), password, out)
        return ToolOutput(out)
    }
}

internal class UnlockRunner(private val password: String) : ToolRunner {
    override val suffix = "unlocked"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        val wasLocked = job.pdf.unlock(job.source(), password, out)
        return ToolOutput(out, if (wasLocked) null else R.string.unlock_not_locked)
    }
}

internal object TextRunner : ToolRunner {
    override val suffix = "text"
    override val mime = TEXT_MIME
    override val extension = ".txt"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile(extension)
        progress(0, 1)
        val found = job.pdf.extractText(job.source(), out, progress)
        return ToolOutput(out, if (found) null else R.string.text_none_found)
    }
}

/** Har page ki alag JPG, naam `bill-page-3.jpg`. */
internal object PdfToImagesRunner : ToolRunner {
    override val mime = JPEG_MIME
    override val extension = ".jpg"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val pages = job.state.pageCount
        val files = mutableListOf<File>()
        progress(0, pages)
        pipeline((0 until pages).toList(), IMAGE_WORKERS, { writePage(job, it) }) { index, file ->
            files += file
            progress(index + 1, pages)
        }
        return ToolOutput(files, name = files.singleOrNull()?.name ?: "${job.baseName}-images")
    }

    private fun writePage(job: ToolJob, page: Int): File {
        val bitmap = job.pageImage(page, IMAGE_DPI) ?: throw IOException("unreadable page")
        val file = job.newFile(extension)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        return job.named(file, "${job.baseName}-page-${page + 1}.jpg")
    }
}

/** Har page par tirchha likha text, jaise "Sirf KYC ke liye". */
internal class WatermarkRunner(private val text: String) : ToolRunner {
    override val suffix = "watermark"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        job.pdf.edit(job.source(), out) { it.addWatermark(text.trim(), progress) }
        return ToolOutput(out)
    }
}

internal class PageNumbersRunner(private val style: NumberStyle, private val place: NumberPlace) : ToolRunner {
    override val suffix = "numbered"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        job.pdf.edit(job.source(), out) { it.addPageNumbers(style, place, progress) }
        return ToolOutput(out)
    }
}
