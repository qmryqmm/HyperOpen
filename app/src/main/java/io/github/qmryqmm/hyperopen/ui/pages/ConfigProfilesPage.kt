package io.github.qmryqmm.hyperopen.ui.pages

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.github.qmryqmm.hyperopen.config.ConfigProfile
import io.github.qmryqmm.hyperopen.config.ConfigProfileStore
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ConfigProfileSelectionState {
    var multiSelect by mutableStateOf(false)
    var showDeleteRequest by mutableStateOf(false)
    var showRenameRequest by mutableStateOf(false)
    val selectedIds = mutableStateListOf<String>()

    fun isAllSelected(profiles: List<ConfigProfile>): Boolean {
        return profiles.isNotEmpty() && profiles.all { it.id in selectedIds }
    }

    fun enter(id: String) {
        multiSelect = true
        selectedIds.clear()
        selectedIds.add(id)
    }

    fun toggle(id: String) {
        if (!multiSelect) {
            enter(id)
            return
        }
        if (!selectedIds.remove(id)) selectedIds.add(id)
        if (selectedIds.isEmpty()) clear()
    }

    fun toggleAll(profiles: List<ConfigProfile>) {
        if (isAllSelected(profiles)) {
            clear()
        } else {
            selectedIds.clear()
            selectedIds.addAll(profiles.map { it.id })
        }
    }

    fun clear() {
        multiSelect = false
        showDeleteRequest = false
        showRenameRequest = false
        selectedIds.clear()
    }
}

@Composable
fun rememberConfigProfileSelectionState(): ConfigProfileSelectionState {
    return remember { ConfigProfileSelectionState() }
}

class ConfigProfileInteractionState {
    var draggingId by mutableStateOf<String?>(null)
    var dragOriginWindowY by mutableFloatStateOf(0f)
    var totalDragY by mutableFloatStateOf(0f)
    var suppressClick by mutableStateOf(false)
    var hasReorderedInDrag by mutableStateOf(false)
    var rowSpacingPx by mutableFloatStateOf(20f)
    var fallbackRowHeightPx by mutableFloatStateOf(200f)
    var viewportTopWindowY by mutableFloatStateOf(0f)
    var viewportBottomWindowY by mutableFloatStateOf(0f)
    var anchorOriginWindowYProvider: (() -> Float?)? = null
    val rowHeights = mutableStateMapOf<String, Float>()
    val rowWindowY = mutableStateMapOf<String, Float>()

    val mainVisualTopY: Float
        get() = dragOriginWindowY + totalDragY

    fun anchorRowHeight(id: String): Float = rowHeights[id] ?: fallbackRowHeightPx

    fun getNodeTopY(id: String, profiles: List<ConfigProfile>): Float {
        var top = 0f
        for (profile in profiles) {
            if (profile.id == id) return top
            top += anchorRowHeight(profile.id) + rowSpacingPx
        }
        return top
    }

    fun windowTopOf(id: String, profiles: List<ConfigProfile>): Float {
        val origin = anchorOriginWindowYProvider?.invoke()
            ?: rowWindowY.values.minOrNull()
            ?: 0f
        return origin + getNodeTopY(id, profiles)
    }

    fun onDragDelta(
        dy: Float,
        profiles: List<ConfigProfile>,
        move: (String, Int) -> Unit,
        onHapticTick: (() -> Unit)? = null,
    ) {
        if (dy == 0f) return
        val id = draggingId ?: return
        val currentIndex = profiles.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        val dragHeight = anchorRowHeight(id)
        val dragCenterY = mainVisualTopY + dragHeight / 2f
        val direction = if (dy > 0f) 1 else -1
        val targetIndex = currentIndex + direction
        if (targetIndex !in profiles.indices) return

        val target = profiles[targetIndex]
        val targetTopY = windowTopOf(target.id, profiles)
        val targetCenterY = targetTopY + anchorRowHeight(target.id) / 2f
        val shouldSwap = if (direction > 0) {
            dragCenterY >= targetCenterY
        } else {
            dragCenterY <= targetCenterY
        }
        if (shouldSwap) {
            hasReorderedInDrag = true
            move(id, targetIndex)
            onHapticTick?.invoke()
        }
    }

    fun clickSuppressed(): Boolean = suppressClick

    fun clearDrag() {
        draggingId = null
        dragOriginWindowY = 0f
        totalDragY = 0f
        hasReorderedInDrag = false
    }
}

@Composable
fun rememberConfigProfileInteractionState(): ConfigProfileInteractionState {
    return remember { ConfigProfileInteractionState() }
}

/** 配置卡片手势：轻触由 BasicComponent 处理；长按不动多选，非多选态下长按后拖动排序。 */
@Composable
private fun Modifier.configProfileDragGestures(
    state: ConfigProfileInteractionState,
    profileId: String,
    isMultiSelect: Boolean,
    getProfiles: () -> List<ConfigProfile>,
    move: (String, Int) -> Unit,
    onLongPressNoMove: () -> Unit,
    onDragSettled: () -> Unit,
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val multiSelectState = rememberUpdatedState(isMultiSelect)
    val getProfilesState = rememberUpdatedState(getProfiles)
    val moveState = rememberUpdatedState(move)
    val onLongPressNoMoveState = rememberUpdatedState(onLongPressNoMove)
    val onDragSettledState = rememberUpdatedState(onDragSettled)

    return pointerInput(profileId) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.suppressClick = false
            state.rowSpacingPx = with(density) { 10.dp.toPx() }
            state.fallbackRowHeightPx = with(density) { 80.dp.toPx() }

            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull "cancel"
                    if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull "up"
                    val net = change.position - down.position
                    if (
                        abs(net.x) > viewConfiguration.touchSlop ||
                        abs(net.y) > viewConfiguration.touchSlop
                    ) {
                        return@withTimeoutOrNull "scroll"
                    }
                }
                "unreachable"
            } ?: "timeout"
            if (outcome != "timeout") return@awaitEachGesture

            state.suppressClick = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            if (multiSelectState.value) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || change.changedToUpIgnoreConsumed()) {
                        onLongPressNoMoveState.value()
                        break
                    }
                }
                return@awaitEachGesture
            }

            state.draggingId = profileId
            state.dragOriginWindowY = state.windowTopOf(profileId, getProfilesState.value())
            state.totalDragY = 0f
            state.hasReorderedInDrag = false

            var dragDistance = 0f
            var settledCalled = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || change.changedToUpIgnoreConsumed()) {
                        if (dragDistance <= viewConfiguration.touchSlop && !state.hasReorderedInDrag) {
                            onLongPressNoMoveState.value()
                        } else if (state.hasReorderedInDrag) {
                            settledCalled = true
                            onDragSettledState.value()
                        }
                        break
                    }

                    val delta = change.positionChangeIgnoreConsumed()
                    dragDistance += abs(delta.x) + abs(delta.y)
                    if (delta.y != 0f) {
                        state.totalDragY += delta.y
                        state.onDragDelta(
                            dy = delta.y,
                            profiles = getProfilesState.value(),
                            move = moveState.value,
                            onHapticTick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        )
                    }
                }
            } finally {
                if (state.hasReorderedInDrag && !settledCalled) {
                    settledCalled = true
                    onDragSettledState.value()
                }
                state.clearDrag()
            }
        }
    }
}

/** 拖拽主卡贴住手指，放大抬升并始终绘制在其他卡片上方。 */
@Composable
private fun Modifier.configProfileDragVisuals(
    state: ConfigProfileInteractionState,
    profileId: String,
    profiles: List<ConfigProfile>,
): Modifier {
    val dragging = state.draggingId == profileId
    val latestProfiles by rememberUpdatedState(profiles)
    return this
        .graphicsLayer {
            if (dragging) {
                translationY = state.mainVisualTopY - state.windowTopOf(profileId, latestProfiles)
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 14f
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
        }
        .zIndex(if (dragging) 1f else 0f)
}

private fun displayMaterialLabel(
    baseLabel: String,
    materialStyle: Int,
    softGlassSupported: Boolean,
): String = if (!softGlassSupported && materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
    "柔光玻璃(转高斯)"
} else {
    baseLabel
}

@Composable
private fun SoftGlassNoticeBanner(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = Color(0xFFFAAD14),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1FFAAD14))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun ConfigProfilesPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    profiles: List<ConfigProfile>,
    selectionState: ConfigProfileSelectionState,
    activeProfileId: String,
    activeSyncStatus: ConfigProfileStore.ProfileSyncStatus,
    softGlassSupported: Boolean,
    currentLayoutLabel: String,
    currentMaterialLabel: String,
    currentShortcutCount: Int,
    pendingImportPreview: ConfigProfileStore.ImportPreview?,
    onDismissImportPreview: () -> Unit,
    onCreateProfile: (String) -> Unit,
    onApplyProfile: (
        profile: ConfigProfile,
        applyParams: Boolean,
        applyShortcuts: Boolean,
        backupCurrentFirst: Boolean,
    ) -> Unit,
    onSyncProfile: (
        profile: ConfigProfile,
        syncParams: Boolean,
        syncShortcuts: Boolean,
        preserveSoftGlassMetadata: Boolean,
    ) -> Unit,
    onRequestPickImportFile: () -> Unit,
    onCommitImport: (
        preview: ConfigProfileStore.ImportPreview,
        singleNameOverride: String,
        includeShortcuts: Boolean,
        applyImmediately: Boolean,
    ) -> Unit,
    onRequestExport: (targetIds: List<String>?, includeShortcuts: Boolean, exportCurrentIfEmpty: Boolean) -> Unit,
    onMoveProfile: (String, Int) -> Unit,
    onPersistProfileOrder: (List<String>) -> Unit,
    onCopyProfiles: (Set<String>) -> Unit,
    onDeleteProfiles: (Set<String>) -> Unit,
    onRenameProfile: (id: String, newName: String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var applyTargetProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var syncTargetProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var actionSheetProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var renameSingleProfile by remember { mutableStateOf<ConfigProfile?>(null) }
    var deleteSingleIds by remember { mutableStateOf<Set<String>?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportTargetIds by remember { mutableStateOf<List<String>?>(null) }
    var exportSingleName by remember { mutableStateOf<String?>(null) }

    val interactionState = rememberConfigProfileInteractionState()
    val density = LocalDensity.current
    val view = LocalView.current
    val currentProfiles by rememberUpdatedState(profiles)
    val currentMoveProfile by rememberUpdatedState(onMoveProfile)
    val topInsetPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val bottomInsetPx = with(density) { contentPadding.calculateBottomPadding().toPx() }

    SideEffect {
        interactionState.anchorOriginWindowYProvider = {
            val profileIds = profiles.map { it.id }.toSet()
            val firstProfileInfo = lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { info ->
                    val itemKey = info.key as? String
                    itemKey != null && itemKey in profileIds
                }
            val firstProfileId = firstProfileInfo?.key as? String
            if (firstProfileInfo == null || firstProfileId == null) {
                null
            } else {
                interactionState.viewportTopWindowY + topInsetPx + firstProfileInfo.offset -
                    interactionState.getNodeTopY(firstProfileId, profiles)
            }
        }
    }

    // 拖拽靠近列表可见区顶部或底部边缘时自动平滑滚动
    LaunchedEffect(interactionState.draggingId, topInsetPx, bottomInsetPx) {
        val draggingId = interactionState.draggingId ?: return@LaunchedEffect
        val edgeThresholdPx = with(density) { 56.dp.toPx() }
        val maxStepPx = with(density) { 14.dp.toPx() }
        while (interactionState.draggingId == draggingId) {
            val viewTop = interactionState.viewportTopWindowY + topInsetPx
            val viewBottom = interactionState.viewportBottomWindowY - bottomInsetPx
            if (viewBottom > viewTop) {
                val dragTop = interactionState.mainVisualTopY
                val dragBottom = dragTop + interactionState.anchorRowHeight(draggingId)
                val scrollDelta = when {
                    dragTop < viewTop + edgeThresholdPx && lazyListState.canScrollBackward -> {
                        val ratio = ((viewTop + edgeThresholdPx - dragTop) / edgeThresholdPx).coerceIn(0.15f, 1f)
                        -maxStepPx * ratio
                    }
                    dragBottom > viewBottom - edgeThresholdPx && lazyListState.canScrollForward -> {
                        val ratio = ((dragBottom - (viewBottom - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.15f, 1f)
                        maxStepPx * ratio
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    val scrolled = lazyListState.scrollBy(scrollDelta)
                    if (scrolled != 0f) {
                        interactionState.onDragDelta(
                            dy = scrolled,
                            profiles = currentProfiles,
                            move = currentMoveProfile,
                            onHapticTick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        )
                    }
                }
            }
            delay(16L)
        }
    }

    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val top = coordinates.positionInWindow().y
                interactionState.viewportTopWindowY = top
                interactionState.viewportBottomWindowY = top + coordinates.size.height
            }
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 方案 A：顶部当前生效配置状态卡 ──
        item(key = "active_status_card") {
            ActiveProfileStatusCard(
                activeProfile = activeProfile,
                syncStatus = activeSyncStatus,
                softGlassSupported = softGlassSupported,
                currentLayoutLabel = currentLayoutLabel,
                currentMaterialLabel = currentMaterialLabel,
                currentShortcutCount = currentShortcutCount,
                onUpdateActive = { profile -> syncTargetProfile = profile },
                onSaveAsNew = { showCreateDialog = true },
                onRestoreActive = { profile -> applyTargetProfile = profile },
            )
        }

        item(key = "profile_actions") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "新建配置",
                    summary = "以当前参数和快捷项目创建一份新配置",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showCreateDialog = true },
                )
                ArrowPreference(
                    title = "导入配置",
                    summary = "从 JSON 文件导入并预览单份配置或配置包",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Import,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = onRequestPickImportFile,
                )
                ArrowPreference(
                    title = "导出配置",
                    summary = when {
                        profiles.isEmpty() -> "暂无已保存配置，将直接导出当前生效配置"
                        selectionState.selectedIds.isNotEmpty() -> "导出选中的 ${selectionState.selectedIds.size} 份配置"
                        else -> "导出全部 ${profiles.size} 份配置，可选择是否包含快捷项目"
                    },
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        exportTargetIds = if (selectionState.selectedIds.isNotEmpty()) {
                            selectionState.selectedIds.toList()
                        } else {
                            null
                        }
                        exportSingleName = null
                        showExportDialog = true
                    },
                )
            }
        }

        item(key = "profile_list_header") {
            SmallTitle(
                text = "配置列表 (${profiles.size})",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        if (profiles.isEmpty()) {
            item(key = "profile_empty") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                    ) {
                        Text(
                            text = "还没有已保存的配置方案",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "把当前参数与快捷项目保存成方案后，可以随时在多套风格之间一键切换，或整包导出迁移。",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(14.dp))
                        StatusActionChip(
                            label = "保存当前配置为第一份方案",
                            highlighted = true,
                            onClick = { showCreateDialog = true },
                        )
                    }
                }
            }
        }

        itemsIndexed(
            profiles,
            key = { _, profile -> profile.id },
        ) { _, profile ->
            val selected = profile.id in selectionState.selectedIds
            val isActive = profile.id == activeProfileId
            val isModifiedActive = isActive && activeSyncStatus.isModified()
            val primary = MiuixTheme.colorScheme.primary
            val dragging = interactionState.draggingId == profile.id
            val shortcutText = if (profile.hasShortcuts) "${profile.shortcutCount} 个快捷项" else "仅参数"
            val materialDesc = displayMaterialLabel(profile.materialLabel(), profile.materialStyle, softGlassSupported)
            val summaryText = "${profile.layoutLabel()} · $materialDesc · $shortcutText · ${formatProfileShortTime(profile.updatedAt)}"

            Box(
                modifier = Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        interactionState.rowHeights[profile.id] = coordinates.size.height.toFloat()
                        interactionState.rowWindowY[profile.id] = coordinates.positionInWindow().y
                    }
                    .configProfileDragGestures(
                        state = interactionState,
                        profileId = profile.id,
                        isMultiSelect = selectionState.multiSelect,
                        getProfiles = { currentProfiles },
                        move = onMoveProfile,
                        onLongPressNoMove = {
                            if (selectionState.multiSelect) {
                                selectionState.toggle(profile.id)
                            } else {
                                selectionState.enter(profile.id)
                            }
                        },
                        onDragSettled = {
                            onPersistProfileOrder(currentProfiles.map { it.id })
                        },
                    )
                    .configProfileDragVisuals(
                        state = interactionState,
                        profileId = profile.id,
                        profiles = profiles,
                    ),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (selected) {
                                val grow = 3.dp.toPx()
                                drawRoundRect(
                                    color = primary.copy(alpha = 0.18f),
                                    topLeft = Offset(-grow, -grow),
                                    size = Size(size.width + grow * 2, size.height + grow * 2),
                                    cornerRadius = CornerRadius(18.dp.toPx()),
                                )
                            }
                        },
                ) {
                    BasicComponent(
                        title = profile.name,
                        summary = summaryText,
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Theme,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        },
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    ProfileBadge(
                                        text = if (isModifiedActive) "已修改" else "当前使用",
                                        textColor = if (isModifiedActive) Color(0xFFFAAD14) else MiuixTheme.colorScheme.primary,
                                        bgColor = if (isModifiedActive) Color(0x1FFAAD14) else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    )
                                }
                                if (!selectionState.multiSelect) {
                                    IconButton(
                                        onClick = {
                                            if (!interactionState.clickSuppressed()) {
                                                actionSheetProfile = profile
                                            }
                                        },
                                        modifier = Modifier.padding(start = 2.dp),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.More,
                                            contentDescription = "更多操作",
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            if (!interactionState.clickSuppressed()) {
                                if (selectionState.multiSelect) {
                                    selectionState.toggle(profile.id)
                                } else {
                                    applyTargetProfile = profile
                                }
                            }
                        },
                    )
                }
            }
        }

        item(key = "profile_list_footer") {
            Text(
                text = "轻触卡片应用或还原配置，右侧菜单可查看快捷项详情或单独同步、导出与管理；长按不动进入多选，长按后拖动可调整顺序。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 18.sp,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 32.dp)
                    .fillMaxWidth(),
            )
        }
    }

    // ── 新建配置弹窗 ──
    if (showCreateDialog) {
        val suggestedName = remember(profiles.size, currentLayoutLabel, currentMaterialLabel) {
            suggestProfileName(currentLayoutLabel, currentMaterialLabel, profiles)
        }
        ProfileNameDialog(
            title = "新建配置",
            text = "以当前参数和快捷项目创建一份新配置。",
            initialName = suggestedName,
            existingNames = profiles.map { it.name }.toSet(),
            confirmText = "创建",
            onConfirm = { name ->
                showCreateDialog = false
                onCreateProfile(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // ── 单卡「更多操作」菜单弹窗 ──
    actionSheetProfile?.let { profile ->
        val latest = profiles.firstOrNull { it.id == profile.id } ?: profile
        val isActive = latest.id == activeProfileId
        val isModifiedActive = isActive && activeSyncStatus.isModified()
        ProfileActionDialog(
            profile = latest,
            isActive = isActive,
            isModifiedActive = isModifiedActive,
            softGlassSupported = softGlassSupported,
            onDismiss = { actionSheetProfile = null },
            onApply = {
                actionSheetProfile = null
                applyTargetProfile = latest
            },
            onSync = {
                actionSheetProfile = null
                syncTargetProfile = latest
            },
            onRename = {
                actionSheetProfile = null
                renameSingleProfile = latest
            },
            onDuplicate = {
                actionSheetProfile = null
                onCopyProfiles(setOf(latest.id))
            },
            onExport = {
                actionSheetProfile = null
                exportTargetIds = listOf(latest.id)
                exportSingleName = latest.name
                showExportDialog = true
            },
            onDelete = {
                actionSheetProfile = null
                deleteSingleIds = setOf(latest.id)
            },
        )
    }

    // ── 重命名弹窗（兼容单卡与多选） ──
    val renameTarget = renameSingleProfile
        ?: if (selectionState.showRenameRequest && selectionState.selectedIds.size == 1) {
            profiles.firstOrNull { it.id == selectionState.selectedIds.first() }
        } else {
            null
        }
    if (renameTarget != null) {
        val fromMultiSelect = renameSingleProfile == null
        val otherNames = profiles.filter { it.id != renameTarget.id }.map { it.name }.toSet()
        ProfileNameDialog(
            title = "重命名配置",
            text = "修改「${renameTarget.name}」在列表中的显示名称。",
            initialName = renameTarget.name,
            existingNames = otherNames,
            confirmText = "保存",
            onConfirm = { newName ->
                renameSingleProfile = null
                if (fromMultiSelect) selectionState.showRenameRequest = false
                onRenameProfile(renameTarget.id, newName)
            },
            onDismiss = {
                renameSingleProfile = null
                if (fromMultiSelect) selectionState.showRenameRequest = false
            },
        )
    }
    LaunchedEffect(selectionState.showRenameRequest, renameTarget) {
        if (selectionState.showRenameRequest && renameTarget == null) {
            selectionState.showRenameRequest = false
        }
    }

    // ── 应用 / 还原配置弹窗 ──
    applyTargetProfile?.let { profile ->
        val isActiveRestore = profile.id == activeProfileId && activeSyncStatus.isModified()
        val needsBackupSuggestion = !isActiveRestore && (activeProfileId.isEmpty() || activeSyncStatus.isModified())
        ApplyProfileDialog(
            profile = profile,
            isActiveRestore = isActiveRestore,
            needsBackupSuggestion = needsBackupSuggestion,
            softGlassSupported = softGlassSupported,
            onConfirm = { applyParams, applyShortcuts, backupCurrentFirst ->
                applyTargetProfile = null
                onApplyProfile(profile, applyParams, applyShortcuts, backupCurrentFirst)
            },
            onDismiss = { applyTargetProfile = null },
        )
    }

    // ── 同步 / 覆盖更新弹窗 ──
    syncTargetProfile?.let { profile ->
        ProfileSyncDialog(
            profile = profile,
            isActive = profile.id == activeProfileId,
            syncStatus = activeSyncStatus,
            softGlassSupported = softGlassSupported,
            onConfirm = { syncParams, syncShortcuts, preserveSoftGlass ->
                syncTargetProfile = null
                onSyncProfile(profile, syncParams, syncShortcuts, preserveSoftGlass)
            },
            onDismiss = { syncTargetProfile = null },
        )
    }

    // ── 导入预览确认弹窗（先选文件，解析预览后再提交） ──
    pendingImportPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            existingNames = profiles.map { it.name }.toSet(),
            softGlassSupported = softGlassSupported,
            onConfirm = { singleNameOverride, includeShortcuts, applyImmediately ->
                onCommitImport(preview, singleNameOverride, includeShortcuts, applyImmediately)
            },
            onDismiss = onDismissImportPreview,
        )
    }

    // ── 导出弹窗 ──
    if (showExportDialog) {
        var includeShortcuts by remember(exportTargetIds, profiles.size) { mutableStateOf(true) }
        val exportLiveCurrent = profiles.isEmpty() && exportTargetIds == null
        val dialogText = when {
            exportLiveCurrent -> "当前列表为空，将直接导出正在生效的面板配置。"
            exportTargetIds?.size == 1 -> "将导出配置「${exportSingleName ?: "所选配置"}」。"
            exportTargetIds != null -> "将导出选中的 ${exportTargetIds!!.size} 份配置。"
            else -> "将导出全部 ${profiles.size} 份配置。"
        }
        ConfigOptionDialog(
            title = "导出配置",
            text = dialogText,
            optionTitle = "包含快捷项目",
            optionSummary = "关闭后文件仅包含配置参数，不包含任何快捷项目。",
            includeShortcuts = includeShortcuts,
            onIncludeShortcutsChange = { includeShortcuts = it },
            confirmText = "选择位置",
            onConfirm = {
                showExportDialog = false
                onRequestExport(exportTargetIds, includeShortcuts, exportLiveCurrent)
            },
            onDismiss = { showExportDialog = false },
        )
    }

    // ── 删除确认弹窗（兼容单卡与多选） ──
    val pendingDeleteIds = deleteSingleIds
        ?: if (selectionState.showDeleteRequest && selectionState.selectedIds.isNotEmpty()) {
            selectionState.selectedIds.toSet()
        } else {
            null
        }
    if (pendingDeleteIds != null) {
        val fromMultiSelect = deleteSingleIds == null
        val includesActive = activeProfileId in pendingDeleteIds
        val targetSingleName = if (pendingDeleteIds.size == 1) {
            profiles.firstOrNull { it.id == pendingDeleteIds.first() }?.name
        } else {
            null
        }
        Dialog(
            onDismissRequest = {
                deleteSingleIds = null
                if (fromMultiSelect) selectionState.showDeleteRequest = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(vertical = 16.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = if (targetSingleName != null) "删除配置？" else "删除所选配置？",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    val baseText = if (targetSingleName != null) {
                        "将删除配置「$targetSingleName」，此操作不可撤销。"
                    } else {
                        "将删除 ${pendingDeleteIds.size} 份配置，此操作不可撤销。"
                    }
                    val fullText = if (includesActive) {
                        "$baseText\n当前正在生效的面板设置不会被清空，仅从配置列表中移除。"
                    } else {
                        baseText
                    }
                    Text(
                        text = fullText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                deleteSingleIds = null
                                if (fromMultiSelect) selectionState.showDeleteRequest = false
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            text = "删除",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                deleteSingleIds = null
                                if (fromMultiSelect) selectionState.showDeleteRequest = false
                                onDeleteProfiles(pendingDeleteIds)
                            },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(selectionState.showDeleteRequest, pendingDeleteIds) {
        if (selectionState.showDeleteRequest && pendingDeleteIds == null) {
            selectionState.showDeleteRequest = false
        }
    }
}

@Composable
private fun ActiveProfileStatusCard(
    activeProfile: ConfigProfile?,
    syncStatus: ConfigProfileStore.ProfileSyncStatus,
    softGlassSupported: Boolean,
    currentLayoutLabel: String,
    currentMaterialLabel: String,
    currentShortcutCount: Int,
    onUpdateActive: (ConfigProfile) -> Unit,
    onSaveAsNew: () -> Unit,
    onRestoreActive: (ConfigProfile) -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (activeProfile != null) {
                val modified = syncStatus.isModified()
                val softGlassDowngraded =
                    !softGlassSupported && activeProfile.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS
                val materialLabel = displayMaterialLabel(
                    activeProfile.materialLabel(),
                    activeProfile.materialStyle,
                    softGlassSupported,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前生效：${activeProfile.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        val statusDetail = when {
                            syncStatus.paramsModified && syncStatus.shortcutsModified ->
                                "参数与快捷项目均已修改，尚未更新到此配置"
                            syncStatus.paramsModified ->
                                "面板参数已修改，尚未更新到此配置"
                            syncStatus.shortcutsModified ->
                                "快捷项目已修改，尚未更新到此配置"
                            else ->
                                "${activeProfile.layoutLabel()} · $materialLabel · 已与保存方案同步"
                        }
                        Text(
                            text = statusDetail,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (softGlassDowngraded) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "当前设备不支持柔光玻璃，已自动转为高斯模糊并补偿模糊半径（原配置档案已保留）",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color(0xFFFAAD14),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    ProfileBadge(
                        text = if (modified) "已修改" else "已同步",
                        textColor = if (modified) Color(0xFFFAAD14) else MiuixTheme.colorScheme.primary,
                        bgColor = if (modified) Color(0x1FFAAD14) else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                }
                if (modified) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusActionChip(
                            label = "更新覆盖",
                            highlighted = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onUpdateActive(activeProfile) },
                        )
                        StatusActionChip(
                            label = "另存为新",
                            highlighted = false,
                            modifier = Modifier.weight(1f),
                            onClick = onSaveAsNew,
                        )
                        StatusActionChip(
                            label = "还原",
                            highlighted = false,
                            modifier = Modifier.weight(1f),
                            onClick = { onRestoreActive(activeProfile) },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前生效：未归档配置",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$currentLayoutLabel · $currentMaterialLabel · $currentShortcutCount 个快捷项 · 尚未保存为方案",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    StatusActionChip(
                        label = "保存方案",
                        highlighted = true,
                        onClick = onSaveAsNew,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBadge(
    text: String,
    textColor: Color,
    bgColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

@Composable
private fun StatusActionChip(
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bgColor = if (highlighted) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }
    val textColor = if (highlighted) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun ConfigProfileMultiSelectBottomBar(
    floating: Boolean,
    profiles: List<ConfigProfile>,
    selectionState: ConfigProfileSelectionState,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val hasSelection = selectionState.selectedIds.isNotEmpty()
    val renameEnabled = selectionState.selectedIds.size == 1
    if (floating) {
        FloatingNavigationBar(showDivider = true) {
            FloatingNavigationBarItem(
                selected = false,
                onClick = { selectionState.toggleAll(profiles) },
                icon = MiuixIcons.SelectAll,
                label = if (selectionState.isAllSelected(profiles)) "取消全选" else "全选",
            )
            FloatingNavigationBarItem(
                selected = false,
                enabled = renameEnabled,
                onClick = onRename,
                icon = MiuixIcons.Edit,
                label = "重命名",
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
                onClick = { selectionState.clear() },
                icon = MiuixIcons.Close,
                label = "取消",
            )
        }
    } else {
        NavigationBar(showDivider = true) {
            NavigationBarItem(
                selected = false,
                onClick = { selectionState.toggleAll(profiles) },
                icon = MiuixIcons.SelectAll,
                label = if (selectionState.isAllSelected(profiles)) "取消全选" else "全选",
            )
            NavigationBarItem(
                selected = false,
                enabled = renameEnabled,
                onClick = onRename,
                icon = MiuixIcons.Edit,
                label = "重命名",
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
                onClick = { selectionState.clear() },
                icon = MiuixIcons.Close,
                label = "取消",
            )
        }
    }
}

@Composable
private fun ProfileActionDialog(
    profile: ConfigProfile,
    isActive: Boolean,
    isModifiedActive: Boolean,
    softGlassSupported: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onSync: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var showShortcuts by remember(profile.id) { mutableStateOf(false) }
    val shortcutItems = remember(profile.id, profile.updatedAt, showShortcuts) {
        if (showShortcuts && profile.hasShortcuts) profile.parseShortcuts() else emptyList()
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
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            ProfileBadge(
                                text = if (isModifiedActive) "已修改" else "当前使用",
                                textColor = if (isModifiedActive) Color(0xFFFAAD14) else MiuixTheme.colorScheme.primary,
                                bgColor = if (isModifiedActive) Color(0x1FFAAD14) else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val shortcutMeta = if (profile.hasShortcuts) "${profile.shortcutCount} 个快捷项目" else "仅包含参数（不替换快捷项目）"
                    val materialMeta = displayMaterialLabel(profile.materialLabel(), profile.materialStyle, softGlassSupported)
                    Text(
                        text = "${profile.layoutLabel()} · $materialMeta · ${profile.keyCount} 项参数 · $shortcutMeta",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "创建于 ${formatProfileFullTime(profile.createdAt)} · 更新于 ${formatProfileFullTime(profile.updatedAt)}",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (!softGlassSupported && profile.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
                        Spacer(Modifier.height(10.dp))
                        SoftGlassNoticeBanner(
                            "此配置原为柔光玻璃材质，当前设备不支持柔光玻璃；应用时将保留原档案并在本机转为高斯模糊+模糊半径补偿。",
                        )
                    }
                }

                if (profile.hasShortcuts && profile.shortcutCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShortcuts = !showShortcuts }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "快捷项目清单 (${profile.shortcutCount})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (showShortcuts) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (showShortcuts) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            shortcutItems.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.title.ifBlank { "未命名项目" },
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (item.enabled) "启用" else "停用",
                                        fontSize = 11.sp,
                                        color = if (item.enabled) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))

                ProfileMenuRow(
                    icon = if (isModifiedActive) MiuixIcons.Undo else MiuixIcons.Ok,
                    title = if (isModifiedActive) "还原为此配置" else "应用此配置",
                    summary = if (isModifiedActive) "放弃未保存的改动，恢复到此方案状态" else "将此配置的参数或快捷项目设为当前生效",
                    onClick = onApply,
                )
                ProfileMenuRow(
                    icon = MiuixIcons.Update,
                    title = "用当前配置覆盖更新",
                    summary = "选择将当前参数和/或快捷项目写入此配置",
                    onClick = onSync,
                )
                ProfileMenuRow(
                    icon = MiuixIcons.Edit,
                    title = "重命名",
                    summary = "修改此配置在列表中的名称",
                    onClick = onRename,
                )
                ProfileMenuRow(
                    icon = MiuixIcons.Copy,
                    title = "创建副本",
                    summary = "在当前配置下方复制一份独立方案",
                    onClick = onDuplicate,
                )
                ProfileMenuRow(
                    icon = MiuixIcons.Share,
                    title = "导出此配置",
                    summary = "单独将此配置保存为 JSON 文件",
                    onClick = onExport,
                )
                ProfileMenuRow(
                    icon = MiuixIcons.Delete,
                    title = "删除配置",
                    summary = "从配置列表中移除此方案",
                    tint = Color(0xFFE94634),
                    onClick = onDelete,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "关闭", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    summary: String,
    tint: Color = MiuixTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (tint == Color(0xFFE94634)) tint else MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun ApplyProfileDialog(
    profile: ConfigProfile,
    isActiveRestore: Boolean,
    needsBackupSuggestion: Boolean,
    softGlassSupported: Boolean,
    onConfirm: (applyParams: Boolean, applyShortcuts: Boolean, backupCurrentFirst: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var applyParams by remember(profile.id) { mutableStateOf(true) }
    var applyShortcuts by remember(profile.id) { mutableStateOf(profile.hasShortcuts) }
    var backupCurrentFirst by remember(profile.id) { mutableStateOf(false) }

    val finalApplyShortcuts = profile.hasShortcuts && applyShortcuts
    val softGlassDowngraded =
        !softGlassSupported && profile.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS
    val materialDesc = displayMaterialLabel(
        profile.materialLabel(),
        profile.materialStyle,
        softGlassSupported,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = if (isActiveRestore) "还原已保存配置" else "应用配置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isActiveRestore) {
                        "将当前面板恢复为「${profile.name}」上次保存时的状态，未保存的改动将被覆盖。"
                    } else {
                        "将「${profile.name}」应用到当前面板（${profile.layoutLabel()} · $materialDesc）。"
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))

                if (profile.hasShortcuts) {
                    BasicComponent(
                        title = "应用配置参数",
                        summary = "应用 ${profile.keyCount} 项外观、布局、动效与触感参数；关闭后仅替换快捷项目，不改变当前参数与激活方案。",
                        endActions = {
                            Switch(
                                checked = applyParams,
                                onCheckedChange = { applyParams = it },
                            )
                        },
                    )
                    BasicComponent(
                        title = "应用快捷项目",
                        summary = "用此配置中的 ${profile.shortcutCount} 个快捷项目替换当前列表；关闭后保留当前快捷项目。",
                        endActions = {
                            Switch(
                                checked = applyShortcuts,
                                onCheckedChange = { applyShortcuts = it },
                            )
                        },
                    )
                } else {
                    Text(
                        text = "此配置仅包含 ${profile.keyCount} 项参数（不含快捷项目），应用时将保留当前快捷项目列表。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(12.dp),
                    )
                }

                if (softGlassDowngraded && applyParams) {
                    Spacer(Modifier.height(10.dp))
                    SoftGlassNoticeBanner(
                        "此配置为柔光玻璃材质，当前设备不支持；应用参数时将自动转为高斯模糊并按磨砂浓度补偿模糊半径（原配置档案保持不变）。",
                    )
                }

                if (needsBackupSuggestion && (applyParams || finalApplyShortcuts)) {
                    Spacer(Modifier.height(10.dp))
                    BasicComponent(
                        title = "切换前备份当前配置",
                        summary = "当前生效设置尚未归档或有未保存改动，开启后先另存为新备份再应用。",
                        endActions = {
                            Switch(
                                checked = backupCurrentFirst,
                                onCheckedChange = { backupCurrentFirst = it },
                            )
                        },
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    val confirmEnabled = applyParams || finalApplyShortcuts
                    val confirmLabel = when {
                        isActiveRestore && applyParams && finalApplyShortcuts -> "还原"
                        isActiveRestore -> "部分还原"
                        else -> "应用"
                    }
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = confirmLabel,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = confirmEnabled,
                        onClick = { onConfirm(applyParams, finalApplyShortcuts, backupCurrentFirst) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSyncDialog(
    profile: ConfigProfile,
    isActive: Boolean,
    syncStatus: ConfigProfileStore.ProfileSyncStatus,
    softGlassSupported: Boolean,
    onConfirm: (syncParams: Boolean, syncShortcuts: Boolean, preserveSoftGlassMetadata: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var syncParams by remember(profile.id) {
        mutableStateOf(
            if (isActive && syncStatus.isModified()) {
                syncStatus.paramsModified || !syncStatus.shortcutsModified
            } else {
                true
            }
        )
    }
    var syncShortcuts by remember(profile.id) {
        mutableStateOf(
            if (isActive && syncStatus.isModified()) {
                syncStatus.shortcutsModified || !syncStatus.paramsModified
            } else {
                true
            }
        )
    }
    var preserveSoftGlass by remember(profile.id) { mutableStateOf(true) }
    val showPreserveSoftGlass =
        !softGlassSupported && profile.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS && syncParams

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "更新配置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "选择要覆盖写入「${profile.name}」的当前设置内容：",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))
                BasicComponent(
                    title = "同步配置参数",
                    summary = "用当前调整好的外观、布局、动效与触感参数覆盖此配置。",
                    endActions = {
                        Switch(
                            checked = syncParams,
                            onCheckedChange = { syncParams = it },
                        )
                    },
                )
                if (showPreserveSoftGlass) {
                    BasicComponent(
                        title = "保留原柔光玻璃材质标记",
                        summary = "仅同步其余参数改动，保留原配置中的柔光玻璃材质与光学参数；关闭后将改写为高斯模糊。",
                        endActions = {
                            Switch(
                                checked = preserveSoftGlass,
                                onCheckedChange = { preserveSoftGlass = it },
                            )
                        },
                    )
                }
                BasicComponent(
                    title = "同步快捷项目",
                    summary = "用当前的快捷项目列表与顺序覆盖此配置。",
                    endActions = {
                        Switch(
                            checked = syncShortcuts,
                            onCheckedChange = { syncShortcuts = it },
                        )
                    },
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = "更新覆盖",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = syncParams || syncShortcuts,
                        onClick = { onConfirm(syncParams, syncShortcuts, showPreserveSoftGlass && preserveSoftGlass) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: ConfigProfileStore.ImportPreview,
    existingNames: Set<String>,
    softGlassSupported: Boolean,
    onConfirm: (singleNameOverride: String, includeShortcuts: Boolean, applyImmediately: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstCandidate = preview.candidates.firstOrNull()
    var singleName by remember(preview) { mutableStateOf(firstCandidate?.name.orEmpty()) }
    val anyHasShortcuts = preview.anyHasShortcuts()
    var includeShortcuts by remember(preview) { mutableStateOf(anyHasShortcuts) }
    var applyImmediately by remember(preview) { mutableStateOf(false) }
    val hasUnsupportedSoftGlass =
        !softGlassSupported && preview.candidates.any { it.materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS }

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
                    text = if (preview.isBundle) "确认导入配置包" else "确认导入配置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))

                if (!preview.isBundle && firstCandidate != null) {
                    val shortcutDesc = if (firstCandidate.hasShortcuts) {
                        "${firstCandidate.shortcutCount} 个快捷项目"
                    } else {
                        "仅参数"
                    }
                    val verInfo = if (preview.sourceVersionOrTime.isNotBlank()) {
                        "来源 v${preview.sourceVersionOrTime} · "
                    } else {
                        ""
                    }
                    val firstMaterial = displayMaterialLabel(
                        firstCandidate.materialLabel(),
                        firstCandidate.materialStyle,
                        softGlassSupported,
                    )
                    Text(
                        text = "${verInfo}${firstCandidate.layoutLabel()} · $firstMaterial · ${firstCandidate.keyCount} 项参数 · $shortcutDesc",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        value = singleName,
                        onValueChange = { singleName = it.take(ConfigProfileStore.MAX_NAME_LENGTH) },
                        label = "配置名称",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (singleName.isNotBlank()) {
                                    onConfirm(singleName.trim(), includeShortcuts, applyImmediately)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (singleName.trim() in existingNames) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "列表中已有同名配置，导入时将自动附加序号以区分",
                            fontSize = 12.sp,
                            color = Color(0xFFFAAD14),
                        )
                    }
                } else {
                    val bundleSummary = buildString {
                        append("共检测到 ${preview.candidates.size} 份有效配置")
                        if (preview.skippedCorruptCount > 0) {
                            append("，已跳过 ${preview.skippedCorruptCount} 份损坏配置")
                        }
                        if (preview.sourceVersionOrTime.isNotBlank()) {
                            append("\n导出时间：${preview.sourceVersionOrTime}")
                        }
                    }
                    Text(
                        text = bundleSummary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        preview.candidates.forEach { item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.hasNameConflict) {
                                        Text(
                                            text = "自动加序号",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFAAD14),
                                        )
                                    }
                                }
                                Text(
                                    text = "${item.layoutLabel()} · ${displayMaterialLabel(item.materialLabel(), item.materialStyle, softGlassSupported)} · ${if (item.hasShortcuts) "${item.shortcutCount} 个快捷项" else "仅参数"}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }

                if (hasUnsupportedSoftGlass) {
                    Spacer(Modifier.height(12.dp))
                    SoftGlassNoticeBanner(
                        if (preview.isBundle) {
                            "部分导入配置为柔光玻璃材质，当前设备不支持；导入将完整保留原参数，应用时会自动转为高斯模糊并补偿模糊半径。"
                        } else {
                            "检测到此配置为柔光玻璃材质，当前设备不支持；导入仍会完整保留原方案参数，应用时将自动转为高斯模糊并补偿模糊半径。"
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                if (anyHasShortcuts) {
                    BasicComponent(
                        title = "包含快捷项目",
                        summary = "关闭后忽略文件中的快捷项目，仅导入配置参数。",
                        endActions = {
                            Switch(
                                checked = includeShortcuts,
                                onCheckedChange = { includeShortcuts = it },
                            )
                        },
                    )
                } else {
                    Text(
                        text = "此文件仅包含配置参数，不包含任何快捷项目。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                if (!preview.isBundle) {
                    BasicComponent(
                        title = "导入后立即应用",
                        summary = "保存到列表的同时，立刻启用此配置。",
                        endActions = {
                            Switch(
                                checked = applyImmediately,
                                onCheckedChange = { applyImmediately = it },
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
                    val confirmEnabled = preview.isBundle || singleName.isNotBlank()
                    TextButton(
                        text = "确认导入",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = confirmEnabled,
                        onClick = {
                            onConfirm(singleName.trim(), includeShortcuts, applyImmediately && !preview.isBundle)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    text: String,
    confirmText: String,
    initialName: String = "",
    existingNames: Set<String> = emptySet(),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName.take(ConfigProfileStore.MAX_NAME_LENGTH)) }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() && trimmed in existingNames

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(18.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it.take(ConfigProfileStore.MAX_NAME_LENGTH) },
                    label = "配置名称（最多 ${ConfigProfileStore.MAX_NAME_LENGTH} 字）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) onConfirm(name.trim())
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDuplicate) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "列表中已有同名配置，建议区分命名",
                        fontSize = 12.sp,
                        color = Color(0xFFFAAD14),
                    )
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = confirmText,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = name.isNotBlank(),
                        onClick = { onConfirm(name.trim()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigOptionDialog(
    title: String,
    text: String,
    optionTitle: String,
    optionSummary: String,
    includeShortcuts: Boolean,
    onIncludeShortcutsChange: (Boolean) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))
                BasicComponent(
                    title = optionTitle,
                    summary = optionSummary,
                    endActions = {
                        Switch(
                            checked = includeShortcuts,
                            onCheckedChange = onIncludeShortcutsChange,
                        )
                    },
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = confirmText,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

private fun suggestProfileName(
    layoutLabel: String,
    materialLabel: String,
    profiles: List<ConfigProfile>,
): String {
    val existing = profiles.map { it.name }.toSet()
    val base = "$layoutLabel · $materialLabel".take(ConfigProfileStore.MAX_NAME_LENGTH)
    if (base !in existing) return base
    var index = 2
    while (true) {
        val suffix = " $index"
        val maxBase = (ConfigProfileStore.MAX_NAME_LENGTH - suffix.length).coerceAtLeast(1)
        val candidate = base.take(maxBase).trim() + suffix
        if (candidate !in existing) return candidate
        index++
    }
}

private fun formatProfileShortTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatProfileFullTime(timestamp: Long): String {
    if (timestamp <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
