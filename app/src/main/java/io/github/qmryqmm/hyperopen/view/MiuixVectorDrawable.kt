package io.github.qmryqmm.hyperopen.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import java.util.concurrent.ConcurrentHashMap

/**
 * 将 Compose Miuix [ImageVector] 真实矢量指令转为 Android 平台 [Drawable]，
 * 支持独立前景矢量色与纯色+透明度的圆形底垫衬底。
 *
 * 性能优化：对每个 [ImageVector] 的解析后 [android.graphics.Path]、线帽属性与总边界框做进程级静态缓存，
 * 避免每次构建气泡时重复解析 SVG PathNode、矩阵变换与 computeBounds。
 */
class MiuixVectorDrawable @JvmOverloads constructor(
    val vector: ImageVector,
    tintColor: Int,
    private val padEnabled: Boolean = false,
    private val padColor: Int = 0xFF2A82E4.toInt(),
    padAlpha: Float = ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA,
    private val cornerRadiusPercent: Int = 50,
) : Drawable() {

    private data class PathElement(
        val path: android.graphics.Path,
        val paint: Paint,
    )

    private val elements = ArrayList<PathElement>(8)
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val totalBounds: RectF

    // 图标前景色：永远忠实呈现指定的矢量颜色（100% 不透明）
    private val iconColor: Int = if (tintColor != 0) {
        Color.rgb(Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
    } else {
        0xFFFFFFFF.toInt()
    }
    private val basePadAlpha: Int = Math.round(ShortcutItem.clampPadAlpha(padAlpha) * 255f)

    init {
        if (padEnabled) {
            val baseColor = if (padColor != 0) padColor else iconColor
            padPaint.style = Paint.Style.FILL
            padPaint.color = Color.argb(basePadAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        }

        val parsed = getOrParseVector(vector)
        totalBounds = parsed.totalBounds

        for (spec in parsed.nodes) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = iconColor
                style = spec.style
                if (spec.style == Paint.Style.STROKE) {
                    strokeWidth = spec.strokeWidth
                    strokeCap = spec.strokeCap
                    strokeJoin = spec.strokeJoin
                    strokeMiter = spec.strokeMiter
                }
            }
            elements.add(PathElement(spec.path, paint))
        }
    }

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        // 1) 绘制圆角/圆形底垫（如启用，不叠加多余描边，保持与气泡材质一致的纯净底色）
        if (padEnabled) {
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val halfRadius = Math.max(1f, Math.min(b.width(), b.height()) / 2f - 1f)
            // 圆角公式与气泡 Outline / 光学层 / 图标裁切统一（BubbleBlurSpec 唯一实现）
            val cornerRadius = io.github.qmryqmm.hyperopen.util.BubbleBlurSpec.cornerRadiusOfHalfSize(halfRadius, cornerRadiusPercent)
            canvas.drawRoundRect(
                cx - halfRadius,
                cy - halfRadius,
                cx + halfRadius,
                cy + halfRadius,
                cornerRadius,
                cornerRadius,
                padPaint,
            )
        }

        // 2) 绘制 Miuix 矢量线条
        // 自动计算内容实际外接矩形或声明的 viewport，实现精准自适应居中与缩放
        val hasBounds = totalBounds.width() > 0f && totalBounds.height() > 0f
        val contentW = if (hasBounds) totalBounds.width() else (if (vector.viewportWidth > 0f) vector.viewportWidth else 24f)
        val contentH = if (hasBounds) totalBounds.height() else (if (vector.viewportHeight > 0f) vector.viewportHeight else 24f)
        val contentCenterX = if (hasBounds) totalBounds.centerX() else (if (vector.viewportWidth > 0f) vector.viewportWidth / 2f else 12f)
        val contentCenterY = if (hasBounds) totalBounds.centerY() else (if (vector.viewportHeight > 0f) vector.viewportHeight / 2f else 12f)

        val contentScaleFactor = if (padEnabled) 0.58f else 0.82f
        val scale = Math.min(b.width() / contentW, b.height() / contentH) * contentScaleFactor

        canvas.save()
        canvas.translate(b.exactCenterX(), b.exactCenterY())
        canvas.scale(scale, scale)
        canvas.translate(-contentCenterX, -contentCenterY)

        for (elem in elements) {
            canvas.drawPath(elem.path, elem.paint)
        }

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        for (elem in elements) {
            elem.paint.alpha = clamped
        }
        padPaint.alpha = Math.round(basePadAlpha * (clamped / 255f))
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        for (elem in elements) {
            elem.paint.colorFilter = colorFilter
        }
    }

    override fun getIntrinsicWidth(): Int = (if (vector.viewportWidth > 0f) vector.viewportWidth else 24f).toInt() * 4
    override fun getIntrinsicHeight(): Int = (if (vector.viewportHeight > 0f) vector.viewportHeight else 24f).toInt() * 4

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private class ParsedNodeSpec(
            val path: android.graphics.Path,
            val style: Paint.Style,
            val strokeWidth: Float,
            val strokeCap: Paint.Cap,
            val strokeJoin: Paint.Join,
            val strokeMiter: Float,
        )

        private class ParsedVector(
            val nodes: List<ParsedNodeSpec>,
            val totalBounds: RectF,
        )

        private val PARSED_VECTOR_CACHE = ConcurrentHashMap<ImageVector, ParsedVector>()

        private fun getOrParseVector(vector: ImageVector): ParsedVector {
            PARSED_VECTOR_CACHE[vector]?.let { return it }

            val nodes = ArrayList<ParsedNodeSpec>(8)
            val totalCombinedPath = android.graphics.Path()

            fun traverse(group: VectorGroup, parentMatrix: android.graphics.Matrix) {
                val groupMatrix = android.graphics.Matrix(parentMatrix).apply {
                    preTranslate(group.translationX, group.translationY)
                    preTranslate(group.pivotX, group.pivotY)
                    preRotate(group.rotation)
                    preScale(group.scaleX, group.scaleY)
                    preTranslate(-group.pivotX, -group.pivotY)
                }

                for (node in group) {
                    when (node) {
                        is VectorPath -> {
                            val originalPath = node.pathData.toPath().asAndroidPath()
                            val transformedPath = android.graphics.Path()
                            originalPath.transform(groupMatrix, transformedPath)
                            totalCombinedPath.addPath(transformedPath)

                            if (node.stroke != null) {
                                val style = Paint.Style.STROKE
                                val strokeWidth = if (node.strokeLineWidth > 0f) node.strokeLineWidth else 1.75f
                                val strokeCap = when (node.strokeLineCap) {
                                    StrokeCap.Round -> Paint.Cap.ROUND
                                    StrokeCap.Square -> Paint.Cap.SQUARE
                                    else -> Paint.Cap.BUTT
                                }
                                val strokeJoin = when (node.strokeLineJoin) {
                                    StrokeJoin.Round -> Paint.Join.ROUND
                                    StrokeJoin.Bevel -> Paint.Join.BEVEL
                                    else -> Paint.Join.MITER
                                }
                                nodes.add(
                                    ParsedNodeSpec(
                                        path = transformedPath,
                                        style = style,
                                        strokeWidth = strokeWidth,
                                        strokeCap = strokeCap,
                                        strokeJoin = strokeJoin,
                                        strokeMiter = node.strokeLineMiter,
                                    )
                                )
                            } else {
                                nodes.add(
                                    ParsedNodeSpec(
                                        path = transformedPath,
                                        style = Paint.Style.FILL,
                                        strokeWidth = 0f,
                                        strokeCap = Paint.Cap.BUTT,
                                        strokeJoin = Paint.Join.MITER,
                                        strokeMiter = 4f,
                                    )
                                )
                            }
                        }
                        is VectorGroup -> traverse(node, groupMatrix)
                    }
                }
            }

            traverse(vector.root, android.graphics.Matrix())
            val bounds = RectF()
            totalCombinedPath.computeBounds(bounds, true)

            val parsed = ParsedVector(nodes, bounds)
            PARSED_VECTOR_CACHE.putIfAbsent(vector, parsed)
            return parsed
        }
    }
}
