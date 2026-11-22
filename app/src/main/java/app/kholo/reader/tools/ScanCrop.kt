package app.kholo.reader.tools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phone ki photo me kaagaz dhoondh kar use seedha kaatta hai, scanner app jaisa. Extension ka `scanCrop.ts`
 * bilkul yahi hai; ek me badlav ho to doosre me bhi.
 *
 * Chhoti tasveer par jahan ujalapan achanak badle wahan kinaari hai. Kaagaz farsh se ujla hota hai, isliye uski
 * har kinaari ka ujla hissa kaagaz ke andar ki taraf hota hai. Un kinaariyon se seedhi lakeerein nikalti hain
 * (Hough), aur har chaar lakeeron ke kaagaz jaise dabbe ko is par number milta hai ki uska kitna ghera sach me
 * kinaari hai. Photo ka apna kinara bhi thoda ginta hai, taaki photo se bahar jaata kaagaz bhi mile. Phir chaaron
 * lakeerein apne kinaari ke pixel par theek baithayi jaati hain.
 */

/** Isse chhota hissa page nahi; poori photo rakhna surakshit hai. */
private const val MIN_PAPER_SHARE = 0.2f

/** Chaaron kone photo ke kono ke itne paas: photo pehle se sirf page hai. */
private const val WHOLE_PHOTO = 0.03f

/** Kinaari ka pixel: ujalapan kam se kam itna badle (Sobel), aur photo ke tez badlav me ho. */
private const val MIN_EDGE = 24f
private const val EDGE_PART = 0.7f

/** Kinaari ka pixel lakeer ka tab hai jab uski disha lakeer ki disha se itne degree tak hi alag ho. */
private const val FACING = 20f

/** Hough ke kadam: ek degree, ek pixel. Har pixel apni disha ke aas-paas itne degree ko vote deta hai. */
private const val ANGLES = 360
private const val VOTE_SPREAD = 3
private const val APART_ANGLE = 6f
private const val APART_DISTANCE = 0.04f
private const val MAX_LINES = 28

/** Lakeer ko photo ke chhote kinare ke itne hisse jitni kinaari chahiye. */
private const val MIN_LINE = 0.15f

/** Agli kinaari lagbhag chauthai chakkar mudti hai. */
private const val MIN_TURN = 55f
private const val MAX_TURN = 125f

/** Aamne-saamne ki kinaariyan tirchhi photo me bhi lagbhag samaanaantar. Zyada farak: peeche ka kaagaz ya farsh. */
private const val MAX_SKEW = 12f

/**
 * Ghere ka kam se kam itna hissa asli kinaari ho jiske bahar farsh hai. Normal PDF ki chhapi table ke dono taraf
 * kaagaz hota hai, isliye normal page kabhi nahi katta.
 */
private const val MIN_REAL = 0.25f

/** Likha hua kaatna sabse buri galti hai: dabbe me photo ki zyaadatar syahi honi chahiye. */
private const val MIN_INK_INSIDE = 0.75f
private const val MIN_INK_POINTS = 200
private const val MAX_INK_POINTS = 20_000
private const val INK_SHARE = 0.65f
private const val INK_REACH = 3

/** Syahi kaagaz par hoti hai: aas-paas lagbhag kaagaz jitna ujla, granite jaisa gehra nahi. */
private const val INK_GROUND = 0.7f

/** Kaagaz ka ujalapan photo ke beech ke itne hisse se seekhte hain. */
private const val MIDDLE = 0.2f
private const val BRIGHT_PART = 0.9f
private const val MAX_TRIES = 40

/** Lagbhag barabar ghere wale do dabbon me bada jeete: aadha mod kaagaz nahi. */
private const val AREA_BONUS = 0.5f

/** Kone photo ke bahar itne tak ja sakte hain (photo se bahar jaata kaagaz). */
private const val OUTSIDE = 0.08f

/** Photo ka kinara bahar jaati kinaari ki jagah le sakta hai, par asli kinaari se kam ginta hai. */
private const val BORDER_SUPPORT = 0.2f

/**
 * Asli kinaari ke bahar kaafi door tak farsh hota hai; chhapi lakeer ya mod ke paar fir kaagaz. Ujalapan itna
 * andar aur bahar dekhte hain, chhote kinare ke hisse me.
 */
private const val LOOK_IN = 0.02f
private const val LOOK_OUT = 0.035f
private const val MIN_CONTRAST = 10f
private const val SIDE_SAMPLES = 24
private const val ENOUGH_SAMPLES = 4

/** Jis kinaari ke bahar bhi kaagaz jitna ujala ho, woh itna hi ginti hai. */
private const val WEAK_SIDE = 0.15f

/** Kinaari ke pixel lakeer ke dono taraf itne pixel tak dhoondhte hain. */
private const val NEAR = 2

private const val FIT_ROUNDS = 3
private const val MIN_FIT_POINTS = 12
private const val FIT_SPREAD = 2.5f

/** Kaat kinaari se itna andar, taaki kinaare par farsh na dikhe. */
private const val INSET = 0.004f

private const val CORNERS = 4
private const val BLUR_SPAN = 3
private const val FULL_TURN_DEGREES = 360f
private const val HALF_TURN_DEGREES = 180f
private const val PARALLEL = 1e-6f
private const val HALF_PIXEL = 0.5f
private const val CENTRE = 0.5f

/**
 * Is chhoti tasveer me kaagaz ke chaar kone (x0, y0 ... x3, y3: upar-baayan, upar-daayan, neeche-daayan,
 * neeche-baayan), ya null jab kaatna nahi: saaf kaagaz nahi mila, ya photo pehle se sirf kaagaz hai.
 */
internal fun findPaper(argb: IntArray, width: Int, height: Int): FloatArray? {
    val photo = CropPhoto(argb, width, height)
    val best = photo.bestSheet(photo.houghLines() + photo.borders()) ?: return null
    val fitted = best.map { line -> if (line.border) line else photo.refit(line) ?: line }
    return cornersOf(fitted, width, height)
        ?.takeIf { looksLikeSheet(it, width, height) }
        ?.let { inset(it, minOf(width, height) * INSET) }
}

/** Seedhe page ki chaudai aur oonchai: aamne-saamne ki kinaariyon me lambi wali. */
internal fun paperSize(corners: FloatArray): IntArray {
    fun side(a: Int, b: Int) = hypot(corners[b * 2] - corners[a * 2], corners[b * 2 + 1] - corners[a * 2 + 1])
    val width = maxOf(side(0, 1), side(3, 2)).roundToInt().coerceAtLeast(1)
    val height = maxOf(side(0, 3), side(1, 2)).roundToInt().coerceAtLeast(1)
    return intArrayOf(width, height)
}

private class Spot(val x: Float, val y: Float)

/** Lakeer jahan n·p = rho, n = (cos, sin) uske ujle hisse ki taraf, kaagaz ke andar. */
private class PaperLine(
    val cos: Float,
    val sin: Float,
    val rho: Float,
    val angle: Float,
    val border: Boolean,
    /** Lakeer ke saath kinaari ka jod, t = -reach se. */
    val support: FloatArray,
)

private class Shape(val score: Float, val four: List<PaperLine>, val corners: List<Spot>)

/** Do dishaon ke beech chhota kon, degree me. */
private fun turn(a: Float, b: Float): Float {
    val difference = abs(a - b) % FULL_TURN_DEGREES
    return if (difference > HALF_TURN_DEGREES) FULL_TURN_DEGREES - difference else difference
}

private fun degrees(radians: Double): Float = ((radians * HALF_TURN_DEGREES / PI).toFloat() + FULL_TURN_DEGREES) %
    FULL_TURN_DEGREES

private fun meet(a: PaperLine, b: PaperLine): Spot? {
    val det = a.cos * b.sin - a.sin * b.cos
    if (abs(det) < PARALLEL) return null
    return Spot((a.rho * b.sin - b.rho * a.sin) / det, (a.cos * b.rho - b.cos * a.rho) / det)
}

/** Kaagaz ki agli kinaari lagbhag chauthai chakkar aage mudti hai. */
private fun turnsRight(a: PaperLine, b: PaperLine): Boolean {
    val step = (b.angle - a.angle + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
    return step in MIN_TURN..MAX_TURN
}

/** Ek photo ki kinaariyan aur syahi, ek baar nikal kar. */
private class CropPhoto(argb: IntArray, val width: Int, val height: Int) {
    private val size = width * height
    private val reach = ceil(hypot(width.toFloat(), height.toFloat())).toInt()

    /** Ujalapan, halka dhundhla. */
    val gray: FloatArray

    /** Syahi ke pixel x, y lagatar: ujle kaagaz par gehre nishaan. */
    val ink: IntArray
    val strength = FloatArray(size)

    /** Ujalapan kis disha me badhta hai, degree me. */
    val facing = FloatArray(size)
    val limit: Float

    init {
        val raw = FloatArray(size) { i ->
            val pixel = argb[i]
            luma((pixel shr RED) and BYTE_MASK, (pixel shr GREEN) and BYTE_MASK, pixel and BYTE_MASK).toFloat()
        }
        ink = inkOf(raw)
        // Do halke dhundhlepan: daane aur JPEG ke khaane mit jaate hain, kaagaz ki kinaari rehti hai.
        gray = blur(blur(raw))
        sobel()
        val sorted = strength.copyOf().apply { sort() }
        limit = maxOf(MIN_EDGE, sorted[(sorted.size * EDGE_PART).toInt()])
    }

    private fun sobel() {
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val at = y * width + x
                val gx = gray[at - width + 1] + 2 * gray[at + 1] + gray[at + width + 1] -
                    gray[at - width - 1] - 2 * gray[at - 1] - gray[at + width - 1]
                val gy = gray[at + width - 1] + 2 * gray[at + width] + gray[at + width + 1] -
                    gray[at - width - 1] - 2 * gray[at - width] - gray[at - width + 1]
                strength[at] = hypot(gx, gy)
                facing[at] = degrees(atan2(gy.toDouble(), gx.toDouble()))
            }
        }
    }

    private fun blur(values: FloatArray): FloatArray {
        val across = FloatArray(size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val at = y * width + x
                val left = values[if (x > 0) at - 1 else at]
                val right = values[if (x < width - 1) at + 1 else at]
                across[at] = (left + values[at] + right) / BLUR_SPAN
            }
        }
        return FloatArray(size) { at ->
            val y = at / width
            val up = across[if (y > 0) at - width else at]
            val down = across[if (y < height - 1) at + width else at]
            (up + across[at] + down) / BLUR_SPAN
        }
    }

    /**
     * Kaagaz par likha: apne paas ke sabse ujle pixel se kaafi gehre pixel, aise hisse me jo kul mila kar kaagaz
     * jitna ujla ho. Granite ke chamakte daane nahi ginte.
     */
    private fun inkOf(raw: FloatArray): IntArray {
        val paper = paperGray(raw)
        val brightest = FloatArray(size)
        val mean = FloatArray(size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val from = maxOf(0, x - INK_REACH)
                val to = minOf(width - 1, x + INK_REACH)
                var best = 0f
                var sum = 0f
                for (k in from..to) {
                    best = maxOf(best, raw[y * width + k])
                    sum += raw[y * width + k]
                }
                brightest[y * width + x] = best
                mean[y * width + x] = sum / (to - from + 1)
            }
        }
        val points = ArrayList<Int>()
        val every = (size / (MAX_INK_POINTS * CORNERS)).coerceAtLeast(1)
        for (at in 0 until size step every) {
            val x = at % width
            val y = at / width
            val from = maxOf(0, y - INK_REACH)
            val to = minOf(height - 1, y + INK_REACH)
            var ground = 0f
            var sum = 0f
            for (k in from..to) {
                ground = maxOf(ground, brightest[k * width + x])
                sum += mean[k * width + x]
            }
            if (sum / (to - from + 1) >= paper * INK_GROUND && raw[at] < ground * INK_SHARE) {
                points += x
                points += y
            }
            if (points.size >= MAX_INK_POINTS * 2) break
        }
        return points.toIntArray()
    }

    /** Kaagaz kitna ujla: photo ke beech ka ujla hissa, jahan kaagaz lagbhag hamesha hota hai. */
    private fun paperGray(raw: FloatArray): Float {
        val values = ArrayList<Float>()
        for (y in (height * (CENTRE - MIDDLE)).toInt() until (height * (CENTRE + MIDDLE)).toInt()) {
            for (x in (width * (CENTRE - MIDDLE)).toInt() until (width * (CENTRE + MIDDLE)).toInt()) {
                values += raw[y * width + x]
            }
        }
        values.sort()
        return values.getOrNull((values.size * BRIGHT_PART).toInt()) ?: BYTE_MASK.toFloat()
    }

    /** Tez seedhi lakeerein; har kinaari ka pixel sirf apni disha wali lakeeron ko vote deta hai. */
    fun houghLines(): List<PaperLine> {
        val span = 2 * reach + 1
        val votes = FloatArray(ANGLES * span)
        val cos = FloatArray(ANGLES) { cos(it * PI / HALF_TURN_DEGREES).toFloat() }
        val sin = FloatArray(ANGLES) { sin(it * PI / HALF_TURN_DEGREES).toFloat() }
        for (at in 0 until size) {
            if (strength[at] < limit) continue
            val x = at % width
            val y = at / width
            val centre = facing[at].roundToInt()
            for (d in -VOTE_SPREAD..VOTE_SPREAD) {
                val a = (centre + d + ANGLES) % ANGLES
                votes[a * span + (x * cos[a] + y * sin[a]).roundToInt() + reach] += 1f
            }
        }
        val minVotes = minOf(width, height) * MIN_LINE
        val peaks = ArrayList<Triple<Int, Int, Float>>()
        for (index in votes.indices) {
            if (votes[index] >= minVotes) peaks += Triple(index / span, index % span - reach, votes[index])
        }
        peaks.sortByDescending { it.third }
        val apart = maxOf(width, height) * APART_DISTANCE
        val chosen = ArrayList<Triple<Int, Int, Float>>()
        for (peak in peaks) {
            val close = chosen.any {
                turn(it.first.toFloat(), peak.first.toFloat()) < APART_ANGLE && abs(it.second - peak.second) < apart
            }
            if (close) continue
            chosen += peak
            if (chosen.size >= MAX_LINES) break
        }
        return chosen.map { line(it.first.toFloat(), it.second.toFloat(), border = false) }
    }

    /** Photo ke chaaron kinare, andar ki taraf munh kiye. */
    fun borders(): List<PaperLine> = listOf(
        line(0f, 0f, border = true),
        line(HALF_TURN_DEGREES / 2, 0f, border = true),
        line(HALF_TURN_DEGREES, -(width - 1f), border = true),
        line(HALF_TURN_DEGREES + HALF_TURN_DEGREES / 2, -(height - 1f), border = true),
    )

    /** Lakeer aur uske saath kinaari ka hisaab ek baar: phir uska koi bhi tukda turant gina ja sakta hai. */
    private fun line(angle: Float, rho: Float, border: Boolean): PaperLine {
        val cos = cos(angle * PI / HALF_TURN_DEGREES).toFloat()
        val sin = sin(angle * PI / HALF_TURN_DEGREES).toFloat()
        val support = FloatArray(2 * reach + 2)
        for (t in -reach..reach) {
            val x = rho * cos - t * sin
            val y = rho * sin + t * cos
            val inPhoto = x >= -HALF_PIXEL && y >= -HALF_PIXEL && x <= width - HALF_PIXEL && y <= height - HALF_PIXEL
            val found = when {
                !inPhoto -> 0f
                border -> BORDER_SUPPORT
                edgeNear(x, y, cos, sin, angle) >= 0 -> 1f
                else -> 0f
            }
            support[t + reach + 1] = support[t + reach] + found
        }
        return PaperLine(cos, sin, rho, angle, border, support)
    }

    /** Lakeer ke aar-paar NEAR pixel tak sabse tez milti kinaari ka pixel, ya -1. */
    private fun edgeNear(x: Float, y: Float, cos: Float, sin: Float, angle: Float): Int {
        var best = -1
        var bestStrength = 0f
        for (k in -NEAR..NEAR) {
            val px = (x + k * cos).roundToInt()
            val py = (y + k * sin).roundToInt()
            if (px !in 0 until width || py !in 0 until height) continue
            val at = py * width + px
            val value = strength[at]
            if (value >= limit && value > bestStrength && turn(facing[at], angle) <= FACING) {
                best = at
                bestStrength = value
            }
        }
        return best
    }

    private fun supportBetween(line: PaperLine, from: Spot, to: Spot): Float {
        val last = line.support.size - 1
        fun index(spot: Spot) = ((-spot.x * line.sin + spot.y * line.cos).roundToInt() + reach + 1).coerceIn(0, last)
        return abs(line.support[index(to)] - line.support[index(from)])
    }

    /** Kinaari ke saath andar thoda ujla aur bahar kuch door gehra ho to true. */
    private fun paperInside(line: PaperLine, p: Spot, q: Spot): Boolean {
        val short = minOf(width, height).toFloat()
        val gaps = ArrayList<Float>()
        for (k in 1 until SIDE_SAMPLES) {
            val x = p.x + (q.x - p.x) * k / SIDE_SAMPLES
            val y = p.y + (q.y - p.y) * k / SIDE_SAMPLES
            val near = grayAt(x + line.cos * short * LOOK_IN, y + line.sin * short * LOOK_IN)
            val far = grayAt(x - line.cos * short * LOOK_OUT, y - line.sin * short * LOOK_OUT)
            if (near != null && far != null) gaps += near - far
        }
        // Photo se bahar jaati kinaari theek hai: bahar milane ko kuch nahi.
        if (gaps.size < SIDE_SAMPLES / ENOUGH_SAMPLES) return true
        gaps.sort()
        return gaps[gaps.size / 2] >= MIN_CONTRAST
    }

    private fun grayAt(x: Float, y: Float): Float? {
        val px = x.roundToInt()
        val py = y.roundToInt()
        return if (px in 0 until width && py in 0 until height) gray[py * width + px] else null
    }

    /** Har chaar lakeerein jo gol ghoom kar dabba banayein; sabse zyada asli ghere wala dabba jeetta hai. */
    fun bestSheet(lines: List<PaperLine>): List<PaperLine>? {
        val sorted = lines.sortedBy { it.angle }
        val n = sorted.size
        val shapes = ArrayList<Shape>()
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (!turnsRight(sorted[i], sorted[j])) continue
                for (k in j + 1 until n) {
                    if (!turnsRight(sorted[j], sorted[k])) continue
                    for (l in k + 1 until n) {
                        shapeOf(listOf(sorted[i], sorted[j], sorted[k], sorted[l]))?.let(shapes::add)
                    }
                }
            }
        }
        shapes.sortByDescending { it.score }
        val checkInk = ink.size / 2 >= MIN_INK_POINTS
        return shapes.take(MAX_TRIES).firstOrNull { !checkInk || inkInside(it.corners) >= MIN_INK_INSIDE }?.four
    }

    /** Chaar lakeeron ke kone, agar woh kaagaz jaisa dabba banati hain. */
    private fun cornersIfSheet(four: List<PaperLine>): List<Spot>? {
        val turnsRound = turnsRight(four[2], four[3]) && turnsRight(four[3], four[0])
        val parallel = turn(four[0].angle, four[2].angle) >= HALF_TURN_DEGREES - MAX_SKEW &&
            turn(four[1].angle, four[3].angle) >= HALF_TURN_DEGREES - MAX_SKEW
        if (!turnsRound || !parallel || four.all { it.border }) return null
        val outside = maxOf(width, height) * OUTSIDE
        val points = four.mapIndexed { at, line -> meet(line, four[(at + 1) % CORNERS]) }.filterNotNull().filter {
            it.x >= -outside && it.y >= -outside && it.x <= width + outside && it.y <= height + outside
        }
        return points.takeIf { it.size == CORNERS }
    }

    private fun shapeOf(four: List<PaperLine>): Shape? {
        val points = cornersIfSheet(four) ?: return null
        var area = 0f
        var score = 0f
        var real = 0f
        var outline = 0f
        for (at in 0 until CORNERS) {
            // Kinaari `at` apne pichhle kone se agle kone tak jaati hai.
            val p = points[(at + CORNERS - 1) % CORNERS]
            val q = points[at]
            area += p.x * q.y - q.x * p.y
            outline += hypot(q.x - p.x, q.y - p.y)
            val support = supportBetween(four[at], p, q)
            when {
                four[at].border -> score += support
                paperInside(four[at], p, q) -> {
                    score += support
                    real += support
                }
                else -> score += support * WEAK_SIDE
            }
        }
        area = abs(area) / 2
        val sheet = area >= width * height * MIN_PAPER_SHARE && real >= outline * MIN_REAL
        return if (sheet) Shape(score + AREA_BONUS * sqrt(area), four, points) else null
    }

    /** Dabbe ke andar syahi ka hissa. */
    private fun inkInside(corners: List<Spot>): Float {
        var inside = 0
        for (k in ink.indices step 2) {
            var positive = 0
            var negative = 0
            for (at in 0 until CORNERS) {
                val p = corners[at]
                val q = corners[(at + 1) % CORNERS]
                val cross = (q.x - p.x) * (ink[k + 1] - p.y) - (q.y - p.y) * (ink[k] - p.x)
                if (cross > 0) {
                    positive++
                } else if (cross < 0) {
                    negative++
                }
            }
            if (positive == 0 || negative == 0) inside++
        }
        return inside.toFloat() / (ink.size / 2).coerceAtLeast(1)
    }

    /** Lakeer ko uske kinaari ke pixel par theek baithata hai, door wale (ungli, mudi kinaari) chhod kar. */
    fun refit(line: PaperLine): PaperLine? {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        for (t in -reach..reach) {
            val x = line.rho * line.cos - t * line.sin
            val y = line.rho * line.sin + t * line.cos
            val at = edgeNear(x, y, line.cos, line.sin, line.angle)
            if (at < 0) continue
            xs += (at % width).toFloat()
            ys += (at / width).toFloat()
        }
        var keep = xs.indices.toList()
        var fitted: PaperLine? = null
        repeat(FIT_ROUNDS) {
            if (keep.size < MIN_FIT_POINTS) return fitted
            val next = fitOnce(line, xs, ys, keep)
            fitted = next
            val misses = keep.map { abs(xs[it] * next.cos + ys[it] * next.sin - next.rho) }
            val allowed = maxOf(1f, misses.sorted()[misses.size / 2] * FIT_SPREAD)
            keep = keep.filterIndexed { at, _ -> misses[at] <= allowed }
        }
        return fitted
    }

    /** Sabse kam bikhraav wali disha me lakeer, purani lakeer ki taraf munh kiye. */
    private fun fitOnce(line: PaperLine, xs: List<Float>, ys: List<Float>, keep: List<Int>): PaperLine {
        val mx = keep.sumOf { xs[it].toDouble() }.toFloat() / keep.size
        val my = keep.sumOf { ys[it].toDouble() }.toFloat() / keep.size
        var sxx = 0f
        var syy = 0f
        var sxy = 0f
        for (index in keep) {
            val dx = xs[index] - mx
            val dy = ys[index] - my
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        val theta = 0.5 * atan2(2.0 * sxy, (sxx - syy).toDouble()) + PI / 2
        val flip = if (cos(theta) * line.cos + sin(theta) * line.sin < 0) -1f else 1f
        val cos = cos(theta).toFloat() * flip
        val sin = sin(theta).toFloat() * flip
        val angle = degrees(atan2(sin.toDouble(), cos.toDouble()))
        return PaperLine(cos, sin, mx * cos + my * sin, angle, false, line.support)
    }
}

/** Kone padhne ke kram me: photo ke upar-baayein kone ke sabse paas wala pehle, phir ghadi ki disha me. */
private fun cornersOf(four: List<PaperLine>, width: Int, height: Int): FloatArray? {
    val points = ArrayList<Spot>()
    for (at in 0 until CORNERS) {
        val point = meet(four[(at + CORNERS - 1) % CORNERS], four[at]) ?: return null
        points += Spot(point.x.coerceIn(0f, width.toFloat()), point.y.coerceIn(0f, height.toFloat()))
    }
    var area = 0f
    for (at in 0 until CORNERS) {
        val next = points[(at + 1) % CORNERS]
        area += points[at].x * next.y - next.x * points[at].y
    }
    // y neeche badhta hai, isliye screen par ghadi ki disha me ghoomne se yahan area positive hai.
    if (area < 0) points.reverse()
    val first = points.indices.minBy { points[it].x + points[it].y }
    val corners = FloatArray(CORNERS * 2)
    for (k in 0 until CORNERS) {
        val spot = points[(first + k) % CORNERS]
        corners[k * 2] = spot.x
        corners[k * 2 + 1] = spot.y
    }
    return corners
}

private fun looksLikeSheet(c: FloatArray, width: Int, height: Int): Boolean {
    val near = minOf(width, height) * WHOLE_PHOTO
    val photo = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
    if (photo.indices.all { abs(photo[it] - c[it]) <= near }) return false
    var area = 0f
    for (k in 0 until CORNERS) {
        val next = (k + 1) % CORNERS
        area += c[k * 2] * c[next * 2 + 1] - c[next * 2] * c[k * 2 + 1]
    }
    return area / 2 >= width * height * MIN_PAPER_SHARE
}

/** Har kone ko beech ki taraf [by] pixel. */
private fun inset(c: FloatArray, by: Float): FloatArray {
    val cx = (c[0] + c[2] + c[4] + c[6]) / CORNERS
    val cy = (c[1] + c[3] + c[5] + c[7]) / CORNERS
    return FloatArray(c.size) { index ->
        val centre = if (index % 2 == 1) cy else cx
        c[index] + if (c[index] > centre) -by else by
    }
}

private const val RED = 16
private const val GREEN = 8
private const val BYTE_MASK = 0xFF
