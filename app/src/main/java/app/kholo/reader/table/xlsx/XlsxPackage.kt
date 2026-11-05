package app.kholo.reader.table.xlsx

import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.readRelationships
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import org.xmlpull.v1.XmlPullParser

data class XlsxSheet(val name: String, val path: String)

class XlsxWorkbook(val sheets: List<XlsxSheet>, val date1904: Boolean, val hiddenSheets: Int)

object XlsxPackage {
    fun read(zip: SafeZip): XlsxWorkbook {
        val rels = readRelationships(zip.open("xl/_rels/workbook.xml.rels"), "xl")
        val sheets = mutableListOf<XlsxSheet>()
        var hidden = 0
        var date1904 = false
        val stream = zip.open("xl/workbook.xml") ?: throw java.io.IOException("no workbook")
        stream.use {
            val parser = safeXml(it)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "workbookPr" -> date1904 = parser.attr("date1904").let { v -> v == "1" || v == "true" }
                        "sheet" -> {
                            val path = parser.attr("id")?.let(rels::get)
                            val state = parser.attr("state")
                            when {
                                state == "hidden" || state == "veryHidden" -> hidden++
                                path != null -> sheets += XlsxSheet(parser.attr("name").orEmpty(), path)
                            }
                        }
                    }
                }
            }
        }
        return XlsxWorkbook(sheets, date1904, hidden)
    }
}
