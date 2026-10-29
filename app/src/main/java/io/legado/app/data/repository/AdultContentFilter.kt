package io.legado.app.data.repository

import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.help.source.AdultContentPolicy

/**
 * 「隐藏成人内容」的过滤动作。判定口径见 [AdultContentPolicy]，这里只负责筛。
 *
 * 用于发现页的源列表、书源管理的分组名列表，以及书架的分组列表（默认的「小说 · 18+」
 * 那三个分组也按分组名判定，不另存 id 清单）。书架的书籍过滤走 `BookshelfViewModel`
 * 里的 `AdultFilter`：那边要按 `BookShelfItem.origin` 反查、还要和分组数量同步收缩，
 * 就地做比抽成通用函数更清楚。
 */
object AdultContentFilter {

    fun filterSources(
        sources: List<ExploreSourceSummary>,
        hideAdult: Boolean,
    ): List<ExploreSourceSummary> {
        if (!hideAdult) return sources
        return sources.filterNot { AdultContentPolicy.isAdult(it) }
    }

    /**
     * 分组名列表的过滤。分组名是 `book_sources` 里 distinct 出来的字符串，不带类型，
     * 只能按 [AdultContentPolicy.containsMarker] 判。
     *
     * 发现页用不到它——那边的分组标题由滤过的源推导出来。书源管理的分组管理弹窗与
     * 分组筛选弹窗直接读分组名列表，不过滤就会留着「小说 · 18+」这种点进去必然空白的入口。
     */
    fun filterGroups(groups: List<String>, hideAdult: Boolean): List<String> {
        if (!hideAdult) return groups
        return groups.filterNot { AdultContentPolicy.containsMarker(it) }
    }

    /**
     * 书架分组列表的过滤。默认的「小说 · 18+」等分组由分组名带上标记，和书源走同一套判定，
     * 所以不需要另存一份成人分组 id 清单——用户改名或自建分组也一并覆盖。
     *
     * 「全部」永远保留：书架清空后没有标签页可显示。
     */
    fun filterShelfGroups(groups: List<BookGroup>, hideAdult: Boolean): List<BookGroup> {
        if (!hideAdult) return groups
        return groups.filterNot {
            it.groupId != BookGroup.IdAll && AdultContentPolicy.containsMarker(it.groupName)
        }
    }
}
