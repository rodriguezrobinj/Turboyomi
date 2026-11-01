package mihon.desktop.app.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** The library manga's details for the user to change (spec 13 §4); Reset brings back the source's. */
@Composable
fun MangaEditInfoDialog(
    manga: Manga,
    onSave: (MangaEdits.Edit) -> Unit,
    onReset: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var title by remember { mutableStateOf(manga.title) }
    var author by remember { mutableStateOf(manga.author.orEmpty()) }
    var artist by remember { mutableStateOf(manga.artist.orEmpty()) }
    var description by remember { mutableStateOf(manga.description.orEmpty()) }
    var genres by remember { mutableStateOf(manga.genre.orEmpty().joinToString(", ")) }
    var status by remember { mutableLongStateOf(manga.status) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.desktop_edit_manga_info)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Field(title, { title = it }, stringResource(MR.strings.title))
                Field(author, { author = it }, stringResource(MR.strings.author))
                Field(artist, { artist = it }, stringResource(MR.strings.artist))
                Field(description, {
                    description = it
                }, stringResource(MR.strings.desktop_manga_description), lines = 4)
                Field(genres, { genres = it }, stringResource(MR.strings.desktop_manga_genres))
                StatusPicker(status) { status = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        MangaEdits.Edit(
                            title = title.trim(),
                            author = author.trim(),
                            artist = artist.trim(),
                            description = description.trim(),
                            genre = genres.split(',').map(String::trim).filter(String::isNotEmpty),
                            status = status,
                        ),
                    )
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(stringResource(MR.strings.action_reset)) }
                TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
            }
        },
    )
}

@Composable
private fun Field(value: String, onValueChange: (String) -> Unit, label: String, lines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusPicker(status: Long, onPick: (Long) -> Unit) {
    val labels = mapOf(
        SManga.UNKNOWN.toLong() to stringResource(MR.strings.unknown),
        SManga.ONGOING.toLong() to stringResource(MR.strings.ongoing),
        SManga.COMPLETED.toLong() to stringResource(MR.strings.completed),
        SManga.LICENSED.toLong() to stringResource(MR.strings.licensed),
        SManga.PUBLISHING_FINISHED.toLong() to stringResource(MR.strings.publishing_finished),
        SManga.CANCELLED.toLong() to stringResource(MR.strings.cancelled),
        SManga.ON_HIATUS.toLong() to stringResource(MR.strings.on_hiatus),
    )
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(MR.strings.status) + ": " + (labels[status] ?: labels.getValue(SManga.UNKNOWN.toLong())),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPick(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
