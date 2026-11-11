package eu.wanderclan.wordquest.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.R
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Grade
import eu.wanderclan.wordquest.data.ProfileEntity
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.PathLesson
import eu.wanderclan.wordquest.game.PathUnit
import eu.wanderclan.wordquest.game.Practice
import eu.wanderclan.wordquest.game.Progression
import eu.wanderclan.wordquest.game.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * The home screen of a profile. [units] is null when the profile's class has no playable pack
 * ("coming soon"); practice and rewards still show, since they cover every class played.
 */
data class PathState(
    val profile: ProfileEntity,
    val grade: Grade?,
    val units: List<PathUnit>?,
    val dueWordIds: List<String>,
    val points: Int,
    val streak: Int,
)

class PathViewModel(private val container: AppContainer, profileId: Long) : ViewModel() {
    /** The device's local date, read again whenever the screen resumes. */
    private val today = MutableStateFlow(container.clock.today())

    val state: StateFlow<PathState?> = combine(
        container.profiles.profile(profileId).filterNotNull(),
        container.progress.bestStars(profileId),
        container.progress.schedule(profileId),
        container.progress.activity(profileId),
        today,
    ) { profile, stars, schedule, activity, day ->
        val gradeId = profile.gradeId
        val pack = gradeId?.let(container.catalog::pack)
        PathState(
            profile = profile,
            grade = gradeId?.let(container.catalog::grade),
            units = pack?.pack?.units?.let { Progression.path(it, stars) },
            dueWordIds = Practice.dueWords(schedule, day, container.catalog),
            points = Rewards.totalPoints(activity),
            streak = Rewards.streak(activity, day),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshDay() {
        today.value = container.clock.today()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathScreen(
    container: AppContainer,
    profileId: Long,
    onChangeClass: () -> Unit,
    onLesson: (unitId: String, number: Int) -> Unit,
    onPractice: (LessonRequest.Practice) -> Unit,
    onAbout: () -> Unit,
) {
    val viewModel: PathViewModel = viewModel(factory = viewModelFactory { initializer { PathViewModel(container, profileId) } })
    // Read the date on every resume, and while resumed whenever the date or the clock changes (midnight included).
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDay()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = viewModel.refreshDay()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RewardsCard(current) { Practice.request(current.dueWordIds)?.let(onPractice) } }
            if (units == null) {
                item { Text(stringResource(R.string.path_coming_soon), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp)) }
                return@LazyColumn
            }
            if (current.grade?.band == Band.A && !speechAvailable) {
                item { Text(stringResource(R.string.speech_unavailable), Modifier.padding(8.dp)) }
            }
            items(units, key = { it.unit.id }) { UnitCard(it, onLesson) }
        }
    }
}

/** Streak, points and the Practice button. When nothing is due the button says so and is disabled. */
@Composable
private fun RewardsCard(state: PathState, onPractice: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_streak), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (state.streak > 0) pluralStringResource(R.plurals.home_streak, state.streak, state.streak)
                    else stringResource(R.string.home_streak_none),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("streak"),
                )
                Text(
                    pluralStringResource(R.plurals.home_points, state.points, state.points),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("points"),
                )
            }
            val due = state.dueWordIds.size
            Button(onClick = onPractice, enabled = due > 0, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("practice")) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (due > 0) pluralStringResource(R.plurals.practice_start, due, due) else stringResource(R.string.practice_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitCard(pathUnit: PathUnit, onLesson: (String, Int) -> Unit) {
    Card(Modifier.fillMaxWidth().alpha(if (pathUnit.isOpen) 1f else 0.6f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                pathUnit.unit.emoji?.let { Picture(it, null, Modifier.size(48.dp)) }
                Spacer(Modifier.size(12.dp))
                Text(pathUnit.unit.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                when {
                    pathUnit.hasCrown -> Icon(painterResource(R.drawable.ic_crown),
                        stringResource(R.string.path_crown_description, pathUnit.unit.title), tint = StarGold, modifier = Modifier.size(40.dp))
                    !pathUnit.isOpen -> Icon(Icons.Filled.Lock,
                        stringResource(R.string.path_unit_locked_description, pathUnit.unit.title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                }
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
    val description = when {
        !lesson.isOpen && lesson.isReview -> stringResource(R.string.path_review_locked_description, unitTitle)
        !lesson.isOpen -> stringResource(R.string.path_lesson_locked_description, unitTitle, lesson.number)
        lesson.isReview -> stringResource(R.string.path_review_description, unitTitle, lesson.stars)
        else -> stringResource(R.string.path_lesson_description, unitTitle, lesson.number, lesson.stars)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            if (lesson.isOpen) onClick { onClick(); true }
        },
    ) {
        val colour = when {
            !lesson.isOpen -> StarEmpty
            lesson.isReview -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primary
        }
        val content = if (lesson.isReview) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
        Surface(
            onClick = onClick,
            enabled = lesson.isOpen,
            shape = CircleShape,
            color = colour,
            modifier = Modifier.size(72.dp),
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (!lesson.isOpen) {
                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        if (lesson.isReview) stringResource(R.string.path_review) else stringResource(R.string.path_lesson, lesson.number),
                        style = if (lesson.isReview) MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall,
                        color = content,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
