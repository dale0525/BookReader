package io.legado.app.feature.explore

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreRouteScreen
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExploreMainRoute(
    onOpenExploreShow: (String?, String, String?) -> Unit,
    onOpenLogin: (String) -> Unit,
    onOpenEdit: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    viewModel: ExploreMainViewModel = koinViewModel(),
    sourceViewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceState by sourceViewModel.uiState.collectAsStateWithLifecycle()
    if (!state.ready) return

    key(state.selectedSourceUrl) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModel) {
            val kinds = remember(state.kinds) {
                loadableExploreMainKinds(state.kinds)
            }
            val pagerState = rememberPagerState { kinds.size }
            val scope = rememberCoroutineScope()
            val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
            val currentKind = kinds.getOrNull(pagerState.currentPage)
            val selectedSource = state.sources.firstOrNull { it.url == state.selectedSourceUrl }
            // 榜单档位与普通分类是两套入口：档位单独成卡片行，分类仍走 pager 的 tab。
            // 两者共用同一个 pager，点卡片只是把 pager 翻到对应页，不另建一套加载逻辑。
            val rankEntries = remember(kinds) {
                kinds.withIndex().filter { it.value.rank.isNotEmpty() }
            }
            val categoryEntries = remember(kinds) {
                kinds.withIndex().filter { it.value.rank.isEmpty() }
            }
            // 恢复该书上上次浏览的分类，并把之后的稳定翻页写回偏好。
            LaunchedEffect(kinds, state.selectedKindKey) {
                val savedIndex = kinds.indexOfFirst { exploreKindKey(it) == state.selectedKindKey }
                if (savedIndex > 0 && pagerState.currentPage != savedIndex) {
                    pagerState.scrollToPage(savedIndex)
                }
                snapshotFlow { pagerState.settledPage }
                    .drop(1)
                    .collect { page ->
                        kinds.getOrNull(page)?.let {
                            viewModel.onIntent(ExploreMainIntent.SelectKind(exploreKindKey(it)))
                        }
                    }
            }
            val activeViewModel: ExploreShowViewModel? = currentKind?.let {
                koinViewModel(key = exploreKindKey(it))
            }
            // 只订阅列表/网格布局字段供书源 sheet 呈现，其余状态仍由各分类界面自己持有。
            val showViewModel = activeViewModel
            val showLayoutMode = if (showViewModel != null) {
                showViewModel.uiState
                    .map { it.layoutState }
                    .distinctUntilChanged()
                    .collectAsStateWithLifecycle(showViewModel.uiState.value.layoutState)
                    .value
            } else {
                null
            }
            val showGridCount = if (showViewModel != null) {
                showViewModel.uiState
                    .map { it.gridCount }
                    .distinctUntilChanged()
                    .collectAsStateWithLifecycle(showViewModel.uiState.value.gridCount)
                    .value
            } else {
                3
            }
            var sourceSheetVisible by remember { mutableStateOf(false) }
            var kindSheetVisible by remember { mutableStateOf(false) }

            AppScaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    if (state.showContent) {
                        GlassMediumFlexibleTopAppBar(
                            title = stringResource(R.string.discovery),
                            subtitle = selectedSource?.name,
                            scrollBehavior = scrollBehavior,
                            actions = {
                                // 等价旧版长按书源的「搜索」：打开搜索界面并只使用当前书源搜索。
                                TopBarActionButton(
                                    onClick = {
                                        selectedSource?.let { source ->
                                            onOpenSearch(
                                                SearchScope(
                                                    BookSourcePart(
                                                        bookSourceUrl = source.url,
                                                        bookSourceName = source.name
                                                    )
                                                ).toString()
                                            )
                                        }
                                    },
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                                // 模式切换放在右侧第二个，最右是书源 / 分组入口。
                                TopBarActionButton(
                                    onClick = {
                                        viewModel.onIntent(
                                            ExploreMainIntent.ShowContent(
                                                false
                                            )
                                        )
                                    },
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = stringResource(R.string.explore_source_list),
                                )
                                TopBarActionButton(
                                    onClick = { sourceSheetVisible = true },
                                    imageVector = AppIcons.MoreVert,
                                    contentDescription = stringResource(R.string.explore_select_source),
                                )
                            },
                            bottomContent = {
                                if (kinds.isNotEmpty()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // 档位卡片只在真有站点提供时出现；没有来源的档位直接不渲染。
                                        if (rankEntries.isNotEmpty()) {
                                            LazyRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                items(rankEntries, key = { it.index }) { entry ->
                                                    RankCard(
                                                        title = entry.value.title,
                                                        selected = pagerState.targetPage == entry.index,
                                                        onClick = {
                                                            scope.launch {
                                                                pagerState.animateScrollToPage(entry.index)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        if (categoryEntries.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AppTabRow(
                                                    tabTitles = categoryEntries.map { it.value.title },
                                                    selectedTabIndex = selectedCategoryTabIndex(
                                                        categoryEntries.map { it.index },
                                                        pagerState.targetPage,
                                                    ),
                                                    onTabSelected = { index ->
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(
                                                                categoryEntries[index].index
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    isScrollable = true,
                                                )
                                                SmallOutlinedButton(
                                                    onClick = { kindSheetVisible = true },
                                                    icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                                    contentDescription = stringResource(R.string.select_or_search_category),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        DynamicTopAppBar(
                            title = stringResource(R.string.discovery),
                            subtitle = sourceState.selectedGroup.ifEmpty { stringResource(R.string.all) },
                            state = sourceState,
                            scrollBehavior = scrollBehavior,
                            onSearchToggle = {
                                sourceViewModel.onIntent(
                                    ExploreIntent.ToggleSearch(
                                        it
                                    )
                                )
                            },
                            onSearchQueryChange = { sourceViewModel.onIntent(ExploreIntent.Search(it)) },
                            searchPlaceholder = stringResource(R.string.search),
                            onClearSelection = {},
                            topBarActions = {
                                TopBarActionButton(
                                    onClick = {
                                        viewModel.onIntent(
                                            ExploreMainIntent.ShowContent(
                                                true
                                            )
                                        )
                                    },
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = stringResource(R.string.explore_main_content),
                                )
                            },
                            dropDownMenuContent = { dismiss ->
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                                    text = stringResource(R.string.all),
                                    onClick = { sourceViewModel.onIntent(ExploreIntent.SetGroup("")); dismiss() },
                                )
                                sourceState.groups.forEach { group ->
                                    RoundDropdownMenuItem(
                                        leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                                        text = group,
                                        onClick = {
                                            sourceViewModel.onIntent(
                                                ExploreIntent.SetGroup(
                                                    group
                                                )
                                            ); dismiss()
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                Crossfade(
                    targetState = state.showContent,
                    label = "ExploreMainMode"
                ) { showContent ->
                    if (!showContent) {
                        ExploreRouteScreen(
                            viewModel = sourceViewModel,
                            onOpenExploreShow = onOpenExploreShow,
                            onOpenLogin = onOpenLogin,
                            onOpenEdit = onOpenEdit,
                            onOpenSearch = onOpenSearch,
                            embeddedPadding = padding,
                        )
                    } else if (kinds.isEmpty() || selectedSource == null) {
                        EmptyMessage(
                            message = state.kindsError ?: stringResource(R.string.explore_empty),
                            isLoading = state.kindsLoading,
                            buttonText = if (state.kindsError != null) stringResource(R.string.refresh) else null,
                            onButtonClick = { viewModel.onIntent(ExploreMainIntent.RetryKinds) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            // 分类列表缩短的那一帧，pager 仍可能按旧的页数请求 key。
                            key = { index ->
                                kinds.getOrNull(index)?.let(::exploreKindKey)
                                    ?: "missing-kind:$index"
                            },
                        ) { page ->
                            val kind = kinds.getOrNull(page) ?: return@HorizontalPager
                            val pageViewModel: ExploreShowViewModel =
                                koinViewModel(key = exploreKindKey(kind))
                            LaunchedEffect(pageViewModel, selectedSource.url, kind.url) {
                                pageViewModel.onIntent(
                                    ExploreShowIntent.InitData(
                                        selectedSource.url,
                                        kind.url
                                    )
                                )
                            }
                            ExploreShowRouteScreen(
                                viewModel = pageViewModel,
                                title = kind.title,
                                onBack = {},
                                onBookClick = onBookClick,
                                embeddedInMain = true,
                                embeddedPadding = padding,
                                embeddedScrollBehavior = scrollBehavior,
                            )
                        }
                    }
                }
            }
            ExploreSourceSelectSheet(
                show = sourceSheetVisible,
                sources = state.sources,
                selectedSourceUrl = state.selectedSourceUrl,
                layoutMode = showLayoutMode,
                gridCount = showGridCount,
                onDismissRequest = { sourceSheetVisible = false },
                onSelectSource = { source ->
                    viewModel.onIntent(ExploreMainIntent.SelectSource(source.url))
                },
                onToggleLayout = {
                    showViewModel?.onIntent(ExploreShowIntent.ToggleLayout)
                },
                onSaveGridCount = { count ->
                    showViewModel?.onIntent(ExploreShowIntent.SaveGridCount(count))
                },
            )
            ExploreKindSelectSheet(
                show = kindSheetVisible,
                sourceUrl = state.selectedSourceUrl,
                initialSelectedTitles = listOfNotNull(currentKind?.title),
                onDismissRequest = { kindSheetVisible = false },
                onKindsChanged = { viewModel.onIntent(ExploreMainIntent.RetryKinds) },
                onSelected = { selected ->
                    val index = kinds.indexOfFirst { it.url == selected.firstOrNull()?.url }
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}

/** 榜单档位卡片：与分类 tab 并列，但按卡片呈现，突出它是另一种浏览方式。 */
@Composable
private fun RankCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "RankCardContainer",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
