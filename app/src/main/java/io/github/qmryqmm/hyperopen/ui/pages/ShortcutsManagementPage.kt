package io.github.qmryqmm.hyperopen.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.components.ShortcutCard
import io.github.qmryqmm.hyperopen.ui.components.ShortcutListInteractionState
import io.github.qmryqmm.hyperopen.ui.components.ShortcutRowNode
import io.github.qmryqmm.hyperopen.ui.components.dragRowVisuals
import io.github.qmryqmm.hyperopen.ui.components.shortcutRowGestures
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 快捷项目列表页（树形）：
 * 顶层快捷方式 + 聚合文件夹展开后的子项缩进树形展示。
 *
 * 交互：
 *  - 轻触卡片：编辑参数（多选模式下改为勾选）；
 *  - 长按拖拽：同级交换排序（顶层互换 / 文件夹内子项互换）；
 *  - 长按不动：进入多选，底部操作栏暂时替代常规底栏（全选、移动、复制、删除、取消）；
 *  - 文件夹行右侧箭头：展开 / 收起子项。
 */
@Composable
fun ShortcutsManagementPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    shortcuts: SnapshotStateList<ShortcutItem>,
    treeState: ShortcutsTreeState = rememberShortcutsTreeState(shortcuts, onShortcutsChanged = {}),
    onOpenPreview: () -> Unit,
    onShortcutToggle: (ShortcutItem, Boolean) -> Unit,
    onShortcutClick: (ShortcutItem) -> Unit,
    onChildClick: (ShortcutItem, ShortcutItem) -> Unit,
    onAddShortcut: () -> Unit,
    onCreateFolder: () -> Unit,
    onShortcutsChanged: () -> Unit = {},
) {
    val state = treeState.interactionState

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical().scrollEndHaptic(),
            state = lazyListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "actions_card") {
                Card(
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                ) {
                    ArrowPreference(
                        title = "快捷方式整体预览",
                        summary = "点击进入气泡预览界面（支持应用内实时预览效果）",
                        startAction = {
                            Icon(
                                MiuixIcons.ScreenMirroring,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = onOpenPreview,
                    )
                    ArrowPreference(
                        title = "新建快捷方式",
                        summary = "从预设动作中选择并创建新的快捷方式",
                        startAction = {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = onAddShortcut,
                    )
                    ArrowPreference(
                        title = "新建聚合文件夹",
                        summary = "创建空白聚合文件夹，之后逐个添加或移入子快捷方式",
                        startAction = {
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = onCreateFolder,
                    )
                }
            }

            item(key = "list_header") {
                SmallTitle(
                    text = "快捷项目列表 (${shortcuts.size})",
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            val nodes = treeState.buildNodes()
            itemsIndexed(
                nodes,
                key = { _, node -> node.key },
                contentType = { _, node -> if (node.depth == 0) "top" else "child" },
            ) { index, node ->
                val isLastChild = node.depth == 1 &&
                        (index == nodes.lastIndex || nodes[index + 1].parentFolderId != node.parentFolderId)
                ShortcutRowEntry(
                    node = node,
                    isLastChild = isLastChild,
                    state = state,
                    getNodes = { treeState.buildNodes() },
                    move = { n, dir -> treeState.moveNode(n, dir) },
                    folderCollapsed = node.depth == 0 && node.item.actionType == ShortcutItem.ACTION_FOLDER &&
                            treeState.isFolderCollapsed(node.item.id),
                    onToggleCollapsed = {
                        if (!state.clickSuppressed()) {
                            treeState.toggleFolderCollapsed(node.item.id)
                        }
                    },
                    onToggle = { enabled ->
                        if (!state.clickSuppressed()) onShortcutToggle(node.item, enabled)
                    },
                    onClick = {
                        if (!state.clickSuppressed()) {
                            if (state.multiSelect) {
                                state.toggleSelection(node.key)
                            } else if (node.depth == 0) {
                                onShortcutClick(node.item)
                            } else {
                                val parent = shortcuts.firstOrNull { it.id == node.parentFolderId }
                                if (parent != null) onChildClick(parent, node.item)
                            }
                        }
                    },
                    onLongPress = { state.toggleSelection(node.key) },
                )
            }

            item(key = "list_footer") {
                Text(
                    text = "提示：轻触卡片编辑参数；长按拖拽排序；长按不动进入多选，可在底栏全选、移动、复制、删除。文件夹点击右侧箭头展开或收起子项。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 32.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (treeState.showMoveDialog) {
        MoveToFolderDialog(
            shortcuts = shortcuts,
            selectedKeys = state.selection,
            onDismiss = { treeState.showMoveDialog = false },
            onMove = { targetId ->
                treeState.showMoveDialog = false
                treeState.moveSelected(targetId)
            },
        )
    }

    if (treeState.showDeleteDialog) {
        Dialog(onDismissRequest = { treeState.showDeleteDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                Column2(
                    title = "确认删除",
                    text = "确定要删除选中的 ${state.selection.size} 个快捷方式吗？此操作不可撤销。",
                    confirmText = "删除",
                    onConfirm = {
                        treeState.showDeleteDialog = false
                        treeState.deleteSelected()
                    },
                    onCancel = { treeState.showDeleteDialog = false },
                )
            }
        }
    }
}

/** 树形行（顶层卡片 / 文件夹子项缩进卡片），承载长按拖拽 + 多选手势。 */
@Composable
private fun ShortcutRowEntry(
    node: ShortcutRowNode,
    isLastChild: Boolean,
    state: ShortcutListInteractionState,
    getNodes: () -> List<ShortcutRowNode>,
    move: (ShortcutRowNode, Int) -> Unit,
    folderCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val selected = node.key in state.selection
    val nodes = getNodes()

    // 子项行：缩进 + 树形引导线（颜色在组合期取，drawBehind 内不可调用 MiuixTheme）
    val guideColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.25f)
    val rowModifier = if (node.depth == 1) {
        Modifier
            .padding(start = 20.dp, end = 12.dp)
            .fillMaxWidth()
            .drawBehind {
                val gap = 10.dp.toPx()
                val lineX = (-9).dp.toPx()
                val stroke = 1.5.dp.toPx()
                val guide = guideColor
                val midY = size.height / 2f
                // 竖线：自上而下贯穿行间空隙；末子行止于中点
                drawLine(
                    color = guide,
                    start = androidx.compose.ui.geometry.Offset(lineX, -gap),
                    end = androidx.compose.ui.geometry.Offset(lineX, if (isLastChild) midY else size.height + gap),
                    strokeWidth = stroke,
                )
                // 横线：连接到卡片
                drawLine(
                    color = guide,
                    start = androidx.compose.ui.geometry.Offset(lineX, midY),
                    end = androidx.compose.ui.geometry.Offset(0f, midY),
                    strokeWidth = stroke,
                )
            }
    } else {
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
    }

    Box(
        modifier = rowModifier
            .onSizeChanged { state.rowHeights[node.key] = it.height.toFloat() }
            .shortcutRowGestures(
                state = state,
                nodeKey = node.key,
                getNodes = getNodes,
                move = move,
                onLongPressNoMove = onLongPress,
            )
            .dragRowVisuals(state, node, nodes),
    ) {
        ShortcutCard(
            item = node.item,
            selected = selected,
            summaryOverride = when {
                node.depth == 1 -> node.item.subtitle.ifBlank { "点击编辑子快捷方式参数" }
                node.item.actionType == ShortcutItem.ACTION_FOLDER -> {
                    val count = node.item.parseFolderChildren().size
                    val base = node.item.subtitle.ifBlank { "聚合文件夹，点击编辑子快捷方式" }
                    if (folderCollapsed && count > 0) "$base · $count 个子项" else base
                }
                else -> null
            },
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.depth == 0 && node.item.actionType == ShortcutItem.ACTION_FOLDER) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onToggleCollapsed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (folderCollapsed) "▾" else "▴",
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Switch(
                        checked = node.item.enabled,
                        onCheckedChange = onToggle,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/** 移动到文件夹对话框：新建聚合文件夹并移入 / 移到根目录 / 各聚合文件夹。 */
@Composable
private fun MoveToFolderDialog(
    shortcuts: SnapshotStateList<ShortcutItem>,
    selectedKeys: Set<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "移动到文件夹",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                val folderCandidates = shortcuts.filter {
                    it.actionType == ShortcutItem.ACTION_FOLDER && "top:${it.id}" !in selectedKeys
                }
                LazyColumn(
                    modifier = Modifier.height(320.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "new_folder") {
                        MoveOptionRow(
                            title = "新建聚合文件夹并移入",
                            summary = "创建空白聚合并把选中项作为子项移入",
                            onClick = { onMove("__new__") },
                        )
                    }
                    item(key = "to_root") {
                        MoveOptionRow(
                            title = "移到根目录",
                            summary = "把选中的子快捷方式移出文件夹，回到顶层列表",
                            onClick = { onMove("__root__") },
                        )
                    }
                    itemsIndexed(
                        folderCandidates,
                        key = { _, f -> "target:${f.id}" },
                    ) { _, f ->
                        MoveOptionRow(
                            title = f.title,
                            summary = "聚合文件夹 · ${f.parseFolderChildren().size} 个子项",
                            onClick = { onMove(f.id) },
                        )
                    }
                }
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun MoveOptionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    lineHeight = 16.sp,
                )
            }
            Icon(
                MiuixIcons.Add,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Column2(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(text = "取消", onClick = onCancel)
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(text = confirmText, onClick = onConfirm)
        }
    }
}
