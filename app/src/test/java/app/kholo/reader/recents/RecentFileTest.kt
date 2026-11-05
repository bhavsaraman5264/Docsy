package app.kholo.reader.recents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFileTest {
    private fun file(uri: String, at: Long, favorite: Boolean = false, name: String = "doc-$uri", size: Long = 1) =
        RecentFile(uri, name, size, "PDF", at, favorite)

    @Test
    fun `newest file comes first and is not duplicated`() {
        val list = listOf(file("a", 1), file("b", 2)).upsert(file("a", 3))
        assertEquals(listOf("a", "b"), list.map { it.uri })
    }

    @Test
    fun `whatsapp giving the same file a new uri does not duplicate it`() {
        val list = listOf(
            file("old", 1, name = "bill.pdf", size = 500),
        ).upsert(file("new", 2, name = "bill.pdf", size = 500))
        assertEquals(1, list.size)
        assertEquals("new", list.single().uri)
    }

    @Test
    fun `same name with a different size stays a different file`() {
        val list = listOf(
            file("a", 1, name = "bill.pdf", size = 500),
        ).upsert(file("b", 2, name = "bill.pdf", size = 900))
        assertEquals(2, list.size)
    }

    @Test
    fun `unknown size falls back to the uri`() {
        val list = listOf(file("a", 1, name = "x.pdf", size = 0)).upsert(file("b", 2, name = "x.pdf", size = 0))
        assertEquals(2, list.size)
    }

    @Test
    fun `opening a favorite through a new uri keeps it favorite`() {
        val list = listOf(
            file("old", 1, favorite = true, name = "n", size = 5),
        ).upsert(file("new", 2, name = "n", size = 5))
        assertTrue(list.single().favorite)
    }

    @Test
    fun `a lasting uri is not replaced by a temporary whatsapp one`() {
        val kept = file("picker", 1, name = "n", size = 5).copy(keepsAccess = true)
        val list = listOf(kept).upsert(file("whatsapp", 2, name = "n", size = 5))
        assertEquals("picker", list.single().uri)
        assertEquals(2L, list.single().openedAt)
    }

    @Test
    fun `old saved duplicates merge into one`() {
        val saved = listOf(
            file("a", 1, favorite = true, name = "n", size = 5),
            file("b", 3, name = "n", size = 5),
            file("c", 2, name = "n", size = 5),
        )
        val unique = saved.deduped()
        assertEquals(1, unique.size)
        assertEquals("b", unique.single().uri)
        assertTrue(unique.single().favorite)
    }

    @Test
    fun `favorite toggles on and off`() {
        val on = listOf(file("a", 1)).toggleFavorite("a", now = 10)
        assertTrue(on.single().favorite)
        assertEquals(10L, on.single().favoritedAt)
        assertFalse(on.toggleFavorite("a", now = 20).single().favorite)
    }

    @Test
    fun `last starred file is first in favorites`() {
        var list = listOf(file("a", 3), file("b", 2), file("c", 1))
        list = list.toggleFavorite("a", now = 10).toggleFavorite("c", now = 20).toggleFavorite("b", now = 30)
        assertEquals(listOf("b", "c", "a"), list.favoritesNewestFirst().map { it.uri })
    }

    @Test
    fun `old non favorites are dropped but favorites stay`() {
        var list = listOf(file("fav", 0, favorite = true))
        for (i in 1..150) list = list.upsert(file("f$i", i.toLong()))
        assertTrue(list.any { it.uri == "fav" })
        assertTrue(list.size <= 102)
    }
}
