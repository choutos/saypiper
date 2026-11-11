package eu.wanderclan.wordquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.wanderclan.wordquest.ui.WordQuestRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WordQuestApplication).container
        setContent {
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(container) { container.speech.prepare(); ready = true }
            if (ready) WordQuestRoot(container)
        }
    }
    override fun onStop() {
        (application as WordQuestApplication).container.speech.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) (application as WordQuestApplication).container.speech.close()
        super.onDestroy()
    }
}
