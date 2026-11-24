package io.github.qmryqmm.hyperopen.market

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.ui.components.ShortcutBubbleIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// 快捷包解析、包名安装检测、重复目标比对、UUID/偏移清洗辅助函数
// ─────────────────────────────────────────────────────────────────────────────

fun shortcutActionTypeLabel(actionType: String?): String = when (actionType?.trim()) {
    ShortcutItem.ACTION_APP -> "应用"
    ShortcutItem.ACTION_INTENT_URI -> "意图"
    ShortcutItem.ACTION_ACTIVITY -> "Activity"
    ShortcutItem.ACTION_FOLDER -> "文件夹"
    ShortcutItem.ACTION_FLASHLIGHT -> "手电筒"
    ShortcutItem.ACTION_XIAOAI -> "小爱"
    ShortcutItem.ACTION_MUTE -> "静音"
    ShortcutItem.ACTION_DND -> "勿扰"
    ShortcutItem.ACTION_CAMERA -> "相机"
    ShortcutItem.ACTION_SHELL -> "Shell"
    ShortcutItem.ACTION_BROADCAST -> "广播"
    else -> actionType?.ifBlank { "未知" } ?: "未知"
}

fun shortcutActionTypeFullLabel(actionType: String?): String = when (actionType?.trim()) {
    ShortcutItem.ACTION_APP -> "打开应用 (APP)"
    ShortcutItem.ACTION_INTENT_URI -> "深度链接 / Intent (INTENT_URI)"
    ShortcutItem.ACTION_ACTIVITY -> "启动 Activity (ACTIVITY)"
    ShortcutItem.ACTION_FOLDER -> "聚合文件夹 (FOLDER)"
    ShortcutItem.ACTION_FLASHLIGHT -> "开关手电筒 (FLASHLIGHT)"
    ShortcutItem.ACTION_XIAOAI -> "唤醒小爱 (XIAOAI)"
    ShortcutItem.ACTION_MUTE -> "静音模式 (MUTE)"
    ShortcutItem.ACTION_DND -> "勿扰模式 (DND)"
    ShortcutItem.ACTION_CAMERA -> "街拍相机 (CAMERA)"
    ShortcutItem.ACTION_SHELL -> "Shell 命令 (SHELL)"
    ShortcutItem.ACTION_BROADCAST -> "发送广播 (BROADCAST)"
    else -> actionType.orEmpty()
}

/**
 * 严格解析快捷方式包 JSON 数组；与 HyperOpenConfig.parseShortcutsJson 不同，
 * 空列表或异常时绝不回退到默认预设快捷方式。
 */
fun parseShortcutPackItems(shortcutsJson: String?): List<ShortcutItem> {
    if (shortcutsJson.isNullOrBlank()) return emptyList()
    val arr = JSONArray(shortcutsJson.trim())
    val out = ArrayList<ShortcutItem>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        out.add(ShortcutItem.fromJson(obj))
    }
    return out
}

/** 提取需检测本机安装状态的目标应用包名（针对 APP 与带包名的 ACTIVITY）。 */
fun extractTargetPackageForInstallCheck(item: ShortcutItem): String? {
    return when (item.actionType) {
        ShortcutItem.ACTION_APP -> item.actionPayload?.trim()?.takeIf { it.isNotEmpty() }
        ShortcutItem.ACTION_ACTIVITY -> {
            val spec = ActivityLaunchSpec.fromJson(item.actionPayload)
            spec.resolvePackage()?.trim()?.takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}

private fun isPackageInstalledBlocking(pm: PackageManager, packageName: String): Boolean {
    if (packageName.isBlank()) return false
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }
}

/** 在 IO 线程批量检测快捷包（含文件夹子项）引用的目标应用安装状态。 */
suspend fun inspectInstalledPackages(
    ctx: Context,
    items: List<ShortcutItem>,
): Map<String, Boolean> = withContext(Dispatchers.IO) {
    val pkgs = LinkedHashSet<String>()
    fun collect(item: ShortcutItem) {
        extractTargetPackageForInstallCheck(item)?.let { pkgs.add(it) }
        if (item.actionType == ShortcutItem.ACTION_FOLDER) {
            item.parseFolderChildren().forEach { child ->
                extractTargetPackageForInstallCheck(child)?.let { pkgs.add(it) }
            }
        }
    }
    items.forEach(::collect)
    if (pkgs.isEmpty()) return@withContext emptyMap()
    val pm = ctx.packageManager
    val result = HashMap<String, Boolean>(pkgs.size)
    for (pkg in pkgs) {
        result[pkg] = isPackageInstalledBlocking(pm, pkg)
    }
    result
}

/** 构造详情清单内的明文目标参数说明，方便用户审查真实跳转目标与命令内容。 */
fun formatShortcutTargetDetails(item: ShortcutItem): String {
    val payload = item.actionPayload.orEmpty().trim()
    val userSuffix = if (item.userId > 0) " · 用户空间 userId=${item.userId}" else ""
    return when (item.actionType) {
        ShortcutItem.ACTION_APP -> {
            val pkg = payload.ifEmpty { "(未填写包名)" }
            "目标包名: $pkg$userSuffix"
        }
        ShortcutItem.ACTION_INTENT_URI -> {
            val uriUser = ShortcutItem.extractUserFromPayload(payload)
            val resolvedUser = if (item.userId > 0) item.userId else uriUser
            val uNote = if (resolvedUser > 0) " · 用户空间 userId=$resolvedUser" else ""
            "Intent URI: ${payload.ifEmpty { "(空)" }}$uNote"
        }
        ShortcutItem.ACTION_ACTIVITY -> {
            val spec = ActivityLaunchSpec.fromJson(payload)
            val comp = spec.resolveComponent()?.flattenToShortString()
                ?: spec.component.orEmpty().trim()
            val pkg = spec.resolvePackage().orEmpty().trim()
            val actionStr = ActivityLaunchSpec.normalizeAction(spec.action)
            val dataStr = spec.data.orEmpty().trim()
            buildList {
                if (comp.isNotEmpty()) add("组件: $comp")
                else if (pkg.isNotEmpty()) add("包名: $pkg")
                if (actionStr.isNotEmpty()) add("Action: $actionStr")
                if (dataStr.isNotEmpty()) add("Data: $dataStr")
                if (spec.extras.isNotEmpty()) add("Extras: ${spec.extras.size} 项")
                if (spec.useRoot) add("Root 启动 (su)")
                if (item.userId > 0) add("userId=${item.userId}")
            }.joinToString(" · ").ifEmpty { "未配置 Activity 参数" }
        }
        ShortcutItem.ACTION_BROADCAST -> {
            val rootTag = if (item.shellUseRoot) " [Root]" else ""
            "广播 Action$rootTag: ${payload.ifEmpty { "(空)" }}"
        }
        ShortcutItem.ACTION_SHELL -> {
            val modeTag = if (item.shellUseRoot) "[su / Root]" else "[sh]"
            "$modeTag ${payload.ifEmpty { "(空命令)" }}"
        }
        ShortcutItem.ACTION_FOLDER -> {
            val count = item.parseFolderChildren().size
            "聚合文件夹 · 包含 $count 个子快捷项"
        }
        ShortcutItem.ACTION_FLASHLIGHT -> "系统动作：开关后置 LED 手电筒"
        ShortcutItem.ACTION_XIAOAI -> "系统动作：唤醒小爱同学 (com.miui.voiceassist)"
        ShortcutItem.ACTION_MUTE -> "系统动作：切换系统响铃 / 静音模式"
        ShortcutItem.ACTION_DND -> "系统动作：开启 / 关闭系统勿扰模式"
        ShortcutItem.ACTION_CAMERA -> "系统动作：快速启动系统相机"
        else -> payload.ifEmpty { item.actionType.orEmpty() }
    }
}

/**
 * 递归克隆快捷方式供本地导入或沙箱使用：
 * - 为顶层与全部文件夹子项生成全新 UUID，杜绝 Compose key 冲突；
 * - 强制清零 customOffsetX / customOffsetY，适配本机默认网格排版。
 */
fun cloneShortcutForImport(raw: ShortcutItem): ShortcutItem {
    val cloned = raw.copy()
    cloned.id = UUID.randomUUID().toString()
    cloned.customOffsetX = 0f
    cloned.customOffsetY = 0f
    if (cloned.actionType == ShortcutItem.ACTION_FOLDER) {
        val clonedChildren = cloned.parseFolderChildren().map { child ->
            child.copy().apply {
                id = UUID.randomUUID().toString()
                customOffsetX = 0f
                customOffsetY = 0f
            }
        }
        cloned.setFolderChildren(clonedChildren)
    }
    return cloned
}

private fun extractNormalizedPackage(item: ShortcutItem): String? {
    return when (item.actionType) {
        ShortcutItem.ACTION_APP -> item.actionPayload?.trim()?.takeIf { it.isNotEmpty() }
        ShortcutItem.ACTION_ACTIVITY -> ActivityLaunchSpec.fromJson(item.actionPayload).resolvePackage()?.trim()?.takeIf { it.isNotEmpty() }
        ShortcutItem.ACTION_INTENT_URI -> runCatching {
            val intent = Intent.parseUri(item.actionPayload.orEmpty(), Intent.URI_INTENT_SCHEME)
            intent.`package`?.trim()?.takeIf { it.isNotEmpty() }
                ?: intent.component?.packageName?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        else -> null
    }
}

private fun isSameShortcutTarget(a: ShortcutItem, b: ShortcutItem): Boolean {
    if (a.actionType != b.actionType) return false
    return when (a.actionType) {
        ShortcutItem.ACTION_APP -> {
            val pa = a.actionPayload.orEmpty().trim()
            val pb = b.actionPayload.orEmpty().trim()
            pa.isNotEmpty() && pa == pb && a.userId == b.userId
        }
        ShortcutItem.ACTION_ACTIVITY -> {
            val sa = ActivityLaunchSpec.fromJson(a.actionPayload)
            val sb = ActivityLaunchSpec.fromJson(b.actionPayload)
            val pkgA = sa.resolvePackage().orEmpty().trim()
            val pkgB = sb.resolvePackage().orEmpty().trim()
            val compA = sa.resolveComponent()?.flattenToString().orEmpty()
            val compB = sb.resolveComponent()?.flattenToString().orEmpty()
            val actA = ActivityLaunchSpec.normalizeAction(sa.action)
            val actB = ActivityLaunchSpec.normalizeAction(sb.action)
            val dataA = sa.data.orEmpty().trim()
            val dataB = sb.data.orEmpty().trim()
            (pkgA.isNotEmpty() || compA.isNotEmpty() || actA.isNotEmpty() || dataA.isNotEmpty()) &&
                pkgA == pkgB &&
                compA == compB &&
                actA == actB &&
                dataA == dataB &&
                a.userId == b.userId
        }
        ShortcutItem.ACTION_INTENT_URI -> {
            val rawA = a.actionPayload.orEmpty().trim()
            val rawB = b.actionPayload.orEmpty().trim()
            if (rawA.isEmpty() || rawB.isEmpty()) return false
            if (rawA == rawB && a.userId == b.userId) return true
            val pkgA = extractNormalizedPackage(a)
            val pkgB = extractNormalizedPackage(b)
            if (pkgA != null && pkgA == pkgB && a.userId == b.userId) {
                val intentA = runCatching { Intent.parseUri(rawA, Intent.URI_INTENT_SCHEME) }.getOrNull()
                val intentB = runCatching { Intent.parseUri(rawB, Intent.URI_INTENT_SCHEME) }.getOrNull()
                if (intentA != null && intentB != null) {
                    return intentA.component == intentB.component &&
                        intentA.action == intentB.action &&
                        intentA.dataString == intentB.dataString
                }
            }
            false
        }
        ShortcutItem.ACTION_SHELL, ShortcutItem.ACTION_BROADCAST -> {
            val pa = a.actionPayload.orEmpty().trim()
            val pb = b.actionPayload.orEmpty().trim()
            pa.isNotEmpty() && pa == pb && a.shellUseRoot == b.shellUseRoot
        }
        ShortcutItem.ACTION_FLASHLIGHT,
        ShortcutItem.ACTION_XIAOAI,
        ShortcutItem.ACTION_MUTE,
        ShortcutItem.ACTION_DND,
        ShortcutItem.ACTION_CAMERA,
        -> true
        ShortcutItem.ACTION_FOLDER -> {
            val titleA = a.title.orEmpty().trim()
            val titleB = b.title.orEmpty().trim()
            val childrenA = a.parseFolderChildren()
            val childrenB = b.parseFolderChildren()
            titleA.isNotEmpty() &&
                titleA == titleB &&
                childrenA.size == childrenB.size &&
                childrenA.indices.all { idx -> isSameShortcutTarget(childrenA[idx], childrenB[idx]) }
        }
        else -> false
    }
}

/** 检测本地所有顶层快捷项及文件夹子项中是否已存在相同目标（同包名+同动作）条目。 */
fun hasDuplicateTargetInLocal(incoming: ShortcutItem, localShortcuts: List<ShortcutItem>): Boolean {
    for (existing in localShortcuts) {
        if (isSameShortcutTarget(incoming, existing)) return true
        if (existing.actionType == ShortcutItem.ACTION_FOLDER) {
            for (child in existing.parseFolderChildren()) {
                if (isSameShortcutTarget(incoming, child)) return true
            }
        }
    }
    return false
}

/** 去重待上传条目：若同时传入了父文件夹及其子项（相同 id 或相同对象），自动剔除冗余子项。 */
fun deduplicateShortcutsForUpload(items: List<ShortcutItem>): List<ShortcutItem> {
    if (items.isEmpty()) return emptyList()
    val folderChildIds = HashSet<String>()
    for (item in items) {
        if (item.actionType == ShortcutItem.ACTION_FOLDER) {
            item.parseFolderChildren().forEach { child ->
                if (!child.id.isNullOrBlank()) folderChildIds.add(child.id)
            }
        }
    }
    val seenIds = HashSet<String>()
    val result = ArrayList<ShortcutItem>(items.size)
    for (item in items) {
        val isFolder = item.actionType == ShortcutItem.ACTION_FOLDER
        if (!isFolder && !item.id.isNullOrBlank() && item.id in folderChildIds) {
            continue
        }
        if (!item.id.isNullOrBlank() && !seenIds.add(item.id)) {
            continue
        }
        result.add(item)
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// 快捷包详情弹窗（实时包名检测 / 文件夹展开 / 单条与子项添加 / 多选导入 / 整表导入 / 沙箱预览）
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutMarketDetailDialog(
    summary: MarketShortcutPackSummary,
    isOwnedLocally: Boolean,
    onDismiss: () -> Unit,
    onBuildSandboxPreview: (detail: MarketShortcutPackDetail, items: List<ShortcutItem>) -> Unit,
    onAddSingleItem: (summary: MarketShortcutPackSummary, item: ShortcutItem, shouldRecordDownload: Boolean) -> Unit,
    onImportSelectedItems: (summary: MarketShortcutPackSummary, items: List<ShortcutItem>, shouldRecordDownload: Boolean) -> Unit,
    onOpenImportAll: (summary: MarketShortcutPackSummary, items: List<ShortcutItem>) -> Unit,
    onOpenReport: () -> Unit,
    onOpenDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var reloadNonce by remember(summary.id) { mutableStateOf(0) }
    var isLoading by remember(summary.id) { mutableStateOf(true) }
    var loadError by remember(summary.id) { mutableStateOf<String?>(null) }
    var detail by remember(summary.id) { mutableStateOf<MarketShortcutPackDetail?>(null) }
    var parsedItems by remember(summary.id) { mutableStateOf<List<ShortcutItem>>(emptyList()) }
    var installedMap by remember(summary.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var hasRecordedDownload by remember(summary.id) { mutableStateOf(false) }

    val selectedIndices = remember(summary.id) { mutableStateMapOf<Int, Boolean>() }
    val expandedFolders = remember(summary.id) { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(summary.id, reloadNonce) {
        isLoading = true
        loadError = null
        val res = runCatching {
            val fetched = MarketApiClient.getShortcutDetail(ctx, summary.id, download = false)
            val parsed = withContext(Dispatchers.Default) {
                parseShortcutPackItems(fetched.shortcutsJson)
            }
            val pkgStatus = inspectInstalledPackages(ctx, parsed)
            Triple(fetched, parsed, pkgStatus)
        }
        isLoading = false
        res.onSuccess { (fetched, parsed, pkgStatus) ->
            detail = fetched
            parsedItems = parsed
            installedMap = pkgStatus
            selectedIndices.clear()
            expandedFolders.clear()
        }.onFailure { err ->
            loadError = err.message ?: "加载快捷包详情失败"
        }
    }

    val activeMeta = detail?.item ?: summary
    val needsNewerApp = activeMeta.minAppVersionCode > BuildConfig.VERSION_CODE
    val selectedCount = parsedItems.indices.count { selectedIndices[it] == true }
    val allSelected = parsedItems.isNotEmpty() && selectedCount == parsedItems.size

    fun consumeDownloadFlag(): Boolean {
        if (hasRecordedDownload) return false
        hasRecordedDownload = true
        return true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeMeta.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isOwnedLocally) {
                        Spacer(Modifier.width(8.dp))
                        MarketBadgePill(
                            text = "本机上传",
                            textColor = MiuixTheme.colorScheme.primary,
                            bgColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                val verStr = if (activeMeta.appVersion.isNotBlank()) " · 来源 v${activeMeta.appVersion}" else ""
                Text(
                    text = "作者：${activeMeta.author.ifBlank { "匿名" }}$verStr · ${activeMeta.downloads} 次下载 · ${formatMarketSize(activeMeta.sizeBytes)}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "meta_header") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "发布于 ${formatMarketFullTime(activeMeta.createdAt)} · 更新于 ${formatMarketFullTime(activeMeta.updatedAt)}",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MarketBadgePill(
                                    text = "${activeMeta.shortcutCount} 顶层快捷项",
                                    textColor = MiuixTheme.colorScheme.primary,
                                    bgColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                )
                                if (activeMeta.folderCount > 0) {
                                    MarketBadgePill(
                                        text = "${activeMeta.folderCount} 文件夹",
                                        textColor = MiuixTheme.colorScheme.onSurface,
                                        bgColor = MiuixTheme.colorScheme.surfaceVariant,
                                    )
                                }
                                activeMeta.actionTypes.forEach { at ->
                                    MarketBadgePill(
                                        text = shortcutActionTypeLabel(at),
                                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                    )
                                }
                            }

                            if (activeMeta.description.isNotBlank()) {
                                Text(
                                    text = activeMeta.description,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }

                            if (activeMeta.tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    activeMeta.tags.forEach { tag ->
                                        MarketBadgePill(
                                            text = "#$tag",
                                            textColor = MiuixTheme.colorScheme.primary,
                                            bgColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        )
                                    }
                                }
                            }

                            if (needsNewerApp) {
                                MarketBannerBox(
                                    text = "版本兼容提示：此快捷包制作于较高版本模块（建议 versionCode ≥ ${activeMeta.minAppVersionCode}，当前为 ${BuildConfig.VERSION_CODE}），部分新字段将被安全忽略。",
                                    textColor = MARKET_COLOR_AMBER,
                                    bgColor = MARKET_COLOR_AMBER_BG,
                                )
                            }

                            if (activeMeta.customImageCount > 0) {
                                MarketBannerBox(
                                    text = "此快捷包携带 ${activeMeta.customImageCount} 张自定义图标图片（已通过格式与 64 KB 上限校验）。",
                                    textColor = MARKET_COLOR_AMBER,
                                    bgColor = MARKET_COLOR_AMBER_BG,
                                )
                            }

                            if (activeMeta.hasRootRisk || activeMeta.riskSummary.isNotEmpty()) {
                                MarketRiskWarningBox(
                                    riskItems = activeMeta.riskSummary,
                                    title = "安全警示：包含 ${activeMeta.riskSummary.size} 项高危动作",
                                    subtitle = "以下动作涉及 Shell、Root 权限、自定义广播或敏感 Intent，请核对下方明文参数后再添加：",
                                )
                            }
                        }
                    }

                    item(key = "list_title_bar") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (parsedItems.isNotEmpty()) {
                                        "包内快捷项清单 (${parsedItems.size})"
                                    } else {
                                        "包内快捷项清单"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!isLoading && parsedItems.isNotEmpty()) {
                                    Text(
                                        text = if (allSelected) "取消全选" else "全选 (${parsedItems.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val next = !allSelected
                                                parsedItems.indices.forEach { idx ->
                                                    selectedIndices[idx] = next
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    when {
                        isLoading -> {
                            item(key = "detail_loading") {
                                MarketBannerBox(
                                    text = "正在加载快捷包明细与本机应用安装状态...",
                                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                        }
                        loadError != null -> {
                            item(key = "detail_error") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MARKET_COLOR_RED_BG)
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = loadError ?: "加载失败",
                                        fontSize = 12.sp,
                                        color = MARKET_COLOR_RED,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    MarketFilterChip(
                                        label = "重新加载",
                                        selected = true,
                                        onClick = { reloadNonce++ },
                                    )
                                }
                            }
                        }
                        parsedItems.isEmpty() -> {
                            item(key = "detail_empty") {
                                MarketBannerBox(
                                    text = "快捷包正文为空",
                                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(
                                items = parsedItems,
                                key = { index, item -> "$index:${item.id}" },
                            ) { index, item ->
                                val isChecked = selectedIndices[index] == true
                                val isFolder = item.actionType == ShortcutItem.ACTION_FOLDER
                                val children = remember(item.actionPayload, isFolder) {
                                    if (isFolder) item.parseFolderChildren() else emptyList()
                                }
                                val isExpanded = isFolder && expandedFolders[index] == true
                                val targetPkg = remember(item.actionType, item.actionPayload) {
                                    extractTargetPackageForInstallCheck(item)
                                }
                                val installStatus = targetPkg?.let { installedMap[it] }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ShortcutPackTopItemRow(
                                        item = item,
                                        checked = isChecked,
                                        isFolder = isFolder,
                                        childCount = children.size,
                                        expanded = isExpanded,
                                        targetPkg = targetPkg,
                                        isInstalled = installStatus,
                                        onToggleCheck = {
                                            selectedIndices[index] = !isChecked
                                        },
                                        onToggleExpand = {
                                            expandedFolders[index] = !isExpanded
                                        },
                                        onAddClick = {
                                            onAddSingleItem(activeMeta, item, consumeDownloadFlag())
                                        },
                                    )

                                    if (isExpanded && children.isNotEmpty()) {
                                        val guideColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            children.forEachIndexed { childIdx, child ->
                                                val isLastChild = childIdx == children.lastIndex
                                                val childPkg = extractTargetPackageForInstallCheck(child)
                                                val childInstalled = childPkg?.let { installedMap[it] }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .drawBehind {
                                                            val gap = 6.dp.toPx()
                                                            val lineX = (-8).dp.toPx()
                                                            val stroke = 1.4.dp.toPx()
                                                            val midY = size.height / 2f
                                                            drawLine(
                                                                color = guideColor,
                                                                start = Offset(lineX, -gap),
                                                                end = Offset(lineX, if (isLastChild) midY else size.height + gap),
                                                                strokeWidth = stroke,
                                                            )
                                                            drawLine(
                                                                color = guideColor,
                                                                start = Offset(lineX, midY),
                                                                end = Offset(0f, midY),
                                                                strokeWidth = stroke,
                                                            )
                                                        },
                                                ) {
                                                    ShortcutPackChildItemRow(
                                                        child = child,
                                                        targetPkg = childPkg,
                                                        isInstalled = childInstalled,
                                                        onAddClick = {
                                                            onAddSingleItem(activeMeta, child, consumeDownloadFlag())
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                val ready = !isLoading && loadError == null && detail != null && parsedItems.isNotEmpty()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "临时预览",
                        enabled = ready,
                        onClick = {
                            val d = detail ?: return@TextButton
                            onBuildSandboxPreview(d, parsedItems)
                        },
                    )
                    TextButton(
                        text = if (selectedCount > 0) "导入所选 ($selectedCount)" else "导入所选",
                        enabled = ready && selectedCount > 0,
                        onClick = {
                            val picked = parsedItems.filterIndexed { idx, _ -> selectedIndices[idx] == true }
                            if (picked.isNotEmpty()) {
                                onImportSelectedItems(activeMeta, picked, consumeDownloadFlag())
                            }
                        },
                    )
                    TextButton(
                        text = "导入全部",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = ready,
                        onClick = {
                            onOpenImportAll(activeMeta, parsedItems)
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = "举报",
                            onClick = onOpenReport,
                        )
                        if (isOwnedLocally) {
                            TextButton(
                                text = "撤回删除",
                                onClick = onOpenDelete,
                            )
                        }
                    }
                    TextButton(
                        text = "关闭",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutPackTopItemRow(
    item: ShortcutItem,
    checked: Boolean,
    isFolder: Boolean,
    childCount: Int,
    expanded: Boolean,
    targetPkg: String?,
    isInstalled: Boolean?,
    onToggleCheck: () -> Unit,
    onToggleExpand: () -> Unit,
    onAddClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val bgColor = if (checked) {
        primary.copy(alpha = 0.08f)
    } else {
        MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onToggleCheck)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                onClick = onToggleCheck,
            )
            Spacer(Modifier.width(6.dp))
            ShortcutBubbleIcon(
                item = item,
                size = 38.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title.orEmpty().ifBlank { "未命名快捷项" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!item.enabled) {
                        Spacer(Modifier.width(6.dp))
                        MarketBadgePill(
                            text = "已停用",
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            bgColor = MiuixTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle.orEmpty(),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isFolder && childCount > 0) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                        contentDescription = if (expanded) "收起文件夹子项" else "展开文件夹子项",
                        tint = primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            AddShortcutActionPill(onClick = onAddClick)
        }

        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 34.dp),
        ) {
            MarketBadgePill(
                text = shortcutActionTypeLabel(item.actionType),
                textColor = primary,
                bgColor = primary.copy(alpha = 0.12f),
            )
            if (targetPkg != null && isInstalled != null) {
                MarketBadgePill(
                    text = if (isInstalled) "本机已安装" else "未安装",
                    textColor = if (isInstalled) MARKET_COLOR_GREEN else MARKET_COLOR_AMBER,
                    bgColor = if (isInstalled) MARKET_COLOR_GREEN_BG else MARKET_COLOR_AMBER_BG,
                )
            }
            if (isFolder) {
                MarketBadgePill(
                    text = if (childCount > 0) {
                        if (expanded) "收起 $childCount 个子项 ▲" else "展开 $childCount 个子项 ▼"
                    } else {
                        "空文件夹"
                    },
                    textColor = if (childCount > 0) primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    bgColor = if (childCount > 0) primary.copy(alpha = 0.10f) else MiuixTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = if (childCount > 0) Modifier.clickable(onClick = onToggleExpand) else Modifier,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = formatShortcutTargetDetails(item),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 34.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutPackChildItemRow(
    child: ShortcutItem,
    targetPkg: String?,
    isInstalled: Boolean?,
    onAddClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutBubbleIcon(
                item = child,
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = child.title.orEmpty().ifBlank { "未命名子项" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!child.subtitle.isNullOrBlank()) {
                    Text(
                        text = child.subtitle.orEmpty(),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            AddShortcutActionPill(onClick = onAddClick)
        }

        Spacer(Modifier.height(5.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 42.dp),
        ) {
            MarketBadgePill(
                text = shortcutActionTypeLabel(child.actionType),
                textColor = primary,
                bgColor = primary.copy(alpha = 0.12f),
            )
            if (targetPkg != null && isInstalled != null) {
                MarketBadgePill(
                    text = if (isInstalled) "本机已安装" else "未安装",
                    textColor = if (isInstalled) MARKET_COLOR_GREEN else MARKET_COLOR_AMBER,
                    bgColor = if (isInstalled) MARKET_COLOR_GREEN_BG else MARKET_COLOR_AMBER_BG,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = formatShortcutTargetDetails(child),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 42.dp),
        )
    }
}

@Composable
private fun AddShortcutActionPill(onClick: () -> Unit) {
    val primary = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "添加",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = primary,
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 导入整个列表二选一确认弹窗（追加 vs 替换 + 替换前备份）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutPackImportAllDialog(
    summary: MarketShortcutPackSummary,
    items: List<ShortcutItem>,
    currentShortcutCount: Int,
    needsBackupSuggestion: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (replaceExisting: Boolean, backupCurrentFirst: Boolean) -> Unit,
) {
    var replaceExisting by remember(summary.id) { mutableStateOf(false) }
    var backupCurrentFirst by remember(summary.id, needsBackupSuggestion) {
        mutableStateOf(true)
    }
    val folderCount = remember(items) {
        items.count { it.actionType == ShortcutItem.ACTION_FOLDER }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "导入快捷方式包",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                val folderMeta = if (folderCount > 0) "（含 $folderCount 个文件夹）" else ""
                Text(
                    text = "「${summary.name}」 · 共 ${items.size} 个顶层快捷项$folderMeta · 作者 ${summary.author.ifBlank { "匿名" }}",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                if (summary.minAppVersionCode > BuildConfig.VERSION_CODE) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = "版本兼容提示：此快捷包建议模块 versionCode ≥ ${summary.minAppVersionCode}（当前为 ${BuildConfig.VERSION_CODE}），不支持的字段将被安全跳过。",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }

                if (summary.hasRootRisk || summary.riskSummary.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    MarketRiskWarningBox(
                        riskItems = summary.riskSummary,
                        title = "安全提示：包含 ${summary.riskSummary.size} 项高危动作",
                        subtitle = "请确认作者「${summary.author.ifBlank { "匿名" }}」可信，导入后可在本地列表逐项检查或停用：",
                    )
                }

                Spacer(Modifier.height(12.dp))
                RadioButtonPreference(
                    title = "追加到当前列表（推荐）",
                    summary = "保留本机现有的 $currentShortcutCount 个快捷方式，将包内 ${items.size} 个顶层项追加到末尾（自动分配全新 UUID 与默认排版）。",
                    selected = !replaceExisting,
                    onClick = { replaceExisting = false },
                )
                RadioButtonPreference(
                    title = "替换当前列表",
                    summary = "清空本地现有快捷方式列表，替换为该快捷包的全部内容。",
                    selected = replaceExisting,
                    onClick = { replaceExisting = true },
                )

                if (replaceExisting && needsBackupSuggestion) {
                    BasicComponent(
                        title = "替换前备份当前配置",
                        summary = "检测到当前配置尚未归档或有未保存改动，开启后先在配置库另存为备份再替换快捷列表。",
                        endActions = {
                            Switch(
                                checked = backupCurrentFirst,
                                onCheckedChange = { backupCurrentFirst = it },
                            )
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = if (replaceExisting) "确认替换" else "确认追加",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            onConfirm(
                                replaceExisting,
                                replaceExisting && needsBackupSuggestion && backupCurrentFirst,
                            )
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 快捷方式市场上传弹窗（支持全量列表或 Tab 1 多选子集，父文件夹子项去重）
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutMarketUploadDialog(
    sourceShortcuts: List<ShortcutItem>,
    isSelectionMode: Boolean = false,
    onDismiss: () -> Unit,
    onUploaded: (MarketUploadResult) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val cleanSourceShortcuts = remember(sourceShortcuts) {
        deduplicateShortcutsForUpload(sourceShortcuts)
    }
    val rawInspection = remember(cleanSourceShortcuts) {
        MarketSanitizer.inspectShortcuts(cleanSourceShortcuts, stripCustomImages = false)
    }

    var name by remember(cleanSourceShortcuts) {
        mutableStateOf(
            if (isSelectionMode && cleanSourceShortcuts.size == 1) {
                cleanSourceShortcuts.first().title.orEmpty().trim().take(60)
            } else {
                ""
            }
        )
    }
    var author by remember { mutableStateOf(MarketSettings.getLastAuthor(ctx)) }
    var description by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var keepCustomImages by remember(cleanSourceShortcuts) { mutableStateOf(true) }

    var isPreparing by remember { mutableStateOf(false) }
    var preparedUpload by remember { mutableStateOf<MarketSanitizer.PreparedShortcutsUpload?>(null) }
    var prepareError by remember { mutableStateOf<String?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cleanSourceShortcuts, keepCustomImages) {
        if (cleanSourceShortcuts.isEmpty()) {
            preparedUpload = null
            prepareError = "待上传列表为空，请至少选择一个快捷方式"
            isPreparing = false
            return@LaunchedEffect
        }
        isPreparing = true
        prepareError = null
        val res = runCatching {
            MarketSanitizer.prepareShortcutsForUpload(
                shortcuts = cleanSourceShortcuts,
                stripCustomImages = !keepCustomImages,
            )
        }
        isPreparing = false
        res.onSuccess { prepared ->
            preparedUpload = prepared
        }.onFailure { err ->
            preparedUpload = null
            prepareError = err.message ?: "本地预检失败"
        }
    }

    val parsedTags = remember(tagsInput) { parseMarketTagsInput(tagsInput) }
    val inspection = preparedUpload?.inspection
    val canSubmit = !isPreparing &&
        !isUploading &&
        prepareError == null &&
        name.trim().isNotEmpty() &&
        author.trim().isNotEmpty() &&
        preparedUpload != null &&
        (inspection?.isUploadAllowed == true)

    Dialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = if (isSelectionMode) "分享所选快捷方式到市场" else "上传快捷方式到市场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "上传前会自动清零本机坐标偏移并校验动作安全性与图片体积（≤50 顶层 / ≤100 总项 / ≤512 KB）。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(12.dp))
                val folderDesc = if (rawInspection.folderCount > 0) "（含 ${rawInspection.folderCount} 个文件夹）" else ""
                val previewNames = cleanSourceShortcuts.take(4).joinToString("、") {
                    it.title.orEmpty().ifBlank { "未命名" }
                } + if (cleanSourceShortcuts.size > 4) " 等" else ""
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (isSelectionMode) {
                            "已选 ${rawInspection.shortcutCount} 个顶层快捷项$folderDesc · 共 ${rawInspection.totalShortcutCount} 条"
                        } else {
                            "当前列表共 ${rawInspection.shortcutCount} 个顶层快捷项$folderDesc · 总计 ${rawInspection.totalShortcutCount} 条"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (previewNames.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "包含：$previewNames",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextField(
                    value = name,
                    onValueChange = {
                        name = it.take(60)
                        submitError = null
                    },
                    label = "快捷包名称（必填，最多 60 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = author,
                    onValueChange = {
                        author = it.take(40)
                        submitError = null
                    },
                    label = "作者昵称（必填，最多 40 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = description,
                    onValueChange = {
                        description = it.take(500)
                        submitError = null
                    },
                    label = "快捷包简介（可选，说明用途与依赖应用）",
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it.take(180) },
                    label = "标签（逗号或空格分隔，最多 8 个，如：微信, 效率工具）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsedTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        parsedTags.forEach { tag ->
                            MarketBadgePill(
                                text = "#$tag",
                                textColor = MiuixTheme.colorScheme.primary,
                                bgColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        }
                    }
                }

                if (rawInspection.customImageCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    BasicComponent(
                        title = "保留自定义图片（提交后需审核）",
                        summary = "检测到 ${rawInspection.customImageCount} 张本地 base64 图片；关闭将自动剥离并回退应用或默认矢量图标。",
                        endActions = {
                            Switch(
                                checked = keepCustomImages,
                                onCheckedChange = { if (!isUploading) keepCustomImages = it },
                            )
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))
                when {
                    isPreparing -> {
                        MarketBannerBox(
                            text = "正在执行本地校验与自定义图片压缩检查...",
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    prepareError != null -> {
                        MarketBannerBox(
                            text = "本地校验未通过：$prepareError",
                            textColor = MARKET_COLOR_RED,
                            bgColor = MARKET_COLOR_RED_BG,
                        )
                    }
                    inspection != null -> {
                        val sizeText = formatMarketSize(inspection.totalSizeBytes)
                        if (!inspection.isUploadAllowed) {
                            val errMsg = when {
                                inspection.validationError != null -> inspection.validationError
                                inspection.exceedsMaxImageCount -> "自定义图片数量 (${inspection.customImageCount}) 超过 24 张上限"
                                inspection.exceedsPayloadLimit -> "快捷包总大小 ($sizeText) 超过 512 KB 上限，请减少条目或关闭保留自定义图片"
                                else -> "当前内容不符合上传要求"
                            }
                            MarketBannerBox(
                                text = errMsg,
                                textColor = MARKET_COLOR_RED,
                                bgColor = MARKET_COLOR_RED_BG,
                            )
                        } else if (!inspection.needsReview) {
                            val compressNote = if (preparedUpload!!.compressedImageCount > 0) {
                                "，已自动压缩 ${preparedUpload!!.compressedImageCount} 张大图"
                            } else {
                                ""
                            }
                            MarketBannerBox(
                                text = "校验通过，提交后将直接上架（体积 $sizeText$compressNote）",
                                textColor = MARKET_COLOR_GREEN,
                                bgColor = MARKET_COLOR_GREEN_BG,
                            )
                        } else {
                            val reasons = buildList {
                                if (inspection.hasRootRisk) add("Root/高危动作 ${inspection.riskItems.size} 项")
                                if (inspection.customImageCount > 0) add("自定义图片 ${inspection.customImageCount} 张")
                            }.joinToString("、")
                            val compressNote = if (preparedUpload!!.compressedImageCount > 0) {
                                "（已自动压缩 ${preparedUpload!!.compressedImageCount} 张大图至 64 KB 内）"
                            } else {
                                ""
                            }
                            MarketBannerBox(
                                text = "含 $reasons，提交后需等待人工审核（体积 $sizeText）$compressNote",
                                textColor = MARKET_COLOR_AMBER,
                                bgColor = MARKET_COLOR_AMBER_BG,
                            )
                        }
                    }
                }

                if (!submitError.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = submitError!!,
                        textColor = MARKET_COLOR_RED,
                        bgColor = MARKET_COLOR_RED_BG,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "取消",
                        enabled = !isUploading,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = if (isUploading) "上传中..." else "提交上传",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = canSubmit,
                        onClick = {
                            val prepared = preparedUpload ?: return@TextButton
                            val cleanName = name.trim()
                            val cleanAuthor = author.trim()
                            if (cleanName.isEmpty() || cleanAuthor.isEmpty()) {
                                submitError = "请填写快捷包名称与作者昵称"
                                return@TextButton
                            }
                            isUploading = true
                            submitError = null
                            MarketSettings.setLastAuthor(ctx, cleanAuthor)
                            scope.launch {
                                val uploadRes = runCatching {
                                    MarketApiClient.uploadShortcutPack(
                                        ctx = ctx,
                                        name = cleanName,
                                        description = description.trim(),
                                        author = cleanAuthor,
                                        tags = parsedTags,
                                        minAppVersionCode = BuildConfig.VERSION_CODE,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        shortcutsJson = prepared.shortcutsJson,
                                    )
                                }
                                isUploading = false
                                uploadRes.onSuccess { res ->
                                    if (res.id.isNotBlank() && res.manageToken.isNotBlank()) {
                                        MarketSettings.saveManageToken(
                                            ctx,
                                            MarketSettings.KIND_SHORTCUT,
                                            res.id,
                                            res.manageToken,
                                        )
                                    }
                                    onUploaded(res)
                                }.onFailure { err ->
                                    submitError = err.message ?: "上传失败"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
