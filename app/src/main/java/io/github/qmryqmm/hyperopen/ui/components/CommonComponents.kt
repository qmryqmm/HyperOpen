package io.github.qmryqmm.hyperopen.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用返回导航图标组件。
 * 遵循 MIUIX 设计规范，自动适配 RTL 镜像翻转与当前主题配色。
 */
@Composable
fun BackNavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 折叠分区标题栏，包含标题文字和"展开/收起"指示器。
 * @param summary 收起时也能看到的关键参数摘要，让长设置页在不展开的情况下可被扫读
 */
@Composable
fun CollapsibleSectionTitle(
    title: String,
    expanded: Boolean,
    summary: String? = null,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SmallTitle(title)
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                )
            }
        }
        Text(
            text = if (expanded) "收起 ▲" else "展开 ▼",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 24.dp),
        )
    }
}

/**
 * Bitmap 转换为 Compose ImageBitmap 扩展方法。
 */
fun Bitmap.toComposeImageBitmap(): ImageBitmap = this.asImageBitmap()
