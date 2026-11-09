package app.kholo.reader.tools

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * [work] ko [workers] threads par saath chalata hai, par [collect] hamesha asli kram me aur isi thread par.
 * Ek samay `workers + 1` se zyada nateeje nahi rukte, taaki 50 photos ek saath memory me na aa jayen.
 * Kaam ki galti wahi exception ban kar aati hai, taaki saaf message ban sake.
 */
internal fun <T, R> pipeline(items: List<T>, workers: Int, work: (T) -> R, collect: (Int, R) -> Unit) {
    val pool = Executors.newFixedThreadPool(workers.coerceAtLeast(1))
    try {
        val waiting = ArrayDeque<Future<R>>()
        var next = 0
        for (done in items.indices) {
            while (next < items.size && waiting.size <= workers) {
                val item = items[next++]
                waiting.addLast(pool.submit(Callable { work(item) }))
            }
            val result = try {
                waiting.removeFirst().get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
            collect(done, result)
        }
    } finally {
        pool.shutdownNow()
    }
}

internal enum class StepOutcome { Fits, TooBig, Stop }

/**
 * Kadam halke se bhaari ke kram me hain, isliye binary search se sabse halka kadam milta hai jo target me aaye:
 * chhe ki jagah zyada se zyada teen koshish. [smallestOnly] me seedha aakhri kadam.
 */
internal fun searchSteps(count: Int, smallestOnly: Boolean, attempt: (Int) -> StepOutcome) {
    var low = if (smallestOnly) count - 1 else 0
    var high = count - 1
    while (low <= high) {
        val middle = (low + high) / 2
        when (attempt(middle)) {
            StepOutcome.Fits -> high = middle - 1
            StepOutcome.TooBig -> low = middle + 1
            StepOutcome.Stop -> return
        }
    }
}
