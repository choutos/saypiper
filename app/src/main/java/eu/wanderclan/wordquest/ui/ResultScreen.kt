package eu.wanderclan.wordquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.content.VocabUnit

/** [crownedUnit] is the unit whose crown this lesson has just earned: it gets a short celebration. */
@Composable
fun ResultScreen(stars: Int, points: Int, crownedUnit: VocabUnit?, onContinue: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            if (crownedUnit != null) Crown(crownedUnit)
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val description = stringResource(R.string.result_stars, stars)
            Stars(stars, Modifier.clearAndSetSemantics { contentDescription = description }, size = 72)
            Text(
                pluralStringResource(R.plurals.result_points, points, points),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.result_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Crown(unit: VocabUnit) {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Icon(painterResource(R.drawable.ic_crown), null, tint = StarGold, modifier = Modifier.size(120.dp).scale(scale.value).testTag("crown"))
    Text(
        stringResource(R.string.result_crown, unit.title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
