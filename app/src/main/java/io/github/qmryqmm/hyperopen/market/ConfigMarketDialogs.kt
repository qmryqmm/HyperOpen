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

/**
 * 市场配置应用 / 保存到配置库确认弹窗：
 * - 若远程配置含快捷项目，提供「同时导入快捷项目」开关（默认开启）；
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
        includeShortcuts: Boolean,
        keepLocalOffsetY: Boolean,
        applyImmediately: Boolean,
        backupCurrentFirst: Boolean,
    ) -> Unit,
    onConfirmApplyDirectly: (
        includeShortcuts: Boolean,
        keepLocalOffsetY: Boolean,
        backupCurrentFirst: Boolean,
    ) -> Unit,
) {
    val summary = request.summary
    val isSaveMode = request.mode == ConfigMarketImportMode.SAVE_TO_LIBRARY
    var profileName by remember(request) {
        mutableStateOf(summary.name.take(ConfigProfileStore.MAX_NAME_LENGTH))
    }
    var includeShortcuts by remember(request) { mutableStateOf(summary.hasShortcuts) }
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
    val finalIncludeShortcuts = summary.hasShortcuts && includeShortcuts
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
                val shortcutMeta = if (summary.hasShortcuts) "${summary.shortcutCount} 个快捷项" else "仅参数"
                Text(
                    text = "「${summary.name}」 · ${summary.layoutLabel()} · $matLabel · ${summary.keyCount} 项参数 · $shortcutMeta",
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

                if (summary.hasRootRisk || summary.riskSummary.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    if (finalIncludeShortcuts) {
                        MarketRiskWarningBox(
                            riskItems = summary.riskSummary,
                            title = "安全提示：包含 ${summary.riskSummary.size} 个高危快捷项",
                            subtitle = "以下动作涉及 Shell、Root 或敏感广播，请确认作者「${summary.author}」可信：",
                        )
                    } else {
                        MarketBannerBox(
                            text = "该配置包含 ${summary.riskSummary.size} 个高危快捷项，当前已关闭「同时导入快捷项目」，不会写入这些动作。",
                            textColor = MARKET_COLOR_AMBER,
                            bgColor = MARKET_COLOR_AMBER_BG,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (summary.hasShortcuts) {
                    BasicComponent(
                        title = "同时导入快捷项目",
                        summary = "导入此配置自带的 ${summary.shortcutCount} 个快捷项目；关闭后仅导入面板参数，保留您本机原有快捷项。",
                        endActions = {
                            Switch(
                                checked = includeShortcuts,
                                onCheckedChange = { includeShortcuts = it },
                            )
                        },
                    )
                } else {
                    MarketBannerBox(
                        text = "该市场配置仅包含面板参数，不含快捷项目；您的本机快捷项目列表将被保留。",
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }

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
                                    finalIncludeShortcuts,
                                    keepLocalOffsetY,
                                    applyImmediately,
                                    applyImmediately && needsBackupSuggestion && backupCurrentFirst,
                                )
                            } else {
                                onConfirmApplyDirectly(
                                    finalIncludeShortcuts,
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
 * - 支持上传「当前生效配置」或本地配置库中的指定 ConfigProfile；
 * - 支持名称、作者（自动预填）、简介、标签；
 * - 「包含快捷项目」默认关闭；当开启且存在 base64: 图片时显示「保留自定义图片（提交后需审核）」开关；
 * - 实时调用 MarketSanitizer 展示本地脱敏与审核预检状态，上传中防重入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigMarketUploadDialog(
    profiles: List<ConfigProfile>,
    initialTargetProfileId: String? = null,
    onDismiss: () -> Unit,
    onUploaded: (MarketUploadResult) -> Unit,
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
    var description by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var includeShortcuts by remember { mutableStateOf(false) }
    var keepCustomImages by remember { mutableStateOf(true) }

    var showSourcePicker by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var preparedUpload by remember { mutableStateOf<MarketSanitizer.PreparedConfigUpload?>(null) }
    var rawShortcutImageCount by remember { mutableStateOf(0) }
    var prepareError by remember { mutableStateOf<String?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    // 实时预检：切换目标配置 / 包含快捷项 / 保留自定义图时在 IO 线程运行 MarketSanitizer
    LaunchedEffect(selectedProfileId, selectedProfile?.updatedAt, includeShortcuts, keepCustomImages) {
        isPreparing = true
        prepareError = null
        val sourceJson = selectedProfile?.backupJson
        val res = runCatching {
            val rawCheckJson = withContext(Dispatchers.IO) {
                sourceJson ?: ConfigBackup.buildExportJson(ctx, true, false)
            }
            val rawImgCount = if (includeShortcuts) {
                val shortcuts = ConfigBackup.parseShortcutsFromBackup(rawCheckJson).orEmpty()
                MarketSanitizer.inspectShortcuts(shortcuts, stripCustomImages = false).customImageCount
            } else {
                0
            }
            val prepared = MarketSanitizer.prepareConfigForUpload(
                ctx = ctx,
                sourceBackupJson = sourceJson,
                includeShortcuts = includeShortcuts,
                stripCustomImages = !keepCustomImages,
                appVersion = BuildConfig.VERSION_NAME,
            )
            rawImgCount to prepared
        }
        isPreparing = false
        res.onSuccess { (rawImgCount, prepared) ->
            rawShortcutImageCount = rawImgCount
            preparedUpload = prepared
        }.onFailure { err ->
            preparedUpload = null
            prepareError = err.message ?: "本地预检失败"
        }
    }

    val parsedTags = remember(tagsInput) { parseMarketTagsInput(tagsInput) }
    val selectedHasShortcuts = selectedProfile?.hasShortcuts ?: true
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
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "上传配置到市场",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "系统会自动剔除运行态与本机界面偏好键，并可选择仅分享参数或携带脱敏后的快捷项目。",
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
                                "${selectedProfile.layoutLabel()} · ${selectedProfile.materialLabel()} · ${if (selectedProfile.hasShortcuts) "${selectedProfile.shortcutCount} 快捷项" else "仅参数"}"
                            } else {
                                "使用当前正在生效的面板参数与快捷项目"
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
                            subtitle = "本机实时生效的参数与快捷项",
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
                                        name = profile.name.take(60)
                                    }
                                    if (!profile.hasShortcuts) {
                                        includeShortcuts = false
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

                if (selectedHasShortcuts) {
                    BasicComponent(
                        title = "包含快捷项目",
                        summary = "默认仅分享面板参数；开启后将一并上传快捷方式列表（不含个人运行态数据）。",
                        endActions = {
                            Switch(
                                checked = includeShortcuts,
                                onCheckedChange = { if (!isUploading) includeShortcuts = it },
                            )
                        },
                    )
                } else {
                    MarketBannerBox(
                        text = "所选配置方案仅保存了面板参数，不包含快捷项目。",
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    )
                }

                if (includeShortcuts && rawShortcutImageCount > 0) {
                    BasicComponent(
                        title = "保留自定义图片（提交后需审核）",
                        summary = "检测到 $rawShortcutImageCount 张本地 base64 图片；关闭将一键剥离并回退应用包名或矢量图标。",
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
                                inspection.exceedsPayloadLimit -> "配置总大小 ($sizeText) 超过 512 KB 上限，请减少快捷项或关闭保留图片"
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
                                submitError = "请填写配置名称与作者昵称"
                                return@TextButton
                            }
                            isUploading = true
                            submitError = null
                            MarketSettings.setLastAuthor(ctx, cleanAuthor)
                            scope.launch {
                                val uploadRes = runCatching {
                                    MarketApiClient.uploadConfig(
                                        ctx = ctx,
                                        name = cleanName,
                                        description = description.trim(),
                                        author = cleanAuthor,
                                        tags = parsedTags,
                                        minAppVersionCode = BuildConfig.VERSION_CODE,
                                        appVersion = BuildConfig.VERSION_NAME,
                                        jsonBody = prepared.jsonBody,
                                    )
                                }
                                isUploading = false
                                uploadRes.onSuccess { res ->
                                    if (res.id.isNotBlank() && res.manageToken.isNotBlank()) {
                                        MarketSettings.saveManageToken(
                                            ctx,
                                            MarketSettings.KIND_CONFIG,
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
