package eu.wanderclan.wordquest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.data.ProfileEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class ParentChallenge(val left: Int, val right: Int) {
    fun accepts(answer: String): Boolean = answer.trim().toIntOrNull() == left + right
    companion object {
        fun create(previous: ParentChallenge? = null): ParentChallenge {
            var next: ParentChallenge
            do { next = ParentChallenge(Random.nextInt(23, 80), Random.nextInt(23, 80)) }
            while (previous != null && next.left + next.right == previous.left + previous.right)
            return next
        }
    }
}

@Composable
fun ParentGate(onPassed: () -> Unit, onClose: () -> Unit, challenge: ParentChallenge? = null) {
    var question by remember { mutableStateOf(challenge ?: ParentChallenge.create()) }
    var answer by remember { mutableStateOf("") }
    var incorrect by remember { mutableStateOf(false) }
    Text(stringResource(R.string.parent_gate_explanation))
    Text(stringResource(R.string.parent_gate_question, question.left, question.right))
    OutlinedTextField(answer, { answer = it.take(4); incorrect = false },
        label = { Text(stringResource(R.string.parent_gate_answer)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = incorrect,
        modifier = Modifier.fillMaxWidth())
    if (incorrect) Text(stringResource(R.string.parent_gate_wrong))
    Button(onClick = {
        if (question.accepts(answer)) onPassed() else {
            question = ParentChallenge.create(question)
            answer = ""
            incorrect = true
        }
    }) {
        Text(stringResource(R.string.parent_gate_enter))
    }
    TextButton(onClick = onClose) { Text(stringResource(R.string.lesson_back)) }
}

@Composable
fun ParentCornerScreen(container: AppContainer, onClose: () -> Unit) {
    val viewModel: ParentCornerViewModel = viewModel(factory = viewModelFactory { initializer { ParentCornerViewModel(container) } })
    // Deliberately not saved: returning, recreation and leaving the app require another answer.
    var unlocked by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    val profiles by container.profiles.profiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.settings().collectAsStateWithLifecycle(initialValue = null)
    var action by remember { mutableStateOf<Pair<ProfileEntity, ParentAction>?>(null) }
    val working by viewModel.working.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    LaunchedEffect(completed) { action = null }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false; action = null }
    Scaffold(topBar = { ScreenHeader(stringResource(R.string.parent_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).screenWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!unlocked) {
                ParentGate({ unlocked = true }, onClose)
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.parent_close)) }
                Text(stringResource(R.string.parent_sound))
                Text(stringResource(R.string.parent_sound_explanation))
                settings?.let { current ->
                    val soundLabel = stringResource(R.string.parent_sound)
                    Switch(current.soundEnabled, { enabled ->
                        viewModel.setSound(enabled)
                    }, enabled = !working, modifier = Modifier.semantics { contentDescription = soundLabel })
                }
                profiles.forEach { profile ->
                    HorizontalDivider()
                    ProfileIdentity(container, profile)
                    TextButton(enabled = !working, onClick = { action = profile to ParentAction.RENAME }) { Text(stringResource(R.string.parent_rename)) }
                    if (action?.let { it.first.id == profile.id && it.second == ParentAction.RENAME } == true) {
                        var nickname by remember(profile.id) { mutableStateOf(profile.nickname) }
                        Text(stringResource(R.string.parent_rename_title, profile.nickname))
                        OutlinedTextField(nickname, { nickname = it.take(20) },
                            label = { Text(stringResource(R.string.profile_nickname)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), enabled = !working)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !working && nickname.isNotBlank(), onClick = {
                                viewModel.perform(profile.id, ParentAction.RENAME, nickname)
                            }) { Text(stringResource(R.string.parent_confirm)) }
                            TextButton(enabled = !working, onClick = { action = null }) { Text(stringResource(R.string.parent_cancel)) }
                        }
                    }
                    TextButton(enabled = !working, onClick = { action = profile to ParentAction.RESET }) { Text(stringResource(R.string.parent_reset)) }
                    TextButton(enabled = !working, onClick = { action = profile to ParentAction.DELETE }) { Text(stringResource(R.string.parent_delete)) }
                    val grade = profile.gradeId
                    if (grade != null) {
                        Text(stringResource(R.string.parent_open_units, container.catalog.grade(grade)?.label ?: grade))
                        val label = stringResource(R.string.parent_open_units_description, profile.nickname)
                        Switch(profile.openUnitsGradeId == grade, { enabled ->
                            viewModel.openUnits(profile.id, enabled)
                        }, enabled = !working, modifier = Modifier.semantics { contentDescription = label })
                    }
                }
            }
        }
    }
    val pending = action?.takeIf { it.second != ParentAction.RENAME }
    if (unlocked && pending != null) {
        val (profile, kind) = pending
        AlertDialog(onDismissRequest = { if (!working) action = null },
            title = { Text(stringResource(kind.title, profile.nickname)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProfileIdentity(container, profile)
                    Text(stringResource(if (kind == ParentAction.DELETE) R.string.parent_delete_explanation else R.string.parent_reset_explanation))
                }
            },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    viewModel.perform(profile.id, kind, profile.nickname)
                }) { Text(stringResource(R.string.parent_confirm)) }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { action = null }) { Text(stringResource(R.string.parent_cancel)) } },
        )
    }
}

@Composable
private fun ProfileIdentity(container: AppContainer, profile: ProfileEntity) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        container.catalog.avatars.firstOrNull { it.id == profile.avatarId }?.let { avatar ->
            Picture(avatar.emoji, stringResource(R.string.avatar_description, avatar.id), Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(profile.nickname)
            Text(profile.gradeId?.let(container.catalog::grade)?.label ?: stringResource(R.string.profile_class_none))
        }
    }
}

private class ParentCornerViewModel(private val container: AppContainer) : ViewModel() {
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()
    private val _completed = MutableStateFlow(0)
    val completed = _completed.asStateFlow()

    fun setSound(enabled: Boolean) {
        container.speech.stop()
        viewModelScope.launch { container.settings.setSound(enabled) }
    }

    fun openUnits(profileId: Long, enabled: Boolean) {
        viewModelScope.launch { container.profiles.openAllUnits(profileId, enabled) }
    }

    fun perform(profileId: Long, action: ParentAction, nickname: String) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            when (action) {
                ParentAction.RENAME -> container.profiles.rename(profileId, nickname)
                ParentAction.RESET -> container.profiles.resetProgress(profileId)
                ParentAction.DELETE -> container.profiles.delete(profileId)
            }
            _completed.value++
            _working.value = false
        }
    }
}

private enum class ParentAction(val title: Int) {
    RENAME(R.string.parent_rename_title), RESET(R.string.parent_reset_title), DELETE(R.string.parent_delete_title),
}
