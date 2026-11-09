package app.kholo.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.theme.LocalBarColors
import app.kholo.reader.ui.theme.Spacing

private val LOGO_SIZE = 160.dp
private val RING_SIZE = 22.dp
private val RING_STROKE = 2.dp
private val RING_GAP = 32.dp
private val SHORT_LOGO_SIZE = 96.dp
private val SHORT_RING_GAP = 12.dp

/** App khulte hi coffee rang ka chhota page. Android ki launch screen bhi isi rang ki hai, isliye flash nahi aata. */
@Composable
fun IntroPage() {
    val bar = LocalBarColors.current
    // Landscape me upar-neeche jagah kam hoti hai.
    val short = rememberWindowInfo().isShortHeight
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bar.container)
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Rangeen logo, launcher icon jaisa. Coffee bar par cream page saaf dikhta hai.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(if (short) SHORT_LOGO_SIZE else LOGO_SIZE),
        )
        Text(
            text = stringResource(R.string.app_name),
            color = bar.content,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = stringResource(R.string.intro_tagline),
            color = bar.quiet,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (short) SHORT_RING_GAP else RING_GAP))
        CircularProgressIndicator(
            modifier = Modifier.size(RING_SIZE),
            color = bar.content,
            strokeWidth = RING_STROKE,
            trackColor = Color.Transparent,
        )
    }
}
