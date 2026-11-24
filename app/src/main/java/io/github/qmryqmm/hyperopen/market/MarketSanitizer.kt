package io.github.qmryqmm.hyperopen.market

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.config.ConfigBackup
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.view.IconGlyphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * 市场上传脱敏、体积压缩与安全预检引擎（规则与后端 validator.js 严格对齐）。
 *
 * 兼容 Stage 2（配置上传/导入）与 Stage 3（快捷方式包上传/导入）：
 * - 剔除运行时状态键、App 界面键、非白名单键与冗余导出元数据；
 * - 递归扫描顶层与文件夹子项内的 base64: 自定义图标与 Root/高危动作；
 * - 对解码超过 64 KB 的 base64: 图片自动在 IO 线程缩放/重编码压缩至 <= 64 KB；
 * - 支持一键剥离自定义图片，自动回退应用包名或默认矢量符号。
 */
object MarketSanitizer {

    const val MAX_PAYLOAD_BYTES = 512 * 1024
    const val MAX_CUSTOM_IMAGE_BYTES = 64 * 1024
    const val MAX_CUSTOM_IMAGE_COUNT = 24
    const val MAX_TOP_LEVEL_SHORTCUTS = 50
    const val MAX_TOTAL_SHORTCUTS = 100

    private val VALID_ACTION_TYPES = setOf(
        "APP",
        "INTENT_URI",
        "ACTIVITY",
        "BROADCAST",
        "FLASHLIGHT",
        "SHELL",
        "XIAOAI",
        "MUTE",
        "DND",
        "CAMERA",
        "FOLDER",
    )

    private val REGEX_WHITESPACE = Regex("\\s+")
    private val REGEX_INTENT_USER = Regex(";[iS]\\.hyperopen_user=([1-9]\\d*)(?:;|$)")
    private val REGEX_INTENT_NEWLINE_OR_CMD_SUB = Regex("[\\r\\n`]|\\$\\(")
    private val REGEX_INTENT_SENSITIVE_SCHEME_PREFIX = Regex("^(?:file|su):", RegexOption.IGNORE_CASE)
    private val REGEX_INTENT_SENSITIVE_SCHEME_PART = Regex(";scheme=(?:file|su)(?:;|$)", RegexOption.IGNORE_CASE)
    private val REGEX_INTENT_CMD_EXTRA = Regex(";[iS]\\.(?:cmd|command|shell|script|su|root|exec)=", RegexOption.IGNORE_CASE)
    private val REGEX_INTENT_PRIV_COMMAND = Regex(
        "(?:^|[;\\s|&])(?:su|sh\\s+-c|busybox|magisk|ksud|apd)(?=\$|[;\\s|&])|(?:^|[;\\s|&])(?:am\\s+start|pm\\s+install|setprop\\s+|chmod\\s+|chown\\s+)",
        RegexOption.IGNORE_CASE,
    )
    private val REGEX_INTENT_PRIV_KEYWORD = Regex(
        "(?:^|[;\\s|&])(?:su|sh\\s+-c|busybox|magisk|ksud|apd)(?=\$|[;\\s|&])",
        RegexOption.IGNORE_CASE,
    )

    data class PreparedConfigUpload(
        val jsonBody: String,
        val inspection: MarketLocalInspection,
        val compressedImageCount: Int,
        val strippedImageCount: Int,
    )

    data class PreparedShortcutsUpload(
        val shortcutsJson: String,
        val items: List<ShortcutItem>,
        val inspection: MarketLocalInspection,
        val compressedImageCount: Int,
        val strippedImageCount: Int,
    )

    private data class SanitizedShortcutsBundle(
        val items: List<ShortcutItem>,
        val shortcutsJson: String,
        val inspection: MarketLocalInspection,
        val compressedImageCount: Int,
        val strippedImageCount: Int,
    )

    /**
     * 准备待上传的配置 JSON：
     * @param sourceBackupJson 为 null 时读取当前生效配置；非 null 时清洗指定 ConfigProfile.backupJson。
     */
    suspend fun prepareConfigForUpload(
        ctx: Context,
        sourceBackupJson: String? = null,
        includeShortcuts: Boolean = false,
        stripCustomImages: Boolean = false,
        appVersion: String = BuildConfig.VERSION_NAME,
    ): PreparedConfigUpload = withContext(Dispatchers.IO) {
        val rawJson = if (sourceBackupJson.isNullOrBlank()) {
            ConfigBackup.buildExportJson(ctx, includeShortcuts, false)
        } else {
            sourceBackupJson
        }
        val preview = ConfigBackup.preview(rawJson)
        if (!preview.ok) {
            throw IllegalArgumentException(preview.message)
        }

        val root = JSONObject(rawJson)
        val rawValues = root.optJSONObject("values")
            ?: throw IllegalArgumentException("配置缺少 values 字段")

        val sortedKeys = ArrayList<String>()
        val iter = rawValues.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k == "shortcuts_json") continue
            if (ConfigBackup.isSkipped(k) || !ConfigBackup.isAllowedKey(k)) continue
            sortedKeys.add(k)
        }
        sortedKeys.sort()

        val cleanedValues = JSONObject()
        for (k in sortedKeys) {
            val entry = rawValues.optJSONObject(k) ?: continue
            cleanedValues.put(k, entry)
        }
        if (cleanedValues.length() == 0) {
            throw IllegalArgumentException("配置不包含任何有效的面板参数键")
        }

        val resolvedAppVersion = appVersion.ifBlank {
            root.optString("appVersion", BuildConfig.VERSION_NAME)
        }.trim().take(32)

        val canonical = JSONObject()
        canonical.put("format", ConfigBackup.FORMAT)
        canonical.put("version", ConfigBackup.FORMAT_VERSION)
        canonical.put("appVersion", resolvedAppVersion)
        canonical.put("values", cleanedValues)

        var compressedCount = 0
        var strippedCount = 0
        val inspection: MarketLocalInspection

        if (includeShortcuts) {
            val parsedShortcuts = ConfigBackup.parseShortcutsFromBackup(rawJson).orEmpty()
            if (parsedShortcuts.isNotEmpty()) {
                val sanitized = sanitizeShortcutsInternal(
                    rawShortcuts = parsedShortcuts,
                    forceZeroOffsets = false,
                    stripCustomImages = stripCustomImages,
                    compressOversized = !stripCustomImages,
                )
                compressedCount = sanitized.compressedImageCount
                strippedCount = sanitized.strippedImageCount
                if (sanitized.items.isNotEmpty()) {
                    val arr = JSONArray()
                    for (item in sanitized.items) {
                        arr.put(item.toJson())
                    }
                    canonical.put("shortcuts", arr)
                }
                val finalJson = canonical.toString()
                val totalBytes = finalJson.toByteArray(StandardCharsets.UTF_8).size
                inspection = sanitized.inspection.copy(totalSizeBytes = totalBytes)
                return@withContext PreparedConfigUpload(
                    jsonBody = finalJson,
                    inspection = inspection,
                    compressedImageCount = compressedCount,
                    strippedImageCount = strippedCount,
                )
            }
        }

        val finalJson = canonical.toString()
        val totalBytes = finalJson.toByteArray(StandardCharsets.UTF_8).size
        inspection = MarketLocalInspection(
            hasRootRisk = false,
            riskItems = emptyList(),
            customImageCount = 0,
            oversizedImageCount = 0,
            totalCustomImageBytes = 0,
            shortcutCount = 0,
            totalShortcutCount = 0,
            folderCount = 0,
            totalSizeBytes = totalBytes,
            needsReview = false,
            reviewReasons = emptyList(),
            actionTypes = emptyList(),
            validationError = if (totalBytes > MAX_PAYLOAD_BYTES) "规范化后的配置正文超过 512 KB 上限" else null,
        )
        PreparedConfigUpload(
            jsonBody = finalJson,
            inspection = inspection,
            compressedImageCount = 0,
            strippedImageCount = 0,
        )
    }

    /**
     * 准备待上传的快捷方式包（供 Stage 3 复用）：
     * 强制清空 customOffsetX / customOffsetY，限额 top<=50 / total<=100，并执行图片压缩或剥离。
     */
    suspend fun prepareShortcutsForUpload(
        shortcuts: List<ShortcutItem>,
        stripCustomImages: Boolean = false,
    ): PreparedShortcutsUpload = withContext(Dispatchers.IO) {
        val sanitized = sanitizeShortcutsInternal(
            rawShortcuts = shortcuts,
            forceZeroOffsets = true,
            stripCustomImages = stripCustomImages,
            compressOversized = !stripCustomImages,
        )
        val totalBytes = sanitized.shortcutsJson.toByteArray(StandardCharsets.UTF_8).size
        val extraError = when {
            sanitized.items.isEmpty() -> "快捷方式包至少需要包含 1 个快捷方式"
            totalBytes > MAX_PAYLOAD_BYTES -> "快捷包正文超过 512 KB 上限"
            else -> sanitized.inspection.validationError
        }
        PreparedShortcutsUpload(
            shortcutsJson = sanitized.shortcutsJson,
            items = sanitized.items,
            inspection = sanitized.inspection.copy(
                totalSizeBytes = totalBytes,
                validationError = extraError,
            ),
            compressedImageCount = sanitized.compressedImageCount,
            strippedImageCount = sanitized.strippedImageCount,
        )
    }

    /** 仅本地同步预检快捷方式列表（不执行耗时位图重编码），供快速统计风险与自定义图片数量。 */
    fun inspectShortcuts(
        shortcuts: List<ShortcutItem>,
        stripCustomImages: Boolean = false,
    ): MarketLocalInspection {
        val sanitized = sanitizeShortcutsInternal(
            rawShortcuts = shortcuts,
            forceZeroOffsets = false,
            stripCustomImages = stripCustomImages,
            compressOversized = false,
        )
        val totalBytes = sanitized.shortcutsJson.toByteArray(StandardCharsets.UTF_8).size
        return sanitized.inspection.copy(totalSizeBytes = totalBytes)
    }

    /** 仅本地预检任意配置备份 JSON（不压缩）。 */
    suspend fun inspectConfigBackupJson(
        backupJson: String,
        includeShortcuts: Boolean = true,
        stripCustomImages: Boolean = false,
    ): MarketLocalInspection = withContext(Dispatchers.IO) {
        val preview = ConfigBackup.preview(backupJson)
        if (!preview.ok) {
            return@withContext MarketLocalInspection(
                hasRootRisk = false,
                riskItems = emptyList(),
                customImageCount = 0,
                oversizedImageCount = 0,
                totalCustomImageBytes = 0,
                shortcutCount = 0,
                totalShortcutCount = 0,
                folderCount = 0,
                totalSizeBytes = backupJson.toByteArray(StandardCharsets.UTF_8).size,
                needsReview = false,
                reviewReasons = emptyList(),
                validationError = preview.message,
            )
        }
        val parsed = if (includeShortcuts) {
            ConfigBackup.parseShortcutsFromBackup(backupJson).orEmpty()
        } else {
            emptyList()
        }
        if (parsed.isEmpty()) {
            val totalBytes = backupJson.toByteArray(StandardCharsets.UTF_8).size
            return@withContext MarketLocalInspection(
                hasRootRisk = false,
                riskItems = emptyList(),
                customImageCount = 0,
                oversizedImageCount = 0,
                totalCustomImageBytes = 0,
                shortcutCount = 0,
                totalShortcutCount = 0,
                folderCount = 0,
                totalSizeBytes = totalBytes,
                needsReview = false,
                reviewReasons = emptyList(),
                validationError = if (totalBytes > MAX_PAYLOAD_BYTES) "配置体积超过 512 KB 上限" else null,
            )
        }
        val base = inspectShortcuts(parsed, stripCustomImages)
        val size = backupJson.toByteArray(StandardCharsets.UTF_8).size
        base.copy(totalSizeBytes = size)
    }

    /**
     * 对解码后超过 64 KB（或非 PNG/JPEG/WEBP 格式）的 base64: 自定义图标进行渐进压缩至 <= 64 KB。
     */
    suspend fun compressBase64Image(
        rawSpec: String,
        maxBytes: Int = MAX_CUSTOM_IMAGE_BYTES,
    ): String = withContext(Dispatchers.IO) {
        compressBase64ImageBlocking(rawSpec, maxBytes)
    }

    private fun compressBase64ImageBlocking(rawSpec: String, maxBytes: Int): String {
        if (!IconGlyphFactory.isCustomImage(rawSpec)) return rawSpec
        val rawBytes = decodeBase64Payload(rawSpec)
            ?: throw IllegalArgumentException("自定义图片 Base64 解码失败")
        if (rawBytes.size <= maxBytes && detectImageMimeType(rawBytes) != null) {
            return IconGlyphFactory.CUSTOM_IMAGE_PREFIX + Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            throw IllegalArgumentException("无法解码自定义图片")
        }

        var sample = 1
        while (maxOf(srcW / sample, srcH / sample) > 512) {
            sample = sample shl 1
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalArgumentException("自定义图片解码失败")

        try {
            val maxDim = maxOf(decoded.width, decoded.height).coerceAtMost(256)
            val candidateDims = listOf(256, 192, 160, 128, 96, 72, 64, 48)
                .filter { it <= maxDim }
                .ifEmpty { listOf(maxDim.coerceAtLeast(32)) }
            val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val qualities = listOf(92, 82, 70, 58, 45, 32, 20)

            for (dim in candidateDims) {
                val scaleRatio = dim.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1)
                val targetW = (decoded.width * scaleRatio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scaleRatio).toInt().coerceAtLeast(1)
                val scaled = if (targetW != decoded.width || targetH != decoded.height) {
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                } else {
                    decoded
                }
                try {
                    if (dim <= 144) {
                        val pngOut = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
                        val pngBytes = pngOut.toByteArray()
                        if (pngBytes.size <= maxBytes && detectImageMimeType(pngBytes) != null) {
                            return IconGlyphFactory.CUSTOM_IMAGE_PREFIX +
                                Base64.encodeToString(pngBytes, Base64.NO_WRAP)
                        }
                    }
                    for (q in qualities) {
                        val webpOut = ByteArrayOutputStream()
                        scaled.compress(webpFormat, q, webpOut)
                        val webpBytes = webpOut.toByteArray()
                        if (webpBytes.size <= maxBytes && detectImageMimeType(webpBytes) != null) {
                            return IconGlyphFactory.CUSTOM_IMAGE_PREFIX +
                                Base64.encodeToString(webpBytes, Base64.NO_WRAP)
                        }
                    }
                } finally {
                    if (scaled !== decoded) {
                        scaled.recycle()
                    }
                }
            }
        } finally {
            decoded.recycle()
        }

        throw IllegalArgumentException("自定义图片体积过大，无法压缩至 64 KB 以内")
    }

    private fun sanitizeShortcutsInternal(
        rawShortcuts: List<ShortcutItem>,
        forceZeroOffsets: Boolean,
        stripCustomImages: Boolean,
        compressOversized: Boolean,
    ): SanitizedShortcutsBundle {
        var validationError: String? = null
        if (rawShortcuts.size > MAX_TOP_LEVEL_SHORTCUTS) {
            validationError = "顶层快捷方式数量不能超过 $MAX_TOP_LEVEL_SHORTCUTS 个"
        }

        var totalCount = 0
        var folderCount = 0
        var customImageCount = 0
        var oversizedImageCount = 0
        var totalCustomImageBytes = 0
        var compressedImageCount = 0
        var strippedImageCount = 0
        val actionTypes = LinkedHashSet<String>()
        val riskItems = ArrayList<MarketRiskEntry>()

        fun processField(
            rawValue: String?,
            itemPath: String,
            fieldName: String,
        ): String {
            val v = rawValue.orEmpty()
            if (!v.startsWith("base64:")) {
                if (v.length > 256 && validationError == null) {
                    validationError = "$itemPath.$fieldName 长度超过 256 字符上限"
                }
                return v
            }
            if (stripCustomImages) {
                strippedImageCount += 1
                return ""
            }
            val decoded = decodeBase64Payload(v)
            if (decoded == null) {
                if (validationError == null) {
                    validationError = "$itemPath.$fieldName 自定义图片 Base64 格式无效"
                }
                return v
            }
            val mime = detectImageMimeType(decoded)
            val isOversizedOrUnsupported = decoded.size > MAX_CUSTOM_IMAGE_BYTES || mime == null
            if (isOversizedOrUnsupported) {
                oversizedImageCount += 1
            }
            val finalValue = if (isOversizedOrUnsupported && compressOversized) {
                try {
                    val compressed = compressBase64ImageBlocking(v, MAX_CUSTOM_IMAGE_BYTES)
                    compressedImageCount += 1
                    compressed
                } catch (e: Throwable) {
                    if (validationError == null) {
                        validationError = "$itemPath.$fieldName ${e.message ?: "压缩失败"}"
                    }
                    v
                }
            } else {
                IconGlyphFactory.CUSTOM_IMAGE_PREFIX + Base64.encodeToString(decoded, Base64.NO_WRAP)
            }
            val finalDecoded = decodeBase64Payload(finalValue)
            val finalSize = finalDecoded?.size ?: decoded.size
            if (finalSize > MAX_CUSTOM_IMAGE_BYTES && !compressOversized) {
                // 在未压缩仅预检场景下仍计入超限字节；真正上传前会自动压缩
            } else if (finalSize > MAX_CUSTOM_IMAGE_BYTES && validationError == null) {
                validationError = "$itemPath.$fieldName 自定义图片超过 64 KB 上限"
            }
            customImageCount += 1
            totalCustomImageBytes += finalSize
            return finalValue
        }

        fun normalizeOne(raw: ShortcutItem, depth: Int, pathPrefix: String): ShortcutItem {
            totalCount += 1
            if (totalCount > MAX_TOTAL_SHORTCUTS && validationError == null) {
                validationError = "含文件夹子项在内的快捷方式总数不能超过 $MAX_TOTAL_SHORTCUTS 个"
            }

            val copy = raw.copy()
            val actionType = (copy.actionType ?: ShortcutItem.ACTION_APP).trim()
            if (actionType !in VALID_ACTION_TYPES && validationError == null) {
                validationError = "不支持的动作类型: $actionType"
            }
            if (depth > 0 && actionType == ShortcutItem.ACTION_FOLDER && validationError == null) {
                validationError = "禁止文件夹嵌套文件夹"
            }
            actionTypes.add(actionType)

            copy.id = (copy.id ?: "").take(128)
            copy.title = (copy.title ?: "未命名").take(128)
            copy.subtitle = (copy.subtitle ?: "").take(256)
            val displayTitle = copy.title.trim().ifEmpty { "未命名快捷方式" }
            val itemPath = if (pathPrefix.isEmpty()) displayTitle else "$pathPrefix > $displayTitle"

            val hadCustomImageInPackage = copy.iconPackage?.startsWith("base64:") == true
            val hadCustomImageInGlyph = copy.iconGlyph?.startsWith("base64:") == true

            copy.iconPackage = processField(copy.iconPackage, itemPath, "iconPackage")
            copy.iconGlyph = processField(copy.iconGlyph, itemPath, "iconGlyph")

            if (stripCustomImages && (hadCustomImageInPackage || hadCustomImageInGlyph)) {
                if (actionType == ShortcutItem.ACTION_FOLDER && copy.iconGlyph.isBlank()) {
                    copy.iconGlyph = "remix:folder-2-fill"
                }
            }

            copy.glyphPadAlpha = ShortcutItem.clampPadAlpha(copy.glyphPadAlpha)
            copy.appIconCropMode = if (copy.appIconCropMode > 0) copy.appIconCropMode else 2
            if (forceZeroOffsets) {
                copy.customOffsetX = 0f
                copy.customOffsetY = 0f
            } else {
                if (!copy.customOffsetX.isFinite()) copy.customOffsetX = 0f
                if (!copy.customOffsetY.isFinite()) copy.customOffsetY = 0f
            }
            copy.userId = copy.userId.coerceIn(0, 99999)

            if (!copy.hintTextAlpha.isFinite()) copy.hintTextAlpha = 1.0f
            if (!copy.hintTextAlphaSub.isFinite()) copy.hintTextAlphaSub = -1.0f
            copy.hintTextShadow = copy.hintTextShadow.coerceIn(0, 200)
            copy.hintTextStroke = copy.hintTextStroke.coerceIn(0, 20)
            copy.hintTextWeight = copy.hintTextWeight.coerceIn(0, 3)
            copy.hintTextLetterSpacing = if (copy.hintTextLetterSpacing.isFinite()) {
                copy.hintTextLetterSpacing.coerceIn(-10f, 20f)
            } else {
                0f
            }

            var payload = copy.actionPayload.orEmpty()
            var activityUseRoot = false
            if (actionType == ShortcutItem.ACTION_FOLDER) {
                if (depth == 0) folderCount += 1
                val children = copy.parseFolderChildren()
                val normalizedChildren = ArrayList<ShortcutItem>(children.size)
                for (child in children) {
                    if (child != null) {
                        normalizedChildren.add(normalizeOne(child, depth + 1, itemPath))
                    }
                }
                copy.setFolderChildren(normalizedChildren)
                payload = copy.actionPayload.orEmpty()
            } else if (actionType == ShortcutItem.ACTION_ACTIVITY) {
                if (payload.trim().isNotEmpty()) {
                    val spec = ActivityLaunchSpec.fromJson(payload)
                    activityUseRoot = spec.useRoot
                }
            }

            if (payload.length > 65536 && validationError == null) {
                validationError = "$itemPath.actionPayload 长度超过 65536 字符上限"
            }

            val itemRiskReasons = ArrayList<String>()
            when (actionType) {
                ShortcutItem.ACTION_SHELL ->
                    itemRiskReasons.add(if (copy.shellUseRoot) "Shell Root 命令" else "Shell 命令")
                ShortcutItem.ACTION_BROADCAST ->
                    itemRiskReasons.add(if (copy.shellUseRoot) "自定义广播 + Root" else "自定义广播")
                ShortcutItem.ACTION_ACTIVITY ->
                    if (activityUseRoot || copy.shellUseRoot) {
                        itemRiskReasons.add("Activity Root 启动")
                    }
                else -> if (copy.shellUseRoot) {
                    itemRiskReasons.add("Root 权限")
                }
            }
            if (copy.userId > 0) {
                itemRiskReasons.add("跨用户启动 (userId=${copy.userId})")
            }
            if (actionType == ShortcutItem.ACTION_INTENT_URI) {
                inspectIntentUriRisk(payload)?.let { itemRiskReasons.add(it) }
            }
            if (itemRiskReasons.isNotEmpty()) {
                riskItems.add(
                    MarketRiskEntry(
                        path = itemPath,
                        title = displayTitle,
                        actionType = actionType,
                        reason = itemRiskReasons.joinToString("；"),
                        shellUseRoot = copy.shellUseRoot || activityUseRoot,
                        userId = copy.userId,
                        payload = if (payload.length > 300) payload.take(300) + "..." else payload,
                    )
                )
            }

            return copy
        }

        val normalizedItems = ArrayList<ShortcutItem>(rawShortcuts.size)
        for (item in rawShortcuts) {
            normalizedItems.add(normalizeOne(item, 0, ""))
        }

        if (customImageCount > MAX_CUSTOM_IMAGE_COUNT && validationError == null) {
            validationError = "单次上传自定义图片总数不能超过 $MAX_CUSTOM_IMAGE_COUNT 张"
        }

        val arr = JSONArray()
        for (item in normalizedItems) {
            arr.put(item.toJson())
        }
        val shortcutsJson = arr.toString()

        val reviewReasons = ArrayList<String>()
        if (riskItems.isNotEmpty()) reviewReasons.add("root_action")
        if (customImageCount > 0) reviewReasons.add("custom_image")

        val inspection = MarketLocalInspection(
            hasRootRisk = riskItems.isNotEmpty(),
            riskItems = riskItems,
            customImageCount = customImageCount,
            oversizedImageCount = oversizedImageCount,
            totalCustomImageBytes = totalCustomImageBytes,
            shortcutCount = normalizedItems.size,
            totalShortcutCount = totalCount,
            folderCount = folderCount,
            totalSizeBytes = shortcutsJson.toByteArray(StandardCharsets.UTF_8).size,
            needsReview = reviewReasons.isNotEmpty(),
            reviewReasons = reviewReasons,
            actionTypes = actionTypes.toList(),
            validationError = validationError,
        )

        return SanitizedShortcutsBundle(
            items = normalizedItems,
            shortcutsJson = shortcutsJson,
            inspection = inspection,
            compressedImageCount = compressedImageCount,
            strippedImageCount = strippedImageCount,
        )
    }

    private fun inspectIntentUriRisk(payload: String): String? {
        if (payload.isEmpty()) return null
        REGEX_INTENT_USER.find(payload)?.let { m ->
            return "跨用户 Intent URI (hyperopen_user=${m.groupValues[1]})"
        }
        if (REGEX_INTENT_NEWLINE_OR_CMD_SUB.containsMatchIn(payload)) {
            return "Intent URI 含换行或命令替换字符"
        }
        if (REGEX_INTENT_SENSITIVE_SCHEME_PREFIX.containsMatchIn(payload.trim()) ||
            REGEX_INTENT_SENSITIVE_SCHEME_PART.containsMatchIn(payload)
        ) {
            return "Intent URI 使用敏感 scheme"
        }
        if (REGEX_INTENT_CMD_EXTRA.containsMatchIn(payload)) {
            return "Intent URI 含命令型 Extra 参数"
        }
        val markerCount = "#Intent;".toRegex(RegexOption.LITERAL).findAll(payload).count()
        if (markerCount > 1) {
            return "Intent URI 含多个 #Intent 片段"
        }
        if (markerCount == 1) {
            val endIdx = payload.lastIndexOf(";end")
            if (endIdx < 0 || payload.substring(endIdx + 4).trim().isNotEmpty()) {
                return "Intent URI 在 #Intent 块外存在异常参数"
            }
        } else if (REGEX_INTENT_PRIV_COMMAND.containsMatchIn(payload)) {
            return "Intent URI 含疑似 Shell/特权命令特征"
        }
        if (REGEX_INTENT_PRIV_KEYWORD.containsMatchIn(payload)) {
            return "Intent URI 含疑似特权执行关键字"
        }
        return null
    }

    private fun decodeBase64Payload(rawSpec: String): ByteArray? {
        if (!rawSpec.startsWith("base64:")) return null
        val clean = rawSpec.substring(7).replace(REGEX_WHITESPACE, "")
        if (clean.isEmpty()) return null
        return try {
            Base64.decode(clean, Base64.DEFAULT)
        } catch (_: Throwable) {
            null
        }
    }

    private fun detectImageMimeType(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val b4 = bytes[4].toInt() and 0xFF
        val b5 = bytes[5].toInt() and 0xFF
        val b6 = bytes[6].toInt() and 0xFF
        val b7 = bytes[7].toInt() and 0xFF
        val b8 = bytes[8].toInt() and 0xFF
        val b9 = bytes[9].toInt() and 0xFF
        val b10 = bytes[10].toInt() and 0xFF
        val b11 = bytes[11].toInt() and 0xFF

        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47 &&
            b4 == 0x0D && b5 == 0x0A && b6 == 0x1A && b7 == 0x0A
        ) {
            return "image/png"
        }
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "image/jpeg"
        }
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
            b8 == 0x57 && b9 == 0x45 && b10 == 0x42 && b11 == 0x50
        ) {
            return "image/webp"
        }
        return null
    }
}
