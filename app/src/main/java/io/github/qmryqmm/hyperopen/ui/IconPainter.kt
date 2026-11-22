package io.github.qmryqmm.hyperopen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.view.IconGlyphFactory

/**
 * 把 IconGlyphFactory 产出的 android.graphics.drawable.Drawable 适配成 Compose Painter。
 * 采用 96px 位图光栅化 + LRU 内存缓存：应用图标与矢量字形统一走同一条路径，
 * 避免转换层处理未知 Drawable（自适应图标/主题图标）的边界情况。
 */
object IconPainter {

    private const val SIZE = 96
    private val cache = LinkedHashMap<String, BitmapPainter>(64, 0.75f, true)

    fun resolve(item: ShortcutItem, ctx: Context, cropMode: Int = item.appIconCropMode): Painter {
        val effectiveCrop = if (cropMode > 0) cropMode else 2
        val key = "${item.id}|${item.iconPackage}|${item.iconGlyph}|${item.actionType}|${item.actionPayload}|${item.tintColor}|${item.glyphPadEnabled}|${item.glyphPadColor}|${item.glyphPadAlpha}|$effectiveCrop"
        synchronized(cache) { cache[key]?.let { return it } }
        val tempItem = item.copy().apply { this.appIconCropMode = effectiveCrop }
        val d: Drawable = IconGlyphFactory.resolve(ctx, tempItem)
        val painter = rasterize(key, d)
        return painter
    }

    fun glyph(key: String, tint: Int): Painter {
        val cacheKey = "glyph|$key|$tint"
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        return rasterize(cacheKey, IconGlyphFactory.createGlyph(key, tint))
    }

    private fun rasterize(cacheKey: String, d: Drawable): BitmapPainter {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, SIZE, SIZE)
        d.draw(canvas)
        val painter = BitmapPainter(bmp.asImageBitmap())
        synchronized(cache) {
            if (cache.size > 96) cache.clear()
            cache[cacheKey] = painter
        }
        return painter
    }
}
