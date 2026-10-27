package io.legado.app.data.repository

import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.help.source.AdultContentPolicy

/**
 * 「隐藏成人内容」的过滤动作。判定口径见 [AdultContentPolicy]，这里只负责筛。
 *
 * 只用于发现页的源列表。书架的书籍过滤走 `BookshelfViewModel` 里的 `AdultFilter`：
 * 那边要按 `BookShelfItem.origin` 反查、还要和分组数量同步收缩，就地做比抽成通用函数更清楚。
 */
object AdultContentFilter {

    fun filterSources(
        sources: List<ExploreSourceSummary>,
        hideAdult: Boolean,
    ): List<ExploreSourceSummary> {
        if (!hideAdult) return sources
        return sources.filterNot { AdultContentPolicy.isAdult(it) }
    }
}
