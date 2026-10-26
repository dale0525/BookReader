package io.legado.app.constant

/**
 * 发现页的分组只有这 6 档：小说 / 漫画 / 视频 × 常规 / 18+。
 *
 * 分组名是手写在书源里的字符串，写错一个标点、少一个空格，或者新源直接写个「短剧」
 * 「玄幻」，发现页就会多出第 7 个分组标题。所以界面上的分组一律经 [canonical] 现算，
 * 不直接读 `bookSourceGroup`：
 *
 * - 分组名里写明了媒体类型（含「小说」「漫画」「视频」）就以它为准——那是人明确的分类意图。
 * - 分组名缺失或不含这三个词时，退回 `bookSourceType`（0 文本 / 2 图片 / 4 视频）。
 *   新源忘了写分组、或者用了别的分类词，靠这一步仍会落到 6 档里，不会另开一档。
 * - 18+ 只能从分组名里认（含 `18+` 即成人档）。没有别的数据能推断：`18PlusList.txt`
 *   只收录上游成人站，内置的成人源不在其中，URL 后缀也只有两个采集接口用。
 */
object ExploreGroups {

    const val NOVEL = "小说"
    const val MANGA = "漫画"
    const val VIDEO = "视频"
    const val NOVEL_ADULT = "$NOVEL · 18+"
    const val MANGA_ADULT = "$MANGA · 18+"
    const val VIDEO_ADULT = "$VIDEO · 18+"

    /** 六个合法分组，顺序即分组下拉与网格里期望的先后。 */
    val ordered = listOf(NOVEL, MANGA, VIDEO, NOVEL_ADULT, MANGA_ADULT, VIDEO_ADULT)

    /**
     * 把任意书源归到 6 档之一，结果恒在 [ordered] 内。
     *
     * @param group 书源自己的分组名（多值取第一个），可为空
     * @param sourceType `bookSourceType`：0 文本、2 图片、4 视频，其余（音频、下载站）按文本处理
     */
    fun canonical(group: String?, sourceType: Int): String {
        val media = when {
            group == null -> mediaOfType(sourceType)
            group.contains(MANGA) -> MANGA
            group.contains(VIDEO) -> VIDEO
            group.contains(NOVEL) -> NOVEL
            else -> mediaOfType(sourceType)
        }
        return if (group?.contains("18+") == true) "$media · 18+" else media
    }

    private fun mediaOfType(sourceType: Int): String = when (sourceType) {
        BookSourceType.image -> MANGA
        BookSourceType.video -> VIDEO
        else -> NOVEL
    }
}
