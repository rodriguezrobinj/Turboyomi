package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import mihon.desktop.app.platform.FileDialogs
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<CreateBackupViewModel>()
        val state by viewModel.state.collectAsState()
        val dialogTitle = stringResource(MR.strings.pref_create_backup)

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    // desktop: the Windows save dialog instead of SAF
                    val file = FileDialogs.saveFile(dialogTitle, BackupCreator.getFilename())
                    when {
                        file == null -> {}
                        viewModel.createBackup(file) -> navigator.pop()
                        else -> context.toast(MR.strings.backup_in_progress)
                    }
                },
            ) {
                // desktop: no MIUI warning
                item {
                    SectionCard(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, viewModel)
                    }
                }

                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, viewModel)
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: List<BackupOptions.Entry>,
        state: CreateBackupViewModel.State,
        model: CreateBackupViewModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
            )
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class CreateBackupViewModel(private val backupJob: BackupJob) : ViewModel() {

    val state: StateFlow<CreateBackupViewModel.State>
        field = MutableStateFlow<CreateBackupViewModel.State>(State())

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        state.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    // desktop: BackupJob instead of WorkManager; false if a backup or restore is already running
    fun createBackup(file: File): Boolean = backupJob.create(file, state.value.options)

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    )
}
