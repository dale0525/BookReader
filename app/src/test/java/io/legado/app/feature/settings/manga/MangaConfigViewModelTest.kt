package io.legado.app.feature.settings.manga

import androidx.lifecycle.ViewModelStore
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MangaConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var failure: Exception? = null
    private val manga = object : MangaSettingsGateway {
        override val settings = MutableStateFlow(MangaSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
            failure?.let { throw it }
            settings.value = transform(settings.value)
        }
    }
    private val reading = object : ReadSettingsGateway {
        override val settings = MutableStateFlow(ReadSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) { settings.value = transform(settings.value) }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model() = MangaConfigViewModel(manga, reading).also { store.put("manga-config", it) }

    @Test fun `changes use latest settings and retain unrelated reader configuration`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        manga.settings.value = manga.currentSettings.copy(menuBottomBarBlur = true)
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(showMangaUi = false) })
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(pageScaleType = 3) })
        runCurrent()
        assertTrue(manga.currentSettings.menuBottomBarBlur)
        assertFalse(manga.currentSettings.showMangaUi)
        assertEquals(3, manga.currentSettings.pageScaleType)
    }

    @Test fun `chapter counts use shared reading configuration and clamp supported ranges`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.onIntent(MangaConfigIntent.SetNextChapters(500))
        vm.onIntent(MangaConfigIntent.SetPreviousChapters(-10)); runCurrent()
        assertEquals(100, reading.currentSettings.preDownloadNum)
        assertEquals(0, reading.currentSettings.preDownloadNumPrev)
        assertEquals(100, vm.uiState.value.nextChapters)
        assertEquals(MangaSettings().chapterPrefetchCount, manga.currentSettings.chapterPrefetchCount)
        reading.settings.value = reading.currentSettings.copy(preDownloadNumPrev = 7); runCurrent()
        assertEquals(7, vm.uiState.value.previousChapters)
    }

    @Test fun `write failures remain visible until dismissed while cancellation is not an error`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        failure = IllegalStateException("Storage unavailable")
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableGray = true) }); runCurrent()
        assertEquals("Storage unavailable", vm.uiState.value.error)
        assertFalse(manga.currentSettings.enableGray)
        vm.onIntent(MangaConfigIntent.DismissError)
        assertNull(vm.uiState.value.error)
        failure = CancellationException("Cancelled")
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableGray = true) }); runCurrent()
        assertNull(vm.uiState.value.error)
    }
}
