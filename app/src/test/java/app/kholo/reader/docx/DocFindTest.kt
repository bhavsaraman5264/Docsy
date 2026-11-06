package app.kholo.reader.docx

import androidx.compose.ui.text.AnnotatedString
import app.kholo.reader.viewer.findAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocFindTest {
    private fun paragraph(text: String) = DocBlock.Paragraph(AnnotatedString(text))

    @Test
    fun findAllIgnoresCaseAndDoesNotOverlap() {
        assertEquals(listOf(0, 5), "Asha asha".findAll("ASHA"))
        assertEquals(listOf(0, 2), "aaaa".findAll("aa"))
        assertTrue("abc".findAll("z").isEmpty())
    }

    @Test
    fun paragraphAndTableCellsAreSearched() {
        val cell = DocCell("rate 18", AnnotatedString("rate 18"), null)
        val table = DocBlock.Table(listOf(listOf(DocCell("x", AnnotatedString("x"), null), cell)))
        assertEquals(1, blockHits(0, paragraph("rate card"), "rate").size)
        val hit = blockHits(3, table, "18").single()
        assertEquals(3, hit.block)
        assertTrue(hit.owner === cell)
        assertEquals(5, hit.start)
        assertEquals(7, hit.end)
    }

    @Test
    fun imagesHaveNoHits() {
        assertTrue(blockHits(0, DocBlock.Image("a.png"), "a").isEmpty())
    }

    @Test
    fun marksStayOnTheirOwnParagraphEvenWhenTextIsTheSame() {
        val first = paragraph("same")
        val second = paragraph("same")
        val hits = blockHits(0, first, "same")
        val marks = DocMarks(hits, hits.single())
        assertTrue(marks.ranges(first).single().current)
        assertTrue(marks.ranges(second).isEmpty())
    }

    @Test
    fun pageItemsFindTheirOwner() {
        val block = paragraph("a")
        val cell = DocCell("b", AnnotatedString("b"), null)
        assertTrue(PageItem.Paragraph(block).owns(block))
        assertFalse(PageItem.Paragraph(paragraph("a")).owns(block))
        assertTrue(PageItem.TableRow(listOf(cell), emptyList()).owns(cell))
    }
}
