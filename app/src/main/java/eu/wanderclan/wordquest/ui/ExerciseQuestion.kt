package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.game.Answer
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Exercise
import eu.wanderclan.wordquest.game.Option
import eu.wanderclan.wordquest.game.PairsExercise
import eu.wanderclan.wordquest.game.Prompt
import eu.wanderclan.wordquest.game.TilesExercise

/** The same renderer is used by the lesson and its interaction/screenshot tests. */
@Composable
fun ExerciseQuestion(
    exercise: Exercise,
    response: Answer?,
    band: Band,
    speechAvailable: Boolean,
    onSay: (String) -> Unit,
    onAnswer: (Answer) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (speechAvailable) {
                FilledTonalIconButton(onClick = { onSay(exercise.wordId) }, modifier = Modifier.size(64.dp).testTag("speaker")) {
                    Icon(painterResource(R.drawable.ic_speaker), stringResource(R.string.speech_say))
                }
            }
            when (exercise) {
                is ChoiceExercise -> ChoiceQuestion(exercise, response as? Answer.Choice, band, onAnswer)
                is PairsExercise -> key(exercise.wordId) { PairsQuestion(exercise, response as? Answer.Pairs, onAnswer) }
                is TilesExercise -> key(exercise.wordId) { TilesQuestion(exercise, response as? Answer.Tiles, onAnswer) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (response != null) {
            val correct = exercise.incorrectWords(response).isEmpty()
            val answer = when (exercise) {
                is ChoiceExercise -> (exercise.options[exercise.answerIndex] as? Option.Text)?.text.orEmpty()
                is PairsExercise -> exercise.words.joinToString(", ") { it.text }
                is TilesExercise -> exercise.text
            }
            Feedback(correct, answer, onContinue)
        }
    }
}

@Composable
private fun QuestionPrompt(prompt: Prompt) {
    when (prompt) {
        is Prompt.Picture -> Picture(prompt.emoji, stringResource(R.string.lesson_question_picture), Modifier.size(160.dp))
        is Prompt.Text -> Text(prompt.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("prompt"))
        is Prompt.Gap -> Text(prompt.example.before + "____" + prompt.example.after,
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("prompt"))
        Prompt.Listening -> Icon(painterResource(R.drawable.ic_speaker), stringResource(R.string.lesson_listen), Modifier.size(96.dp))
    }
}

@Composable
private fun ChoiceQuestion(exercise: ChoiceExercise, response: Answer.Choice?, band: Band, onAnswer: (Answer) -> Unit) {
    QuestionPrompt(exercise.prompt)
    exercise.options.forEachIndexed { index, option ->
        val correct = response != null && index == exercise.answerIndex
        val wrong = response?.index == index && !correct
        val colours = MaterialTheme.colorScheme
        val colour = when { correct -> colours.tertiaryContainer; wrong -> colours.errorContainer; else -> colours.surface }
        OutlinedButton(
            onClick = { onAnswer(Answer.Choice(index)) }, enabled = response == null,
            colors = ButtonDefaults.outlinedButtonColors(disabledContainerColor = colour, disabledContentColor = colours.onSurface),
            border = BorderStroke(if (correct || wrong) 3.dp else 1.dp, when { correct -> colours.tertiary; wrong -> colours.error; else -> colours.outline }),
            modifier = Modifier.fillMaxWidth().height(if (option is Option.Picture) 104.dp else if (band == Band.A) 72.dp else 64.dp)
                .testTag("option-$index"),
        ) {
            when (option) {
                is Option.Text -> Text(option.text, style = MaterialTheme.typography.headlineSmall)
                is Option.Picture -> Picture(option.emoji, stringResource(R.string.lesson_picture_option, index + 1), Modifier.size(88.dp))
            }
            if (correct || wrong) Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun PairsQuestion(exercise: PairsExercise, response: Answer.Pairs?, onAnswer: (Answer) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    Text(stringResource(R.string.lesson_pairs), style = MaterialTheme.typography.titleLarge)
    exercise.words.forEachIndexed { index, word ->
        val picture = exercise.pictures[index]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { selected = word.wordId },
                enabled = response == null && word.wordId !in matches.values,
                border = BorderStroke(if (selected == word.wordId) 3.dp else 1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).height(64.dp).testTag("pair-word-${word.wordId}")) {
                Text(word.text)
            }
            OutlinedButton(onClick = {
                selected?.let { matches = matches + (picture.wordId to it); selected = null }
            }, enabled = response == null && selected != null && picture.wordId !in matches,
                modifier = Modifier.weight(1f).height(64.dp).testTag("pair-picture-${picture.wordId}")) {
                Picture(picture.emoji, stringResource(R.string.lesson_picture_option, index + 1), Modifier.size(48.dp))
                val paired = if (response != null) picture.wordId else matches[picture.wordId]
                paired?.let { id ->
                    Text(exercise.words.first { it.wordId == id }.text, Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    AnswerControls(matches.size == exercise.words.size, response != null,
        { matches = emptyMap(); selected = null },
        { onAnswer(Answer.Pairs(exercise.pictures.map { matches.getValue(it.wordId) })) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TilesQuestion(exercise: TilesExercise, response: Answer.Tiles?, onAnswer: (Answer) -> Unit) {
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    Text(stringResource(R.string.lesson_tiles), style = MaterialTheme.typography.titleLarge)
    QuestionPrompt(exercise.prompt)
    Text(selected.map { exercise.tiles[it] }.joinToString(" ").ifEmpty { "____" },
        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("spelling"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        exercise.tiles.forEachIndexed { index, letter ->
            OutlinedButton(onClick = { selected = selected + index }, enabled = response == null && index !in selected,
                contentPadding = PaddingValues(4.dp), modifier = Modifier.size(56.dp).testTag("tile-$index")) { Text(letter.toString()) }
        }
    }
    AnswerControls(selected.size == exercise.tiles.size, response != null, { selected = emptyList() },
        { onAnswer(Answer.Tiles(selected)) })
}

@Composable
private fun AnswerControls(complete: Boolean, answered: Boolean, reset: () -> Unit, submit: () -> Unit) {
    if (!answered) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = reset, modifier = Modifier.heightIn(min = 56.dp).testTag("reset")) { Text(stringResource(R.string.lesson_reset)) }
        Button(onClick = submit, enabled = complete, modifier = Modifier.heightIn(min = 56.dp).testTag("submit")) {
            Icon(Icons.Filled.Check, null)
            Text(stringResource(R.string.lesson_check))
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, answer: String, onContinue: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Surface(color = if (correct) colours.tertiaryContainer else colours.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.size(32.dp))
                Text(if (correct) stringResource(R.string.lesson_right) else stringResource(R.string.lesson_wrong, answer),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text(stringResource(R.string.lesson_continue))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 8.dp))
            }
        }
    }
}
