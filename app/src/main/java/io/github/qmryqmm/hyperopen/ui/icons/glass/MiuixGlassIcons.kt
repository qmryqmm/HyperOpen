package io.github.qmryqmm.hyperopen.ui.icons.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

object MiuixGlassIcons {
    object Light
    object Normal
    object Regular
    object Medium
    object Demibold
}

internal fun buildHyperOSIcon(
    name: String,
    pathData: List<PathNode>,
    scale: Float,
    translationX: Float,
    translationY: Float,
    autoMirror: Boolean,
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 28.dp,
        defaultHeight = 28.dp,
        viewportWidth = 1333.3833f,
        viewportHeight = 1333.3833f,
        autoMirror = autoMirror,
    ).apply {
        group(
            scaleX = scale,
            scaleY = -scale,
            translationX = translationX,
            translationY = translationY,
        ) {
            addPath(
                pathData = pathData,
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            )
        }
    }.build()
}
