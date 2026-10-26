package io.legado.app.help.coil

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 禁漫天堂的图片是服务端分块打乱的：每张图被横向切成 num 条，倒序拼接后下发。
 * 官方网页用 [scramble_image] 在 canvas 上重排，这里做同样的事，否则一张图会被
 * 看成若干错位的横条。
 *
 * num 由章节 id 和图片文件名决定（官方 get_num）：md5("$aid$page") 末位字符的
 * 字符码按年代取模，再映射到 2..20 的偶数。
 */
object JmImageDescrambler {

    /** 低于此值的章节不切图，官方与各客户端实现都用同一个阈值。 */
    private const val SCRAMBLE_ID = 220980
    private const val SEGMENTS_X10_FROM = 268850
    private const val SEGMENTS_X8_FROM = 421926

    /** 命中禁漫图片地址时返回 aid 与图片序号；其他地址返回 null。 */
    private fun parse(url: String): Pair<Int, String>? {
        if (!url.contains("/media/photos/")) return null
        val path = url.substringAfter("/media/photos/", "").substringBefore('?')
        val aid = path.substringBefore('/').toIntOrNull() ?: return null
        val file = path.substringAfter('/', "")
        val page = file.substringBeforeLast('.')
        if (page.isEmpty()) return null
        return aid to page
    }

    /** 该地址是否需要还原；不需要时调用方应原样返回字节，避免多余的解码开销。 */
    fun needsDescramble(url: String): Boolean {
        val (aid, _) = parse(url) ?: return false
        return aid >= SCRAMBLE_ID
    }

    internal fun segments(aid: Int, page: String): Int {
        // 268850 之前的章节统一切 10 条，不参与 md5 计算。
        if (aid < SEGMENTS_X10_FROM) return 10
        // 官方取的是 md5 十六进制串末位「字符」的 charCode（如 'e' -> 101），
        // 不是摘要的字节值——两者会算出不同的条数。
        val hex = MessageDigest.getInstance("MD5")
            .digest("$aid$page".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val last = hex.last().code
        val key = if (aid < SEGMENTS_X8_FROM) last % 10 else last % 8
        return key * 2 + 2
    }

    /**
     * 按官方算法把横条倒序贴回。解不出图（格式不支持等）时返回原字节，
     * 宁可显示错位的图，也不要整页空白。
     */
    fun descramble(url: String, bytes: ByteArray): ByteArray {
        val (aid, page) = parse(url) ?: return bytes
        if (aid < SCRAMBLE_ID) return bytes
        val num = segments(aid, page)
        if (num <= 1) return bytes

        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        return try {
            val w = src.width
            val h = src.height
            if (h < num) return bytes
            val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dst)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val over = h % num
            val move = h / num
            for (i in 0 until num) {
                // 第 0 条吸收余数，其余条整体下移 over，保证总高度不变。
                val srcH = if (i == 0) move + over else move
                val ySrc = h - move * (i + 1) - over
                val yDst = if (i == 0) 0 else move * i + over
                canvas.drawBitmap(
                    src,
                    Rect(0, ySrc, w, ySrc + srcH),
                    Rect(0, yDst, w, yDst + srcH),
                    paint,
                )
            }
            val out = ByteArrayOutputStream(bytes.size)
            // 用 PNG 而不是 JPEG：重排是逐像素搬运，有损重编码会在横条接缝处留下色块。
            dst.compress(Bitmap.CompressFormat.PNG, 100, out)
            dst.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            bytes
        } finally {
            src.recycle()
        }
    }
}
