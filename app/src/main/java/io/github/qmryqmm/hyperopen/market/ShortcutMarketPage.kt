package io.github.qmryqmm.hyperopen.market

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qmryqmm.hyperopen.BuildConfig
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SHORTCUT_ACTION_FILTERS: List<Pair<String?, String>> = listOf(
    null to "全部",
    ShortcutItem.ACTION_APP to "应用",
    ShortcutItem.ACTION_INTENT_URI to "意图",
    ShortcutItem.ACTION_ACTIVITY to "Activity",
    ShortcutItem.ACTION_FOLDER to "文件夹",
    ShortcutItem.ACTION_FLASHLIGHT to "手电筒",
    ShortcutItem.ACTION_XIAOAI to "小爱",
    ShortcutItem.ACTION_MUTE to "静音",
    ShortcutItem.ACTION_DND to "勿扰",
    ShortcutItem.ACTION_CAMERA to "相机",
    ShortcutItem.ACTION_SHELL to "Shell",
    ShortcutItem.ACTION_BROADCAST to "广播",
)

private data class ShortcutPackImportAllRequest(
    val summary: MarketShortcutPackSummary,
    val items: List<ShortcutItem>,
)

/**
 * 快捷方式市场页面状态持有者：提升到 HyperOpenApp 顶层后，
 * 在切换「本地快捷项 | 快捷市场」或进出「沙箱预览」时保留列表、筛选条件与滚动位置。
 */
class ShortcutMarketState {
    val items = mutableStateListOf<MarketShortcutPackSummary>()
    var pagination by mutableStateOf<MarketPagination?>(null)
    var searchQuery by mutableStateOf("")
    var appliedQuery by mutableStateOf("")
    var actionTypeFilter by mutableStateOf<String?>(null)
    var onlySafeFilter by mutableStateOf(false)
    var onlyCustomImagesFilter by mutableStateOf(false)
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
fun rememberShortcutMarketState(): ShortcutMarketState {
    return remember { ShortcutMarketState() }
}

@Composable
fun ShortcutMarketPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    marketState: ShortcutMarketState,
    refreshTrigger: Int,
    currentShortcutCount: Int,
    needsBackupSuggestion: Boolean,
    onOpenUpload: () -> Unit,
    onEnterSandboxPreview: (detail: MarketShortcutPackDetail, items: List<ShortcutItem>) -> Unit,
    onAppendItems: (
        summary: MarketShortcutPackSummary,
        items: List<ShortcutItem>,
        shouldRecordDownload: Boolean,
    ) -> Unit,
    onImportAll: (
        summary: MarketShortcutPackSummary,
        items: List<ShortcutItem>,
        replaceExisting: Boolean,
        backupCurrentFirst: Boolean,
        shouldRecordDownload: Boolean,
    ) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showServerSettings by remember { mutableStateOf(false) }
    var selectedDetailItem by remember { mutableStateOf<MarketShortcutPackSummary?>(null) }
    var importAllRequest by remember { mutableStateOf<ShortcutPackImportAllRequest?>(null) }
    var reportTarget by remember { mutableStateOf<MarketShortcutPackSummary?>(null) }
    var isReporting by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MarketShortcutPackSummary?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun loadFirstPage(queryOverride: String = marketState.searchQuery.trim()) {
        val gen = ++marketState.requestGeneration
        marketState.appliedQuery = queryOverride
        marketState.isLoading = true
        marketState.isLoadingMore = false
        marketState.errorMessage = null
        marketState.loadMoreError = null
        scope.launch {
            val res = runCatching {
                MarketApiClient.listShortcuts(
                    ctx = ctx,
                    query = queryOverride,
                    actionType = marketState.actionTypeFilter,
                    hasRoot = if (marketState.onlySafeFilter) false else null,
                    hasImages = if (marketState.onlyCustomImagesFilter) true else null,
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
                marketState.errorMessage = err.message ?: "加载快捷方式市场失败"
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
                MarketApiClient.listShortcuts(
                    ctx = ctx,
                    query = marketState.appliedQuery,
                    actionType = marketState.actionTypeFilter,
                    hasRoot = if (marketState.onlySafeFilter) false else null,
                    hasImages = if (marketState.onlyCustomImagesFilter) true else null,
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

    LaunchedEffect(marketState.searchQuery) {
        val trimmed = marketState.searchQuery.trim()
        if (trimmed == marketState.appliedQuery) return@LaunchedEffect
        delay(380L)
        if (trimmed != marketState.appliedQuery) {
            loadFirstPage(trimmed)
        }
    }

    val lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    val totalItemCount = lazyListState.layoutInfo.totalItemsCount
    LaunchedEffect(
        lastVisibleIndex,
        totalItemCount,
        marketState.pagination?.hasMore,
        marketState.isLoading,
        marketState.isLoadingMore,
    ) {
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
        item(key = "shortcut_market_notice") {
            MarketTopNoticeCard(
                onOpenServerSettings = { showServerSettings = true },
                onOpenUpload = onOpenUpload,
                uploadLabel = "上传当前快捷项",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        item(key = "shortcut_market_search_filters") {
            ShortcutMarketFilterCard(
                searchQuery = marketState.searchQuery,
                actionTypeFilter = marketState.actionTypeFilter,
                onlySafeFilter = marketState.onlySafeFilter,
                onlyCustomImagesFilter = marketState.onlyCustomImagesFilter,
                sortOption = marketState.sortOption,
                isLoading = marketState.isLoading,
                totalCount = marketState.pagination?.total,
                onSearchQueryChange = { marketState.searchQuery = it },
                onSubmitSearch = { loadFirstPage(marketState.searchQuery.trim()) },
                onActionTypeSelect = { next ->
                    if (marketState.actionTypeFilter != next) {
                        marketState.actionTypeFilter = next
                        loadFirstPage()
                    }
                },
                onToggleSafe = {
                    marketState.onlySafeFilter = !marketState.onlySafeFilter
                    loadFirstPage()
                },
                onToggleCustomImages = {
                    marketState.onlyCustomImagesFilter = !marketState.onlyCustomImagesFilter
                    loadFirstPage()
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
            item(key = "shortcut_market_loading") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "正在加载快捷方式市场...",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                    )
                }
            }
        } else if (marketState.errorMessage != null && marketState.items.isEmpty()) {
            item(key = "shortcut_market_error") {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarketFilterChip(
                                label = "点击重试",
                                selected = true,
                                onClick = { loadFirstPage() },
                            )
                            MarketFilterChip(
                                label = "检查服务器地址",
                                selected = false,
                                onClick = { showServerSettings = true },
                            )
                        }
                    }
                }
            }
        } else if (marketState.items.isEmpty() && marketState.hasLoadedOnce) {
            item(key = "shortcut_market_empty") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "没有找到匹配的快捷包",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "可以尝试清空关键词或放宽动作类型与安全筛选条件，也可以把你整理好的快捷方式打包分享出来。",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (marketState.searchQuery.isNotBlank() ||
                                marketState.actionTypeFilter != null ||
                                marketState.onlySafeFilter ||
                                marketState.onlyCustomImagesFilter
                            ) {
                                MarketFilterChip(
                                    label = "重置筛选",
                                    selected = false,
                                    onClick = {
                                        marketState.searchQuery = ""
                                        marketState.actionTypeFilter = null
                                        marketState.onlySafeFilter = false
                                        marketState.onlyCustomImagesFilter = false
                                        loadFirstPage("")
                                    },
                                )
                            }
                            MarketFilterChip(
                                label = "上传当前快捷项",
                                selected = true,
                                onClick = onOpenUpload,
                            )
                        }
                    }
                }
            }
        } else {
            if (marketState.errorMessage != null) {
                item(key = "shortcut_market_inline_error") {
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
                ShortcutMarketItemCard(
                    item = item,
                    isOwnedLocally = MarketSettings.hasManageToken(ctx, MarketSettings.KIND_SHORTCUT, item.id),
                    onClick = { selectedDetailItem = item },
                )
            }

            if (marketState.pagination?.hasMore == true) {
                item(key = "shortcut_market_load_more") {
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

        item(key = "shortcut_market_bottom_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showServerSettings) {
        MarketServerSettingsDialog(
            onDismiss = { showServerSettings = false },
            onSaved = {
                showServerSettings = false
                Toast.makeText(ctx, "市场服务器地址已更新", Toast.LENGTH_SHORT).show()
                loadFirstPage()
            },
        )
    }

    selectedDetailItem?.let { item ->
        ShortcutMarketDetailDialog(
            summary = item,
            isOwnedLocally = MarketSettings.hasManageToken(ctx, MarketSettings.KIND_SHORTCUT, item.id),
            onDismiss = { selectedDetailItem = null },
            onBuildSandboxPreview = { detail, parsedItems ->
                selectedDetailItem = null
                onEnterSandboxPreview(detail, parsedItems)
            },
            onAddSingleItem = { summary, shortcutItem, recordDownload ->
                onAppendItems(summary, listOf(shortcutItem), recordDownload)
            },
            onImportSelectedItems = { summary, pickedItems, recordDownload ->
                onAppendItems(summary, pickedItems, recordDownload)
            },
            onOpenImportAll = { summary, allItems ->
                selectedDetailItem = null
                importAllRequest = ShortcutPackImportAllRequest(
                    summary = summary,
                    items = allItems,
                )
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

    importAllRequest?.let { req ->
        ShortcutPackImportAllDialog(
            summary = req.summary,
            items = req.items,
            currentShortcutCount = currentShortcutCount,
            needsBackupSuggestion = needsBackupSuggestion,
            onDismiss = { importAllRequest = null },
            onConfirm = { replaceExisting, backupCurrentFirst ->
                importAllRequest = null
                onImportAll(
                    req.summary,
                    req.items,
                    replaceExisting,
                    backupCurrentFirst,
                    true,
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
                        MarketApiClient.reportShortcutPack(ctx, target.id, reason)
                    }
                    isReporting = false
                    res.onSuccess { reportRes ->
                        reportTarget = null
                        if (reportRes.hidden) {
                            marketState.items.removeAll { it.id == target.id }
                            if (selectedDetailItem?.id == target.id) {
                                selectedDetailItem = null
                            }
                            Toast.makeText(ctx, "举报成功，该快捷包因累计举报过多已被暂时隐藏", Toast.LENGTH_LONG).show()
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
                val token = MarketSettings.getManageToken(ctx, MarketSettings.KIND_SHORTCUT, target.id)
                if (token.isNullOrBlank()) {
                    deleteError = "本地未找到此条目的管理凭证"
                    return@MarketDeleteConfirmDialog
                }
                isDeleting = true
                deleteError = null
                scope.launch {
                    val res = runCatching {
                        MarketApiClient.deleteShortcutPack(ctx, target.id, token)
                    }
                    isDeleting = false
                    res.onSuccess {
                        MarketSettings.removeManageToken(ctx, MarketSettings.KIND_SHORTCUT, target.id)
                        marketState.items.removeAll { it.id == target.id }
                        if (selectedDetailItem?.id == target.id) {
                            selectedDetailItem = null
                        }
                        deleteTarget = null
                        Toast.makeText(ctx, "已从快捷市场撤回删除「${target.name}」", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        if (err is MarketApiException && err.statusCode == 404) {
                            MarketSettings.removeManageToken(ctx, MarketSettings.KIND_SHORTCUT, target.id)
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
private fun ShortcutMarketFilterCard(
    searchQuery: String,
    actionTypeFilter: String?,
    onlySafeFilter: Boolean,
    onlyCustomImagesFilter: Boolean,
    sortOption: String,
    isLoading: Boolean,
    totalCount: Int?,
    onSearchQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onActionTypeSelect: (String?) -> Unit,
    onToggleSafe: () -> Unit,
    onToggleCustomImages: () -> Unit,
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
                SHORTCUT_ACTION_FILTERS.forEach { (actionKey, label) ->
                    MarketFilterChip(
                        label = label,
                        selected = actionTypeFilter == actionKey,
                        onClick = { onActionTypeSelect(actionKey) },
                    )
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
                MarketFilterChip("仅无高危", onlySafeFilter, onToggleSafe)
                MarketFilterChip("含自定义图", onlyCustomImagesFilter, onToggleCustomImages)
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
                    text = if (totalCount != null) "共 $totalCount 份公开快捷包" else "快捷方式市场列表",
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
private fun ShortcutMarketItemCard(
    item: MarketShortcutPackSummary,
    isOwnedLocally: Boolean,
    onClick: () -> Unit,
) {
    val primary = MiuixTheme.colorScheme.primary
    val needsNewerApp = item.minAppVersionCode > BuildConfig.VERSION_CODE

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
            Text(
                text = "作者 ${item.author.ifBlank { "匿名" }} · ${formatMarketShortTime(item.updatedAt)}",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                    text = "${item.shortcutCount} 快捷项",
                    textColor = primary,
                    bgColor = primary.copy(alpha = 0.12f),
                )
                if (item.folderCount > 0) {
                    MarketBadgePill(
                        text = "${item.folderCount} 文件夹",
                        textColor = MiuixTheme.colorScheme.onSurface,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant,
                    )
                }
                item.actionTypes.take(4).forEach { actionType ->
                    MarketBadgePill(
                        text = shortcutActionTypeLabel(actionType),
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    )
                }
                if (item.actionTypes.size > 4) {
                    MarketBadgePill(
                        text = "+${item.actionTypes.size - 4} 类型",
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        bgColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    )
                }
                if (item.customImageCount > 0) {
                    MarketBadgePill(
                        text = "${item.customImageCount} 自定义图",
                        textColor = MARKET_COLOR_AMBER,
                        bgColor = MARKET_COLOR_AMBER_BG,
                    )
                }
                if (item.hasRootRisk) {
                    MarketBadgePill(
                        text = "含 Root/高危",
                        textColor = MARKET_COLOR_RED,
                        bgColor = MARKET_COLOR_RED_BG,
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
