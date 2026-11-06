package app.kholo.reader.viewer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SETTLE_MS = 1000L

@OptIn(ExperimentalCoroutinesApi::class)
class FindControllerTest {
    private fun TestScope.controller(scan: suspend (String, (List<Int>) -> Unit) -> Unit): FindController<Int> =
        FindController(backgroundScope, StandardTestDispatcher(testScheduler), scan).apply { open() }

    /** backgroundScope ka kaam advanceUntilIdle nahi chalata, isliye samay khud aage badhao. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(SETTLE_MS)
        testScheduler.runCurrent()
    }

    @Test
    fun hitsArriveAndFirstBecomesCurrent() = runTest {
        val find = controller { _, emit ->
            emit(listOf(1, 2))
            emit(listOf(3))
        }
        find.query("a")
        settle()
        assertEquals(listOf(1, 2, 3), find.state.value.hits)
        assertEquals(1, find.state.value.currentHit)
        assertFalse(find.state.value.searching)
    }

    @Test
    fun stepWrapsBothWays() = runTest {
        val find = controller { _, emit -> emit(listOf(1, 2, 3)) }
        find.query("a")
        settle()
        find.step(-1)
        assertEquals(3, find.state.value.currentHit)
        find.step(1)
        assertEquals(1, find.state.value.currentHit)
    }

    @Test
    fun typingFastScansOnlyTheLastText() = runTest {
        val seen = ArrayList<String>()
        val find = controller { query, _ -> seen += query }
        find.query("a")
        find.query("ab")
        find.query("abc")
        settle()
        assertEquals(listOf("abc"), seen)
    }

    @Test
    fun blankQueryClearsHits() = runTest {
        val find = controller { _, emit -> emit(listOf(1)) }
        find.query("a")
        settle()
        find.query("  ")
        settle()
        assertTrue(find.state.value.hits.isEmpty())
        assertNull(find.state.value.currentHit)
    }

    @Test
    fun closeStopsARunningScan() = runTest {
        var cancelled = false
        val find = controller { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        find.query("a")
        testScheduler.advanceTimeBy(1000)
        testScheduler.runCurrent()
        find.close()
        testScheduler.runCurrent()
        assertTrue(cancelled)
        assertFalse(find.state.value.open)
    }

    @Test
    fun hitsAreCapped() = runTest {
        val find = controller { _, emit -> emit(List(MAX_FIND_HITS + 500) { it }) }
        find.query("a")
        settle()
        assertEquals(MAX_FIND_HITS, find.state.value.hits.size)
    }

    @Test
    fun rescanRunsOnlyWhenOpen() = runTest {
        var runs = 0
        val find = controller { _, _ -> runs++ }
        find.query("a")
        settle()
        find.rescan()
        settle()
        assertEquals(2, runs)
        find.close()
        find.rescan()
        settle()
        assertEquals(2, runs)
    }
}
