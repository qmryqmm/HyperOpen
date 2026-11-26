package io.github.qmryqmm.hyperopen.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.log.ShortcutLaunchLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LaunchStatusFilter(val label: String) {
    ALL("全部"),
    SUCCESS("仅成功"),
    FAILURE("仅失败"),
}

private enum class LaunchTypeFilter(val label: String) {
    ALL("全部类型"),
    SHELL("Shell"),
    ACTIVITY("Activity"),
    APP("应用"),
    INTENT_URI("Intent"),
    BROADCAST("广播"),
    SYSTEM("系统动作"),
    ;

    fun matches(actionType: String?): Boolean {
        return when (this) {
            ALL -> true
            SHELL -> actionType == "SHELL"
            ACTIVITY -> actionType == "ACTIVITY"
            APP -> actionType == "APP"
            INTENT_URI -> actionType == "INTENT_URI"
            BROADCAST -> actionType == "BROADCAST"
            SYSTEM -> actionType == "FLASHLIGHT" ||
                actionType == "XIAOAI" ||
                actionType == "MUTE" ||
                actionType == "DND" ||
                actionType == "CAMERA"
        }
    }
}

/**
 * 快捷方式启动日志查看器二级页面。
 * 展示最近 100 条来自锁屏面板、预览与测试入口的快捷方式执行结果，支持按状态与动作类型筛选及详情排查。
 */
@Composable
fun ShortcutLaunchLogPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var reloadTick by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<ShortcutLaunchLogStore.Entry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    var statusFilter by remember { mutableStateOf(LaunchStatusFilter.ALL) }
    var typeFilter by remember { mutableStateOf(LaunchTypeFilter.ALL) }
    var selectedEntry by remember { mutableStateOf<ShortcutLaunchLogStore.Entry?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reloadTick) {
        val result = withContext(Dispatchers.IO) {
            ShortcutLaunchLogStore.loadAll(ctx)
        }
        entries = result
        loaded = true
    }

    val failureCount = remember(entries) {
        entries.count { !it.success }
    }

    val filteredEntries = remember(entries, statusFilter, typeFilter) {
        entries.filter { entry ->
            val statusOk = when (statusFilter) {
                LaunchStatusFilter.ALL -> true
                LaunchStatusFilter.SUCCESS -> entry.success
                LaunchStatusFilter.FAILURE -> !entry.success
            }
            statusOk && typeFilter.matches(entry.actionType)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 顶部筛选与操作卡片 ──
        item(key = "filter_header_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "执行记录筛选",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "共 ${entries.size} 条 · $failureCount 失败",
                            fontSize = 12.sp,
                            color = if (failureCount > 0) Color(0xFFE94634) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // 状态筛选芯片
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LaunchStatusFilter.entries.forEach { option ->
                            FilterChipPill(
                                label = option.label,
                                selected = statusFilter == option,
                                onClick = { statusFilter = option },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 类型横向筛选芯片
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LaunchTypeFilter.entries.forEach { option ->
                            FilterChipPill(
                                label = option.label,
                                selected = typeFilter == option,
                                onClick = { typeFilter = option },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    // 操作按钮：刷新 / 清空日志
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            text = "刷新",
                            onClick = {
                                reloadTick++
                                Toast.makeText(ctx, "已刷新启动日志", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "清空日志",
                            onClick = { showClearConfirmDialog = true },
                            enabled = entries.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── 空状态卡片 ──
        if (loaded && entries.isEmpty()) {
            item(key = "empty_all") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "暂无快捷方式启动记录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "通过锁屏面板、预览或测试按钮执行快捷方式后，系统会自动保留最近 100 条执行记录。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        } else if (loaded && filteredEntries.isEmpty()) {
            item(key = "empty_filtered") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "当前筛选条件下无匹配记录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = "重置筛选",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                statusFilter = LaunchStatusFilter.ALL
                                typeFilter = LaunchTypeFilter.ALL
                            },
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredEntries,
                key = { it.id },
            ) { entry ->
                LaunchLogEntryCard(
                    entry = entry,
                    onClick = { selectedEntry = entry },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 清空二次确认弹窗 ──
    if (showClearConfirmDialog) {
        Dialog(
            onDismissRequest = { showClearConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                            tint = Color(0xFFE94634),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "确认清空启动日志",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "确定要清空全部 ${entries.size} 条快捷方式启动日志吗？此操作不可撤销。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showClearConfirmDialog = false },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            text = "清空",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                showClearConfirmDialog = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        ShortcutLaunchLogStore.clear(ctx)
                                    }
                                    reloadTick++
                                    Toast.makeText(ctx, "已清空启动日志", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 日志详情弹窗 ──
    selectedEntry?.let { entry ->
        LaunchLogDetailDialog(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onCopy = { text ->
                copyTextToClipboard(ctx, text)
                Toast.makeText(ctx, "已复制完整日志详情", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val textColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

@Composable
private fun LaunchLogEntryCard(
    entry: ShortcutLaunchLogStore.Entry,
    onClick: () -> Unit,
) {
    val statusColor = if (entry.success) Color(0xFF07C160) else Color(0xFFE94634)
    val statusBg = if (entry.success) Color(0x1F07C160) else Color(0x1FE94634)
    val statusText = if (entry.success) "成功" else "失败"

    val typeLabel = ShortcutLaunchLogStore.actionTypeDisplayLabel(entry.actionType, entry.useRoot)
    val sourceLabel = ShortcutLaunchLogStore.sourceDisplayLabel(entry.source)
    val timeText = formatShortTime(entry.timestamp)

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 顶行：名称 + 状态徽章 + 耗时
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.shortcutTitle.ifBlank { "未命名快捷方式" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${entry.durationMs} ms",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Spacer(Modifier.height(6.dp))

            // 次行：启动类型徽章 + 触发来源徽章 + 执行时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaBadge(
                    text = typeLabel,
                    bg = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                    fg = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                MetaBadge(
                    text = sourceLabel,
                    bg = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    fg = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 启动参数预览
            if (entry.params.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.params,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 失败摘要
            if (!entry.success && entry.errorMessage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1FE94634))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = entry.errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFE94634),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaBadge(
    text: String,
    bg: Color,
    fg: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun LaunchLogDetailDialog(
    entry: ShortcutLaunchLogStore.Entry,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val statusColor = if (entry.success) Color(0xFF07C160) else Color(0xFFE94634)
    val statusBg = if (entry.success) Color(0x1F07C160) else Color(0x1FE94634)
    val statusText = if (entry.success) "成功" else "失败"

    val fullTime = formatFullTime(entry.timestamp)
    val typeLabel = ShortcutLaunchLogStore.actionTypeDisplayLabel(entry.actionType, entry.useRoot)
    val sourceLabel = ShortcutLaunchLogStore.sourceDisplayLabel(entry.source)
    val extraFlags = buildList {
        if (entry.useRoot) add("Root 执行")
        if (entry.userId > 0) add("跨用户 userId=${entry.userId}")
    }.joinToString(" · ")

    val copyPayload = remember(entry) {
        buildString {
            appendLine("快捷方式：${entry.shortcutTitle.ifBlank { "未命名快捷方式" }}")
            appendLine("执行状态：$statusText（耗时 ${entry.durationMs} ms）")
            appendLine("执行时间：$fullTime")
            appendLine("触发来源：$sourceLabel (${entry.source})")
            appendLine("启动类型：$typeLabel (${entry.actionType})")
            if (extraFlags.isNotEmpty()) {
                appendLine("执行环境：$extraFlags")
            }
            appendLine("启动参数：${entry.params.ifBlank { "（无）" }}")
            if (!entry.success || entry.errorMessage.isNotBlank()) {
                appendLine("报错信息：${entry.errorMessage.ifBlank { "（无）" }}")
            }
            if (entry.outputSnippet.isNotBlank()) {
                appendLine("输出摘要：${entry.outputSnippet}")
            }
        }.trimEnd()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // 标题与状态徽章
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.shortcutTitle.ifBlank { "未命名快捷方式" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DetailMetaRow("执行耗时", "${entry.durationMs} ms")
                    DetailMetaRow("执行时间", fullTime)
                    DetailMetaRow("触发来源", sourceLabel)
                    DetailMetaRow("启动类型", if (extraFlags.isNotEmpty()) "$typeLabel · $extraFlags" else typeLabel)

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "启动参数",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = entry.params.ifBlank { "（无启动参数）" },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            lineHeight = 17.sp,
                        )
                    }

                    if (!entry.success || entry.errorMessage.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "报错信息",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE94634),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1FE94634))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = entry.errorMessage.ifBlank { "未知错误（无详细堆栈）" },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE94634),
                                lineHeight = 17.sp,
                            )
                        }
                    }

                    if (entry.outputSnippet.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "输出摘要",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = entry.outputSnippet,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "复制详情",
                        onClick = { onCopy(copyPayload) },
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = "关闭",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(68.dp),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatShortTime(timestamp: Long): String {
    return try {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Throwable) {
        "--"
    }
}

private fun formatFullTime(timestamp: Long): String {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Throwable) {
        "--"
    }
}

private fun copyTextToClipboard(ctx: Context, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("HyperOpen LaunchLog", text))
    } catch (_: Throwable) {}
}
