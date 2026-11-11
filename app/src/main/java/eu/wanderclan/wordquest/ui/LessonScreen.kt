package eu.wanderclan.wordquest.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.wanderclan.wordquest.audio.SpeechResult
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.game.Answer
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Environment
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonSession
import eu.wanderclan.wordquest.game.Progression
import eu.wanderclan.wordquest.game.Prompt
import eu.wanderclan.wordquest.game.Scoring
import eu.wanderclan.wordquest.game.TilesExercise
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LessonViewModel(
    private val container: AppContainer,
    private val profileId: Long,
    request: LessonRequest,
    private val environment: Environment,
) : ViewModel() {
    private val _session = MutableStateFlow(
        LessonSession(container.newSessionId(), container.generator.generate(request, environment, container.nextSeed())),
    )
    val session: StateFlow<LessonSession> = _session.asStateFlow()
    private val _finished = MutableStateFlow<Finished?>(null)
    val finished: StateFlow<Finished?> = _finished.asStateFlow()
    private var speaking: Job? = null

    fun answer(answer: Answer) = _session.update { if (it.isAnswered || it.isFinished) it else it.answer(answer) }

    fun say(wordId: String? = _session.value.current?.wordId) {
        if (wordId == null) return
        if (_session.value.speechUnavailable || !container.speech.isAvailable || speaking?.isActive == true) return
        val home = container.catalog.word(wordId) ?: return
        val position = _session.value.position
        speaking = viewModelScope.launch {
            if (container.speech.say(home.word, home.gradePack.band) == SpeechResult.FAILED) {
                _session.update { if (it.position == position) it.withoutSpeech(container.generator, environment) else it }
            }
        }
    }

    fun next() {
        if (!_session.value.isAnswered) return
        speaking?.cancel()
        container.speech.stop()
        val session = _session.value.next()
        _session.value = session
        if (session.isFinished) viewModelScope.launch {
            val result = session.result()
            // A crown is earned when this lesson's star, at least one, completes its unit: compare before saving.
            val crownedUnit = (result.request as? LessonRequest.UnitLesson)?.let { request ->
                val unit = container.catalog.unit(request.unitId)?.first ?: return@let null
                val before = container.progress.bestStars(profileId).first()
                val after = before + ((request.unitId to request.number) to result.stars)
                unit.takeIf { !Progression.hasCrown(it, before) && Progression.hasCrown(it, after) }
            }
            container.progress.save(profileId, result)
            _finished.value = Finished(result.stars, Scoring.points(result), crownedUnit?.id, result.request is LessonRequest.Practice)
        }
    }

    override fun onCleared() {
        speaking?.cancel()
        container.speech.stop()
    }
}

/** What the result screen shows: stars, points earned, the unit whose crown was just earned, and whether it was practice. */
data class Finished(val stars: Int, val points: Int, val crownedUnitId: String?, val practice: Boolean)

@Composable
fun LessonScreen(
    container: AppContainer,
    profileId: Long,
    request: LessonRequest,
    onClose: () -> Unit,
    onFinished: (Finished) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LessonViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val accessibility = context.getSystemService(AccessibilityManager::class.java)
            LessonViewModel(container, profileId, request, Environment(container.speech.isAvailable,
                accessibility?.isTouchExplorationEnabled != true))
        }
    })
    val session by viewModel.session.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val available by container.speech.availability.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { finished?.let(onFinished) }
    val exercise = session.current
    val home = exercise?.let { container.catalog.word(it.wordId) }
    val band = home?.gradePack?.band ?: Band.B
    LaunchedEffect(session.position) {
        val listening = (exercise is ChoiceExercise && exercise.prompt == Prompt.Listening) ||
            (exercise is TilesExercise && exercise.prompt == Prompt.Listening)
        if (band == Band.A || listening) viewModel.say()
    }
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(64.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.lesson_close)) }
                LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.weight(1f).height(12.dp))
            }
        },
    ) { padding ->
        if (exercise == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                if (session.lesson.exercises.isEmpty()) {
                    Text(stringResource(R.string.lesson_empty))
                    Button(onClick = onClose) { Text(stringResource(R.string.lesson_back)) }
                }
            }
        } else {
            key(session.position) {
                ExerciseQuestion(exercise, session.response, band, available && !session.speechUnavailable,
                    { viewModel.say(it) }, viewModel::answer, viewModel::next, requireNotNull(home).word.text, Modifier.padding(padding))
            }
        }
    }
}
