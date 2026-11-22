package app.kholo.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kholo.reader.R
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.theme.LocalBarColors
import app.kholo.reader.ui.theme.Spacing
import kotlin.math.roundToInt

private val LOGO_SIZE = 160.dp
private val SHORT_LOGO_SIZE = 96.dp
private val DOTS_GAP = 28.dp
private val SHORT_DOTS_GAP = 12.dp
private val DOT_SIZE = 7.dp
private val LOCK_SIZE = 14.dp
private val RISE = 14.dp

/** Logo ki drawing apne dabbe ke itne hisse par khatam hoti hai. Neeche khaali kinara hai, naam yahin se shuru. */
private const val LOGO_ART_BOTTOM = 0.78f

/** Logo ke peeche ki garam roshni logo se itni badi. */
private const val GLOW_SCALE = 1.7f
private const val GLOW_ALPHA = 0.22f
private val Caramel = Color(0xFFE5A060)

/** Poora parda itne ms me khulta hai. Intro 900 ms ka hai, isliye sab kuch aadhe se thoda baad tak dikh jata hai. */
private const val REVEAL_MS = 560
private const val PIECE_MS = 260f
private const val NAME_AT = 60f
private const val TAGLINE_AT = 140f
private const val DOTS_AT = 220f
private const val FOOTER_AT = 300f

private const val DOT_COUNT = 3
private const val DOT_PULSE_MS = 420
private const val DOT_STAGGER = 0.33f
private const val DOT_DIM = 0.3f

/**
 * App khulte hi coffee rang ka chhota page. Android ki launch screen bhi isi rang ki hai.
 * [splashIcon] launch screen ke logo ki asli jagah hai, window ke pixels me. Logo theek wahin aur utna hi bada banta
 * hai aur hilta nahi, isliye launch screen se yeh page aate waqt kuch koodta nahi. Baaki sab uske neeche ek-ek karke
 * halka sa upar aata hai. Purane Android par jagah nahi milti, tab logo screen ke beech me rehta hai.
 */
@Composable
fun IntroPage(splashIcon: IntRect? = null) {
    val bar = LocalBarColors.current
    // Landscape me upar-neeche jagah kam hoti hai.
    val short = rememberWindowInfo().isShortHeight
    val density = LocalDensity.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(REVEAL_MS.toFloat(), tween(REVEAL_MS, easing = LinearEasing)) }
    val risePx = with(density) { RISE.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().background(bar.container)) {
        val logo = splashIcon ?: with(density) {
            val side = (if (short) SHORT_LOGO_SIZE else LOGO_SIZE).roundToPx()
            val left = (constraints.maxWidth - side) / 2
            val top = (constraints.maxHeight - side) / 2
            IntRect(left, top, left + side, top + side)
        }
        val glow = (logo.width * GLOW_SCALE).roundToInt()
        Box(
            Modifier
                .offset { IntOffset(logo.center.x - glow / 2, logo.center.y - glow / 2) }
                .size(with(density) { glow.toDp() })
                .graphicsLayer { alpha = reveal.share(0f) }
                .background(Brush.radialGradient(listOf(Caramel.copy(alpha = GLOW_ALPHA), Color.Transparent))),
        )
        // Rangeen logo, launcher icon jaisa. Shuru se poora dikhta hai, kyunki launch screen par bhi dikh raha tha.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(logo.left, logo.top) }
                .size(with(density) { logo.width.toDp() }, with(density) { logo.height.toDp() }),
        )
        Column(
            modifier = Modifier
                .offset { IntOffset(0, logo.top + (logo.height * LOGO_ART_BOTTOM).roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.rise(reveal, NAME_AT, risePx),
                color = bar.content,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.intro_tagline),
                modifier = Modifier.rise(reveal, TAGLINE_AT, risePx),
                color = bar.quiet,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (short) SHORT_DOTS_GAP else DOTS_GAP))
            LoadingDots(bar.content, Modifier.rise(reveal, DOTS_AT, risePx))
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (short) Spacing.s else Spacing.xl)
                .rise(reveal, FOOTER_AT, risePx),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(LOCK_SIZE),
                tint = bar.quiet,
            )
            Text(
                text = stringResource(R.string.intro_private),
                color = bar.quiet,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** [start] ms ke baad yeh hissa kitna dikhe, 0 se 1. */
private fun Animatable<Float, *>.share(start: Float): Float =
    FastOutSlowInEasing.transform(((value - start) / PIECE_MS).coerceIn(0f, 1f))

/** Halka sa neeche se upar aur dhundhla se saaf. Sirf draw me padha jata hai, isliye har frame par layout nahi hota. */
private fun Modifier.rise(reveal: Animatable<Float, *>, start: Float, risePx: Float): Modifier = graphicsLayer {
    val shown = reveal.share(start)
    alpha = shown
    translationY = (1f - shown) * risePx
}

/** Teen dots baari-baari chamakte hain. Ghoomte spinner se zyada shaant lagte hain. */
@Composable
private fun LoadingDots(color: Color, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DOT_PULSE_MS * DOT_COUNT, easing = LinearEasing), RepeatMode.Restart),
        label = "dots",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(DOT_SIZE)) {
        repeat(DOT_COUNT) { index -> Dot(color, pulse, index * DOT_STAGGER) }
    }
}

@Composable
private fun Dot(color: Color, pulse: State<Float>, phase: Float) {
    Box(
        Modifier
            .size(DOT_SIZE)
            .graphicsLayer {
                // Har dot apni baari par poora chamakta hai, phir dheema ho jata hai.
                val turn = (pulse.value - phase + 1f) % 1f
                val glow = if (turn < DOT_STAGGER) 1f - turn / DOT_STAGGER else 0f
                alpha = DOT_DIM + (1f - DOT_DIM) * glow
                scaleX = 1f + glow * DOT_DIM
                scaleY = scaleX
            }
            .background(color, CircleShape),
    )
}
