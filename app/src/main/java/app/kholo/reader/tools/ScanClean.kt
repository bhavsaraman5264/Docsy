package app.kholo.reader.tools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Scan saaf karne ka dimaag. Koi model nahi, sirf seedha hisaab, isliye app me kuch KB hi judte hain.
 * Extension ka `scanClean.ts` bilkul yahi hai; ek me badlav ho to doosre me bhi.
 *
 * Tareeka: chhoti tasveer par pehle "kaagaz kaisa dikhna chahiye" nikalte hain. Jo bada hissa kaagaz jaisa nahi
 * (ungli, table, kinare ki shadow, file ke chhed), use pehchaante hain. Shadow ke andar text dikhta hai, isliye
 * use uske apne rang se bhaag dete hain: woh kaagaz ban jata hai par text bacha rehta hai. Ungli, chhed aur table
 * ke neeche kuch nahi dikhta, isliye woh seedha kaagaz se bhar diye jaate hain. Baaki page ko kaagaz ke rang se
 * bhaag dene par roshni barabar ho jati hai. Bade page par yahi naksha faila kar lagta hai, dekho [PlanPainter].
 */

/** User chip se chunta hai kya saaf karna hai. */
internal enum class CleanFix(val onByDefault: Boolean) {
    /** Photo me se sirf kaagaz kaat kar seedha karna, dekho [findPaper]. */
    Crop(true),

    /** Ungli, haath, table aur kinare ki shadow: jo page ke kinare se andar aata hai. */
    Edges(true),
    Holes(true),

    /** Roshni barabar aur kaagaz safed. */
    Light(true),
    Straighten(true),
    Dots(true),
    Blank(false),
    ;

    val bit: Int get() = 1 shl ordinal

    companion object {
        val defaults: Int = entries.filter(CleanFix::onByDefault).fold(0) { all, fix -> all or fix.bit }
    }
}

internal class CleanChoice(private val fixes: Int, val blackWhite: Boolean) {
    operator fun contains(fix: CleanFix): Boolean = fixes and fix.bit != 0

    /** B&W bina roshni barabar kiye kaala dhabba ban jata hai, isliye woh hamesha saath. */
    val light: Boolean get() = CleanFix.Light in this || blackWhite
}

/** Chhoti tasveer ka lamba kinara. Analysis isi par hota hai: A4 par ~2 pixel prati mm, ungli ke liye kaafi. */
internal const val PLAN_SIDE = 640

/** Chhoti tasveer par nikla naksha. Bade page par [PlanPainter] isse faila kar lagata hai. */
internal class CleanPlan(
    val width: Int,
    val height: Int,
    /** R G B lagatar: har jagah woh rang jisse pixel ko bhaag diya jata hai. */
    val divide: FloatArray,
    /** Bhaag ke baad ka rang, R G B lagatar. Null ka matlab safed kaagaz. */
    val target: FloatArray?,
    /** Har pixel kitna kaagaz se dhakna hai, 0 se 1: ungli, chhed, table. Null jab aisa kuch nahi. */
    val fill: FloatArray?,
    /** Text ki lines itne degree tedhi hain. Ulta ghumane par page seedha hota hai. */
    val tilt: Float,
    /** Ungli, kinara ya chhed mila aur bhara gaya. */
    val found: Boolean,
    /** Page par lagbhag kuch likha hi nahi. */
    val blank: Boolean,
    /** Kaagaz ka aam rang, ghumane ke baad khaali kono ke liye. */
    val paper: Int,
)

private const val CHANNELS = 3
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE = 0xFF
private const val BITS = 8
private const val OPAQUE = 0xFF000000.toInt()
private const val LUMA_RED = 77
private const val LUMA_GREEN = 150
private const val LUMA_BLUE = 29
private const val LUMA_SHIFT = 8
private const val LEVELS = 256

/** Akshar ki motai se bada: isse chhoti kaali cheez "kaagaz ke naksha" me nahi aati. Chhote kinare ka hissa. */
private const val TEXT_REACH = 0.007f

/** Kaagaz ka rang itne bade khaano me naapte hain, phir aas-paas ke sabse ujle khaane se. */
private const val PAPER_BLOCKS = 48
private const val PAPER_REACH = 3

/** Kaagaz se itna andhera hissa "kaagaz nahi" hai. Otsu isse kam ya zyada nahi ja sakta. */
private const val MIN_DIFF = 28
private const val MAX_DIFF = 90
private const val COLOR_WEIGHT = 1f

/** Pakki gandagi se judi halki gandagi bhi usi ki hai, jaise ungli ka ujla beech. */
private const val WEAK_SHARE = 0.6f
private const val MIN_WEAK = 20f

/** Gandagi ke kinare ke paas ka halka rang bhi jaye, aur seema dhundhli ho. */
/**
 * Parchhai (phone ya haath ki, ya kam roshni) kaagaz se gehri par zyada nahi hoti: kaagaz ke ujalepan ke in hisson
 * ke beech woh apne hi rang se bhaag di jati hai aur dheere se kaagaz ban jati hai, upar ka text bacha rehta hai.
 * Isse gehri cheez (logo, moti syahi) asli content hai aur rehti hai.
 */
private const val SHADE_FLOOR = 0.3f
private const val SHADE_FULL = 0.45f

private const val NOISE_GROW = 2
private const val FILL_GROW = 1
private const val FEATHER = 2
private const val EDGE_DARKEN = 2

/** Pixel apni jagah ke kaagaz se itna andhera ho to akshar hai. */
internal const val DARK_SHARE = 0.55f

/** Kinare ki patli patti me jo cheez aaye, woh page ko chhoo rahi maani jati hai. */
private const val EDGE_MARGIN = 0.01f

/** Itna bada hissa page ka hi hissa hai, jaise poori photo wala page. */
private const val MAX_NOISE_SHARE = 0.6f

/** Kinare se itna hi andar aaye to patti hai: table, shadow, kaali border. */
private const val STRIP_DEPTH = 0.12f

/**
 * Ungli: kinare se itna andar tak, itni badi, akshar se kaafi moti, aur gol sire wali. Chhape box ke charon kone
 * bhare hote hain.
 */
private const val FINGER_DEPTH = 0.45f
private const val FINGER_SHARE = 0.25f
private const val FINGER_THICKNESS = 0.012f
private const val CORNER_SIDE = 4
private const val CORNER_FULL = 0.8f
private const val BOX_CORNERS = 4

/** Charon taraf ka table ek ghera banata hai jo beech ko nahi chhoota. */
private const val FRAME_EDGES = 2
private const val FRAME_FILL = 0.6f

/** File ke chhed: gol, ek tay size ke, kinare ki patti me. */
private const val HOLE_BAND = 0.15f
private const val HOLE_MIN = 0.015f
private const val HOLE_MAX = 0.07f
private const val HOLE_ROUND_MIN = 0.6f
private const val HOLE_ROUND_MAX = 1.8f
private const val HOLE_ASPECT = 1.7f

/**
 * Isse kam akshar jaise pixel wali gandagi page ko poora dhakti hai: ungli ke neeche kuch nahi dikhta. Gehri
 * parchhai me text halka dikhta hai, isliye yahan aas-paas se thoda bhi gehra pixel ginta hai: ungli chikni hoti hai.
 */
private const val SOLID_INK = 0.03f
private const val SOLID_TEXTURE = 0.8f

/** Gandagi ke andar band itna chhota halka hissa (jaise nakhun) bhi gandagi hai. */
private const val HOLE_FILL = 0.3f

/** Chamfer doori: seedha kadam 3, tirchha 4. */
private const val STEP = 3
private const val DIAGONAL = 4

private const val MAX_TILT = 5f
private const val TILT_STEP = 0.25f
private const val FINE_STEP = 0.05f
private const val MIN_TILT = 0.3f
private const val TILT_GAIN = 1.04f
private const val MIN_TEXT_POINTS = 300
private const val MAX_TEXT_POINTS = 40_000

/** Isse kam akshar wala page khaali hai. */
private const val BLANK_SHARE = 0.0015f

private const val HALF = 0.5f
private const val WHITE = 255f
private const val HALF_CIRCLE = 180.0

/**
 * Roshni barabar karne me kaagaz safed hota hai aur saath me text bhi grey ho jata. Isliye syahi ek curve se guzarti
 * hai jo use photo se thoda gehra karti hai, halka print bhi; kabhi halka nahi. Kaagaz ke ujalepan ke INK_FULL se
 * neeche poori syahi, INK_NONE se upar kaagaz, beech me milawat, taaki akshar ke kinare chikne rahein.
 */
private const val INK_FULL = 0.62f
private const val INK_NONE = 0.86f
private const val INK_GAMMA = 2f
private val INK_CURVE = FloatArray(LEVELS) { WHITE * (it / WHITE).pow(INK_GAMMA) }

/** Halka unsharp mask, taaki seedha kiye page par bhi akshar tez rahein. */
private const val SHARPEN = 0.6f
private const val SQUARE = 9f

/** Roshni barabar karne ke baad kaagaz ka halka daag bhi safed ho, aur akshar thode gehre. */
private const val LEVEL_SCALE = WHITE / 238f

/** [argb] chhoti tasveer hai, [width] x [height]. */
internal fun planPage(argb: IntArray, width: Int, height: Int, choice: CleanChoice): CleanPlan {
    val size = width * height
    val shortSide = minOf(width, height)
    val reach = (shortSide * TEXT_REACH).roundToInt().coerceAtLeast(1)
    val block = (shortSide / PAPER_BLOCKS).coerceAtLeast(1)
    val closed = Array(CHANNELS) { closing(channel(argb, it), width, height, reach) }
    val paper = Array(CHANNELS) { paperOf(closed[it], width, height, block) }

    val (pr, pg, pb) = paper
    val (cr, cg, cb) = closed
    val ground = IntArray(size) { luma(cr[it], cg[it], cb[it]) }
    val diff = IntArray(size) { i ->
        // Andhera poora ginta hai, alag rang (twacha, lakdi) thoda kam.
        val darker = luma(pr[i], pg[i], pb[i]) - ground[i]
        val color = maxOf(abs(pr[i] - cr[i]), abs(pg[i] - cg[i]), abs(pb[i] - cb[i]))
        maxOf(darker, (color * COLOR_WEIGHT).toInt())
    }
    val limit = otsu(histogram(diff)).coerceIn(MIN_DIFF, MAX_DIFF)
    val big = ByteArray(size) { if (diff[it] > limit) 1 else 0 }
    val noise = ByteArray(size)
    val solid = ByteArray(size)
    val found = markNoise(Sheet(big, width, height, argb, ground), choice, noise, solid)
    // Gandagi sirf gehre hisse se pehchaani jati hai, taaki ungli se judi parchhai ungli ko na chhupaye.
    // Phir woh apne halke hisse (jaise ungli ka beech) tak phailti hai.
    if (found) growWeak(noise, diff, width, height, maxOf(MIN_WEAK, limit * WEAK_SHARE))

    val divide = FloatArray(size * CHANNELS)
    val alpha = if (found) feather(noise, width, height, NOISE_GROW) else null
    val fill = if (found && solid.any { it.toInt() != 0 }) feather(solid, width, height, FILL_GROW) else null
    val near = if (found) Array(CHANNELS) { extreme(closed[it], width, height, EDGE_DARKEN, max = false) } else null
    for (i in 0 until size) {
        val a = alpha?.get(i) ?: 0f
        val ratio = ground[i] / luma(pr[i], pg[i], pb[i]).coerceAtLeast(1).toFloat()
        val shade = ((ratio - SHADE_FLOOR) / (SHADE_FULL - SHADE_FLOOR)).coerceIn(0f, 1f)
        for (c in 0 until CHANNELS) {
            val flat = paper[c][i].toFloat()
            val keep = flat + (minOf(flat, closed[c][i].toFloat()) - flat) * shade
            divide[i * CHANNELS + c] = if (near == null) keep else keep + (near[c][i] - keep) * a
        }
    }
    val target = if (choice.light) {
        null
    } else {
        FloatArray(size * CHANNELS) { paper[it % CHANNELS][it / CHANNELS].toFloat() }
    }

    val every = (size / MAX_TEXT_POINTS).coerceAtLeast(1)
    val text = textPoints(argb, ground, big, width, every)
    val tilt = if (CleanFix.Straighten in choice) tiltOf(text, width, height) else 0f
    val blank = text.size / 2 * every < size * BLANK_SHARE
    return CleanPlan(width, height, divide, target, fill, tilt, found, blank, averageColor(paper))
}

/** [big] ko un chhoote pixel tak phailata hai jo kaagaz se [weak] se zyada alag hain. */
private fun growWeak(big: ByteArray, diff: IntArray, width: Int, height: Int, weak: Float) {
    val queue = IntArray(big.size)
    var tail = 0
    for (at in big.indices) if (big[at].toInt() != 0) queue[tail++] = at
    var head = 0
    while (head < tail) {
        val at = queue[head++]
        val x = at % width
        if (x > 0) tail = grow(big, diff, at - 1, weak, queue, tail)
        if (x < width - 1) tail = grow(big, diff, at + 1, weak, queue, tail)
        if (at >= width) tail = grow(big, diff, at - width, weak, queue, tail)
        if (at < (height - 1) * width) tail = grow(big, diff, at + width, weak, queue, tail)
    }
}

private fun grow(big: ByteArray, diff: IntArray, at: Int, weak: Float, queue: IntArray, tail: Int): Int {
    if (big[at].toInt() != 0 || diff[at] <= weak) return tail
    big[at] = 1
    queue[tail] = at
    return tail + 1
}

private fun channel(argb: IntArray, index: Int): IntArray {
    val shift = when (index) {
        0 -> RED_SHIFT
        1 -> GREEN_SHIFT
        else -> 0
    }
    return IntArray(argb.size) { (argb[it] shr shift) and BYTE }
}

internal fun luma(red: Int, green: Int, blue: Int): Int =
    (red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE) shr LUMA_SHIFT

private fun lumaOf(pixel: Int): Int {
    val red = (pixel shr RED_SHIFT) and BYTE
    val green = (pixel shr GREEN_SHIFT) and BYTE
    return luma(red, green, pixel and BYTE)
}

/** Kaali patli cheezein (akshar) mit jati hain, badi cheezein (ungli, table) apni shakal me rehti hain. */
private fun closing(values: IntArray, width: Int, height: Int, reach: Int): IntArray =
    extreme(extreme(values, width, height, reach, max = true), width, height, reach, max = false)

/**
 * [reach] pixel ke square me sabse bada ya chhota. Pehle har pankti, phir poori panktiyan ek saath, taaki memory
 * kram se padhi jaye aur kaam tez rahe.
 */
internal fun extreme(values: IntArray, width: Int, height: Int, reach: Int, max: Boolean): IntArray = if (max) {
    extremeOf(values, width, height, reach) { a, b -> a > b }
} else {
    extremeOf(values, width, height, reach) { a, b -> a < b }
}

private inline fun extremeOf(
    values: IntArray,
    width: Int,
    height: Int,
    reach: Int,
    better: (Int, Int) -> Boolean,
): IntArray {
    val across = IntArray(values.size)
    for (y in 0 until height) {
        val base = y * width
        for (x in 0 until width) {
            val to = base + minOf(width - 1, x + reach)
            var k = base + maxOf(0, x - reach)
            var best = values[k]
            while (++k <= to) if (better(values[k], best)) best = values[k]
            across[base + x] = best
        }
    }
    val out = IntArray(values.size)
    for (y in 0 until height) {
        val row = y * width
        val from = maxOf(0, y - reach)
        System.arraycopy(across, from * width, out, row, width)
        for (k in from + 1..minOf(height - 1, y + reach)) {
            val source = k * width
            for (x in 0 until width) if (better(across[source + x], out[row + x])) out[row + x] = across[source + x]
        }
    }
    return out
}

/**
 * Kaagaz ka rang har jagah: khaano me sabse ujla, phir aas-paas ka sabse ujla (ungli ke neeche bhi kaagaz
 * ka rang aa jaye), phir chikna. Roshni ka dheere badalna isme rehta hai, isliye bhaag dene par woh mit jata hai.
 */
private fun paperOf(closed: IntArray, width: Int, height: Int, block: Int): IntArray {
    val cols = (width + block - 1) / block
    val rows = (height + block - 1) / block
    val coarse = IntArray(cols * rows)
    for (y in 0 until height) {
        val row = (y / block) * cols
        for (x in 0 until width) {
            val cell = row + x / block
            coarse[cell] = maxOf(coarse[cell], closed[y * width + x])
        }
    }
    val bright = extreme(coarse, cols, rows, PAPER_REACH, max = true)
    val smooth = boxBlur(boxBlur(bright, cols, rows), cols, rows)
    return upsample(smooth, cols, rows, width, height, block)
}

private fun boxBlur(values: IntArray, width: Int, height: Int): IntArray = IntArray(values.size) { i ->
    val x = i % width
    val y = i / width
    var sum = 0
    var count = 0
    for (dy in -1..1) {
        for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) {
                sum += values[ny * width + nx]
                count++
            }
        }
    }
    sum / count
}

/** Khaano ke beech seedhi line se rang bharna. Khaane ka beech uske rang ki asli jagah hai. */
private fun upsample(coarse: IntArray, cols: Int, rows: Int, width: Int, height: Int, block: Int): IntArray {
    val out = IntArray(width * height)
    for (y in 0 until height) {
        val fy = ((y + HALF) / block - HALF).coerceIn(0f, (rows - 1).toFloat())
        val y0 = fy.toInt()
        val y1 = minOf(y0 + 1, rows - 1)
        val ty = fy - y0
        for (x in 0 until width) {
            val fx = ((x + HALF) / block - HALF).coerceIn(0f, (cols - 1).toFloat())
            val x0 = fx.toInt()
            val x1 = minOf(x0 + 1, cols - 1)
            val tx = fx - x0
            val top = coarse[y0 * cols + x0] + (coarse[y0 * cols + x1] - coarse[y0 * cols + x0]) * tx
            val bottom = coarse[y1 * cols + x0] + (coarse[y1 * cols + x1] - coarse[y1 * cols + x0]) * tx
            out[y * width + x] = (top + (bottom - top) * ty).roundToInt()
        }
    }
    return out
}

private fun histogram(values: IntArray): IntArray {
    val counts = IntArray(LEVELS)
    for (value in values) counts[value.coerceIn(0, LEVELS - 1)]++
    return counts
}

/** Do guton me baantne ki sabse achhi seema. */
internal fun otsu(counts: IntArray): Int {
    val total = counts.sum().toDouble()
    if (total == 0.0) return 0
    var sumAll = 0.0
    for (level in counts.indices) sumAll += level.toDouble() * counts[level]
    var sumLow = 0.0
    var low = 0.0
    var best = 0.0
    var limit = 0
    for (level in counts.indices) {
        low += counts[level]
        if (low == 0.0) continue
        val high = total - low
        if (high == 0.0) break
        sumLow += level.toDouble() * counts[level]
        val gap = sumLow / low - (sumAll - sumLow) / high
        val between = low * high * gap * gap
        if (between > best) {
            best = between
            limit = level
        }
    }
    return limit
}

/** Chhota page jaisa [markNoise] ko chahiye. [ground] text hata kar page ka ujalapan hai. */
private class Sheet(val big: ByteArray, val width: Int, val height: Int, val argb: IntArray, val ground: IntArray)

/** Ek "kaagaz nahi" wala tukda aur uski pehchaan. */
private class Blob(val pixels: IntArray, val count: Int, val width: Int, val height: Int, distance: IntArray) {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    val thickness: Float

    init {
        var deepest = 0
        for (k in 0 until count) {
            val at = pixels[k]
            val x = at % width
            val y = at / width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
            deepest = maxOf(deepest, distance[at])
        }
        thickness = deepest.toFloat() / STEP
    }

    val share: Float get() = count.toFloat() / (width * height)
    val boxFill: Float get() = count.toFloat() / ((right - left + 1) * (bottom - top + 1))
}

/**
 * Kaagaz-nahi wale tukde dhoondh kar jo gandagi hain unhe [noise] me likhta hai. Jo gandagi page ko poora dhakti
 * hai (andar koi text nahi dikhta), woh [solid] me bhi. Kuch mila to true.
 */
private fun markNoise(sheet: Sheet, choice: CleanChoice, noise: ByteArray, solid: ByteArray): Boolean {
    val edges = CleanFix.Edges in choice
    val holes = CleanFix.Holes in choice
    if (!edges && !holes) return false
    val big = sheet.big
    val distance = chamfer(big, sheet.width, sheet.height)
    val seen = BooleanArray(big.size)
    val queue = IntArray(big.size)
    var found = false
    for (start in big.indices) {
        if (big[start].toInt() == 0 || seen[start]) continue
        val count = flood(big, sheet.width, sheet.height, start, seen, queue)
        val blob = Blob(queue, count, sheet.width, sheet.height, distance)
        val bad = if (touchedEdges(blob) > 0) edges && isEdgeNoise(blob) else holes && isHole(blob)
        if (!bad) continue
        val masks = if (inkShare(sheet, blob) < SOLID_INK) listOf(noise, solid) else listOf(noise)
        for (mask in masks) for (k in 0 until count) mask[queue[k]] = 1
        fillHoles(blob, masks)
        found = true
    }
    return found
}

/** Tukde ka kitna hissa akshar jaisa hai. Shadow ke andar text dikhta hai, ungli ke andar nahi. */
private fun inkShare(sheet: Sheet, blob: Blob): Float {
    var ink = 0
    for (k in 0 until blob.count) {
        val at = blob.pixels[k]
        if (lumaOf(sheet.argb[at]) < sheet.ground[at] * SOLID_TEXTURE) ink++
    }
    return ink.toFloat() / blob.count
}

/**
 * Gandagi ke andar band halke hisse (jaise nakhun) bhi gandagi hain. Bada band hissa nahi: charon taraf dikhte
 * table ke andar ka poora page.
 */
private fun fillHoles(blob: Blob, masks: List<ByteArray>) {
    val boxWidth = blob.right - blob.left + 1
    val boxHeight = blob.bottom - blob.top + 1
    val open = ByteArray(boxWidth * boxHeight) { 1 }
    for (k in 0 until blob.count) {
        val at = blob.pixels[k]
        open[(at / blob.width - blob.top) * boxWidth + at % blob.width - blob.left] = 0
    }
    val seen = BooleanArray(open.size)
    val queue = IntArray(open.size)
    val last = open.size - 1
    for (at in 0..last) {
        val x = at % boxWidth
        val border = at < boxWidth || at > last - boxWidth || x == 0 || x == boxWidth - 1
        if (border && open[at].toInt() != 0 && !seen[at]) flood(open, boxWidth, boxHeight, at, seen, queue)
    }
    for (at in 0..last) {
        if (open[at].toInt() == 0 || seen[at]) continue
        val count = flood(open, boxWidth, boxHeight, at, seen, queue)
        if (count > blob.count * HOLE_FILL) continue
        for (k in 0 until count) {
            val local = queue[k]
            val pixel = (local / boxWidth + blob.top) * blob.width + local % boxWidth + blob.left
            for (mask in masks) mask[pixel] = 1
        }
    }
}

/** Juda hua tukda, chaar disha me. Pixel [queue] ke shuru me aate hain. */
private fun flood(mask: ByteArray, width: Int, height: Int, start: Int, seen: BooleanArray, queue: IntArray): Int {
    var head = 0
    var tail = 0
    queue[tail++] = start
    seen[start] = true
    while (head < tail) {
        val at = queue[head++]
        val x = at % width
        if (x > 0) tail = visit(mask, at - 1, seen, queue, tail)
        if (x < width - 1) tail = visit(mask, at + 1, seen, queue, tail)
        if (at >= width) tail = visit(mask, at - width, seen, queue, tail)
        if (at < (height - 1) * width) tail = visit(mask, at + width, seen, queue, tail)
    }
    return tail
}

private fun visit(mask: ByteArray, at: Int, seen: BooleanArray, queue: IntArray, tail: Int): Int {
    if (mask[at].toInt() == 0 || seen[at]) return tail
    seen[at] = true
    queue[tail] = at
    return tail + 1
}

private fun edgeMargin(blob: Blob): Int = (minOf(blob.width, blob.height) * EDGE_MARGIN).toInt().coerceAtLeast(1)

private fun touchesLeft(blob: Blob) = blob.left < edgeMargin(blob)

private fun touchesTop(blob: Blob) = blob.top < edgeMargin(blob)

private fun touchesRight(blob: Blob) = blob.right >= blob.width - edgeMargin(blob)

private fun touchesBottom(blob: Blob) = blob.bottom >= blob.height - edgeMargin(blob)

private fun touchedEdges(blob: Blob): Int =
    listOf(touchesLeft(blob), touchesTop(blob), touchesRight(blob), touchesBottom(blob)).count { it }

/** Jis kinare se aaya, wahan se page me kitna andar tak. Kai kinare chhoye to sabse kam. */
private fun depth(blob: Blob): Float {
    var depth = 1f
    if (touchesLeft(blob)) depth = minOf(depth, (blob.right + 1f) / blob.width)
    if (touchesRight(blob)) depth = minOf(depth, (blob.width - blob.left).toFloat() / blob.width)
    if (touchesTop(blob)) depth = minOf(depth, (blob.bottom + 1f) / blob.height)
    if (touchesBottom(blob)) depth = minOf(depth, (blob.height - blob.top).toFloat() / blob.height)
    return depth
}

private fun coversCenter(blob: Blob): Boolean {
    val center = (blob.height / 2) * blob.width + blob.width / 2
    for (k in 0 until blob.count) if (blob.pixels[k] == center) return true
    return false
}

/** Tukde ke box ke kitne kone bhare hain. Chaukor me chaar; ungli ka gol sira do khaali chhodta hai. */
private fun fullCorners(blob: Blob): Int {
    val boxWidth = blob.right - blob.left + 1
    val boxHeight = blob.bottom - blob.top + 1
    val side = (minOf(boxWidth, boxHeight) / CORNER_SIDE).coerceAtLeast(2)
    val counts = IntArray(BOX_CORNERS)
    for (k in 0 until blob.count) {
        val x = blob.pixels[k] % blob.width - blob.left
        val y = blob.pixels[k] / blob.width - blob.top
        val column = when {
            x < side -> 0
            x >= boxWidth - side -> 1
            else -> continue
        }
        val row = when {
            y < side -> 0
            y >= boxHeight - side -> 2
            else -> continue
        }
        counts[row + column]++
    }
    return counts.count { it >= side * side * CORNER_FULL }
}

/** Kinare se aayi cheez: patti (table, shadow, border), ghera, ya ungli. Chhapa hua box ya photo nahi. */
private fun isEdgeNoise(blob: Blob): Boolean {
    if (blob.share > MAX_NOISE_SHARE) return false
    val depth = depth(blob)
    val strip = depth <= STRIP_DEPTH
    val frame = touchedEdges(blob) >= FRAME_EDGES && blob.boxFill < FRAME_FILL && !coversCenter(blob)
    val thick = blob.thickness >= minOf(blob.width, blob.height) * FINGER_THICKNESS
    val finger = depth <= FINGER_DEPTH && blob.share <= FINGER_SHARE && thick && fullCorners(blob) < BOX_CORNERS
    return strip || frame || finger
}

/** File ka chhed: gol, sahi size ka, aur kinare ke paas. */
private fun isHole(blob: Blob): Boolean {
    val shortSide = minOf(blob.width, blob.height).toFloat()
    val across = 2 * blob.thickness / shortSide
    if (across !in HOLE_MIN..HOLE_MAX) return false
    val round = blob.count / (PI.toFloat() * blob.thickness * blob.thickness)
    val boxWidth = (blob.right - blob.left + 1).toFloat()
    val boxHeight = (blob.bottom - blob.top + 1).toFloat()
    val aspect = maxOf(boxWidth / boxHeight, boxHeight / boxWidth)
    val centerX = (blob.left + blob.right) / 2f / blob.width
    val centerY = (blob.top + blob.bottom) / 2f / blob.height
    val nearEdge = minOf(centerX, 1 - centerX) <= HOLE_BAND || minOf(centerY, 1 - centerY) <= HOLE_BAND
    return round in HOLE_ROUND_MIN..HOLE_ROUND_MAX && aspect <= HOLE_ASPECT && nearEdge
}

/** Har pixel ki tukde ke kinare se doori, [STEP] guna. Isse pata chalta hai cheez kitni moti hai. */
private fun chamfer(mask: ByteArray, width: Int, height: Int): IntArray {
    val far = Int.MAX_VALUE / 2
    val distance = IntArray(mask.size) { if (mask[it].toInt() == 0) 0 else far }
    for (y in 0 until height) {
        for (x in 0 until width) {
            val at = y * width + x
            if (distance[at] == 0) continue
            var best = distance[at]
            if (x > 0) best = minOf(best, distance[at - 1] + STEP)
            if (y > 0) {
                best = minOf(best, distance[at - width] + STEP)
                if (x > 0) best = minOf(best, distance[at - width - 1] + DIAGONAL)
                if (x < width - 1) best = minOf(best, distance[at - width + 1] + DIAGONAL)
            }
            distance[at] = best
        }
    }
    for (y in height - 1 downTo 0) {
        for (x in width - 1 downTo 0) {
            val at = y * width + x
            if (distance[at] == 0) continue
            var best = distance[at]
            if (x < width - 1) best = minOf(best, distance[at + 1] + STEP)
            if (y < height - 1) {
                best = minOf(best, distance[at + width] + STEP)
                if (x < width - 1) best = minOf(best, distance[at + width + 1] + DIAGONAL)
                if (x > 0) best = minOf(best, distance[at + width - 1] + DIAGONAL)
            }
            distance[at] = best
        }
    }
    return distance
}

/** Gandagi [grow] pixel phaila kar dhundhli seema: 0 kaagaz, 1 poori gandagi. Kinare par sirf asli padosi ginte. */
private fun feather(mask: ByteArray, width: Int, height: Int, grow: Int): FloatArray {
    val plain = IntArray(mask.size) { mask[it].toInt() }
    val grown = if (grow > 0) extreme(plain, width, height, grow, max = true) else plain
    val across = FloatArray(grown.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val from = maxOf(0, x - FEATHER)
            val to = minOf(width - 1, x + FEATHER)
            var sum = 0
            for (k in from..to) sum += grown[y * width + k]
            across[y * width + x] = sum.toFloat() / (to - from + 1)
        }
    }
    val out = FloatArray(grown.size)
    for (y in 0 until height) {
        val from = maxOf(0, y - FEATHER)
        val to = minOf(height - 1, y + FEATHER)
        for (x in 0 until width) {
            var sum = 0f
            for (k in from..to) sum += across[k * width + x]
            out[y * width + x] = sum / (to - from + 1)
        }
    }
    return out
}

/**
 * Har [every] me se ek pixel jaanch kar akshar ke pixel (x, y lagatar): apni jagah ke kaagaz se kaafi andhere,
 * aur kisi badi cheez ka hissa nahi.
 */
private fun textPoints(argb: IntArray, ground: IntArray, big: ByteArray, width: Int, every: Int): IntArray {
    val points = ArrayList<Int>()
    for (at in argb.indices step every) {
        if (big[at].toInt() != 0) continue
        if (lumaOf(argb[at]) < ground[at] * DARK_SHARE) {
            points += at % width
            points += at / width
        }
    }
    return points.toIntArray()
}

/**
 * Text ki lines kitni tedhi hain: jis kon par ghumane se har line ek pankti me aa jaye, wahan pankti ke
 * hisaab me sabse oonche khambe bante hain. Pakka farak na ho to 0, taaki seedha page na ghoome.
 */
internal fun tiltOf(points: IntArray, width: Int, height: Int): Float {
    if (points.size / 2 < MIN_TEXT_POINTS) return 0f
    val bins = IntArray(width + height + 2)
    val flat = peakiness(points, 0f, bins, width)
    var best = 0f
    var bestScore = flat
    fun scan(from: Float, to: Float, step: Float) {
        var angle = from
        while (angle <= to + step / 2) {
            val score = peakiness(points, angle, bins, width)
            if (score > bestScore) {
                bestScore = score
                best = angle
            }
            angle += step
        }
    }
    scan(-MAX_TILT, MAX_TILT, TILT_STEP)
    scan(best - TILT_STEP, best + TILT_STEP, FINE_STEP)
    return if (abs(best) >= MIN_TILT && bestScore >= flat * TILT_GAIN) best else 0f
}

private fun peakiness(points: IntArray, degrees: Float, bins: IntArray, width: Int): Double {
    bins.fill(0)
    val radians = degrees * PI / HALF_CIRCLE
    val cos = cos(radians)
    val sin = sin(radians)
    for (k in points.indices step 2) {
        val row = (points[k + 1] * cos - points[k] * sin).roundToInt() + width
        bins[row.coerceIn(0, bins.size - 1)]++
    }
    var score = 0.0
    for (count in bins) score += count.toDouble() * count
    return score
}

private fun averageColor(paper: Array<IntArray>): Int {
    var color = OPAQUE
    for (c in 0 until CHANNELS) {
        val mean = (paper[c].sumOf(Int::toLong) / paper[c].size.coerceAtLeast(1)).toInt()
        color = color or (mean shl ((CHANNELS - 1 - c) * BITS))
    }
    return color
}

/**
 * Chhoti tasveer ka naksha bade page ki har pankti par lagata hai. Har pixel ek "gain" se guna hota hai: kaagaz
 * ka rang (ya safed) bata apni jagah ka rang. Gain chhote naksha ki har pankti ke liye ek baar banta hai, isliye
 * bade page par koi bhaag nahi. Ek page ke liye ek painter, ek hi thread par.
 */
internal class PlanPainter(private val plan: CleanPlan, private val width: Int, private val height: Int) {
    private val stride = plan.width * CHANNELS
    private val gains = FloatArray(plan.height * stride) { k ->
        val target = plan.target?.get(k) ?: (WHITE * LEVEL_SCALE)
        target / plan.divide[k].coerceAtLeast(1f)
    }
    private val grounds = FloatArray(plan.height * plan.width) { paperGray(it) * DARK_SHARE }
    private val x0 = IntArray(width)
    private val x1 = IntArray(width)
    private val tx = FloatArray(width)
    private val gainRow = FloatArray(stride)
    private val groundRow = FloatArray(plan.width)
    private val fillRow = plan.fill?.let { FloatArray(plan.width) }
    private val paperRow = FloatArray(stride) { WHITE }

    /** Saaf page ke ujalepan ki ginti, B&W ki seema ke liye. */
    val histogram = IntArray(LEVELS)

    init {
        for (x in 0 until width) {
            val fx = ((x + HALF) * plan.width / width - HALF).coerceIn(0f, (plan.width - 1).toFloat())
            x0[x] = fx.toInt()
            x1[x] = minOf(x0[x] + 1, plan.width - 1)
            tx[x] = fx - x0[x]
        }
    }

    /** Pankti [y] ke pixel [row] me badalte hain. [dark] mile to akshar wale pixel 1 ho jate hain. */
    fun paint(row: IntArray, offset: Int, y: Int, dark: ByteArray?) {
        val filling = prepareRow(y)
        for (x in 0 until width) {
            val a = x0[x]
            val b = x1[x]
            val t = tx[x]
            val pixel = row[offset + x]
            val a3 = a * CHANNELS
            val b3 = b * CHANNELS
            var red = tone((pixel shr RED_SHIFT) and BYTE, a3, b3, t)
            var green = tone((pixel shr GREEN_SHIFT) and BYTE, a3 + 1, b3 + 1, t)
            var blue = tone(pixel and BYTE, a3 + 2, b3 + 2, t)
            val ground = groundRow[a] + (groundRow[b] - groundRow[a]) * t
            val ink = inkShare(red, green, blue, ground)
            if (ink > 0f) {
                red += (minOf(INK_CURVE[red.toInt()], ((pixel shr RED_SHIFT) and BYTE).toFloat()) - red) * ink
                green += (minOf(INK_CURVE[green.toInt()], ((pixel shr GREEN_SHIFT) and BYTE).toFloat()) - green) * ink
                blue += (minOf(INK_CURVE[blue.toInt()], (pixel and BYTE).toFloat()) - blue) * ink
            }
            if (filling) {
                val f = fillRow!![a] + (fillRow[b] - fillRow[a]) * t
                red += (paperRow[a3] - red) * f
                green += (paperRow[a3 + 1] - green) * f
                blue += (paperRow[a3 + 2] - blue) * f
            }
            val r = red.toInt()
            val g = green.toInt()
            val bl = blue.toInt()
            row[offset + x] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or bl
            val value = luma(r, g, bl)
            histogram[value]++
            if (dark != null && value < ground) dark[y * width + x] = 1
        }
    }

    /** Chhote naksha ke pixel [k] par kaagaz ka ujalapan. */
    private fun paperGray(k: Int): Float {
        val target = plan.target ?: return WHITE
        val at = k * CHANNELS
        return luma(target[at].toInt(), target[at + 1].toInt(), target[at + 2].toInt()).toFloat()
    }

    /** Pixel kitna syahi hai, 0 se 1, roshni barabar hone ke baad ke ujalepan se. [ground] syahi ki seema hai. */
    private fun inkShare(red: Float, green: Float, blue: Float, ground: Float): Float {
        val paper = ground / DARK_SHARE
        val lifted = (red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE) / LEVELS
        return ((paper * INK_NONE - lifted) / (paper * (INK_NONE - INK_FULL))).coerceIn(0f, 1f)
    }

    /** Pixel (x, y) par kaagaz kaisa hona chahiye: daane mitane ke liye. */
    fun paperAt(x: Int, y: Int): Int {
        val target = plan.target ?: return WHITE_COLOR
        val px = ((x + HALF) * plan.width / width).toInt().coerceIn(0, plan.width - 1)
        val py = ((y + HALF) * plan.height / height).toInt().coerceIn(0, plan.height - 1)
        val at = (py * plan.width + px) * CHANNELS
        return OPAQUE or (target[at].toInt() shl RED_SHIFT) or (target[at + 1].toInt() shl GREEN_SHIFT) or
            target[at + 2].toInt()
    }

    /** Is pankti ke gain, seema aur bharna. Pankti me kuch bharna ho to true. */
    private fun prepareRow(y: Int): Boolean {
        val fy = ((y + HALF) * plan.height / height - HALF).coerceIn(0f, (plan.height - 1).toFloat())
        val y0 = fy.toInt()
        val y1 = minOf(y0 + 1, plan.height - 1)
        val t = fy - y0
        lerpRows(gains, gainRow, y0 * stride, y1 * stride, t)
        lerpRows(grounds, groundRow, y0 * plan.width, y1 * plan.width, t)
        val fill = plan.fill ?: return false
        lerpRows(fill, fillRow!!, y0 * plan.width, y1 * plan.width, t)
        val filling = fillRow.any { it > 0f }
        val target = plan.target
        if (filling && target != null) lerpRows(target, paperRow, y0 * stride, y1 * stride, t)
        return filling
    }

    private fun lerpRows(source: FloatArray, into: FloatArray, a: Int, b: Int, t: Float) {
        for (k in into.indices) into[k] = source[a + k] + (source[b + k] - source[a + k]) * t
    }

    private fun tone(value: Int, i0: Int, i1: Int, t: Float): Float =
        minOf(WHITE, value * (gainRow[i0] + (gainRow[i1] - gainRow[i0]) * t))
}

private const val WHITE_COLOR = -1

/**
 * Akshar thode tez: har rang apne 3x3 ke ausat se thoda door jata hai. Saaf page ka zyaadatar hissa safed kaagaz hai,
 * jo chhod diya jata hai, isliye yeh jaldi hota hai.
 */
internal fun sharpen(argb: IntArray, width: Int, height: Int) {
    val source = argb.copyOf()
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val at = y * width + x
            if (source[at] == WHITE_COLOR) continue
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val near = source[at + dy * width + dx]
                    red += (near shr RED_SHIFT) and BYTE
                    green += (near shr GREEN_SHIFT) and BYTE
                    blue += near and BYTE
                }
            }
            val pixel = source[at]
            argb[at] = OPAQUE or (crisp((pixel shr RED_SHIFT) and BYTE, red) shl RED_SHIFT) or
                (crisp((pixel shr GREEN_SHIFT) and BYTE, green) shl GREEN_SHIFT) or crisp(pixel and BYTE, blue)
        }
    }
}

private fun crisp(value: Int, sum: Int): Int = (value + SHARPEN * (value - sum / SQUARE)).roundToInt().coerceIn(0, BYTE)

/**
 * Daana: 200 DPI par aadhe mm tak ki syahi, jiske aas-paas aur aadhe mm tak kuch nahi. Poorn viraam bhi itna
 * hi bada hota hai, par hamesha kisi akshar ke paas; i ki bindi aur Hindi ka bindu bhi.
 */
private const val SPECK_MAX = 12
private const val SPECK_NEAR = 3
private const val SPECK_FAR = 7

/**
 * Akele daane dhoondhta hai. [dark] me 1 wale pixel akshar hain. Nateeje me daane wale pixel 1. Daane ke 7x7 me
 * kam pixel hote hain aur 15x15 me usse zyada nahi, yaani aas-paas kuch nahi. 15x15 ki ginti sirf unhi kuch
 * pixel ke liye banti hai jo pehli jaanch me paas hon.
 */
internal fun speckles(dark: ByteArray, width: Int, height: Int): ByteArray {
    val near = boxCount(dark, width, height, SPECK_NEAR)
    for (at in dark.indices) {
        val count = near[at].toInt()
        val speck = dark[at].toInt() != 0 && count <= SPECK_MAX && countAround(dark, width, height, at) == count
        near[at] = if (speck) 1 else 0
    }
    return near
}

private fun countAround(dark: ByteArray, width: Int, height: Int, at: Int): Int {
    val x = at % width
    val y = at / width
    var count = 0
    for (ny in maxOf(0, y - SPECK_FAR)..minOf(height - 1, y + SPECK_FAR)) {
        for (nx in maxOf(0, x - SPECK_FAR)..minOf(width - 1, x + SPECK_FAR)) count += dark[ny * width + nx]
    }
    return count
}

/** Har pixel ke (2r+1) square me kitne 1. Ginti 49 tak, Byte me aati hai. Memory kram se padhi jati hai. */
private fun boxCount(src: ByteArray, width: Int, height: Int, reach: Int): ByteArray {
    val across = ByteArray(src.size)
    for (y in 0 until height) {
        val base = y * width
        var sum = 0
        for (x in 0..minOf(reach, width - 1)) sum += src[base + x]
        for (x in 0 until width) {
            across[base + x] = sum.toByte()
            if (x + reach + 1 < width) sum += src[base + x + reach + 1]
            if (x - reach >= 0) sum -= src[base + x - reach]
        }
    }
    val out = ByteArray(src.size)
    val column = IntArray(width)
    for (y in 0..minOf(reach, height - 1)) for (x in 0 until width) column[x] += across[y * width + x]
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) out[row + x] = column[x].toByte()
        if (y + reach + 1 < height) {
            val add = (y + reach + 1) * width
            for (x in 0 until width) column[x] += across[add + x]
        }
        if (y - reach >= 0) {
            val drop = (y - reach) * width
            for (x in 0 until width) column[x] -= across[drop + x]
        }
    }
    return out
}
