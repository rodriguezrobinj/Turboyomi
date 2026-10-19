package tachiyomi.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class DatabaseSchemaTest {
    @Test
    fun `schema matches Mihon so backups stay compatible`() {
        Database.Schema.version shouldBe 20L
    }

    @Test
    fun `fresh database accepts an extension store`() = runTest {
        val file = createTempDirectory("db José").resolve("app.db").toFile()
        val driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(file.absolutePath),
            schema = Database.Schema,
        )
        val db = Database(
            driver = driver,
            historyAdapter = History.Adapter(read_atAdapter = InstantColumnAdapter),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )

        db.extension_storeQueries.getAll().awaitAsList().size shouldBe 0
        driver.close()
    }
}
