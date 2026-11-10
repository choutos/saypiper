package eu.wanderclan.wordquest.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Environment
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonSession
import eu.wanderclan.wordquest.game.Option
import eu.wanderclan.wordquest.game.Prompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LessonViewModel(
    private val container: AppContainer,
    private val profileId: Long,
    request: LessonRequest,
    environment: Environment,
) : ViewModel() {
    private val _session = MutableStateFlow(
        LessonSession(container.newSessionId(), container.generator.generate(request, environment, container.nextSeed())),
    )
    val session: StateFlow<LessonSession> = _session.asStateFlow()

    private val _savedStars = MutableStateFlow<Int?>(null)

    /** Set once the finished lesson is stored; the screen then moves on to the result. */
    val savedStars: StateFlow<Int?> = _savedStars.asStateFlow()

    // Both ignore repeated taps: a second tap may arrive before the screen has changed.
    fun answer(option: Int) = _session.update { if (it.isAnswered || it.isFinished) it else it.answer(option) }

    fun next() {
        if (!_session.value.isAnswered) return
        val session = _session.value.next()
        _session.value = session
        if (session.isFinished) {
            viewModelScope.launch {
                container.progress.save(profileId, session.result())
                _savedStars.value = session.stars
            }
        }
    }
}

@Composable
fun LessonScreen(
    container: AppContainer,
    profileId: Long,
    request: LessonRequest,
    onClose: () -> Unit,
    onFinished: (stars: Int) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LessonViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val accessibility = context.getSystemService(AccessibilityManager::class.java)
                val environment = Environment(
                    speechAvailable = container.speech.isAvailable,
                    // Pictures cannot be perceived with a screen reader on (ADR section 6).
                    picturesPerceivable = accessibility?.isTouchExplorationEnabled != true,
                )
                LessonViewModel(container, profileId, request, environment)
            }
        },
    )
    val session by viewModel.session.collectAsStateWithLifecycle()
    val savedStars by viewModel.savedStars.collectAsStateWithLifecycle()
    LaunchedEffect(savedStars) { savedStars?.let(onFinished) }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.lesson_close)) }
                LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.weight(1f).height(12.dp))
            }
        },
        bottomBar = {
            val correct = session.lastAnswerCorrect
            val exercise = session.current as? ChoiceExercise
            if (correct != null && exercise != null) {
                Feedback(correct, (exercise.options[exercise.answerIndex] as Option.Text).text, viewModel::next)
            }
        },
    ) { padding ->
        val exercise = session.current
        if (session.lesson.exercises.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.lesson_empty), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onClose) { Text(stringResource(R.string.lesson_back)) }
            }
        } else if (exercise is ChoiceExercise) {
            ChoiceQuestion(exercise, session.chosen, viewModel::answer, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ChoiceQuestion(exercise: ChoiceExercise, chosen: Int?, onAnswer: (Int) -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        when (val prompt = exercise.prompt) {
            is Prompt.Picture -> Picture(prompt.emoji, stringResource(R.string.lesson_question_picture), Modifier.size(200.dp))
        }
        Spacer(Modifier.height(8.dp))
        exercise.options.forEachIndexed { index, option ->
            val text = when (option) {
                is Option.Text -> option.text
            }
            val colours = MaterialTheme.colorScheme
            val (border, container) = when {
                chosen == null -> colours.outline to colours.surface
                index == exercise.answerIndex -> colours.tertiary to colours.tertiaryContainer
                index == chosen -> colours.error to colours.errorContainer
                else -> colours.outlineVariant to colours.surface
            }
            OutlinedButton(
                onClick = { onAnswer(index) },
                enabled = chosen == null,
                border = BorderStroke(if (chosen != null && (index == chosen || index == exercise.answerIndex)) 3.dp else 1.dp, border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = container,
                    disabledContainerColor = container,
                    disabledContentColor = colours.onSurface,
                ),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(text, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, answer: String, onContinue: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Surface(color = if (correct) colours.tertiaryContainer else colours.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (correct) stringResource(R.string.lesson_right) else stringResource(R.string.lesson_wrong, answer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = if (correct) colours.tertiary else colours.error),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.lesson_continue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
