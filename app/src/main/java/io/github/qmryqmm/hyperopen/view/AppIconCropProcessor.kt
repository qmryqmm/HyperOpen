package io.github.qmryqmm.hyperopen.view

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig

/**
 * 应用图标圆形化与自适应外扩满铺裁切处理器
 */
object AppIconCropProcessor {

    private const val BITMAP_SIZE = 192

    /**
     * 根据全局裁切模式对应用图标进行圆形处理
     */
    @JvmStatic
    @JvmOverloads
    fun process(src: Drawable?, cropMode: Int, res: Resources? = null): Drawable? {
        if (src == null) return null

        return try {
            when (cropMode) {
                HyperOpenConfig.CROP_BITMAP_CIRCLE -> createStandardCircle(src, res)
                HyperOpenConfig.CROP_ADAPTIVE_EXPAND -> createAdaptiveExpandedCircle(src, res)
                else -> createAdaptiveExpandedCircle(src, res)
            }
        } catch (t: Throwable) {
            src
        }
    }

    /**
     * 标准抗锯齿圆形裁切 (BitmapShader)
     */
    private fun createStandardCircle(drawable: Drawable, res: Resources?): Drawable {
        return renderCircularIcon(drawable, expandAdaptive = false, res = res)
    }

    /**
     * 自适应留白外扩并圆形满铺裁切 (专为 AdaptiveIcon 和带白底的应用打造)
     */
    private fun createAdaptiveExpandedCircle(drawable: Drawable, res: Resources?): Drawable {
        return renderCircularIcon(drawable, expandAdaptive = true, res = res)
    }

    private fun renderCircularIcon(drawable: Drawable, expandAdaptive: Boolean, res: Resources?): Drawable {
        val size = BITMAP_SIZE
        val srcBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val srcCanvas = Canvas(srcBmp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            // 直接绘制 background 与 foreground 图层，绕过系统 AdaptiveIconDrawable.draw() 内部的超椭圆/方圆 (Squircle) mMask 裁切，
            // 避免系统先裁成超椭圆再被圆形二次裁切时四角与四边交界形成八边形。
            val bg = drawable.background
            val fg = drawable.foreground
            if (bg != null || fg != null) {
                val layerScale = if (expandAdaptive) 1.36f else 1.22f
                val drawSize = (size * layerScale).toInt()
                val offset = (size - drawSize) / 2
                bg?.let {
                    it.setBounds(offset, offset, offset + drawSize, offset + drawSize)
                    it.draw(srcCanvas)
                }
                fg?.let {
                    it.setBounds(offset, offset, offset + drawSize, offset + drawSize)
                    it.draw(srcCanvas)
                }
            } else {
                val scale = if (expandAdaptive) 1.3333f else 1.0f
                val drawSize = (size * scale).toInt()
                val offset = (size - drawSize) / 2
                drawable.setBounds(offset, offset, offset + drawSize, offset + drawSize)
                drawable.draw(srcCanvas)
            }
        } else {
            val rawBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val rawCanvas = Canvas(rawBmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(rawCanvas)

            // 检测图标在正交四边与四对角方向上的实际不透明半径；
            // 若系统返回的是带透明边距的 MIUI 超椭圆位图（四直边落在圆内、四角落在圆外），
            // 直接裁圆会导致“四条直边 + 四段圆弧”拼成八边形，因此自动外扩使有效内容完全填满正圆。
            val fitScale = computeCircleFillScale(rawBmp, expandAdaptive)
            if (fitScale > 1.005f) {
                srcCanvas.save()
                val center = size / 2f
                srcCanvas.scale(fitScale, fitScale, center, center)
                srcCanvas.drawBitmap(rawBmp, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                srcCanvas.restore()
            } else {
                srcCanvas.drawBitmap(rawBmp, 0f, 0f, null)
            }
            // 及时回收临时位图，减轻 GC 压力
            rawBmp.recycle()
        }

        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(outBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(srcBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val center = size / 2f
        // 内缩 1px 确保抗锯齿圆边缘完整落在 Bitmap 内部，放大时绝不被四边截平
        val circleRadius = center - 1f
        outCanvas.drawCircle(center, center, circleRadius, paint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(if (expandAdaptive) 32 else 28, 255, 255, 255)
        }
        outCanvas.drawCircle(center, center, circleRadius - 1f, strokePaint)

        // 中间位图仅用于本次绘制，立即回收，避免每次呼出面板都在 SystemUI 堆上遗留数百 KB 垃圾触发 GC
        srcBmp.recycle()

        return BitmapDrawable(res ?: Resources.getSystem(), outBmp)
    }

    /**
     * 沿 8 个方向（上下左右 + 四个 45° 对角线）从中心向外扫描不透明像素边界，
     * 计算将超椭圆/带留白图标外扩至完全覆盖正圆所需的缩放比例。
     */
    private fun computeCircleFillScale(bmp: Bitmap, expandAdaptive: Boolean): Float {
        val size = bmp.width
        val center = size / 2f
        val targetRadius = center - 1f
        val dirs = arrayOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(-1f, 0f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, -1f),
            floatArrayOf(0.7071f, 0.7071f),
            floatArrayOf(-0.7071f, 0.7071f),
            floatArrayOf(0.7071f, -0.7071f),
            floatArrayOf(-0.7071f, -0.7071f),
        )

        var minReach = targetRadius
        val maxStep = center.toInt()
        for (dir in dirs) {
            var lastOpaqueDist = 0f
            for (step in 1..maxStep) {
                val x = (center + dir[0] * step).toInt().coerceIn(0, size - 1)
                val y = (center + dir[1] * step).toInt().coerceIn(0, size - 1)
                val alpha = Color.alpha(bmp.getPixel(x, y))
                if (alpha > 180) {
                    lastOpaqueDist = step.toFloat()
                }
            }
            if (lastOpaqueDist > center * 0.45f && lastOpaqueDist < minReach) {
                minReach = lastOpaqueDist
            }
        }

        if (minReach <= 0f || minReach >= targetRadius) {
            return 1.0f
        }
        val neededScale = (targetRadius / minReach) * if (expandAdaptive) 1.04f else 1.01f
        return neededScale.coerceIn(1.0f, 1.38f)
    }
}
