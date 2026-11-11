package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keep reading lines comfortable on tablets; let titles grow with the system font. */
fun Modifier.screenWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 840.dp)

@Composable
fun ScreenHeader(title: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    Surface {
        Column(Modifier.screenWidth().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            actions()
        }
    }
}
