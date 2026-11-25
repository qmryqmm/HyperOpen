package io.github.qmryqmm.hyperopen.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.config.ConfigBackup
import io.github.qmryqmm.hyperopen.config.ConfigProfile
import io.github.qmryqmm.hyperopen.config.ConfigProfileStore
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ConfigMarketImportMode {
    SAVE_TO_LIBRARY,
    APPLY_DIRECTLY,
}

data class ConfigMarketImportRequest(
    val mode: ConfigMarketImportMode,
    val summary: MarketConfigSummary,
    val rawConfigJson: String,
    val fromSandbox: Boolean = false,
)

data class ConfigMarketUploadOutcome(
    val configResult: MarketUploadResult,
    val linkedShortcutPackId: String? = null,
    val linkedShortcutPackReused: Boolean = false,
    val linkedShortcutPackStatus: String? = null,
    val authorUpdated: Boolean = false,
)

private fun buildDefaultLinkedPackName(configName: String): String {
    val suffix = " · 配套快捷方式"
    val base = configName.trim()
    if (base.isEmpty()) return "配套快捷方式"
    val maxBase = (60 - suffix.length).coerceAtLeast(1)
    return "${base.take(maxBase)}$suffix"
}

/**
 * 市场纯外观配置应用 / 保存到配置库确认弹窗：
 * - 严格仅导入外观参数，绝不覆盖本机快捷项目列表；
 * - 默认勾选「保留本机指纹位置偏移 (offsetY)」；
 * - 直接应用且当前配置未归档/有未同步修改时，默认勾选「应用前备份当前配置」。
 */
@Composable
fun ConfigMarketImportDialog(
    request: ConfigMarketImportRequest,
    existingProfileNames: Set<String>,
    localOffsetY: Int,
    softGlassSupported: Boolean,
    needsBackupSuggestion: Boolean,
    onDismiss: () -> Unit,
    onConfirmSaveToLibrary: (
        profileName: String,
        keepLocalOffsetY: Boolean,
        applyImmediately: Boolean,
        backupCurrentFirst: Boolean,
    ) -> Unit,
    onConfirmApplyDirectly: (
        keepLocalOffsetY: Boolean,
        backupCurrentFirst: Boolean,
    ) -> Unit,
) {
    val summary = request.summary
    val isSaveMode = request.mode == ConfigMarketImportMode.SAVE_TO_LIBRARY
    var profileName by remember(request) {
        mutableStateOf(summary.name.take(ConfigProfileStore.MAX_NAME_LENGTH))
    }
    var keepLocalOffsetY by remember(request) { mutableStateOf(true) }
    var applyImmediately by remember(request) { mutableStateOf(false) }
    var backupCurrentFirst by remember(request, needsBackupSuggestion) {
        mutableStateOf(needsBackupSuggestion)
    }

    val remoteOffsetY = remember(request.rawConfigJson) {
        runCatching {
            val values = JSONObject(request.rawConfigJson).optJSONObject("values")
            val entry = values?.optJSONObject("offset_y")
            if (entry != null && entry.has("v")) entry.optInt("v", 100) else 100
        }.getOrDefault(100)
    }
    val softGlassDowngraded =
        !softGlassSupported && summary.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS
    val showBackupOption = needsBackupSuggestion && (!isSaveMode || applyImmediately)

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
                    text = if (isSaveMode) "保存到本地配置库" else "直接应用市场配置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                val matLabel = displayMarketMaterialLabel(summary.materialStyle, softGlassSupported)
                Text(
                    text = "「${summary.name}」 · ${summary.layoutLabel()} · $matLabel · ${summary.keyCount} 项外观参数",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                if (isSaveMode) {
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(ConfigProfileStore.MAX_NAME_LENGTH) },
                        label = "本地配置名称（最多 ${ConfigProfileStore.MAX_NAME_LENGTH} 字）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (profileName.trim() in existingProfileNames) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "本地列表中已有同名配置，保存时将自动附加序号以区分",
                            fontSize = 12.sp,
                            color = MARKET_COLOR_AMBER,
                        )
                    }
                }

                if (summary.minAppVersionCode > BuildConfig.VERSION_CODE) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = "版本兼容提示：此配置建议模块 versionCode ≥ ${summary.minAppVersionCode}（当前为 ${BuildConfig.VERSION_CODE}），部分新参数将被安全跳过。",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }

                if (softGlassDowngraded) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = "此配置为柔光玻璃材质，当前设备不支持；应用时将自动转为高斯模糊并按磨砂浓度补偿模糊半径。",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }

                Spacer(Modifier.height(12.dp))
                val pureTip = if (summary.linkedShortcutPack != null) {
                    "该配置仅包含面板外观参数，您的本机快捷项目列表将被保留。如需配套快捷方式，可在配置详情查看「${summary.linkedShortcutPack.name}」。"
                } else {
                    "该市场配置仅包含面板外观参数，您的本机快捷项目列表将被保留。"
                }
                MarketBannerBox(
                    text = pureTip,
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                )

                BasicComponent(
                    title = "保留本机指纹位置偏移 (offsetY)",
                    summary = "开启后沿用您当前的 offsetY = ${localOffsetY}dp，避免他人偏移（${remoteOffsetY}dp）覆盖本机指纹对齐位置。",
                    endActions = {
                        Switch(
                            checked = keepLocalOffsetY,
                            onCheckedChange = { keepLocalOffsetY = it },
                        )
                    },
                )

                if (isSaveMode) {
                    BasicComponent(
                        title = "保存后立即应用",
                        summary = "写入配置库的同时立即启用此配置。",
                        endActions = {
                            Switch(
                                checked = applyImmediately,
                                onCheckedChange = { applyImmediately = it },
                            )
                        },
                    )
                }

                if (showBackupOption) {
                    BasicComponent(
                        title = "应用前备份当前配置",
                        summary = "当前生效配置尚未归档或有未保存改动，开启后先另存为备份再切换。",
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
                    val confirmEnabled = !isSaveMode || profileName.isNotBlank()
                    TextButton(
                        text = if (isSaveMode) "确认保存" else "确认应用",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = confirmEnabled,
                        onClick = {
                            if (isSaveMode) {
                                onConfirmSaveToLibrary(
                                    profileName.trim(),
                                    keepLocalOffsetY,
                                    applyImmediately,
                                    applyImmediately && needsBackupSuggestion && backupCurrentFirst,
                                )
                            } else {
                                onConfirmApplyDirectly(
                                    keepLocalOffsetY,
                                    needsBackupSuggestion && backupCurrentFirst,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 配置市场上传弹窗：
 * - 上传纯外观参数配置（自动剔除运行态、App 界面键及快捷项），提交后直接上架；
 * - 接入一设备一作者唯一绑定与改名同步提示；
 * - 若本地配置包含快捷项，支持开启「同时将快捷方式上传到快捷方式市场并关联」（遇 409 自动复用已有快捷包 ID）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigMarketUploadDialog(
    profiles: List<ConfigProfile>,
    initialTargetProfileId: String? = null,
    onDismiss: () -> Unit,
    onShortcutPackSideUploaded: () -> Unit = {},
    onUploaded: (ConfigMarketUploadOutcome) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialProfile = remember(profiles, initialTargetProfileId) {
        if (initialTargetProfileId == null) null
        else profiles.firstOrNull { it.id == initialTargetProfileId }
    }
    var selectedProfileId by remember(initialTargetProfileId) {
        mutableStateOf(initialProfile?.id)
    }
    val selectedProfile = remember(profiles, selectedProfileId) {
        selectedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
    }

    var name by remember(initialTargetProfileId) {
        mutableStateOf((initialProfile?.name ?: "").take(60))
    }
    var nameTouched by remember { mutableStateOf(false) }
    var author by remember { mutableStateOf(MarketSettings.getLastAuthor(ctx)) }
    var authorTouched by remember { mutableStateOf(false) }
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
    var description by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }

    var linkShortcutPack by remember { mutableStateOf(false) }
    var linkedPackName by remember(initialTargetProfileId) {
        mutableStateOf(buildDefaultLinkedPackName(initialProfile?.name ?: ""))
    }
    var linkedPackNameTouched by remember { mutableStateOf(false) }
    var keepCustomImages by remember { mutableStateOf(true) }

    var showSourcePicker by remember { mutableStateOf(false) }
    var isPreparingConfig by remember { mutableStateOf(false) }
    var preparedConfig by remember { mutableStateOf<MarketSanitizer.PreparedConfigUpload?>(null) }
    var configPrepareError by remember { mutableStateOf<String?>(null) }

    var sourceShortcuts by remember { mutableStateOf<List<ShortcutItem>>(emptyList()) }
    var isPreparingShortcuts by remember { mutableStateOf(false) }
    var preparedShortcuts by remember { mutableStateOf<MarketSanitizer.PreparedShortcutsUpload?>(null) }
    var shortcutPrepareError by remember { mutableStateOf<String?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val fetched = runCatching { MarketApiClient.getMyAuthor(ctx) }.getOrNull()
        boundAuthor = fetched
        if (fetched != null && !authorTouched) {
            author = fetched.displayName
        }
    }

    // 预检纯外观配置并提取所选配置中的快捷方式（若存在）
    LaunchedEffect(selectedProfileId, selectedProfile?.updatedAt) {
        isPreparingConfig = true
        configPrepareError = null
        val sourceJson = selectedProfile?.backupJson
        val res = runCatching {
            val prepared = MarketSanitizer.prepareConfigForUpload(
                ctx = ctx,
                sourceBackupJson = sourceJson,
                appVersion = BuildConfig.VERSION_NAME,
            )
            val rawCheckJson = withContext(Dispatchers.IO) {
                sourceJson ?: ConfigBackup.buildExportJson(ctx, true, false)
            }
            val parsedShortcuts = withContext(Dispatchers.Default) {
                deduplicateShortcutsForUpload(
                    ConfigBackup.parseShortcutsFromBackup(rawCheckJson).orEmpty()
                )
            }
            prepared to parsedShortcuts
        }
        isPreparingConfig = false
        res.onSuccess { (prepared, shortcuts) ->
            preparedConfig = prepared
            sourceShortcuts = shortcuts
            if (shortcuts.isEmpty()) {
                linkShortcutPack = false
            }
        }.onFailure { err ->
            preparedConfig = null
            sourceShortcuts = emptyList()
            linkShortcutPack = false
            configPrepareError = err.message ?: "本地配置校验失败"
        }
    }

    val rawShortcutInspection = remember(sourceShortcuts) {
        if (sourceShortcuts.isEmpty()) null
        else MarketSanitizer.inspectShortcuts(sourceShortcuts, stripCustomImages = false)
    }

    // 开启「同时将快捷方式上传到快捷方式市场并关联」时对快捷方式执行压缩与安全预检
    LaunchedEffect(sourceShortcuts, linkShortcutPack, keepCustomImages) {
        if (!linkShortcutPack || sourceShortcuts.isEmpty()) {
            preparedShortcuts = null
            shortcutPrepareError = null
            isPreparingShortcuts = false
            return@LaunchedEffect
        }
        isPreparingShortcuts = true
        shortcutPrepareError = null
        val res = runCatching {
            MarketSanitizer.prepareShortcutsForUpload(
                shortcuts = sourceShortcuts,
                stripCustomImages = !keepCustomImages,
            )
        }
        isPreparingShortcuts = false
        res.onSuccess { prepared ->
            preparedShortcuts = prepared
        }.onFailure { err ->
            preparedShortcuts = null
            shortcutPrepareError = err.message ?: "配套快捷方式预检失败"
        }
    }

    val boundDisplayName = boundAuthor?.displayName?.trim().orEmpty()
    val isBound = boundAuthor != null
    val isRenamingAuthor = isBound &&
        boundDisplayName.isNotEmpty() &&
        author.trim().isNotEmpty() &&
        author.trim() != boundDisplayName

    val parsedTags = remember(tagsInput) { parseMarketTagsInput(tagsInput) }
    val configInspection = preparedConfig?.inspection
    val shortcutInspection = preparedShortcuts?.inspection

    val configReady = !isPreparingConfig &&
        configPrepareError == null &&
        preparedConfig != null &&
        (configInspection?.isUploadAllowed == true)
    val linkedPackReady = !linkShortcutPack || (
        !isPreparingShortcuts &&
            shortcutPrepareError == null &&
            linkedPackName.trim().isNotEmpty() &&
            preparedShortcuts != null &&
            (shortcutInspection?.isUploadAllowed == true)
        )
    val canSubmit = !isUploading &&
        name.trim().isNotEmpty() &&
        author.trim().isNotEmpty() &&
        configReady &&
        linkedPackReady

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
                    text = "上传外观配置到市场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "配置市场仅分享纯外观参数，自动剔除运行态与个人快捷项；若需要，可选择将快捷方式同步上传并关联。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "上传来源",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .clickable(enabled = !isUploading) { showSourcePicker = !showSourcePicker }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedProfile?.name ?: "当前生效配置",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (selectedProfile != null) {
                                "${selectedProfile.layoutLabel()} · ${selectedProfile.materialLabel()}"
                            } else {
                                "提取当前正在生效的面板外观参数"
                            },
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = if (showSourcePicker) "收起 ▲" else "切换 ▼",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }

                if (showSourcePicker) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        UploadSourceOptionRow(
                            title = "当前生效配置",
                            subtitle = "提取本机实时生效的外观参数",
                            selected = selectedProfileId == null,
                            onClick = {
                                selectedProfileId = null
                                showSourcePicker = false
                            },
                        )
                        profiles.forEach { profile ->
                            UploadSourceOptionRow(
                                title = profile.name,
                                subtitle = "${profile.layoutLabel()} · ${profile.materialLabel()}",
                                selected = selectedProfileId == profile.id,
                                onClick = {
                                    selectedProfileId = profile.id
                                    if (!nameTouched || name.isBlank()) {
                                        val nextName = profile.name.take(60)
                                        name = nextName
                                        if (!linkedPackNameTouched) {
                                            linkedPackName = buildDefaultLinkedPackName(nextName)
                                        }
                                    }
                                    showSourcePicker = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextField(
                    value = name,
                    onValueChange = {
                        nameTouched = true
                        name = it.take(60)
                        if (!linkedPackNameTouched) {
                            linkedPackName = buildDefaultLinkedPackName(name)
                        }
                        submitError = null
                    },
                    label = "配置名称（必填，最多 60 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = author,
                    onValueChange = {
                        authorTouched = true
                        author = it.take(40)
                        submitError = null
                    },
                    label = "作者昵称（必填，最多 40 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (boundAuthor?.isOfficial == true) {
                    Spacer(Modifier.height(6.dp))
                    MarketAuthorBadgeRow(
                        author = boundDisplayName,
                        isOfficial = true,
                        badgeLabel = boundAuthor?.badgeLabel.orEmpty(),
                        prefix = "本机认证身份：",
                        fontSize = 11.sp,
                    )
                }
                if (isRenamingAuthor) {
                    Spacer(Modifier.height(6.dp))
                    MarketBannerBox(
                        text = "修改昵称将同步更新你之前发布的所有配置和快捷方式",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = description,
                    onValueChange = {
                        description = it.take(500)
                        submitError = null
                    },
                    label = "配置简介（可选，说明风格与适用机型）",
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it.take(180) },
                    label = "标签（逗号或空格分隔，最多 8 个，如：柔光玻璃, 极简）",
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

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))

                if (sourceShortcuts.isNotEmpty() && rawShortcutInspection != null) {
                    BasicComponent(
                        title = "同时将快捷方式上传到快捷方式市场并关联",
                        summary = "检测到当前配置含 ${rawShortcutInspection.shortcutCount} 个快捷项；开启后将同步上传配套快捷包并在配置详情展示入口。",
                        endActions = {
                            Switch(
                                checked = linkShortcutPack,
                                onCheckedChange = { checked ->
                                    if (!isUploading) {
                                        linkShortcutPack = checked
                                        submitError = null
                                        if (checked && (!linkedPackNameTouched || linkedPackName.isBlank())) {
                                            linkedPackName = buildDefaultLinkedPackName(name)
                                        }
                                    }
                                },
                            )
                        },
                    )
                }

                if (linkShortcutPack && rawShortcutInspection != null) {
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = linkedPackName,
                        onValueChange = {
                            linkedPackNameTouched = true
                            linkedPackName = it.take(60)
                            submitError = null
                        },
                        label = "配套快捷包名称（必填，最多 60 字）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val folderNote = if (rawShortcutInspection.folderCount > 0) {
                        "（含 ${rawShortcutInspection.folderCount} 个文件夹）"
                    } else {
                        ""
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "配套快捷包统计：${rawShortcutInspection.shortcutCount} 个顶层项$folderNote · 总计 ${rawShortcutInspection.totalShortcutCount} 条",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (rawShortcutInspection.customImageCount > 0 || rawShortcutInspection.hasRootRisk) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (rawShortcutInspection.customImageCount > 0) {
                                    MarketBadgePill(
                                        text = "${rawShortcutInspection.customImageCount} 自定义图",
                                        textColor = MARKET_COLOR_AMBER,
                                        bgColor = MARKET_COLOR_AMBER_BG,
                                    )
                                }
                                if (rawShortcutInspection.hasRootRisk) {
                                    MarketBadgePill(
                                        text = "含 Root/高危 ${rawShortcutInspection.riskItems.size} 项",
                                        textColor = MARKET_COLOR_RED,
                                        bgColor = MARKET_COLOR_RED_BG,
                                    )
                                }
                            }
                        }
                    }

                    if (rawShortcutInspection.customImageCount > 0) {
                        BasicComponent(
                            title = "保留自定义图片（提交后需审核）",
                            summary = "检测到 ${rawShortcutInspection.customImageCount} 张本地 base64 图片；关闭将自动剥离并回退应用或矢量图标。",
                            endActions = {
                                Switch(
                                    checked = keepCustomImages,
                                    onCheckedChange = { if (!isUploading) keepCustomImages = it },
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                when {
                    isPreparingConfig -> {
                        MarketBannerBox(
                            text = "正在提取并校验纯外观配置参数...",
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    configPrepareError != null -> {
                        MarketBannerBox(
                            text = "配置参数校验未通过：$configPrepareError",
                            textColor = MARKET_COLOR_RED,
                            bgColor = MARKET_COLOR_RED_BG,
                        )
                    }
                    configInspection != null && !configInspection.isUploadAllowed -> {
                        val errMsg = configInspection.validationError ?: "当前外观配置不符合上传要求"
                        MarketBannerBox(
                            text = errMsg,
                            textColor = MARKET_COLOR_RED,
                            bgColor = MARKET_COLOR_RED_BG,
                        )
                    }
                    !linkShortcutPack -> {
                        val sizeText = formatMarketSize(configInspection?.totalSizeBytes ?: 0)
                        MarketBannerBox(
                            text = "外观配置不含快捷方式，提交后将直接上架（体积 $sizeText）",
                            textColor = MARKET_COLOR_GREEN,
                            bgColor = MARKET_COLOR_GREEN_BG,
                        )
                    }
                    isPreparingShortcuts -> {
                        MarketBannerBox(
                            text = "正在执行配套快捷包校验与自定义图片压缩检查...",
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    shortcutPrepareError != null -> {
                        MarketBannerBox(
                            text = "配套快捷包校验未通过：$shortcutPrepareError",
                            textColor = MARKET_COLOR_RED,
                            bgColor = MARKET_COLOR_RED_BG,
                        )
                    }
                    shortcutInspection != null && !shortcutInspection.isUploadAllowed -> {
                        val sSizeText = formatMarketSize(shortcutInspection.totalSizeBytes)
                        val errMsg = when {
                            shortcutInspection.validationError != null -> shortcutInspection.validationError
                            shortcutInspection.exceedsMaxImageCount -> "配套快捷包自定义图片 (${shortcutInspection.customImageCount}) 超过 24 张上限"
                            shortcutInspection.exceedsPayloadLimit -> "配套快捷包体积 ($sSizeText) 超过 512 KB 上限，请减少条目或关闭保留图片"
                            else -> "配套快捷包不符合上传要求"
                        }
                        MarketBannerBox(
                            text = errMsg,
                            textColor = MARKET_COLOR_RED,
                            bgColor = MARKET_COLOR_RED_BG,
                        )
                    }
                    shortcutInspection != null -> {
                        val cfgSize = formatMarketSize(configInspection?.totalSizeBytes ?: 0)
                        val packSize = formatMarketSize(shortcutInspection.totalSizeBytes)
                        val compressNote = if ((preparedShortcuts?.compressedImageCount ?: 0) > 0) {
                            "，已压缩 ${preparedShortcuts?.compressedImageCount} 张大图"
                        } else {
                            ""
                        }
                        MarketBannerBox(
                            text = "外观配置将直接上架（$cfgSize）；配套快捷包将同步上传至快捷方式市场（$packSize$compressNote，含自定义图或 Root 需审核，过审后配置详情会自动亮出关联入口）",
                            textColor = if (shortcutInspection.needsReview) MARKET_COLOR_AMBER else MARKET_COLOR_GREEN,
                            bgColor = if (shortcutInspection.needsReview) MARKET_COLOR_AMBER_BG else MARKET_COLOR_GREEN_BG,
                        )
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
                            val preparedCfg = preparedConfig ?: return@TextButton
                            val cleanName = name.trim()
                            val cleanAuthor = author.trim()
                            if (cleanName.isEmpty() || cleanAuthor.isEmpty()) {
                                submitError = "请填写配置名称与作者昵称"
                                return@TextButton
                            }
                            val shouldLink = linkShortcutPack && sourceShortcuts.isNotEmpty()
                            val preparedPack = if (shouldLink) preparedShortcuts else null
                            val cleanPackName = linkedPackName.trim()
                            if (shouldLink && (preparedPack == null || cleanPackName.isEmpty())) {
                                submitError = "请填写配套快捷包名称"
                                return@TextButton
                            }

                            isUploading = true
                            submitError = null
                            val renamedOnSubmit = isRenamingAuthor
                            scope.launch {
                                var linkedPackId: String? = null
                                var linkedPackReused = false
                                var linkedPackStatus: String? = null
                                var shortcutPackJustUploaded = false

                                if (shouldLink && preparedPack != null) {
                                    val packRes = runCatching {
                                        MarketApiClient.uploadShortcutPack(
                                            ctx = ctx,
                                            name = cleanPackName,
                                            description = "配置「$cleanName」配套快捷方式",
                                            author = cleanAuthor,
                                            tags = parsedTags,
                                            minAppVersionCode = BuildConfig.VERSION_CODE,
                                            appVersion = BuildConfig.VERSION_NAME,
                                            shortcutsJson = preparedPack.shortcutsJson,
                                        )
                                    }
                                    val packErr = packRes.exceptionOrNull()
                                    if (packErr != null) {
                                        if (packErr is MarketApiException &&
                                            packErr.statusCode == 409 &&
                                            !packErr.existingId.isNullOrBlank()
                                        ) {
                                            linkedPackId = packErr.existingId
                                            linkedPackReused = true
                                            linkedPackStatus = packErr.existingStatus
                                        } else {
                                            isUploading = false
                                            submitError = packErr.message ?: "配套快捷包上传失败"
                                            return@launch
                                        }
                                    } else {
                                        val uploadedPack = packRes.getOrNull()!!
                                        linkedPackId = uploadedPack.id.takeIf { it.isNotBlank() }
                                        linkedPackStatus = uploadedPack.status
                                        shortcutPackJustUploaded = true
                                        if (uploadedPack.id.isNotBlank() && uploadedPack.manageToken.isNotBlank()) {
                                            MarketSettings.saveManageToken(
                                                ctx,
                                                MarketSettings.KIND_SHORTCUT,
                                                uploadedPack.id,
                                                uploadedPack.manageToken,
                                            )
                                        }
                                        MarketSettings.setLastAuthor(
                                            ctx,
                                            uploadedPack.shortcutPackItem?.author ?: cleanAuthor,
                                        )
                                        onShortcutPackSideUploaded()
                                    }
                                }

                                val uploadRes = runCatching {
                                    MarketApiClient.uploadConfig(
                                        ctx = ctx,
                                        name = cleanName,
                                        description = description.trim(),
                                        author = cleanAuthor,
                                        tags = parsedTags,
                                        minAppVersionCode = BuildConfig.VERSION_CODE,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        jsonBody = preparedCfg.jsonBody,
                                        linkedShortcutPackId = linkedPackId,
                                    )
                                }
                                isUploading = false
                                uploadRes.onSuccess { res ->
                                    MarketSettings.setLastAuthor(ctx, res.configItem?.author ?: cleanAuthor)
                                    if (res.id.isNotBlank() && res.manageToken.isNotBlank()) {
                                        MarketSettings.saveManageToken(
                                            ctx,
                                            MarketSettings.KIND_CONFIG,
                                            res.id,
                                            res.manageToken,
                                        )
                                    }
                                    onUploaded(
                                        ConfigMarketUploadOutcome(
                                            configResult = res,
                                            linkedShortcutPackId = linkedPackId,
                                            linkedShortcutPackReused = linkedPackReused,
                                            linkedShortcutPackStatus = linkedPackStatus,
                                            authorUpdated = renamedOnSubmit || shortcutPackJustUploaded,
                                        )
                                    )
                                }.onFailure { err ->
                                    val prefix = if (shortcutPackJustUploaded) {
                                        "配套快捷包已上传，但外观配置上传失败："
                                    } else {
                                        ""
                                    }
                                    submitError = "$prefix${err.message ?: "上传失败"}"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadSourceOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) primary.copy(alpha = 0.14f)
                else MiuixTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) primary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "已选",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
            )
        }
    }
}
