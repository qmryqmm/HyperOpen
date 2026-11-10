package io.github.qmryqmm.hyperopen.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.key
import androidx.compose.ui.viewinterop.AndroidView
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.IconPainter
import io.github.qmryqmm.hyperopen.ui.LocalHyperOpenConfig
import io.github.qmryqmm.hyperopen.ui.MiuixIconRegistry
import io.github.qmryqmm.hyperopen.ui.RemixIconRegistry
import io.github.qmryqmm.hyperopen.view.CircularBubbleView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

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

    /** 手指从长按被拖起那一刻开始累加的总垂直拖动物理距离（绝对物理输入） */
    var totalDragY: Float by mutableFloatStateOf(0f)

    /** 拖拽开始时被拖拽项的窗口坐标 Y（positionInWindow）。
     *  与内容坐标不同：窗口坐标天然反映内容收缩 + 滚动 clamp 双重位移。
     *  叠起让上方卡片收缩、列表滚动位置被钳制（尤其滚到底部时），卡片在窗口里的
     *  位置会变、手指却不动——用窗口坐标锚定，整叠才能始终钉在手指下方。 */
    var dragOriginWindowY: Float by mutableFloatStateOf(0f)

    val rowHeights = mutableStateMapOf<String, Float>()
    /** 各行完整（收缩前）实测高度，key -> 高度px。由 ShortcutRowEntry 在收缩
     *  Modifier.layout 内部记录，不随叠起收缩变化——rowHeightNow 用它把携带行
     *  折算成当前帧真实行高（rowHeights 由 onGloballyPositioned 记录、晚一帧）。 */
    val fullRowHeights = mutableStateMapOf<String, Float>()
    val rowBounds = mutableStateMapOf<String, Pair<Float, Float>>() // key -> (topY, height)
    /** 各行窗口坐标 Y（positionInWindow）。与内容坐标（rowBounds/topYOfSlot）不同：
     *  窗口坐标天然反映「上方卡片收缩导致内容变短」+「滚到底部时 LazyColumn 滚动钳制」
     *  双重位移。拖拽锚定必须用窗口坐标——手指与滚动 clamp 都活在窗口坐标系里。 */
    val rowWindowY = mutableStateMapOf<String, Float>()

    /** 拖拽窗口锚点「内容原点窗口 Y」提供器：返回 getNodeTopY==0 槽位（首个节点顶）的窗口 Y。
     *  由宿主页面用 layoutInfo 槽位几何量计算——槽位偏移不含 animateItem 的换位动画位移
     *  （onGloballyPositioned 的 rowWindowY 含动画：被换入首槽的卡在动画期间 rowWindowY
     *  停留旧位，min 锚点被污染一整行 → 拖拽第一项下行时主卡被短暂回拉原位）；且交换只换
     *  内容不移动槽位 → 换算出的原点交换稳定。滚动/滚动钳制经 layoutInfo 随帧更新（慢变化
     *  滞后一帧无妨）。未设置（编辑页，Column 无 animateItem、rowWindowY 恒为槽位值）时
     *  回退全部子项 rowWindowY 最小值。 */
    var anchorOriginWindowYProvider: (() -> Float?)? = null

    /** 相邻行之间的间距（density 感知像素值，由 shortcutRowGestures 在组合期写入）。 */
    var rowSpacingPx: Float by mutableFloatStateOf(10f)
    var selection: Set<String> by mutableStateOf(emptySet())
    var targetFolderKey: String? by mutableStateOf(null)
    var isDraggingOutOfFolder: Boolean by mutableStateOf(false)

    // ── 多选组合拖拽（叠起/散开）状态 ──
    /** 组合拖拽进行中：其余选中卡片被“拾起”叠放到主卡下方随行 */
    var groupDragActive: Boolean by mutableStateOf(false)

    /** 松手散开阶段（叠放锚点已冻结到 groupScatterAnchorY） */
    var groupScattering: Boolean by mutableStateOf(false)

    /** 散开动画起始时冻结的叠放锚点 Y（主卡松手瞬间的视觉顶部） */
    var groupScatterAnchorY: Float by mutableFloatStateOf(0f)

    /** 被携带的行 key（不含主卡）。散开阶段会临时并入主卡，用于主卡的散开位移。 */
    var carriedKeys: Set<String> by mutableStateOf(emptySet())

    /** 行高被收缩的行 key（= 拾起时的携带集，始终不含主卡）。
     *  主卡在拖拽期间走 inDragBlock 分支、行高从未收缩，散开时只应恢复位移、
     *  不应恢复行高——否则松手瞬间主卡行高从满高塌缩到 0，下方卡片整体上跳一整行，
     *  且散开偏移冻结值（getNodeTopY）整体偏差一个主卡行高。 */
    var shrunkKeys: Set<String> by mutableStateOf(emptySet())

    /** 携带行 → 叠放序号（1 起，决定下沉偏移与渲染层级） */
    val stackIndices = mutableStateMapOf<String, Int>()

    /** 拾起瞬间冻结的携带卡基准叠放偏移（主卡原位 − 自身槽位 + 阶梯下沉）。
     *  拖拽期间只与 totalDragY 相加，中途交换重排不会引起携带卡视觉跳动。 */
    val stackBaseOffsets = mutableStateMapOf<String, Float>()

    /** 松手瞬间冻结的散开起始偏移（= 基准偏移 + totalDragY，与拖拽段末值无缝衔接）。 */
    val stackScatterOffsets = mutableStateMapOf<String, Float>()

    /** 叠放进度：0=各行在自身槽位，1=完全叠到主卡下（叠起与散开共用同一进度，保证丝滑衔接） */
    val stackProgress = Animatable(0f)

    /** 相邻叠层露出高度（density 像素，shortcutRowGestures 组合期写入）——形成重叠阶梯感 */
    var stackGapPx: Float by mutableFloatStateOf(0f)

    /** 携带卡横向扇形错位（density 像素/层，shortcutRowGestures 组合期写入）：
     *  让下层卡从主卡右侧露出一条竖边，叠起状态一眼可辨。 */
    var stackFanPx: Float by mutableFloatStateOf(0f)

    /** 叠起/散开动画的驱动协程（并发于手势循环之后，不阻塞手势收尾） */
    var stackAnimJob: Job? = null

    /** 组合拖拽世代令牌：新一轮 groupDrag 递增，用于取消上一轮尚未收敛的散开动画 */
    var stackGeneration: Int by mutableIntStateOf(0)

    val multiSelect: Boolean get() = selection.isNotEmpty()

    /** 主卡当前视觉顶部（窗口坐标）。 = 拾起时的窗口锚点 + 手指累计位移。
     *  必须用窗口坐标而非内容坐标：叠起让上方携带卡收缩 → 内容变短，且滚到底部时
     *  LazyColumn 会钳制滚动位置（无法继续滚过已缩短的内容），卡片在「窗口」里被
     *  整体上移，但内容坐标看不到这次位移。窗口坐标天然反映「内容收缩 + 滚动钳制」
     *  双重位移，整叠才能始终钉在手指下方。 */
    fun mainVisualTopY(): Float =
        dragOriginWindowY + totalDragY

    fun toggleSelection(key: String) {
        selection = if (key in selection) selection - key else selection + key
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /**
     * 行当前实际布局高度。携带行在叠起/散开期间按 stackProgress 实时折算——
     * rowHeights 由 onGloballyPositioned 记录、晚一帧，直接用会让锚定坐标逐帧抖动；
     * fullRowHeights 是收缩前完整高度，与 (1−progress) 相乘即当前帧真实行高，零滞后。
     */
    fun rowHeightNow(key: String): Float {
        val measured = rowHeights[key] ?: return 180f
        if ((groupDragActive || groupScattering) && key in shrunkKeys) {
            val full = fullRowHeights[key] ?: measured
            return full * (1f - stackProgress.value)
        }
        return measured
    }

    /**
     * 锚定坐标系用行高：与 rowHeightNow 唯一差别是非携带行退回 rowHeights 实测值。
     * 原因：非携带行的补位走 LazyColumn animateItem 位置动画（渐进演进），
     * 若 getNodeTopY 立刻采用数据层目标行高，锚定坐标会先于真实布局跳到目标位置，
     * 叠起中的整叠被“挤开错位”（用户实测：补位导致叠起卡片组不在手指下方）。
     * rowHeights 由 onGloballyPositioned 记录，与 animateItem 的渐进动画同帧率演进，
     * 锚定坐标系与可见布局保持一致。
     */
    fun anchorRowHeight(key: String): Float {
        val measured = rowHeights[key] ?: return 180f
        if ((groupDragActive || groupScattering) && key in shrunkKeys) {
            val full = fullRowHeights[key] ?: measured
            return full * (1f - stackProgress.value)
        }
        return measured
    }

    /** 按槽位序号直接累加物理 Y（与 getNodeTopY 同规则：行高 + 行距）。 */
    fun topYOfSlot(slot: Int, nodes: List<ShortcutRowNode>): Float {
        var acc = 0f
        val end = slot.coerceIn(0, nodes.size)
        for (i in 0 until end) {
            acc += anchorRowHeight(nodes[i].key) + rowSpacingPx
        }
        return acc
    }

    /** 叠起末段层距压缩系数（p>0.9 从 1 收拢到 0.85）。
     *  手势松手冻结散开偏移与 dragRowVisuals 实时偏移必须共用同一系数，
     *  否则松手瞬间携带卡会跳 idx×gap×(1−comp)。
     *  下限 0.85（16dp→13.6dp）：压得更狠时层间露边被主卡阴影盖住，看不出叠起状态。 */
    fun stackGapComp(): Float =
        if (stackProgress.value > 0.9f) 0.85f + 0.15f * (1f - stackProgress.value) / 0.1f else 1f

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
     * 用 rowHeightNow 而非 rowHeights：携带行叠起收缩期间行高零滞后折算，
     * 槽位坐标即当前帧真实布局位置——任何额外补偿都会双重扣除导致乱跳。
     */
    fun getNodeTopY(targetKey: String, nodes: List<ShortcutRowNode>): Float {
        var acc = 0f
        for (n in nodes) {
            if (n.key == targetKey) return acc
            acc += anchorRowHeight(n.key) + rowSpacingPx
        }
        return acc
    }

    /** 节点当前窗口坐标 Y。
     *  方向 A 拆解：窗口位置 = 内容原点窗口 Y + 内容坐标偏移。
     *   - 内容原点窗口 Y（锚点）取「首个可见行」rowWindowY（firstVisibleNodeKeyProvider 提供）：
     *     只随「滚动/滚动钳制」变化（慢变化，onGloballyPositioned 滞后一帧无妨——钳制是
     *     随叠起动画的平滑渐变，不产生闪烁）。锚首个可见行而非 node0：node0 滚出屏幕后
     *     rowWindowY 被冻结、不再反映滚动钳制 → 滚到底部叠起/折叠文件夹时整叠漂离手指。
     *   - 内容偏移 getNodeTopY：纯同步累加（nodes 顺序即当前数据），交换当帧立刻反映新槽位，
     *     与布局位移「一增一减」同帧抵消——彻底消除交换闪烁。
     *  两者各管各的坐标系，互不拖累：快变化（交换）走同步内容坐标，慢变化（滚动钳制）走窗口锚点。 */
    fun windowTopOf(key: String, nodes: List<ShortcutRowNode>): Float {
        // 首选：宿主提供的「内容原点窗口 Y」（主列表——槽位几何量，不受 animateItem
        // 换位动画污染、交换时稳定）；回退（编辑页）：全部子项 rowWindowY 最小值
        // （Column 无 animateItem，rowWindowY 恒为槽位值，min = 首槽窗口 Y，交换=位置
        // 互换值集合不变 → min 稳定）。
        val minWin = anchorOriginWindowYProvider?.invoke()
            ?: rowWindowY.values.minOrNull()
            ?: 0f
        return minWin + getNodeTopY(key, nodes)
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
        // 注意：totalDragY 已在 shortcutRowGestures 手势循环内统一累加（组合拖拽也需要累计位移），
        // 此处不再重复累加，否则非组合拖拽位移会翻倍。

        val curIdx = nodes.indexOfFirst { it.key == dk }
        if (curIdx < 0) return
        val draggedNode = nodes[curIdx]

        // 被拖拽卡片当前的绝对视觉顶部和中心坐标（严格跟随手指输入，与底层重绘彻底解耦）
        val currentVisualTopY = mainVisualTopY()
        val dragH = rowHeights[dk] ?: 180f
        val dragCenterY = currentVisualTopY + dragH / 2f

        // ── 场景 1：拖拽子项（depth == 1）时的移出文件夹检测 ──
        if (canDropIntoFolder && draggedNode.depth == 1) {
            val parentFolderId = draggedNode.parentFolderId
            if (parentFolderId != null) {
                val pTop = windowTopOf("top:$parentFolderId", nodes)
                val allChildNodes = nodes.filter { it.parentFolderId == parentFolderId }
                val pBottom = if (allChildNodes.isNotEmpty()) {
                    val lastChild = allChildNodes.last()
                    windowTopOf(lastChild.key, nodes) + (rowHeights[lastChild.key] ?: 180f)
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
                    val fTop = windowTopOf(node.key, nodes)
                    val childNodes = nodes.filter { it.parentFolderId == node.item.id }
                    val fBottom = if (childNodes.isNotEmpty()) {
                        val last = childNodes.last()
                        windowTopOf(last.key, nodes) + (rowHeights[last.key] ?: 180f)
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
        // 组合拖拽中：跳过其他被叠起的选中行——交换只发生在主卡与非选中卡之间，
        // 整叠作为一块与外界换位，选中卡之间相对顺序冻结、互不交换。
        val dir = if (dy >= 0f) 1 else -1

        // 视觉方向上最近的同作用域行 = 作用域内相邻行
        var j = -1
        var k = curIdx + dir
        while (k in nodes.indices) {
            val candidate = nodes[k]
            if (candidate.scopeId == nodes[curIdx].scopeId &&
                candidate.key !in carriedKeys
            ) {
                j = k
                break
            }
            k += dir
        }
        if (j < 0) return

        val targetTopY = windowTopOf(nodes[j].key, nodes)
        val targetH = rowHeights[nodes[j].key] ?: 180f
        val targetCenterY = targetTopY + targetH / 2f

        // 当手指拖拽的中心线越过相邻卡片的中心线时，触发交换！
        val shouldSwap = if (dir > 0) {
            dragCenterY >= targetCenterY
        } else {
            dragCenterY <= targetCenterY
        }

        if (shouldSwap) {
            // 交换后 LazyColumn 同帧重排：布局槽位 Y 与 visualTranslationY 里的「−槽位Y」
            // 一增一减相互抵消，视觉位置天然连续（dragOriginWindowY 保持拾起时的值）。
            // 切勿随交换改写 dragOriginWindowY——那会与布局位移双重补偿，卡片被反向拉跳。
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
    /** 组合拖拽松手时把整叠新顺序写回数据层（主卡 key + 携带卡按叠中顺序）。 */
    onGroupReorder: ((mainKey: String, carriedKeys: List<String>) -> Unit)? = null,
    /** 组合拖拽叠起开始时收起选中集内的展开态文件夹（散开完成后由 onGroupExpand 恢复）。 */
    onGroupCollapse: (() -> Unit)? = null,
    /** 组合拖拽散开完成后恢复叠起时被自动收起的文件夹。 */
    onGroupExpand: () -> Unit = {},
    rowSpacing: Dp = 10.dp,
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    state.rowSpacingPx = with(density) { rowSpacing.toPx() }
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
            // 拾起锚点用窗口坐标（方向 A 的窗口锚点 = 首行窗口Y + 内容偏移）：
            // 窗口锚点只随「滚动/滚动钳制」慢变化；内容偏移由 getNodeTopY 同步累加，
            // 交换当帧即反映新槽位。二者分离：快变化（交换）同步抵消、慢变化（钳制）窗口吸收。
            state.dragOriginWindowY = state.windowTopOf(nodeKey, initialNodes)
            state.totalDragY = 0f
            state.targetFolderKey = null
            state.isDraggingOutOfFolder = false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            // ── 多选组合拖拽：若长按的行在多选集内，则其余选中行被一起“拾起”叠到主卡下 ──
            // 仅当选中集与长按行同层（同一排序作用域 scopeId）时才组合叠起；
            // 跨层选中（根目录项与文件夹子项混选）不叠起，仅拖动手指按中的卡片。
            val pressedNode = initialNodes.firstOrNull { it.key == nodeKey }
            val groupDrag = state.multiSelect && nodeKey in state.selection && pressedNode != null &&
                initialNodes.filter { it.key in state.selection }.all { it.scopeId == pressedNode.scopeId }
            // 判断当前多选堆叠中是否包含文件夹
            val groupContainsFolder = groupDrag && initialNodes.any {
                it.key in state.selection && it.item.actionType == ShortcutItem.ACTION_FOLDER
            }
            if (groupDrag) {
                // 上一轮散开动画若未收敛，立即取消并冻结当前进度，作为本轮叠起起点（丝滑续接）
                state.stackAnimJob?.cancel()
                state.stackAnimJob = null
                state.stackGeneration++
                state.stackIndices.clear()
                state.stackBaseOffsets.clear()
                state.stackScatterOffsets.clear()
                state.carriedKeys = state.selection - nodeKey
                state.shrunkKeys = state.carriedKeys
                state.stackGapPx = with(density) { 16.dp.toPx() }
                // 每层右错 6dp：主卡 scale1.02 放大后右缘外扩 ~3dp，6dp 错位保证
                // 下层卡右边缘条带在主卡放大阴影之外清晰可见。
                state.stackFanPx = with(density) { 6.dp.toPx() }
                // 叠中序号按拾起时的视觉槽位顺序分配（最靠近主卡的卡 = idx 1）：
                // 直接迭代 selection Set 是「点选顺序」，扇形阶梯会与视觉顺序错乱，
                // 看起来像整叠被挤开错位；松手 reorder 也按此顺序落位。
                val orderedCarried = state.carriedKeys.sortedBy { key -> state.getNodeTopY(key, initialNodes) }
                var idx = 1
                orderedCarried.forEach { key ->
                    state.stackIndices[key] = idx
                    // 冻结拾起瞬间自身槽位 Y（满高布局）：携带卡视觉路径 =
                    // 从拾起位置平滑滑到主卡下方阶梯（纯 lerp），与自身槽位此后的
                    // 漂移（收缩/补位/交换推动）完全解耦，不被补位过程挤开。
                    state.stackBaseOffsets[key] = state.getNodeTopY(key, initialNodes)
                    idx++
                }
                state.groupScattering = false
                state.groupDragActive = true
                // 多选含文件夹：叠起开始时收起选中集内的全部展开态文件夹
                // （onGroupCollapse 透传到 treeState；松手散开完成后恢复）。
                // 注意：收起后切勿重取 dragOriginWindowY——冻结原点机制下主卡渲染位置
                // = dragOriginWindowY + totalDragY，收起引起的槽位上移已被
                // 「−anchorSlotY」的反向变化抵消，卡片视觉钉在手指按下处；
                // 若按收起后槽位重取锚点，卡片会在拾起瞬间上跳、脱离手指。
                onGroupCollapse?.invoke()
                // 拾起即叠起：长按瞬间就开始把其余选中卡片叠到主卡下（并发驱动，不阻塞手势循环）。
                // 先叠起、后拖动——长按保持期间动画有时间完成，拖动时整叠已成形（不会“有概率不触发”）。
                state.stackAnimJob = scope.launch {
                    state.stackProgress.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    )
                }
            }

            var dragDist = 0f
            var dragStarted = false
            var settledCalled = false
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    ev.changes.forEach { it.consume() }
                    if (ch == null || ch.changedToUpIgnoreConsumed()) {
                        if (dragDist <= viewConfiguration.touchSlop) {
                            onLongPressNoMove()
                            // 未拖动的长按：不进入散开动画，取消动画协程并复位叠放状态
                            if (groupDrag) {
                                state.stackAnimJob?.cancel()
                                state.stackAnimJob = null
                                state.groupDragActive = false
                                state.groupScattering = false
                                state.carriedKeys = emptySet()
                                state.shrunkKeys = emptySet()
                                state.stackIndices.clear()
                                state.stackBaseOffsets.clear()
                                state.stackScatterOffsets.clear()
                                onGroupExpand()
                                scope.launch { state.stackProgress.snapTo(0f) }
                            }
                        } else {
                            val droppingIntoFolder = state.targetFolderKey != null
                            val droppingOutOfFolder = state.isDraggingOutOfFolder
                            if (groupDrag) {
                                if (!droppingIntoFolder && !droppingOutOfFolder) {
                                    // ── 松手：把整叠的当前顺序写回数据层（散开后按新顺序落位，不回原位）──
                                    // 主卡已通过中途交换重排到新槽位；携带卡按叠中相对顺序紧随主卡插入。
                                    onGroupReorder?.invoke(nodeKey, state.carriedKeys.toList())
                                    // 冻结散开起始偏移：按重排后的节点序计算「松手视觉位置 − 新槽位」，
                                    // 散开动画从这无缝起步、精确滑向新槽位。
                                    val settledNodes = getNodes()
                                    val mainVisualTopY = state.mainVisualTopY()
                                    state.carriedKeys.forEach { key ->
                                        val idx = state.stackIndices[key] ?: 0
                                        // 与 dragRowVisuals 实时偏移同公式（含 stackGapComp）：
                                        // 压缩系数不一致时松手瞬间携带卡跳 idx×gap×(1−comp)。
                                        state.stackScatterOffsets[key] =
                                            (mainVisualTopY - state.windowTopOf(key, settledNodes)) +
                                                idx * state.stackGapPx * state.stackGapComp()
                                    }
                                    // 主卡也参与散开：从松手视觉位置平滑滑向其新槽位
                                    state.stackScatterOffsets[nodeKey] = mainVisualTopY -
                                        state.windowTopOf(nodeKey, settledNodes)
                                    // 主卡并入携带集参与散开位移，但 shrunkKeys 不含主卡：
                                    // 拖拽期间主卡行高从未收缩，散开时不做行高恢复，
                                    // 否则松手瞬间行高塌缩 0 → 下方卡片整体上跳一整行。
                                    state.carriedKeys = state.carriedKeys + nodeKey
                                    state.groupScattering = true
                                    state.groupScatterAnchorY = mainVisualTopY
                                } else {
                                    // 移入或移出文件夹场景：无需执行顺序重排与散开动画，立即复位叠放状态
                                    state.stackAnimJob?.cancel()
                                    state.stackAnimJob = null
                                    state.groupDragActive = false
                                    state.groupScattering = false
                                    state.carriedKeys = emptySet()
                                    state.shrunkKeys = emptySet()
                                    state.stackIndices.clear()
                                    state.stackBaseOffsets.clear()
                                    state.stackScatterOffsets.clear()
                                    onGroupExpand()
                                    scope.launch { state.stackProgress.snapTo(0f) }
                                }
                            }
                            if (dragStarted) {
                                settledCalled = true
                                onDragSettled()
                            }
                        }
                        break
                    }
                    val dp = ch.positionChangeIgnoreConsumed()
                    dragDist += abs(dp.x) + abs(dp.y)
                    if (dragDist > viewConfiguration.touchSlop && !dragStarted) {
                        dragStarted = true
                        // 组合拖拽不做“拖文件夹自动收起”：被选中的子项要参与叠起，
                        // 收起会让子项行从节点树消失、无法渲染叠放动画。
                        if (!groupDrag) onDragStart?.invoke()
                    }
                    if (dp != Offset.Zero) {
                        // 手指位移始终累计（组合拖拽靠它驱动整叠移动与锚点换算）
                        state.totalDragY += dp.y
                        // 组合拖拽也调用 onDragDelta：主卡照常与相邻卡交换位置（重排底层顺序）。
                        // 当叠起的卡片包含文件夹时，不触发添加进文件夹操作；无文件夹时允许拖入文件夹。
                        state.onDragDelta(
                            dy = dp.y,
                            nodes = getNodes(),
                            move = move,
                            canDropIntoFolder = canDropIntoFolder && (!groupDrag || !groupContainsFolder),
                            onHapticTick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        )
                    }
                }
            } finally {
                if (dragStarted && !settledCalled) {
                    settledCalled = true
                    onDragSettled()
                }
                state.draggingKey = null
                state.totalDragY = 0f
                state.targetFolderKey = null
                state.isDraggingOutOfFolder = false
            }
            if (groupDrag && dragStarted) {
                // 叠起/散开动画由独立协程并发驱动（不阻塞 pointerInput 协程收尾）：
                // 从冻结的散开末偏移继续 → 弹回 0（携带卡滑回各自槽位）→ 清理状态。
                val gen = state.stackGeneration
                state.stackAnimJob?.cancel()
                state.stackAnimJob = scope.launch {
                    try {
                        state.stackProgress.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    } finally {
                        if (gen == state.stackGeneration) {
                            state.groupDragActive = false
                            state.groupScattering = false
                            state.carriedKeys = emptySet()
                            state.shrunkKeys = emptySet()
                            state.stackIndices.clear()
                            state.stackBaseOffsets.clear()
                            state.stackScatterOffsets.clear()
                            // 散开完成：恢复叠起时被自动收起的文件夹
                            onGroupExpand()
                        }
                    }
                }
            } else if (groupDrag) {
                state.groupDragActive = false
                state.groupScattering = false
                state.carriedKeys = emptySet()
                state.shrunkKeys = emptySet()
                state.stackIndices.clear()
                state.stackBaseOffsets.clear()
                state.stackScatterOffsets.clear()
                onGroupExpand()
            }
        }
    }
}

/** 拖拽中的行视觉反馈：整块（文件夹+子项）随手势平移，主行轻微放大并抬起，完美贴合圆角无黑边。
 *  `enableSwapAnimation` = 非拖拽行的槽位补位动画（等价 LazyColumn animateItem，
 *  供无 LazyItemScope 的普通 Column 列表使用，与快捷项目列表的交换动画观感对齐）。
 */
@Composable
fun Modifier.dragRowVisuals(
    state: ShortcutListInteractionState,
    node: ShortcutRowNode,
    nodes: List<ShortcutRowNode>,
    enableSwapAnimation: Boolean = false,
): Modifier {
    val dragKey = state.draggingKey
    val draggedFolderId = if (dragKey != null) {
        nodes.firstOrNull { it.key == dragKey }?.let { if (it.depth == 0) it.item.id else null }
    } else null
    // 跨层多选（子项+顶层项混选）不触发组合拖拽 → 拖拽块吸附逻辑保持原样：
    // 拖文件夹时其全部子项随块移动（用户确认的预期行为）。
    val inDragBlock = dragKey == node.key || (draggedFolderId != null && node.parentFolderId == draggedFolderId)
    val isMain = dragKey == node.key

    // ── 多选组合拖拽：本行是否被“拾起” ──
    // groupDrag 仅在同层多选时激活（shortcutRowGestures 已按 scopeId 校验）；
    // 拖拽中主卡走 inDragBlock 分支（实时跟随手指），其余选中行走 carried 分支。
    // 散开阶段 draggingKey 已清空，主卡也落入 carried 分支，整叠一起滑回。
    val carried = state.groupDragActive && !inDragBlock && node.key in state.carriedKeys
    val stackIdx = if (carried) (state.stackIndices[node.key] ?: 0) else 0

    // 计算当前项为了绝对精确锚定在当前手指视觉坐标上所需的平移偏移。
    // getNodeTopY 经 rowHeightNow 已是当前帧真实布局槽位（携带行收缩实时折算），
    // 无需任何额外补偿——额外补偿即双重扣除，整叠漂移+交换乱跳。
    val anchorKey = if (draggedFolderId != null && node.parentFolderId == draggedFolderId) dragKey else node.key
    val visualTranslationY = if (anchorKey != null) {
        // 锚定用窗口坐标：targetAnchorTopY（mainVisualTopY）已是窗口坐标，
        // anchorSlotY 也必须用窗口坐标（windowTopOf），坐标系一致才不漂移。
        val anchorSlotY = state.windowTopOf(anchorKey, nodes)
        val targetAnchorTopY = state.mainVisualTopY()
        targetAnchorTopY - anchorSlotY
    } else 0f

    // 携带卡片叠放偏移（绝对丝滑）：
    //  - 拖拽中：实时计算「主卡当前视觉 top − 自身槽位 Y + 阶梯下沉」——主卡交换重排、
    //    其余行行高收缩补位时布局与偏移互补抵消，整叠始终精确贴住主卡视觉位置下方；
    //  - 散开中：松手瞬间冻结的起始偏移（按重排后的新槽位计算）→ 随 stackProgress 弹回 0，
    //    卡片滑向新顺序的槽位（散开落位 = 新位置，而非原位置）。
    // stackProgress 从 0 涨到 1 时与各卡原位无缝衔接（progress=0 恒为自身槽位）。
    // 叠起末段（progress>0.9）额外收拢层间距（最低 ×0.75），让卡叠紧凑但层间
    // 仍露出一条稳定条带（重叠可见）；state.stackGapComp() 是同一函数——松手冻结
    // 散开偏移时用同一系数，保证拖拽→散开帧间无缝、无跳变。
    val stackGapCompression = state.stackGapComp()
    val carryOffset = if (carried) {
        val base = if (state.groupScattering) {
            state.stackScatterOffsets[node.key] ?: 0f
        } else {
            // 拖拽中：钉在主卡视觉位置下方的阶梯处。
            // carryOffset 是「实时布局槽位 → 目标视觉位置」的修正量：
            //   目标 = 主卡视觉 top（窗口坐标）+ idx×层距×压缩
            //   起点 = 自身实时槽位（窗口坐标 windowTopOf）
            // 槽位被补位动画 / 滚动钳制推动时，修正量同帧反向变化、视觉位置不变——
            // 整叠只跟主卡位移走，绝不被补位挤开。
            val mainVisualTopY = state.mainVisualTopY()
            val ownSlotY = state.windowTopOf(node.key, nodes)
            (mainVisualTopY - ownSlotY) + stackIdx * state.stackGapPx * stackGapCompression
        }
        base * state.stackProgress.value
    } else 0f

    // ── 槽位补位动画（无闪烁）：槽位变化当帧在组合期直接写入偏移（与布局同步渲染在旧位置），
    //    随后弹簧动画从 delta 滑回 0。绝不能在 LaunchedEffect 里才 snapTo——那会晚一帧，
    //    卡片先以偏移 0 渲染在新槽位上（肉眼可见的闪烁）再跳回旧位置。 ──
    val slotIndex = if (enableSwapAnimation) nodes.indexOfFirst { it.key == node.key } else -1
    val swapOffset = remember(node.key) { mutableFloatStateOf(0f) }
    val slotMemo = remember(node.key) { intArrayOf(-1) }
    val animVersion = remember(node.key) { mutableIntStateOf(0) }

    val prevSlot = slotMemo[0]
    if (slotIndex >= 0 && prevSlot != slotIndex) {
        slotMemo[0] = slotIndex
        if (prevSlot >= 0 && state.draggingKey != node.key) {
            // 非拖拽主行的槽位变化：立即叠加补位偏移（动画进行中再次交换则从剩余偏移继续）
            val delta = state.topYOfSlot(prevSlot, nodes) - state.topYOfSlot(slotIndex, nodes)
            if (abs(delta) > 0.5f) {
                swapOffset.floatValue += delta
                animVersion.intValue++
            }
        }
    }

    LaunchedEffect(animVersion.intValue) {
        if (animVersion.intValue == 0) return@LaunchedEffect
        val start = swapOffset.floatValue
        if (abs(start) < 0.5f) return@LaunchedEffect
        animate(
            initialValue = start,
            targetValue = 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { v, _ -> swapOffset.floatValue = v }
    }

    return this
        .graphicsLayer {
            if (carried) {
                // 层叠厚度感：层距收拢后仍露出稳定条带（「底下的卡片露一点边」）；
                // 叠中序号越靠下的卡：下沉越多 + 右错越多（扇形阶梯）+ 略小 + 阴影更深，
                // 侧面轮廓形成可辨识的「一叠卡片」。叠起形态拖拽全程恒定、不被交换改变。
                translationY = carryOffset
                val p = state.stackProgress.value
                // 横向扇形：每层右错 3dp×p，让下层卡从主卡右侧露出一条竖边
                // （纵向露边会被主卡阴影盖住，横向露边在卡片圆角外清晰可见）。
                translationX = state.stackFanPx * stackIdx * p
                // scale 渐变保守（每层仅 0.5%），避免缩到看不出「还是那张卡」；
                // 阴影每层加深，圆角外沿与主卡阴影分离出可见层次。
                val scale = 1f - 0.005f * stackIdx * p
                scaleX = scale
                scaleY = scale
                shadowElevation = 10f * p + stackIdx * 2f * p
                shape = RoundedCornerShape(16.dp)
                clip = false
                alpha = 1f
            } else if (inDragBlock) {
                translationY = visualTranslationY
            } else if (swapOffset.floatValue != 0f) {
                translationY = swapOffset.floatValue
            }
            if (isMain) {
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 14f
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
        }
        .then(
            if (carried && node.key in state.shrunkKeys) {
                // 拾起后行高随叠起进度收缩到 0 → 布局层面闭合原空位，
                // 其余卡片由 animateItem 平滑上移补位；散开时行高随进度恢复，卡片滑回原槽。
                // 主卡不参与收缩（shrunkKeys 不含主卡）：拖拽期间主卡走 inDragBlock、
                // 行高从未收缩，散开时套上收缩 layout 会让行高塌缩 0、下方卡片整体跳一整行。
                // 收缩=full×(1−p)：行距交给外层 spacedBy（不随收缩变化），使 getNodeTopY
                // 的累加模型（rowHeightNow+rowSpacingPx）与真实布局逐帧精确一致。
                // fullRowHeights 在测量结果上记录完整高度，供 rowHeightNow 零滞后折算。
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    state.fullRowHeights[node.key] = placeable.height.toFloat()
                    val shrink = placeable.height * (1f - state.stackProgress.value)
                    val h = shrink.roundToInt().coerceIn(0, placeable.height)
                    layout(placeable.width, h) {
                        placeable.placeRelative(0, 0)
                    }
                }
            } else {
                Modifier
            },
        )
        .zIndex(
            if (carried) 0.9f - stackIdx * 0.01f
            else if (isMain) 1f
            else if (inDragBlock) 0.5f
            else 0f,
        )
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

/** 38dp 快捷方式卡片图标：与实际唤出的悬浮球气泡 (CircularBubbleView) 同源渲染。 */
@Composable
fun ShortcutIconBox(item: ShortcutItem) {
    ShortcutBubbleIcon(
        item = item,
        size = 38.dp,
    )
}

/**
 * 快捷方式气泡图标：直接复用真实 SystemUI / 交互预览同一渲染实现 [CircularBubbleView]，
 * 不再单独编写绘制方法——底衬光学层、图标解析裁切、内边距、圆角裁剪、留白 0% 关闭气泡等
 * 一切渲染逻辑与实机 100% 同源，任何渲染改动自动同步。
 * 窗口级毛玻璃在应用内禁用（与交互预览一致），避免列表逐图标触发 SurfaceFlinger 全屏模糊。
 */
@Composable
fun ShortcutBubbleIcon(
    item: ShortcutItem,
    size: Dp,
    modifier: Modifier = Modifier,
    config: HyperOpenConfig? = null,
    isSelected: Boolean = false,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val liveConfig = LocalHyperOpenConfig.current
    val cfg = config ?: liveConfig ?: remember(ctx) { HyperOpenConfig.loadFromApp(ctx) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(ctx, cfg.materialStyle)

    // 图标相关配置变化时整体重建视图（图标解析缓存由 IconGlyphFactory 提供）；
    // 其余参数（模糊/压暗/留白/圆角/透明度/选中）经 update 实时热同步，与真实容器的热更新链一致
    val viewKey = listOf(
        item.id, item.iconPackage, item.iconGlyph, item.actionType, item.actionPayload,
        item.appIconCropMode, item.glyphPadEnabled, item.glyphPadColor, item.glyphPadMode,
        item.tintColor, sizePx, resolvedStyle,
        cfg.appIconCropMode, cfg.glyphPadEnabled, cfg.glyphPadColor, cfg.glyphPadMode,
        cfg.iconGlowEffect,
    ).joinToString("|")

    key(viewKey) {
        AndroidView(
            factory = { viewCtx ->
                CircularBubbleView(viewCtx, item, resolvedStyle, sizePx, viewCtx.classLoader, cfg).apply {
                    // 与交互预览一致：应用内禁用窗口级模糊
                    setSystemBlurEnabled(false)
                }
            },
            modifier = modifier.size(size),
            update = { v ->
                v.setMaterialStyle(resolvedStyle)
                v.setBlurRadius(cfg.bubbleBlurRadius.toFloat())
                v.setDimAlpha(cfg.bubbleDimAlpha)
                v.setBubblePaddingPercent(cfg.bubblePaddingPercent)
                v.setCornerRadiusPercent(cfg.iconCornerRadius)
                v.setIconAlpha(cfg.iconAlpha)
                v.setSelectedState(isSelected)
            },
        )
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
