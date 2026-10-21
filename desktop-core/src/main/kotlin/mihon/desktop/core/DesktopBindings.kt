package mihon.desktop.core

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConcurrencyModel.MultipleReadersSingleWriter
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.core.metro.IsDebugBuild
import mihon.data.database.DatabaseRepositoryImpl
import mihon.desktop.core.cef.CefInstaller
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Chapter
import tachiyomi.data.Database
import tachiyomi.data.History
import tachiyomi.data.InstantColumnAdapter
import tachiyomi.data.IntColumnAdapter
import tachiyomi.data.Manga
import tachiyomi.data.Manga_track
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter

/** Desktop versions of Mihon's AppBindings and DatabaseBindings. */
@BindingContainer
object DesktopBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesCefInstaller(appDirs: AppDirs): CefInstaller = CefInstaller(appDirs.cef)

    @Provides
    @SingleIn(AppScope::class)
    @AppCoroutineScope
    fun providesAppCoroutineScope(@IsDebugBuild isDebugBuild: Boolean): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (isDebugBuild) {
                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler?.uncaughtException(thread, throwable)
            } else {
                logcat(LogPriority.ERROR, throwable) { "Uncaught exception in the app scope" }
            }
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesXML(): XML = XML.v1 {
        policy {
            ignoreUnknownChildren()
            autoPolymorphic = true
        }
        xmlDeclMode = XmlDeclMode.Charset
        xmlVersion = XmlVersion.XML10
        setIndent(2)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesProtoBuf(): ProtoBuf = ProtoBuf

    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(appDirs: AppDirs, databaseRepository: DatabaseRepositoryImpl): SqlDriver {
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = AndroidxSqliteDatabaseType.File(appDirs.db.resolve("app.db").toString()),
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                concurrencyModel = MultipleReadersSingleWriter(isWal = true, walCount = 4),
            ),
            onConfigure = {
                val version = executePragmaQuery("user_version", { QueryResult.Value(it.apply { next() }.getLong(0)) })
                databaseRepository.isMigrating.value = version in 1..<Database.Schema.version
            },
            onOpen = {
                databaseRepository.isMigrating.value = false
            },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = InstantColumnAdapter,
            ),
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
    }
}
