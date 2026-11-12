package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.wanderclan.wordquest.BuildConfig
import eu.wanderclan.wordquest.R

/** Credits. Plain text only: the app has no links out (children's app, fully offline). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            ScreenHeader(stringResource(R.string.about_title)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.lesson_back)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).screenWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_accessibility), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_pictures), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_audio), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_licence_notice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
