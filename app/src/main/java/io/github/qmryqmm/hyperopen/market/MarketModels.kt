package io.github.qmryqmm.hyperopen.market

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem

/** 市场条目高危动作详情（与后端 riskSummary 对齐）。 */
data class MarketRiskEntry(
    val path: String,
    val title: String,
    val actionType: String,
    val reason: String,
    val shellUseRoot: Boolean = false,
    val userId: Int = 0,
    val payload: String = "",
)

/** 分页元数据（与后端 pagination 对象对齐）。 */
data class MarketPagination(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int,
    val hasMore: Boolean,
)

/** 通用分页列表返回结果。 */
data class MarketPageResult<T>(
    val items: List<T>,
    val pagination: MarketPagination,
)

/** 本机绑定的市场创作者档案（与后端 /api/author/me 对齐）。 */
data class MarketAuthorProfile(
    val id: String,
    val displayName: String,
    val isOfficial: Boolean = false,
    val badgeLabel: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** 配置关联的配套快捷方式包摘要（与后端 ConfigMetadata.linkedShortcutPack 对齐）。 */
data class MarketLinkedShortcutPack(
    val id: String,
    val name: String,
    val shortcutCount: Int = 0,
    val folderCount: Int = 0,
    val hasRootRisk: Boolean = false,
    val customImageCount: Int = 0,
)

/** 配置市场列表/详情元数据（与后端 ConfigMetadata 对齐）。 */
data class MarketConfigSummary(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val tags: List<String>,
    val layoutStyle: Int,
    val materialStyle: Int,
    val keyCount: Int,
    val shortcutCount: Int,
    val hasShortcuts: Boolean,
    val customImageCount: Int,
    val hasRootRisk: Boolean,
    val riskSummary: List<MarketRiskEntry>,
    val reviewReasons: List<String>,
    val minAppVersionCode: Int,
    val appVersion: String,
    val downloads: Int,
    val reportsCount: Int,
    val status: String,
    val sha256: String,
    val sizeBytes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val authorId: String? = null,
    val authorIsOfficial: Boolean = false,
    val authorBadgeLabel: String = "",
    val linkedShortcutPackId: String? = null,
    val linkedShortcutPack: MarketLinkedShortcutPack? = null,
) {
    fun layoutLabel(): String = when (layoutStyle) {
        HyperOpenConfig.LAYOUT_RING -> "空心圆环"
        HyperOpenConfig.LAYOUT_ARC -> "弧形扇面"
        HyperOpenConfig.LAYOUT_SYSTEM_STOCK -> "系统经典"
        else -> "蜂窝"
    }

    fun materialLabel(): String =
        if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) "柔光玻璃" else "高斯模糊"
}

/** 配置市场详情正文（含 jsonBody 与 ETag 缓存标记）。 */
data class MarketConfigDetail(
    val item: MarketConfigSummary,
    val jsonBody: String,
    val sha256: String,
    val fromCache: Boolean = false,
)

/** 快捷方式包列表/详情元数据（供 Stage 3 复用）。 */
data class MarketShortcutPackSummary(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val tags: List<String>,
    val shortcutCount: Int,
    val folderCount: Int,
    val actionTypes: List<String>,
    val customImageCount: Int,
    val hasRootRisk: Boolean,
    val riskSummary: List<MarketRiskEntry>,
    val reviewReasons: List<String>,
    val minAppVersionCode: Int,
    val appVersion: String,
    val downloads: Int,
    val reportsCount: Int,
    val status: String,
    val sha256: String,
    val sizeBytes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val authorId: String? = null,
    val authorIsOfficial: Boolean = false,
    val authorBadgeLabel: String = "",
)

/** 快捷方式包详情正文（供 Stage 3 复用）。 */
data class MarketShortcutPackDetail(
    val item: MarketShortcutPackSummary,
    val shortcutsJson: String,
    val sha256: String,
    val fromCache: Boolean = false,
)

/** 上传接口统一返回结果（配置与快捷方式包共用）。 */
data class MarketUploadResult(
    val id: String,
    val status: String,
    val reviewReasons: List<String>,
    val manageToken: String,
    val configItem: MarketConfigSummary? = null,
    val shortcutPackItem: MarketShortcutPackSummary? = null,
) {
    val isPendingReview: Boolean
        get() = status == "pending"
}

/** 举报结果。 */
data class MarketReportResult(
    val id: String,
    val reportsCount: Int,
    val hidden: Boolean,
)

/** 撤回删除结果。 */
data class MarketDeleteResult(
    val id: String,
    val deleted: Boolean,
)

/** 本地预检结果（提交上传前由 MarketSanitizer 实时产出）。 */
data class MarketLocalInspection(
    val hasRootRisk: Boolean,
    val riskItems: List<MarketRiskEntry>,
    val customImageCount: Int,
    val oversizedImageCount: Int,
    val totalCustomImageBytes: Int,
    val shortcutCount: Int,
    val totalShortcutCount: Int,
    val folderCount: Int,
    val totalSizeBytes: Int,
    val needsReview: Boolean,
    val reviewReasons: List<String>,
    val actionTypes: List<String> = emptyList(),
    val validationError: String? = null,
) {
    val exceedsMaxImageCount: Boolean
        get() = customImageCount > MarketSanitizer.MAX_CUSTOM_IMAGE_COUNT

    val exceedsPayloadLimit: Boolean
        get() = totalSizeBytes > MarketSanitizer.MAX_PAYLOAD_BYTES

    val isUploadAllowed: Boolean
        get() = validationError == null && !exceedsMaxImageCount && !exceedsPayloadLimit
}

/** 纯内存沙箱预览类型（Stage 2 配置预览 / Stage 3 快捷包预览）。 */
enum class MarketSandboxKind {
    CONFIG,
    SHORTCUT_PACK,
}

/** 纯内存沙箱预览会话载体：全程仅驻留内存，退出后置空绝不落盘。 */
data class MarketSandboxPreview(
    val kind: MarketSandboxKind,
    val id: String,
    val title: String,
    val author: String,
    val config: HyperOpenConfig,
    val rawConfigJson: String? = null,
    val hasShortcuts: Boolean = false,
    val shortcutCount: Int = 0,
    val shortcutList: List<ShortcutItem> = emptyList(),
    val minAppVersionCode: Int = 0,
    val appVersion: String = "",
    val hasRootRisk: Boolean = false,
    val riskSummary: List<MarketRiskEntry> = emptyList(),
    val summary: MarketConfigSummary? = null,
    val shortcutPackSummary: MarketShortcutPackSummary? = null,
    val authorIsOfficial: Boolean = false,
    val authorBadgeLabel: String = "",
)

/** 市场接口异常，保留 HTTP 状态码及限流/去重结构化字段以便 UI 精准提示。 */
class MarketApiException(
    val statusCode: Int,
    message: String,
    val serverError: String = "",
    val retryAfterSeconds: Int? = null,
    val existingId: String? = null,
    val existingStatus: String? = null,
    val isNetworkError: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isDuplicateConflict: Boolean
        get() = statusCode == 409 && !existingId.isNullOrBlank()
}
