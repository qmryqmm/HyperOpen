package io.github.qmryqmm.hyperopen.market

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.config.ConfigBackup
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 配置市场页面状态持有者：提升到 HyperOpenApp 顶层后，
 * 在切换「本地配置 | 配置市场」或进出「沙箱预览」时可保留列表、筛选条件与滚动位置。
 */
class ConfigMarketState {
    val items = mutableStateListOf<MarketConfigSummary>()
    var pagination by mutableStateOf<MarketPagination?>(null)
    var searchQuery by mutableStateOf("")
    var appliedQuery by mutableStateOf("")
    var layoutFilter by mutableStateOf<Int?>(null)
    var materialFilter by mutableStateOf<Int?>(null)
    var sortOption by mutableStateOf("latest")
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var loadMoreError by mutableStateOf<String?>(null)
    var hasLoadedOnce by mutableStateOf(false)
    var handledRefreshTrigger by mutableIntStateOf(0)
    var requestGeneration by mutableIntStateOf(0)
}

@Composable
fun rememberConfigMarketState(): ConfigMarketState {
    return remember { ConfigMarketState() }
}

@Composable
fun ConfigMarketPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    marketState: ConfigMarketState,
    refreshTrigger: Int,
    softGlassSupported: Boolean,
    currentShortcuts: List<ShortcutItem>,
    existingProfileNames: Set<String>,
    localOffsetY: Int,
    needsBackupSuggestion: Boolean,
    onOpenUpload: () -> Unit,
    onAuthorUpdated: () -> Unit,
    onShortcutPackDeleted: () -> Unit,
    onEnterSandboxPreview: (MarketSandboxPreview) -> Unit,
    onEnterShortcutSandboxPreview: (detail: MarketShortcutPackDetail, items: List<ShortcutItem>) -> Unit,
    onAppendShortcutItems: (
        summary: MarketShortcutPackSummary,
        items: List<ShortcutItem>,
        shouldRecordDownload: Boolean,
    ) -> Unit,
    onImportAllShortcuts: (
        summary: MarketShortcutPackSummary,
        items: List<ShortcutItem>,
        replaceExisting: Boolean,
        backupCurrentFirst: Boolean,
        shouldRecordDownload: Boolean,
    ) -> Unit,
    onSaveToLibrary: (
        summary: MarketConfigSummary,
        rawConfigJson: String,
        profileName: String,
        keepLocalOffsetY: Boolean,
        applyImmediately: Boolean,
        backupCurrentFirst: Boolean,
        linkedShortcuts: List<ShortcutItem>?,
        linkedShortcutPackId: String?,
    ) -> Unit,
    onApplyDirectly: (
        summary: MarketConfigSummary,
        rawConfigJson: String,
        keepLocalOffsetY: Boolean,
        backupCurrentFirst: Boolean,
        linkedShortcuts: List<ShortcutItem>?,
        linkedShortcutPackId: String?,
    ) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showServerSettings by remember { mutableStateOf(false) }
    var selectedDetailItem by remember { mutableStateOf<MarketConfigSummary?>(null) }
    var parentConfigForLinkedPack by remember { mutableStateOf<MarketConfigSummary?>(null) }
    var linkedShortcutDetail by remember { mutableStateOf<MarketShortcutPackDetail?>(null) }
    var importRequest by remember { mutableStateOf<ConfigMarketImportRequest?>(null) }
    var reportTarget by remember { mutableStateOf<MarketConfigSummary?>(null) }
    var isReporting by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MarketConfigSummary?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val localAuthorId = MarketSettings.getAuthorId(ctx)
    fun isOwnedLocally(item: MarketConfigSummary): Boolean {
        return (localAuthorId != null && item.authorId == localAuthorId) ||
            MarketSettings.hasManageToken(ctx, MarketSettings.KIND_CONFIG, item.id)
    }

    fun loadFirstPage(queryOverride: String = marketState.searchQuery.trim()) {
        val gen = ++marketState.requestGeneration
        marketState.appliedQuery = queryOverride
        marketState.isLoading = true
        marketState.isLoadingMore = false
        marketState.errorMessage = null
        marketState.loadMoreError = null
        scope.launch {
            val res = runCatching {
                MarketApiClient.listConfigs(
                    ctx = ctx,
                    query = queryOverride,
                    layoutStyle = marketState.layoutFilter,
                    materialStyle = marketState.materialFilter,
                    sort = marketState.sortOption,
                    page = 1,
                    pageSize = 20,
                )
            }
            if (gen != marketState.requestGeneration) return@launch
            marketState.isLoading = false
            marketState.hasLoadedOnce = true
            res.onSuccess { pageRes ->
                marketState.items.clear()
                marketState.items.addAll(pageRes.items)
                marketState.pagination = pageRes.pagination
                marketState.errorMessage = null
            }.onFailure { err ->
                marketState.errorMessage = err.message ?: "加载配置市场失败"
            }
        }
    }

    fun loadNextPage() {
        val curPage = marketState.pagination ?: return
        if (!curPage.hasMore || marketState.isLoading || marketState.isLoadingMore) return
        val gen = marketState.requestGeneration
        val nextPage = curPage.page + 1
        marketState.isLoadingMore = true
        marketState.loadMoreError = null
        scope.launch {
            val res = runCatching {
                MarketApiClient.listConfigs(
                    ctx = ctx,
                    query = marketState.appliedQuery,
                    layoutStyle = marketState.layoutFilter,
                    materialStyle = marketState.materialFilter,
                    sort = marketState.sortOption,
                    page = nextPage,
                    pageSize = curPage.pageSize,
                )
            }
            if (gen != marketState.requestGeneration) return@launch
            marketState.isLoadingMore = false
            res.onSuccess { pageRes ->
                val existingIds = marketState.items.map { it.id }.toHashSet()
                pageRes.items.forEach { item ->
                    if (existingIds.add(item.id)) {
                        marketState.items.add(item)
                    }
                }
                marketState.pagination = pageRes.pagination
            }.onFailure { err ->
                marketState.loadMoreError = err.message ?: "加载更多失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!marketState.hasLoadedOnce && !marketState.isLoading) {
            loadFirstPage()
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > marketState.handledRefreshTrigger) {
            marketState.handledRefreshTrigger = refreshTrigger
            loadFirstPage()
        }
    }

    // 搜索框输入防抖刷新（380ms），避免每敲一个字符都发请求
    LaunchedEffect(marketState.searchQuery) {
        val trimmed = marketState.searchQuery.trim()
        if (trimmed == marketState.appliedQuery) return@LaunchedEffect
        delay(380L)
        if (trimmed != marketState.appliedQuery) {
            loadFirstPage(trimmed)
        }
    }

    // 滚动至接近底部时自动加载下一页
    val lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val totalItemCount = lazyListState.layoutInfo.totalItemsCount
    LaunchedEffect(lastVisibleIndex, totalItemCount, marketState.pagination?.hasMore, marketState.isLoading, marketState.isLoadingMore) {
        if (marketState.pagination?.hasMore == true &&
            !marketState.isLoading &&
            !marketState.isLoadingMore &&
            marketState.loadMoreError == null &&
            totalItemCount > 0 &&
            lastVisibleIndex >= totalItemCount - 2
        ) {
            loadNextPage()
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
        item(key = "market_notice") {
            MarketTopNoticeCard(
                onOpenServerSettings = { showServerSettings = true },
                onOpenUpload = onOpenUpload,
                uploadLabel = "上传配置",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        item(key = "market_search_filters") {
            ConfigMarketFilterCard(
                searchQuery = marketState.searchQuery,
                layoutFilter = marketState.layoutFilter,
                materialFilter = marketState.materialFilter,
                sortOption = marketState.sortOption,
                isLoading = marketState.isLoading,
                totalCount = marketState.pagination?.total,
                onSearchQueryChange = { marketState.searchQuery = it },
                onSubmitSearch = { loadFirstPage(marketState.searchQuery.trim()) },
                onLayoutSelect = { next ->
                    if (marketState.layoutFilter != next) {
                        marketState.layoutFilter = next
                        loadFirstPage()
                    }
                },
                onMaterialSelect = { next ->
                    if (marketState.materialFilter != next) {
                        marketState.materialFilter = next
                        loadFirstPage()
                    }
                },
                onSortSelect = { next ->
                    if (marketState.sortOption != next) {
                        marketState.sortOption = next
                        loadFirstPage()
                    }
                },
                onRefresh = { loadFirstPage() },
            )
        }

        if (marketState.isLoading && marketState.items.isEmpty()) {
            item(key = "market_loading") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "正在加载配置市场...",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                    )
                }
            }
        } else if (marketState.errorMessage != null && marketState.items.isEmpty()) {
            item(key = "market_error") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "加载失败",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MARKET_COLOR_RED,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = marketState.errorMessage!!,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        MarketFilterChip(
                            label = "点击重试",
                            selected = true,
                            onClick = { loadFirstPage() },
                        )
                    }
                }
            }
        } else if (marketState.items.isEmpty() && marketState.hasLoadedOnce) {
            item(key = "market_empty") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "没有找到匹配的配置",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "可以尝试清空关键词或放宽筛选条件，也可以把你调试好的配置上传分享给大家。",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (marketState.searchQuery.isNotBlank() ||
                                marketState.layoutFilter != null ||
                                marketState.materialFilter != null
                            ) {
                                MarketFilterChip(
                                    label = "重置筛选",
                                    selected = false,
                                    onClick = {
                                        marketState.searchQuery = ""
                                        marketState.layoutFilter = null
                                        marketState.materialFilter = null
                                        loadFirstPage("")
                                    },
                                )
                            }
                            MarketFilterChip(
                                label = "上传我的配置",
                                selected = true,
                                onClick = onOpenUpload,
                            )
                        }
                    }
                }
            }
        } else {
            if (marketState.errorMessage != null) {
                item(key = "market_inline_error") {
                    MarketBannerBox(
                        text = "刷新失败：${marketState.errorMessage}",
                        textColor = MARKET_COLOR_RED,
                        bgColor = MARKET_COLOR_RED_BG,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            items(
                items = marketState.items,
                key = { it.id },
            ) { item ->
                ConfigMarketItemCard(
                    item = item,
                    softGlassSupported = softGlassSupported,
                    isOwnedLocally = isOwnedLocally(item),
                    onClick = { selectedDetailItem = item },
                )
            }

            if (marketState.pagination?.hasMore == true) {
                item(key = "market_load_more") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .clickable(enabled = !marketState.isLoadingMore) { loadNextPage() },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val text = when {
                                marketState.isLoadingMore -> "正在加载更多..."
                                marketState.loadMoreError != null -> "${marketState.loadMoreError}，点击重试"
                                else -> "点击加载更多（已显示 ${marketState.items.size} / ${marketState.pagination?.total ?: marketState.items.size}）"
                            }
                            Text(
                                text = text,
                                fontSize = 13.sp,
                                color = if (marketState.loadMoreError != null) {
                                    MARKET_COLOR_RED
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "market_bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showServerSettings) {
        MarketServerSettingsDialog(
            onDismiss = { showServerSettings = false },
            onAuthorUpdated = {
                onAuthorUpdated()
            },
        )
    }

    selectedDetailItem?.let { item ->
        ConfigMarketDetailDialog(
            item = item,
            softGlassSupported = softGlassSupported,
            isOwnedLocally = isOwnedLocally(item),
            currentShortcuts = currentShortcuts,
            onDismiss = { selectedDetailItem = null },
            onEnterSandbox = { preview ->
                selectedDetailItem = null
                onEnterSandboxPreview(preview)
            },
            onOpenImport = { mode, detail ->
                selectedDetailItem = null
                importRequest = ConfigMarketImportRequest(
                    mode = mode,
                    summary = detail.item,
                    rawConfigJson = detail.jsonBody,
                    fromSandbox = false,
                )
            },
            onOpenLinkedShortcutPack = { packDetail ->
                parentConfigForLinkedPack = item
                selectedDetailItem = null
                linkedShortcutDetail = packDetail
            },
            onLinkedPackUnavailable = {
                loadFirstPage()
            },
            onOpenReport = {
                reportError = null
                reportTarget = item
            },
            onOpenDelete = {
                deleteError = null
                deleteTarget = item
            },
        )
    }

    ShortcutPackDetailHost(
        detailTarget = linkedShortcutDetail?.item,
        preloadedDetail = linkedShortcutDetail,
        currentShortcutCount = currentShortcuts.size,
        needsBackupSuggestion = needsBackupSuggestion,
        onDismissDetail = {
            linkedShortcutDetail = null
            parentConfigForLinkedPack?.let { selectedDetailItem = it }
            parentConfigForLinkedPack = null
        },
        onFlowExit = {
            linkedShortcutDetail = null
            parentConfigForLinkedPack = null
        },
        onEnterSandboxPreview = onEnterShortcutSandboxPreview,
        onAppendItems = onAppendShortcutItems,
        onImportAll = onImportAllShortcuts,
        onHiddenByReport = {
            onShortcutPackDeleted()
            loadFirstPage()
        },
        onDeleted = {
            onShortcutPackDeleted()
            loadFirstPage()
        },
    )

    importRequest?.let { req ->
        ConfigMarketImportDialog(
            request = req,
            existingProfileNames = existingProfileNames,
            localOffsetY = localOffsetY,
            softGlassSupported = softGlassSupported,
            needsBackupSuggestion = needsBackupSuggestion,
            onDismiss = { importRequest = null },
            onLinkedPackUnavailable = { loadFirstPage() },
            onConfirmSaveToLibrary = { profileName, keepLocalOffsetY, applyImmediately, backupCurrentFirst, linkedShortcuts, linkedPackId ->
                importRequest = null
                onSaveToLibrary(
                    req.summary,
                    req.rawConfigJson,
                    profileName,
                    keepLocalOffsetY,
                    applyImmediately,
                    backupCurrentFirst,
                    linkedShortcuts,
                    linkedPackId,
                )
            },
            onConfirmApplyDirectly = { keepLocalOffsetY, backupCurrentFirst, linkedShortcuts, linkedPackId ->
                importRequest = null
                onApplyDirectly(
                    req.summary,
                    req.rawConfigJson,
                    keepLocalOffsetY,
                    backupCurrentFirst,
                    linkedShortcuts,
                    linkedPackId,
                )
            },
        )
    }

    reportTarget?.let { target ->
        MarketReportDialog(
            itemName = target.name,
            isSubmitting = isReporting,
            errorMessage = reportError,
            onDismiss = { if (!isReporting) reportTarget = null },
            onSubmit = { reason ->
                isReporting = true
                reportError = null
                scope.launch {
                    val res = runCatching {
                        MarketApiClient.reportConfig(ctx, target.id, reason)
                    }
                    isReporting = false
                    res.onSuccess { reportRes ->
                        reportTarget = null
                        if (reportRes.hidden) {
                            marketState.items.removeAll { it.id == target.id }
                            if (selectedDetailItem?.id == target.id) {
                                selectedDetailItem = null
                            }
                            Toast.makeText(ctx, "举报成功，该配置因累计举报过多已被暂时隐藏", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(ctx, "已提交举报，感谢您的反馈", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { err ->
                        reportError = err.message ?: "举报失败"
                    }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        MarketDeleteConfirmDialog(
            itemName = target.name,
            isDeleting = isDeleting,
            errorMessage = deleteError,
            onDismiss = { if (!isDeleting) deleteTarget = null },
            onConfirm = {
                val token = MarketSettings.getManageToken(ctx, MarketSettings.KIND_CONFIG, target.id)
                val canDeleteByAuthor = localAuthorId != null && target.authorId == localAuthorId
                if (token.isNullOrBlank() && !canDeleteByAuthor) {
                    deleteError = "本地未找到此条目的管理凭证"
                    return@MarketDeleteConfirmDialog
                }
                isDeleting = true
                deleteError = null
                scope.launch {
                    val res = runCatching {
                        MarketApiClient.deleteConfig(ctx, target.id, token)
                    }
                    isDeleting = false
                    res.onSuccess {
                        MarketSettings.removeManageToken(ctx, MarketSettings.KIND_CONFIG, target.id)
                        marketState.items.removeAll { it.id == target.id }
                        if (selectedDetailItem?.id == target.id) {
                            selectedDetailItem = null
                        }
                        deleteTarget = null
                        Toast.makeText(ctx, "已从配置市场撤回删除「${target.name}」", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        if (err is MarketApiException && err.statusCode == 404) {
                            MarketSettings.removeManageToken(ctx, MarketSettings.KIND_CONFIG, target.id)
                            marketState.items.removeAll { it.id == target.id }
                            if (selectedDetailItem?.id == target.id) {
                                selectedDetailItem = null
                            }
                            deleteTarget = null
                            Toast.makeText(ctx, "该条目已不在市场中，已清理本地凭证", Toast.LENGTH_SHORT).show()
                        } else {
                            deleteError = err.message ?: "删除失败"
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ConfigMarketFilterCard(
    searchQuery: String,
    layoutFilter: Int?,
    materialFilter: Int?,
    sortOption: String,
    isLoading: Boolean,
    totalCount: Int?,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onLayoutSelect: (Int?) -> Unit,
    onMaterialSelect: (Int?) -> Unit,
    onSortSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = "搜索名称、简介、作者或标签",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                    modifier = Modifier.weight(1f),
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                            onSubmitSearch()
                        },
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "清空搜索",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                IconButton(
                    onClick = onSubmitSearch,
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = "搜索",
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarketFilterChip("全部布局", layoutFilter == null) { onLayoutSelect(null) }
                MarketFilterChip("蜂窝", layoutFilter == HyperOpenConfig.LAYOUT_HONEYCOMB) {
                    onLayoutSelect(HyperOpenConfig.LAYOUT_HONEYCOMB)
                }
                MarketFilterChip("圆环", layoutFilter == HyperOpenConfig.LAYOUT_RING) {
                    onLayoutSelect(HyperOpenConfig.LAYOUT_RING)
                }
                MarketFilterChip("扇面", layoutFilter == HyperOpenConfig.LAYOUT_ARC) {
                    onLayoutSelect(HyperOpenConfig.LAYOUT_ARC)
                }
                MarketFilterChip("经典", layoutFilter == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
                    onLayoutSelect(HyperOpenConfig.LAYOUT_SYSTEM_STOCK)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarketFilterChip("全部材质", materialFilter == null) { onMaterialSelect(null) }
                MarketFilterChip("柔光玻璃", materialFilter == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
                    onMaterialSelect(HyperOpenConfig.MATERIAL_SOFT_GLASS)
                }
                MarketFilterChip("高斯模糊", materialFilter == HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR) {
                    onMaterialSelect(HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR)
                }
                Spacer(Modifier.width(4.dp))
                MarketFilterChip("最新", sortOption == "latest") { onSortSelect("latest") }
                MarketFilterChip("最多下载", sortOption == "downloads") { onSortSelect("downloads") }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (totalCount != null) "共 $totalCount 份公开配置" else "配置市场列表",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLoading, onClick = onRefresh)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isLoading) "刷新中..." else "刷新",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigMarketItemCard(
    item: MarketConfigSummary,
    softGlassSupported: Boolean,
    isOwnedLocally: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val needsNewerApp = item.minAppVersionCode > BuildConfig.VERSION_CODE
    val materialText = displayMarketMaterialLabel(item.materialStyle, softGlassSupported)

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isOwnedLocally) {
                    Spacer(Modifier.width(6.dp))
                    MarketBadgePill(
                        text = "我上传的",
                        textColor = primary,
                        bgColor = primary.copy(alpha = 0.12f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Download,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${item.downloads}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            MarketAuthorBadgeRow(
                author = item.author,
                isOfficial = item.authorIsOfficial,
                badgeLabel = item.authorBadgeLabel,
                prefix = "作者 ",
                suffix = "· ${formatMarketShortTime(item.updatedAt)}",
            )

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarketBadgePill(
                    text = item.layoutLabel(),
                    textColor = primary,
                    bgColor = primary.copy(alpha = 0.12f),
                )
                MarketBadgePill(
                    text = materialText,
                    textColor = MiuixTheme.colorScheme.onSurface,
                    bgColor = MiuixTheme.colorScheme.surfaceVariant,
                )
                MarketBadgePill(
                    text = "${item.keyCount} 项参数",
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                )
                if (item.linkedShortcutPack != null) {
                    MarketBadgePill(
                        text = "含配套快捷包",
                        textColor = MARKET_COLOR_GREEN,
                        bgColor = MARKET_COLOR_GREEN_BG,
                    )
                }
                if (needsNewerApp) {
                    MarketBadgePill(
                        text = "需 vCode≥${item.minAppVersionCode}",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }
            }

            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.9f),
                        )
                    }
                    if (item.tags.size > 3) {
                        Text(
                            text = "+${item.tags.size - 3}",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigMarketDetailDialog(
    item: MarketConfigSummary,
    softGlassSupported: Boolean,
    isOwnedLocally: Boolean,
    currentShortcuts: List<ShortcutItem>,
    onDismiss: () -> Unit,
    onEnterSandbox: (MarketSandboxPreview) -> Unit,
    onOpenImport: (ConfigMarketImportMode, MarketConfigDetail) -> Unit,
    onOpenLinkedShortcutPack: (MarketShortcutPackDetail) -> Unit,
    onLinkedPackUnavailable: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MiuixTheme.colorScheme.primary
    var activeAction by remember(item.id) { mutableStateOf<String?>(null) }
    var actionError by remember(item.id) { mutableStateOf<String?>(null) }
    var showPreviewModeDialog by remember(item.id) { mutableStateOf(false) }

    val needsNewerApp = item.minAppVersionCode > BuildConfig.VERSION_CODE
    val softGlassDowngraded =
        !softGlassSupported && item.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS
    val busy = activeAction != null

    fun launchPreview(withLinkedPack: Boolean) {
        val linkedPack = item.linkedShortcutPack
        val useLinkedPack = withLinkedPack && linkedPack != null
        activeAction = "preview"
        actionError = null
        scope.launch {
            if (!useLinkedPack) {
                val res = runCatching {
                    val detail = MarketApiClient.getConfigDetail(ctx, item.id, download = false)
                    val previewConfig = withContext(Dispatchers.Default) {
                        ConfigBackup.buildPreviewConfigFromBackupJson(
                            ctx,
                            detail.jsonBody,
                            currentShortcuts,
                            false,
                            softGlassSupported,
                        )
                    }
                    MarketSandboxPreview(
                        kind = MarketSandboxKind.CONFIG,
                        id = detail.item.id,
                        title = detail.item.name,
                        author = detail.item.author,
                        authorIsOfficial = detail.item.authorIsOfficial,
                        authorBadgeLabel = detail.item.authorBadgeLabel,
                        config = previewConfig,
                        rawConfigJson = detail.jsonBody,
                        hasShortcuts = false,
                        shortcutCount = 0,
                        shortcutList = previewConfig.shortcuts.toList(),
                        minAppVersionCode = detail.item.minAppVersionCode,
                        appVersion = detail.item.appVersion,
                        hasRootRisk = false,
                        riskSummary = emptyList(),
                        summary = detail.item,
                        previewingWithLinkedPack = false,
                    )
                }
                activeAction = null
                res.onSuccess { preview ->
                    onEnterSandbox(preview)
                }.onFailure { err ->
                    actionError = err.message ?: "无法加载沙箱预览"
                }
                return@launch
            }

            val targetPack = linkedPack
            val res = runCatching {
                val (detail, packRes) = coroutineScope {
                    val configDeferred = async {
                        MarketApiClient.getConfigDetail(ctx, item.id, download = false)
                    }
                    val packDeferred = async {
                        runCatching {
                            MarketApiClient.getShortcutDetail(ctx, targetPack.id, download = false)
                        }
                    }
                    configDeferred.await() to packDeferred.await()
                }
                val packErr = packRes.exceptionOrNull()
                if (packErr != null) {
                    throw LinkedPackPreviewException(packErr)
                }
                val packDetail = packRes.getOrThrow()
                val linkedItems = withContext(Dispatchers.Default) {
                    parseShortcutPackItems(packDetail.shortcutsJson).map(::cloneShortcutForImport)
                }
                val hasLinkedItems = linkedItems.isNotEmpty()
                val previewConfig = withContext(Dispatchers.Default) {
                    ConfigBackup.buildPreviewConfigFromBackupJson(
                        ctx,
                        detail.jsonBody,
                        if (hasLinkedItems) linkedItems else currentShortcuts,
                        false,
                        softGlassSupported,
                    )
                }
                val preview = MarketSandboxPreview(
                    kind = MarketSandboxKind.CONFIG,
                    id = detail.item.id,
                    title = detail.item.name,
                    author = detail.item.author,
                    authorIsOfficial = detail.item.authorIsOfficial,
                    authorBadgeLabel = detail.item.authorBadgeLabel,
                    config = previewConfig,
                    rawConfigJson = detail.jsonBody,
                    hasShortcuts = hasLinkedItems,
                    shortcutCount = if (hasLinkedItems) linkedItems.size else 0,
                    shortcutList = if (hasLinkedItems) linkedItems else previewConfig.shortcuts.toList(),
                    minAppVersionCode = detail.item.minAppVersionCode,
                    appVersion = detail.item.appVersion,
                    hasRootRisk = if (hasLinkedItems) packDetail.item.hasRootRisk else false,
                    riskSummary = if (hasLinkedItems) packDetail.item.riskSummary else emptyList(),
                    summary = detail.item,
                    shortcutPackSummary = if (hasLinkedItems) packDetail.item else null,
                    previewingWithLinkedPack = hasLinkedItems,
                    linkedPackName = if (hasLinkedItems) {
                        packDetail.item.name.ifBlank { targetPack.name }
                    } else {
                        null
                    },
                )
                preview to hasLinkedItems
            }
            activeAction = null
            res.onSuccess { (preview, hasLinkedItems) ->
                if (!hasLinkedItems) {
                    Toast.makeText(
                        ctx,
                        "配套快捷包内容为空，已使用本机快捷方式预览",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onEnterSandbox(preview)
            }.onFailure { err ->
                if (err is LinkedPackPreviewException) {
                    val cause = err.cause
                    if (cause is MarketApiException && cause.statusCode == 404) {
                        actionError = "配套快捷包已被作者删除或下架，请改用「仅预览外观配置」"
                        onLinkedPackUnavailable()
                    } else {
                        actionError = cause?.message ?: "无法加载配套快捷包"
                    }
                } else {
                    actionError = err.message ?: "无法加载沙箱预览"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isOwnedLocally) {
                        Spacer(Modifier.width(8.dp))
                        MarketBadgePill(
                            text = "本机上传",
                            textColor = primary,
                            bgColor = primary.copy(alpha = 0.14f),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                MarketAuthorBadgeRow(
                    author = item.author,
                    isOfficial = item.authorIsOfficial,
                    badgeLabel = item.authorBadgeLabel,
                    prefix = "作者：",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(2.dp))
                val verStr = if (item.appVersion.isNotBlank()) "来源 v${item.appVersion} · " else ""
                Text(
                    text = "$verStr${item.downloads} 次下载 · ${formatMarketSize(item.sizeBytes)}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "发布于 ${formatMarketFullTime(item.createdAt)} · 更新于 ${formatMarketFullTime(item.updatedAt)}",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MarketBadgePill(
                        text = item.layoutLabel(),
                        textColor = primary,
                        bgColor = primary.copy(alpha = 0.12f),
                    )
                    MarketBadgePill(
                        text = displayMarketMaterialLabel(item.materialStyle, softGlassSupported),
                        textColor = MiuixTheme.colorScheme.onSurface,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant,
                    )
                    MarketBadgePill(
                        text = "${item.keyCount} 项外观参数",
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    )
                }

                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = item.description,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }

                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tags.forEach { tag ->
                            MarketBadgePill(
                                text = "#$tag",
                                textColor = primary,
                                bgColor = primary.copy(alpha = 0.1f),
                            )
                        }
                    }
                }

                item.linkedShortcutPack?.let { linkedPack ->
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(primary.copy(alpha = 0.08f))
                            .border(1.dp, primary.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "配套快捷方式包",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = linkedPack.name.ifBlank { "配套快捷方式" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MarketBadgePill(
                                text = "${linkedPack.shortcutCount} 快捷项",
                                textColor = primary,
                                bgColor = primary.copy(alpha = 0.14f),
                            )
                            if (linkedPack.folderCount > 0) {
                                MarketBadgePill(
                                    text = "${linkedPack.folderCount} 文件夹",
                                    textColor = MiuixTheme.colorScheme.onSurface,
                                    bgColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f),
                                )
                            }
                            if (linkedPack.customImageCount > 0) {
                                MarketBadgePill(
                                    text = "${linkedPack.customImageCount} 自定义图",
                                    textColor = MARKET_COLOR_AMBER,
                                    bgColor = MARKET_COLOR_AMBER_BG,
                                )
                            }
                            if (linkedPack.hasRootRisk) {
                                MarketBadgePill(
                                    text = "含 Root/高危",
                                    textColor = MARKET_COLOR_RED,
                                    bgColor = MARKET_COLOR_RED_BG,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                text = if (activeAction == "linked_pack") "加载快捷包..." else "查看 / 导入配套快捷包",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                enabled = !busy,
                                onClick = {
                                    activeAction = "linked_pack"
                                    actionError = null
                                    scope.launch {
                                        val res = runCatching {
                                            MarketApiClient.getShortcutDetail(ctx, linkedPack.id, download = false)
                                        }
                                        activeAction = null
                                        res.onSuccess { packDetail ->
                                            onOpenLinkedShortcutPack(packDetail)
                                        }.onFailure { err ->
                                            if (err is MarketApiException && err.statusCode == 404) {
                                                actionError = "该配套快捷包已被作者删除或下架"
                                                onLinkedPackUnavailable()
                                            } else {
                                                actionError = err.message ?: "无法加载配套快捷包"
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (needsNewerApp) {
                    Spacer(Modifier.height(12.dp))
                    MarketBannerBox(
                        text = "版本兼容提示：该配置建议模块版本 code ≥ ${item.minAppVersionCode}（当前本机为 ${BuildConfig.VERSION_CODE}），不支持的新参数将被安全跳过。",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }

                if (softGlassDowngraded) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = "此配置为柔光玻璃材质，当前设备不支持；预览或应用时会自动转为高斯模糊并补偿模糊半径。",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }

                if (!actionError.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MarketBannerBox(
                        text = actionError!!,
                        textColor = MARKET_COLOR_RED,
                        bgColor = MARKET_COLOR_RED_BG,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = if (activeAction == "preview") "加载预览..." else "临时预览",
                        enabled = !busy,
                        onClick = {
                            if (item.linkedShortcutPack == null) {
                                launchPreview(false)
                            } else {
                                showPreviewModeDialog = true
                            }
                        },
                    )
                    TextButton(
                        text = if (activeAction == "save") "下载中..." else "保存到配置库",
                        enabled = !busy,
                        onClick = {
                            activeAction = "save"
                            actionError = null
                            scope.launch {
                                val res = runCatching {
                                    MarketApiClient.getConfigDetail(ctx, item.id, download = true)
                                }
                                activeAction = null
                                res.onSuccess { detail ->
                                    onOpenImport(ConfigMarketImportMode.SAVE_TO_LIBRARY, detail)
                                }.onFailure { err ->
                                    actionError = err.message ?: "下载配置失败"
                                }
                            }
                        },
                    )
                    TextButton(
                        text = if (activeAction == "apply") "下载中..." else "直接应用",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !busy,
                        onClick = {
                            activeAction = "apply"
                            actionError = null
                            scope.launch {
                                val res = runCatching {
                                    MarketApiClient.getConfigDetail(ctx, item.id, download = true)
                                }
                                activeAction = null
                                res.onSuccess { detail ->
                                    onOpenImport(ConfigMarketImportMode.APPLY_DIRECTLY, detail)
                                }.onFailure { err ->
                                    actionError = err.message ?: "下载配置失败"
                                }
                            }
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = "举报",
                            enabled = !busy,
                            onClick = onOpenReport,
                        )
                        if (isOwnedLocally) {
                            TextButton(
                                text = "撤回删除",
                                enabled = !busy,
                                onClick = onOpenDelete,
                            )
                        }
                    }
                    TextButton(
                        text = "关闭",
                        enabled = !busy,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }

    val linkedPack = item.linkedShortcutPack
    if (showPreviewModeDialog && linkedPack != null) {
        ConfigPreviewModeDialog(
            linkedPack = linkedPack,
            onDismiss = { showPreviewModeDialog = false },
            onSelectConfigOnly = {
                showPreviewModeDialog = false
                launchPreview(false)
            },
            onSelectWithLinkedPack = {
                showPreviewModeDialog = false
                launchPreview(true)
            },
        )
    }
}

private class LinkedPackPreviewException(cause: Throwable) : Exception(cause)

@Composable
private fun ConfigPreviewModeDialog(
    linkedPack: MarketLinkedShortcutPack,
    onDismiss: () -> Unit,
    onSelectConfigOnly: () -> Unit,
    onSelectWithLinkedPack: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val packName = linkedPack.name.ifBlank { "配套快捷方式" }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "选择预览方式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "该配置关联了配套快捷包，请选择纯内存沙箱预览使用的快捷方式：",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                        .clickable(onClick = onSelectConfigOnly)
                        .padding(14.dp),
                ) {
                    Text(
                        text = "仅预览外观配置",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "使用你当前本地的快捷方式列表预览外观排版、材质与动效",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.08f))
                        .border(1.dp, primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onSelectWithLinkedPack)
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "与配套快捷包一起预览",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            modifier = Modifier.weight(1f),
                        )
                        MarketBadgePill(
                            text = "${linkedPack.shortcutCount} 项",
                            textColor = primary,
                            bgColor = primary.copy(alpha = 0.14f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "同步加载配套快捷包「$packName」（${linkedPack.shortcutCount} 项）在纯内存沙箱中完整体验（不修改本地配置与快捷列表）",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}
