package io.legado.app.feature.explore

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 发现页：分组 → 源卡片 → 单个源的榜单/分类与书列表。
 *
 * 与旧版的关键差别是**不再跨源聚合**：每张卡片就是一个真实书源，点进去只看这个站。
 * 内置源已按站拆开（31 个），所以卡片名就是站点名，副标题写着这个站能提供多少分类和榜单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreMainRoute(
    onBookClick: (SearchBook, String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (sourceUrl: String) -> Unit,
    viewModel: ExploreMainViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.ready) return

    val openedUrl = state.openedSourceUrl
    // 详情页在同一个 tab 内，返回键要先退出详情再交给底部导航。
    BackHandler(enabled = openedUrl != null) { viewModel.onIntent(ExploreMainIntent.CloseSource) }

    if (openedUrl == null) {
        SourceGridScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onOpenSource = { viewModel.onIntent(ExploreMainIntent.OpenSource(it)) },
        )
    } else {
        // 分类页的 ViewModel 要挂在发现页自己的 store 上，换源时才能整体释放。
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModel) {
            SourceDetailScreen(
                state = state,
                onIntent = viewModel::onIntent,
                onBack = { viewModel.onIntent(ExploreMainIntent.CloseSource) },
                onBookClick = onBookClick,
                onOpenLogin = onOpenLogin,
                onOpenEdit = onOpenEdit,
                // 搜索页要的是 SearchScope 的编码串，不是裸 url：裸 url 会被
                // BookSearchScope 当成一个分组名，搜出来一条都没有。
                onOpenSearch = { url ->
                    onOpenSearch(
                        SearchScope(
                            BookSourcePart(
                                bookSourceUrl = url,
                                bookSourceName = state.openedSourceName.orEmpty(),
                            )
                        ).toString()
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceGridScreen(
    state: ExploreMainUiState,
    onIntent: (ExploreMainIntent) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var groupMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    AppScaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.discovery),
                scrollBehavior = scrollBehavior,
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(ExploreMainIntent.ToggleSearch(!state.searchVisible)) },
                        imageVector = io.legado.app.ui.widget.components.icon.AppIcons.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.explore_group_filter),
                                ) { groupMenuExpanded = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = state.selectedGroup.exploreGroupLabel(),
                                style = LegadoTheme.typography.labelLarge,
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(20.dp),
                                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RoundDropdownMenu(
                            expanded = groupMenuExpanded,
                            onDismissRequest = { groupMenuExpanded = false },
                        ) {
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.all),
                                isSelected = state.selectedGroup == EXPLORE_GROUP_ALL,
                                onClick = {
                                    groupMenuExpanded = false
                                    onIntent(ExploreMainIntent.SelectGroup(EXPLORE_GROUP_ALL))
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                            )
                            state.groupNames.forEach { group ->
                                RoundDropdownMenuItem(
                                    text = group.exploreGroupLabel(),
                                    isSelected = state.selectedGroup == group,
                                    onClick = {
                                        groupMenuExpanded = false
                                        onIntent(ExploreMainIntent.SelectGroup(group))
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                )
                            }
                        }
                    }
                },
                bottomContent = {
                    AnimatedVisibility(
                        visible = state.searchVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        SearchBar(
                            query = state.searchKey,
                            onQueryChange = { onIntent(ExploreMainIntent.Search(it)) },
                            placeholder = stringResource(R.string.search),
                            autoFocus = false,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.groups.isEmpty()) {
            EmptyMessage(
                message = stringResource(R.string.explore_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@AppScaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            state.groups.forEach { group ->
                item(key = "header:${group.name}") {
                    GroupHeader(
                        name = group.name.exploreGroupLabel(),
                        count = group.sources.size,
                    )
                }
                // 两列网格按行铺：LazyColumn 的 item 里再套 Row，避免再开一个 LazyVerticalGrid
                // 与分组头一起滚动时出现嵌套滚动冲突。
                items(
                    items = group.sources.chunked(2),
                    key = { row -> "row:${group.name}:${row.first().url}" },
                ) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { source ->
                            SourceCard(
                                source = source,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenSource(source.url) },
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                item(key = "spacer:${group.name}") {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = name,
            style = LegadoTheme.typography.titleSmall,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = stringResource(R.string.explore_group_source_count, count),
            style = LegadoTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = LegadoTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SourceCard(
    source: ExploreSourceItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .background(
                color = LegadoTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = LegadoTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(
            text = source.name,
            style = LegadoTheme.typography.bodyMedium,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!source.subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            AppText(
                text = source.subtitle,
                style = LegadoTheme.typography.labelMedium,
                fontSize = 12.sp,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 单个源的详情：榜单卡片 + 分类 tab + 该站的书列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDetailScreen(
    state: ExploreMainUiState,
    onIntent: (ExploreMainIntent) -> Unit,
    onBack: () -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    onOpenLogin: (sourceUrl: String) -> Unit,
    onOpenEdit: (sourceUrl: String) -> Unit,
    onOpenSearch: (sourceUrl: String) -> Unit,
) {
    val sourceUrl = state.openedSourceUrl ?: return
    val kinds = remember(state.kinds) { loadableExploreMainKinds(state.kinds) }
    val rankEntries = remember(kinds) { kinds.filter { it.rank.isNotEmpty() } }
    val categoryEntries = remember(kinds) { kinds.filter { it.rank.isEmpty() } }
    // 只有一个 pager 位置：选中的分类/榜单就是当前页。
    val selectedIndex = kinds.indexOfFirst { exploreKindKey(it) == state.selectedKindKey }
        .takeIf { it >= 0 } ?: 0
    val currentKind = kinds.getOrNull(selectedIndex)
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var kindSheetVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 分类页的 ViewModel 挂在发现页自己的 store 上：换源时 store.clear() 一次性释放
    // 上一源的所有分类页，不用逐个 key 记账。owner 由调用方通过 CompositionLocal 传入。
    val pageViewModel: ExploreShowViewModel? = currentKind?.let {
        koinViewModel(key = "$sourceUrl|${exploreKindKey(it)}")
    }

    AppScaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = state.openedSourceName ?: "",
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    Box {
                        TopBarActionButton(
                            onClick = { menuExpanded = true },
                            imageVector = io.legado.app.ui.widget.components.icon.AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                        )
                        RoundDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.edit),
                                onClick = { menuExpanded = false; onOpenEdit(sourceUrl) },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.search),
                                onClick = { menuExpanded = false; onOpenSearch(sourceUrl) },
                            )
                            if (state.openedSourceHasLogin) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.login),
                                    onClick = { menuExpanded = false; onOpenLogin(sourceUrl) },
                                )
                            }
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.refresh),
                                onClick = {
                                    menuExpanded = false
                                    onIntent(ExploreMainIntent.RetryKinds)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.a11y_switch_layout),
                                onClick = {
                                    menuExpanded = false
                                    pageViewModel?.onIntent(ExploreShowIntent.ToggleLayout)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.delete),
                                color = LegadoTheme.colorScheme.error,
                                onClick = { menuExpanded = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 榜单与分类放在内容区而不是顶栏的 bottomContent：顶栏随折叠动画改高度，
            // 挂在上面的行会被裁掉半行字。
            if (rankEntries.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rankEntries, key = { exploreKindKey(it) }) { kind ->
                        RankCard(
                            title = kind.title,
                            rank = kind.rank,
                            selected = exploreKindKey(kind) ==
                                    kinds.getOrNull(selectedIndex)?.let(::exploreKindKey),
                            onClick = {
                                onIntent(ExploreMainIntent.SelectKind(exploreKindKey(kind)))
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (categoryEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTabRow(
                        tabTitles = categoryEntries.map { it.title },
                        selectedTabIndex = categoryEntries.indexOfFirst {
                            exploreKindKey(it) ==
                                    kinds.getOrNull(selectedIndex)?.let(::exploreKindKey)
                        },
                        onTabSelected = { index ->
                            onIntent(
                                ExploreMainIntent.SelectKind(
                                    exploreKindKey(categoryEntries[index])
                                )
                            )
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
            Box(modifier = Modifier.weight(1f)) {
                when {
                    currentKind == null && state.kindsLoading -> EmptyMessage(
                        message = stringResource(R.string.loading),
                        isLoading = true,
                        modifier = Modifier.fillMaxSize(),
                    )

                    currentKind == null -> EmptyMessage(
                        message = state.kindsError ?: stringResource(R.string.explore_empty),
                        buttonText = if (state.kindsError != null) {
                            stringResource(R.string.refresh)
                        } else {
                            null
                        },
                        onButtonClick = { onIntent(ExploreMainIntent.RetryKinds) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> {
                        val page = pageViewModel ?: return@Box
                        LaunchedEffect(page, sourceUrl, currentKind.url) {
                            page.onIntent(ExploreShowIntent.InitData(sourceUrl, currentKind.url))
                        }
                        ExploreShowRouteScreen(
                            viewModel = page,
                            title = currentKind.title,
                            onBack = {},
                            onBookClick = onBookClick,
                            embeddedInMain = true,
                            // 外层 Column 已经让出顶栏高度，内容区不能再吃一遍 padding。
                            embeddedPadding = PaddingValues(0.dp),
                            embeddedScrollBehavior = scrollBehavior,
                        )
                    }
                }
            }
        }
    }

    ExploreKindSelectSheet(
        show = kindSheetVisible,
        sourceUrl = sourceUrl,
        initialSelectedTitles = listOfNotNull(currentKind?.title),
        onDismissRequest = { kindSheetVisible = false },
        onKindsChanged = { onIntent(ExploreMainIntent.RetryKinds) },
        onSelected = { selected ->
            selected.firstOrNull()?.let {
                onIntent(ExploreMainIntent.SelectKind(exploreKindKey(it)))
            }
        },
    )

    if (confirmDelete) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.sure_del),
            content = { AppText(text = state.openedSourceName.orEmpty()) },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                confirmDelete = false
                // 删除后源列表会自己刷新，这里只负责把详情页关掉。
                onIntent(ExploreMainIntent.CloseSource)
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun RankCard(
    title: String,
    rank: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        LegadoTheme.colorScheme.primaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainerLow
    }
    Row(
        modifier = Modifier
            .height(48.dp)
            .background(container, RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.dp,
                    LegadoTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                )
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = rankIcon(rank),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp),
            tint = if (selected) {
                LegadoTheme.colorScheme.onPrimaryContainer
            } else {
                LegadoTheme.colorScheme.outline
            },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                LegadoTheme.colorScheme.onPrimaryContainer
            } else {
                LegadoTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** 档位图标：按 `rank` 取值挑，站点给的标题（总榜 / 热门排行 / 完本小说）不足以反推。 */
private fun rankIcon(rank: String): ImageVector = when (rank) {
    "update" -> Icons.Default.Update
    "week" -> Icons.Default.DateRange
    "month" -> Icons.Default.CalendarMonth
    else -> Icons.Default.Leaderboard
}

/** 「全部」这个占位分组名要换成本地化文案，六个真实分组名原样显示。 */
@Composable
private fun String.exploreGroupLabel(): String = when (this) {
    EXPLORE_GROUP_ALL -> stringResource(R.string.all)
    else -> this
}
