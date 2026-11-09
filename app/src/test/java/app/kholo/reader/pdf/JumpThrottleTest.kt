package app.kholo.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpThrottleTest {
    private val throttle = JumpThrottle(50L)

    @Test
    fun firstMoveJumpsAtOnce() {
        assertEquals(3, throttle.offer(3, now = 1_000L))
    }

    @Test
    fun fastMovesWaitButTheLastPageIsKept() {
        throttle.offer(3, 1_000L)
        assertNull(throttle.offer(4, 1_010L))
        assertNull(throttle.offer(5, 1_020L))
        assertEquals(5, throttle.pending)
        assertEquals(30L, throttle.waitFrom(1_020L))
        assertEquals(5, throttle.flush(1_050L))
        assertNull(throttle.pending)
    }

    @Test
    fun comingBackToTheShownPageNeedsNoJump() {
        throttle.offer(3, 1_000L)
        assertNull(throttle.offer(4, 1_010L))
        assertNull(throttle.offer(3, 1_020L))
        assertNull(throttle.pending)
        assertNull(throttle.flush(1_100L))
    }

    @Test
    fun slowMovesJumpEveryTime() {
        assertEquals(1, throttle.offer(1, 1_000L))
        assertEquals(2, throttle.offer(2, 1_060L))
        assertEquals(3, throttle.offer(3, 1_120L))
    }

    @Test
    fun resetStartsFresh() {
        throttle.offer(3, 1_000L)
        throttle.offer(4, 1_010L)
        throttle.reset()
        assertNull(throttle.pending)
        assertEquals(3, throttle.offer(3, 1_011L))
    }
}
