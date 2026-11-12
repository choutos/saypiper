package eu.wanderclan.wordquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import eu.wanderclan.wordquest.content.Cycle
import eu.wanderclan.wordquest.content.Grade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClassPickerViewModel(private val container: AppContainer, private val profileId: Long) : ViewModel() {
    private val _picked = MutableStateFlow(false)

    /** True once the class is stored on the profile. */
    val picked: StateFlow<Boolean> = _picked.asStateFlow()

    /** Changing class keeps all progress. */
    fun pick(gradeId: String) {
        viewModelScope.launch {
            container.profiles.setGrade(profileId, gradeId)
            _picked.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassPickerScreen(container: AppContainer, profileId: Long, onPicked: () -> Unit) {
    val viewModel: ClassPickerViewModel = viewModel(factory = viewModelFactory { initializer { ClassPickerViewModel(container, profileId) } })
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    LaunchedEffect(picked) { if (picked) onPicked() }
    val catalog = container.catalog
    Scaffold(topBar = { ScreenHeader(stringResource(R.string.grades_title)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).screenWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for ((cycle, title) in listOf(Cycle.PRIMARY to R.string.grades_primary, Cycle.SECONDARY to R.string.grades_secondary)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    catalog.grades.filter { it.cycle == cycle }.forEach { grade ->
                        GradeButton(grade, available = catalog.pack(grade.id) != null) { viewModel.pick(grade.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(grade: Grade, available: Boolean, onClick: () -> Unit) {
    val comingSoon = stringResource(R.string.grade_coming_soon, grade.label)
    FilledTonalButton(
        onClick = onClick,
        enabled = available,
        modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 80.dp)
            .then(if (available) Modifier else Modifier.semantics { contentDescription = comingSoon }),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!available) Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.labelSmall)
        }
    }
}
