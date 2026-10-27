package io.legado.app.utils

import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType

object ChineseUtils {

    private var fixed = false

    fun s2t(content: String): String {
        return ChineseUtils.s2t(content)
    }

    fun t2s(content: String): String {
        if (!fixed) {
            fixT2sDict()
        }
        return ChineseUtils.t2s(content)
    }

    /**
     * 简繁字形归一后再比较。
     *
     * 书源的检索词与站点返回的书名可能分处两种字形（简体「剑来」对繁体「劍來」），
     * 直接比较字符串会把同一本书判成不同。先转繁再转简做基准，两种字形由此落到同一形态。
     *
     * 走底层库而不是本对象的 [t2s]：后者会加载排除词表（[fixT2sDict]），那是阅读界面的转换设置，
     * 搜索路径不该顺手改掉它。排除词表是否加载不影响这里的结果——只有基准形态会变
     * （「规则」要么归到「守则」要么归到「規則」），两侧始终一致。
     */
    fun sameText(a: String, b: String): Boolean = toComparable(a) == toComparable(b)

    fun containsText(text: String, part: String): Boolean =
        toComparable(text).contains(toComparable(part))

    /**
     * 同一段文字可能被站点写成的几种字形：原样、通用繁体、台湾繁体、通用简体、归一基准。
     *
     * 给「按书名查库」用。SQL 没法做简繁归一，只能拿候选字形去撞库，
     * 因此这份清单决定了哪些结果能被捞出来；判等仍以 [sameText] 为准。
     */
    fun variants(text: String): List<String> = listOf(
        text,
        ChineseUtils.s2t(text),
        ChineseUtils.s2tw(text),
        ChineseUtils.t2s(text),
        toComparable(text),
    ).distinct()

    private fun toComparable(text: String): String = ChineseUtils.t2s(ChineseUtils.s2t(text))

    fun preLoad(async: Boolean, vararg transType: TransType) {
        ChineseUtils.preLoad(async, *transType)
    }

    fun unLoad(vararg transType: TransType) {
        ChineseUtils.unLoad(*transType)
    }

    fun fixT2sDict() {
        fixed = true
        val excludeList = listOf(
            "槃",
            "划槳", "列根", "雪梨", "雪糕", "多士", "起司", "芝士", "沙芬", "母音",
            "华乐", "民乐", "晶元", "晶片", "映像", "明覆", "明瞭", "新力", "新喻",
            "零錢", "零钱", "離線", "碟片", "模組", "桌球", "案頭", "機車", "電漿",
            "鳳梨", "魔戒", "載入", "菲林", "整合", "變數", "解碼", "散钱", "插水",
            "房屋", "房价", "快取", "德士", "建立", "常式", "席丹", "布殊", "布希",
            "巴哈", "巨集", "夜学", "向量", "半形", "加彭", "列印", "函式", "全形",
            "光碟", "介面", "乳酪", "沈船", "永珍", "演化", "牛油", "相容", "磁碟",
            "菲林", "規則", "酵素", "雷根", "饭盒",
            "路易斯", "非同步", "出租车", "周杰倫", "马铃薯", "馬鈴薯", "機械人", "電單車",
            "電扶梯", "音效卡", "飆車族", "點陣圖", "個入球", "顆進球", "沃尓沃", "晶片集",
            "斯瓦巴", "斜角巷", "战列舰", "快速面", "希特拉", "太空梭", "吐瓦魯", "吉布堤",
            "吉布地", "史太林", "南冰洋", "区域网", "波札那", "解析度", "酷洛米", "金夏沙",
            "魔獸紀元", "高空彈跳", "铁达尼号", "太空战士", "埃及妖后", "吉里巴斯", "附加元件",
            "魔鬼終結者", "純文字檔案", "奇幻魔法Melody", "列支敦斯登"
        )
        ChineseUtils.loadExcludeDict(TransType.TRADITIONAL_TO_SIMPLE, excludeList)
    }

}
