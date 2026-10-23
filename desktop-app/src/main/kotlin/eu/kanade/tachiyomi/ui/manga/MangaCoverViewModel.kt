package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.util.editCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.image.LocalCoverManager
import java.io.File

// desktop: Mihon's version saves through MediaStore and shares through Intents; this one uses Windows files, and a
// local manga's cover goes to its folder (spec 4a §3 D)
@AssistedInject
class MangaCoverViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val coverCache: CoverCache,
    private val localCoverManager: LocalCoverManager,
    private val updateManga: UpdateManga,
    private val context: Context,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaCoverViewModel
    }

    val snackbarHostState = SnackbarHostState()

    val state: StateFlow<Manga?> = getManga.subscribe(mangaId)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the cover as PNG, whatever format the source served. */
    fun saveCover(target: File) {
        viewModelScope.launch {
            try {
                val png = encodeCover()
                withContext(Dispatchers.IO) { target.writeBytes(png) }
                notify(MR.strings.cover_saved)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.error_saving_cover)
            }
        }
    }

    fun editCover(file: File) {
        val manga = state.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                file.inputStream().use { manga.editCover(localCoverManager, it, updateManga, coverCache) }
                notify(MR.strings.cover_updated)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.notification_cover_update_failed)
            }
        }
    }

    fun deleteCustomCover() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notify(MR.strings.cover_updated)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(MR.strings.notification_cover_update_failed)
            }
        }
    }

    fun addToLibraryWarning() {
        viewModelScope.launch { notify(MR.strings.notification_first_add_to_library) }
    }

    private suspend fun encodeCover(): ByteArray {
        val manga = state.value ?: error("The manga isn't loaded")
        val request = ImageRequest.Builder(PlatformContext.INSTANCE).data(manga).size(Size.ORIGINAL).build()
        val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request) as? SuccessResult
            ?: error("The cover couldn't be loaded")
        return withContext(Dispatchers.Default) {
            Image.makeFromBitmap(result.image.toBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
                ?: error("PNG encoding failed")
        }
    }

    private suspend fun notify(message: StringResource) {
        snackbarHostState.showSnackbar(context.stringResource(message), withDismissAction = true)
    }
}
