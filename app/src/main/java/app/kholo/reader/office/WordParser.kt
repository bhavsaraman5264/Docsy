package app.kholo.reader.office

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.DocCell
import app.kholo.reader.docx.ParsedDocument
import java.io.IOException
import java.nio.charset.Charset

private const val WORD_MAGIC = 0xA5EC
private const val NFIB_AT = 2
private const val FLAGS_AT = 0x0A
private const val KEY_SIZE_AT = 0x0E
private const val FLAG_ENCRYPTED = 0x0100
private const val FLAG_TABLE_1 = 0x0200
private const val FLAG_OBFUSCATED = 0x8000

/** Isse purani (Word 6/95) files ka FIB alag hota hai. */
private const val NFIB_WORD_97 = 101

private const val CSW_AT = 32
private const val CCP_TEXT_SLOT = 3
private const val PAPX_SLOT = 13
private const val CLX_SLOT = 33
private const val FC_LCB_BYTES = 8
private const val INT_BYTES = 4
private const val CLX_PROPERTIES = 1
private const val CLX_PIECES = 2
private const val PIECE_BYTES = 8
private const val PIECE_FC_AT = 2
private const val FC_COMPRESSED = 0x40000000L
private const val PAGE_BYTES = 512
private const val PAGE_NUMBER_MASK = 0x3FFFFFL
private const val RUN_COUNT_AT = 511
private const val BX_BYTES = 13
private const val SPRM_IN_TABLE = 0x2416
private const val SPRM_TTP = 0x2417
private const val SPRM_INNER_TTP = 0x244C
private const val SPRM_LIST_LEVEL = 0x260A
private const val SPRM_LIST = 0x460B
private const val SPRM_TABS = 0xC615
private const val SPRM_WIDE_A = 0xD606
private const val SPRM_WIDE_B = 0xD608
private const val SPRA_SHIFT = 13
private const val OPERAND_1 = 1
private const val OPERAND_2 = 2
private const val OPERAND_3 = 3
private const val OPERAND_4 = 4
private const val HEADING_MAX = 9
private const val SPACE_AFTER_PT = 6f
private const val FIELD_START = 0x13
private const val FIELD_SEPARATOR = 0x14
private const val FIELD_END = 0x15
private const val CELL_MARK = 0x07
private const val PAGE_MARK = 0x0C
private const val LINE_MARK = 0x0B
private const val PICTURE_MARK = 0x01
private const val DRAWING_MARK = 0x08
private const val HARD_HYPHEN = 0x1E
private const val FIRST_TEXT_CHAR = 0x20
private const val TAB = 0x09
private const val BULLET = "•"

/** `WordDocument` stream ka shuru: kholne se pehle file ke baare me yahin se pata chalta hai. */
class WordHeader(document: ByteArray) {
    private val flags: Int

    val encrypted: Boolean get() = flags and FLAG_ENCRYPTED != 0
    val obfuscated: Boolean get() = flags and FLAG_OBFUSCATED != 0
    val tableName: String get() = if (flags and FLAG_TABLE_1 != 0) "1Table" else "0Table"

    init {
        if (document.u16(0) != WORD_MAGIC) throw IOException("not word")
        if (document.u16(NFIB_AT) < NFIB_WORD_97) throw UnsupportedOffice()
        flags = document.u16(FLAGS_AT)
        // Header ka size padhna zaroori nahi, par kati hui file yahin pakdi jati hai.
        document.i32(KEY_SIZE_AT)
    }
}

private class ParagraphProps(
    val fcStart: Long,
    val fcEnd: Long,
    val heading: Int,
    val inTable: Boolean,
    val rowEnd: Boolean,
    val list: Boolean,
    val level: Int,
)

private class Piece(val cpStart: Int, val cpEnd: Int, val fc: Long, val compressed: Boolean)

/**
 * Word 97-2003 ki `.doc` se text, table aur heading nikalta hai. Formatting aur tasveerein nahi:
 * unke liye bahut bada parser chahiye, aur phone par padhne ke liye text hi kaam ki cheez hai.
 */
class WordParser(private val document: ByteArray, private val table: ByteArray) {
    private val rgFc: Int
    private val textLength: Int

    private val blocks = ArrayList<DocBlock>()
    private val line = StringBuilder()
    private val cell = StringBuilder()
    private var row = ArrayList<DocCell>()
    private var rows = ArrayList<List<DocCell>>()
    private val fields = ArrayDeque<Boolean>()
    private var sawPicture = false
    private var props: List<ParagraphProps> = emptyList()

    init {
        val csw = document.u16(CSW_AT)
        val cslwAt = CSW_AT + 2 + csw * 2
        val cslw = document.u16(cslwAt)
        val rgLw = cslwAt + 2
        textLength = document.i32(rgLw + CCP_TEXT_SLOT * INT_BYTES)
        rgFc = rgLw + cslw * INT_BYTES + 2
        if (textLength < 0) throw IOException("bad text length")
    }

    fun parse(): ParsedDocument {
        props = readProps().sortedBy { it.fcStart }
        val windows = Charset.forName("windows-1252")
        for (piece in readPieces()) {
            val count = minOf(piece.cpEnd, textLength) - piece.cpStart
            if (count <= 0) continue
            val start = (if (piece.compressed) piece.fc / 2 else piece.fc).toSafeInt()
            val width = if (piece.compressed) 1 else 2
            val text = if (piece.compressed) {
                String(document, start, count, windows)
            } else {
                String(document, start, count * 2, Charsets.UTF_16LE)
            }
            for (index in text.indices) feed(text[index], start.toLong() + index.toLong() * width)
        }
        endParagraph(null)
        flushTable()
        if (sawPicture) blocks += DocBlock.Skipped
        return ParsedDocument(blocks)
    }

    private fun slot(index: Int): Pair<Int, Int> {
        val at = rgFc + index * FC_LCB_BYTES
        return document.u32(at).toSafeInt() to document.u32(at + INT_BYTES).toSafeInt()
    }

    private fun readPieces(): List<Piece> {
        val (start, length) = slot(CLX_SLOT)
        val end = start + length
        var at = start
        while (at < end && table.u8(at) == CLX_PROPERTIES) at += 1 + 2 + table.u16(at + 1)
        if (at >= end || table.u8(at) != CLX_PIECES) throw IOException("no piece table")
        val size = table.u32(at + 1).toSafeInt()
        val plc = at + 1 + INT_BYTES
        val count = (size - INT_BYTES) / (INT_BYTES + PIECE_BYTES)
        if (count <= 0) throw IOException("empty piece table")
        val descriptors = plc + (count + 1) * INT_BYTES
        return List(count) { index ->
            val raw = table.u32(descriptors + index * PIECE_BYTES + PIECE_FC_AT)
            Piece(
                cpStart = table.i32(plc + index * INT_BYTES),
                cpEnd = table.i32(plc + (index + 1) * INT_BYTES),
                fc = raw and FC_COMPRESSED.inv(),
                compressed = raw and FC_COMPRESSED != 0L,
            )
        }
    }

    private fun readProps(): List<ParagraphProps> {
        val (start, length) = slot(PAPX_SLOT)
        if (length <= INT_BYTES) return emptyList()
        val count = (length - INT_BYTES) / (INT_BYTES * 2)
        val found = ArrayList<ParagraphProps>()
        for (index in 0 until count) {
            val number = table.u32(start + (count + 1 + index) * INT_BYTES) and PAGE_NUMBER_MASK
            readPage((number * PAGE_BYTES).toSafeInt(), found)
        }
        return found
    }

    private fun readPage(page: Int, target: MutableList<ParagraphProps>) {
        val runs = document.u8(page + RUN_COUNT_AT)
        for (run in 0 until runs) {
            val from = document.u32(page + run * INT_BYTES)
            val to = document.u32(page + (run + 1) * INT_BYTES)
            val bx = page + (runs + 1) * INT_BYTES + run * BX_BYTES
            val offset = document.u8(bx) * 2
            target += if (offset == 0) {
                ParagraphProps(from, to, 0, inTable = false, rowEnd = false, list = false, level = 0)
            } else {
                readPapx(page + offset, from, to)
            }
        }
    }

    private fun readPapx(at: Int, from: Long, to: Long): ParagraphProps {
        val first = document.u8(at)
        val start = if (first == 0) at + 2 else at + 1
        val length = if (first == 0) document.u8(at + 1) * 2 else first * 2 - 1
        val style = document.u16(start)
        var inTable = false
        var rowEnd = false
        var list = false
        var level = 0
        var cursor = start + 2
        val end = start + length
        while (cursor + 2 < end) {
            val sprm = document.u16(cursor)
            val operand = cursor + 2
            when (sprm) {
                SPRM_IN_TABLE -> inTable = document.u8(operand) != 0
                SPRM_TTP, SPRM_INNER_TTP -> rowEnd = document.u8(operand) != 0
                SPRM_LIST -> list = document.u16(operand) != 0
                SPRM_LIST_LEVEL -> level = document.u8(operand)
            }
            val size = operandSize(sprm, operand)
            if (size < 0) break
            cursor = operand + size
        }
        val heading = if (style in 1..HEADING_MAX) style else 0
        return ParagraphProps(from, to, heading, inTable, rowEnd, list, level)
    }

    /** Anjaan lambai par -1: us paragraph ki baaki properties chhod do, text par asar nahi. */
    private fun operandSize(sprm: Int, at: Int): Int = when (sprm) {
        SPRM_TABS -> -1
        SPRM_WIDE_A, SPRM_WIDE_B -> document.u16(at) + 2
        else -> when (sprm shr SPRA_SHIFT) {
            0, 1 -> OPERAND_1
            2, 4, 5 -> OPERAND_2
            3 -> OPERAND_4
            6 -> document.u8(at) + 1
            else -> OPERAND_3
        }
    }

    private fun propsAt(fc: Long): ParagraphProps? {
        var low = 0
        var high = props.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val candidate = props[middle]
            when {
                fc < candidate.fcStart -> high = middle - 1
                fc >= candidate.fcEnd -> low = middle + 1
                else -> return candidate
            }
        }
        return null
    }

    private fun feed(char: Char, fc: Long) {
        val code = char.code
        when {
            code == FIELD_START -> fields.addLast(true)
            code == FIELD_SEPARATOR -> if (fields.isNotEmpty()) {
                fields.removeLast()
                fields.addLast(false)
            }
            code == FIELD_END -> fields.removeLastOrNull()
            fields.any { it } -> Unit
            char == '\r' -> endParagraph(propsAt(fc))
            code == CELL_MARK -> endCell(propsAt(fc))
            code == PAGE_MARK -> {
                endParagraph(propsAt(fc))
                flushTable()
                blocks += DocBlock.PageBreak
            }
            code == LINE_MARK -> line.append('\n')
            code == PICTURE_MARK || code == DRAWING_MARK -> sawPicture = true
            code == HARD_HYPHEN -> line.append('-')
            code == TAB || code >= FIRST_TEXT_CHAR -> line.append(char)
        }
    }

    private fun endParagraph(props: ParagraphProps?) {
        if (props?.inTable == true) {
            cell.append(line).append('\n')
            line.clear()
            return
        }
        flushTable()
        val text = line.toString().trim()
        line.clear()
        if (text.isEmpty()) return
        val heading = props?.heading ?: 0
        blocks += DocBlock.Paragraph(
            text = AnnotatedString(text),
            heading = heading,
            marker = if (props?.list == true) BULLET else null,
            indent = props?.level ?: 0,
            rich = if (heading > 0) headingText(text, heading) else AnnotatedString(text),
            spaceAfter = SPACE_AFTER_PT,
        )
    }

    private fun headingText(text: String, heading: Int): AnnotatedString = buildAnnotatedString {
        val size = when (heading) {
            1 -> 18
            2 -> 15
            3 -> 13
            else -> 12
        }
        withStyle(SpanStyle(fontSize = size.sp, fontWeight = FontWeight.Bold)) { append(text) }
    }

    private fun endCell(props: ParagraphProps?) {
        if (props?.rowEnd == true) {
            line.clear()
            cell.clear()
            rows.add(row)
            row = ArrayList()
            return
        }
        cell.append(line)
        line.clear()
        val text = cell.toString().trim()
        cell.clear()
        row.add(DocCell(text, AnnotatedString(text), null))
    }

    private fun flushTable() {
        if (row.isNotEmpty()) {
            rows.add(row)
            row = ArrayList()
        }
        if (rows.isNotEmpty()) {
            blocks += DocBlock.Table(rows)
            rows = ArrayList()
        }
    }
}
