package app.kholo.reader.convert

import kotlin.math.abs
import kotlin.math.roundToInt

private const val SAME_LINE_SHARE = 0.4f
private const val WORD_GAP_SHARE = 0.15f
private const val PARAGRAPH_GAP_SHARE = 1.7f
private const val SIZE_TOLERANCE_PT = 1f
private const val LINE_END_SHARE = 4f
private const val BUCKET_PT = 2f
private const val GUTTER_FROM = 0.35f
private const val GUTTER_TO = 0.65f
private const val MIN_GUTTER_PT = 12f
private const val MIN_COLUMN_LINES = 6
private const val GUTTER_NOISE_SHARE = 0.05f
private const val HEADING_ONE_RATIO = 1.6f
private const val HEADING_TWO_RATIO = 1.3f
private const val HEADING_THREE_RATIO = 1.15f
private const val MAX_HEADING_CHARS = 120
private const val CENTER_TOLERANCE_SHARE = 0.04f
private const val CENTER_MIN_GAP_SHARE = 0.12f
private const val FULL_WIDTH = 0
private const val LEFT_COLUMN = 1
private const val RIGHT_COLUMN = 2
private const val LIST_MARKS = "•·▪◦‣-–—*"

/** PDF ka ek shabd. [y] page ke upar se baseline tak, points me. */
data class TextRun(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val size: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
) {
    val right: Float get() = x + width
}

data class WordRun(val text: String, val bold: Boolean, val italic: Boolean, val size: Float)

data class PageParagraph(
    val runs: List<WordRun>,
    val heading: Int,
    val centered: Boolean,
    val top: Float,
) {
    val text: String get() = runs.joinToString("") { it.text }
}

private class TextLine(val runs: List<TextRun>, val column: Int = FULL_WIDTH) {
    val y = runs.first().y
    val x = runs.first().x
    val right = runs.maxOf { it.right }
    val size = runs.maxOf { it.size }
}

/**
 * PDF me paragraph nahi hote, sirf yeh likha hota hai ki kaunsa shabd kahan hai. Yeh code jagah aur size
 * dekh kar lines, columns aur paragraphs wapas banata hai. Yeh andaza hai, isliye Android ke bina test hota hai.
 */
object PageLayout {
    fun paragraphs(runs: List<TextRun>, pageWidth: Float): List<PageParagraph> {
        val clean = runs.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return emptyList()
        val lines = linesOf(clean)
        val ordered = readingOrder(lines, gutterOf(lines, pageWidth))
        val body = bodySize(clean)
        val textLeft = clean.minOf { it.x }
        val textRight = clean.maxOf { it.right }
        val edges = ordered.groupBy { it.column }.mapValues { (_, group) -> group.maxOf { it.right } }
        val result = mutableListOf<PageParagraph>()
        var current = mutableListOf<TextLine>()
        ordered.forEach { line ->
            val last = current.lastOrNull()
            if (last != null && !continues(last, line, edges.getValue(last.column))) {
                result += build(current, body, textLeft, textRight)
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) result += build(current, body, textLeft, textRight)
        return result
    }

    private fun linesOf(runs: List<TextRun>): List<TextLine> {
        val groups = mutableListOf<MutableList<TextRun>>()
        runs.sortedBy { it.y }.forEach { run ->
            val group = groups.lastOrNull()
            val anchor = group?.first()
            val sameLine = anchor != null && abs(run.y - anchor.y) <= maxOf(run.size, anchor.size) * SAME_LINE_SHARE
            if (group != null && sameLine) {
                group += run
            } else {
                groups += mutableListOf(run)
            }
        }
        return groups.map { group -> TextLine(group.sortedBy { it.x }) }
    }

    /** Beech ki khaali patti, jismen lagbhag koi line nahi jati. Isi se do column pehchante hain. */
    private fun gutterOf(lines: List<TextLine>, pageWidth: Float): Float? {
        val buckets = (pageWidth / BUCKET_PT).toInt() + 1
        val covered = IntArray(buckets)
        lines.forEach { line ->
            val touched = BooleanArray(buckets)
            line.runs.forEach { run ->
                val from = (run.x / BUCKET_PT).toInt().coerceIn(0, buckets - 1)
                val to = (run.right / BUCKET_PT).toInt().coerceIn(from, buckets - 1)
                for (bucket in from..to) touched[bucket] = true
            }
            touched.forEachIndexed { bucket, hit -> if (hit) covered[bucket]++ }
        }
        val noise = (lines.size * GUTTER_NOISE_SHARE).toInt()
        val first = (buckets * GUTTER_FROM).toInt()
        val last = (buckets * GUTTER_TO).toInt().coerceAtMost(buckets - 1)
        var bestStart = -1
        var bestLength = 0
        var start = -1
        for (bucket in first..last) {
            if (covered[bucket] <= noise) {
                if (start < 0) start = bucket
                if (bucket - start + 1 > bestLength) {
                    bestStart = start
                    bestLength = bucket - start + 1
                }
            } else {
                start = -1
            }
        }
        if (bestLength * BUCKET_PT < MIN_GUTTER_PT) return null
        val gutter = (bestStart + bestLength / 2f) * BUCKET_PT
        val both = lines.count { line -> line.right > gutter } >= MIN_COLUMN_LINES &&
            lines.count { line -> line.x < gutter } >= MIN_COLUMN_LINES
        return gutter.takeIf { both }
    }

    /** Pehle baayan column poora, phir daayan. Poori chaudai wali line (jaise title) dono ke beech deewar hai. */
    private fun readingOrder(lines: List<TextLine>, gutter: Float?): List<TextLine> {
        if (gutter == null) return lines
        val result = mutableListOf<TextLine>()
        val leftSide = mutableListOf<TextLine>()
        val rightSide = mutableListOf<TextLine>()
        val flush = {
            result += leftSide
            result += rightSide
            leftSide.clear()
            rightSide.clear()
        }
        lines.forEach { line ->
            val crosses = line.runs.any { it.x < gutter && it.right > gutter }
            if (crosses) {
                flush()
                result += line
            } else {
                val (leftRuns, rightRuns) = line.runs.partition { it.right <= gutter }
                if (leftRuns.isNotEmpty()) leftSide += TextLine(leftRuns, LEFT_COLUMN)
                if (rightRuns.isNotEmpty()) rightSide += TextLine(rightRuns, RIGHT_COLUMN)
            }
        }
        flush()
        return result
    }

    private fun continues(previous: TextLine, line: TextLine, edge: Float): Boolean {
        val gap = line.y - previous.y
        val closeBelow = gap > 0f && gap <= previous.size * PARAGRAPH_GAP_SHARE
        val sameSize = abs(line.size - previous.size) <= SIZE_TOLERANCE_PT
        val reachedEnd = previous.right >= edge - previous.size * LINE_END_SHARE
        val notIndented = line.x <= previous.x + previous.size
        return previous.column == line.column && closeBelow && sameSize && reachedEnd && notIndented &&
            !startsList(line)
    }

    private fun startsList(line: TextLine): Boolean {
        val first = line.runs.first().text.trim()
        return first.length <= 2 && first.first() in LIST_MARKS
    }

    private fun bodySize(runs: List<TextRun>): Float =
        runs.groupBy { it.size.roundToInt() }
            .maxByOrNull { (_, group) -> group.sumOf { it.text.length } }
            ?.key?.toFloat() ?: 0f

    private fun build(lines: List<TextLine>, body: Float, textLeft: Float, textRight: Float): PageParagraph {
        val runs = mutableListOf<WordRun>()
        lines.forEachIndexed { index, line ->
            if (index > 0) joinLines(runs, line)
            var previous: TextRun? = null
            line.runs.forEach { run ->
                val before = previous
                val spaced = before != null && run.x - before.right > before.size * WORD_GAP_SHARE
                append(runs, if (spaced) " ${run.text}" else run.text, run)
                previous = run
            }
        }
        val paragraph = PageParagraph(runs, 0, false, lines.first().y - lines.first().size)
        val allBold = runs.all { it.bold }
        return paragraph.copy(
            heading = headingLevel(lines.maxOf { it.size }, body, allBold, paragraph.text.length),
            centered = lines.size == 1 && isCentered(lines.first(), textLeft, textRight),
        )
    }

    /** Line ke aakhir ka `-` shabd ka toot-na hai. Baaki jagah lines ke beech space aata hai. */
    private fun joinLines(runs: MutableList<WordRun>, next: TextLine) {
        val last = runs.lastOrNull() ?: return
        val continuesWord = last.text.endsWith("-") && next.runs.first().text.firstOrNull()?.isLowerCase() == true
        if (continuesWord) {
            runs[runs.lastIndex] = last.copy(text = last.text.dropLast(1))
        } else {
            runs[runs.lastIndex] = last.copy(text = last.text + " ")
        }
    }

    private fun append(runs: MutableList<WordRun>, text: String, source: TextRun) {
        val size = (source.size * 2).roundToInt() / 2f
        val last = runs.lastOrNull()
        if (last != null && last.bold == source.bold && last.italic == source.italic && last.size == size) {
            runs[runs.lastIndex] = last.copy(text = last.text + text)
        } else {
            runs += WordRun(text, source.bold, source.italic, size)
        }
    }

    private fun headingLevel(size: Float, body: Float, bold: Boolean, length: Int): Int {
        if (body <= 0f || length > MAX_HEADING_CHARS) return 0
        val ratio = size / body
        return when {
            ratio >= HEADING_ONE_RATIO -> 1
            ratio >= HEADING_TWO_RATIO -> 2
            ratio >= HEADING_THREE_RATIO && bold -> 3
            else -> 0
        }
    }

    private fun isCentered(line: TextLine, textLeft: Float, textRight: Float): Boolean {
        val width = textRight - textLeft
        val leftGap = line.x - textLeft
        val rightGap = textRight - line.right
        return abs(leftGap - rightGap) < width * CENTER_TOLERANCE_SHARE && leftGap > width * CENTER_MIN_GAP_SHARE
    }
}

private const val MIN_LETTERS = 8
private const val MAX_BAD_SHARE = 0.05f
private const val MAX_PAGE_IMAGES = 40
private const val C1_START = 0x80
private const val C1_END = 0x9F
private const val PRIVATE_START = 0xE000
private const val PRIVATE_END = 0xF8FF
private const val REPLACEMENT = 0xFFFD

/** Kruti Dev jaise purane Hindi fonts me text andar se angrezi akshar hota hai, jo Word me kachra dikhega. */
private val LEGACY_FONTS = listOf("kruti", "devlys", "chanakya", "shusha", "agra", "walkman", "aps-dv")

/** Tay karta hai ki page ka text bharosemand hai, ya page ko tasveer ki tarah rakhna behtar hai. */
object PageQuality {
    fun isLegacyFont(name: String?): Boolean {
        val lower = name.orEmpty().lowercase()
        return LEGACY_FONTS.any { it in lower }
    }

    fun readable(text: String, legacyFont: Boolean, imageCount: Int): Boolean {
        val letters = text.count(Char::isLetterOrDigit)
        val bad = text.count(::isBad)
        return !legacyFont &&
            imageCount <= MAX_PAGE_IMAGES &&
            letters >= MIN_LETTERS &&
            bad <= (letters + bad) * MAX_BAD_SHARE
    }

    private fun isBad(char: Char): Boolean {
        val code = char.code
        return code == REPLACEMENT ||
            code in C1_START..C1_END ||
            code in PRIVATE_START..PRIVATE_END ||
            (char.isISOControl() && !char.isWhitespace())
    }
}
