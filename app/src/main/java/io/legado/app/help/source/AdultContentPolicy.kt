package io.legado.app.help.source

import io.legado.app.data.entities.ExploreSourceSummary

/**
 * 成人书源的唯一判定口径：`bookSourceGroup` 里带 `18+`。
 *
 * 为什么不用 `assets/18PlusList.txt`：那份清单是 208 个 base64 域名，实测内置的 10 个
 * 成人源一个都不在其中；更关键的是 `SourceHelp.insertBookSource()` 对命中它的源是
 * **直接拒绝导入**的——它挡的是"不该进库的站"，不是"进了库的成人内容"。拿它做展示过滤
 * 既覆盖不到内置源，也和它的原意错位。
 *
 * 分组名是唯一由书源自己声明的成人标记，`ExploreGroups.canonical()` 归六档时也只看它。
 * 用户自建源只要分组名里写了 `18+` 就能被认出来，不限于内置的那六个字符串。
 */
object AdultContentPolicy {

    /** 与 `BookSourceDao.flowAdultSourceUrls()` 里的 `instr(bookSourceGroup, '18+')` 同一口径 */
    private const val ADULT_MARKER = "18+"

    fun isAdult(source: ExploreSourceSummary): Boolean =
        containsMarker(source.bookSourceGroup)

    /** 分组名为空返回 false——没有声明就不算成人。 */
    fun containsMarker(group: String?): Boolean = group?.contains(ADULT_MARKER) == true
}
