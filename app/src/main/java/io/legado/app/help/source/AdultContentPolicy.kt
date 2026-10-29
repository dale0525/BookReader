package io.legado.app.help.source

import io.legado.app.data.entities.ExploreSourceSummary

/**
 * 成人书源的唯一判定口径：`bookSourceGroup` 里带成人标记（见 [MARKERS]）。
 *
 * 为什么不用 `assets/18PlusList.txt`：那份清单是 208 个 base64 域名，实测内置的 10 个
 * 成人源一个都不在其中；更关键的是 `SourceHelp.insertBookSource()` 对命中它的源是
 * **直接拒绝导入**的——它挡的是"不该进库的站"，不是"进了库的成人内容"。拿它做展示过滤
 * 既覆盖不到内置源，也和它的原意错位。
 *
 * 分组名是唯一由书源自己声明的成人标记，`ExploreGroups.canonical()` 归六档时也只看它。
 * 用户自建源只要分组名里写了标记就能被认出来，不限于内置的那六个字符串。
 */
object AdultContentPolicy {

    private const val MARKER_18_PLUS = "18+"
    private const val MARKER_ADULT = "成人"
    private const val MARKER_NO_ENTRY = "🔞"
    private const val MARKER_R18 = "r18"
    private const val MARKER_R_18 = "r-18"

    /**
     * 分组名里出现任一项即判成人。匹配前统一转小写，所以 `R18` / `R-18` 只登记小写形式。
     *
     * 不登记裸 `18`：分组名里写年份（`2018 书单`）、写字数都会误伤，而真实写法几乎都带
     * `+` 或 `R`。`成人` 也覆盖了 `成人向`、`成人漫画` 这类写法。
     */
    val MARKERS: List<String> = listOf(
        MARKER_18_PLUS,
        MARKER_ADULT,
        MARKER_NO_ENTRY,
        MARKER_R18,
        MARKER_R_18
    )

    /**
     * 与 [MARKERS] 同源的 SQL 片段，供 `@Query` 对 `bookSourceGroup` 列做同一套判定。
     *
     * Room 的 `@Query` 只接受编译期常量，SQL 里也调不到 Kotlin，只能把同一份标记再拼一遍；
     * 两处必须一起改，`AdultContentQueryTest` 按 [MARKERS] 逐条比对。`lower()` 只折叠 ASCII，
     * `成人` 与 `🔞` 不受影响。
     */
    const val SQL_GROUP_MATCHES_ADULT: String =
        "(instr(lower(bookSourceGroup), '" + MARKER_18_PLUS + "') > 0" +
            " OR instr(lower(bookSourceGroup), '" + MARKER_ADULT + "') > 0" +
            " OR instr(lower(bookSourceGroup), '" + MARKER_NO_ENTRY + "') > 0" +
            " OR instr(lower(bookSourceGroup), '" + MARKER_R18 + "') > 0" +
            " OR instr(lower(bookSourceGroup), '" + MARKER_R_18 + "') > 0)"

    fun isAdult(source: ExploreSourceSummary): Boolean =
        containsMarker(source.bookSourceGroup)

    /** 分组名为空返回 false——没有声明就不算成人。 */
    fun containsMarker(group: String?): Boolean {
        if (group.isNullOrEmpty()) return false
        val lower = group.lowercase()
        return MARKERS.any { lower.contains(it) }
    }
}
