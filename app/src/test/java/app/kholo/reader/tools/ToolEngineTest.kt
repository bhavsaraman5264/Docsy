package app.kholo.reader.tools

import app.kholo.reader.ui.adaptive.WidthClass
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolEngineTest {
    @Test
    fun pipelineKeepsOrderEvenWhenLaterWorkFinishesFirst() {
        val seen = mutableListOf<Pair<Int, Int>>()
        pipeline(listOf(30L, 20L, 10L, 0L), workers = 3, work = { delay ->
            Thread.sleep(delay)
            delay.toInt()
        }) { index, value -> seen += index to value }
        assertEquals(listOf(0 to 30, 1 to 20, 2 to 10, 3 to 0), seen)
    }

    @Test
    fun pipelineNeverHoldsMoreThanItsLimit() {
        val started = AtomicInteger()
        var worst = 0
        var collected = 0
        pipeline(List(50) { it }, workers = 3, work = { started.incrementAndGet() }) { _, _ ->
            worst = maxOf(worst, started.get() - collected)
            collected++
        }
        assertEquals(50, collected)
        assertTrue("held $worst", worst <= 4)
    }

    @Test
    fun pipelineKeepsTheOriginalError() {
        assertThrows(IOException::class.java) {
            pipeline(listOf(1, 2), workers = 2, work = { throw IOException("bad photo") }) { _, _ -> }
        }
    }

    @Test
    fun pipelineHandlesNothing() {
        pipeline(emptyList<Int>(), workers = 2, work = { it }) { _, _ -> error("nothing to collect") }
    }

    private fun tried(count: Int, firstFit: Int): List<Int> {
        val steps = mutableListOf<Int>()
        searchSteps(count, smallestOnly = false) { index ->
            steps += index
            if (index >= firstFit) StepOutcome.Fits else StepOutcome.TooBig
        }
        return steps
    }

    @Test
    fun searchFindsTheGentlestStepThatFitsInThreeTries() {
        for (firstFit in 0 until 6) {
            val steps = tried(6, firstFit)
            assertTrue("$firstFit took $steps", steps.size <= 3)
            assertTrue("$firstFit missed in $steps", firstFit in steps)
        }
    }

    @Test
    fun searchEndsOnTheLastStepWhenNothingFits() {
        val steps = tried(6, firstFit = 99)
        assertEquals(5, steps.last())
        assertTrue(steps.size <= 3)
    }

    @Test
    fun smallestGoesStraightToTheLastStep() {
        val steps = mutableListOf<Int>()
        searchSteps(6, smallestOnly = true) {
            steps += it
            StepOutcome.TooBig
        }
        assertEquals(listOf(5), steps)
    }

    @Test
    fun searchStopsWhenAskedTo() {
        val steps = mutableListOf<Int>()
        searchSteps(6, smallestOnly = false) {
            steps += it
            StepOutcome.Stop
        }
        assertEquals(1, steps.size)
    }

    @Test
    fun everyToolAppearsExactlyOnceInTheGrid() {
        for (width in WidthClass.entries) {
            val shown = ToolGroup.entries.flatMap { toolRows(it, toolColumns(width)).flatten() }
            assertEquals(ToolKind.entries.toSet(), shown.toSet())
            assertEquals(ToolKind.entries.size, shown.size)
        }
    }

    @Test
    fun gridRowsNeverOverflow() {
        for (width in WidthClass.entries) {
            val columns = toolColumns(width)
            for (group in ToolGroup.entries) assertTrue(toolRows(group, columns).all { it.size in 1..columns })
        }
    }

    @Test
    fun splitKeepsEveryPageExactlyOnce() {
        for (after in 1 until 10) {
            val parts = checkNotNull(splitParts(10, after))
            assertEquals(2, parts.size)
            assertEquals((0 until 10).toList(), parts.flatten())
        }
        assertEquals(listOf(0 until 3, 3 until 10), splitParts(10, 3))
    }

    @Test
    fun splitRefusesACutThatLeavesAnEmptyFile() {
        assertNull(splitParts(10, 0))
        assertNull(splitParts(10, 10))
        assertNull(splitParts(10, 11))
        assertNull(splitParts(1, 1))
        assertNull(splitParts(0, 1))
    }

    @Test
    fun onlyShrinkingToolsCompareSizes() {
        val comparing = ToolKind.entries.filter { it.comparesSize }.toSet()
        assertEquals(setOf(ToolKind.Compress, ToolKind.ShrinkPhoto), comparing)
    }

    @Test
    fun savedPercentOnlyShowsARealSaving() {
        assertEquals(90, sizeSavedPercent(1000L, 100L))
        assertNull(sizeSavedPercent(1000L, 1000L))
        assertNull(sizeSavedPercent(1000L, 1200L))
        assertNull(sizeSavedPercent(0L, 100L))
        assertNull(sizeSavedPercent(100_000L, 99_999L))
    }
}
