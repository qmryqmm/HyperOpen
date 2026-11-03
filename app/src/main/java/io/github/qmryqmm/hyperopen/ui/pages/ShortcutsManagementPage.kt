package io.github.qmryqmm.hyperopen.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.IconPainter
import io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry
import io.github.qmryqmm.hyperopen.ui.RemixIconRegistry
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 快捷项目管理页面（Tab 1）。
 * 展示当前所有快捷方式卡片列表，支持一键启闭、点击进入编辑详情、新建快捷方式以及进入整体预览。
 */
@Composable
fun ShortcutsManagementPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    shortcuts: SnapshotStateList<ShortcutItem>,
    onOpenPreview: () -> Unit,
    onShortcutToggle: (ShortcutItem, Boolean) -> Unit,
    onShortcutClick: (ShortcutItem) -> Unit,
    onAddShortcut: () -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 操作按钮卡片 ──
        item(key = "actions_card") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "快捷方式整体预览",
                        summary = "所见即所得，支持长按图标拖拽调整蜂窝顺序",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.ScreenMirroring,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        },
                        onClick = onOpenPreview,
                    )
                    ArrowPreference(
                        title = "新建快捷方式",
                        summary = "添加自定义应用、Intent 或快捷指令",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        },
                        onClick = onAddShortcut,
                    )
                }
            }
        }

        // ── 快捷项目列表 ──
        item(key = "shortcuts_title") { SmallTitle("快捷项目列表 (${shortcuts.size})") }
        itemsIndexed(shortcuts, key = { _, it -> it.id }) { _, item ->
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                ShortcutRow(
                    item = item,
                    onToggle = { onShortcutToggle(item, it) },
                    onClick = { onShortcutClick(item) },
                )
            }
        }

        item(key = "footer_tips") {
            Text(
                "提示：轻触卡片可修改参数（图标、字重底垫、名称及启动选项），右侧开关控制是否在指纹面板中展示。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 快捷方式列表项：点击卡片进入编辑，点击右侧开关切换启用状态
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutRow(
    item: ShortcutItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val vectorIcon = remember(item.iconPackage, item.iconGlyph) {
        if (item.iconPackage.isBlank()) {
            if (RemixIconRegistry.isRemixIcon(item.iconGlyph)) {
                RemixIconRegistry.find(item.iconGlyph)
            } else {
                MiuixIconRegistry.find(item.iconGlyph)
            }
        } else null
    }
    val painter = remember(item.id, item.iconPackage, item.iconGlyph, item.appIconCropMode, item.actionType, item.actionPayload) {
        if (item.iconPackage.isBlank() && vectorIcon != null) {
            null
        } else {
            IconPainter.resolve(item, ctx, item.appIconCropMode)
        }
    }

    val itemPadBg = if (vectorIcon != null && item.glyphPadEnabled) {
        when (item.glyphPadMode) {
            HyperOpenConfig.GLYPH_PAD_MODE_SOLID -> Color(item.glyphPadColor)
            HyperOpenConfig.GLYPH_PAD_MODE_INVERTED -> Color(0x30FFFFFF)
            else -> Color(item.glyphPadColor).copy(alpha = 0.65f)
        }
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }
    val itemIconTint = if (vectorIcon != null && item.glyphPadEnabled) {
        if (item.glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) Color(item.glyphPadColor)
        else Color.White
    } else {
        MiuixTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = item.title,
            summary = item.subtitle.ifBlank { "点击编辑快捷方式参数" },
            startAction = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(itemPadBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vectorIcon != null) {
                        Icon(
                            imageVector = vectorIcon,
                            contentDescription = item.title,
                            tint = itemIconTint,
                            modifier = Modifier.size(if (item.glyphPadEnabled) 20.dp else 24.dp),
                        )
                    } else if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = item.title,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            },
            endActions = {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggle,
                )
            },
            onClick = onClick,
        )
    }
}
