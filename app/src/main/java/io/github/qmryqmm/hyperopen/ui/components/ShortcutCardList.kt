package io.github.qmryqmm.hyperopen.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.IconPainter
import io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry
import io.github.qmryqmm.hyperopen.ui.RemixIconRegistry
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID
import kotlin.math.abs

/**
 * 快捷方式卡片列表共享交互组件：
 * 供「快捷项目」树形列表与「聚合文件夹」子快捷方式卡片列表复用。
 *
 * 交互约定（长按语义区分）：
 *  - 轻触卡片：点击回调（BasicComponent 原生点击，带水波纹）；
 *  - 长按后未位移抬起：进入/调整多选；
 *  - 长按后拖动：交换式拖拽排序（同一排序作用域内逐步交换）。
 */

/** 树形/卡片列表中的可视化行：key 用于拖拽与多选定位，scopeId 是「排序作用域」（同域才可交换位置）。 */
data class ShortcutRowNode(
    val key: String,
    val scopeId: String,
    val item: ShortcutItem,
    /** 0 = 顶层；1 = 文件夹子项 */
    val depth: Int,
    /** 子项所属文件夹 id（顶层行为 null） */
    val parentFolderId: String? = null,
)

/** 拖拽排序 + 长按多选的共享状态机。 */
class ShortcutListInteractionState {
    var draggingKey: String? by mutableStateOf(null)
    var dragOffsetY: Float by mutableFloatStateOf(0f)
    val rowHeights = mutableStateMapOf<String, Float>()
    var selection: Set<String> by mutableStateOf(emptySet())

    val multiSelect: Boolean get() = selection.isNotEmpty()

    fun toggleSelection(key: String) {
        selection = if (key in selection) selection - key else selection + key
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /**
     * 长按手势期间 / 结束后抑制行内误触点击：
     * 拖拽段位移被手势吞掉后，行内 clickable 看到 positionChange()==Zero，
     * 会把「长按释放」误判成一次轻触补发 onClick（导致多选被立即切回、
     * 文件夹被误收起）。阶段二开始时置位，下一次手势按下时清零。
     */
    var suppressClick: Boolean = false

    /** true = 正处于误触抑制窗口，点击处理端应忽略本次点击（非破坏性检查，可多端共用）。 */
    fun clickSuppressed(): Boolean = suppressClick

    /**
     * 拖拽位移增量 → 在同一作用域内逐步交换相邻行。
     * 交换阈值 = 两行中心距（含两行之间的行高），位移补偿 = 目标行「块高」
     * （顶层文件夹展开时其子项行跟随整块移动，块高含子项行高之和）。
     */
    fun onDragDelta(
        dy: Float,
        nodes: List<ShortcutRowNode>,
        move: (node: ShortcutRowNode, dir: Int) -> Unit,
    ) {
        val dk = draggingKey ?: return
        dragOffsetY += dy
        var guard = 0
        while (guard++ < 40 && dragOffsetY != 0f) {
            val i = nodes.indexOfFirst { it.key == dk }
            if (i < 0) return
            val dir = if (dragOffsetY > 0f) 1 else -1
            // 视觉方向上最近的同作用域行 = 作用域内相邻行
            var j = -1
            var k = i + dir
            while (k in nodes.indices) {
                if (nodes[k].scopeId == nodes[i].scopeId) {
                    j = k
                    break
                }
                k += dir
            }
            if (j < 0) return
            val hi = rowHeights[nodes[i].key] ?: return
            val hj = rowHeights[nodes[j].key] ?: return
            var sumBetween = 0f
            if (dir > 0) {
                for (t in i + 1 until j) sumBetween += rowHeights[nodes[t].key] ?: 0f
            } else {
                for (t in j + 1 until i) sumBetween += rowHeights[nodes[t].key] ?: 0f
            }
            val threshold = (hi + hj) / 2f + sumBetween
            if (abs(dragOffsetY) <= threshold) return
            // 目标行块高：顶层文件夹展开时子项行随目标块整体移动
            var blockH = hj
            if (nodes[j].depth == 0) {
                for (t in nodes) {
                    if (t.parentFolderId == nodes[j].item.id) blockH += rowHeights[t.key] ?: 0f
                }
            }
            move(nodes[i], dir)
            dragOffsetY -= dir * blockH
        }
    }
}

@Composable
fun rememberShortcutListInteractionState(): ShortcutListInteractionState =
    remember { ShortcutListInteractionState() }

/**
 * 卡片行长按手势：长按拖拽排序 + 长按不动多选。
 * 抬起/滑动前不消费事件，轻触仍由卡片 onClick 处理、快速滑动仍由列表滚动处理。
 */
@Composable
fun Modifier.shortcutRowGestures(
    state: ShortcutListInteractionState,
    nodeKey: String,
    getNodes: () -> List<ShortcutRowNode>,
    move: (node: ShortcutRowNode, dir: Int) -> Unit,
    onLongPressNoMove: () -> Unit,
    onDragSettled: () -> Unit = {},
): Modifier {
    val view = LocalView.current
    return this.pointerInput(nodeKey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 新手势按下 → 清除上一次长按留下的误触抑制
            state.suppressClick = false
            // ── 阶段一：等长按触发；提前移动交给列表滚动，提前抬起交给卡片点击 ──
            // 移动判定用「离按下点的净位移」：手指静止时的微抖不会累计成移动，
            // 避免长按多选永远触发不了。
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "cancel"
                    if (ch.changedToUpIgnoreConsumed()) return@withTimeoutOrNull "up"
                    if (ch.isConsumed) return@withTimeoutOrNull "cancel"
                    val net = ch.position - down.position
                    if (abs(net.x) > viewConfiguration.touchSlop || abs(net.y) > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull "scroll"
                    }
                }
                "unreachable"
            } ?: "timeout"
            if (outcome != "timeout") return@awaitEachGesture
            // ── 阶段二：长按已触发 → 手势独占 ──
            // 立即置误触抑制，并从现在起吞掉全部事件（含抬起）：
            // 列表滚动不再抢占（拖拽排序不与滚动冲突），行内点击不再补发。
            state.suppressClick = true
            state.draggingKey = nodeKey
            state.dragOffsetY = 0f
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            var dragDist = 0f
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id }
                ev.changes.forEach { it.consume() }
                if (ch == null || ch.changedToUpIgnoreConsumed()) {
                    if (dragDist <= viewConfiguration.touchSlop) onLongPressNoMove() else onDragSettled()
                    break
                }
                // 事件已被吞，必须用 ignoreConsumed 版本才能拿到真实位移
                val dp = ch.positionChangeIgnoreConsumed()
                dragDist += abs(dp.x) + abs(dp.y)
                if (dp != Offset.Zero) state.onDragDelta(dp.y, getNodes(), move)
            }
            state.draggingKey = null
            state.dragOffsetY = 0f
        }
    }
}

/** 拖拽中的行视觉反馈：整块（文件夹+子项）随手势平移，主行轻微放大并抬起。 */
@Composable
fun Modifier.dragRowVisuals(
    state: ShortcutListInteractionState,
    node: ShortcutRowNode,
    nodes: List<ShortcutRowNode>,
): Modifier {
    val dragKey = state.draggingKey
    val draggedFolderId = if (dragKey != null) {
        nodes.firstOrNull { it.key == dragKey }?.let { if (it.depth == 0) it.item.id else null }
    } else null
    val inDragBlock = dragKey == node.key || (draggedFolderId != null && node.parentFolderId == draggedFolderId)
    val isMain = dragKey == node.key
    return this
        .graphicsLayer {
            if (inDragBlock) translationY = state.dragOffsetY
            if (isMain) {
                scaleX = 1.04f
                scaleY = 1.04f
                shadowElevation = 12f
            }
        }
        .zIndex(if (isMain) 1f else if (inDragBlock) 0.5f else 0f)
}

/**
 * 快捷方式卡片（图标 + 标题 + 副标题 + 可选右侧控件）。
 * 选中时卡片外圈显示主题色高亮光晕。
 */
@Composable
fun ShortcutCard(
    item: ShortcutItem,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    summaryOverride: String? = null,
    endActions: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (selected) {
                    val grow = 3.dp.toPx()
                    drawRoundRect(
                        color = primary.copy(alpha = 0.18f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }
            },
    ) {
        BasicComponent(
            title = item.title,
            summary = summaryOverride ?: item.subtitle.ifBlank { "点击编辑快捷方式参数" },
            startAction = { ShortcutIconBox(item) },
            endActions = { endActions?.invoke() },
            onClick = onClick,
        )
    }
}

/** 38dp 圆形底垫图标：矢量图标（Remix/Miuix）或应用图标位图。 */
@Composable
fun ShortcutIconBox(item: ShortcutItem) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vectorIcon = androidx.compose.runtime.remember(item.iconPackage, item.iconGlyph) {
        if (item.iconPackage.isBlank()) {
            if (RemixIconRegistry.isRemixIcon(item.iconGlyph)) {
                RemixIconRegistry.find(item.iconGlyph)
            } else {
                MiuixIconRegistry.find(item.iconGlyph)
            }
        } else null
    }
    val painter = androidx.compose.runtime.remember(
        item.id, item.iconPackage, item.iconGlyph, item.appIconCropMode, item.actionType, item.actionPayload
    ) {
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
}

/** 复制快捷方式：新 UUID、标题加「副本」；文件夹深拷贝（子项全部换新 id）。 */
fun duplicateShortcut(item: ShortcutItem): ShortcutItem {
    val copy = item.copy()
    copy.id = UUID.randomUUID().toString()
    copy.title = item.title + " 副本"
    if (copy.actionType == ShortcutItem.ACTION_FOLDER) {
        val children = copy.parseFolderChildren().map { c ->
            c.copy().also { it.id = UUID.randomUUID().toString() }
        }
        copy.setFolderChildren(children)
    }
    return copy
}

/** 新建空白子快捷方式（聚合文件夹子项默认模板）。 */
fun newBlankChildShortcut(): ShortcutItem = ShortcutItem(
    UUID.randomUUID().toString(),
    "新子快捷方式",
    "",
    ShortcutItem.ACTION_APP,
    "",
    "",
    "remix:sparkling-fill",
    0xFF2A82E4.toInt(),
    true,
    true,
    0xFF2A82E4.toInt(),
    1,
)
