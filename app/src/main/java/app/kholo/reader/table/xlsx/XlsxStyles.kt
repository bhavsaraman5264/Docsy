package app.kholo.reader.table.xlsx

import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import org.xmlpull.v1.XmlPullParser

private val BUILT_IN_DATES = (14..22).toSet() + (45..47).toSet()

/** Cell style se pata chalta hai ki number asal me date hai ya nahi. */
class XlsxStyles(private val dateStyles: Set<Int>) {
    fun isDate(styleIndex: Int): Boolean = styleIndex in dateStyles

    companion object {
        fun read(zip: SafeZip): XlsxStyles {
            val stream = zip.open("xl/styles.xml") ?: return XlsxStyles(emptySet())
            val customDates = mutableSetOf<Int>()
            val dates = mutableSetOf<Int>()
            var inCellXfs = false
            var index = 0
            stream.use {
                val parser = safeXml(it)
                parser.walk { event ->
                    when {
                        event == XmlPullParser.START_TAG && parser.name == "numFmt" -> {
                            val id = parser.attr("numFmtId")?.toIntOrNull()
                            if (id != null && looksLikeDate(parser.attr("formatCode").orEmpty())) customDates += id
                        }
                        event == XmlPullParser.START_TAG && parser.name == "cellXfs" -> inCellXfs = true
                        event == XmlPullParser.END_TAG && parser.name == "cellXfs" -> inCellXfs = false
                        event == XmlPullParser.START_TAG && inCellXfs && parser.name == "xf" -> {
                            val format = parser.attr("numFmtId")?.toIntOrNull() ?: 0
                            if (format in BUILT_IN_DATES || format in customDates) dates += index
                            index++
                        }
                    }
                }
            }
            return XlsxStyles(dates)
        }

        /** Quote aur bracket ke andar ka text format code nahi hota. */
        fun looksLikeDate(code: String): Boolean {
            val plain = code.replace(Regex("\"[^\"]*\"|\\[[^]]*]|\\\\."), "").lowercase()
            return plain.any { it == 'y' || it == 'd' || it == 'h' } || plain.contains("mm")
        }
    }
}
