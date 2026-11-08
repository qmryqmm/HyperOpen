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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
 *  - 长按后拖动：绝对坐标平滑拖拽排序（零顿挫、零跳跃）、直接移入目标文件夹、或拖拽移出文件夹。
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

    /** 拖拽开始时被拖拽项在整个列表中的初始基准 Y 坐标 */
    var dragOriginTopY: Float by mutableFloatStateOf(0f)

    /** 手指从长按被拖起那一刻开始累加的总垂直拖动物理距离（绝对物理输入） */
    var totalDragY: Float by mutableFloatStateOf(0f)

    val rowHeights = mutableStateMapOf<String, Float>()
    val rowBounds = mutableStateMapOf<String, Pair<Float, Float>>() // key -> (topY, height)
    var selection: Set<String> by mutableStateOf(emptySet())
    var targetFolderKey: String? by mutableStateOf(null)
    var isDraggingOutOfFolder: Boolean by mutableStateOf(false)

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
     * 辅助纯计算：获取指定 key 在当前节点排布中的相对 Y 坐标（基于当前节点顺序及实测行高）。
     */
    fun getNodeTopY(targetKey: String, nodes: List<ShortcutRowNode>): Float {
        var acc = 0f
        for (n in nodes) {
            if (n.key == targetKey) return acc
            acc += (rowHeights[n.key] ?: 180f) + 10f
        }
        return acc
    }

    /**
     * 拖拽位移增量 → 绝对物理坐标驱动，中心线穿透判定，绝无异步时序跳动与震荡！
     */
    fun onDragDelta(
        dy: Float,
        nodes: List<ShortcutRowNode>,
        move: (node: ShortcutRowNode, dir: Int) -> Unit,
        canDropIntoFolder: Boolean = false,
        onHapticTick: (() -> Unit)? = null,
    ) {
        val dk = draggingKey ?: return
        totalDragY += dy

        val curIdx = nodes.indexOfFirst { it.key == dk }
        if (curIdx < 0) return
        val draggedNode = nodes[curIdx]

        // 被拖拽卡片当前的绝对视觉顶部和中心坐标（严格跟随手指输入，与底层重绘彻底解耦）
        val currentVisualTopY = dragOriginTopY + totalDragY
        val dragH = rowHeights[dk] ?: 180f
        val dragCenterY = currentVisualTopY + dragH / 2f

        // ── 场景 1：拖拽子项（depth == 1）时的移出文件夹检测 ──
        if (canDropIntoFolder && draggedNode.depth == 1) {
            val parentFolderId = draggedNode.parentFolderId
            if (parentFolderId != null) {
                val pTop = getNodeTopY("top:$parentFolderId", nodes)
                val allChildNodes = nodes.filter { it.parentFolderId == parentFolderId }
                val pBottom = if (allChildNodes.isNotEmpty()) {
                    val lastChild = allChildNodes.last()
                    getNodeTopY(lastChild.key, nodes) + (rowHeights[lastChild.key] ?: 180f)
                } else {
                    pTop + (rowHeights["top:$parentFolderId"] ?: 180f)
                }

                // 向上拖出父文件夹头部或向下拖出最末子项底部
                if (dragCenterY < pTop || dragCenterY > pBottom + 20f) {
                    isDraggingOutOfFolder = true
                    targetFolderKey = null
                    return // 处于准备移出到根列表状态，暂不触发内部子项交换
                } else {
                    isDraggingOutOfFolder = false
                }
            }
        } else {
            isDraggingOutOfFolder = false
        }

        // ── 场景 2：拖拽顶层非文件夹卡片时的移入文件夹检测 ──
        if (canDropIntoFolder && draggedNode.depth == 0 && draggedNode.item.actionType != ShortcutItem.ACTION_FOLDER) {
            var foundFolderKey: String? = null
            for (node in nodes) {
                if (node.depth == 0 && node.item.actionType == ShortcutItem.ACTION_FOLDER && node.key != dk) {
                    val fTop = getNodeTopY(node.key, nodes)
                    val childNodes = nodes.filter { it.parentFolderId == node.item.id }
                    val fBottom = if (childNodes.isNotEmpty()) {
                        val last = childNodes.last()
                        getNodeTopY(last.key, nodes) + (rowHeights[last.key] ?: 180f)
                    } else {
                        fTop + (rowHeights[node.key] ?: 180f)
                    }

                    // 落在展开文件夹的整块区域内（含头部与子项区域）
                    if (dragCenterY in fTop..fBottom) {
                        foundFolderKey = node.key
                        break
                    }
                }
            }
            targetFolderKey = foundFolderKey
        } else {
            targetFolderKey = null
        }

        // 悬停在目标文件夹上准备移入时，暂不触发上下交换，方便平稳松手
        if (targetFolderKey != null) return

        // ── 场景 3：同级卡片中心线穿透交换（优雅、自然、零顿挫）──
        val dir = if (dy >= 0f) 1 else -1

        // 视觉方向上最近的同作用域行 = 作用域内相邻行
        var j = -1
        var k = curIdx + dir
        while (k in nodes.indices) {
            if (nodes[k].scopeId == nodes[curIdx].scopeId) {
                j = k
                break
            }
            k += dir
        }
        if (j < 0) return

        val targetTopY = getNodeTopY(nodes[j].key, nodes)
        val targetH = rowHeights[nodes[j].key] ?: 180f
        val targetCenterY = targetTopY + targetH / 2f

        // 当手指拖拽的中心线越过相邻卡片的中心线时，触发交换！
        val shouldSwap = if (dir > 0) {
            dragCenterY >= targetCenterY
        } else {
            dragCenterY <= targetCenterY
        }

        if (shouldSwap) {
            move(nodes[curIdx], dir)
            onHapticTick?.invoke()
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
    canDropIntoFolder: Boolean = false,
    onDragStart: (() -> Unit)? = null,
    onDragSettled: () -> Unit = {},
): Modifier {
    val view = LocalView.current
    return this.pointerInput(nodeKey) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 新手势按下 → 清除上一次长按留下的误触抑制
            state.suppressClick = false
            // ── 阶段一：等长按触发；提前移动交给列表滚动，提前抬起交给卡片点击 ──
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "cancel"
                    if (ch.changedToUpIgnoreConsumed()) return@withTimeoutOrNull "up"
                    val net = ch.position - down.position
                    if (abs(net.x) > viewConfiguration.touchSlop || abs(net.y) > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull "scroll"
                    }
                }
                "unreachable"
            } ?: "timeout"
            if (outcome != "timeout") return@awaitEachGesture
            // ── 阶段二：长按已触发 → 手势独占 ──
            state.suppressClick = true
            state.draggingKey = nodeKey
            val initialNodes = getNodes()
            state.dragOriginTopY = state.getNodeTopY(nodeKey, initialNodes)
            state.totalDragY = 0f
            state.targetFolderKey = null
            state.isDraggingOutOfFolder = false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            var dragDist = 0f
            var dragStarted = false
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    ev.changes.forEach { it.consume() }
                    if (ch == null || ch.changedToUpIgnoreConsumed()) {
                        if (dragDist <= viewConfiguration.touchSlop) {
                            onLongPressNoMove()
                        } else {
                            dragStarted = false
                            onDragSettled()
                        }
                        break
                    }
                    val dp = ch.positionChangeIgnoreConsumed()
                    dragDist += abs(dp.x) + abs(dp.y)
                    if (dragDist > viewConfiguration.touchSlop && !dragStarted) {
                        dragStarted = true
                        onDragStart?.invoke()
                    }
                    if (dp != Offset.Zero) {
                        state.onDragDelta(
                            dy = dp.y,
                            nodes = getNodes(),
                            move = move,
                            canDropIntoFolder = canDropIntoFolder,
                            onHapticTick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        )
                    }
                }
            } finally {
                if (dragStarted) {
                    onDragSettled()
                }
                state.draggingKey = null
                state.totalDragY = 0f
                state.targetFolderKey = null
                state.isDraggingOutOfFolder = false
            }
        }
    }
}

/** 拖拽中的行视觉反馈：整块（文件夹+子项）随手势平移，主行轻微放大并抬起，完美贴合圆角无黑边。 */
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

    // 计算当前项为了绝对精确锚定在当前手指视觉坐标上所需的平移偏移
    val anchorKey = if (draggedFolderId != null && node.parentFolderId == draggedFolderId) dragKey else node.key
    val visualTranslationY = if (anchorKey != null) {
        val anchorSlotY = state.getNodeTopY(anchorKey, nodes)
        val targetAnchorTopY = state.dragOriginTopY + state.totalDragY
        targetAnchorTopY - anchorSlotY
    } else 0f

    return this
        .graphicsLayer {
            if (inDragBlock) {
                translationY = visualTranslationY
            }
            if (isMain) {
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 14f
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
        }
        .zIndex(if (isMain) 1f else if (inDragBlock) 0.5f else 0f)
}

/**
 * 快捷方式卡片（图标 + 标题 + 副标题 + 可选右侧控件）。
 * 选中时卡片外圈显示主题色高亮光晕；作为拖入目标或准备移出时显示呼吸边框与提示。
 */
@Composable
fun ShortcutCard(
    item: ShortcutItem,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isDropTarget: Boolean = false,
    isDraggingOut: Boolean = false,
    summaryOverride: String? = null,
    endActions: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (isDropTarget || isDraggingOut) {
                    val grow = 3.dp.toPx()
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                    drawRoundRect(
                        color = primary.copy(alpha = 0.14f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                } else if (selected) {
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
            summary = when {
                isDropTarget -> "松手直接放入此文件夹"
                isDraggingOut -> "松手移出到快捷列表"
                summaryOverride != null -> summaryOverride
                else -> item.subtitle.ifBlank { "点击编辑快捷方式参数" }
            },
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
