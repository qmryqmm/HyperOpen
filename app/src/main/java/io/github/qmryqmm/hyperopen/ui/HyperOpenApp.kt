package io.github.qmryqmm.hyperopen.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.qmryqmm.hyperopen.config.ConfigBackup
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.layout.LayoutCapacity
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.safety.SafetyStore
import io.github.qmryqmm.hyperopen.ui.components.BackNavigationIcon
import io.github.qmryqmm.hyperopen.ui.edit.PresetActionPickerDialog
import io.github.qmryqmm.hyperopen.ui.edit.ShortcutEditPage
import io.github.qmryqmm.hyperopen.ui.pages.ParamsPage
import io.github.qmryqmm.hyperopen.ui.pages.PermissionCheckPage
import io.github.qmryqmm.hyperopen.ui.pages.SettingsPage
import io.github.qmryqmm.hyperopen.ui.pages.ShortcutsManagementPage
import io.github.qmryqmm.hyperopen.ui.pages.ThemeSettingsPage
import io.github.qmryqmm.hyperopen.util.LiveWallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

/**
 * HyperOpen 设置界面顶层组合容器。
 * 负责主界面整体状态调度、底栏导航（参数、快捷项目、设置）、子页面导航栈路由、
 * 备份导入导出启动器以及全局对话框。
 */

@Composable
fun HyperOpenApp(
    activity: MainActivity? = null,
    initialConfig: HyperOpenConfig,
) {
    val ctx = LocalContext.current

    // 配置镜像状态
    var materialStyle by remember {
        mutableStateOf(
            if (initialConfig.materialStyle == HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR) {
                HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR
            } else {
                HyperOpenConfig.MATERIAL_SOFT_GLASS
            }
        )
    }
    var offsetY by remember { mutableFloatStateOf(initialConfig.offsetY.toFloat()) }
    var regionDiameter by remember { mutableFloatStateOf(initialConfig.regionDiameter.toFloat()) }
    var showRegionBg by remember { mutableStateOf(initialConfig.showRegionBg) }
    var showHeaderPill by remember { mutableStateOf(initialConfig.showHeaderPill) }
    var showHeaderPillBg by remember { mutableStateOf(initialConfig.showHeaderPillBg) }
    var headerTopMargin by remember { mutableFloatStateOf(initialConfig.headerTopMargin.toFloat()) }
    var headerTitleSize by remember { mutableFloatStateOf(initialConfig.headerTitleSize.toFloat()) }
    var headerSubtitleSize by remember { mutableFloatStateOf(initialConfig.headerSubtitleSize.toFloat()) }
    var headerBlurRadius by remember { mutableFloatStateOf(initialConfig.headerBlurRadius.toFloat()) }
    var headerTextCentered by remember { mutableStateOf(initialConfig.headerTextCentered) }
    var iconSize by remember { mutableFloatStateOf(initialConfig.iconSize.toFloat()) }
    var maxScale by remember { mutableFloatStateOf(initialConfig.maxScale) }
    var influenceRadius by remember { mutableFloatStateOf(initialConfig.influenceRadius.toFloat()) }
    var iconAlpha by remember { mutableFloatStateOf(initialConfig.iconAlpha) }
    var fisheyeZoomAlpha by remember { mutableFloatStateOf(initialConfig.fisheyeZoomAlpha) }
    var fisheyeOtherAlpha by remember { mutableFloatStateOf(initialConfig.fisheyeOtherAlpha) }
    var iconCornerRadius by remember { mutableFloatStateOf(initialConfig.iconCornerRadius.toFloat()) }
    var fullscreenBlurMaskAlpha by remember { mutableFloatStateOf(initialConfig.fullscreenBlurMaskAlpha) }
    var fullscreenDimAlpha by remember { mutableFloatStateOf(initialConfig.fullscreenDimAlpha) }
    var headerDimAlpha by remember { mutableFloatStateOf(initialConfig.headerDimAlpha) }
    var regionDimAlpha by remember { mutableFloatStateOf(initialConfig.regionDimAlpha) }
    var bubbleDimAlpha by remember { mutableFloatStateOf(initialConfig.bubbleDimAlpha) }
    var showDelayMs by remember { mutableFloatStateOf(initialConfig.showDelayMs.toFloat()) }
    var faceUnlockQuickOpen by remember { mutableStateOf(initialConfig.faceUnlockQuickOpen) }
    var hapticEnabled by remember { mutableStateOf(initialConfig.hapticEnabled) }
    var hapticStrength by remember { mutableFloatStateOf(initialConfig.hapticStrength.toFloat()) }
    var hapticDurationMs by remember { mutableFloatStateOf(initialConfig.hapticDurationMs.toFloat()) }
    var hapticMode by remember { mutableIntStateOf(initialConfig.hapticMode) }
    var hapticSlideEnabled by remember { mutableStateOf(initialConfig.hapticSlideEnabled) }
    var hapticSlideStrength by remember { mutableFloatStateOf(initialConfig.hapticSlideStrength.toFloat()) }
    var hapticSlideDurationMs by remember { mutableFloatStateOf(initialConfig.hapticSlideDurationMs.toFloat()) }
    var hapticSlideMode by remember { mutableIntStateOf(initialConfig.hapticSlideMode) }
    var hapticStockWaveExpand by remember { mutableIntStateOf(initialConfig.hapticStockWaveExpand) }
    var hapticStockWaveSlide by remember { mutableIntStateOf(initialConfig.hapticStockWaveSlide) }
    var hapticBrakePulse by remember { mutableStateOf(initialConfig.hapticBrakePulse) }
    var hapticDoubleGapMs by remember { mutableFloatStateOf(initialConfig.hapticDoubleGapMs.toFloat()) }
    var hapticSlideBrakePulse by remember { mutableStateOf(initialConfig.hapticSlideBrakePulse) }
    var hapticSlideDoubleGapMs by remember { mutableFloatStateOf(initialConfig.hapticSlideDoubleGapMs.toFloat()) }
    var regionBlurRadius by remember { mutableFloatStateOf(initialConfig.regionBlurRadius.toFloat()) }
    var iconSpacingOffset by remember { mutableFloatStateOf(initialConfig.iconSpacingOffset.toFloat()) }
    var bubblePaddingPercent by remember { mutableFloatStateOf(initialConfig.bubblePaddingPercent.toFloat()) }
    var bubbleBlurRadius by remember { mutableFloatStateOf(initialConfig.bubbleBlurRadius.toFloat()) }
    var selectedIconScale by remember { mutableFloatStateOf(initialConfig.selectedIconScale) }
    var glyphPadEnabled by remember { mutableStateOf(initialConfig.glyphPadEnabled) }
    var glyphPadColor by remember { mutableIntStateOf(initialConfig.glyphPadColor) }
    var glyphPadMode by remember { mutableIntStateOf(initialConfig.glyphPadMode) }
    var iconGlowEffect by remember { mutableStateOf(initialConfig.iconGlowEffect) }
    var themeMode by remember { mutableStateOf(initialConfig.themeMode) }
    var monetColors by remember { mutableStateOf(initialConfig.monetColors) }
    var barBlur by remember { mutableStateOf(initialConfig.barBlur) }
    var floatingNavBar by remember { mutableStateOf(initialConfig.floatingNavBar) }
    var predictiveBack by remember { mutableStateOf(initialConfig.predictiveBack) }
    var maxIconCount by remember { mutableStateOf(initialConfig.maxIconCount.toFloat()) }
    var layoutStyle by remember { mutableStateOf(initialConfig.layoutStyle) }
    var stockSystemAnim by remember { mutableStateOf(initialConfig.stockSystemAnim) }
    var stockSystemRegion by remember { mutableStateOf(initialConfig.stockSystemRegion) }
    var ringCount by remember { mutableStateOf(initialConfig.ringCount.toFloat()) }
    var ringSectorMode by remember { mutableStateOf(initialConfig.ringSectorMode) }
    var sectorLineColor by remember { mutableIntStateOf(initialConfig.sectorLineColor) }
    var sectorLineAlpha by remember { mutableStateOf(initialConfig.sectorLineAlpha) }
    var sectorDashLength by remember { mutableStateOf(initialConfig.sectorDashLength.toFloat()) }
    var sectorDashGap by remember { mutableStateOf(initialConfig.sectorDashGap.toFloat()) }
    var sectorLineWidth by remember { mutableStateOf(initialConfig.sectorLineWidth) }
    val shortcuts = remember { mutableStateListOf<ShortcutItem>().apply { addAll(initialConfig.shortcuts) } }
    var safetyTick by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(0) }
    var showResetDefaultsDialog by remember { mutableStateOf(false) }
    var showResetShortcutsDialog by remember { mutableStateOf(false) }
    // 配置导入：先读文件并预检，通过后弹确认框再落盘
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportSummary by remember { mutableStateOf<ConfigBackup.Result?>(null) }

    // ── 配置导入 / 导出：SAF 选文件，读写与解析都在 IO 线程 ──
    val configScope = rememberCoroutineScope()

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            configScope.launch(Dispatchers.IO) {
                val msg = runCatching {
                    val json = ConfigBackup.buildExportJson(ctx)
                    ConfigBackup.writeTo(ctx, uri, json)
                    // prefs 只存「改过的项」，全默认时文件里只有快捷方式，这里如实告知
                    val preview = ConfigBackup.preview(json)
                    if (preview.keyCount == 0) {
                        "已导出快捷方式（当前参数均为默认值，无自定义项）"
                    } else {
                        "配置已导出：${preview.keyCount} 项参数、${preview.shortcutCount} 个快捷方式"
                    }
                }.getOrElse { "导出失败：${it.message ?: it.javaClass.simpleName}" }
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            configScope.launch(Dispatchers.IO) {
                val pair = runCatching {
                    val text = ConfigBackup.readFrom(ctx, uri)
                    ConfigBackup.preview(text) to text
                }.getOrElse {
                    ConfigBackup.Result.fail("读取失败：${it.message ?: it.javaClass.simpleName}") to null
                }
                withContext(Dispatchers.Main) {
                    val preview = pair.first
                    val text = pair.second
                    if (preview.ok && text != null) {
                        pendingImportText = text
                        pendingImportSummary = preview
                        showImportConfirmDialog = true
                    } else {
                        Toast.makeText(ctx, preview.message ?: "读取失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ── 页面滚动位置记忆：状态提升到 HyperOpenApp 顶层，跨底部导航切换与二级页进出持续存活，
    //    返回页面时停在上次滚动位置而不是回到顶部 ──
    // 参数页按 Tab 分成 4 页，每页各自记住滚动位置，切 Tab 不会把别页的进度带过去
    val paramsListStates = listOf(
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
    )
    var paramsTab by remember { mutableIntStateOf(0) }
    val shortcutsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val themeListState = rememberLazyListState()
    val permissionListState = rememberLazyListState()

    // 导航路由：0=主界面, 1=主题设置页, 2=快捷方式编辑页, 3=整体预览页, 4=权限检测页
    var subPage by remember { mutableStateOf(0) }
    var editingItem by remember { mutableStateOf<ShortcutItem?>(null) }
    var isNewShortcut by remember { mutableStateOf(false) }
    var requestEditBackTrigger by remember { mutableIntStateOf(0) }
    var showNewShortcutPresetDialog by remember { mutableStateOf(false) }

    // ── 布局容量联动（方案 A）：几何参数决定「图标数量」滑块上限，超额值就地钳制使滑块位置随容量移动 ──
    // 容量探针与排布引擎同源（同一 dpToPx 管道的 px 值），弧形扇面无几何上限恒为 15
    val density = ctx.resources.displayMetrics.density

    fun computeLayoutCapacity(): Int = LayoutCapacity.maxFeasibleCount(
        layoutStyle,
        (regionDiameter / 2f) * density,
        iconSize * density,
        iconSpacingOffset * density,
        ringCount.toInt().coerceIn(1, 4),
    )

    val iconCapacity = computeLayoutCapacity()
    val enabledShortcutsCount = shortcuts.count { it.enabled }

    /** 钳制「图标数量」到当前容量内（0=全部显示永不钳制）；调用方负责 persist() */
    fun clampIconCountToCapacity() {
        val cap = computeLayoutCapacity()
        if (maxIconCount > cap) maxIconCount = cap.toFloat()
    }

    // 排版预览页：视图引用（返回确认弹窗需调用 commitLayoutChanges/discardLayoutChanges）与「是否保存」弹窗开关
    var previewViewRef by remember { mutableStateOf<HyperOpenPreviewView?>(null) }
    var showLayoutSaveDialog by remember { mutableStateOf(false) }

    /** 排版预览页的统一返回出口：存在未保存的排版修改时弹「是否保存」，否则直接退出 */
    fun requestPreviewExit() {
        val pv = previewViewRef
        if (pv != null && pv.hasUnsavedLayoutChanges()) {
            showLayoutSaveDialog = true
        } else {
            pv?.discardLayoutChanges()
            subPage = 0
        }
    }

    // 预览界面进入全屏沉浸模式，与真实锁屏 GxzwWindowFrameLayout 1:1 物理屏幕坐标对齐
    DisposableEffect(activity, subPage) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (subPage == 3) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun persist() {
        val c = HyperOpenConfig.loadFromApp(ctx)
        c.materialStyle = materialStyle
        c.offsetY = offsetY.toInt()
        c.regionDiameter = regionDiameter.toInt()
        c.showRegionBg = showRegionBg
        c.showHeaderPill = showHeaderPill
        c.showHeaderPillBg = showHeaderPillBg
        c.headerTopMargin = headerTopMargin.toInt()
        c.headerTitleSize = headerTitleSize.toInt()
        c.headerSubtitleSize = headerSubtitleSize.toInt()
        c.headerBlurRadius = headerBlurRadius.toInt()
        c.headerTextCentered = headerTextCentered
        c.iconSize = iconSize.toInt()
        c.maxIconCount = maxIconCount.toInt()
        c.layoutStyle = layoutStyle
        c.stockSystemAnim = stockSystemAnim
        c.stockSystemRegion = stockSystemRegion
        c.ringCount = ringCount.toInt().coerceIn(1, 4)
        c.ringSectorMode = ringSectorMode
        c.sectorLineColor = sectorLineColor
        c.sectorLineAlpha = sectorLineAlpha
        c.sectorDashLength = sectorDashLength.toInt()
        c.sectorDashGap = sectorDashGap.toInt()
        c.sectorLineWidth = sectorLineWidth
        c.maxScale = maxScale
        c.influenceRadius = influenceRadius.toInt()
        c.iconAlpha = iconAlpha
        c.fisheyeZoomAlpha = fisheyeZoomAlpha
        c.fisheyeOtherAlpha = fisheyeOtherAlpha
        c.iconCornerRadius = iconCornerRadius.toInt().coerceIn(0, 50)
        c.fullscreenBlurMaskAlpha = fullscreenBlurMaskAlpha
        c.fullscreenDimAlpha = fullscreenDimAlpha
        c.headerDimAlpha = headerDimAlpha
        c.regionDimAlpha = regionDimAlpha
        c.bubbleDimAlpha = bubbleDimAlpha
        c.showDelayMs = showDelayMs.toInt()
        c.faceUnlockQuickOpen = faceUnlockQuickOpen
        c.hapticEnabled = hapticEnabled
        c.hapticStrength = hapticStrength.toInt()
        c.hapticDurationMs = hapticDurationMs.toInt()
        c.hapticMode = hapticMode
        c.hapticStockWaveExpand = hapticStockWaveExpand
        c.hapticBrakePulse = hapticBrakePulse
        c.hapticDoubleGapMs = hapticDoubleGapMs.toInt()
        c.hapticSlideEnabled = hapticSlideEnabled
        c.hapticSlideStrength = hapticSlideStrength.toInt()
        c.hapticSlideDurationMs = hapticSlideDurationMs.toInt()
        c.hapticSlideMode = hapticSlideMode
        c.hapticStockWaveSlide = hapticStockWaveSlide
        c.hapticSlideBrakePulse = hapticSlideBrakePulse
        c.hapticSlideDoubleGapMs = hapticSlideDoubleGapMs.toInt()
        c.regionBlurRadius = regionBlurRadius.toInt()
        c.iconSpacingOffset = iconSpacingOffset.toInt()
        c.bubblePaddingPercent = bubblePaddingPercent.toInt()
        c.bubbleBlurRadius = bubbleBlurRadius.toInt()
        c.selectedIconScale = selectedIconScale
        c.glyphPadEnabled = glyphPadEnabled
        c.glyphPadColor = glyphPadColor
        c.glyphPadMode = glyphPadMode
        c.iconGlowEffect = iconGlowEffect
        c.themeMode = themeMode
        c.monetColors = monetColors
        c.barBlur = barBlur
        c.floatingNavBar = floatingNavBar
        c.predictiveBack = predictiveBack
        c.shortcuts = ArrayList(shortcuts)
        c.save(ctx)
    }

    // 进入时校准一次：旧配置的「图标数量」可能超过当前几何容量（仅超出时钳制+持久化，0=全部显示不动）
    LaunchedEffect(Unit) {
        val cap = computeLayoutCapacity()
        if (maxIconCount > cap) {
            maxIconCount = cap.toFloat()
            persist()
        }
    }

    /**
     * 恢复全部参数为出厂默认值（视觉 / 物理 / 震动 / 等待时间 / 快捷方式列表）。
     * 以 HyperOpenConfig 字段初始值 + 默认快捷项为准，统一重置所有可编辑状态并持久化。
     */
    fun resetDefaults() {
        val defaults = HyperOpenConfig()
        materialStyle = defaults.materialStyle
        offsetY = defaults.offsetY.toFloat()
        regionDiameter = defaults.regionDiameter.toFloat()
        showRegionBg = defaults.showRegionBg
        showHeaderPill = defaults.showHeaderPill
        showHeaderPillBg = defaults.showHeaderPillBg
        headerTopMargin = defaults.headerTopMargin.toFloat()
        headerTitleSize = defaults.headerTitleSize.toFloat()
        headerSubtitleSize = defaults.headerSubtitleSize.toFloat()
        headerBlurRadius = defaults.headerBlurRadius.toFloat()
        headerTextCentered = defaults.headerTextCentered
        iconSize = defaults.iconSize.toFloat()
        maxIconCount = defaults.maxIconCount.toFloat()
        layoutStyle = defaults.layoutStyle
        stockSystemAnim = defaults.stockSystemAnim
        stockSystemRegion = defaults.stockSystemRegion
        ringCount = defaults.ringCount.toFloat()
        ringSectorMode = defaults.ringSectorMode
        sectorLineColor = defaults.sectorLineColor
        sectorLineAlpha = defaults.sectorLineAlpha
        sectorDashLength = defaults.sectorDashLength.toFloat()
        sectorDashGap = defaults.sectorDashGap.toFloat()
        sectorLineWidth = defaults.sectorLineWidth
        maxScale = defaults.maxScale
        influenceRadius = defaults.influenceRadius.toFloat()
        iconAlpha = defaults.iconAlpha
        fisheyeZoomAlpha = defaults.fisheyeZoomAlpha
        fisheyeOtherAlpha = defaults.fisheyeOtherAlpha
        iconCornerRadius = defaults.iconCornerRadius.toFloat()
        fullscreenBlurMaskAlpha = defaults.fullscreenBlurMaskAlpha
        fullscreenDimAlpha = defaults.fullscreenDimAlpha
        headerDimAlpha = defaults.headerDimAlpha
        regionDimAlpha = defaults.regionDimAlpha
        bubbleDimAlpha = defaults.bubbleDimAlpha
        showDelayMs = defaults.showDelayMs.toFloat()
        faceUnlockQuickOpen = defaults.faceUnlockQuickOpen
        hapticEnabled = defaults.hapticEnabled
        hapticStrength = defaults.hapticStrength.toFloat()
        hapticDurationMs = defaults.hapticDurationMs.toFloat()
        hapticMode = defaults.hapticMode
        hapticStockWaveExpand = defaults.hapticStockWaveExpand
        hapticBrakePulse = defaults.hapticBrakePulse
        hapticDoubleGapMs = defaults.hapticDoubleGapMs.toFloat()
        hapticSlideEnabled = defaults.hapticSlideEnabled
        hapticSlideStrength = defaults.hapticSlideStrength.toFloat()
        hapticSlideDurationMs = defaults.hapticSlideDurationMs.toFloat()
        hapticSlideMode = defaults.hapticSlideMode
        hapticStockWaveSlide = defaults.hapticStockWaveSlide
        hapticSlideBrakePulse = defaults.hapticSlideBrakePulse
        hapticSlideDoubleGapMs = defaults.hapticSlideDoubleGapMs.toFloat()
        regionBlurRadius = defaults.regionBlurRadius.toFloat()
        iconSpacingOffset = defaults.iconSpacingOffset.toFloat()
        bubblePaddingPercent = defaults.bubblePaddingPercent.toFloat()
        bubbleBlurRadius = defaults.bubbleBlurRadius.toFloat()
        selectedIconScale = defaults.selectedIconScale
        glyphPadEnabled = defaults.glyphPadEnabled
        glyphPadColor = defaults.glyphPadColor
        glyphPadMode = defaults.glyphPadMode
        iconGlowEffect = defaults.iconGlowEffect
        themeMode = defaults.themeMode
        monetColors = defaults.monetColors
        barBlur = defaults.barBlur
        floatingNavBar = defaults.floatingNavBar
        predictiveBack = defaults.predictiveBack
        // 恢复默认参数：保留现有快捷项目列表不变，仅重置外观、几何、物理、震动与等待时间等其他参数
        persist()
    }

    // 拦截系统返回键与手势返回：在二级页时优先处理
    LaunchedEffect(activity, subPage) {
        activity?.backPressConsumer = {
            when (subPage) {
                2 -> {
                    requestEditBackTrigger++
                    true
                }
                3 -> {
                    // 排版预览页：有未保存修改时弹「是否保存」，否则直接退出
                    requestPreviewExit()
                    true
                }
                else -> if (subPage != 0) {
                    subPage = 0
                    editingItem = null
                    true
                } else {
                    false
                }
            }
        }
    }
    BackHandler(enabled = subPage != 0) {
        when (subPage) {
            2 -> requestEditBackTrigger++
            3 -> requestPreviewExit()
            else -> {
                subPage = 0
                editingItem = null
            }
        }
    }

    HyperOpenTheme(
        themeMode = themeMode,
        monet = monetColors,
    ) {
        val topAppBarScrollBehavior = MiuixScrollBehavior()
        val title = when (subPage) {
            1 -> "主题设置"
            2 -> if (isNewShortcut) "新建快捷方式" else "快捷方式参数设置"
            3 -> "指纹快捷整体预览"
            4 -> "权限检测"
            else -> when (page) {
                0 -> "参数配置"
                1 -> "快捷项目"
                else -> "软件设置"
            }
        }

        val navItems = remember {
            listOf(
                NavigationItem("参数配置", MiuixIcons.Tune),
                NavigationItem("快捷项目", MiuixIcons.ListView),
                NavigationItem("软件设置", MiuixIcons.Settings),
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    if (subPage != 3) {
                        TopAppBar(
                            title = title,
                            scrollBehavior = topAppBarScrollBehavior,
                            navigationIcon = {
                                if (subPage != 0) {
                                    BackNavigationIcon(onClick = {
                                        if (subPage == 2) {
                                            requestEditBackTrigger++
                                        } else {
                                            subPage = 0
                                            editingItem = null
                                        }
                                    })
                                }
                            },
                        )
                    }
                    // 参数页分页 Tab：跟着顶栏一起折叠上移，所以放在 topBar 里而不是内容区。
                    // 不用 Miuix 的 TabRow —— 它的 backgroundColor 是「每个 Tab 药丸」的颜色
                    // （带 selected 参数），整条容器底色不可控，会留下一条与标题栏不同色的白带。
                    // 这里自绘分段控件，视觉与应用其它白色卡片 + 主色强调保持一致。
                    if (subPage == 0 && page == 0) {
                        ParamsTabBar(
                            selectedIndex = paramsTab,
                            onSelected = { paramsTab = it },
                        )
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = subPage == 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AppBottomBar(
                        items = navItems,
                        selectedPage = page,
                        floating = floatingNavBar,
                        onPageSelect = { page = it },
                    )
                }
            },
        ) { innerPadding ->
            val pagePadding = if (subPage == 3) PaddingValues(0.dp) else innerPadding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                when (subPage) {
                    1 -> {
                        ThemeSettingsPage(
                            contentPadding = pagePadding,
                            lazyListState = themeListState,
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it; persist() },
                            monet = monetColors,
                            onMonet = { monetColors = it; persist() },
                            floatingNavBar = floatingNavBar,
                            onFloatingNavBar = { floatingNavBar = it; persist() },
                        )
                    }
                    2 -> {
                        editingItem?.let { currentEdit ->
                            ShortcutEditPage(
                                contentPadding = pagePadding,
                                initialItem = currentEdit,
                                isNew = isNewShortcut,
                                backTrigger = requestEditBackTrigger,
                                onSave = { updated, exitAfter ->
                                    if (isNewShortcut) {
                                        shortcuts.add(updated)
                                        Toast.makeText(ctx, "已添加快捷方式: ${updated.title}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val idx = shortcuts.indexOfFirst { it.id == updated.id }
                                        if (idx >= 0) {
                                            shortcuts[idx] = updated.copy()
                                            Toast.makeText(ctx, "已保存参数设置", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    persist()
                                    if (exitAfter) {
                                        subPage = 0
                                        editingItem = null
                                    }
                                    // exitAfter = false（「保存修改」）：留在编辑界面，editingItem 保持不变
                                },
                                onDelete = { toDelete ->
                                    shortcuts.removeAll { it.id == toDelete.id }
                                    persist()
                                    Toast.makeText(ctx, "已删除快捷方式", Toast.LENGTH_SHORT).show()
                                    subPage = 0
                                    editingItem = null
                                },
                                onExitWithoutSave = {
                                    subPage = 0
                                    editingItem = null
                                },
                            )
                        }
                    }
                    3 -> {
                        // 指纹快捷方式整体预览与长按自由排版界面
                        val currentConfig = HyperOpenConfig.loadFromApp(ctx).apply {
                            this.materialStyle = materialStyle
                            this.offsetY = offsetY.toInt()
                            this.regionDiameter = regionDiameter.toInt()
                            this.showRegionBg = showRegionBg
                            this.showHeaderPill = showHeaderPill
                            this.showHeaderPillBg = showHeaderPillBg
                            this.headerTopMargin = headerTopMargin.toInt()
                            this.headerTitleSize = headerTitleSize.toInt()
                            this.headerSubtitleSize = headerSubtitleSize.toInt()
                            this.headerBlurRadius = headerBlurRadius.toInt()
                            this.headerTextCentered = headerTextCentered
                            this.iconSize = iconSize.toInt()
                            this.maxIconCount = maxIconCount.toInt()
                            this.layoutStyle = layoutStyle
                            this.ringCount = ringCount.toInt().coerceIn(1, 4)
                            this.ringSectorMode = ringSectorMode
                            this.sectorLineColor = sectorLineColor
                            this.sectorLineAlpha = sectorLineAlpha
                            this.sectorDashLength = sectorDashLength.toInt()
                            this.sectorDashGap = sectorDashGap.toInt()
                            this.sectorLineWidth = sectorLineWidth
                            this.maxScale = maxScale
                            this.influenceRadius = influenceRadius.toInt()
                            this.layoutStyle = layoutStyle
                            this.stockSystemAnim = stockSystemAnim
                            this.stockSystemRegion = stockSystemRegion
                            this.ringCount = ringCount.toInt()
                            this.ringSectorMode = ringSectorMode
                            this.sectorLineColor = sectorLineColor
                            this.sectorLineAlpha = sectorLineAlpha
                            this.sectorDashLength = sectorDashLength.toInt()
                            this.sectorDashGap = sectorDashGap.toInt()
                            this.sectorLineWidth = sectorLineWidth
                            this.iconAlpha = iconAlpha
                            this.fisheyeZoomAlpha = fisheyeZoomAlpha
                            this.fisheyeOtherAlpha = fisheyeOtherAlpha
                            this.iconCornerRadius = iconCornerRadius.toInt().coerceIn(0, 50)
                            this.fullscreenBlurMaskAlpha = fullscreenBlurMaskAlpha
                            this.fullscreenDimAlpha = fullscreenDimAlpha
                            this.headerDimAlpha = headerDimAlpha
                            this.regionDimAlpha = regionDimAlpha
                            this.bubbleDimAlpha = bubbleDimAlpha
                            this.showDelayMs = showDelayMs.toInt()
                            this.faceUnlockQuickOpen = faceUnlockQuickOpen
                            this.hapticEnabled = hapticEnabled
                            this.hapticStrength = hapticStrength.toInt()
                            this.hapticDurationMs = hapticDurationMs.toInt()
                            this.hapticMode = hapticMode
                            this.regionBlurRadius = regionBlurRadius.toInt()
                            this.iconSpacingOffset = iconSpacingOffset.toInt()
                            this.bubblePaddingPercent = bubblePaddingPercent.toInt()
                            this.bubbleBlurRadius = bubbleBlurRadius.toInt()
                            this.selectedIconScale = selectedIconScale
                            this.iconGlowEffect = iconGlowEffect
                            this.shortcuts = ArrayList(shortcuts)
                        }

                        var blurScene by remember { mutableStateOf(PreviewBlurSceneState()) }

                        val previewBackdrop = rememberLayerBackdrop {
                            drawRect(Color(0xFF0C0E14))
                            drawContent()
                        }

                        val bubbleBackdrop = rememberLayerBackdrop {
                            drawRect(Color(0xFF0C0E14))
                            drawContent()
                        }

                        fun unifiedBlurColors(alphaMultiplier: Float = 1f) = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    color = if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS)
                                        Color.White.copy(alpha = (0x16 / 255f) * alphaMultiplier.coerceIn(0f, 1f))
                                    else
                                        Color.White.copy(alpha = (0x12 / 255f) * alphaMultiplier.coerceIn(0f, 1f)),
                                    mode = BlurBlendMode.SrcOver
                                )
                            ),
                            saturation = if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) 1.75f else 1.20f
                        )

                        val constantUnifiedBlurColors = remember(materialStyle) { unifiedBlurColors(1f) }

                        // 彻底实现系统桌面壁纸完全自动、实时动态拉取与变更监听
                        val liveWallpaperBitmap by io.github.qmryqmm.hyperopen.util.LiveWallpaperLoader.rememberLiveWallpaper(ctx)
                        val density = androidx.compose.ui.platform.LocalDensity.current

                        Box(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 1~3. 底层壁纸 + 全屏毛玻璃遮罩 + 圆形视界区域底衬（共同捕获入 bubbleBackdrop 供上层气泡与胶囊采样，与真实锁屏 SurfaceFlinger 多层叠加完全一致）
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdrop(bubbleBackdrop)
                            ) {
                                // 1. 底层真实桌面壁纸层
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(previewBackdrop)
                                ) {
                                    val currentWallpaper = liveWallpaperBitmap
                                    if (currentWallpaper != null) {
                                        Image(
                                            bitmap = currentWallpaper,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRect(Color(0xFF0D1117))
                                            drawCircle(
                                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                    colors = listOf(Color(0xFF2A82E4).copy(alpha = 0.45f), Color.Transparent),
                                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.38f),
                                                    radius = size.width * 0.65f
                                                )
                                            )
                                            drawCircle(
                                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                    colors = listOf(Color(0xFF7928CA).copy(alpha = 0.40f), Color.Transparent),
                                                    center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.68f),
                                                    radius = size.width * 0.70f
                                                )
                                            )
                                        }
                                    }
                                }

                                // 2. 全屏毛玻璃遮罩层与独立黑色压暗层 (与全屏遮罩/压暗滑块实时联动，与真实锁屏 GxzwWindowFrameLayout 1:1 对齐)
                                if (currentConfig.fullscreenBlurMaskAlpha > 0.001f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .textureBlur(
                                                backdrop = previewBackdrop,
                                                shape = RectangleShape,
                                                blurRadius = (currentConfig.fullscreenBlurMaskAlpha * 40f).coerceAtLeast(1f),
                                                colors = BlurColors(
                                                    blendColors = listOf(
                                                        BlendColorEntry(
                                                            color = Color.Black.copy(alpha = currentConfig.fullscreenDimAlpha.coerceIn(0f, 1f)),
                                                            mode = BlurBlendMode.SrcOver
                                                        )
                                                    )
                                                )
                                            )
                                    )
                                } else if (currentConfig.fullscreenDimAlpha > 0.001f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = currentConfig.fullscreenDimAlpha.coerceIn(0f, 1f)))
                                    )
                                }

                                // 3. 圆形视界区域的统一毛玻璃底衬 (与真实锁屏 CircularRegionBackdropView 及气泡底衬完全一致)
                                if (currentConfig.showRegionBg && currentConfig.regionBlurRadius > 0) {
                                    val screenH = ctx.resources.displayMetrics.heightPixels
                                    val screenW = ctx.resources.displayMetrics.widthPixels
                                    val fallbackCenterX = screenW / 2.0f
                                    val fallbackCenterY = screenH * 0.795f - with(density) { currentConfig.offsetY.dp.toPx() }
                                    val fallbackDiameterPx = with(density) { currentConfig.regionDiameter.dp.toPx() }

                                    val centerX = if (blurScene.regionDiameterPx > 0f) blurScene.regionCenterX else fallbackCenterX
                                    val centerY = if (blurScene.regionDiameterPx > 0f) blurScene.regionCenterY else fallbackCenterY
                                    val diameterPx = if (blurScene.regionDiameterPx > 0f) blurScene.regionDiameterPx else fallbackDiameterPx
                                    val radiusPx = diameterPx / 2f
                                    val diameterDp = with(density) { diameterPx.toDp() }

                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = with(density) { (centerX - radiusPx).toDp() },
                                                y = with(density) { (centerY - radiusPx).toDp() }
                                            )
                                            .size(diameterDp)
                                            .textureBlur(
                                                backdrop = previewBackdrop,
                                                shape = CircleShape,
                                                blurRadius = currentConfig.regionBlurRadius.toFloat(),
                                                colors = constantUnifiedBlurColors
                                            )
                                    )
                                }
                            }

                            // 4. 顶部提示胶囊与左上角返回按钮的毛玻璃底衬
                            if (currentConfig.showHeaderPill && currentConfig.showHeaderPillBg && currentConfig.headerBlurRadius > 0 && blurScene.headerWidthPx > 0f && blurScene.headerHeightPx > 0f) {
                                // 与真实锁屏 createCompositeHeaderPillDrawable 的 layerInset(2px) 对齐：模糊层四周内缩 2px
                                val headerInsetPx = 2f
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { (blurScene.headerLeftPx + headerInsetPx).toDp() },
                                            y = with(density) { (blurScene.headerTopPx + headerInsetPx).toDp() }
                                        )
                                        .size(
                                            width = with(density) { (blurScene.headerWidthPx - headerInsetPx * 2f).coerceAtLeast(1f).toDp() },
                                            height = with(density) { (blurScene.headerHeightPx - headerInsetPx * 2f).coerceAtLeast(1f).toDp() }
                                        )
                                        .textureBlur(
                                            backdrop = bubbleBackdrop,
                                            shape = RoundedCornerShape(percent = 50),
                                            blurRadius = currentConfig.headerBlurRadius.toFloat(),
                                            colors = constantUnifiedBlurColors
                                        )
                                )
                            }

                            if (currentConfig.regionBlurRadius > 0 && blurScene.backBtnSizePx > 0f) {
                                val backSizeDp = with(density) { blurScene.backBtnSizePx.toDp() }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { blurScene.backBtnLeftPx.toDp() },
                                            y = with(density) { blurScene.backBtnTopPx.toDp() }
                                        )
                                        .size(backSizeDp)
                                        .textureBlur(
                                            backdrop = bubbleBackdrop,
                                            shape = CircleShape,
                                            blurRadius = currentConfig.regionBlurRadius.toFloat(),
                                            colors = constantUnifiedBlurColors
                                        )
                                )
                            }

                            // 5. 图标气泡留白区域的独立毛玻璃底衬 (固定 key 复用 GPU 模糊纹理，不随手指滑动重建 Shader，彻底消除轻微闪烁；且底色恒定与真实锁屏 1:1 一致)
                            if (currentConfig.bubbleBlurRadius > 0) {
                                val cornerPercent = currentConfig.iconCornerRadius.coerceIn(0, 50)
                                val bubbleBlurShape = if (cornerPercent >= 50) CircleShape else RoundedCornerShape(percent = cornerPercent)
                                blurScene.bubbles.forEach { spot ->
                                    androidx.compose.runtime.key(spot.id) {
                                        if (spot.diameterPx > 2f && spot.alpha > 0.01f) {
                                            val bubbleRadiusPx = spot.diameterPx / 2f
                                            val bubbleDiameterDp = with(density) { spot.diameterPx.toDp() }
                                            Box(
                                                modifier = Modifier
                                                    .offset(
                                                        x = with(density) { (spot.centerX - bubbleRadiusPx).toDp() },
                                                        y = with(density) { (spot.centerY - bubbleRadiusPx).toDp() }
                                                    )
                                                    .size(bubbleDiameterDp)
                                                    .graphicsLayer { alpha = spot.alpha.coerceIn(0f, 1f) }
                                                    .textureBlur(
                                                        backdrop = bubbleBackdrop,
                                                        shape = bubbleBlurShape,
                                                        blurRadius = currentConfig.bubbleBlurRadius.toFloat(),
                                                        colors = constantUnifiedBlurColors
                                                    )
                                            )
                                        }
                                    }
                                }
                            }

                            // 6. 交互式预览与拖拽排版控件
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { viewCtx ->
                                    HyperOpenPreviewView(
                                        context = viewCtx,
                                        config = currentConfig,
                                        onBackClick = {
                                            // 统一返回出口：有未保存排版修改时弹「是否保存」确认框
                                            requestPreviewExit()
                                        },
                                        onOrderChanged = { newOrder ->
                                            // 保持启用/未启用项与整体列表同步重排
                                            val reordered = ArrayList<ShortcutItem>()
                                            reordered.addAll(newOrder)
                                            for (orig in shortcuts) {
                                                if (reordered.none { it.id == orig.id }) {
                                                    reordered.add(orig)
                                                }
                                            }
                                            shortcuts.clear()
                                            shortcuts.addAll(reordered)
                                            persist()
                                            Toast.makeText(viewCtx, "已更新并保存快捷方式排版", Toast.LENGTH_SHORT).show()
                                        },
                                        onBlurSceneChanged = { scene ->
                                            blurScene = scene
                                        },
                                    ).also { previewViewRef = it }
                                },
                            )
                        }
                    }
                    4 -> {
                        PermissionCheckPage(
                            contentPadding = pagePadding,
                            lazyListState = permissionListState,
                        )
                    }
                    else -> {
                        when (page) {
                            0 -> {
                                ParamsPage(
                                    contentPadding = innerPadding,
                                    lazyListState = paramsListStates[paramsTab.coerceIn(0, paramsListStates.lastIndex)],
                                    selectedTab = paramsTab,
                                    materialStyle = materialStyle,
                                    onMaterialStyle = { materialStyle = it; persist() },
                                    offsetY = offsetY,
                                    onOffsetY = { offsetY = it; persist() },
                                    regionDiameter = regionDiameter,
                                    onRegionDiameter = { regionDiameter = it; clampIconCountToCapacity(); persist() },
                                    showRegionBg = showRegionBg,
                                    onShowRegionBg = { showRegionBg = it; persist() },
                                    showHeaderPill = showHeaderPill,
                                    onShowHeaderPill = { showHeaderPill = it; persist() },
                                    showHeaderPillBg = showHeaderPillBg,
                                    onShowHeaderPillBg = { showHeaderPillBg = it; persist() },
                                    headerTopMargin = headerTopMargin,
                                    onHeaderTopMargin = { headerTopMargin = it; persist() },
                                    headerTitleSize = headerTitleSize,
                                    onHeaderTitleSize = { headerTitleSize = it; persist() },
                                    headerSubtitleSize = headerSubtitleSize,
                                    onHeaderSubtitleSize = { headerSubtitleSize = it; persist() },
                                    headerBlurRadius = headerBlurRadius,
                                    onHeaderBlurRadius = { headerBlurRadius = it; persist() },
                                    headerTextCentered = headerTextCentered,
                                    onHeaderTextCentered = { headerTextCentered = it; persist() },
                                    iconSize = iconSize,
                                    onIconSize = { iconSize = it; clampIconCountToCapacity(); persist() },
                                    maxIconCount = maxIconCount,
                                    onMaxIconCount = { maxIconCount = it; persist() },
                                    iconCapacity = iconCapacity,
                                    enabledShortcutsCount = enabledShortcutsCount,
                                    layoutStyle = layoutStyle,
                                    onLayoutStyle = { layoutStyle = it; clampIconCountToCapacity(); persist() },
                                    stockSystemAnim = stockSystemAnim,
                                    onStockSystemAnim = { stockSystemAnim = it; persist() },
                                    stockSystemRegion = stockSystemRegion,
                                    onStockSystemRegion = { stockSystemRegion = it; persist() },
                                    ringCount = ringCount,
                                    onRingCount = { ringCount = it; clampIconCountToCapacity(); persist() },
                                    ringSectorMode = ringSectorMode,
                                    onRingSectorMode = { ringSectorMode = it; persist() },
                                    sectorLineColor = sectorLineColor,
                                    onSectorLineColor = { sectorLineColor = it; persist() },
                                    sectorLineAlpha = sectorLineAlpha,
                                    onSectorLineAlpha = { sectorLineAlpha = it; persist() },
                                    sectorDashLength = sectorDashLength,
                                    onSectorDashLength = { sectorDashLength = it; persist() },
                                    sectorDashGap = sectorDashGap,
                                    onSectorDashGap = { sectorDashGap = it; persist() },
                                    sectorLineWidth = sectorLineWidth,
                                    onSectorLineWidth = { sectorLineWidth = it; persist() },
                                    maxScale = maxScale,
                                    onMaxScale = { maxScale = it; persist() },
                                    influenceRadius = influenceRadius,
                                    onInfluenceRadius = { influenceRadius = it; persist() },
                                    iconAlpha = iconAlpha,
                                    onIconAlpha = { iconAlpha = it; persist() },
                                    fisheyeZoomAlpha = fisheyeZoomAlpha,
                                    onFisheyeZoomAlpha = { fisheyeZoomAlpha = it; persist() },
                                    fisheyeOtherAlpha = fisheyeOtherAlpha,
                                    onFisheyeOtherAlpha = { fisheyeOtherAlpha = it; persist() },
                                    iconCornerRadius = iconCornerRadius,
                                    onIconCornerRadius = { iconCornerRadius = it; persist() },
                                    fullscreenBlurMaskAlpha = fullscreenBlurMaskAlpha,
                                    onFullscreenBlurMaskAlpha = { fullscreenBlurMaskAlpha = it; persist() },
                                    fullscreenDimAlpha = fullscreenDimAlpha,
                                    onFullscreenDimAlpha = { fullscreenDimAlpha = it; persist() },
                                    headerDimAlpha = headerDimAlpha,
                                    onHeaderDimAlpha = { headerDimAlpha = it; persist() },
                                    regionDimAlpha = regionDimAlpha,
                                    onRegionDimAlpha = { regionDimAlpha = it; persist() },
                                    bubbleDimAlpha = bubbleDimAlpha,
                                    onBubbleDimAlpha = { bubbleDimAlpha = it; persist() },
                                    showDelayMs = showDelayMs,
                                    onShowDelayMs = { showDelayMs = it; persist() },
                                    faceUnlockQuickOpen = faceUnlockQuickOpen,
                                    onFaceUnlockQuickOpen = { faceUnlockQuickOpen = it; persist() },
                                    hapticEnabled = hapticEnabled,
                                    onHapticEnabled = { hapticEnabled = it; persist() },
                                    hapticStrength = hapticStrength,
                                    onHapticStrength = { hapticStrength = it; persist() },
                                    hapticDurationMs = hapticDurationMs,
                                    onHapticDurationMs = { hapticDurationMs = it; persist() },
                                    hapticMode = hapticMode,
                                    onHapticMode = { hapticMode = it; persist() },
                                    hapticSlideEnabled = hapticSlideEnabled,
                                    onHapticSlideEnabled = { hapticSlideEnabled = it; persist() },
                                    hapticSlideStrength = hapticSlideStrength,
                                    onHapticSlideStrength = { hapticSlideStrength = it; persist() },
                                    hapticSlideDurationMs = hapticSlideDurationMs,
                                    onHapticSlideDurationMs = { hapticSlideDurationMs = it; persist() },
                                    hapticSlideMode = hapticSlideMode,
                                    onHapticSlideMode = { hapticSlideMode = it; persist() },
                                    hapticStockWaveExpand = hapticStockWaveExpand,
                                    onHapticStockWaveExpand = { hapticStockWaveExpand = it; persist() },
                                    hapticStockWaveSlide = hapticStockWaveSlide,
                                    onHapticStockWaveSlide = { hapticStockWaveSlide = it; persist() },
                                    hapticBrakePulse = hapticBrakePulse,
                                    onHapticBrakePulse = { hapticBrakePulse = it; persist() },
                                    hapticDoubleGapMs = hapticDoubleGapMs,
                                    onHapticDoubleGapMs = { hapticDoubleGapMs = it; persist() },
                                    hapticSlideBrakePulse = hapticSlideBrakePulse,
                                    onHapticSlideBrakePulse = { hapticSlideBrakePulse = it; persist() },
                                    hapticSlideDoubleGapMs = hapticSlideDoubleGapMs,
                                    onHapticSlideDoubleGapMs = { hapticSlideDoubleGapMs = it; persist() },
                                    regionBlurRadius = regionBlurRadius,
                                    onRegionBlurRadius = { regionBlurRadius = it; persist() },
                                    iconSpacingOffset = iconSpacingOffset,
                                    onIconSpacingOffset = { iconSpacingOffset = it; clampIconCountToCapacity(); persist() },
                                    bubblePaddingPercent = bubblePaddingPercent,
                                    onBubblePaddingPercent = { bubblePaddingPercent = it; persist() },
                                    bubbleBlurRadius = bubbleBlurRadius,
                                    onBubbleBlurRadius = { bubbleBlurRadius = it; persist() },
                                    selectedIconScale = selectedIconScale,
                                    onSelectedIconScale = { selectedIconScale = it; persist() },
                                    iconGlowEffect = iconGlowEffect,
                                    onIconGlowEffect = { iconGlowEffect = it; persist() },
                                )
                            }
                            1 -> {
                                ShortcutsManagementPage(
                                    contentPadding = innerPadding,
                                    lazyListState = shortcutsListState,
                                    shortcuts = shortcuts,
                                    onOpenPreview = {
                                        subPage = 3
                                    },
                                    onShortcutToggle = { item, enabled ->
                                        val i = shortcuts.indexOfFirst { it.id == item.id }
                                        if (i >= 0) {
                                            val updated = item.copy().apply { this.enabled = enabled }
                                            shortcuts[i] = updated
                                        }
                                        persist()
                                    },
                                    onShortcutClick = { item ->
                                        requestEditBackTrigger = 0
                                        editingItem = item.copy()
                                        isNewShortcut = false
                                        subPage = 2
                                    },
                                    onAddShortcut = {
                                        showNewShortcutPresetDialog = true
                                    },
                                )
                            }
                            else -> {
                                SettingsPage(
                                    contentPadding = innerPadding,
                                    lazyListState = settingsListState,
                                    safetyTick = safetyTick,
                                    onSafetyRefresh = { safetyTick++ },
                                    onOpenThemePage = { subPage = 1 },
                                    onOpenPermissionPage = { subPage = 4 },
                                    onResetDefaults = { showResetDefaultsDialog = true },
                                    onResetShortcuts = { showResetShortcutsDialog = true },
                                    onExportConfig = {
                                        exportConfigLauncher.launch(ConfigBackup.suggestedFileName(ctx))
                                    },
                                    onImportConfig = {
                                        // 部分文件管理器把 .json 报成 text/plain 或 octet-stream，一并放行
                                        importConfigLauncher.launch(
                                            arrayOf(
                                                "application/json",
                                                "text/plain",
                                                "application/octet-stream",
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNewShortcutPresetDialog) {
            PresetActionPickerDialog(
                onDismiss = { showNewShortcutPresetDialog = false },
                onSelect = { preset ->
                    showNewShortcutPresetDialog = false
                    requestEditBackTrigger = 0
                    editingItem = ShortcutItem(
                        UUID.randomUUID().toString(),
                        preset.title,
                        preset.subtitle,
                        preset.actionType,
                        preset.actionPayload,
                        preset.iconPackage,
                        preset.iconGlyph,
                        preset.tintColor,
                        true,
                        preset.glyphPadEnabled,
                        preset.glyphPadColor,
                        preset.glyphPadMode,
                    ).apply {
                        userId = preset.userId
                        shellUseRoot = preset.shellUseRoot
                        if (this.actionType == ShortcutItem.ACTION_INTENT_URI) {
                            // 预设 payload 同步写回用户标记，旧版 SystemUI 代码也能识别
                            this.actionPayload = ShortcutItem.syncUserMarker(this.actionPayload, preset.userId)
                        }
                    }
                    isNewShortcut = true
                    subPage = 2
                },
            )
        }

        // ── 恢复默认参数二次确认弹窗（必须在 HyperOpenTheme 内，才能跟随深浅色） ──
        if (showResetDefaultsDialog) {
            Dialog(
                onDismissRequest = { showResetDefaultsDialog = false },
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
                        Text(
                            text = "恢复默认参数？",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "将重置全部外观、几何、物理、震动与等待时间等参数为出厂默认值，但保留当前配置的快捷项目。确定继续吗？",
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
                                onClick = { showResetDefaultsDialog = false },
                            )
                            Spacer(Modifier.width(10.dp))
                            TextButton(
                                text = "恢复默认",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    showResetDefaultsDialog = false
                                    resetDefaults()
                                    Toast.makeText(ctx, "已恢复默认参数", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 恢复预设布局二次确认弹窗 ──
        if (showResetShortcutsDialog) {
            Dialog(
                onDismissRequest = { showResetShortcutsDialog = false },
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
                        Text(
                            text = "恢复预设布局？",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "将重置快捷项目列表为系统初始预设，您自定义添加或修改的快捷方式将被替换。其他外观与物理参数保持不变。此操作不可撤销，确定继续吗？",
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
                                onClick = { showResetShortcutsDialog = false },
                            )
                            Spacer(Modifier.width(10.dp))
                            TextButton(
                                text = "恢复预设",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    showResetShortcutsDialog = false
                                    shortcuts.clear()
                                    shortcuts.addAll(HyperOpenConfig.getDefaultShortcuts())
                                    persist()
                                    Toast.makeText(ctx, "已恢复预设布局", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 排版预览返回确认弹窗：返回键/返回按钮退出排版界面时询问是否保存排版修改 ──
        if (showLayoutSaveDialog) {
            Dialog(
                onDismissRequest = { showLayoutSaveDialog = false },
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
                        Text(
                            text = "保存排版修改？",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "当前图标的排版（位置与顺序）尚未保存，退出前是否保存？",
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
                                text = "放弃修改",
                                onClick = {
                                    showLayoutSaveDialog = false
                                    previewViewRef?.discardLayoutChanges()
                                    subPage = 0
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            TextButton(
                                text = "保存并退出",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    showLayoutSaveDialog = false
                                    previewViewRef?.commitLayoutChanges()
                                    subPage = 0
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 导入配置二次确认：先展示文件来源与条目数，确认后再落盘 ──
        if (showImportConfirmDialog) {
            val summary = pendingImportSummary
            Dialog(
                onDismissRequest = { showImportConfirmDialog = false },
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
                        Text(
                            text = "导入配置？",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "文件来自 HyperOpen v${summary?.appVersion ?: "?"}，包含 " +
                                "${summary?.keyCount ?: 0} 项参数、${summary?.shortcutCount ?: 0} 个快捷方式。\n" +
                                "同名项会被覆盖，文件里没有的项保持不动；导入完成后界面会重新加载。",
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
                                onClick = { showImportConfirmDialog = false },
                            )
                            Spacer(Modifier.width(10.dp))
                            TextButton(
                                text = "导入",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    showImportConfirmDialog = false
                                    val content = pendingImportText
                                    if (content != null) {
                                        configScope.launch(Dispatchers.IO) {
                                            val r = ConfigBackup.apply(ctx, content)
                                            withContext(Dispatchers.Main) {
                                                if (r.ok) {
                                                    Toast.makeText(
                                                        ctx,
                                                        "已导入 ${r.keyCount} 项参数、${r.shortcutCount} 个快捷方式",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    // 界面状态是启动时的一次性快照，重建 Activity 才能读到新配置
                                                    activity?.recreate()
                                                } else {
                                                    Toast.makeText(
                                                        ctx,
                                                        r.message ?: "导入失败",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 顶部返回按钮组件（miuix 官方模板 component/BackNavigationIcon.kt 规范）
// ─────────────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────────────
// 底部导航栏组件（遵循 miuix 官方模板 AppContent.kt 规范）
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun AppBottomBar(
    items: List<NavigationItem>,
    selectedPage: Int,
    floating: Boolean,
    onPageSelect: (Int) -> Unit,
) {
    if (floating) {
        FloatingNavigationBar(
            showDivider = true,
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavigationBarItem(
                    selected = selectedPage == index,
                    onClick = { onPageSelect(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    } else {
        NavigationBar(
            showDivider = true,
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedPage == index,
                    onClick = { onPageSelect(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
    }
}

/**
 * 参数页的分段式分页控件。
 *
 * 刻意不用 Miuix 的 [TabRow]：它的 `backgroundColor` 是按 Tab 逐项求值（带 selected 参数）
 * 的药丸底色，整条容器底色无法覆盖，会和折叠后的标题栏拼出一条深浅不一的白带。
 * 这里自绘：页面底色（与标题栏同色）铺底 + 白色圆角轨道 + 主色浅底选中块，
 * 与应用里「白色卡片落在浅灰页面上」的整体风格一致。
 */
@Composable
private fun ParamsTabBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val labels = listOf("布局", "外观", "图标", "震动")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            // 左右留 12dp，与下方 Card 的 horizontal padding 对齐，避免轨道比卡片窄一圈
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
