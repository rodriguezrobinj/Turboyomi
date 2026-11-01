package mihon.desktop.app.platform

import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TaskbarProgressTest {
    @Test
    fun `the library update goes before the downloads`() {
        val library = LibraryUpdateJob.Progress(done = 3, total = 12, current = emptyList())
        taskbarBar(library, queued = 4, peak = 4, chapterPercent = 50, downloading = true) shouldBe
            TaskbarBar(25, paused = false)
    }

    @Test
    fun `the downloads count the chapters done since the queue was last empty, and the one downloading`() {
        // 10 chapters queued, 6 done, the 7th half way
        taskbarBar(null, queued = 4, peak = 10, chapterPercent = 50, downloading = true) shouldBe
            TaskbarBar(65, paused = false)
    }

    @Test
    fun `a stopped queue with chapters left is paused`() {
        taskbarBar(null, queued = 2, peak = 2, chapterPercent = 0, downloading = false) shouldBe
            TaskbarBar(0, paused = true)
    }

    @Test
    fun `nothing running clears the bar`() {
        taskbarBar(null, queued = 0, peak = 0, chapterPercent = 0, downloading = false) shouldBe null
        val starting = LibraryUpdateJob.Progress(done = 0, total = 0, current = emptyList())
        taskbarBar(starting, queued = 0, peak = 0, chapterPercent = 0, downloading = false) shouldBe null
    }
}
