package io.github.qmryqmm.hyperopen.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        val nodes = ArrayList<ShortcutRowNode>(shortcuts.size + 8)
        for (item in shortcuts) {
            nodes.add(ShortcutRowNode("top:${item.id}", "root", item, 0, null))
            if (item.actionType == ShortcutItem.ACTION_FOLDER && item.id !in collapsedFolders) {
                for (child in item.parseFolderChildren()) {
                    nodes.add(ShortcutRowNode("child:${item.id}:${child.id}", "folder:${item.id}", child, 1, item.id))
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
            folder.setFolderChildren(children)
            shortcuts[fi] = folder
        }
        onShortcutsChanged()
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
                        folder.setFolderChildren(children)
                        shortcuts[fi] = folder
                    }
                }
            }
        }
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
                folder.setFolderChildren(children)
                shortcuts[fi] = folder
            }
        }
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
                folder.setFolderChildren(folder.parseFolderChildren().filter { it.id !in ids })
                shortcuts[fi] = folder
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
                shortcuts.addAll(movedItems)
                Toast.makeText(context, "已将 ${movedItems.size} 个快捷方式移到根目录", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val fi = shortcuts.indexOfFirst { it.id == targetId }
                if (fi >= 0) {
                    val folder = shortcuts[fi]
                    val children = folder.parseFolderChildren().toMutableList()
                    children.addAll(movedItems)
                    folder.setFolderChildren(children)
                    shortcuts[fi] = folder
                    Toast.makeText(context, "已移入「${folder.title}」", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
