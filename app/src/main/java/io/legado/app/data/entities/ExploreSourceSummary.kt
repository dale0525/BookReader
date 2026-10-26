package io.legado.app.data.entities

import androidx.room.Ignore
import io.legado.app.constant.ExploreGroups

/**
 * 发现页源卡片需要的字段。刻意不查 `book_sources` 整行：那里有 200KB+ 的 `jsLib`，
 * 31 个源一次拉全表会把整段规则搬进内存，而卡片只需要名字和一行说明。
 *
 * `bookSourceComment` 由内置源随包写入「N 个分类 · M 个榜单」——
 * 这行数据要跑一遍站点的 JS 才能算出来，不能在界面上现算。
 */
data class ExploreSourceSummary(
    val bookSourceUrl: String = "",
    val bookSourceName: String = "",
    val bookSourceGroup: String? = null,
    val bookSourceComment: String? = null,
    val customOrder: Int = 0,
    /** 有登录地址的源才在详情页菜单里给出「登录」。 */
    val hasLoginUrl: Boolean = false,
    /** 0 文本、2 图片、4 视频；分组名缺失或写成别的词时以它为准，见 [ExploreGroups]。 */
    val bookSourceType: Int = 0,
) {

    /** 分组名可能用 , ; ， ； 分隔多个，卡片只归到第一个，避免同一张卡片出现两次。 */
    @get:Ignore
    val firstGroup: String
        get() = bookSourceGroup
            ?.split(",", ";", "，", "；")
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

    /** 界面上的分组名，恒为 6 档之一。 */
    @get:Ignore
    val exploreGroup: String
        get() = ExploreGroups.canonical(firstGroup, bookSourceType)
}
