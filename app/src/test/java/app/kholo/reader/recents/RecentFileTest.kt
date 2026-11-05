package app.kholo.reader.recents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFileTest {
    private fun file(uri: String, at: Long, favorite: Boolean = false) = RecentFile(uri, "doc", 1, "PDF", at, favorite)

    @Test
    fun `newest file comes first and is not duplicated`() {
        val list = listOf(file("a", 1), file("b", 2)).upsert(file("a", 3))
        assertEquals(listOf("a", "b"), list.map { it.uri })
    }

    @Test
    fun `opening a favorite again keeps it favorite`() {
        val list = listOf(file("a", 1, favorite = true)).upsert(file("a", 2))
        assertTrue(list.single().favorite)
    }

    @Test
    fun `favorite toggles on and off`() {
        val on = listOf(file("a", 1)).toggleFavorite("a")
        assertTrue(on.single().favorite)
        assertFalse(on.toggleFavorite("a").single().favorite)
    }

    @Test
    fun `old non favorites are dropped but favorites stay`() {
        var list = listOf(file("fav", 0, favorite = true))
        for (i in 1..150) list = list.upsert(file("f$i", i.toLong()))
        assertTrue(list.any { it.uri == "fav" })
        assertTrue(list.size <= 102)
    }
}
