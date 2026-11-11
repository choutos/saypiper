package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.wanderclan.wordquest.R

@Composable
fun ResultScreen(stars: Int, onContinue: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val description = stringResource(R.string.result_stars, stars)
            Stars(stars, Modifier.clearAndSetSemantics { contentDescription = description }, size = 72)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.result_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
