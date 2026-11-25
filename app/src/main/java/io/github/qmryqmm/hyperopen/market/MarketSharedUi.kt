package io.github.qmryqmm.hyperopen.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val MARKET_COLOR_AMBER = Color(0xFFFAAD14)
internal val MARKET_COLOR_AMBER_BG = Color(0x1FFAAD14)
internal val MARKET_COLOR_RED = Color(0xFFE53935)
internal val MARKET_COLOR_RED_BG = Color(0x1FE53935)
internal val MARKET_COLOR_GREEN = Color(0xFF2E9E5B)
internal val MARKET_COLOR_GREEN_BG = Color(0x1F2E9E5B)

fun formatMarketShortTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatMarketFullTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatMarketSize(bytes: Int): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.1f KB", kb)
}

fun displayMarketMaterialLabel(materialStyle: Int, softGlassSupported: Boolean): String {
    val base = if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) "柔光玻璃" else "高斯模糊"
    return if (!softGlassSupported && materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
        "柔光玻璃(转高斯)"
    } else {
        base
    }
}

fun parseMarketTagsInput(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val result = ArrayList<String>()
    val seen = HashSet<String>()
    raw.split(',', '，', ';', '；', ' ', '\n', '\t')
        .map { it.trim().trimStart('#').trim() }
        .filter { it.isNotEmpty() }
        .forEach { tag ->
            val clipped = tag.take(20)
            if (clipped.isNotEmpty() && seen.add(clipped) && result.size < 8) {
                result.add(clipped)
            }
        }
    return result
}

/** 顶部透明说明卡 + 服务器地址设置与上传工具按钮（Stage 2/3 共用）。 */
@Composable
fun MarketTopNoticeCard(
    onOpenServerSettings: () -> Unit,
    onOpenUpload: () -> Unit,
    uploadLabel: String = "上传配置",
    modifier: Modifier = Modifier,
) {
    val primary = MiuixTheme.colorScheme.primary
    val outline = MiuixTheme.colorScheme.outline.copy(alpha = 0.28f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(primary.copy(alpha = 0.05f))
            .border(1.dp, outline, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Info,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "仅市场页主动联网，SystemUI 进程绝对零联网，本地配置完全离线可用",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onOpenServerSettings,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Settings,
                contentDescription = "服务器地址设置",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(primary.copy(alpha = 0.14f))
                .clickable(onClick = onOpenUpload)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.UploadCloud,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = uploadLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MarketBadgePill(
    text: String,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 作者名与官方徽章展示行（isOfficial 为 true 时展示醒目徽章）。 */
@Composable
fun MarketAuthorBadgeRow(
    author: String,
    isOfficial: Boolean,
    badgeLabel: String,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    fontSize: TextUnit = 11.sp,
    textColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    val primary = MiuixTheme.colorScheme.primary
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$prefix${author.ifBlank { "匿名" }}",
            fontSize = fontSize,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isOfficial) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeLabel.ifBlank { "官方" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                fontSize = fontSize,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MarketFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val bgColor = if (selected) primary.copy(alpha = 0.15f) else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val textColor = if (selected) primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun MarketBannerBox(
    text: String,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun MarketRiskWarningBox(
    riskItems: List<MarketRiskEntry>,
    title: String = "高危动作提示：包含 ${riskItems.size} 项敏感操作",
    subtitle: String = "以下快捷项涉及 Shell、Root 权限、自定义广播或敏感 Intent，请确认来源可信：",
) {
    if (riskItems.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MARKET_COLOR_RED_BG)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MARKET_COLOR_RED,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MARKET_COLOR_RED,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            riskItems.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    val header = buildString {
                        append(entry.path.ifBlank { entry.title.ifBlank { "未命名快捷项" } })
                        if (entry.actionType.isNotBlank()) append(" [${entry.actionType}]")
                    }
                    Text(
                        text = header,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (entry.reason.isNotBlank()) {
                        Text(
                            text = "风险原因：${entry.reason}",
                            fontSize = 11.sp,
                            color = MARKET_COLOR_RED,
                        )
                    }
                    if (entry.payload.isNotBlank()) {
                        Text(
                            text = "动作载荷：${entry.payload}",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 市场服务器地址与本机创作者身份设置弹窗（支持连通性测试、查看官方徽章及全量改名同步）。 */
@Composable
fun MarketServerSettingsDialog(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onAuthorUpdated: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(MarketSettings.getBaseUrl(ctx)) }
    var testing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var authorReloadTick by remember { mutableIntStateOf(0) }
    var isLoadingAuthor by remember { mutableStateOf(true) }
    var authorLoadError by remember { mutableStateOf<String?>(null) }
    var boundAuthor by remember {
        mutableStateOf(
            MarketSettings.getAuthorId(ctx)?.let { id ->
                MarketAuthorProfile(
                    id = id,
                    displayName = MarketSettings.getLastAuthor(ctx),
                    isOfficial = MarketSettings.isAuthorOfficial(ctx),
                    badgeLabel = MarketSettings.getAuthorBadgeLabel(ctx),
                )
            }
        )
    }
    var authorInput by remember {
        mutableStateOf(
            if (MarketSettings.getAuthorId(ctx) != null) MarketSettings.getLastAuthor(ctx) else ""
        )
    }
    var authorInputTouched by remember { mutableStateOf(false) }
    var isUpdatingAuthor by remember { mutableStateOf(false) }
    var authorFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(authorReloadTick) {
        isLoadingAuthor = true
        authorLoadError = null
        val res = runCatching { MarketApiClient.getMyAuthor(ctx) }
        isLoadingAuthor = false
        res.onSuccess { profile ->
            boundAuthor = profile
            if (!authorInputTouched) {
                authorInput = profile?.displayName.orEmpty()
            }
        }.onFailure { err ->
            authorLoadError = err.message ?: "无法同步云端创作者信息"
        }
    }

    Dialog(
        onDismissRequest = { if (!testing && !isUpdatingAuthor) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "市场服务器与创作者设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "仅影响模块设置页内的在线市场请求，绝不写入系统配置或传递给 SystemUI。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        feedback = null
                    },
                    label = "API Base URL (http:// 或 https://)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val normalized = MarketSettings.normalizeBaseUrl(urlInput)
                            if (normalized == null) {
                                feedback = false to "请输入有效的 http:// 或 https:// 地址"
                            } else {
                                MarketSettings.setBaseUrl(ctx, normalized)
                                onSaved(normalized)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "恢复默认",
                        enabled = !testing && !isUpdatingAuthor,
                        onClick = {
                            urlInput = MarketSettings.DEFAULT_BASE_URL
                            feedback = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = if (testing) "测试中..." else "测试连接",
                        enabled = !testing && !isUpdatingAuthor,
                        onClick = {
                            val normalized = MarketSettings.normalizeBaseUrl(urlInput)
                            if (normalized == null) {
                                feedback = false to "请输入有效的 http:// 或 https:// 地址"
                                return@TextButton
                            }
                            testing = true
                            feedback = null
                            val previous = MarketSettings.getBaseUrl(ctx)
                            scope.launch {
                                val okResult = runCatching {
                                    MarketSettings.setBaseUrl(ctx, normalized)
                                    MarketApiClient.checkHealth(ctx)
                                }
                                if (okResult.isFailure) {
                                    MarketSettings.setBaseUrl(ctx, previous)
                                }
                                testing = false
                                okResult.onSuccess { ok ->
                                    if (ok) {
                                        feedback = true to "连接成功（已自动应用新地址）"
                                        authorInputTouched = false
                                        authorReloadTick++
                                    } else {
                                        MarketSettings.setBaseUrl(ctx, previous)
                                        feedback = false to "服务器返回状态异常"
                                    }
                                }.onFailure { err ->
                                    feedback = false to (err.message ?: "连接失败")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                feedback?.let { (ok, msg) ->
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = msg,
                        textColor = if (ok) MARKET_COLOR_GREEN else MARKET_COLOR_RED,
                        bgColor = if (ok) MARKET_COLOR_GREEN_BG else MARKET_COLOR_RED_BG,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "创作者身份与昵称",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "一设备对应唯一创作者昵称；修改昵称将同步更新你之前发布的所有配置和快捷方式。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val activeProfile = boundAuthor
                    when {
                        isLoadingAuthor && activeProfile == null -> {
                            Text(
                                text = "正在获取本机创作者身份...",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        activeProfile != null -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MarketAuthorBadgeRow(
                                    author = activeProfile.displayName,
                                    isOfficial = activeProfile.isOfficial,
                                    badgeLabel = activeProfile.badgeLabel,
                                    prefix = "当前绑定：",
                                    fontSize = 13.sp,
                                    textColor = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isLoadingAuthor) {
                                    Text(
                                        text = "同步中...",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            if (activeProfile.isOfficial) {
                                val badgeText = activeProfile.badgeLabel.ifBlank { "官方" }
                                Text(
                                    text = "已获得官方认证徽章「$badgeText」，将在你的全部市场作品中展示。",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "当前设备尚未绑定创作者昵称（可在此直接设置，或在首次上传作品时自动绑定）。",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    if (!authorLoadError.isNullOrBlank()) {
                        Text(
                            text = authorLoadError!!,
                            fontSize = 11.sp,
                            color = MARKET_COLOR_AMBER,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                TextField(
                    value = authorInput,
                    onValueChange = {
                        authorInput = it.take(40)
                        authorInputTouched = true
                        authorFeedback = null
                    },
                    label = "作者昵称（1~40 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = if (isUpdatingAuthor) "修改中..." else "修改昵称",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !testing && !isUpdatingAuthor && authorInput.trim().isNotEmpty(),
                        onClick = {
                            val cleanName = authorInput.trim()
                            if (cleanName.isEmpty()) {
                                authorFeedback = false to "作者昵称不能为空"
                                return@TextButton
                            }
                            isUpdatingAuthor = true
                            authorFeedback = null
                            scope.launch {
                                val res = runCatching {
                                    MarketApiClient.updateMyAuthorName(ctx, cleanName)
                                }
                                isUpdatingAuthor = false
                                res.onSuccess { updated ->
                                    boundAuthor = updated
                                    authorInput = updated.displayName
                                    authorInputTouched = false
                                    authorFeedback = true to "昵称已同步为「${updated.displayName}」"
                                    onAuthorUpdated()
                                }.onFailure { err ->
                                    authorFeedback = false to (err.message ?: "修改昵称失败")
                                }
                            }
                        },
                    )
                }

                authorFeedback?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp))
                    MarketBannerBox(
                        text = msg,
                        textColor = if (ok) MARKET_COLOR_GREEN else MARKET_COLOR_RED,
                        bgColor = if (ok) MARKET_COLOR_GREEN_BG else MARKET_COLOR_RED_BG,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "取消",
                        enabled = !testing && !isUpdatingAuthor,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = "保存地址",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !testing && !isUpdatingAuthor,
                        onClick = {
                            val normalized = MarketSettings.normalizeBaseUrl(urlInput)
                            if (normalized == null) {
                                feedback = false to "请输入有效的 http:// 或 https:// 地址"
                            } else {
                                MarketSettings.setBaseUrl(ctx, normalized)
                                onSaved(normalized)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 举报条目弹窗（Stage 2/3 共用）。 */
@Composable
fun MarketReportDialog(
    itemName: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (reason: String) -> Unit,
) {
    var reason by remember(itemName) { mutableStateOf("") }
    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "举报条目",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "若「$itemName」包含恶意脚本、失效内容或违规信息，可提交举报说明。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = "举报原因（可选，最多 300 字）",
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = errorMessage,
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
                        enabled = !isSubmitting,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = if (isSubmitting) "提交中..." else "提交举报",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !isSubmitting,
                        onClick = { onSubmit(reason.trim()) },
                    )
                }
            }
        }
    }
}

/** 作者使用本机 manageToken 撤回删除确认弹窗（Stage 2/3 共用）。 */
@Composable
fun MarketDeleteConfirmDialog(
    itemName: String,
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "撤回并删除已上传条目？",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "将使用本机保存的管理凭证从市场中永久移除「$itemName」，此操作不可撤销。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = errorMessage,
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
                        enabled = !isDeleting,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = if (isDeleting) "删除中..." else "确认删除",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !isDeleting,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

/**
 * 纯内存沙箱预览顶部/底部悬浮操作栏（用于 subPage == 3 沙箱预览模式）：
 * 展示名称与作者，并提供「退出」「保存到配置库」「直接应用」入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketSandboxFloatingBar(
    preview: MarketSandboxPreview,
    onExit: () -> Unit,
    onSaveToLibrary: (() -> Unit)? = null,
    onApplyDirectly: (() -> Unit)? = null,
    exitLabel: String = "退出",
    primaryLabel: String = "直接应用",
    secondaryLabel: String = "保存到配置库",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarketBadgePill(
                    text = "沙箱预览",
                    textColor = MiuixTheme.colorScheme.primary,
                    bgColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = preview.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                MarketAuthorBadgeRow(
                    author = preview.author,
                    isOfficial = preview.authorIsOfficial,
                    badgeLabel = preview.authorBadgeLabel,
                    prefix = "by ",
                    fontSize = 11.sp,
                )
            }
            if (preview.kind == MarketSandboxKind.CONFIG && preview.previewingWithLinkedPack) {
                Spacer(Modifier.height(6.dp))
                val packLabel = preview.linkedPackName.orEmpty().ifBlank { "已加载" }
                MarketBadgePill(
                    text = "含配套快捷包：$packLabel",
                    textColor = MARKET_COLOR_GREEN,
                    bgColor = MARKET_COLOR_GREEN_BG,
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SandboxActionPill(
                    text = exitLabel,
                    highlighted = false,
                    onClick = onExit,
                )
                if (onSaveToLibrary != null) {
                    SandboxActionPill(
                        text = secondaryLabel,
                        highlighted = false,
                        onClick = onSaveToLibrary,
                    )
                }
                if (onApplyDirectly != null) {
                    SandboxActionPill(
                        text = primaryLabel,
                        highlighted = true,
                        onClick = onApplyDirectly,
                    )
                }
            }
        }
    }
}

@Composable
private fun SandboxActionPill(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val bgColor = if (highlighted) primary else MiuixTheme.colorScheme.surfaceVariant
    val textColor = if (highlighted) Color.White else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}
