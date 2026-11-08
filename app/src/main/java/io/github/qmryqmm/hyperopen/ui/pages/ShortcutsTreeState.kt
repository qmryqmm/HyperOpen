package io.github.qmryqmm.hyperopen.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.components.ShortcutListInteractionState
import io.github.qmryqmm.hyperopen.ui.components.ShortcutRowNode
import io.github.qmryqmm.hyperopen.ui.components.duplicateShortcut
import io.github.qmryqmm.hyperopen.ui.components.rememberShortcutListInteractionState
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import java.util.UUID

/**
 * 快捷方式树形列表的状态与批量操作管理器。
 * 供主界面列表与底部多选操作栏（替代常规底栏）共享状态。
 */
class ShortcutsTreeState(
    val shortcuts: SnapshotStateList<ShortcutItem>,
    val interactionState: ShortcutListInteractionState,
    val onShortcutsChanged: () -> Unit,
    val context: Context,
) {
    var version by mutableIntStateOf(0)
    var collapsedFolders by mutableStateOf<Set<String>>(emptySet())
    var showMoveDialog by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)

    val multiSelect: Boolean
        get() = interactionState.multiSelect

    val selectedCount: Int
        get() = interactionState.selection.size

    val isAllSelected: Boolean
        get() {
            val nodes = buildNodes()
            return nodes.isNotEmpty() && nodes.all { it.key in interactionState.selection }
        }

    fun buildNodes(): List<ShortcutRowNode> {
        @Suppress("UNUSED_VARIABLE")
        val v = version // 建立响应式依赖，确保子项变动时毫秒级重组
        val nodes = ArrayList<ShortcutRowNode>(shortcuts.size + 8)
        val seenKeys = HashSet<String>()

        fun uniqueKey(baseKey: String): String {
            if (seenKeys.add(baseKey)) return baseKey
            var count = 2
            while (true) {
                val candidate = "$baseKey#$count"
                if (seenKeys.add(candidate)) return candidate
                count++
            }
        }

        for (item in shortcuts) {
            val topKey = uniqueKey("top:${item.id}")
            nodes.add(ShortcutRowNode(topKey, "root", item, 0, null))
            if (item.actionType == ShortcutItem.ACTION_FOLDER && item.id !in collapsedFolders) {
                for (child in item.parseFolderChildren()) {
                    val childKey = uniqueKey("child:${item.id}:${child.id}")
                    nodes.add(ShortcutRowNode(childKey, "folder:${item.id}", child, 1, item.id))
                }
            }
        }
        return nodes
    }

    fun toggleFolderCollapsed(folderId: String) {
        collapsedFolders = if (folderId in collapsedFolders) {
            collapsedFolders - folderId
        } else {
            collapsedFolders + folderId
        }
    }

    fun isFolderCollapsed(folderId: String): Boolean = folderId in collapsedFolders

    fun selectAllOrNone() {
        val allKeys = buildNodes().map { it.key }.toSet()
        if (allKeys.isNotEmpty() && interactionState.selection.containsAll(allKeys)) {
            interactionState.clearSelection()
        } else {
            interactionState.selection = allKeys
        }
    }

    fun selectedNodes(): List<ShortcutRowNode> =
        buildNodes().filter { it.key in interactionState.selection }

    fun moveNode(node: ShortcutRowNode, dir: Int) {
        if (node.depth == 0) {
            val i = shortcuts.indexOfFirst { it.id == node.item.id }
            val j = i + dir
            if (i < 0 || j < 0 || j >= shortcuts.size) return
            val tmp = shortcuts[i]
            shortcuts[i] = shortcuts[j]
            shortcuts[j] = tmp
        } else {
            val fi = shortcuts.indexOfFirst { it.id == node.parentFolderId }
            if (fi < 0) return
            val folder = shortcuts[fi]
            val children = folder.parseFolderChildren().toMutableList()
            val i = children.indexOfFirst { it.id == node.item.id }
            val j = i + dir
            if (i < 0 || j < 0 || j >= children.size) return
            val tmp = children[i]
            children[i] = children[j]
            children[j] = tmp
            // 采用深拷贝新对象替换，确保 SnapshotStateList 派发重组
            shortcuts[fi] = folder.copy().apply { setFolderChildren(children) }
        }
        version++
        onShortcutsChanged()
    }

    /**
     * 将拖拽的卡片直接移入目标聚合文件夹。
     */
    fun dropIntoFolder(draggedKey: String, targetFolderKey: String) {
        val nodes = buildNodes()
        val draggedNode = nodes.firstOrNull { it.key == draggedKey } ?: return
        val targetNode = nodes.firstOrNull { it.key == targetFolderKey } ?: return
        val draggedItem = draggedNode.item
        val targetFolder = targetNode.item
        if (targetFolder.actionType != ShortcutItem.ACTION_FOLDER) return
        if (draggedItem.id == targetFolder.id) return

        // 1) 从来源移除
        if (draggedNode.depth == 0) {
            shortcuts.removeAll { it.id == draggedItem.id }
        } else {
            val fromFolderId = draggedNode.parentFolderId ?: return
            val fromFi = shortcuts.indexOfFirst { it.id == fromFolderId }
            if (fromFi >= 0) {
                val fromFolder = shortcuts[fromFi]
                val fromChildren = fromFolder.parseFolderChildren().filter { it.id != draggedItem.id }
                shortcuts[fromFi] = fromFolder.copy().apply { setFolderChildren(fromChildren) }
            }
        }

        // 2) 移入目标文件夹（如果目标文件夹已存在完全相同的 id，自动生成新唯一 UUID，彻底防止 Compose key 碰撞闪退）
        val toFi = shortcuts.indexOfFirst { it.id == targetFolder.id }
        if (toFi >= 0) {
            val toFolder = shortcuts[toFi]
            val toChildren = toFolder.parseFolderChildren().toMutableList()
            val finalItem = if (toChildren.any { it.id == draggedItem.id }) {
                draggedItem.copy().also { it.id = UUID.randomUUID().toString() }
            } else {
                draggedItem
            }
            toChildren.add(finalItem)
            shortcuts[toFi] = toFolder.copy().apply { setFolderChildren(toChildren) }
            // 确保该目标文件夹展开，让用户直接看到移入效果
            collapsedFolders = collapsedFolders - targetFolder.id
        }

        version++
        onShortcutsChanged()
        interactionState.targetFolderKey = null
        interactionState.draggingKey = null
        interactionState.totalDragY = 0f
        Toast.makeText(context, "已将「${draggedItem.title}」放入「${targetFolder.title}」", Toast.LENGTH_SHORT).show()
    }

    /**
     * 将拖拽的子快捷方式移出文件夹，插入到根目录列表中。
     */
    fun dropOutOfFolder(draggedKey: String) {
        val nodes = buildNodes()
        val draggedNode = nodes.firstOrNull { it.key == draggedKey } ?: return
        if (draggedNode.depth != 1) return
        val draggedItem = draggedNode.item
        val fromFolderId = draggedNode.parentFolderId ?: return
        val fromFi = shortcuts.indexOfFirst { it.id == fromFolderId }
        if (fromFi < 0) return

        // 1) 从原文件夹中移除该子项
        val fromFolder = shortcuts[fromFi]
        val fromChildren = fromFolder.parseFolderChildren().filter { it.id != draggedItem.id }
        shortcuts[fromFi] = fromFolder.copy().apply { setFolderChildren(fromChildren) }

        // 2) 插入到根目录（插在该父文件夹后面；若根目录已有相同 id 则分配新 UUID）
        val finalOutItem = if (shortcuts.any { it.id == draggedItem.id }) {
            draggedItem.copy().also { it.id = UUID.randomUUID().toString() }
        } else {
            draggedItem
        }
        shortcuts.add(fromFi + 1, finalOutItem)

        version++
        onShortcutsChanged()
        interactionState.draggingKey = null
        interactionState.totalDragY = 0f
        interactionState.isDraggingOutOfFolder = false
        Toast.makeText(context, "已将「${draggedItem.title}」移出到快捷列表", Toast.LENGTH_SHORT).show()
    }

    /** 复制选中项（保持视觉顺序，逐个插到原位之后；文件夹深拷贝子项换新 id）。 */
    fun copySelected() {
        val nodes = selectedNodes()
        for (node in nodes.asReversed()) {
            val dup = duplicateShortcut(node.item)
            if (node.depth == 0) {
                val i = shortcuts.indexOfFirst { it.id == node.item.id }
                if (i >= 0) shortcuts.add(i + 1, dup)
            } else {
                val fi = shortcuts.indexOfFirst { it.id == node.parentFolderId }
                if (fi >= 0) {
                    val folder = shortcuts[fi]
                    val children = folder.parseFolderChildren().toMutableList()
                    val ci = children.indexOfFirst { it.id == node.item.id }
                    if (ci >= 0) {
                        children.add(ci + 1, dup)
                        shortcuts[fi] = folder.copy().apply { setFolderChildren(children) }
                    }
                }
            }
        }
        version++
        onShortcutsChanged()
        interactionState.clearSelection()
        Toast.makeText(context, "已复制 ${nodes.size} 个快捷方式", Toast.LENGTH_SHORT).show()
    }

    /** 删除选中项（弹窗确认后执行）。 */
    fun deleteSelected() {
        val nodes = selectedNodes()
        val topIds = nodes.filter { it.depth == 0 }.map { it.item.id }.toSet()
        shortcuts.removeAll { it.id in topIds }
        nodes.filter { it.depth == 1 }.groupBy { it.parentFolderId }.forEach { (fid, list) ->
            val fi = shortcuts.indexOfFirst { it.id == fid }
            if (fi >= 0) {
                val folder = shortcuts[fi]
                val ids = list.map { it.item.id }.toSet()
                val children = folder.parseFolderChildren().filter { it.id !in ids }
                shortcuts[fi] = folder.copy().apply { setFolderChildren(children) }
            }
        }
        version++
        onShortcutsChanged()
        interactionState.clearSelection()
        Toast.makeText(context, "已删除 ${nodes.size} 个快捷方式", Toast.LENGTH_SHORT).show()
    }

    /**
     * 移动选中项：
     *  - targetId == "__new__"：新建聚合文件夹并移入；
     *  - targetId == "__root__"：移到根目录（仅子项）；
     *  - 否则移入对应 FOLDER 的子项末尾。
     * 顶层 FOLDER 不参与移动（运行时不支持嵌套文件夹展开）。
     */
    fun moveSelected(targetId: String) {
        val nodes = selectedNodes()
        val movable = nodes.filterNot { it.depth == 0 && it.item.actionType == ShortcutItem.ACTION_FOLDER }
        val skipped = nodes.size - movable.size
        if (movable.isEmpty()) {
            interactionState.clearSelection()
            if (skipped > 0) Toast.makeText(context, "文件夹不支持嵌套移动", Toast.LENGTH_SHORT).show()
            return
        }
        val movedItems = movable.map { it.item }
        val firstTopIdx = movable.firstOrNull { it.depth == 0 }
            ?.let { n -> shortcuts.indexOfFirst { it.id == n.item.id } } ?: -1

        // 1) 从来源移除
        val topMoveIds = movable.filter { it.depth == 0 }.map { it.item.id }.toSet()
        shortcuts.removeAll { it.id in topMoveIds }
        movable.filter { it.depth == 1 }.groupBy { it.parentFolderId }.forEach { (fid, list) ->
            val fi = shortcuts.indexOfFirst { it.id == fid }
            if (fi >= 0) {
                val folder = shortcuts[fi]
                val ids = list.map { it.item.id }.toSet()
                val children = folder.parseFolderChildren().filter { it.id !in ids }
                shortcuts[fi] = folder.copy().apply { setFolderChildren(children) }
            }
        }

        // 2) 加入目标
        when (targetId) {
            "__new__" -> {
                val newFolder = ShortcutItem(
                    UUID.randomUUID().toString(),
                    "新聚合文件夹",
                    "点击编辑子快捷方式与样式",
                    ShortcutItem.ACTION_FOLDER,
                    "[]",
                    "",
                    "remix:folder-fill",
                    0xFF2A82E4.toInt(),
                    true,
                    true,
                    0xFF2A82E4.toInt(),
                    1,
                )
                newFolder.setFolderChildren(movedItems)
                if (firstTopIdx in 0..shortcuts.size) shortcuts.add(firstTopIdx, newFolder) else shortcuts.add(newFolder)
                Toast.makeText(context, "已移入新建聚合文件夹", Toast.LENGTH_SHORT).show()
            }
            "__root__" -> {
                for (item in movedItems) {
                    val finalItem = if (shortcuts.any { it.id == item.id }) {
                        item.copy().also { it.id = UUID.randomUUID().toString() }
                    } else {
                        item
                    }
                    shortcuts.add(finalItem)
                }
                Toast.makeText(context, "已将 ${movedItems.size} 个快捷方式移到根目录", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val fi = shortcuts.indexOfFirst { it.id == targetId }
                if (fi >= 0) {
                    val folder = shortcuts[fi]
                    val children = folder.parseFolderChildren().toMutableList()
                    for (item in movedItems) {
                        val finalItem = if (children.any { it.id == item.id }) {
                            item.copy().also { it.id = UUID.randomUUID().toString() }
                        } else {
                            item
                        }
                        children.add(finalItem)
                    }
                    shortcuts[fi] = folder.copy().apply { setFolderChildren(children) }
                    Toast.makeText(context, "已移入「${folder.title}」", Toast.LENGTH_SHORT).show()
                }
            }
        }
        version++
        onShortcutsChanged()
        interactionState.clearSelection()
    }
}

@Composable
fun rememberShortcutsTreeState(
    shortcuts: SnapshotStateList<ShortcutItem>,
    onShortcutsChanged: () -> Unit,
): ShortcutsTreeState {
    val ctx = LocalContext.current
    val interactionState = rememberShortcutListInteractionState()
    return remember(shortcuts) {
        ShortcutsTreeState(shortcuts, interactionState, onShortcutsChanged, ctx)
    }
}

/**
 * 多选操作底栏：暂时替代主界面底栏（自动适配悬浮底栏与常规底栏样式）。
 */
@Composable
fun ShortcutMultiSelectBottomBar(
    floating: Boolean,
    isAllSelected: Boolean,
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    if (floating) {
        FloatingNavigationBar(
            showDivider = true,
        ) {
            FloatingNavigationBarItem(
                selected = false,
                onClick = onSelectAll,
                icon = MiuixIcons.SelectAll,
                label = if (isAllSelected) "取消全选" else "全选",
            )
            FloatingNavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onMove,
                icon = MiuixIcons.MoveFile,
                label = "移动",
            )
            FloatingNavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onCopy,
                icon = MiuixIcons.Copy,
                label = "复制",
            )
            FloatingNavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onDelete,
                icon = MiuixIcons.Delete,
                label = "删除",
            )
            FloatingNavigationBarItem(
                selected = false,
                onClick = onCancel,
                icon = MiuixIcons.Close,
                label = "取消",
            )
        }
    } else {
        NavigationBar(
            showDivider = true,
        ) {
            NavigationBarItem(
                selected = false,
                onClick = onSelectAll,
                icon = MiuixIcons.SelectAll,
                label = if (isAllSelected) "取消全选" else "全选",
            )
            NavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onMove,
                icon = MiuixIcons.MoveFile,
                label = "移动",
            )
            NavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onCopy,
                icon = MiuixIcons.Copy,
                label = "复制",
            )
            NavigationBarItem(
                selected = false,
                enabled = hasSelection,
                onClick = onDelete,
                icon = MiuixIcons.Delete,
                label = "删除",
            )
            NavigationBarItem(
                selected = false,
                onClick = onCancel,
                icon = MiuixIcons.Close,
                label = "取消",
            )
        }
    }
}
