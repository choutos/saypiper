package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.data.ProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    container: AppContainer,
    onNewProfile: () -> Unit,
    onOpen: (ProfileEntity) -> Unit,
    onAbout: () -> Unit,
    onParent: () -> Unit = {},
) {
    val profiles by container.profiles.profiles().collectAsStateWithLifecycle(initialValue = null)
    Scaffold(
        topBar = {
            ScreenHeader(stringResource(R.string.profiles_title)) {
                Row {
                    TextButton(onClick = onParent) { Text(stringResource(R.string.parent_title)) }
                    IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, stringResource(R.string.about)) }
                }
            }
        },
        bottomBar = {
            Button(
                onClick = onNewProfile,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().screenWidth().padding(24.dp).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_new), style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        val list = profiles ?: return@Scaffold
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.headlineSmall)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).screenWidth(), contentPadding = PaddingValues(16.dp)) {
            items(list, key = { it.id }) { profile ->
                Card(onClick = { onOpen(profile) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val avatar = container.catalog.avatars.firstOrNull { it.id == profile.avatarId }
                        if (avatar != null) Picture(avatar.emoji, null, Modifier.size(56.dp))
                        Spacer(Modifier.size(16.dp))
                        Text(profile.nickname, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

class CreateProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _createdId = MutableStateFlow<Long?>(null)

    /** The new profile's id once it is stored. */
    val createdId: StateFlow<Long?> = _createdId.asStateFlow()
    private var saving = false

    fun create(nickname: String, avatarId: String) {
        if (saving) return
        saving = true
        viewModelScope.launch { _createdId.value = container.profiles.create(nickname, avatarId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(container: AppContainer, onCreated: (Long) -> Unit) {
    val viewModel: CreateProfileViewModel = viewModel(factory = viewModelFactory { initializer { CreateProfileViewModel(container) } })
    val createdId by viewModel.createdId.collectAsStateWithLifecycle()
    LaunchedEffect(createdId) { createdId?.let(onCreated) }
    val avatars = container.catalog.avatars
    var nickname by rememberSaveable { mutableStateOf("") }
    var avatarId by rememberSaveable { mutableStateOf(avatars.firstOrNull()?.id) }

    Scaffold(topBar = { ScreenHeader(stringResource(R.string.profile_create_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).screenWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(MAX_NICKNAME) },
                label = { Text(stringResource(R.string.profile_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.profile_avatar), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AvatarPicker(container, avatarId) { avatarId = it }
            Button(
                onClick = { avatarId?.let { viewModel.create(nickname, it) } },
                enabled = nickname.isNotBlank() && avatarId != null && createdId == null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.profile_create), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private const val MAX_NICKNAME = 20

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarPicker(container: AppContainer, selectedId: String?, onSelected: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        container.catalog.avatars.forEach { avatar ->
            val selected = avatar.id == selectedId
            Surface(onClick = { onSelected(avatar.id) }, shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 4.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(88.dp).semantics { this.selected = selected; role = Role.RadioButton }) {
                Picture(avatar.emoji, stringResource(R.string.avatar_description, avatar.id), Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun EditAvatarScreen(container: AppContainer, profileId: Long, onSaved: () -> Unit) {
    val viewModel: EditAvatarViewModel = viewModel(factory = viewModelFactory { initializer { EditAvatarViewModel(container, profileId) } })
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) onSaved() }
    val profile by container.profiles.profile(profileId).collectAsStateWithLifecycle(initialValue = null)
    val current = profile ?: return
    var avatarId by rememberSaveable(current.id) { mutableStateOf(current.avatarId) }
    Scaffold(topBar = { ScreenHeader(stringResource(R.string.profile_edit_avatar)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).screenWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarPicker(container, avatarId) { avatarId = it }
            Button(onClick = { viewModel.save(avatarId) }, enabled = !saved) {
                Text(stringResource(R.string.profile_save))
            }
        }
    }
}

class EditAvatarViewModel(private val container: AppContainer, private val profileId: Long) : ViewModel() {
    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()
    private var saving = false
    fun save(avatarId: String) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            container.profiles.setAvatar(profileId, avatarId)
            _saved.value = true
        }
    }
}
