package app.kholo.reader.office

import androidx.compose.ui.text.AnnotatedString
import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.ParsedDocument
import java.nio.charset.Charset

private const val HEADER_BYTES = 8
private const val TYPE_AT = 2
private const val LENGTH_AT = 4
private const val CONTAINER = 0x0F
private const val INSTANCE_SHIFT = 4
private const val DOCUMENT = 0x03E8
private const val SLIDE = 0x03EE
private const val SLIDE_PERSIST = 0x03F3
private const val SLIDE_LIST = 0x0FF0
private const val TEXT_HEADER = 0x0F9F
private const val TEXT_CHARS = 0x0FA0
private const val TEXT_BYTES = 0x0FA8
private const val PERSIST_DIRECTORY = 0x1772
private const val SLIDES_INSTANCE = 0
private const val PERSIST_ID_MASK = 0xFFFFF
private const val PERSIST_COUNT_SHIFT = 20
private const val TITLE_TYPE = 0
private const val CENTER_TITLE_TYPE = 6
private const val TITLE_HEADING = 2
private const val MAX_DEPTH = 32
private const val INT_BYTES = 4
private const val VERTICAL_TAB = '\u000B'

/** Slide-number placeholder ka nishan, asli text nahi. */
private const val FIELD_PLACEHOLDER = "*"

private class Record(val type: Int, val instance: Int, val container: Boolean, val body: Int, val end: Int)

private class SlideText(val type: Int, val text: String)

private class SlideEntry(val persistId: Int) {
    val texts = ArrayList<SlideText>()
}

/**
 * PowerPoint 97-2003 ki `.ppt` ka text, slide ke order me. Placeholder ka text document ki slide list
 * me hota hai aur text box ka slide ke andar, isliye dono jagah se lete hain. Tasveerein nahi.
 */
class PptParser(private val data: ByteArray) {
    private val windows = Charset.forName("windows-1252")

    fun parse(): ParsedDocument {
        val persist = HashMap<Int, Int>()
        var document: Record? = null
        val slideRecords = ArrayList<Record>()
        forEachRecord(0, data.size) { record ->
            when (record.type) {
                PERSIST_DIRECTORY -> readPersist(record, persist)
                // Baad me save hua hissa file ke aakhir me judta hai, isliye aakhri wala hi sahi hai.
                DOCUMENT -> document = record
                SLIDE -> slideRecords += record
            }
        }
        val entries = document?.let(::readSlideList).orEmpty()
        val blocks = ArrayList<DocBlock>()
        if (entries.isEmpty()) {
            slideRecords.forEachIndexed { index, record -> addSlide(index + 1, emptyList(), record, blocks) }
        } else {
            entries.forEachIndexed { index, entry ->
                val record = persist[entry.persistId]?.let(::recordAt)?.takeIf { it.type == SLIDE }
                addSlide(index + 1, entry.texts, record, blocks)
            }
        }
        return ParsedDocument(blocks)
    }

    private fun recordAt(at: Int): Record? {
        if (at < 0 || at + HEADER_BYTES > data.size) return null
        val first = data.u16(at)
        val length = data.u32(at + LENGTH_AT)
        val end = at + HEADER_BYTES + length
        if (end > data.size) return null
        return Record(
            type = data.u16(at + TYPE_AT),
            instance = first shr INSTANCE_SHIFT,
            container = first and CONTAINER == CONTAINER,
            body = at + HEADER_BYTES,
            end = end.toInt(),
        )
    }

    private inline fun forEachRecord(from: Int, to: Int, each: (Record) -> Unit) {
        var at = from
        while (at + HEADER_BYTES <= to) {
            val record = recordAt(at) ?: return
            if (record.end > to) return
            each(record)
            at = record.end
        }
    }

    private fun readPersist(record: Record, target: MutableMap<Int, Int>) {
        var at = record.body
        while (at + INT_BYTES <= record.end) {
            val entry = data.i32(at)
            val first = entry and PERSIST_ID_MASK
            val count = entry ushr PERSIST_COUNT_SHIFT
            at += INT_BYTES
            for (index in 0 until count) {
                if (at + INT_BYTES > record.end) return
                target[first + index] = data.i32(at)
                at += INT_BYTES
            }
        }
    }

    private fun readSlideList(document: Record): List<SlideEntry> {
        val entries = ArrayList<SlideEntry>()
        forEachRecord(document.body, document.end) { child ->
            if (child.type == SLIDE_LIST && child.instance == SLIDES_INSTANCE) {
                var type = -1
                forEachRecord(child.body, child.end) { item ->
                    when (item.type) {
                        SLIDE_PERSIST -> entries += SlideEntry(data.i32(item.body))
                        TEXT_HEADER -> type = data.i32(item.body)
                        TEXT_CHARS, TEXT_BYTES -> entries.lastOrNull()?.texts?.add(SlideText(type, textOf(item)))
                    }
                }
            }
        }
        return entries
    }

    private fun textOf(record: Record): String {
        val length = record.end - record.body
        return if (record.type == TEXT_CHARS) {
            String(data, record.body, length, Charsets.UTF_16LE)
        } else {
            String(data, record.body, length, windows)
        }
    }

    private fun collect(record: Record, target: MutableList<SlideText>, depth: Int) {
        if (depth > MAX_DEPTH) return
        var type = -1
        forEachRecord(record.body, record.end) { child ->
            when {
                child.type == TEXT_HEADER -> type = data.i32(child.body)
                child.type == TEXT_CHARS || child.type == TEXT_BYTES -> target += SlideText(type, textOf(child))
                child.container -> collect(child, target, depth + 1)
            }
        }
    }

    private fun addSlide(number: Int, listed: List<SlideText>, record: Record?, blocks: MutableList<DocBlock>) {
        blocks += DocBlock.SlideHeader(number)
        val texts = ArrayList(listed)
        if (record != null) collect(record, texts, 0)
        val seen = HashSet<String>()
        // Title pehle dikhe, chahe file me kisi bhi order me ho.
        val ordered = texts.sortedBy { if (isTitle(it)) 0 else 1 }
        for (item in ordered) {
            val text = item.text.trim()
            if (text.isEmpty() || text == FIELD_PLACEHOLDER || !seen.add(text)) continue
            text.split('\r').map { it.replace(VERTICAL_TAB, '\n').trim() }.filter { it.isNotEmpty() }.forEach { line ->
                blocks += DocBlock.Paragraph(
                    text = AnnotatedString(line),
                    heading = if (isTitle(item)) TITLE_HEADING else 0,
                )
            }
        }
    }

    private fun isTitle(item: SlideText): Boolean = item.type == TITLE_TYPE || item.type == CENTER_TITLE_TYPE
}
