package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Grade
import eu.wanderclan.wordquest.content.VocabUnit
import eu.wanderclan.wordquest.data.ProfileEntity
import eu.wanderclan.wordquest.game.LessonPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

data class PathLesson(val number: Int, val isReview: Boolean, val stars: Int)
data class PathUnit(val unit: VocabUnit, val lessons: List<PathLesson>)

/** [units] is null when the profile's class has no playable pack ("coming soon"). */
data class PathState(val profile: ProfileEntity, val grade: Grade?, val units: List<PathUnit>?)

class PathViewModel(container: AppContainer, profileId: Long) : ViewModel() {
    val state: StateFlow<PathState?> = combine(
        container.profiles.profile(profileId).filterNotNull(),
        container.progress.bestStars(profileId),
    ) { profile, stars ->
        val gradeId = profile.gradeId
        val pack = gradeId?.let(container.catalog::pack)
        val units = pack?.pack?.units?.map { unit ->
            val count = LessonPlan.lessonCount(unit)
            PathUnit(unit, (1..count).map { n -> PathLesson(n, n == count, stars[unit.id to n] ?: 0) })
        }
        PathState(profile, gradeId?.let(container.catalog::grade), units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathScreen(
    container: AppContainer,
    profileId: Long,
    onChangeClass: () -> Unit,
    onLesson: (unitId: String, number: Int) -> Unit,
    onAbout: () -> Unit,
) {
    val viewModel: PathViewModel = viewModel(factory = viewModelFactory { initializer { PathViewModel(container, profileId) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val speechAvailable by container.speech.availability.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        container.catalog.avatars.firstOrNull { it.id == current.profile.avatarId }?.let {
                            Picture(it.emoji, null, Modifier.size(40.dp))
                            Spacer(Modifier.size(12.dp))
                        }
                        Text(listOfNotNull(current.profile.nickname, current.grade?.label).joinToString(" · "))
                    }
                },
                actions = {
                    TextButton(onClick = onChangeClass) { Text(stringResource(R.string.path_change_class)) }
                    IconButton(onClick = onAbout) { Icon(Icons.Filled.Info, stringResource(R.string.about)) }
                },
            )
        },
    ) { padding ->
        val units = current.units
        if (units == null) {
            Text(
                stringResource(R.string.path_coming_soon),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current.grade?.band == Band.A && !speechAvailable) {
                item { Text(stringResource(R.string.speech_unavailable), Modifier.padding(8.dp)) }
            }
            items(units, key = { it.unit.id }) { UnitCard(it, onLesson) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitCard(pathUnit: PathUnit, onLesson: (String, Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                pathUnit.unit.emoji?.let { Picture(it, null, Modifier.size(48.dp)) }
                Spacer(Modifier.size(12.dp))
                Text(pathUnit.unit.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pathUnit.lessons.forEach { lesson ->
                    LessonNode(pathUnit.unit.title, lesson) { onLesson(pathUnit.unit.id, lesson.number) }
                }
            }
        }
    }
}

@Composable
private fun LessonNode(unitTitle: String, lesson: PathLesson, onClick: () -> Unit) {
    val description = if (lesson.isReview) {
        stringResource(R.string.path_review_description, unitTitle, lesson.stars)
    } else {
        stringResource(R.string.path_lesson_description, unitTitle, lesson.number, lesson.stars)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            onClick { onClick(); true }
        },
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (lesson.isReview) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (lesson.isReview) stringResource(R.string.path_review) else stringResource(R.string.path_lesson, lesson.number),
                    style = if (lesson.isReview) MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall,
                    color = if (lesson.isReview) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Stars(lesson.stars, Modifier.padding(top = 4.dp), size = 20)
    }
}

/** Three stars, [filled] of them gold. Decorative: callers describe the count in words. */
@Composable
fun Stars(filled: Int, modifier: Modifier = Modifier, size: Int = 24) {
    Row(modifier) {
        repeat(3) { i ->
            Icon(Icons.Filled.Star, null, tint = if (i < filled) StarGold else StarEmpty, modifier = Modifier.size(size.dp))
        }
    }
}
