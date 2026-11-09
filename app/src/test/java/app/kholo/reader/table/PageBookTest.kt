package app.kholo.reader.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBookTest {
    private val book = PageBook(capacity = 3)

    private fun rows(count: Int) = List(count) { TableRow(it + 1, listOf("r$it")) }

    @Test
    fun aPageLoadsOnlyOnce() {
        assertTrue(book.startLoad(0))
        assertFalse(book.startLoad(0))
        book.loaded(0, rows(5), complete = true, from = book.generation)
        assertFalse(book.startLoad(0))
        assertEquals(5, book[0]?.size)
    }

    @Test
    fun aPartPageIsShownAndOnlyItIsForgotten() {
        book.startLoad(0)
        book.loaded(0, rows(128), complete = true, from = book.generation)
        book.startLoad(1)
        book.loaded(1, rows(40), complete = false, from = book.generation)
        assertEquals(40, book[1]?.size)
        assertEquals(listOf(1), book.forgetPartial())
        assertNull(book[1])
        assertEquals(128, book[0]?.size)
        assertTrue(book.forgetPartial().isEmpty())
    }

    @Test
    fun aLatePageFromTheOldSheetIsDropped() {
        val old = book.generation
        book.startLoad(0)
        book.clear()
        assertFalse(book.loaded(0, rows(5), complete = true, from = old))
        assertNull(book[0])
        assertTrue(book.startLoad(0))
    }

    @Test
    fun oldPagesAreLetGo() {
        for (page in 0..3) {
            book.startLoad(page)
            book.loaded(page, rows(1), complete = true, from = book.generation)
        }
        assertNull(book[0])
        assertEquals(1, book[3]?.size)
    }
}
