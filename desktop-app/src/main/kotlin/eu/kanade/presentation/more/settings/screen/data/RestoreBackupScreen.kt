package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

class RestoreBackupScreen(
    private val path: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel =
            assistedMetroViewModel<RestoreBackupViewModel, RestoreBackupViewModel.Factory> { create(path = path) }
        val state by viewModel.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_restore_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_restore),
                actionEnabled = state.canRestore && state.options.canRestore(),
                onClickAction = {
                    if (viewModel.startRestore()) navigator.pop() else context.toast(MR.strings.restore_in_progress)
                },
            ) {
                // desktop: no MIUI warning
                if (state.canRestore) {
                    item {
                        SectionCard {
                            RestoreOptions.options.forEach { option ->
                                LabeledCheckbox(
                                    label = stringResource(option.label),
                                    checked = option.getter(state.options),
                                    onCheckedChange = {
                                        viewModel.toggle(option.setter, it)
                                    },
                                )
                            }
                        }
                    }
                }

                if (state.error != null) {
                    errorMessageItem(state.error)
                }
            }
        }
    }

    private fun LazyListScope.errorMessageItem(
        error: Any?,
    ) {
        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    val msg = buildAnnotatedString {
                        when (error) {
                            is MissingRestoreComponents -> {
                                appendLine(stringResource(MR.strings.backup_restore_content_full))
                                if (error.sources.isNotEmpty()) {
                                    appendLine()
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        appendLine(stringResource(MR.strings.backup_restore_missing_sources))
                                    }
                                    error.sources.joinTo(
                                        this,
                                        separator = "\n- ",
                                        prefix = "- ",
                                    )
                                }
                                if (error.trackers.isNotEmpty()) {
                                    appendLine()
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        appendLine(stringResource(MR.strings.backup_restore_missing_trackers))
                                    }
                                    error.trackers.joinTo(
                                        this,
                                        separator = "\n- ",
                                        prefix = "- ",
                                    )
                                }
                            }

                            is InvalidRestore -> {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.invalid_backup_file))
                                }
                                appendLine(error.uri.toString())

                                appendLine()

                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.invalid_backup_file_error))
                                }
                                appendLine(error.message)
                            }

                            else -> {
                                appendLine(error.toString())
                            }
                        }
                    }

                    SelectionContainer {
                        Text(text = msg)
                    }
                }
            }
        }
    }
}

@AssistedInject
class RestoreBackupViewModel(
    @Assisted private val path: String,
    private val backupFileValidator: BackupFileValidator,
    private val backupJob: BackupJob,
) : ViewModel() {

    val state: StateFlow<RestoreBackupViewModel.State>
        field = MutableStateFlow<RestoreBackupViewModel.State>(State())

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(path: String): RestoreBackupViewModel
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            validate(File(path))
        }
    }

    fun toggle(setter: (RestoreOptions, Boolean) -> RestoreOptions, enabled: Boolean) {
        state.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    // desktop: BackupJob instead of WorkManager; false if a backup or restore is already running
    fun startRestore(): Boolean = backupJob.restore(File(path), state.value.options)

    private suspend fun validate(file: File) {
        val results = try {
            backupFileValidator.validate(file)
        } catch (e: Exception) {
            setError(
                error = InvalidRestore(file.path, e.message.toString()),
                canRestore = false,
            )
            return
        }

        if (results.missingSources.isNotEmpty() || results.missingTrackers.isNotEmpty()) {
            setError(
                error = MissingRestoreComponents(file.path, results.missingSources, results.missingTrackers),
                canRestore = true,
            )
            return
        }

        setError(error = null, canRestore = true)
    }

    private fun setError(error: Any?, canRestore: Boolean) {
        state.update {
            it.copy(
                error = error,
                canRestore = canRestore,
            )
        }
    }

    @Immutable
    data class State(
        val error: Any? = null,
        val canRestore: Boolean = false,
        val options: RestoreOptions = RestoreOptions(),
    )
}

private data class MissingRestoreComponents(
    val uri: String,
    val sources: List<String>,
    val trackers: List<String>,
)

private data class InvalidRestore(
    val uri: String? = null,
    val message: String,
)
