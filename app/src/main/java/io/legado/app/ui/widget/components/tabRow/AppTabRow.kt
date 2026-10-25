package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour

/**
 * @param selectedTabIndex 选中项下标。传 -1 表示当前不在任何 tab 上——发现页把榜单档位和
 * 普通分类放在同一个 pager 里，停到榜单页时分类这一行没有对应项。material3 的 TabRow
 * 直接拿越界下标会抛 IndexOutOfBoundsException，所以这里先收敛到合法值，再用零宽指示器
 * 把选中态藏掉。
 */
@Composable
fun AppTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine
    val hasSelection = selectedTabIndex in tabTitles.indices
    val tabIndex = if (hasSelection) selectedTabIndex else 0
    // 没选中时给一个零宽指示器，避免默认指示条落在第 0 个 tab 上造成误读。
    val indicator: @Composable TabIndicatorScope.() -> Unit = {
        if (hasSelection) {
            androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabIndex)
            )
        } else {
            androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.width(0.dp)
            )
        }
    }

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        TabRowWithContour(
            tabs = tabTitles,
            selectedTabIndex = tabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier
                .padding(vertical = 4.dp),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = Color.Transparent
            )
        )
    } else {
        if (isScrollable) {
            PrimaryScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 0.dp,
                divider = { },
                containerColor = Color.Transparent,
                minTabWidth = 0.dp,
                indicator = indicator,
                modifier = modifier
            ) {
                tabTitles.forEachIndexed { index, title ->
                    AppTab(
                        selected = hasSelection && selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        title = title
                    )
                }
            }
        } else {
            PrimaryTabRow(
                selectedTabIndex = tabIndex,
                divider = { },
                containerColor = Color.Transparent,
                indicator = indicator,
                modifier = modifier
            ) {
                tabTitles.forEachIndexed { index, title ->
                    AppTab(
                        selected = hasSelection && selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        title = title
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            AppText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = if (selected) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}