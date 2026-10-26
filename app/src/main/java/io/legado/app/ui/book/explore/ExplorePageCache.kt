package io.legado.app.ui.book.explore

import io.legado.app.data.entities.SearchBook

/**
 * 发现页列表的进程级缓存。
 *
 * 切走再切回同一个分类会重建 ViewModel，之前每次都要重跑一遍整页抓取；分类越多、
 * 站点越慢，来回切 tab 的等待越明显。这里按「书源 + 分类 URL」记住已加载的书和
 * 分页位置，重建时直接恢复，下拉刷新才重新抓。
 *
 * 只放在内存里：进程被杀后重新拉一次是合理代价，也避免把几百本书写进数据库。
 */
internal object ExplorePageCache {

    data class Entry(
        val books: List<SearchBook>,
        val nextPage: Int,
        val isEnd: Boolean,
    )

    private const val MAX_ENTRIES = 24
    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun key(sourceUrl: String, exploreUrl: String?): String =
        "$sourceUrl\u0000${exploreUrl.orEmpty()}"

    @Synchronized
    fun get(sourceUrl: String, exploreUrl: String?): Entry? =
        entries[key(sourceUrl, exploreUrl)]

    @Synchronized
    fun put(sourceUrl: String, exploreUrl: String?, entry: Entry) {
        entries[key(sourceUrl, exploreUrl)] = entry
    }

    @Synchronized
    fun remove(sourceUrl: String, exploreUrl: String?) {
        entries.remove(key(sourceUrl, exploreUrl))
    }
}
