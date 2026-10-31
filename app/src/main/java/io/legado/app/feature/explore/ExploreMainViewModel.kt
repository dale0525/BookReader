package io.legado.app.feature.explore

import androidx.compose.runtime.Stable
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.constant.ExploreGroups
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.AdultContentFilter
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.utils.eventObservable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 发现页的一张源卡片。`subtitle` 来自书源的 `bookSourceComment`（「N 个分类 · M 个榜单」）。 */
@Stable
data class ExploreSourceItem(
    val url: String,
    val name: String,
    val subtitle: String?,
    val hasLoginUrl: Boolean = false,
)

/** 一个分组下的源卡片。`name` 恒为 `ExploreGroups.ordered` 里的六档之一。 */
@Stable
data class ExploreSourceGroup(
    val name: String,
    val sources: ImmutableList<ExploreSourceItem>,
)

@Stable
data class ExploreMainUiState(
    val ready: Boolean = false,
    val groups: ImmutableList<ExploreSourceGroup> = persistentListOf(),
    /** 分组筛选下拉里可选的项，顺序按源自身的排列，不受搜索词影响。 */
    val groupNames: ImmutableList<String> = persistentListOf(),
    val searchVisible: Boolean = false,
    val searchKey: String = "",
    val selectedGroup: String = EXPLORE_GROUP_ALL,
    val openedSourceUrl: String? = null,
    val openedSourceName: String? = null,
    val openedSourceHasLogin: Boolean = false,
    val selectedKindKey: String? = null,
    val kinds: ImmutableList<ExploreKind> = persistentListOf(),
    val kindsLoading: Boolean = false,
    val kindsError: String? = null,
)

sealed interface ExploreMainIntent {
    data class ToggleSearch(val enabled: Boolean) : ExploreMainIntent
    data class Search(val key: String) : ExploreMainIntent
    data class SelectGroup(val group: String) : ExploreMainIntent
    data class OpenSource(val sourceUrl: String) : ExploreMainIntent
    data object CloseSource : ExploreMainIntent
    data class SelectKind(val kindKey: String) : ExploreMainIntent
    data object RetryKinds : ExploreMainIntent
    data object DeleteOpenedSource : ExploreMainIntent
}

class ExploreMainViewModel(
    private val repository: ExploreRepository,
    private val settingsRepository: SettingsRepository,
    private val privateAccessGateway: PrivateAccessGateway,
) : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    private val _uiState = MutableStateFlow(ExploreMainUiState())
    val uiState: StateFlow<ExploreMainUiState> = _uiState.asStateFlow()

    private val local = MutableStateFlow(ExploreLocalState())
    private var rawSources: List<ExploreSourceSummary> = emptyList()
    private var kindsJob: Job? = null
    private var loadedSourceUrl: String? = null
    private var kindsGeneration = 0L
    private var kindSelections: Map<String, String> = emptyMap()
    /** 用户已打开的源优先于源列表刷新：书源被停用后不该把正在看的详情页顶掉。 */
    private var openedOverride: String? = null

    init {
        viewModelScope.launch {
            combine(
                repository.getExploreSourceSummaries(),
                settingsRepository.getString(PreferKey.exploreMainKind),
                privateAccessGateway.settings.map { it.hideAdultContent }.distinctUntilChanged(),
            ) { sources, savedKinds, hideAdult ->
                Triple(sources, savedKinds, hideAdult)
            }.collect { (sources, savedKinds, hideAdult) ->
                // 过滤放在 rawSources 之前：分组标题由实际存在的源推导，源被滤光后
                // 「漫画 · 18+」这类标题自然不会再出现，不用另外维护一份标题黑名单。
                val visibleSources = AdultContentFilter.filterSources(sources, hideAdult)
                rawSources = visibleSources
                // 写偏好是异步的，回读可能仍是不含刚选分类的旧快照，本地记录优先。
                kindSelections = parseExploreKindSelections(savedKinds) + kindSelections
                if (openedOverride != null && visibleSources.none { it.bookSourceUrl == openedOverride }) {
                    openedOverride = null
                }
                publish()
            }
        }
        // 登录这类操作会让书源自己把分类缓存作废（BaseSource.refreshExplore），
        // 正在看的页面要跟着重算，否则「我的收藏」得等重启 app 才出现。
        viewModelScope.launch {
            eventFlow<String>(EventBus.EXPLORE_KINDS_INVALIDATED).collect { sourceUrl ->
                if (sourceUrl == local.value.openedSourceUrl) loadKinds(sourceUrl, force = true)
            }
        }
    }

    /** 把 LiveEventBus 的某个事件转成 Flow；订阅在收集结束时一并摘掉。 */
    private inline fun <reified T> eventFlow(tag: String) = callbackFlow {
        val observable = eventObservable<T>(tag)
        val observer = Observer<T> { trySend(it) }
        observable.observeForever(observer)
        awaitClose { observable.removeObserver(observer) }
    }

    fun onIntent(intent: ExploreMainIntent) {
        when (intent) {
            is ExploreMainIntent.ToggleSearch -> {
                local.update {
                    it.copy(
                        searchVisible = intent.enabled,
                        searchKey = if (intent.enabled) it.searchKey else "",
                    )
                }
                publish()
            }

            is ExploreMainIntent.Search -> {
                local.update { it.copy(searchKey = intent.key) }
                publish()
            }

            is ExploreMainIntent.SelectGroup -> {
                local.update { it.copy(selectedGroup = intent.group) }
                publish()
            }

            is ExploreMainIntent.OpenSource -> {
                if (rawSources.none { it.bookSourceUrl == intent.sourceUrl }) return
                openedOverride = intent.sourceUrl
                clearKindOwners()
                local.update {
                    it.copy(
                        openedSourceUrl = intent.sourceUrl,
                        selectedKindKey = kindSelections[intent.sourceUrl],
                    )
                }
                publish()
                loadKinds(intent.sourceUrl)
            }

            ExploreMainIntent.CloseSource -> {
                openedOverride = null
                clearKindOwners()
                local.update {
                    it.copy(
                        openedSourceUrl = null,
                        selectedKindKey = null,
                        kinds = persistentListOf(),
                        kindsLoading = false,
                        kindsError = null,
                    )
                }
                publish()
            }

            is ExploreMainIntent.SelectKind -> {
                val sourceUrl = local.value.openedSourceUrl ?: return
                if (kindSelections[sourceUrl] == intent.kindKey) return
                kindSelections = kindSelections + (sourceUrl to intent.kindKey)
                local.update { it.copy(selectedKindKey = intent.kindKey) }
                publish()
                viewModelScope.launch {
                    settingsRepository.putString(
                        PreferKey.exploreMainKind,
                        encodeExploreKindSelections(kindSelections),
                    )
                }
            }

            ExploreMainIntent.RetryKinds -> loadKinds(local.value.openedSourceUrl, force = true)

            ExploreMainIntent.DeleteOpenedSource -> {
                val sourceUrl = local.value.openedSourceUrl ?: return
                // 删除后源列表的 Flow 会自己少一项，这里只负责关掉详情页并释放分类页。
                onIntent(ExploreMainIntent.CloseSource)
                viewModelScope.launch { repository.deleteSource(sourceUrl) }
            }
        }
    }

    override fun onCleared() {
        viewModelStore.clear()
        super.onCleared()
    }

    /** 分类页各自持有 ExploreShowViewModel，换源时必须整体释放，否则旧源的列表会留在内存里。 */
    private fun clearKindOwners() {
        viewModelStore.clear()
    }

    private fun publish() {
        _uiState.update { current ->
            val state = local.value
            val openedSource = rawSources.firstOrNull { it.bookSourceUrl == state.openedSourceUrl }
            val groups = buildExploreSourceGroups(
                sources = rawSources,
                searchKey = state.searchKey,
                selectedGroup = state.selectedGroup,
            )
            current.copy(
                ready = true,
                groups = groups.toImmutableList(),
                groupNames = groupNamesOf(rawSources).toImmutableList(),
                searchVisible = state.searchVisible,
                searchKey = state.searchKey,
                selectedGroup = state.selectedGroup,
                openedSourceUrl = state.openedSourceUrl,
                openedSourceName = openedSource?.bookSourceName,
                openedSourceHasLogin = openedSource?.hasLoginUrl == true,
                selectedKindKey = state.selectedKindKey,
                kinds = state.kinds,
                kindsLoading = state.kindsLoading,
                kindsError = state.kindsError,
            )
        }
    }

    private fun loadKinds(sourceUrl: String?, force: Boolean = false) {
        if (!force && loadedSourceUrl == sourceUrl) return
        loadedSourceUrl = sourceUrl
        val generation = ++kindsGeneration
        kindsJob?.cancel()
        local.update {
            it.copy(kinds = persistentListOf(), kindsLoading = sourceUrl != null, kindsError = null)
        }
        publish()
        if (sourceUrl == null) return
        kindsJob = viewModelScope.launch {
            try {
                val kinds = repository.getSourceExploreKinds(sourceUrl)
                if (generation == kindsGeneration) {
                    local.update { it.copy(kinds = kinds.toImmutableList(), kindsLoading = false) }
                    publish()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == kindsGeneration) {
                    local.update {
                        it.copy(
                            kindsLoading = false,
                            kindsError = error.localizedMessage ?: error.toString(),
                        )
                    }
                    publish()
                }
            }
        }
    }
}

private data class ExploreLocalState(
    val searchVisible: Boolean = false,
    val searchKey: String = "",
    val selectedGroup: String = EXPLORE_GROUP_ALL,
    val openedSourceUrl: String? = null,
    val selectedKindKey: String? = null,
    val kinds: ImmutableList<ExploreKind> = persistentListOf(),
    val kindsLoading: Boolean = false,
    val kindsError: String? = null,
)

/** 分组筛选下拉里的「全部」。 */
const val EXPLORE_GROUP_ALL = ""

/**
 * 按分组切分源卡片，组顺序固定为 [ExploreGroups.ordered]。
 *
 * 分组名不直接取书源里写的那串文字，而是经 [ExploreGroups.canonical] 归一：
 * 书源的分组是手写的，写错标点、写成「视频·18+」少个空格、或者新增一个别的组名，
 * 发现页就会多出第 7 个分组标题。归一之后只可能出现 6 档，顺序也不再依赖源的排列。
 */
internal fun buildExploreSourceGroups(
    sources: List<ExploreSourceSummary>,
    searchKey: String,
    selectedGroup: String,
): List<ExploreSourceGroup> {
    val key = searchKey.trim()
    return sources
        .filter { key.isEmpty() || it.bookSourceName.contains(key, ignoreCase = true) }
        .filter { selectedGroup == EXPLORE_GROUP_ALL || it.exploreGroup == selectedGroup }
        .groupBy { it.exploreGroup }
        .entries
        .sortedBy { ExploreGroups.ordered.indexOf(it.key) }
        .map { (name, list) ->
            ExploreSourceGroup(
                name = name,
                sources = list.map {
                    ExploreSourceItem(
                        url = it.bookSourceUrl,
                        name = it.bookSourceName,
                        subtitle = it.bookSourceComment,
                        hasLoginUrl = it.hasLoginUrl,
                    )
                }.toImmutableList(),
            )
        }
}

private fun groupNamesOf(sources: List<ExploreSourceSummary>): List<String> {
    val present = sources.map { it.exploreGroup }.toSet()
    return ExploreGroups.ordered.filter { it in present }
}

internal fun loadableExploreMainKinds(kinds: List<ExploreKind>): List<ExploreKind> =
    aggregateExploreKinds(
        kinds.filter { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
    )

/** 分类在偏好与 ViewModel 之间的稳定标识：标题可能重复，必须带上探索地址。 */
internal fun exploreKindKey(kind: ExploreKind): String =
    "${kind.title.length}:${kind.title}:${kind.url}"

private const val KIND_ENTRY_SEPARATOR = '\u0001'
private const val KIND_VALUE_SEPARATOR = '\u0000'

internal fun encodeExploreKindSelections(selections: Map<String, String>): String =
    selections.entries.joinToString(KIND_ENTRY_SEPARATOR.toString()) { (source, kind) ->
        "$source$KIND_VALUE_SEPARATOR$kind"
    }

internal fun parseExploreKindSelections(raw: String): Map<String, String> {
    if (raw.isEmpty()) return emptyMap()
    return raw.split(KIND_ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val index = entry.indexOf(KIND_VALUE_SEPARATOR)
            if (index <= 0 || index == entry.lastIndex) return@mapNotNull null
            entry.substring(0, index) to entry.substring(index + 1)
        }
        .toMap()
}
