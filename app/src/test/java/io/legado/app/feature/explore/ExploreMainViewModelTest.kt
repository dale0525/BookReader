package io.legado.app.feature.explore

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ExploreMainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeExploreRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        // AppConfigStore 是进程级单例，逐个测试重置分类记忆避免互相污染。
        AppConfigStore.putString(PreferKey.exploreMainKind, "")
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `source grid groups keep the order the sources arrive in`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()

        assertTrue(model.uiState.value.ready)
        assertEquals(listOf("小说", "漫画"), model.uiState.value.groups.map { it.name })
        assertEquals(listOf("A"), model.uiState.value.groups.first().sources.map { it.name })
        assertEquals(listOf("B"), model.uiState.value.groups[1].sources.map { it.name })
        assertEquals(
            "10 个分类 · 2 个榜单",
            model.uiState.value.groups.first().sources.first().subtitle,
        )
    }

    @Test
    fun `search filters cards but keeps every group name selectable`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()

        model.onIntent(ExploreMainIntent.Search("b"))
        runCurrent()

        assertEquals(listOf("漫画"), model.uiState.value.groups.map { it.name })
        assertEquals(listOf("小说", "漫画"), model.uiState.value.groupNames)
    }

    @Test
    fun `group filter narrows the grid to one group`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()

        model.onIntent(ExploreMainIntent.SelectGroup("漫画"))
        runCurrent()

        assertEquals(listOf("漫画"), model.uiState.value.groups.map { it.name })
    }

    @Test
    fun `opening a source loads only that source's categories`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        assertTrue(repository.requests.isEmpty())

        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        assertEquals(listOf("a"), repository.requests)
        assertTrue(model.uiState.value.kindsLoading)

        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()

        assertEquals(listOf(kind("a")), model.uiState.value.kinds)
        assertEquals("A", model.uiState.value.openedSourceName)
        assertFalse(model.uiState.value.kindsLoading)
    }

    @Test
    fun `closing a source releases its category pages and clears kinds`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()
        var categoryCleared = false
        model.viewModelStore.put("a-category", object : ViewModel() {
            override fun onCleared() {
                categoryCleared = true
            }
        })

        model.onIntent(ExploreMainIntent.CloseSource)
        runCurrent()

        assertTrue(categoryCleared)
        assertNull(model.uiState.value.openedSourceUrl)
        assertEquals(emptyList<ExploreKind>(), model.uiState.value.kinds)
    }

    @Test
    fun `switching source rejects categories that arrive late for the previous one`() =
        runTest(dispatcher) {
            val model = createModel()
            runCurrent()
            model.onIntent(ExploreMainIntent.OpenSource("a"))
            runCurrent()

            model.onIntent(ExploreMainIntent.CloseSource)
            model.onIntent(ExploreMainIntent.OpenSource("b"))
            runCurrent()
            repository.responses.getValue("b").complete(listOf(kind("b")))
            runCurrent()
            repository.responses.getValue("a").complete(listOf(kind("a")))
            runCurrent()

            assertEquals("b", model.uiState.value.openedSourceUrl)
            assertEquals(listOf(kind("b")), model.uiState.value.kinds)
        }

    @Test
    fun `selecting a category persists it for the opened source`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()

        val categoryKey = exploreKindKey(kind("a"))
        model.onIntent(ExploreMainIntent.SelectKind(categoryKey))
        runCurrent()

        assertEquals(categoryKey, model.uiState.value.selectedKindKey)
        assertEquals(
            categoryKey,
            parseExploreKindSelections(
                AppConfigStore.getString(PreferKey.exploreMainKind).orEmpty()
            )["a"],
        )
    }

    @Test
    fun `saved category is restored when reopening that source`() = runTest(dispatcher) {
        val categoryKey = exploreKindKey(kind("a"))
        AppConfigStore.putString(
            PreferKey.exploreMainKind,
            encodeExploreKindSelections(mapOf("a" to categoryKey)),
        )
        val model = createModel()
        runCurrent()

        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()

        assertEquals(categoryKey, model.uiState.value.selectedKindKey)
    }

    @Test
    fun `retry re-runs category loading for the same source`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()

        model.onIntent(ExploreMainIntent.RetryKinds)
        runCurrent()

        assertEquals(listOf("a", "a"), repository.requests)
    }

    @Test
    fun `refreshing explore reloads categories for the opened source`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()
        assertEquals(listOf("a"), repository.requests)

        // 登录成功（MissAV 的「我的收藏」）走的就是这条：书源清缓存后广播，
        // 页面不重启也要重算一次分类。
        postEvent(EventBus.EXPLORE_KINDS_INVALIDATED, "a")
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        assertEquals(listOf("a", "a"), repository.requests)
    }

    @Test
    fun `refreshing another source leaves the opened one alone`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.OpenSource("a"))
        runCurrent()
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()

        postEvent(EventBus.EXPLORE_KINDS_INVALIDATED, "b")
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        assertEquals(listOf("a"), repository.requests)
    }

    private fun createModel() = ExploreMainViewModel(
        repository,
        SettingsRepository(),
        FakePrivateAccessGateway(),
    ).also { store.put("main", it) }

    /** 发现页只用到 settings 里的「隐藏成人内容」；其余能力本测试不涉及，保持空实现 */
    private class FakePrivateAccessGateway : PrivateAccessGateway {
        private val settingsFlow = MutableStateFlow(PrivateAccessSettings())
        override val state: Flow<PrivateAccessState> = MutableStateFlow(PrivateAccessState())
        override val settings: Flow<PrivateAccessSettings> get() = settingsFlow
        override suspend fun updateSettings(
            transform: (PrivateAccessSettings) -> PrivateAccessSettings,
        ) {
            settingsFlow.value = transform(settingsFlow.value)
        }

        override fun isGrantedNow(bookUrl: String, group: Long): Boolean = false
        override suspend fun verifyPassword(password: String, target: PrivateUnlockTarget?) = true
        override suspend fun verifyPasswordOnly(password: String): Boolean = true
        override fun revoke(target: PrivateUnlockTarget) = Unit
        override suspend fun disableBiometricShortcut() = Unit
        override suspend fun storeBiometricEnvelope(ciphertext: String, iv: String) = false
        override fun refreshBiometricStatus() = Unit
        override fun lock() = Unit
        override fun onAppBackground() = Unit
    }

    private fun kind(source: String) = ExploreKind(title = source, url = "https://$source/books")

    private class FakeExploreRepository : ExploreRepository {
        val requests = mutableListOf<String>()
        val responses = mapOf(
            "a" to CompletableDeferred<List<ExploreKind>>(),
            "b" to CompletableDeferred<List<ExploreKind>>(),
        )
        val sources = MutableStateFlow(
            listOf(
                ExploreSourceSummary(
                    bookSourceUrl = "a",
                    bookSourceName = "A",
                    bookSourceGroup = "小说",
                    bookSourceComment = "10 个分类 · 2 个榜单",
                    customOrder = 0,
                ),
                ExploreSourceSummary(
                    bookSourceUrl = "b",
                    bookSourceName = "B",
                    bookSourceGroup = "漫画",
                    bookSourceComment = "4 个分类",
                    customOrder = 1,
                ),
            )
        )

        override fun getExploreSourceSummaries() = sources
        override suspend fun refreshSourceExploreKinds(sourceUrl: String) =
            getSourceExploreKinds(sourceUrl)

        override suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> {
            requests += sourceUrl
            // Simulate a rule engine operation which cannot stop immediately on cancellation.
            return withContext(NonCancellable) { responses.getValue(sourceUrl).await() }
        }

        override fun getBookshelfItems() = flowOf(emptyList<SearchBook>())
        override suspend fun getBookSource(sourceUrl: String) = null
        override suspend fun deleteSource(sourceUrl: String) = Unit
    }
}
