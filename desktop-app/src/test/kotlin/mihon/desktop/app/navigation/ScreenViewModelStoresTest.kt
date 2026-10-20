package mihon.desktop.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ScreenViewModelStoresTest {
    class Counter(val id: Int) : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private fun counterIn(stores: ScreenViewModelStores, key: String, id: Int): Counter {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Counter(id) as T
        }
        return ViewModelProvider.create(stores.ownerFor(key), factory)[Counter::class]
    }

    @Test
    fun `two screens of the same class get their own view model`() {
        val stores = ScreenViewModelStores()
        val first = counterIn(stores, "manga-1", 1)
        val second = counterIn(stores, "manga-2", 2)

        first shouldNotBeSameInstanceAs second
        counterIn(stores, "manga-1", 99) shouldBeSameInstanceAs first
    }

    @Test
    fun `a screen that leaves the stack clears its view model and keeps the others`() {
        val stores = ScreenViewModelStores()
        val first = counterIn(stores, "manga-1", 1)
        val second = counterIn(stores, "manga-2", 2)

        stores.retainOnly(setOf("manga-1"))

        second.cleared shouldBe true
        first.cleared shouldBe false
        counterIn(stores, "manga-1", 99).id shouldBe 1
    }
}
