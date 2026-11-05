package app.kholo.reader.core.file

import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

/** Namespace-aware parser. DOCTYPE wali XML reject, taaki entity attack na ho sake. */
fun safeXml(input: InputStream): XmlPullParser {
    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
    val parser = factory.newPullParser()
    parser.setInput(input, null)
    while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.nextToken() == XmlPullParser.DOCDECL) throw XmlPullParserException("DOCTYPE not allowed")
    }
    return parser
}

/** Attribute ka local naam se match, taaki `w:val` aur strict OOXML dono chalen. */
fun XmlPullParser.attr(name: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeName(i).substringAfter(':') == name) return getAttributeValue(i)
    }
    return null
}

/** Current START_TAG se aakhir tak har event par [block]. */
inline fun XmlPullParser.walk(block: (Int) -> Unit) {
    var event = eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        block(event)
        event = next()
    }
}

/** `word/document.xml` ke rels me `../media/a.png` jaise target ko zip ke path me badalta hai. */
fun resolveZipPath(baseDir: String, target: String): String {
    if (target.startsWith("/")) return target.removePrefix("/")
    val parts = ArrayDeque(baseDir.split('/').filter { it.isNotEmpty() })
    target.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

/** Relationship file se `rId` se target ka map. */
fun readRelationships(input: InputStream?, baseDir: String): Map<String, String> {
    if (input == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    input.use { stream ->
        val parser = safeXml(stream)
        parser.walk { event ->
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.attr("Id")
                val target = parser.attr("Target")
                val external = parser.attr("TargetMode") == "External"
                if (id != null && target != null && !external) result[id] = resolveZipPath(baseDir, target)
            }
        }
    }
    return result
}
