package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.game.Answer
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Exercise
import eu.wanderclan.wordquest.game.ExerciseType
import eu.wanderclan.wordquest.game.Option
import eu.wanderclan.wordquest.game.PairsExercise
import eu.wanderclan.wordquest.game.Prompt
import eu.wanderclan.wordquest.game.TilesExercise
import eu.wanderclan.wordquest.game.TypingExercise
import kotlinx.coroutines.flow.collectLatest

/** A given word may be heard immediately; an answer is heard only with feedback. */
private val Exercise.wordIsPrompt: Boolean get() = when (this) {
    is ChoiceExercise -> type == ExerciseType.WORD_TO_PICTURE || prompt == Prompt.Listening
    is TilesExercise -> prompt == Prompt.Listening
    is TypingExercise -> prompt == Prompt.Listening
    is PairsExercise -> false
}

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
    wordText: String,
    modifier: Modifier = Modifier,
) {
    val say by rememberUpdatedState(onSay)
    val answered = response != null
    val scroll = rememberScrollState()
    // Feedback can be taller than the remaining space at large fonts. Keep its final control
    // visible, including when layout changes after the answer (for example, decoded pictures).
    LaunchedEffect(answered) {
        if (answered) snapshotFlow { scroll.maxValue }.collectLatest { scroll.animateScrollTo(it) }
    }
    // Pairs has several words and no single spoken prompt or answer.
    val canSayWord = speechAvailable && exercise !is PairsExercise
    LaunchedEffect(exercise, answered) {
        if (canSayWord && (if (exercise.wordIsPrompt) !answered else answered)) say(exercise.wordId)
    }
    Column(modifier.fillMaxSize().screenWidth().then(if (exercise is TypingExercise) Modifier.imePadding() else Modifier).verticalScroll(scroll)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canSayWord && (exercise.wordIsPrompt || answered) &&
                !(exercise is TypingExercise && exercise.prompt == Prompt.Listening)) {
                FilledTonalIconButton(onClick = { onSay(exercise.wordId) }, modifier = Modifier.size(64.dp).testTag("speaker")) {
                    Icon(painterResource(R.drawable.ic_speaker), stringResource(R.string.speech_say))
                }
            }
            when (exercise) {
                is ChoiceExercise -> ChoiceQuestion(exercise, response as? Answer.Choice, band, onAnswer) { onSay(exercise.wordId) }
                is PairsExercise -> key(exercise.wordId) { PairsQuestion(exercise, response as? Answer.Pairs, onAnswer) }
                is TilesExercise -> key(exercise.wordId) { TilesQuestion(exercise, response as? Answer.Tiles, onAnswer) { onSay(exercise.wordId) } }
                is TypingExercise -> key(exercise.wordId) { TypingQuestion(exercise, response as? Answer.Typed, onAnswer) { onSay(exercise.wordId) } }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (response != null) {
            val correct = exercise.incorrectWords(response).isEmpty()
            val answer = when (exercise) {
                is ChoiceExercise -> wordText
                is PairsExercise -> null
                is TilesExercise -> exercise.text
                is TypingExercise -> exercise.text
            }
            Feedback(correct, answer, onContinue)
        }
    }
}

@Composable
private fun QuestionPrompt(prompt: Prompt, onReplay: () -> Unit) {
    when (prompt) {
        is Prompt.Picture -> Picture(prompt.emoji, stringResource(R.string.lesson_question_picture), Modifier.size(160.dp))
        is Prompt.Text -> Text(prompt.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("prompt"))
        is Prompt.Gap -> Text(prompt.example.before + "____" + prompt.example.after,
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("prompt"))
        Prompt.Listening -> FilledTonalIconButton(onClick = onReplay, modifier = Modifier.size(96.dp).testTag("listen-replay")) {
            Icon(painterResource(R.drawable.ic_speaker), stringResource(R.string.lesson_listen), Modifier.size(64.dp))
        }
    }
}

@Composable
private fun ChoiceQuestion(exercise: ChoiceExercise, response: Answer.Choice?, band: Band, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    QuestionPrompt(exercise.prompt, onReplay)
    exercise.options.forEachIndexed { index, option ->
        val correct = response != null && index == exercise.answerIndex
        val wrong = response?.index == index && !correct
        val colours = MaterialTheme.colorScheme
        val colour = when { correct -> colours.tertiaryContainer; wrong -> colours.errorContainer; else -> colours.surface }
        OutlinedButton(
            onClick = { onAnswer(Answer.Choice(index)) }, enabled = response == null,
            colors = ButtonDefaults.outlinedButtonColors(disabledContainerColor = colour, disabledContentColor = colours.onSurface),
            border = BorderStroke(if (correct || wrong) 3.dp else 1.dp, when { correct -> colours.tertiary; wrong -> colours.error; else -> colours.outline }),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (option is Option.Picture) 104.dp else if (band == Band.A) 72.dp else 64.dp)
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
    val definitions = exercise.meanings.first() is Option.Text
    val shape = MaterialTheme.shapes.medium
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val widestWord = remember(exercise.words, exercise.meanings, textMeasurer, labelStyle) {
        (exercise.words.map { it.text } + exercise.meanings.filterIsInstance<Option.Text>().map { it.text })
            .flatMap { it.split(Regex("\\s+")) }
            .maxOf { textMeasurer.measure(it, style = labelStyle, softWrap = false).size.width }
    }
    Text(stringResource(if (definitions) R.string.lesson_definition_pairs else R.string.lesson_pairs), style = MaterialTheme.typography.titleLarge)
    exercise.words.forEachIndexed { index, word ->
        val meaning = exercise.meanings[index]
        val correct = response?.wordIds?.get(index)?.let { it == meaning.wordId }
        val colours = MaterialTheme.colorScheme
        val colour = when (correct) { true -> colours.tertiaryContainer; false -> colours.errorContainer; null -> colours.surface }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Reserve both buttons' padding and the definition feedback icon before measuring.
            // Use the same layout before and after Check so feedback cannot squeeze a word.
            val reservedWidth = if (definitions) 52.dp else 24.dp
            val wordWidth = with(LocalDensity.current) { ((maxWidth - 12.dp) / 2 - reservedWidth).toPx().toInt() }
            val stacked = widestWord > wordWidth
            val choices: @Composable (Modifier) -> Unit = { choiceModifier ->
                OutlinedButton(onClick = { selected = word.wordId },
                    enabled = response == null && word.wordId !in matches.values,
                    shape = shape,
                    contentPadding = PaddingValues(12.dp),
                    border = BorderStroke(if (selected == word.wordId) 3.dp else 1.dp, MaterialTheme.colorScheme.primary),
                    modifier = choiceModifier.heightIn(min = 64.dp).testTag("pair-word-${word.wordId}")) {
                    Text(word.text)
                }
                OutlinedButton(onClick = {
                    selected?.let { matches = matches + (meaning.wordId to it); selected = null }
                }, enabled = response == null && selected != null && meaning.wordId !in matches,
                    shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(disabledContainerColor = colour, disabledContentColor = colours.onSurface),
                    border = BorderStroke(if (correct == null) 1.dp else 3.dp,
                        when (correct) { true -> colours.tertiary; false -> colours.error; null -> colours.outline }),
                    contentPadding = PaddingValues(12.dp),
                    modifier = choiceModifier.heightIn(min = 64.dp)
                        .testTag("pair-${if (definitions) "definition" else "picture"}-${meaning.wordId}")) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(end = if (definitions && correct != null) 28.dp else 0.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (meaning is Option.Picture) Picture(meaning.emoji, stringResource(R.string.lesson_picture_option, index + 1), Modifier.size(48.dp))
                            if (meaning is Option.Text) Text(meaning.text)
                            val paired = response?.wordIds?.get(index) ?: matches[meaning.wordId]
                            paired?.let { id ->
                                Text(exercise.words.first { it.wordId == id }.text)
                                if (correct == false) Text(
                                    stringResource(R.string.lesson_pair_answer, exercise.words.first { it.wordId == meaning.wordId }.text),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("pair-answer-${meaning.wordId}"),
                                )
                            }
                        }
                        if (correct != null) Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close,
                            stringResource(if (correct) R.string.lesson_pair_right else R.string.lesson_pair_wrong),
                            Modifier.align(Alignment.TopEnd).size(24.dp).testTag("pair-feedback-${meaning.wordId}"))
                    }
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { choices(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { choices(Modifier.weight(1f)) }
        }
    }
    AnswerControls(matches.size == exercise.words.size, response != null,
        { matches = emptyMap(); selected = null },
        { onAnswer(Answer.Pairs(exercise.meanings.map { matches.getValue(it.wordId) })) })
}

@Composable
private fun TypingQuestion(exercise: TypingExercise, response: Answer.Typed?, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = {
        if (response == null && text.isNotBlank()) {
            focus.clearFocus()
            onAnswer(Answer.Typed(text))
        }
    }
    Text(stringResource(R.string.lesson_type), style = MaterialTheme.typography.titleLarge)
    QuestionPrompt(exercise.prompt, onReplay)
    exercise.clue?.let { QuestionPrompt(it, onReplay) }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, readOnly = response != null, singleLine = true,
        label = { Text(stringResource(R.string.lesson_your_answer)) },
        // Password input suppresses keyboard suggestions; the answer remains visibly plain text.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().testTag("typed-answer"),
    )
    AnswerControls(text.isNotBlank(), response != null, { text = "" }, submit)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TilesQuestion(exercise: TilesExercise, response: Answer.Tiles?, onAnswer: (Answer) -> Unit, onReplay: () -> Unit) {
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    Text(stringResource(R.string.lesson_tiles), style = MaterialTheme.typography.titleLarge)
    QuestionPrompt(exercise.prompt, onReplay)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerControls(complete: Boolean, answered: Boolean, reset: () -> Unit, submit: () -> Unit) {
    if (!answered) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = reset, modifier = Modifier.heightIn(min = 56.dp).testTag("reset")) { Text(stringResource(R.string.lesson_reset)) }
        Button(onClick = submit, enabled = complete, modifier = Modifier.heightIn(min = 56.dp).testTag("submit")) {
            Icon(Icons.Filled.Check, null)
            Text(stringResource(R.string.lesson_check))
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, answer: String?, onContinue: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Surface(color = if (correct) colours.tertiaryContainer else colours.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (correct) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.size(32.dp))
                Text(when {
                    correct -> stringResource(R.string.lesson_right)
                    answer == null -> stringResource(R.string.lesson_pairs_wrong)
                    else -> stringResource(R.string.lesson_wrong, answer)
                },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.lesson_continue))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 8.dp))
            }
        }
    }
}
