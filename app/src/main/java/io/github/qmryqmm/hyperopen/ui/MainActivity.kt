package io.github.qmryqmm.hyperopen.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qmryqmm.hyperopen.config.ConfigBackup
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.layout.LayoutCapacity
import io.github.qmryqmm.hyperopen.model.ActivityLaunchSpec
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.safety.SafetyStore
import io.github.qmryqmm.hyperopen.view.IconGlyphFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
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
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.util.UUID

/**
 * Compose 设置界面：全面采用 miuix 官方 example 架构模板。
 * 支持快捷方式参数详情编辑（图标/名称/动作选项等）、应用图标与 miuix 矢量图标自定义选取，以及新建快捷方式。
 */
class MainActivity : ComponentActivity() {

    internal var backPressConsumer: (() -> Boolean)? = null

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (backPressConsumer?.invoke() == true) {
            return
        }
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Direct Boot：入口先补一次 CE→DE 数据迁移（幂等；已迁移时为进程内短路）
        try {
            io.github.qmryqmm.hyperopen.util.DePrefs.migrateCeToDe(this, HyperOpenConfig.PREF_NAME)
        } catch (ignored: Throwable) {}
        val config = HyperOpenConfig.loadFromApp(this)
        setContent {
            HyperOpenApp(activity = this, initialConfig = config)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// App 主结构
// ─────────────────────────────────────────────────────────────────────

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
    var ringCount by remember { mutableStateOf(initialConfig.ringCount.toFloat()) }
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
    val paramsListState = rememberLazyListState()
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
        c.ringCount = ringCount.toInt().coerceIn(1, 4)
        c.maxScale = maxScale
        c.influenceRadius = influenceRadius.toInt()
        c.iconAlpha = iconAlpha
        c.fisheyeZoomAlpha = fisheyeZoomAlpha
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
        ringCount = defaults.ringCount.toFloat()
        maxScale = defaults.maxScale
        influenceRadius = defaults.influenceRadius.toFloat()
        iconAlpha = defaults.iconAlpha
        fisheyeZoomAlpha = defaults.fisheyeZoomAlpha
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
                            this.maxScale = maxScale
                            this.influenceRadius = influenceRadius.toInt()
                            this.iconAlpha = iconAlpha
                            this.fisheyeZoomAlpha = fisheyeZoomAlpha
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
                                blurScene.bubbles.forEach { spot ->
                                    androidx.compose.runtime.key(spot.id) {
                                        if (spot.diameterPx > 2f) {
                                            val bubbleRadiusPx = spot.diameterPx / 2f
                                            val bubbleDiameterDp = with(density) { spot.diameterPx.toDp() }
                                            Box(
                                                modifier = Modifier
                                                    .offset(
                                                        x = with(density) { (spot.centerX - bubbleRadiusPx).toDp() },
                                                        y = with(density) { (spot.centerY - bubbleRadiusPx).toDp() }
                                                    )
                                                    .size(bubbleDiameterDp)
                                                    .textureBlur(
                                                        backdrop = bubbleBackdrop,
                                                        shape = CircleShape,
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
                                    lazyListState = paramsListState,
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
                                    maxScale = maxScale,
                                    onMaxScale = { maxScale = it; persist() },
                                    influenceRadius = influenceRadius,
                                    onInfluenceRadius = { influenceRadius = it; persist() },
                                    iconAlpha = iconAlpha,
                                    onIconAlpha = { iconAlpha = it; persist() },
                                    fisheyeZoomAlpha = fisheyeZoomAlpha,
                                    onFisheyeZoomAlpha = { fisheyeZoomAlpha = it; persist() },
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

@Composable
fun BackNavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    IconButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

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

// ─────────────────────────────────────────────────────────────────────
// Page0：参数配置（miuix 官方 Preference 规范）
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsibleSectionTitle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) { SmallTitle(title) }
        Text(
            text = if (expanded) "收起 ▲" else "展开 ▼",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 24.dp),
        )
    }
}

@Composable
fun ParamsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    materialStyle: Int, onMaterialStyle: (Int) -> Unit,
    offsetY: Float, onOffsetY: (Float) -> Unit,
    regionDiameter: Float, onRegionDiameter: (Float) -> Unit,
    showRegionBg: Boolean, onShowRegionBg: (Boolean) -> Unit,
    showHeaderPill: Boolean, onShowHeaderPill: (Boolean) -> Unit,
    showHeaderPillBg: Boolean, onShowHeaderPillBg: (Boolean) -> Unit,
    headerTopMargin: Float, onHeaderTopMargin: (Float) -> Unit,
    headerTitleSize: Float, onHeaderTitleSize: (Float) -> Unit,
    headerSubtitleSize: Float, onHeaderSubtitleSize: (Float) -> Unit,
    headerBlurRadius: Float, onHeaderBlurRadius: (Float) -> Unit,
    headerTextCentered: Boolean, onHeaderTextCentered: (Boolean) -> Unit,
    iconSize: Float, onIconSize: (Float) -> Unit,
    maxIconCount: Float, onMaxIconCount: (Float) -> Unit,
    iconCapacity: Int, enabledShortcutsCount: Int,
    layoutStyle: Int, onLayoutStyle: (Int) -> Unit,
    maxScale: Float, onMaxScale: (Float) -> Unit,
    influenceRadius: Float, onInfluenceRadius: (Float) -> Unit,
    iconAlpha: Float, onIconAlpha: (Float) -> Unit,
    fisheyeZoomAlpha: Float, onFisheyeZoomAlpha: (Float) -> Unit,
    fullscreenBlurMaskAlpha: Float, onFullscreenBlurMaskAlpha: (Float) -> Unit,
    fullscreenDimAlpha: Float, onFullscreenDimAlpha: (Float) -> Unit,
    headerDimAlpha: Float, onHeaderDimAlpha: (Float) -> Unit,
    regionDimAlpha: Float, onRegionDimAlpha: (Float) -> Unit,
    bubbleDimAlpha: Float, onBubbleDimAlpha: (Float) -> Unit,
    showDelayMs: Float, onShowDelayMs: (Float) -> Unit,
    faceUnlockQuickOpen: Boolean, onFaceUnlockQuickOpen: (Boolean) -> Unit,
    hapticEnabled: Boolean, onHapticEnabled: (Boolean) -> Unit,
    hapticStrength: Float, onHapticStrength: (Float) -> Unit,
    hapticDurationMs: Float, onHapticDurationMs: (Float) -> Unit,
    hapticMode: Int, onHapticMode: (Int) -> Unit,
    regionBlurRadius: Float, onRegionBlurRadius: (Float) -> Unit,
    iconSpacingOffset: Float, onIconSpacingOffset: (Float) -> Unit,
    bubblePaddingPercent: Float, onBubblePaddingPercent: (Float) -> Unit,
    bubbleBlurRadius: Float, onBubbleBlurRadius: (Float) -> Unit,
    selectedIconScale: Float, onSelectedIconScale: (Float) -> Unit,
    iconGlowEffect: Boolean, onIconGlowEffect: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current

    // 各分区折叠状态（key → 是否已收起），通过 SharedPreferences 记忆持久化
    val uiPrefs = remember { ctx.getSharedPreferences("hyperopen_ui_state", android.content.Context.MODE_PRIVATE) }
    val collapsed = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            listOf("material", "geometry", "header_pill", "physics", "haptic").forEach { k ->
                if (uiPrefs.getBoolean("section_collapsed_$k", false)) {
                    put(k, true)
                }
            }
        }
    }
    fun isOpen(key: String) = collapsed[key] != true
    fun toggle(key: String) {
        val willCollapse = isOpen(key)
        collapsed[key] = willCollapse
        uiPrefs.edit().putBoolean("section_collapsed_$key", willCollapse).apply()
    }

    fun triggerSampleHaptic(        mode: Int = hapticMode,
        strength: Int = hapticStrength.toInt(),
        durationMs: Int = hapticDurationMs.toInt(),
        enabled: Boolean = hapticEnabled,
    ) {
        try {
            @Suppress("DEPRECATION")
            val vibrator = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            val sampleCfg = HyperOpenConfig().apply {
                this.hapticEnabled = enabled
                this.hapticMode = mode
                this.hapticStrength = strength
                this.hapticDurationMs = durationMs
            }
            io.github.qmryqmm.hyperopen.view.CustomHyperOpenContainer.performConfiguredHaptic(vibrator, sampleCfg)
        } catch (_: Throwable) {}
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
        // ── 材质与系统风格 ──
        item(key = "material_title") { CollapsibleSectionTitle("材质与系统风格", isOpen("material")) { toggle("material") } }
        if (isOpen("material")) item(key = "material_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "液态玻璃",
                    summary = "Liquid Glass",
                    endActions = {
                        if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onMaterialStyle(HyperOpenConfig.MATERIAL_SOFT_GLASS) },
                )
                ArrowPreference(
                    title = "高斯模糊",
                    summary = "Gaussian Blur",
                    endActions = {
                        if (materialStyle == HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onMaterialStyle(HyperOpenConfig.MATERIAL_GAUSSIAN_BLUR) },
                )
                SliderPreference(
                    title = "全屏模糊遮罩程度",
                    summary = "控制呼出面板时全屏背景高斯模糊强度",
                    valueText = "${(fullscreenBlurMaskAlpha * 100).toInt()} %",
                    value = fullscreenBlurMaskAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onFullscreenBlurMaskAlpha(it / 100f) },
                )
                SliderPreference(
                    title = "全屏遮罩压暗程度",
                    summary = "独立调节全屏背景黑色压暗遮罩深浅",
                    valueText = if (kotlin.math.round(fullscreenDimAlpha * 100f) <= 0.5f) "不压暗" else "${kotlin.math.round(fullscreenDimAlpha * 100f).toInt()} %",
                    value = fullscreenDimAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onFullscreenDimAlpha(it / 100f) },
                )
            }
        }

        // ── 几何与位置参数 ──
        item(key = "geometry_title") { CollapsibleSectionTitle("几何与位置参数", isOpen("geometry")) { toggle("geometry") } }
        if (isOpen("geometry")) item(key = "geometry_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SliderPreference(
                    title = "整体垂直高度偏移",
                    summary = "改变整体图标高度",
                    valueText = "${offsetY.toInt()} dp",
                    value = offsetY,
                    valueRange = 0f..450f,
                    onValueChange = onOffsetY,
                )
                SliderPreference(
                    title = "圆形视界直径",
                    summary = "修改此项同时会影响最大图标显示数量",
                    valueText = "${regionDiameter.toInt()} dp",
                    value = regionDiameter,
                    valueRange = 150f..420f,
                    onValueChange = onRegionDiameter,
                )
                RadioButtonPreference(
                    title = "蜂窝阵列布局",
                    summary = "图标紧凑排布在整个圆形视界内（默认）",
                    selected = layoutStyle != HyperOpenConfig.LAYOUT_RING && layoutStyle != HyperOpenConfig.LAYOUT_ARC,
                    onClick = { onLayoutStyle(HyperOpenConfig.LAYOUT_HONEYCOMB) },
                )
                RadioButtonPreference(
                    title = "空心同心圆环布局",
                    summary = "中心留空，图标排布在同心圆环上",
                    selected = layoutStyle == HyperOpenConfig.LAYOUT_RING,
                    onClick = { onLayoutStyle(HyperOpenConfig.LAYOUT_RING) },
                )
                RadioButtonPreference(
                    title = "弧形扇面布局",
                    summary = "图标沿 150° 弧带排布，贴合拇指热区",
                    selected = layoutStyle == HyperOpenConfig.LAYOUT_ARC,
                    onClick = { onLayoutStyle(HyperOpenConfig.LAYOUT_ARC) },
                )
                SliderPreference(
                    title = "图标数量",
                    summary = if (layoutStyle == HyperOpenConfig.LAYOUT_ARC) {
                        "弧形扇面布局不设几何上限；已启用 $enabledShortcutsCount 项"
                    } else {
                        "当前参数下最多容纳 $iconCapacity 个；已启用 $enabledShortcutsCount 项" +
                            "，超出上限的启用项不显示"
                    },
                    valueText = if (maxIconCount.toInt() <= 0) "全部显示" else "${maxIconCount.toInt()} 个",
                    value = maxIconCount.coerceIn(0f, maxOf(iconCapacity, 1).toFloat()),
                    valueRange = 0f..maxOf(iconCapacity, 1).toFloat(),
                    onValueChange = onMaxIconCount,
                )
                SwitchPreference(
                    title = "显示圆形区域底衬与边框",
                    checked = showRegionBg,
                    onCheckedChange = onShowRegionBg,
                )
                if (showRegionBg) {
                    SliderPreference(
                        title = "圆形视界背景模糊半径",
                        summary = "结合高斯模糊与次表面散射渲染毛玻璃底盘",
                        valueText = if (regionBlurRadius.toInt() == 0) "已关闭模糊" else "${regionBlurRadius.toInt()} dp",
                        value = regionBlurRadius,
                        valueRange = 0f..50f,
                        onValueChange = onRegionBlurRadius,
                    )
                    SliderPreference(
                        title = "圆形视界背景压暗程度",
                        summary = "在毛玻璃底盘之上叠加黑色压暗，提升图标对比度",
                        valueText = if (kotlin.math.round(regionDimAlpha * 100f) <= 0.5f) "不压暗" else "${kotlin.math.round(regionDimAlpha * 100f).toInt()} %",
                        value = regionDimAlpha * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onRegionDimAlpha(it / 100f) },
                    )
                }
            }
        }

        // ── 顶部提示胶囊参数 ──
        item(key = "header_pill_title") { CollapsibleSectionTitle("顶部提示胶囊参数", isOpen("header_pill")) { toggle("header_pill") } }
        if (isOpen("header_pill")) item(key = "header_pill_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "显示顶部提示文本",
                    summary = "展示当前选中的快捷方式标题与描述，顶部提示总开关",
                    checked = showHeaderPill,
                    onCheckedChange = onShowHeaderPill,
                )
                if (showHeaderPill) {
                    SwitchPreference(
                        title = "顶部提示水平居中",
                        summary = "关闭后顶部提示胶囊将靠左对齐展示",
                        checked = headerTextCentered,
                        onCheckedChange = onHeaderTextCentered,
                    )
                    SliderPreference(
                        title = "顶部提示高低位置",
                        summary = "调节顶部提示胶囊距离屏幕顶部的垂直高度",
                        valueText = "${headerTopMargin.toInt()} dp",
                        value = headerTopMargin,
                        valueRange = 24f..360f,
                        onValueChange = onHeaderTopMargin,
                    )
                    SliderPreference(
                        title = "主标题文字大小",
                        valueText = "${"%.1f".format(headerTitleSize)} sp",
                        value = headerTitleSize,
                        valueRange = 12f..28f,
                        onValueChange = onHeaderTitleSize,
                    )
                    SliderPreference(
                        title = "副标题文字大小",
                        valueText = "${"%.1f".format(headerSubtitleSize)} sp",
                        value = headerSubtitleSize,
                        valueRange = 9f..20f,
                        onValueChange = onHeaderSubtitleSize,
                    )
                    SwitchPreference(
                        title = "显示顶部提示胶囊背景",
                        checked = showHeaderPillBg,
                        onCheckedChange = onShowHeaderPillBg,
                    )
                    if (showHeaderPillBg) {
                        SliderPreference(
                            title = "顶部提示背景模糊程度",
                            summary = "独立调节顶部胶囊背景毛玻璃模糊半径",
                            valueText = if (headerBlurRadius.toInt() == 0) "已关闭模糊" else "${headerBlurRadius.toInt()} dp",
                            value = headerBlurRadius,
                            valueRange = 0f..50f,
                            onValueChange = onHeaderBlurRadius,
                        )
                        SliderPreference(
                            title = "顶部提示背景压暗程度",
                            summary = "在胶囊毛玻璃背景之上叠加黑色压暗",
                            valueText = if (kotlin.math.round(headerDimAlpha * 100f) <= 0.5f) "不压暗" else "${kotlin.math.round(headerDimAlpha * 100f).toInt()} %",
                            value = headerDimAlpha * 100f,
                            valueRange = 0f..100f,
                            onValueChange = { onHeaderDimAlpha(it / 100f) },
                        )
                    }
                }
            }
        }

        // ── 图标与物理动力学 ──
        item(key = "physics_title") { CollapsibleSectionTitle("图标与物理动力学", isOpen("physics")) { toggle("physics") } }
        if (isOpen("physics")) item(key = "physics_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SliderPreference(
                    title = "解锁进入快捷方式等待时间",
                    summary = "指纹解锁后按住传感器多久进入面板（500 ms 为系统默认，0 为立即弹出）",
                    valueText = when {
                        showDelayMs.toInt() <= 0 -> "立即弹出"
                        showDelayMs.toInt() == 500 -> "系统默认"
                        else -> "${showDelayMs.toInt()} ms"
                    },
                    value = showDelayMs,
                    valueRange = 0f..2000f,
                    onValueChange = onShowDelayMs,
                )
                SwitchPreference(
                    title = "人脸解锁后长按快捷方式",
                    summary = "人脸识别成功停留在锁屏时，长按指纹进入桌面并继续唤出快捷方式（短按轻触仅进入桌面）",
                    checked = faceUnlockQuickOpen,
                    onCheckedChange = onFaceUnlockQuickOpen,
                )
                SliderPreference(
                    title = "图标基础尺寸",
                    valueText = "${iconSize.toInt()} dp",
                    value = iconSize,
                    valueRange = 40f..80f,
                    onValueChange = onIconSize,
                )
                SliderPreference(
                    title = "图标常态透明度",
                    valueText = "${(iconAlpha * 100).toInt()} %",
                    value = iconAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onIconAlpha(it / 100f) },
                )
                SliderPreference(
                    title = "鱼眼放大时透明度",
                    summary = "手指划过受鱼眼动力学放大时的动态透明度",
                    valueText = "${(fisheyeZoomAlpha * 100).toInt()} %",
                    value = fisheyeZoomAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onFisheyeZoomAlpha(it / 100f) },
                )
                SliderPreference(
                    title = "图标紧凑程度",
                    summary = "对蜂窝阵列、空心同心圆环与弧形扇面布局均生效",
                    valueText = when {
                        iconSpacingOffset.toInt() < 0 -> "紧凑 (${iconSpacingOffset.toInt()} dp)"
                        iconSpacingOffset.toInt() > 0 -> "松散 (+${iconSpacingOffset.toInt()} dp)"
                        else -> "标准 (0 dp)"
                    },
                    value = iconSpacingOffset,
                    valueRange = -10f..20f,
                    onValueChange = onIconSpacingOffset,
                )
                SliderPreference(
                    title = "鱼眼最大放大倍率",
                    valueText = "%.2f x".format(maxScale),
                    value = maxScale,
                    valueRange = 1.10f..1.80f,
                    onValueChange = onMaxScale,
                )
                SliderPreference(
                    title = "感应辐射范围",
                    valueText = "${influenceRadius.toInt()} dp",
                    value = influenceRadius,
                    valueRange = 60f..200f,
                    onValueChange = onInfluenceRadius,
                )
                SliderPreference(
                    title = "气泡内部留白比例",
                    valueText = "${bubblePaddingPercent.toInt()} %",
                    value = bubblePaddingPercent,
                    valueRange = 0f..40f,
                    onValueChange = onBubblePaddingPercent,
                )
                SliderPreference(
                    title = "气泡留白区域模糊半径",
                    summary = "与圆形视界区域底衬统一的高斯模糊毛玻璃样式",
                    valueText = if (bubbleBlurRadius.toInt() == 0) "已关闭模糊" else "${bubbleBlurRadius.toInt()} dp",
                    value = bubbleBlurRadius,
                    valueRange = 0f..50f,
                    onValueChange = onBubbleBlurRadius,
                )
                SliderPreference(
                    title = "气泡留白区域压暗程度",
                    summary = "在每个图标气泡的毛玻璃底衬之上叠加黑色压暗",
                    valueText = if (kotlin.math.round(bubbleDimAlpha * 100f) <= 0.5f) "不压暗" else "${kotlin.math.round(bubbleDimAlpha * 100f).toInt()} %",
                    value = bubbleDimAlpha * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onBubbleDimAlpha(it / 100f) },
                )
                SliderPreference(
                    title = "选中时图标缩放倍率",
                    valueText = "%.2f x".format(selectedIconScale),
                    value = selectedIconScale,
                    valueRange = 1.00f..1.50f,
                    onValueChange = onSelectedIconScale,
                )
                SwitchPreference(
                    title = "图标激活呼吸辉光动效",
                    summary = "手指滑过选中目标时泛起柔光微晕反馈",
                    checked = iconGlowEffect,
                    onCheckedChange = onIconGlowEffect,
                )
            }
        }

        // ── 震动与触觉反馈 ──
        item(key = "haptic_title") { CollapsibleSectionTitle("震动与触觉反馈", isOpen("haptic")) { toggle("haptic") } }
        if (isOpen("haptic")) item(key = "haptic_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "启用触觉震动反馈",
                    summary = "手指滑入或切换快捷方式图标时提供线性马达触觉反馈",
                    checked = hapticEnabled,
                    onCheckedChange = {
                        onHapticEnabled(it)
                        if (it) triggerSampleHaptic(enabled = true)
                    },
                )
                if (hapticEnabled) {
                    SliderPreference(
                        title = "震动强度",
                        summary = "调节马达脉冲振幅强弱（1 ~ 255）",
                        valueText = "${((hapticStrength / 255f) * 100).toInt().coerceIn(1, 100)} % (${hapticStrength.toInt()})",
                        value = hapticStrength,
                        valueRange = 1f..255f,
                        onValueChange = onHapticStrength,
                    )
                    RadioButtonPreference(
                        title = "清脆短震 (Tick)",
                        summary = "轻快短促的微动点按手感",
                        selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_TICK,
                        onClick = {
                            onHapticMode(HyperOpenConfig.HAPTIC_MODE_TICK)
                            triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_TICK)
                        },
                    )
                    RadioButtonPreference(
                        title = "标准点按 (Click)",
                        summary = "扎实均衡的系统级确认震感",
                        selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_CLICK,
                        onClick = {
                            onHapticMode(HyperOpenConfig.HAPTIC_MODE_CLICK)
                            triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_CLICK)
                        },
                    )
                    RadioButtonPreference(
                        title = "浑厚重按 (Heavy)",
                        summary = "深沉有力的重击触感反馈",
                        selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_HEAVY,
                        onClick = {
                            onHapticMode(HyperOpenConfig.HAPTIC_MODE_HEAVY)
                            triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_HEAVY)
                        },
                    )
                    RadioButtonPreference(
                        title = "双脉冲震动 (Double)",
                        summary = "连续两次轻快短脉冲反馈",
                        selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_DOUBLE,
                        onClick = {
                            onHapticMode(HyperOpenConfig.HAPTIC_MODE_DOUBLE)
                            triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_DOUBLE)
                        },
                    )
                    RadioButtonPreference(
                        title = "自定义时长脉冲",
                        summary = "自由设定单次震动脉冲持续毫秒数與振幅",
                        selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_CUSTOM,
                        onClick = {
                            onHapticMode(HyperOpenConfig.HAPTIC_MODE_CUSTOM)
                            triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_CUSTOM)
                        },
                    )
                    if (hapticMode == HyperOpenConfig.HAPTIC_MODE_CUSTOM) {
                        SliderPreference(
                            title = "自定义震动持续时长",
                            valueText = "${hapticDurationMs.toInt()} ms",
                            value = hapticDurationMs,
                            valueRange = 4f..80f,
                            onValueChange = onHapticDurationMs,
                        )
                    }
                    ArrowPreference(
                        title = "点击体验当前震动效果",
                        summary = "立即触发一次当前配置的触觉反馈",
                        onClick = { triggerSampleHaptic() },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Page1：快捷项目管理（独立底栏页面）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutsManagementPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    shortcuts: SnapshotStateList<ShortcutItem>,
    onOpenPreview: () -> Unit,
    onShortcutToggle: (ShortcutItem, Boolean) -> Unit,
    onShortcutClick: (ShortcutItem) -> Unit,
    onAddShortcut: () -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 操作按钮卡片 ──
        item(key = "actions_card") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "快捷方式整体预览",
                        summary = "所见即所得，支持长按图标拖拽调整蜂窝顺序",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.ScreenMirroring,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        },
                        onClick = onOpenPreview,
                    )
                    ArrowPreference(
                        title = "新建快捷方式",
                        summary = "添加自定义应用、Intent 或快捷指令",
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        },
                        onClick = onAddShortcut,
                    )
                }
            }
        }

        // ── 快捷项目列表 ──
        item(key = "shortcuts_title") { SmallTitle("快捷项目列表 (${shortcuts.size})") }
        itemsIndexed(shortcuts, key = { _, it -> it.id }) { _, item ->
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                ShortcutRow(
                    item = item,
                    onToggle = { onShortcutToggle(item, it) },
                    onClick = { onShortcutClick(item) },
                )
            }
        }

        item(key = "footer_tips") {
            Text(
                "提示：轻触卡片可修改参数（图标、字重底垫、名称及启动选项），右侧开关控制是否在指纹面板中展示。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 快捷方式列表项：点击卡片进入编辑，点击右侧开关切换启用状态
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutRow(
    item: ShortcutItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val vectorIcon = remember(item.iconPackage, item.iconGlyph) {
        if (item.iconPackage.isBlank()) {
            if (RemixIconRegistry.isRemixIcon(item.iconGlyph)) {
                RemixIconRegistry.find(item.iconGlyph)
            } else {
                MiuixIconRegistry.find(item.iconGlyph)
            }
        } else null
    }
    val painter = remember(item.id, item.iconPackage, item.iconGlyph, item.appIconCropMode, item.actionType, item.actionPayload) {
        if (item.iconPackage.isBlank() && vectorIcon != null) {
            null
        } else {
            IconPainter.resolve(item, ctx, item.appIconCropMode)
        }
    }

    val itemPadBg = if (vectorIcon != null && item.glyphPadEnabled) {
        when (item.glyphPadMode) {
            HyperOpenConfig.GLYPH_PAD_MODE_SOLID -> Color(item.glyphPadColor)
            HyperOpenConfig.GLYPH_PAD_MODE_INVERTED -> Color(0x30FFFFFF)
            else -> Color(item.glyphPadColor).copy(alpha = 0.65f)
        }
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }
    val itemIconTint = if (vectorIcon != null && item.glyphPadEnabled) {
        if (item.glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) Color(item.glyphPadColor)
        else Color.White
    } else {
        MiuixTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = item.title,
            summary = item.subtitle.ifBlank { "点击编辑快捷方式参数" },
            startAction = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(itemPadBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vectorIcon != null) {
                        Icon(
                            imageVector = vectorIcon,
                            contentDescription = item.title,
                            tint = itemIconTint,
                            modifier = Modifier.size(if (item.glyphPadEnabled) 20.dp else 24.dp),
                        )
                    } else if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = item.title,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            },
            endActions = {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggle,
                )
            },
            onClick = onClick,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 快捷方式参数编辑界面（全新二级设置界面，完全符合 miuix 规范）
// ─────────────────────────────────────────────────────────────────────

/** 设备 Android 用户条目（id + 显示名），供「启动用户」选择列表使用 */
private data class AndroidUserOption(val id: Int, val name: String)

/**
 * 枚举设备上的 Android 用户：
 * 1) 反射 UserManager#getUsers()（@SystemApi；可用时拿到真实用户名，如「手机分身」）
 * 2) 失败则退化为常用固定项：主空间 0 + 应用双开/手机分身 999
 */
private fun loadAndroidUsers(ctx: android.content.Context): List<AndroidUserOption> {
    val found = mutableListOf<AndroidUserOption>()
    try {
        val um = ctx.getSystemService(android.os.UserManager::class.java)
        if (um != null) {
            val raw: List<*>? = try {
                @Suppress("UNCHECKED_CAST")
                um.javaClass.getMethod("getUsers").invoke(um) as? List<*>
            } catch (_: Throwable) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    um.javaClass.getMethod("getUsers", java.lang.Boolean.TYPE)
                        .invoke(um, true) as? List<*>
                } catch (_: Throwable) {
                    null
                }
            }
            raw?.forEach { u ->
                if (u == null) return@forEach
                try {
                    val id = u.javaClass.getField("id").getInt(u)
                    val name = (u.javaClass.getField("name").get(u) as? String)
                        ?.ifBlank { "用户$id" } ?: "用户$id"
                    found.add(AndroidUserOption(id, name))
                } catch (_: Throwable) {
                }
            }
        }
    } catch (_: Throwable) {
    }
    if (found.isEmpty()) found.add(AndroidUserOption(0, "主空间"))
    if (found.none { it.id == 999 }) found.add(AndroidUserOption(999, "应用双开 / 手机分身"))
    return found.sortedBy { it.id }
}

/** 「启动 Activity」附加 Extra 的编辑器内存行（保存/测试时转 ActivityLaunchSpec.Extra） */
private data class ActivityExtraRow(
    val key: String = "",
    val type: String = ActivityLaunchSpec.TYPE_STRING,
    val value: String = "",
)

/** Extra 类型循环切换：string → int → long → bool → float → string */
private fun nextExtraType(t: String): String = when (t) {
    ActivityLaunchSpec.TYPE_STRING -> ActivityLaunchSpec.TYPE_INT
    ActivityLaunchSpec.TYPE_INT -> ActivityLaunchSpec.TYPE_LONG
    ActivityLaunchSpec.TYPE_LONG -> ActivityLaunchSpec.TYPE_BOOL
    ActivityLaunchSpec.TYPE_BOOL -> ActivityLaunchSpec.TYPE_FLOAT
    else -> ActivityLaunchSpec.TYPE_STRING
}

@Composable
fun ShortcutEditPage(
    contentPadding: PaddingValues,
    initialItem: ShortcutItem,
    isNew: Boolean,
    backTrigger: Int = 0,
    onSave: (ShortcutItem, Boolean) -> Unit,
    onDelete: (ShortcutItem) -> Unit,
    onExitWithoutSave: () -> Unit,
) {
    val ctx = LocalContext.current
    val lazyListState = rememberLazyListState()

    // ── 选项分组展开收起（与参数配置页同款）：key → 是否已收起，持久化到 hyperopen_ui_state ──
    val uiPrefs = remember { ctx.getSharedPreferences("hyperopen_ui_state", android.content.Context.MODE_PRIVATE) }
    val collapsed = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            listOf(
                "icon_settings", "single_app_icon_crop", "single_miuix_pad",
                "basic_info", "hint_text", "action_type", "target_user", "payload",
            ).forEach { k ->
                if (uiPrefs.getBoolean("edit_section_collapsed_$k", false)) put(k, true)
            }
        }
    }
    fun isOpen(key: String) = collapsed[key] != true
    fun toggle(key: String) {
        val willCollapse = isOpen(key)
        collapsed[key] = willCollapse
        uiPrefs.edit().putBoolean("edit_section_collapsed_$key", willCollapse).apply()
    }

    var title by remember { mutableStateOf(initialItem.title) }
    var subtitle by remember { mutableStateOf(initialItem.subtitle) }
    var actionType by remember { mutableStateOf(initialItem.actionType) }
    var actionPayload by remember { mutableStateOf(initialItem.actionPayload) }
    var iconPackage by remember { mutableStateOf(initialItem.iconPackage) }
    var iconGlyph by remember { mutableStateOf(initialItem.iconGlyph) }
    var glyphPadEnabled by remember { mutableStateOf(initialItem.glyphPadEnabled) }
    var glyphPadColor by remember { mutableIntStateOf(initialItem.glyphPadColor) }
    var glyphPadMode by remember { mutableIntStateOf(initialItem.glyphPadMode) }
    var appIconCropMode by remember {
        mutableIntStateOf(
            if (initialItem.appIconCropMode > 0) initialItem.appIconCropMode
            else HyperOpenConfig.CROP_ADAPTIVE_EXPAND
        )
    }
    var enabled by remember { mutableStateOf(initialItem.enabled) }
    // ── 提示文字样式（hint* 字段）：颜色 / 透明度 / 投影 / 描边 / 字重 / 字距 / 显隐 ──
    var hintTextColor by remember { mutableIntStateOf(initialItem.hintTextColor) }
    var hintTextAlpha by remember { mutableFloatStateOf(initialItem.hintTextAlpha) }
    var hintTextColorSub by remember { mutableIntStateOf(initialItem.hintTextColorSub) }
    /** -1 = 跟随标题透明度 ×0.82 */
    var hintTextAlphaSub by remember { mutableFloatStateOf(initialItem.hintTextAlphaSub) }
    var hintTextShadow by remember { mutableFloatStateOf(initialItem.hintTextShadow.toFloat()) }
    var hintTextStroke by remember { mutableFloatStateOf(initialItem.hintTextStroke.toFloat()) }
    var hintTextWeight by remember { mutableIntStateOf(initialItem.hintTextWeight) }
    var hintTextLetterSpacing by remember { mutableFloatStateOf(initialItem.hintTextLetterSpacing) }
    var showHintTitle by remember { mutableStateOf(initialItem.showHintTitle) }
    var showHintSubtitle by remember { mutableStateOf(initialItem.showHintSubtitle) }
    // 颜色十六进制输入（关闭「跟随」后显示，与底垫 padHexInput 同款双向同步模式）
    var hintHexInput by remember {
        val base = if (initialItem.hintTextColor != 0) initialItem.hintTextColor else initialItem.tintColor
        mutableStateOf(String.format("#%06X", base and 0xFFFFFF))
    }
    var hintHexSubInput by remember {
        val base = if (initialItem.hintTextColorSub != 0) initialItem.hintTextColorSub
            else if (initialItem.hintTextColor != 0) initialItem.hintTextColor else initialItem.tintColor
        mutableStateOf(String.format("#%06X", base and 0xFFFFFF))
    }
    // 启动用户（0 = 当前/主空间默认；>0 跨用户，如 999 应用双开/分身）与 SHELL Root 执行开关
    // 启动用户：userId 字段优先；老快捷方式只有 URI 标记（i.hyperopen_user）时从 payload 解析回来
    val initialPickedUser = if (initialItem.userId > 0) initialItem.userId
        else if (initialItem.actionType == ShortcutItem.ACTION_INTENT_URI)
            ShortcutItem.extractUserFromPayload(initialItem.actionPayload).coerceAtLeast(0)
        else 0
    var pickedUser by remember { mutableIntStateOf(initialPickedUser) }
    var shellAsRoot by remember { mutableStateOf(initialItem.shellUseRoot) }
    var customUserText by remember {
        mutableStateOf(if (initialPickedUser > 0 && initialPickedUser != 999) initialPickedUser.toString() else "")
    }
    // ── 「启动 Activity」结构化 Intent 编辑状态（与 actionPayload 的 JSON 双向同步）──
    val initialActSpec = remember(initialItem) {
        if (initialItem.actionType == ShortcutItem.ACTION_ACTIVITY) ActivityLaunchSpec.fromJson(initialItem.actionPayload)
        else ActivityLaunchSpec()
    }
    var actAction by remember { mutableStateOf(initialActSpec.action ?: "") }
    var actData by remember { mutableStateOf(initialActSpec.data ?: "") }
    var actComponent by remember { mutableStateOf(initialActSpec.component ?: "") }
    var actPackage by remember { mutableStateOf(initialActSpec.packageName ?: "") }
    var actExtras by remember {
        mutableStateOf(initialActSpec.extras.map { ActivityExtraRow(it.key ?: "", it.type ?: "string", it.value ?: "") })
    }
    var actNewTask by remember { mutableStateOf(initialActSpec.newTask) }
    var actUseRoot by remember { mutableStateOf(initialActSpec.useRoot) }
    // 拼装当前结构化规格为 payload JSON（保存与「立即启动测试」共用，保证测的就是存的）
    fun buildActPayload(): String {
        val spec = ActivityLaunchSpec()
        spec.action = actAction.trim()
        spec.data = actData.trim()
        spec.component = actComponent.trim()
        spec.packageName = actPackage.trim()
        spec.newTask = actNewTask
        spec.useRoot = actUseRoot
        spec.extras = actExtras.map { ActivityLaunchSpec.Extra(it.key.trim(), it.type, it.value) }
        return spec.toJson()
    }
    // 组装当前编辑态为可保存的快捷方式（「保存修改」按钮与未保存弹窗「保存并退出」共用）
    fun buildSavedItem(): ShortcutItem {
        val newItem = initialItem.copy()
        newItem.title = title.trim().ifBlank { "快捷方式" }
        newItem.subtitle = subtitle.trim()
        newItem.actionType = actionType
        newItem.actionPayload = if (actionType == ShortcutItem.ACTION_ACTIVITY) buildActPayload() else actionPayload.trim()
        newItem.iconPackage = iconPackage.trim()
        newItem.iconGlyph = iconGlyph.trim()
        newItem.glyphPadEnabled = glyphPadEnabled
        newItem.glyphPadColor = glyphPadColor
        newItem.glyphPadMode = glyphPadMode
        newItem.appIconCropMode = appIconCropMode
        newItem.enabled = enabled
        newItem.userId = pickedUser
        newItem.shellUseRoot = shellAsRoot
        newItem.hintTextColor = hintTextColor
        newItem.hintTextAlpha = hintTextAlpha.coerceIn(0.05f, 1f)
        newItem.hintTextColorSub = hintTextColorSub
        newItem.hintTextAlphaSub = hintTextAlphaSub
        newItem.hintTextShadow = hintTextShadow.toInt().coerceIn(0, 100)
        newItem.hintTextStroke = Math.round(hintTextStroke).coerceIn(0, 4)
        newItem.hintTextWeight = hintTextWeight
        newItem.hintTextLetterSpacing = (Math.round(hintTextLetterSpacing * 2f) / 2f).coerceIn(-2f, 6f)
        newItem.showHintTitle = showHintTitle
        newItem.showHintSubtitle = showHintSubtitle
        if (newItem.actionType == ShortcutItem.ACTION_INTENT_URI) {
            // 同步写回 URI 标记（i.hyperopen_user=N）：payload 自描述，
            // 且 SystemUI 里未重启的旧版模块代码也能按用户启动
            newItem.actionPayload = ShortcutItem.syncUserMarker(newItem.actionPayload, pickedUser)
        }
        return newItem
    }
    // 设备用户列表（「启动用户」选择用；反射枚举一次即可）
    val userOptions = remember(ctx) { loadAndroidUsers(ctx) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showActivityPicker by remember { mutableStateOf(false) }
    var showMiuixIconPicker by remember { mutableStateOf(false) }
    var showRemixIconPicker by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // 自定义矢量图标底垫颜色：十六进制文本框（与 RGB 分量滑条双向同步）
    var padHexInput by remember {
        mutableStateOf(String.format("#%06X", initialItem.glyphPadColor and 0xFFFFFF))
    }
    fun parseHexColor(text: String): Int? {
        val t = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (t.length != 6) return null
        return try {
            (0xFF shl 24) or t.toLong(16).toInt()
        } catch (_: Throwable) {
            null
        }
    }
    fun composePadColor(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    fun syncPadHexFromColor() {
        padHexInput = String.format("#%06X", glyphPadColor and 0xFFFFFF)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val rawBmp = android.graphics.BitmapFactory.decodeStream(stream)
                    if (rawBmp != null && rawBmp.width > 0 && rawBmp.height > 0) {
                        val size = 192
                        val srcW = rawBmp.width
                        val srcH = rawBmp.height
                        val minEdge = kotlin.math.min(srcW, srcH)
                        val cropX = (srcW - minEdge) / 2
                        val cropY = (srcH - minEdge) / 2
                        val cropped = android.graphics.Bitmap.createBitmap(rawBmp, cropX, cropY, minEdge, minEdge)
                        val scaled = if (minEdge != size) {
                            android.graphics.Bitmap.createScaledBitmap(cropped, size, size, true)
                        } else {
                            cropped
                        }
                        val bos = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
                        val b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                        iconPackage = "${IconGlyphFactory.CUSTOM_IMAGE_PREFIX}$b64"
                        iconGlyph = ""
                        Toast.makeText(ctx, "已成功导入本地自定义图标", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "无法解析所选图片文件", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(ctx, "导入图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 检查是否有未保存的修改（基准为「最近一次保存」的快照：保存修改后留在编辑页时重置基准，
    // 返回键不会因已保存内容误弹未保存弹窗）
    var savedBaseline by remember { mutableStateOf(initialItem) }
    val isModified = remember(title, subtitle, actionType, actionPayload, iconPackage, iconGlyph, glyphPadEnabled, glyphPadColor, glyphPadMode, appIconCropMode, enabled, pickedUser, shellAsRoot, actAction, actData, actComponent, actPackage, actExtras, actNewTask, actUseRoot, savedBaseline, hintTextColor, hintTextAlpha, hintTextColorSub, hintTextAlphaSub, hintTextShadow, hintTextStroke, hintTextWeight, hintTextLetterSpacing, showHintTitle, showHintSubtitle) {
        title != savedBaseline.title ||
        subtitle != savedBaseline.subtitle ||
        actionType != savedBaseline.actionType ||
        actionPayload != savedBaseline.actionPayload ||
        iconPackage != savedBaseline.iconPackage ||
        iconGlyph != savedBaseline.iconGlyph ||
        glyphPadEnabled != savedBaseline.glyphPadEnabled ||
        glyphPadColor != savedBaseline.glyphPadColor ||
        glyphPadMode != savedBaseline.glyphPadMode ||
        appIconCropMode != (if (savedBaseline.appIconCropMode > 0) savedBaseline.appIconCropMode else HyperOpenConfig.CROP_ADAPTIVE_EXPAND) ||
        enabled != savedBaseline.enabled ||
        pickedUser != savedBaseline.userId ||
        shellAsRoot != savedBaseline.shellUseRoot ||
        hintTextColor != savedBaseline.hintTextColor ||
        hintTextAlpha != savedBaseline.hintTextAlpha ||
        hintTextColorSub != savedBaseline.hintTextColorSub ||
        hintTextAlphaSub != savedBaseline.hintTextAlphaSub ||
        hintTextShadow.toInt() != savedBaseline.hintTextShadow ||
        Math.round(hintTextStroke) != savedBaseline.hintTextStroke ||
        hintTextWeight != savedBaseline.hintTextWeight ||
        Math.round(hintTextLetterSpacing * 2f) / 2 != Math.round(savedBaseline.hintTextLetterSpacing * 2f) / 2 ||
        showHintTitle != savedBaseline.showHintTitle ||
        showHintSubtitle != savedBaseline.showHintSubtitle ||
        (actionType == ShortcutItem.ACTION_ACTIVITY && buildActPayload() != savedBaseline.actionPayload)
    }

    // 响应顶部返回键或系统返回手势（记录初始 trigger，只有在当前页面内新发生点击时才响应）
    var lastHandledTrigger by remember { mutableIntStateOf(backTrigger) }
    LaunchedEffect(backTrigger) {
        if (backTrigger > lastHandledTrigger) {
            lastHandledTrigger = backTrigger
            if (isModified || isNew) {
                showUnsavedDialog = true
            } else {
                onExitWithoutSave()
            }
        }
    }

    // 实时图标预览与是否为应用图标判定
    val vectorIcon = remember(iconPackage, iconGlyph) {
        if (iconPackage.isBlank()) {
            if (RemixIconRegistry.isRemixIcon(iconGlyph)) {
                RemixIconRegistry.find(iconGlyph)
            } else {
                MiuixIconRegistry.find(iconGlyph)
            }
        } else null
    }
    val rawAppIconDrawable = remember(iconPackage, iconGlyph, actionType, actionPayload) {
        if (vectorIcon != null) {
            null
        } else {
            IconGlyphFactory.resolveRawAppIcon(ctx, iconPackage, iconGlyph, actionType, actionPayload)
        }
    }
    val isAppIcon = rawAppIconDrawable != null
    val appDrawablePainter = remember(rawAppIconDrawable, iconGlyph, appIconCropMode, initialItem.tintColor) {
        if (vectorIcon != null) {
            null
        } else {
            val processed = if (rawAppIconDrawable != null) {
                io.github.qmryqmm.hyperopen.view.AppIconCropProcessor.process(rawAppIconDrawable, appIconCropMode) ?: rawAppIconDrawable
            } else {
                IconGlyphFactory.createGlyph(iconGlyph.ifBlank { "sparkle" }, initialItem.tintColor)
            }
            val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            processed.setBounds(0, 0, 96, 96)
            processed.draw(canvas)
            BitmapPainter(bmp.toComposeImageBitmap())
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
        // ── 预览卡片 ──
        item(key = "preview_card") {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val isVectorSelected = vectorIcon != null && iconPackage.isBlank()
                        val previewPadBg = if (isVectorSelected && glyphPadEnabled) {
                            when (glyphPadMode) {
                                HyperOpenConfig.GLYPH_PAD_MODE_SOLID -> Color(glyphPadColor)
                                HyperOpenConfig.GLYPH_PAD_MODE_INVERTED -> Color(0x30FFFFFF)
                                else -> Color(glyphPadColor).copy(alpha = 0.65f)
                            }
                        } else {
                            MiuixTheme.colorScheme.surfaceVariant
                        }
                        val previewIconTint = if (isVectorSelected && glyphPadEnabled) {
                            if (glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED) Color(glyphPadColor)
                            else Color.White
                        } else {
                            MiuixTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(previewPadBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (vectorIcon != null) {
                                Icon(
                                    imageVector = vectorIcon,
                                    contentDescription = title,
                                    tint = previewIconTint,
                                    modifier = Modifier.size(if (glyphPadEnabled) 32.dp else 38.dp),
                                )
                            } else if (appDrawablePainter != null) {
                                Image(
                                    painter = appDrawablePainter,
                                    contentDescription = title,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = title.ifBlank { "快捷方式名称" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        TextButton(
                            text = "立即启动测试",
                            onClick = {
                                val testItem = initialItem.copy().apply {
                                    this.title = title.trim().ifBlank { "测试快捷方式" }
                                    this.actionType = actionType
                                    this.actionPayload = if (actionType == ShortcutItem.ACTION_ACTIVITY) buildActPayload() else actionPayload.trim()
                                }
                                testItem.userId = pickedUser
                                testItem.shellUseRoot = shellAsRoot
                                if (testItem.actionType == ShortcutItem.ACTION_INTENT_URI) {
                                    // 与保存一致：URI 同步写回用户标记，测试的就是最终生效的 payload
                                    testItem.actionPayload = ShortcutItem.syncUserMarker(testItem.actionPayload, pickedUser)
                                }
                                testItem.executeAsync(ctx)
                            },
                        )
                    }
                }
            }
        }

        // ── 图标自定义配置 ──
        item(key = "icon_settings_title") {
            CollapsibleSectionTitle("图标设置", isOpen("icon_settings")) { toggle("icon_settings") }
        }
        if (isOpen("icon_settings")) {
        item(key = "icon_settings_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "上传本地自定义图片",
                    summary = if (IconGlyphFactory.isCustomImage(iconPackage)) "已使用本地上传的自定义图片" else "从相册或文件管理器选择本地图片作为图标",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { imagePickerLauncher.launch("image/*") },
                )
                ArrowPreference(
                    title = "选择系统/应用图标",
                    summary = when {
                        IconGlyphFactory.isCustomImage(iconPackage) -> "未选择"
                        iconPackage.isNotBlank() -> "当前选择：$iconPackage"
                        else -> "未选择"
                    },
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Photos,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { showAppPicker = true },
                )
                ArrowPreference(
                    title = "选择 Remix Icon 快捷图标(推荐)",
                    summary = if (iconPackage.isBlank() && iconGlyph.isNotBlank() && RemixIconRegistry.isRemixIcon(iconGlyph)) {
                        val meta = RemixIconRegistry.findMeta(iconGlyph)
                        "当前选择：${meta?.desc ?: iconGlyph} (${meta?.rawName ?: iconGlyph})"
                    } else "未选择",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Tune,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { showRemixIconPicker = true },
                )
                ArrowPreference(
                    title = "选择 Miuix 矢量图标",
                    summary = if (iconPackage.isBlank() && iconGlyph.isNotBlank() && !RemixIconRegistry.isRemixIcon(iconGlyph) && vectorIcon != null) "当前选择：$iconGlyph" else "未选择",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Scan,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { showMiuixIconPicker = true },
                )
                if (iconPackage.isNotBlank() || iconGlyph.isNotBlank()) {
                    ArrowPreference(
                        title = "清除自定义图标",
                        summary = "恢复跟随启动应用或默认符号",
                        onClick = {
                            iconPackage = ""
                            iconGlyph = ""
                        },
                    )
                }
            }
        }
        }

        // ── 仅当快捷方式的图标为应用图标时，显示独享的应用图标形状方案 ──
        if (isAppIcon) {
            item(key = "single_app_icon_crop_title") {
                CollapsibleSectionTitle("应用图标形状方案", isOpen("single_app_icon_crop")) { toggle("single_app_icon_crop") }
            }
            if (isOpen("single_app_icon_crop")) {
            item(key = "single_app_icon_crop_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    RadioButtonPreference(
                        title = "自适应外扩满铺",
                        summary = "分离前景与背景图层并外扩满铺正圆，彻底消除直角白边",
                        selected = appIconCropMode == HyperOpenConfig.CROP_ADAPTIVE_EXPAND,
                        onClick = { appIconCropMode = HyperOpenConfig.CROP_ADAPTIVE_EXPAND },
                    )
                    RadioButtonPreference(
                        title = "标准抗锯齿圆形裁切",
                        summary = "对完整渲染图标直接进行正圆形 BitmapShader 裁切",
                        selected = appIconCropMode == HyperOpenConfig.CROP_BITMAP_CIRCLE,
                        onClick = { appIconCropMode = HyperOpenConfig.CROP_BITMAP_CIRCLE },
                    )
                }
            }
            }
        }

        // ── 仅当选择 Miuix 或 Remix 矢量图标时，展示独享的底垫与色彩配置 ──
        if (vectorIcon != null && iconPackage.isBlank()) {
            item(key = "single_miuix_pad_title") {
                CollapsibleSectionTitle("矢量图标底垫与色彩", isOpen("single_miuix_pad")) { toggle("single_miuix_pad") }
            }
            if (isOpen("single_miuix_pad")) {
            item(key = "single_miuix_pad_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = "启用此图标圆形底垫",
                        summary = "为当前矢量图标添加独立圆形底板",
                        checked = glyphPadEnabled,
                        onCheckedChange = { glyphPadEnabled = it },
                    )

                    if (glyphPadEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        Text(
                            text = "底垫渲染质感",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )

                        RadioButtonPreference(
                            title = "磨砂微透底板",
                            summary = "65% 半透明高光底板，颜色与背景毛玻璃相互交融",
                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT,
                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_TRANSLUCENT },
                        )
                        RadioButtonPreference(
                            title = "饱满纯色底板",
                            summary = "100% 不透明纯色实心圆，上方呈现白色高对比矢量",
                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_SOLID,
                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_SOLID },
                        )
                        RadioButtonPreference(
                            title = "反色渲染模式",
                            summary = "微透亮白底板 + 彩色矢量图标",
                            selected = glyphPadMode == HyperOpenConfig.GLYPH_PAD_MODE_INVERTED,
                            onClick = { glyphPadMode = HyperOpenConfig.GLYPH_PAD_MODE_INVERTED },
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        Text(
                            text = "快捷品牌色预设",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                        )

                        val brandColors = listOf(
                            "微信绿" to 0xFF07C160.toInt(),
                            "支付宝蓝" to 0xFF1677FF.toInt(),
                            "小米橙" to 0xFFFF6900.toInt(),
                            "网易红" to 0xFFE60026.toInt(),
                            "极客紫" to 0xFF7928CA.toInt(),
                            "暗夜黑" to 0xFF1F2329.toInt(),
                            "科技蓝" to 0xFF2A82E4.toInt(),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            brandColors.forEach { (name, colorValue) ->
                                val isSelectedColor = glyphPadColor == colorValue
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .clickable {
                                            glyphPadColor = colorValue
                                            padHexInput = String.format("#%06X", colorValue and 0xFFFFFF)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelectedColor) {
                                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        Text(
                            text = "自定义底垫颜色",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(glyphPadColor)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (brandColors.none { it.second == glyphPadColor }) {
                                    Text("✦", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            TextField(
                                value = padHexInput,
                                onValueChange = { s ->
                                    padHexInput = s
                                    parseHexColor(s)?.let { glyphPadColor = it }
                                },
                                label = "十六进制颜色 (#RRGGBB)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        SliderPreference(
                            title = "红色分量",
                            summary = "自定义底垫颜色的红色通道 (R)",
                            valueText = "${(glyphPadColor shr 16) and 0xFF}",
                            value = ((glyphPadColor shr 16) and 0xFF).toFloat(),
                            valueRange = 0f..255f,
                            onValueChange = { r ->
                                glyphPadColor = composePadColor(
                                    r.toInt(), (glyphPadColor shr 8) and 0xFF, glyphPadColor and 0xFF
                                )
                                syncPadHexFromColor()
                            },
                        )
                        SliderPreference(
                            title = "绿色分量",
                            summary = "自定义底垫颜色的绿色通道 (G)",
                            valueText = "${(glyphPadColor shr 8) and 0xFF}",
                            value = ((glyphPadColor shr 8) and 0xFF).toFloat(),
                            valueRange = 0f..255f,
                            onValueChange = { g ->
                                glyphPadColor = composePadColor(
                                    (glyphPadColor shr 16) and 0xFF, g.toInt(), glyphPadColor and 0xFF
                                )
                                syncPadHexFromColor()
                            },
                        )
                        SliderPreference(
                            title = "蓝色分量",
                            summary = "自定义底垫颜色的蓝色通道 (B)",
                            valueText = "${glyphPadColor and 0xFF}",
                            value = (glyphPadColor and 0xFF).toFloat(),
                            valueRange = 0f..255f,
                            onValueChange = { b ->
                                glyphPadColor = composePadColor(
                                    (glyphPadColor shr 16) and 0xFF, (glyphPadColor shr 8) and 0xFF, b.toInt()
                                )
                                syncPadHexFromColor()
                            },
                        )

                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            }
        }

        // ── 基础信息 ──
        item(key = "basic_info_title") {
            CollapsibleSectionTitle("基础参数", isOpen("basic_info")) { toggle("basic_info") }
        }
        if (isOpen("basic_info")) {
        item(key = "basic_info_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "快捷方式标题",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    TextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = "副标题 / 功能描述（可选）",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "在指纹快捷面板中启用",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        }
        }

        // ── 提示文字（顶部胶囊选中提示的颜色/透明度/文案/质感）──
        item(key = "hint_text_title") {
            CollapsibleSectionTitle("提示文字", isOpen("hint_text")) { toggle("hint_text") }
        }
        if (isOpen("hint_text")) {
        item(key = "hint_text_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                // 实时预览胶囊：颜色/透明度/字重/字距/投影按与真机一致的回退链解析
                val previewDensity = androidx.compose.ui.platform.LocalDensity.current.density
                val previewTitleBase = Color(if (hintTextColor != 0) hintTextColor else initialItem.tintColor)
                val previewTitleAlpha = hintTextAlpha.coerceIn(0.05f, 1f)
                val previewSubAlphaEffective =
                    (if (hintTextAlpha <= 0f) 1f else hintTextAlpha).coerceIn(0.05f, 1f) * 0.82f
                val previewSubBase = Color(
                    if (hintTextColorSub != 0) hintTextColorSub
                    else if (hintTextColor != 0) hintTextColor else initialItem.tintColor
                )
                val previewSubAlpha =
                    if (hintTextAlphaSub >= 0f) hintTextAlphaSub.coerceIn(0.05f, 1f) else previewSubAlphaEffective
                val previewShadow = if (hintTextShadow >= 0.5f) {
                    Shadow(
                        color = Color(0x99000000),
                        blurRadius = hintTextShadow / 20f * 4f * previewDensity,
                    )
                } else null
                val previewWeight = when (hintTextWeight) {
                    0 -> FontWeight.Light
                    2 -> FontWeight.Medium
                    3 -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (showHintTitle) {
                            Text(
                                text = title.trim().ifBlank { "快捷方式" },
                                fontSize = 15.sp,
                                fontWeight = previewWeight,
                                letterSpacing = (15f * hintTextLetterSpacing * 0.05f).sp,
                                color = previewTitleBase.copy(alpha = previewTitleBase.alpha * previewTitleAlpha),
                                style = TextStyle(shadow = previewShadow),
                            )
                        }
                        if (showHintSubtitle) {
                            Text(
                                text = subtitle.trim().ifEmpty { "移至目标图标后松手直接打开" },
                                fontSize = 11.sp,
                                letterSpacing = (11f * hintTextLetterSpacing * 0.05f).sp,
                                color = previewSubBase.copy(alpha = previewSubBase.alpha * previewSubAlpha),
                                style = TextStyle(shadow = previewShadow),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "文字内容",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                SwitchPreference(
                    title = "显示提示标题",
                    summary = "选中图标时胶囊内显示主标题（快捷方式标题）",
                    checked = showHintTitle,
                    onCheckedChange = { showHintTitle = it },
                )
                SwitchPreference(
                    title = "显示提示副标题",
                    summary = "选中图标时胶囊内显示副标题描述",
                    checked = showHintSubtitle,
                    onCheckedChange = { showHintSubtitle = it },
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "颜色与透明度",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                SwitchPreference(
                    title = "标题颜色跟随图标主题色",
                    summary = "开启 = 使用图标底色，关闭可自定义提示标题颜色",
                    checked = hintTextColor == 0,
                    onCheckedChange = { follow ->
                        hintTextColor = if (follow) 0
                        else parseHexColor(hintHexInput) ?: (initialItem.tintColor or 0xFF000000.toInt())
                    },
                )
                if (hintTextColor != 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(hintTextColor)),
                        )
                        TextField(
                            value = hintHexInput,
                            onValueChange = { s ->
                                hintHexInput = s
                                parseHexColor(s)?.let { hintTextColor = it }
                            },
                            label = "标题颜色 十六进制 (#RRGGBB)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SliderPreference(
                        title = "标题红色分量",
                        summary = "提示标题颜色的红色通道 (R)",
                        valueText = "${(hintTextColor shr 16) and 0xFF}",
                        value = ((hintTextColor shr 16) and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { r ->
                            hintTextColor = composePadColor(r.toInt(), (hintTextColor shr 8) and 0xFF, hintTextColor and 0xFF)
                            hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                        },
                    )
                    SliderPreference(
                        title = "绿色分量",
                        summary = "提示标题颜色的绿色通道 (G)",
                        valueText = "${(hintTextColor shr 8) and 0xFF}",
                        value = ((hintTextColor shr 8) and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { g ->
                            hintTextColor = composePadColor((hintTextColor shr 16) and 0xFF, g.toInt(), hintTextColor and 0xFF)
                            hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                        },
                    )
                    SliderPreference(
                        title = "蓝色分量",
                        summary = "提示标题颜色的蓝色通道 (B)",
                        valueText = "${hintTextColor and 0xFF}",
                        value = (hintTextColor and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { b ->
                            hintTextColor = composePadColor((hintTextColor shr 16) and 0xFF, (hintTextColor shr 8) and 0xFF, b.toInt())
                            hintHexInput = String.format("#%06X", hintTextColor and 0xFFFFFF)
                        },
                    )
                }
                SliderPreference(
                    title = "标题透明度",
                    summary = "提示标题不透明度 5%~100%",
                    valueText = "${Math.round(hintTextAlpha.coerceIn(0.05f, 1f) * 100)}%",
                    value = hintTextAlpha.coerceIn(0.05f, 1f),
                    valueRange = 0.05f..1f,
                    onValueChange = { hintTextAlpha = (Math.round(it * 20f) / 20f).coerceIn(0.05f, 1f) },
                )
                SwitchPreference(
                    title = "副标题颜色跟随标题",
                    summary = "开启 = 与标题同色；关闭可自定义副标题独立颜色",
                    checked = hintTextColorSub == 0,
                    onCheckedChange = { follow ->
                        hintTextColorSub = if (follow) 0
                        else parseHexColor(hintHexSubInput)
                            ?: (if (hintTextColor != 0) hintTextColor else initialItem.tintColor)
                    },
                )
                if (hintTextColorSub != 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(hintTextColorSub)),
                        )
                        TextField(
                            value = hintHexSubInput,
                            onValueChange = { s ->
                                hintHexSubInput = s
                                parseHexColor(s)?.let { hintTextColorSub = it }
                            },
                            label = "副标题颜色 十六进制 (#RRGGBB)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SliderPreference(
                        title = "副标题红色分量",
                        summary = "提示副标题颜色的红色通道 (R)",
                        valueText = "${(hintTextColorSub shr 16) and 0xFF}",
                        value = ((hintTextColorSub shr 16) and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { r ->
                            hintTextColorSub = composePadColor(r.toInt(), (hintTextColorSub shr 8) and 0xFF, hintTextColorSub and 0xFF)
                            hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                        },
                    )
                    SliderPreference(
                        title = "副标题绿色分量",
                        summary = "提示副标题颜色的绿色通道 (G)",
                        valueText = "${(hintTextColorSub shr 8) and 0xFF}",
                        value = ((hintTextColorSub shr 8) and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { g ->
                            hintTextColorSub = composePadColor((hintTextColorSub shr 16) and 0xFF, g.toInt(), hintTextColorSub and 0xFF)
                            hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                        },
                    )
                    SliderPreference(
                        title = "副标题蓝色分量",
                        summary = "提示副标题颜色的蓝色通道 (B)",
                        valueText = "${hintTextColorSub and 0xFF}",
                        value = (hintTextColorSub and 0xFF).toFloat(),
                        valueRange = 0f..255f,
                        onValueChange = { b ->
                            hintTextColorSub = composePadColor((hintTextColorSub shr 16) and 0xFF, (hintTextColorSub shr 8) and 0xFF, b.toInt())
                            hintHexSubInput = String.format("#%06X", hintTextColorSub and 0xFFFFFF)
                        },
                    )
                }
                SwitchPreference(
                    title = "副标题透明度跟随标题",
                    summary = "开启 = 标题透明度 ×82%，关闭可独立调节",
                    checked = hintTextAlphaSub < 0f,
                    onCheckedChange = { follow ->
                        hintTextAlphaSub = if (follow) -1f else previewSubAlphaEffective
                    },
                )
                if (hintTextAlphaSub >= 0f) {
                    SliderPreference(
                        title = "副标题透明度",
                        summary = "提示副标题不透明度 5%~100%",
                        valueText = "${Math.round(hintTextAlphaSub.coerceIn(0.05f, 1f) * 100)}%",
                        value = hintTextAlphaSub.coerceIn(0.05f, 1f),
                        valueRange = 0.05f..1f,
                        onValueChange = { hintTextAlphaSub = (Math.round(it * 20f) / 20f).coerceIn(0.05f, 1f) },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Text(
                    text = "质感与排版",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                SliderPreference(
                    title = "文字阴影",
                    summary = "黑色柔光投影，弱壁纸背景下提升可读性；半径 = 强度/20×4px",
                    valueText = if (hintTextShadow < 0.5f) "关闭" else "${hintTextShadow.toInt()}",
                    value = hintTextShadow,
                    valueRange = 0f..100f,
                    onValueChange = { hintTextShadow = (Math.round(it / 5f) * 5f).coerceIn(0f, 100f) },
                )
                SliderPreference(
                    title = "文字描边",
                    summary = "深色对比描边，任意壁纸下都清晰；1dp 步进",
                    valueText = if (Math.round(hintTextStroke) == 0) "关闭" else "${Math.round(hintTextStroke)}dp",
                    value = Math.round(hintTextStroke).toFloat(),
                    valueRange = 0f..4f,
                    onValueChange = { hintTextStroke = Math.round(it).coerceIn(0, 4).toFloat() },
                )
                RadioButtonPreference(
                    title = "细体 (Light)",
                    summary = "提示标题使用细字重",
                    selected = hintTextWeight == 0,
                    onClick = { hintTextWeight = 0 },
                )
                RadioButtonPreference(
                    title = "常规 (Regular)",
                    summary = "提示标题使用系统常规字重",
                    selected = hintTextWeight == 1,
                    onClick = { hintTextWeight = 1 },
                )
                RadioButtonPreference(
                    title = "中黑 (Medium)",
                    summary = "提示标题使用中黑字重",
                    selected = hintTextWeight == 2,
                    onClick = { hintTextWeight = 2 },
                )
                RadioButtonPreference(
                    title = "粗体 (Bold)",
                    summary = "提示标题粗体（默认）",
                    selected = hintTextWeight == 3,
                    onClick = { hintTextWeight = 3 },
                )
                SliderPreference(
                    title = "字间距",
                    summary = "提示文字字间距 -2~+6（×0.05em），字母文案调宽更精致",
                    valueText = String.format("%+.1f", hintTextLetterSpacing),
                    value = hintTextLetterSpacing,
                    valueRange = -2f..6f,
                    onValueChange = { hintTextLetterSpacing = (Math.round(it * 2f) / 2f).coerceIn(-2f, 6f) },
                )

                Spacer(Modifier.height(10.dp))
            }
        }
        }

        // ── 启动选项类型选择 ──
        item(key = "action_type_title") {
            CollapsibleSectionTitle("启动选项与执行动作", isOpen("action_type")) { toggle("action_type") }
        }
        if (isOpen("action_type")) {
        item(key = "action_type_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                RadioButtonPreference(
                    title = "唤醒小爱 (XIAOAI)",
                    summary = "直接唤醒 HyperOS 小爱语音助手",
                    selected = actionType == ShortcutItem.ACTION_XIAOAI,
                    onClick = { actionType = ShortcutItem.ACTION_XIAOAI },
                )
                RadioButtonPreference(
                    title = "静音模式 (MUTE)",
                    summary = "快速切换系统全局静音与响铃模式",
                    selected = actionType == ShortcutItem.ACTION_MUTE,
                    onClick = { actionType = ShortcutItem.ACTION_MUTE },
                )
                RadioButtonPreference(
                    title = "勿扰模式 (DND)",
                    summary = "快速开启或退出静音防打扰模式",
                    selected = actionType == ShortcutItem.ACTION_DND,
                    onClick = { actionType = ShortcutItem.ACTION_DND },
                )
                RadioButtonPreference(
                    title = "开关手电筒 (FLASHLIGHT)",
                    summary = "直接切换手机后置 LED 手电筒",
                    selected = actionType == ShortcutItem.ACTION_FLASHLIGHT,
                    onClick = { actionType = ShortcutItem.ACTION_FLASHLIGHT },
                )
                RadioButtonPreference(
                    title = "街拍相机 (CAMERA)",
                    summary = "极速启动原生系统相机抓拍",
                    selected = actionType == ShortcutItem.ACTION_CAMERA,
                    onClick = { actionType = ShortcutItem.ACTION_CAMERA },
                )
                RadioButtonPreference(
                    title = "打开应用 (APP)",
                    summary = "启动手机安装的目标应用程序",
                    selected = actionType == ShortcutItem.ACTION_APP,
                    onClick = { actionType = ShortcutItem.ACTION_APP },
                )
                RadioButtonPreference(
                    title = "深度链接 / Intent (INTENT_URI)",
                    summary = "调起扫一扫、付款码等特定 Intent URI",
                    selected = actionType == ShortcutItem.ACTION_INTENT_URI,
                    onClick = { actionType = ShortcutItem.ACTION_INTENT_URI },
                )
                RadioButtonPreference(
                    title = "启动 Activity (ACTIVITY)",
                    summary = "结构化启动任意 Activity",
                    selected = actionType == ShortcutItem.ACTION_ACTIVITY,
                    onClick = { actionType = ShortcutItem.ACTION_ACTIVITY },
                )
                RadioButtonPreference(
                    title = "Shell 指令 (SHELL)",
                    summary = "执行终端 Shell 脚本命令",
                    selected = actionType == ShortcutItem.ACTION_SHELL,
                    onClick = { actionType = ShortcutItem.ACTION_SHELL },
                )
                RadioButtonPreference(
                    title = "发送广播 (BROADCAST)",
                    summary = "向系统分发广播 Action",
                    selected = actionType == ShortcutItem.ACTION_BROADCAST,
                    onClick = { actionType = ShortcutItem.ACTION_BROADCAST },
                )
            }
        }
        }

        // ── 启动用户选择（APP / INTENT_URI / ACTIVITY 生效；0 = 主空间，>0 跨用户启动）──
        if (actionType == ShortcutItem.ACTION_APP || actionType == ShortcutItem.ACTION_INTENT_URI ||
            actionType == ShortcutItem.ACTION_ACTIVITY) {
            val customUserSelected = pickedUser > 0 && userOptions.none { it.id == pickedUser }
            item(key = "target_user_title") {
                CollapsibleSectionTitle("启动用户（应用双开 / 分身）", isOpen("target_user")) { toggle("target_user") }
            }
            if (isOpen("target_user")) {
            item(key = "target_user_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    RadioButtonPreference(
                        title = "当前用户 / 主空间（默认）",
                        summary = "在默认用户空间中启动，不跨用户",
                        selected = pickedUser <= 0,
                        onClick = { pickedUser = 0 },
                    )
                    userOptions.filter { it.id > 0 }.forEach { u ->
                        RadioButtonPreference(
                            title = "${u.id} · ${u.name}",
                            summary = "以 user ${u.id} 身份跨用户启动（该用户内需已安装/双开目标应用）",
                            selected = pickedUser == u.id,
                            onClick = { pickedUser = u.id },
                        )
                    }
                    RadioButtonPreference(
                        title = "自定义用户 ID",
                        summary = "手动输入其他 Android 用户 ID（user > 0）",
                        selected = customUserSelected,
                        onClick = {
                            pickedUser = customUserText.trim().toIntOrNull()
                                ?.takeIf { v -> v > 0 && userOptions.none { it.id == v } } ?: 10
                        },
                    )
                    if (customUserSelected) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            TextField(
                                value = customUserText,
                                onValueChange = { text ->
                                    customUserText = text
                                    text.trim().toIntOrNull()?.let { v -> if (v > 0) pickedUser = v }
                                },
                                label = "目标用户 ID（例如 10）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            }
        }

        // ── 动作参数详情填写 ──
        val isNoPayloadAction = actionType == ShortcutItem.ACTION_FLASHLIGHT ||
            actionType == ShortcutItem.ACTION_XIAOAI ||
            actionType == ShortcutItem.ACTION_MUTE ||
            actionType == ShortcutItem.ACTION_DND ||
            actionType == ShortcutItem.ACTION_CAMERA

        if (!isNoPayloadAction) {
            item(key = "payload_title") {
                CollapsibleSectionTitle("动作参数详情 (Payload)", isOpen("payload")) { toggle("payload") }
            }
            if (isOpen("payload")) {
            item(key = "payload_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    if (actionType == ShortcutItem.ACTION_ACTIVITY) {
                        // ── 结构化「启动 Activity」编辑器（组件 / Data / Extra / Action / NEW_TASK / Root）──
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column {
                                TextField(
                                    value = actComponent,
                                    onValueChange = { actComponent = it },
                                    label = "目标组件（仅类名，可留空）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = actPackage,
                                    onValueChange = { actPackage = it },
                                    label = "目标包名（组件填类名时需配合填写，也可单独按包名启动，可选）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    text = "从已安装应用选取活动",
                                    onClick = { showActivityPicker = true },
                                    modifier = Modifier.align(Alignment.End),
                                )
                                if (!actUseRoot) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "附加 Action（点预设快速填入，或在下方自定义）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    listOf(
                                        "无" to "",
                                        "MAIN" to "android.intent.action.MAIN",
                                        "VIEW" to "android.intent.action.VIEW",
                                        "DIAL" to "android.intent.action.DIAL",
                                        "SEND" to "android.intent.action.SEND",
                                        "EDIT" to "android.intent.action.EDIT",
                                        "PICK" to "android.intent.action.PICK",
                                        "SEARCH" to "android.intent.action.SEARCH",
                                        "SETTINGS" to "android.settings.SETTINGS",
                                    ).forEach { (label, value) ->
                                        TextButton(text = label, onClick = { actAction = value })
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextField(
                                    value = actAction,
                                    onValueChange = { actAction = it },
                                    label = "自定义 Action 字符串（可留空）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = actData,
                                    onValueChange = { actData = it },
                                    label = "附加 Data URI（可选，例如 https:// / tel: / weixin://）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "附加 Extra（点类型按钮切换 string/int/long/bool/float）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(6.dp))
                                actExtras.forEachIndexed { i, e ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        TextField(
                                            value = e.key,
                                            onValueChange = { v -> actExtras = actExtras.toMutableList().also { it[i] = e.copy(key = v) } },
                                            label = "Key",
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = e.type,
                                            onClick = { actExtras = actExtras.toMutableList().also { it[i] = e.copy(type = nextExtraType(e.type)) } },
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                        TextField(
                                            value = e.value,
                                            onValueChange = { v -> actExtras = actExtras.toMutableList().also { it[i] = e.copy(value = v) } },
                                            label = "Value",
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            text = "✕",
                                            onClick = { actExtras = actExtras.toMutableList().also { it.removeAt(i) } },
                                        )
                                    }
                                }
                                TextButton(
                                    text = "＋ 添加 Extra",
                                    onClick = { actExtras = actExtras + ActivityExtraRow() },
                                )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "使用 Root 打开（su）",
                            summary = "经 root（桥接 su）执行 am start -n 拉起，可绕过组件未导出/权限限制，并支持与「启动用户」组合",
                            checked = actUseRoot,
                            onCheckedChange = { actUseRoot = it },
                        )
                        if (actUseRoot) {
                            Text(
                                text = "Root 模式仅使用 目标组件 / 目标包名（am start -n），Action / Data / Extra / NEW_TASK 不参与执行（已填写内容会保留）",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            SwitchPreference(
                                title = "Intent.FLAG_ACTIVITY_NEW_TASK",
                                summary = "在新任务栈中启动 Activity（从指纹面板/SystemUI 启动时通常必须开启）",
                                checked = actNewTask,
                                onCheckedChange = { actNewTask = it },
                            )
                        }
                    } else {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column {
                            val payloadSingleLine = actionType == ShortcutItem.ACTION_APP || actionType == ShortcutItem.ACTION_BROADCAST
                            // TextField 内部用 rememberSaveable 保存滚动位置（含横/纵方向），
                            // 当 payload 卡片被移出再移入 LazyColumn 且 singleLine 发生变化时，
                            // 恢复出的滚动方向与新模式不一致会抛出 IllegalArgumentException 闪退。
                            // 以 singleLine 作为 key，让单行/多行模式各自持有独立的保存状态。
                            key(payloadSingleLine) {
                                TextField(
                                    value = actionPayload,
                                    onValueChange = { actionPayload = it },
                                    label = when (actionType) {
                                        ShortcutItem.ACTION_APP -> "应用包名（例如 com.tencent.mm）"
                                        ShortcutItem.ACTION_INTENT_URI -> "Intent URI 字符串"
                                        ShortcutItem.ACTION_SHELL -> "Shell 脚本命令"
                                        ShortcutItem.ACTION_BROADCAST -> "广播 Action"
                                        else -> "参数详情"
                                    },
                                    singleLine = payloadSingleLine,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (actionType == ShortcutItem.ACTION_APP) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    text = "从已安装应用选取",
                                    onClick = {
                                        showAppPicker = true
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }
                    }
                    if (actionType == ShortcutItem.ACTION_SHELL) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "以 Root 权限执行（su）",
                            summary = "开启后命令经 su 以 root 身份执行，可写受保护的系统设置；关闭则以普通 shell 身份执行",
                            checked = shellAsRoot,
                            onCheckedChange = { shellAsRoot = it },
                        )
                    }
                    }
                }
            }
            }
        }

        // ── 保存与删除按钮 ──
        item(key = "confirm_buttons") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = if (isNew) "确认添加" else "保存修改",
                        onClick = {
                            val newItem = buildSavedItem()
                            if (isNew) {
                                // 新建：保存后退出编辑器，回到快捷方式列表
                                onSave(newItem, true)
                            } else {
                                // 编辑：保存后留在编辑界面继续调整（不自动退出）
                                savedBaseline = newItem
                                onSave(newItem, false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    if (!isNew) {
                        TextButton(
                            text = "删除此快捷方式",
                            onClick = {
                                onDelete(initialItem)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    // ── 应用选择弹窗 ──
    if (showAppPicker) {
        AppListPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelectApp = { pkg, appName ->
                if (actionType == ShortcutItem.ACTION_APP && actionPayload.isBlank()) {
                    actionPayload = pkg
                }
                if (title.isBlank() || title == "新快捷方式") {
                    title = appName
                }
                iconPackage = pkg
                iconGlyph = ""
                showAppPicker = false
            },
        )
    }

    // ── Activity 选择弹窗（应用 → 活动列表两级，点活动直接填参）──
    if (showActivityPicker) {
        ActivityPickerDialog(
            onDismiss = { showActivityPicker = false },
            onPick = { pkg, cls, appName ->
                actComponent = cls
                actPackage = pkg
                if (title.isBlank() || title == "新快捷方式") {
                    title = appName
                }
                iconPackage = pkg
                iconGlyph = ""
                showActivityPicker = false
            },
        )
    }

    // ── Miuix 图标选择弹窗 ──
    if (showMiuixIconPicker) {
        MiuixIconPickerDialog(
            currentIconWithWeight = iconGlyph,
            onDismiss = { showMiuixIconPicker = false },
            onSelectIcon = { glyphKey ->
                iconGlyph = glyphKey
                iconPackage = ""
                showMiuixIconPicker = false
            },
        )
    }

    // ── Remix Icon 图标选择弹窗 ──
    if (showRemixIconPicker) {
        RemixIconPickerDialog(
            currentIconKey = iconGlyph,
            onDismiss = { showRemixIconPicker = false },
            onSelectIcon = { glyphKey ->
                iconGlyph = glyphKey
                iconPackage = ""
                showRemixIconPicker = false
            },
        )
    }

    // ── 退出时未保存确认弹窗（MIUIX 质感弹窗） ──
    if (showUnsavedDialog) {
        Dialog(
            onDismissRequest = { showUnsavedDialog = false },
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
                        text = "保存修改？",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isNew) "您刚刚创建的快捷方式尚未保存，是否保存并生效？" else "当前快捷方式的参数已发生更改，退出前是否保存？",
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
                            text = "放弃",
                            onClick = {
                                showUnsavedDialog = false
                                onExitWithoutSave()
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            text = "保存并退出",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                showUnsavedDialog = false
                                // 保存并退出：共用 buildSavedItem()（含 userId/shellUseRoot/ACTIVITY payload/URI 标记同步）
                                onSave(buildSavedItem(), true)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// 预选动作列表项数据与弹窗
// ─────────────────────────────────────────────────────────────────────

data class PresetAction(
    val title: String,
    val subtitle: String,
    val actionType: String,
    val actionPayload: String = "",
    val userId: Int = 0,
    val shellUseRoot: Boolean = false,
    val iconGlyph: String = "",
    val iconPackage: String = "",
    val tintColor: Int = 0xFF2A82E4.toInt(),
    val glyphPadEnabled: Boolean = true,
    val glyphPadColor: Int = 0xFF2A82E4.toInt(),
    val glyphPadMode: Int = 1,
    val tag: String = "",
)

val PRESET_ACTIONS = listOf(
    PresetAction(
        title = "自定义快捷方式",
        subtitle = "自由配置目标应用、自定义 Intent 或 Shell",
        actionType = ShortcutItem.ACTION_APP,
        iconGlyph = "remix:sparkling-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "自定义",
    ),
    PresetAction(
        title = "唤醒小爱",
        subtitle = "一键唤醒语音助手与小爱同学",
        actionType = ShortcutItem.ACTION_XIAOAI,
        iconGlyph = "remix:mic-fill",
        tintColor = 0xFF1485EE.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1485EE.toInt(),
        glyphPadMode = 1,
        tag = "系统助手",
    ),
    PresetAction(
        title = "静音模式",
        subtitle = "快速切换响铃与静音震动",
        actionType = ShortcutItem.ACTION_MUTE,
        iconGlyph = "remix:volume-mute-fill",
        tintColor = 0xFFFA8C16.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFA8C16.toInt(),
        glyphPadMode = 1,
        tag = "系统开关",
    ),
    PresetAction(
        title = "勿扰模式",
        subtitle = "快速开启或退出静音勿扰模式",
        actionType = ShortcutItem.ACTION_DND,
        iconGlyph = "remix:notification-3-fill",
        tintColor = 0xFF722ED1.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF722ED1.toInt(),
        glyphPadMode = 1,
        tag = "系统开关",
    ),
    PresetAction(
        title = "强光手电筒",
        subtitle = "开关手机后置 LED 强光照明",
        actionType = ShortcutItem.ACTION_FLASHLIGHT,
        iconGlyph = "remix:flashlight-fill",
        tintColor = 0xFFFAAD14.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFAAD14.toInt(),
        glyphPadMode = 1,
        tag = "常用工具",
    ),
    PresetAction(
        title = "街拍相机",
        subtitle = "秒级直达系统原生相机抓拍界面",
        actionType = ShortcutItem.ACTION_CAMERA,
        iconGlyph = "remix:camera-fill",
        tintColor = 0xFFFF4D4F.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFF4D4F.toInt(),
        glyphPadMode = 1,
        tag = "系统相机",
    ),
    PresetAction(
        title = "微信扫一扫",
        subtitle = "直接唤起微信官方扫码镜头",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.scanner.ui.BaseScanUI;end",
        // userId 默认 0 = 主空间；要启动应用双开/分身（XSpace user 999）在编辑界面「启动用户」里选择
        userId = 0,
        iconGlyph = "remix:qr-scan-2-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "快捷扫码",
    ),
    PresetAction(
        title = "微信付款码",
        subtitle = "出示收银台向商家付款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x10000000;component=com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI;i.key_entry_scene=2;end",
        iconGlyph = "remix:qr-code-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "微信收款码",
        subtitle = "快速出示面对面二维码收款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.collect.ui.CollectMainUI;end",
        iconGlyph = "remix:money-cny-circle-line",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "微信朋友圈",
        subtitle = "直接打开微信朋友圈主页",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x14000000;component=com.tencent.mm/.plugin.sns.ui.SnsTimeLineUI;B.sns_timeline_NeedFirstLoadint=true;end",
        iconGlyph = "remix:image-2-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "社交娱乐",
    ),
    PresetAction(
        title = "微信名片",
        subtitle = "展示微信个人二维码名片",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x14000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.setting.ui.setting.SelfQRCodeUI;end",
        iconGlyph = "remix:user-3-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "社交娱乐",
    ),
    PresetAction(
        title = "微信群收款",
        subtitle = "快速发起微信AA分账收款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.aa.ui.AAEntranceUI;end",
        iconGlyph = "remix:money-cny-circle-fill",
        tintColor = 0xFF07C160.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF07C160.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝扫一扫",
        subtitle = "快速识别二维码或扫码骑行",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=10000007&sourceId=xiaomiLeft&source=shortcut&showOthers=true&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:qr-scan-2-line",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝付款码",
        subtitle = "直接打开支付宝付款条码",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000056&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:qr-code-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "支付宝收款码",
        subtitle = "快速展示支付宝面对面二维码收钱",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000123&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:money-cny-circle-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "乘车码",
        subtitle = "地铁与公交交通一卡通直接刷码",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=200011235&source=xiaomiLeft&chInfo=ch_xiaomiLeft",
        iconGlyph = "remix:subway-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "绿色出行",
    ),
    PresetAction(
        title = "支付宝市民中心",
        subtitle = "政务办事、社保医保公积金",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000178&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:shield-check-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "支付宝生活缴费",
        subtitle = "水费、电费、燃气费便民缴费",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "alipays://platformapi/startapp?appId=20000193&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
        iconGlyph = "remix:coupon-3-fill",
        tintColor = 0xFF1677FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1677FF.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "系统扫一扫",
        subtitle = "小米系统原生扫码器",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=0;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:qr-scan-2-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "扫文档",
        subtitle = "小米扫一扫快速提取文档",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=5;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:sticky-note-fill",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "扫一扫翻译",
        subtitle = "小米扫一扫实时拍照翻译",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.scanner;launchFlags=0x10000000;component=com.xiaomi.scanner/.app.ScanActivity;i.extra_intent_module_index=2;S.fromApp=com.miui.personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:translate-2",
        tintColor = 0xFF3482FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF3482FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "垃圾清理",
        subtitle = "安全中心一键扫描垃圾清理",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=miui.intent.action.GARBAGE_CLEANUP;category=android.intent.category.DEFAULT;launchFlags=0x10000000;component=com.miui.cleanmaster/com.miui.optimizecenter.MainActivity;end",
        iconGlyph = "remix:delete-bin-6-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "科学计算器",
        subtitle = "小米计算器高级科学换算",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;launchFlags=0x10080000;component=com.miui.calculator/.cal.AllInOneCalculatorActivity;B.extra_is_scientific=true;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:calculator-fill",
        tintColor = 0xFF13C2C2.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF13C2C2.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "汇率计算",
        subtitle = "即时多国汇率实时换算",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.calculator.action.CURRENCY;launchFlags=0x10080000;component=com.miui.calculator/.convert.CurrencyActivity;S.external_jump_type=personalassistant;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:exchange-funds-fill",
        tintColor = 0xFF13C2C2.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF13C2C2.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "新的录音",
        subtitle = "快速开启系统高保真录音",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=mi.quicksearch.intent.action.RETRIEVE;launchFlags=0x10000000;component=com.android.soundrecorder/.SoundRecorder;S.miref=PersonalAssistant_QuickStart;end",
        iconGlyph = "remix:disc-fill",
        tintColor = 0xFFFAAD14.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFAAD14.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "新建日程",
        subtitle = "日历快速创建待办与活动",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=android.intent.action.INSERT;category=android.intent.category.DEFAULT;type=vnd.android.cursor.item/event;package=com.android.calendar;end",
        iconGlyph = "remix:calendar-event-fill",
        tintColor = 0xFF1890FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1890FF.toInt(),
        glyphPadMode = 1,
        tag = "实用工具",
    ),
    PresetAction(
        title = "Mi Pay",
        subtitle = "小米智能卡银行卡刷卡界面",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;i.card_group_id=2;end",
        iconGlyph = "remix:bank-card-fill",
        tintColor = 0xFFFA541C.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFA541C.toInt(),
        glyphPadMode = 1,
        tag = "移动支付",
    ),
    PresetAction(
        title = "交通卡",
        subtitle = "刷公交地铁一卡通卡面",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;S.event_source=shortcut_of_trans_card;end",
        iconGlyph = "remix:subway-fill",
        tintColor = 0xFF1890FF.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF1890FF.toInt(),
        glyphPadMode = 1,
        tag = "绿色出行",
    ),
    PresetAction(
        title = "门卡",
        subtitle = "快速调出小区/门禁实体模拟卡",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "intent:#Intent;action=com.miui.intent.action.DOUBLE_CLICK;launchFlags=0x10000000;i.card_group_id=3;end",
        iconGlyph = "remix:lock-unlock-fill",
        tintColor = 0xFF52C41A.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFF52C41A.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
    PresetAction(
        title = "淘宝拍立淘",
        subtitle = "淘宝拍照图像识物查同款",
        actionType = ShortcutItem.ACTION_INTENT_URI,
        actionPayload = "taobao://h5.m.taobao.com/tusou/capture.html?pssource=xiaomi&bc_fl_src=growth_dhh_2200803434487_android-xiaomikuaijie-1069565676-xiaomikuaijie",
        iconGlyph = "remix:shopping-bag-3-fill",
        tintColor = 0xFFFF5000.toInt(),
        glyphPadEnabled = true,
        glyphPadColor = 0xFFFF5000.toInt(),
        glyphPadMode = 1,
        tag = "生活服务",
    ),
)

@Composable
fun PresetActionPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (PresetAction) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(20.dp),
            ) {
                Text(
                    text = "新建快捷方式",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "提供丰富预选常用动作，轻触即可自动填充配置",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PRESET_ACTIONS) { action ->
                        val vector = remember(action.iconGlyph) {
                            if (RemixIconRegistry.isRemixIcon(action.iconGlyph)) {
                                RemixIconRegistry.find(action.iconGlyph)
                            } else {
                                MiuixIconRegistry.find(action.iconGlyph)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .clickable { onSelect(action) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(action.glyphPadColor).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (vector != null) {
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = null,
                                        tint = Color(action.tintColor),
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text("⚡", fontSize = 16.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = action.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    if (action.tag.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = action.tag,
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = action.subtitle,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                "选用",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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

// ─────────────────────────────────────────────────────────────────────
// 应用选择弹窗组件
// ─────────────────────────────────────────────────────────────────────

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: android.graphics.drawable.Drawable?,
)

@Composable
fun AppListPickerDialog(
    onDismiss: () -> Unit,
    onSelectApp: (pkg: String, name: String) -> Unit,
) {
    val ctx = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部, 1=用户应用, 2=系统应用
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = installedPackages.mapNotNull { pi ->
                val appInfo = pi.applicationInfo ?: return@mapNotNull null
                val pkg = pi.packageName
                val name = appInfo.loadLabel(pm).toString().ifBlank { pkg }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (t: Throwable) {
                    null
                }
                AppInfoItem(pkg, name, isSys, icon)
            }.distinctBy { it.packageName }
                .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
            withContext(Dispatchers.Main) {
                allApps = list
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, filterTab, allApps) {
        var base = when (filterTab) {
            1 -> allApps.filter { !it.isSystem }
            2 -> allApps.filter { it.isSystem }
            else -> allApps
        }
        if (searchQuery.isNotBlank()) {
            base = base.filter {
                it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择应用程序",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "选取应用作为快捷图标或启动目标",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TabRow(
                    tabs = listOf("全部 (${allApps.size})", "用户应用", "系统应用"),
                    selectedTabIndex = filterTab,
                    onTabSelected = { filterTab = it },
                    colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索应用名称或包名...",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在加载已安装应用...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(filteredApps.size, key = { filteredApps[it].packageName }) { idx ->
                            val app = filteredApps[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app.packageName, app.appName) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) {
                                    val bmp = remember(app.packageName) {
                                        val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                        val c = android.graphics.Canvas(b)
                                        app.icon.setBounds(0, 0, 96, 96)
                                        app.icon.draw(c)
                                        b.toComposeImageBitmap()
                                    }
                                    Image(
                                        painter = BitmapPainter(bmp),
                                        contentDescription = app.appName,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MiuixTheme.colorScheme.surfaceVariant),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (app.isSystem) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "系统",
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

data class ActivityInfoItem(
    val className: String,
    val exported: Boolean,
    val label: String,
)

/** 应用 → 活动列表两级选择器：点活动直接填入 目标组件/目标包名 */
@Composable
fun ActivityPickerDialog(
    onDismiss: () -> Unit,
    onPick: (pkg: String, cls: String, appName: String) -> Unit,
) {
    val ctx = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    var appSearch by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=全部, 1=用户应用, 2=系统应用
    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var activities by remember { mutableStateOf<List<ActivityInfoItem>>(emptyList()) }
    var actLoading by remember { mutableStateOf(false) }
    var actSearch by remember { mutableStateOf("") }
    var actFilter by remember { mutableIntStateOf(0) } // 0=全部, 1=已导出, 2=未导出

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = installedPackages.mapNotNull { pi ->
                val appInfo = pi.applicationInfo ?: return@mapNotNull null
                val pkg = pi.packageName
                val name = appInfo.loadLabel(pm).toString().ifBlank { pkg }
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (t: Throwable) {
                    null
                }
                AppInfoItem(pkg, name, isSys, icon)
            }.distinctBy { it.packageName }
                .sortedWith(compareBy({ it.appName.lowercase() }, { it.packageName }))
            withContext(Dispatchers.Main) {
                allApps = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedApp) {
        val app = selectedApp ?: return@LaunchedEffect
        actLoading = true
        activities = emptyList()
        withContext(Dispatchers.IO) {
            val list = try {
                val pm = ctx.packageManager
                @Suppress("DEPRECATION")
                val acts = pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities ?: emptyArray()
                acts.map { ai ->
                    val label = try {
                        ai.loadLabel(pm).toString()
                    } catch (t: Throwable) {
                        ""
                    }
                    ActivityInfoItem(ai.name, ai.exported, label)
                }.sortedBy { it.className }
            } catch (t: Throwable) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                activities = list
                actLoading = false
            }
        }
    }

    val filteredApps = remember(appSearch, filterTab, allApps) {
        var base = when (filterTab) {
            1 -> allApps.filter { !it.isSystem }
            2 -> allApps.filter { it.isSystem }
            else -> allApps
        }
        if (appSearch.isNotBlank()) {
            base = base.filter {
                it.appName.contains(appSearch, ignoreCase = true) || it.packageName.contains(appSearch, ignoreCase = true)
            }
        }
        base
    }

    val filteredActs = remember(actSearch, actFilter, activities) {
        var base = when (actFilter) {
            1 -> activities.filter { it.exported }
            2 -> activities.filter { !it.exported }
            else -> activities
        }
        if (actSearch.isNotBlank()) {
            base = base.filter {
                it.className.contains(actSearch, ignoreCase = true) ||
                    it.label.contains(actSearch, ignoreCase = true)
            }
        }
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(20.dp),
            ) {
                val app = selectedApp
                if (app == null) {
                    Text(
                        text = "选择应用程序",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "点击应用查看其活动列表，点活动直接填入组件参数",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TabRow(
                        tabs = listOf("全部 (${allApps.size})", "用户应用", "系统应用"),
                        selectedTabIndex = filterTab,
                        onTabSelected = { filterTab = it },
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    TextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = "搜索应用名称或包名...",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("正在加载已安装应用...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            items(filteredApps.size, key = { "app-" + filteredApps[it].packageName }) { idx ->
                                val item = filteredApps[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedApp = item }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (item.icon != null) {
                                        val bmp = remember(item.packageName) {
                                            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                                            val c = android.graphics.Canvas(b)
                                            item.icon.setBounds(0, 0, 96, 96)
                                            item.icon.draw(c)
                                            b.toComposeImageBitmap()
                                        }
                                        Image(
                                            painter = BitmapPainter(bmp),
                                            contentDescription = item.appName,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MiuixTheme.colorScheme.surfaceVariant),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.appName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (item.isSystem) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "系统",
                                                    fontSize = 10.sp,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .background(
                                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                            RoundedCornerShape(4.dp),
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = item.packageName,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            text = "← 返回",
                            onClick = {
                                selectedApp = null
                                activities = emptyList()
                                actSearch = ""
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = app.appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "点活动直接填入 目标组件 / 目标包名（共 ${activities.size} 个活动）",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TabRow(
                        tabs = listOf("全部 (${activities.size})", "已导出", "未导出"),
                        selectedTabIndex = actFilter,
                        onTabSelected = { actFilter = it },
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    TextField(
                        value = actSearch,
                        onValueChange = { actSearch = it },
                        label = "搜索活动类名或标签...",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    if (actLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("正在加载活动列表...", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else if (activities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("该应用未声明任何活动（或无法读取）", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                        ) {
                            items(filteredActs.size, key = { "act-" + filteredActs[it].className }) { idx ->
                                val act = filteredActs[idx]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(app.packageName, act.className, app.appName) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = act.className,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (act.exported) "导出" else "未导出",
                                            fontSize = 10.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .background(
                                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        )
                                    }
                                    if (act.label.isNotBlank()) {
                                        Text(
                                            text = act.label,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Miuix 矢量图标选择弹窗组件（完整 125+ 图标库 + 5 档粗细切换 + 搜索）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun MiuixIconPickerDialog(
    currentIconWithWeight: String,
    onDismiss: () -> Unit,
    onSelectIcon: (String) -> Unit,
) {
    val initialWeight = remember(currentIconWithWeight) {
        if (currentIconWithWeight.contains(":")) currentIconWithWeight.split(":")[1].trim() else "Regular"
    }
    val initialName = remember(currentIconWithWeight) {
        if (currentIconWithWeight.contains(":")) currentIconWithWeight.split(":")[0].trim() else currentIconWithWeight.trim()
    }

    var selectedWeight by remember { mutableStateOf(initialWeight) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNames = remember(searchQuery) {
        if (searchQuery.isBlank()) MiuixIconRegistry.ICON_NAMES
        else MiuixIconRegistry.ICON_NAMES.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val weightIndex = remember(selectedWeight) {
        MiuixIconRegistry.WEIGHTS.indexOf(selectedWeight).coerceAtLeast(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择 Miuix 矢量图标",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "支持 125+ 完整官方矢量库，并可调节线条粗细",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 粗细字重切换器
                Text(
                    text = "图标粗细：$selectedWeight",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixIconRegistry.WEIGHTS.forEach { weight ->
                        val isSelected = weight == selectedWeight
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedWeight = weight }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = weight,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索图标名称（如 Scan, Search, Settings...）",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredNames, key = { it }) { name ->
                        val isSelected = name.equals(initialName, ignoreCase = true)
                        val vector = remember(name, selectedWeight) {
                            MiuixIconRegistry.find(name, selectedWeight)
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    val finalKey = "$name:$selectedWeight"
                                    onSelectIcon(finalKey)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (vector != null) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = name,
                                    tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Remix Icon 矢量图标选择弹窗组件（128款高频锁屏精选 + 分类标签 + 实时搜索）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun RemixIconPickerDialog(
    currentIconKey: String,
    onDismiss: () -> Unit,
    onSelectIcon: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }

    val categories = remember {
        listOf("全部", "核心高频", "系统控制", "支付卡证", "出行导航", "影音娱乐", "通讯工具")
    }

    val filteredIcons = remember(searchQuery, selectedCategory) {
        RemixIconRegistry.ALL_ICONS.filter { item ->
            val matchQuery = if (searchQuery.isBlank()) true else {
                item.desc.contains(searchQuery, ignoreCase = true) ||
                item.rawName.contains(searchQuery, ignoreCase = true)
            }
            val matchCat = when (selectedCategory) {
                "核心高频" -> listOf("flashlight", "qr-scan", "qr-code", "camera", "mic", "calculator", "search", "sticky-note").any { item.rawName.startsWith(it) }
                "系统控制" -> listOf("bluetooth", "wifi", "hotspot", "volume", "notification", "flight", "battery", "screenshot", "lock", "settings").any { item.rawName.startsWith(it) }
                "支付卡证" -> listOf("wallet", "bank-card", "contactless", "shield", "coupon", "shopping", "exchange", "money").any { item.rawName.startsWith(it) }
                "出行导航" -> listOf("bus", "subway", "map-pin", "navigation", "taxi", "riding", "car", "compass").any { item.rawName.startsWith(it) }
                "影音娱乐" -> listOf("music", "disc", "play", "pause", "speed", "headphone", "video", "film", "image", "book", "gamepad", "radio").any { item.rawName.startsWith(it) }
                "通讯工具" -> listOf("phone", "chat", "message", "mail", "alarm", "timer", "calendar", "translate", "earth", "share", "user", "heart", "star", "sparkling", "folder", "delete").any { item.rawName.startsWith(it) }
                else -> true
            }
            matchQuery && matchCat
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "选择 Remix Icon 快捷图标",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "精选 128 款最高频锁屏动作与硬件开关图标，支持纯白与底垫交融渲染",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 分类切换标签（横向滑动支持完整名称展示，杜绝挤压省略）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索图标名称或中文描述（如 扫一扫, 手电筒, 蓝牙...）",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredIcons, key = { it.key }) { item ->
                        val isSelected = currentIconKey == item.key || currentIconKey == item.rawName
                        val vector = remember(item.key) {
                            RemixIconRegistry.find(item.key)
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MiuixTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    onSelectIcon(item.key)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (vector != null) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = item.desc,
                                    tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.desc,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = item.rawName,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Page1：软件设置（主题入口 / 安全模式 / 关于）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    safetyTick: Int,
    onSafetyRefresh: () -> Unit,
    onOpenThemePage: () -> Unit,
    onOpenPermissionPage: () -> Unit,
    onResetDefaults: () -> Unit,
    onResetShortcuts: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 主题设置 ──
        item(key = "theme_title") { SmallTitle("主题") }
        item(key = "theme_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "主题设置",
                    summary = "深浅模式 · Monet 动态取色 · 悬浮底栏",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Theme,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onOpenThemePage,
                )
            }
        }

        // ── 权限检测 ──
        item(key = "permission_title") { SmallTitle("权限") }
        item(key = "permission_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "权限检测",
                    summary = "Root · 应用列表 · 振动 · 勿扰 · 存储 · LSPosed 作用域",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Scan,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onOpenPermissionPage,
                )
            }
        }

        // ── 安全模式与崩溃保护 ──
        item(key = "safety_title") { SmallTitle("安全模式与崩溃保护") }
        item(key = "safety_card") {
            SafetyCard(tick = safetyTick, onRequestRefresh = onSafetyRefresh)
        }

        item(key = "restart_systemui_card") {
            val ctx = LocalContext.current
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "\u91cd\u542f\u7cfb\u7edf\u754c\u9762",
                    summary = "\u91cd\u542f SystemUI \u4f7f\u914d\u7f6e\u7acb\u5373\u751f\u6548\uff08\u9700\u8981 Root \u6743\u9650\uff09",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { restartSystemUi(ctx) },
                )
            }
        }

        // ── 恢复默认参数与预设布局 ──
        item(key = "reset_defaults_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "恢复默认参数",
                    summary = "重置全部外观、几何、物理与等待时间参数为出厂默认值，但保留现有快捷项目",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onResetDefaults,
                )
                ArrowPreference(
                    title = "恢复预设布局",
                    summary = "重置快捷项目列表为预设",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Reset,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onResetShortcuts,
                )
            }
        }

        // ── 配置备份 ──
        item(key = "config_backup_title") { SmallTitle("配置备份") }
        item(key = "config_backup_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "导出配置",
                    summary = "把当前全部参数与快捷方式写成 JSON 文件，便于备份或换机迁移",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onExportConfig,
                )
                ArrowPreference(
                    title = "导入配置",
                    summary = "从 JSON 文件恢复参数与快捷方式；只覆盖文件里包含的项",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Import,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = onImportConfig,
                )
            }
        }

        // ── 关于 ──
        item(key = "about_title") { SmallTitle("关于") }
        item(key = "about_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                AboutLine("模块名称", "HyperOpen")
                AboutLine("作者", "青冥日月", url = "https://www.coolapk.com/u/3268816")
                AboutLine("Github", "HyperOpen", url = "https://github.com/qmryqmm/HyperOpen")
                AboutLine("当前版本", "v${io.github.qmryqmm.hyperopen.BuildConfig.VERSION_NAME}")
                AboutLine("功能简介", "指纹快捷面板自定义 · 图标与动效增强")
                AboutLine("使用方式", "指纹解锁时按压指纹传感器唤出快捷面板")
                AboutLine("生效条件", "LSPosed 激活模块并勾选 SystemUI 作用域，重启 SystemUI")
                AboutLine("UI 框架", "Miuix Compose 0.9.4 ")
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** root重启系统界面 (the system relaunches it automatically); toast on failure. */
fun restartSystemUi(ctx: android.content.Context) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    fun toast(msg: String) = handler.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    Thread {
        try {
            val p = ProcessBuilder("su", "-c", "pkill -f com.android.systemui || killall com.android.systemui")
                .redirectErrorStream(true)
                .start()
            if (p.waitFor() != 0) toast("\u91cd\u542f\u5931\u8d25\uff1a\u8bf7\u786e\u8ba4\u5df2\u6388\u4e88 Root \u6743\u9650")
        } catch (t: Throwable) {
            toast("\u91cd\u542f\u5931\u8d25\uff1a\u672a\u83b7\u53d6\u5230 Root \u6743\u9650")
        }
    }.start()
}
@Composable
fun AboutLine(label: String, value: String, url: String? = null) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (url != null) {
                    Modifier.clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {
                            Toast.makeText(ctx, "\u65e0\u6cd5\u6253\u5f00\u94fe\u63a5", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            modifier = Modifier.size(width = 84.dp, height = 20.dp),
        )
        Text(
            value,
            color = if (url != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun android.graphics.Bitmap.toComposeImageBitmap(): androidx.compose.ui.graphics.ImageBitmap = this.asImageBitmap()

// ─────────────────────────────────────────────────────────────────────
// 安全模式卡片
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SafetyCard(tick: Int, onRequestRefresh: () -> Unit) {
    val ctx = LocalContext.current

    val state = remember(tick) { SafetyStore.getState(ctx) }
    val safeMode = state.getBoolean("safe_mode", false)
    val hookEnabled = state.getBoolean("hook_enabled", true)
    val crashCount = state.getInt("crash_count", 0)
    val threshold = state.getInt("threshold", SafetyStore.CRASH_THRESHOLD)
    val total = state.getInt("total_crash", 0)
    val lastTs = state.getLong("last_crash_ts", 0L)
    val sessionHealthy = state.getBoolean("session_healthy", true)
    val log = state.getString("crash_log", "") ?: ""

    val statusText = when {
        safeMode -> "安全模式已激活 · Hook 已停用"
        !hookEnabled -> "Hook 总开关已关闭 · 不安装任何 Hook"
        else -> "运行正常 · Hook 已启用"
    }
    val statusColor = when {
        safeMode -> Color(0xFFE94634)
        !hookEnabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> Color(0xFF07C160)
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(statusText, color = statusColor, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            InfoLine("连续崩溃计数：$crashCount / $threshold")
            InfoLine("历史累计崩溃：$total 次")
            InfoLine("上次会话存活确认：${if (sessionHealthy) "已确认" else "未确认（疑似崩溃）"}")
            InfoLine(
                if (lastTs > 0L) {
                    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastTs))
                    "最近一次崩溃：$fmt"
                } else "最近一次崩溃：无",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "崩溃记录",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
            Text(
                if (log.isBlank()) "暂无崩溃记录。" else log,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = "退出安全模式",
                    onClick = {
                        SafetyStore.exitSafeMode(ctx)
                        Toast.makeText(ctx, "已退出安全模式，重启 SystemUI 后 Hook 恢复生效", Toast.LENGTH_LONG).show()
                        onRequestRefresh()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "清空崩溃记录",
                    onClick = {
                        SafetyStore.clearCrash(ctx)
                        Toast.makeText(ctx, "崩溃记录已清空", Toast.LENGTH_SHORT).show()
                        onRequestRefresh()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            SwitchPreference(
                title = "启用指纹快捷方式增强 (Hook 总开关)",
                checked = hookEnabled && !safeMode,
                onCheckedChange = { checked ->
                    SafetyStore.setHookEnabled(ctx, checked)
                    Toast.makeText(
                        ctx,
                        if (checked) "Hook 已启用，重启 SystemUI 后生效" else "Hook 已停用，重启 SystemUI 后生效",
                        Toast.LENGTH_LONG,
                    ).show()
                    onRequestRefresh()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "当 Hook 导致 SystemUI 在 5 分钟内连续崩溃 2 次时，模块会自动进入安全模式并停用全部 Hook，SystemUI 恢复原生指纹界面。修复配置后点「退出安全模式」即可重新启用。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
fun InfoLine(text: String) {
    Text(
        text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────
// 二级页：主题设置（深浅模式 / Monet / 悬浮底栏）
// ─────────────────────────────────────────────────────────────────────

@Composable
fun ThemeSettingsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    themeMode: Int, onThemeMode: (Int) -> Unit,
    monet: Boolean, onMonet: (Boolean) -> Unit,
    floatingNavBar: Boolean, onFloatingNavBar: (Boolean) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 深浅模式选择器 ──
        item(key = "theme_mode_title") { SmallTitle("深浅模式") }
        item(key = "theme_mode_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "跟随系统",
                    summary = "随系统深色模式设置自适应切换",
                    endActions = {
                        if (themeMode == ThemeMode.FOLLOW_SYSTEM) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.FOLLOW_SYSTEM) },
                )
                ArrowPreference(
                    title = "浅色模式",
                    summary = "始终使用明亮色彩",
                    endActions = {
                        if (themeMode == ThemeMode.LIGHT) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.LIGHT) },
                )
                ArrowPreference(
                    title = "深色模式",
                    summary = "始终使用暗色护眼色彩",
                    endActions = {
                        if (themeMode == ThemeMode.DARK) {
                            Text("✓", color = MiuixTheme.colorScheme.primary, fontSize = 16.sp)
                        }
                    },
                    onClick = { onThemeMode(ThemeMode.DARK) },
                )
            }
        }

        // ── Monet 动态取色 ──
        item(key = "monet_title") { SmallTitle("色彩风格") }
        item(key = "monet_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "启用 Monet 动态取色",
                    summary = "吸取系统壁纸主题色，与系统界面深度融合",
                    checked = monet,
                    onCheckedChange = onMonet,
                )
            }
        }

        // ── 外观效果 ──
        item(key = "appearance_title") { SmallTitle("导航栏样式") }
        item(key = "appearance_card") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "胶囊悬浮底栏",
                    checked = floatingNavBar,
                    onCheckedChange = onFloatingNavBar,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────
// 二级页：权限检测（Root / 应用列表 / 勿扰 / 存储 / LSPosed 作用域）
// ─────────────────────────────────────────────────────────────
@Composable
fun PermissionCheckPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    val ctx = LocalContext.current
    var detectTick by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    val status = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Boolean?, String>>() }
    val items = remember {
        listOf(
            Triple("root", "Root 权限", "用途：重启系统界面、SHELL/root 启动动作、跨用户启动兜底、锁屏壁纸读取兜底。建议授权。"),
            Triple("packages", "应用列表", "用途：快捷方式「从已安装应用选取」列表、应用图标获取与图标裁切缓存、APP 动作启动目标解析。"),
            Triple("dnd", "勿扰策略访问", "用途：快捷动作切换勿扰与响铃模式。本项仅反映本应用自身授权状态，有root无需授权"),
            Triple("storage", "所有文件访问", "用途：直接读取本地图片作为自定义图标；经系统相册 / 文件选择器选图时无需此权限。"),
            Triple("lsposed", "LSPosed 模块与作用域", "用途：模块需在 LSPosed 中启用并勾选作用域 com.android.systemui，否则锁屏快捷面板不生效；读取 LSPosed 数据校验依赖 Root。"),
        )
    }

    val permScope = androidx.compose.runtime.rememberCoroutineScope()
    // 从系统授权页返回后整页复检
    val permSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { detectTick++ }

    fun refreshKey(key: String, rootOk: Boolean) {
        permScope.launch {
            status[key] = withContext(Dispatchers.IO) {
                when (key) {
                    "root" -> detectRootAccess()
                    "packages" -> detectPackageVisibility(ctx)
                    "dnd" -> detectDndAccess(ctx)
                    "storage" -> detectStorageAccess()
                    else -> detectLsposedScope(ctx, rootOk)
                }
            }
        }
    }

    // 点击「未满足」卡片时尝试申请对应权限
    fun tryRequestPermission(key: String) {
        when (key) {
            "root" -> {
                status[key] = null to "正在请求 Root 授权…"
                Toast.makeText(ctx, "已唤起 Root 授权请求，请在弹窗中允许", Toast.LENGTH_SHORT).show()
                refreshKey("root", false)
            }
            "packages" -> Toast.makeText(
                ctx,
                "该权限随安装包声明授予，无法运行时申请；若应用列表偏少属系统限制",
                Toast.LENGTH_SHORT
            ).show()
            "dnd" -> runCatching {
                permSettingsLauncher.launch(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }.onFailure {
                Toast.makeText(ctx, "未找到勿扰授权设置页", Toast.LENGTH_SHORT).show()
            }
            "storage" -> {
                val intent = runCatching {
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                }.getOrElse { Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }
                runCatching { permSettingsLauncher.launch(intent) }.onFailure {
                    Toast.makeText(ctx, "未找到文件访问授权设置页", Toast.LENGTH_SHORT).show()
                }
            }
            else -> { // lsposed：打开 LSPosed 管理器，装不上则退回复检
                val managerIntent = listOf("org.lsposed.manager", "io.github.lsposed.manager")
                    .firstNotNullOfOrNull { pkg -> ctx.packageManager.getLaunchIntentForPackage(pkg) }
                if (managerIntent != null) {
                    Toast.makeText(ctx, "已打开 LSPosed 管理器，请确认模块已启用并勾选作用域", Toast.LENGTH_SHORT).show()
                    runCatching {
                        managerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(managerIntent)
                    }
                    status[key] = null to "已打开 LSPosed 管理器，切换回来后复检…"
                } else {
                    Toast.makeText(ctx, "未安装 LSPosed 管理器，改用 Root 复检", Toast.LENGTH_SHORT).show()
                    status[key] = null to "未检测到管理器，尝试 Root 复检…"
                }
                refreshKey(key, status["root"]?.first == true)
            }
        }
    }

    LaunchedEffect(detectTick) {
        running = true
        status.clear()
        items.forEach { (key, _, _) -> status[key] = null to "检测中…" }
        val root = withContext(Dispatchers.IO) { detectRootAccess() }
        status["root"] = root
        status["packages"] = withContext(Dispatchers.IO) { detectPackageVisibility(ctx) }
        status["dnd"] = withContext(Dispatchers.IO) { detectDndAccess(ctx) }
        status["storage"] = withContext(Dispatchers.IO) { detectStorageAccess() }
        status["lsposed"] = withContext(Dispatchers.IO) { detectLsposedScope(ctx, root.first == true) }
        running = false
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
        item(key = "perm_header") { SmallTitle("权限检测") }
        item(key = "perm_overview") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = if (running) {
                            "正在检测…（共 ${items.size} 项）"
                        } else {
                            val ok = items.count { status[it.first]?.first == true }
                            val no = items.count { status[it.first]?.first == false }
                            "共 ${items.size} 项：已满足 $ok 项 · 未满足 $no 项"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "绿色 ✓ 已满足 · 红色 ✗ 未满足（点击可尝试申请） · 黄色 … 检测中或需在 LSPosed 中人工确认",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "perm_refresh") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth(),
            ) {
                ArrowPreference(
                    title = "重新检测",
                    summary = "重新扫描全部权限状态",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Scan,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    onClick = { detectTick++ },
                )
            }
        }
        items.forEach { (key, itemTitle, purpose) ->
            item(key = "perm_$key") {
                val st = status[key]
                val color = when (st?.first) {
                    true -> Color(0xFF07C160)
                    false -> Color(0xFFE94634)
                    null -> Color(0xFFFF9933)
                }
                val label = when (st?.first) {
                    true -> "✓ 已满足"
                    false -> "✗ 未满足 · 点击申请"
                    null -> "… 检测中"
                }
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .clickable(enabled = st?.first == false) { tryRequestPermission(key) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = itemTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = color,
                            )
                        }
                        Text(
                            text = purpose,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = st?.second ?: "检测中…",
                            fontSize = 12.sp,
                            color = color.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── 权限检测执行器（全部跑在 IO 线程）：返回 (状态 true=满足 / false=不满足 / null=待确认, 说明文字) ──

private fun detectRootAccess(): Pair<Boolean?, String> {
    var process: Process? = null
    return try {
        process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        process.outputStream.close()
        val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false to "检测超时：首次使用需在弹窗中允许 Root，授权后点「重新检测」"
        } else {
            val out = process.inputStream.bufferedReader().readText().trim()
            if (process.exitValue() == 0 && out.contains("uid=0")) {
                true to out.lineSequence().first().trim()
            } else {
                val err = process.errorStream.bufferedReader().readText().trim()
                false to (err.ifBlank { "su 未授权或系统未 Root" }).lineSequence().first().take(70)
            }
        }
    } catch (e: Exception) {
        false to (e.message ?: "未找到 su（系统未 Root）")
    } finally {
        process?.destroy()
    }
}

private fun detectPackageVisibility(ctx: android.content.Context): Pair<Boolean?, String> {
    val declared = ctx.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED
    val count = try {
        ctx.packageManager.getInstalledApplications(0).size
    } catch (e: Exception) {
        -1
    }
    return when {
        declared && count >= 50 -> true to "已声明并授予；当前可见 $count 个应用"
        declared -> true to "已声明并授予；可见 $count 个应用（偏少，可能受系统限制）"
        count in 0..49 -> false to "未授权；仅可见 $count 个应用，应用选择器与图标加载会不全"
        else -> true to "可见 $count 个应用"
    }
}


private fun detectDndAccess(ctx: android.content.Context): Pair<Boolean?, String> {
    return try {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            true to "已授予勿扰策略访问"
        } else {
            false to "未授予勿扰策略访问（本项仅供参考，有root无需授权）"
        }
    } catch (e: Exception) {
        null to "检测失败：${e.message}"
    }
}

private fun detectStorageAccess(): Pair<Boolean?, String> {
    return try {
        if (android.os.Environment.isExternalStorageManager()) {
            true to "已授予所有文件访问权限"
        } else {
            false to "未授予（可选：经系统相册 / 文件选择器选图时不需要）"
        }
    } catch (e: Exception) {
        false to "检测失败：${e.message}"
    }
}

private fun detectLsposedScope(ctx: android.content.Context, hasRoot: Boolean): Pair<Boolean?, String> {
    val managerInstalled = listOf("org.lsposed.manager", "io.github.lsposed.manager").any { pkg ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    if (!hasRoot) {
        return if (managerInstalled) {
            null to "已装 LSPosed 管理器；读取作用域需 Root，通过 Root 检测后点「重新检测」"
        } else {
            false to "未检测到 LSPosed 管理器，且无 Root 无法校验作用域"
        }
    }
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "grep -r -l io.github.qmryqmm.hyperopen /data/adb/lspd 2>/dev/null"))
        p.outputStream.close()
        val finished = p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            null to "读取 LSPosed 数据超时，点「重新检测」重试"
        } else {
            val out = p.inputStream.bufferedReader().readText().trim()
            when {
                p.exitValue() == 0 && out.isNotEmpty() ->
                    true to "LSPosed 数据中找到本模块记录：${out.lineSequence().first().take(60)}"
                p.exitValue() == 0 ->
                    false to if (managerInstalled) "未找到本模块记录；请在 LSPosed 中启用模块并勾选作用域 com.android.systemui" else "未找到本模块记录，且未安装 LSPosed 管理器"
                else ->
                    false to "/data/adb/lspd 下未找到本模块（LSPosed 未安装或模块未启用）"
            }
        }
    } catch (e: Exception) {
        false to (e.message ?: "读取 LSPosed 数据失败")
    }
}
