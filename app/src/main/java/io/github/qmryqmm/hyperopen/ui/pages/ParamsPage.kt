package io.github.qmryqmm.hyperopen.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.layout.LayoutCapacity
import io.github.qmryqmm.hyperopen.ui.components.CollapsibleSectionTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 布局样式的展示名，用于分区标题摘要 */
private fun layoutNameOf(style: Int): String = when (style) {
    HyperOpenConfig.LAYOUT_HONEYCOMB -> "蜂窝阵列"
    HyperOpenConfig.LAYOUT_RING -> "空心圆环"
    HyperOpenConfig.LAYOUT_ARC -> "弧形扇面"
    HyperOpenConfig.LAYOUT_SYSTEM_STOCK -> "系统经典扇形"
    else -> "自定义布局"
}

/** 震动模式的展示名，用于分区标题摘要 */
private fun hapticModeNameOf(mode: Int): String = when (mode) {
    HyperOpenConfig.HAPTIC_MODE_STOCK -> "系统原生波形"
    HyperOpenConfig.HAPTIC_MODE_TICK -> "清脆短震"
    HyperOpenConfig.HAPTIC_MODE_CLICK -> "标准点按"
    HyperOpenConfig.HAPTIC_MODE_HEAVY -> "浑厚重按"
    HyperOpenConfig.HAPTIC_MODE_DOUBLE -> "双脉冲"
    else -> "自定义时长"
}

/**
 * 参数配置页面（Tab 0），按 4 页 Tab 分页：布局 / 外观 / 图标 / 震动。
 * 集中管理指纹快捷方式的材质风格（液态玻璃 / 高斯模糊）、几何排布（蜂窝 / 空心圆环 / 扇区 / 系统扇形）、
 * 顶部胶囊提示位置字号、鱼眼缩放动力学曲线与线性马达触觉反馈。
 */
@Composable
fun ParamsPage(
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    selectedTab: Int,
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
    stockSystemAnim: Boolean, onStockSystemAnim: (Boolean) -> Unit,
    stockSystemRegion: Boolean, onStockSystemRegion: (Boolean) -> Unit,
    ringCount: Float, onRingCount: (Float) -> Unit,
    ringSectorMode: Boolean, onRingSectorMode: (Boolean) -> Unit,
    sectorLineColor: Int, onSectorLineColor: (Int) -> Unit,
    sectorLineAlpha: Float, onSectorLineAlpha: (Float) -> Unit,
    sectorDashLength: Float, onSectorDashLength: (Float) -> Unit,
    sectorDashGap: Float, onSectorDashGap: (Float) -> Unit,
    sectorLineWidth: Float, onSectorLineWidth: (Float) -> Unit,
    maxScale: Float, onMaxScale: (Float) -> Unit,
    influenceRadius: Float, onInfluenceRadius: (Float) -> Unit,
    iconAlpha: Float, onIconAlpha: (Float) -> Unit,
    fisheyeZoomAlpha: Float, onFisheyeZoomAlpha: (Float) -> Unit,
    fisheyeOtherAlpha: Float, onFisheyeOtherAlpha: (Float) -> Unit,
    iconCornerRadius: Float, onIconCornerRadius: (Float) -> Unit,
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
    hapticSlideEnabled: Boolean, onHapticSlideEnabled: (Boolean) -> Unit,
    hapticSlideStrength: Float, onHapticSlideStrength: (Float) -> Unit,
    hapticSlideDurationMs: Float, onHapticSlideDurationMs: (Float) -> Unit,
    hapticSlideMode: Int, onHapticSlideMode: (Int) -> Unit,
    hapticStockWaveExpand: Int, onHapticStockWaveExpand: (Int) -> Unit,
    hapticStockWaveSlide: Int, onHapticStockWaveSlide: (Int) -> Unit,
    hapticBrakePulse: Boolean, onHapticBrakePulse: (Boolean) -> Unit,
    hapticDoubleGapMs: Float, onHapticDoubleGapMs: (Float) -> Unit,
    hapticSlideBrakePulse: Boolean, onHapticSlideBrakePulse: (Boolean) -> Unit,
    hapticSlideDoubleGapMs: Float, onHapticSlideDoubleGapMs: (Float) -> Unit,
    regionBlurRadius: Float, onRegionBlurRadius: (Float) -> Unit,
    iconSpacingOffset: Float, onIconSpacingOffset: (Float) -> Unit,
    bubblePaddingPercent: Float, onBubblePaddingPercent: (Float) -> Unit,
    bubbleBlurRadius: Float, onBubbleBlurRadius: (Float) -> Unit,
    selectedIconScale: Float, onSelectedIconScale: (Float) -> Unit,
    iconGlowEffect: Boolean, onIconGlowEffect: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    // 各分区折叠状态（key → 是否已收起），通过 SharedPreferences 记忆持久化
    val uiPrefs = remember { ctx.getSharedPreferences("hyperopen_ui_state", android.content.Context.MODE_PRIVATE) }
    val collapsed = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            // 分区一览：默认只在「扇区分隔线样式」收起（精调项，一次调好后很少再动），
            // 其余保持展开，避免升级后用户找不到原来的开关
            listOf(
                "layout", "sector", "material", "header_pill",
                "icon", "bubble", "trigger", "haptic_expand", "haptic_slide",
            ).forEach { k ->
                if (uiPrefs.getBoolean("section_collapsed_$k", k == "sector")) {
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

    fun triggerSampleHaptic(
        mode: Int = hapticMode,
        strength: Int = hapticStrength.toInt(),
        durationMs: Int = hapticDurationMs.toInt(),
        enabled: Boolean = hapticEnabled,
        isSlide: Boolean = false,
        stockWave: Int = hapticStockWaveExpand,
        slideWave: Int = hapticStockWaveSlide,
        brake: Boolean = hapticBrakePulse,
        doubleGap: Int = hapticDoubleGapMs.toInt(),
        slideMode: Int = hapticSlideMode,
        slideDur: Int = hapticSlideDurationMs.toInt(),
        slideBrake: Boolean = hapticSlideBrakePulse,
        slideDoubleGap: Int = hapticSlideDoubleGapMs.toInt(),
    ) {
        try {
            // 稍许延时 25ms 执行，彻底避开 Miuix 控件本身在 onClick 瞬间触发的默认按键点击触感（HapticFeedbackConstants.KEYBOARD_TAP），避免双重震动干扰
            view.postDelayed({
                try {
                    @Suppress("DEPRECATION")
                    val vibrator = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    val sampleCfg = HyperOpenConfig().apply {
                        this.hapticEnabled = if (isSlide) true else enabled
                        this.hapticMode = mode
                        this.hapticStrength = strength
                        this.hapticDurationMs = durationMs
                        this.hapticStockWaveExpand = stockWave
                        this.hapticBrakePulse = brake
                        this.hapticDoubleGapMs = doubleGap
                        this.hapticSlideEnabled = if (isSlide) enabled else hapticSlideEnabled
                        this.hapticSlideStrength = if (isSlide) strength else hapticSlideStrength.toInt()
                        this.hapticSlideDurationMs = if (isSlide) slideDur else hapticSlideDurationMs.toInt()
                        this.hapticSlideMode = if (isSlide) slideMode else hapticSlideMode
                        this.hapticStockWaveSlide = slideWave
                        this.hapticSlideBrakePulse = if (isSlide) slideBrake else hapticSlideBrakePulse
                        this.hapticSlideDoubleGapMs = if (isSlide) slideDoubleGap else hapticSlideDoubleGapMs.toInt()
                    }
                    io.github.qmryqmm.hyperopen.view.CustomHyperOpenContainer.performConfiguredHaptic(ctx, vibrator, sampleCfg, isSlide)
                } catch (_: Throwable) {}
            }, 25L)
        } catch (_: Throwable) {}
    }

    val layoutName = layoutNameOf(layoutStyle)

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ══ Tab 0：布局 ══
        if (selectedTab == 0) {
            // ── 布局与几何 ──
            item(key = "layout_title") {
                CollapsibleSectionTitle(
                    title = "布局与几何",
                    expanded = isOpen("layout"),
                    onToggle = { toggle("layout") },
                    summary = "$layoutName · 图标 " +
                        (if (maxIconCount.toInt() <= 0) "全部" else "${maxIconCount.toInt()} 个") +
                        " · 已启用 $enabledShortcutsCount 项",
                )
            }
            if (isOpen("layout")) item(key = "layout_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    RadioButtonPreference(
                        title = "蜂窝阵列布局",
                        summary = "图标紧凑排布在整个圆形视界内（默认）",
                        selected = layoutStyle == HyperOpenConfig.LAYOUT_HONEYCOMB,
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
                    RadioButtonPreference(
                        title = "系统经典扇形布局",
                        summary = "完全复用 MIUI/HyperOS 原生 45°/60° 扇形几何与直线展开动画",
                        selected = layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK,
                        onClick = { onLayoutStyle(HyperOpenConfig.LAYOUT_SYSTEM_STOCK) },
                    )
                    SliderPreference(
                        title = "图标数量",
                        summary = when {
                            layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK -> "系统经典扇形建议 3~5 项（最多支持 $iconCapacity 项）；已启用 $enabledShortcutsCount 项"
                            layoutStyle == HyperOpenConfig.LAYOUT_ARC -> "弧形扇面布局不设几何上限；已启用 $enabledShortcutsCount 项"
                            layoutStyle == HyperOpenConfig.LAYOUT_RING && ringSectorMode -> "扇区触发模式支持 1~6 个，7 个及以上自动回退常规圆环；当前参数最多容纳 $iconCapacity 个，已启用 $enabledShortcutsCount 项"
                            else -> "当前参数下最多容纳 $iconCapacity 个；已启用 $enabledShortcutsCount 项，超出上限的启用项不显示"
                        },
                        valueText = if (maxIconCount.toInt() <= 0) "全部显示" else "${maxIconCount.toInt()} 个",
                        value = maxIconCount.coerceIn(0f, maxOf(iconCapacity, 1).toFloat()),
                        valueRange = 0f..maxOf(iconCapacity, 1).toFloat(),
                        onValueChange = onMaxIconCount,
                    )
                    if (layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
                        SwitchPreference(
                            title = "复用系统直线发射动画",
                            summary = "开启后使用系统 150ms 原生从指纹发射飞出动画；关闭则使用蜂窝弹性缩放动画",
                            checked = stockSystemAnim,
                            onCheckedChange = onStockSystemAnim,
                        )
                        SwitchPreference(
                            title = "复用系统几何扇区判定",
                            summary = "开启后使用系统原生 Path 差集多边形扇区判定当前选中项（无缝还原官方手感）",
                            checked = stockSystemRegion,
                            onCheckedChange = onStockSystemRegion,
                        )
                    }
                    if (layoutStyle == HyperOpenConfig.LAYOUT_RING) {
                        SwitchPreference(
                            title = "扇区触发模式",
                            summary = when {
                                !ringSectorMode -> "开启后：快捷方式 1~6 个时，图标沿圆周均分，中心圆内为取消区，圆外按图标数量均分为扇形（虚线分隔），滑入扇区即选中；该模式下强制开启图标吸附"
                                enabledShortcutsCount in 1..6 -> "已生效（当前 $enabledShortcutsCount 个快捷方式）：中心圆内松手取消，圆外 $enabledShortcutsCount 个扇区各对应一个图标；图标吸附已锁定开启"
                                else -> "快捷方式达到 7 个及以上时自动回退为常规同心圆环布局（当前 $enabledShortcutsCount 个）"
                            },
                            checked = ringSectorMode,
                            onCheckedChange = onRingSectorMode,
                        )
                        SliderPreference(
                            title = "同心圆环圈数",
                            summary = if (ringSectorMode && enabledShortcutsCount in 1..6)
                                "扇区触发模式下图标固定排布在单圈圆周上，圈数暂不生效" else "中心留空，图标分布在 N 圈同心环上",
                            valueText = "${ringCount.toInt()} 圈",
                            value = ringCount,
                            valueRange = 1f..4f,
                            onValueChange = onRingCount,
                        )
                    }
                    SliderPreference(
                        title = "整体垂直高度偏移",
                        summary = "改变整体图标高度",
                        valueText = "${offsetY.toInt()} dp",
                        value = offsetY,
                        valueRange = 0f..450f,
                        onValueChange = onOffsetY,
                    )
                    if (layoutStyle != HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
                        SliderPreference(
                            title = "圆形视界直径",
                            summary = "修改此项同时会影响最大图标显示数量",
                            valueText = "${regionDiameter.toInt()} dp",
                            value = regionDiameter,
                            valueRange = 150f..420f,
                            onValueChange = onRegionDiameter,
                        )
                    }
                }
            }

            // ── 扇区分隔线样式（仅空心圆环 + 扇区触发模式时出现） ──
            if (layoutStyle == HyperOpenConfig.LAYOUT_RING && ringSectorMode) {
                item(key = "sector_title") {
                    CollapsibleSectionTitle(
                        title = "扇区分隔线样式",
                        expanded = isOpen("sector"),
                        onToggle = { toggle("sector") },
                        summary = String.format(
                            "#%06X · 透明度 %d%% · 虚线 %d/%d dp · 线宽 %.1f dp",
                            sectorLineColor and 0xFFFFFF,
                            kotlin.math.round(sectorLineAlpha * 100f).toInt(),
                            sectorDashLength.toInt(),
                            sectorDashGap.toInt(),
                            sectorLineWidth,
                        ),
                    )
                }
                if (isOpen("sector")) item(key = "sector_card") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        var sectorHexInput by remember {
                            mutableStateOf(String.format("#%06X", sectorLineColor and 0xFFFFFF))
                        }
                        var showRgbSliders by remember { mutableStateOf(false) }
                        fun parseSectorHex(text: String): Int? {
                            val t = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
                            if (t.length != 6) return null
                            return try {
                                (0xFF shl 24) or t.toLong(16).toInt()
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        fun syncSectorHex(color: Int) {
                            sectorHexInput = String.format("#%06X", color and 0xFFFFFF)
                        }

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
                                    .background(
                                        Color(0xFF000000.toInt() or (sectorLineColor and 0xFFFFFF))
                                            .copy(alpha = sectorLineAlpha.coerceIn(0f, 1f))
                                    ),
                            )
                            TextField(
                                value = sectorHexInput,
                                onValueChange = { s ->
                                    sectorHexInput = s
                                    parseSectorHex(s)?.let { onSectorLineColor(it) }
                                },
                                label = "分隔线颜色 (#RRGGBB)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        val sectorPresets = listOf(
                            "淡灰" to 0xFFBFBFBF.toInt(),
                            "白色" to 0xFFFFFFFF.toInt(),
                            "黑色" to 0xFF000000.toInt(),
                            "青色" to 0xFF5DCAA5.toInt(),
                            "蓝色" to 0xFF378ADD.toInt(),
                            "橙色" to 0xFFEF9F27.toInt(),
                            "红色" to 0xFFE24B4A.toInt(),
                            "紫色" to 0xFF7F77DD.toInt(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            sectorPresets.forEach { (_, colorValue) ->
                                val selected = (sectorLineColor and 0xFFFFFF) == (colorValue and 0xFFFFFF)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .clickable {
                                            onSectorLineColor((0xFF shl 24) or (colorValue and 0xFFFFFF))
                                            syncSectorHex(colorValue)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Text(
                                            "✓",
                                            color = if ((colorValue and 0xFFFFFF) == 0xFFFFFF) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }

                        // RGB 三分量属于精调，收进二级开关，避免一屏十来个颜色控件
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRgbSliders = !showRgbSliders }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "自定义 RGB 分量（当前 ${(sectorLineColor shr 16) and 0xFF}, ${(sectorLineColor shr 8) and 0xFF}, ${sectorLineColor and 0xFF}）",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (showRgbSliders) "收起 ▲" else "展开 ▼",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        if (showRgbSliders) {
                            SliderPreference(
                                title = "红色分量",
                                summary = "分隔线颜色的红色通道 (R)",
                                valueText = "${(sectorLineColor shr 16) and 0xFF}",
                                value = ((sectorLineColor shr 16) and 0xFF).toFloat(),
                                valueRange = 0f..255f,
                                onValueChange = { r ->
                                    val nc = (0xFF shl 24) or ((r.toInt() and 0xFF) shl 16) or
                                        (sectorLineColor and 0xFF00) or (sectorLineColor and 0xFF)
                                    onSectorLineColor(nc)
                                    syncSectorHex(nc)
                                },
                            )
                            SliderPreference(
                                title = "绿色分量",
                                summary = "分隔线颜色的绿色通道 (G)",
                                valueText = "${(sectorLineColor shr 8) and 0xFF}",
                                value = ((sectorLineColor shr 8) and 0xFF).toFloat(),
                                valueRange = 0f..255f,
                                onValueChange = { g ->
                                    val nc = (0xFF shl 24) or (sectorLineColor and 0xFF0000) or
                                        ((g.toInt() and 0xFF) shl 8) or (sectorLineColor and 0xFF)
                                    onSectorLineColor(nc)
                                    syncSectorHex(nc)
                                },
                            )
                            SliderPreference(
                                title = "蓝色分量",
                                summary = "分隔线颜色的蓝色通道 (B)",
                                valueText = "${sectorLineColor and 0xFF}",
                                value = (sectorLineColor and 0xFF).toFloat(),
                                valueRange = 0f..255f,
                                onValueChange = { b ->
                                    val nc = (0xFF shl 24) or (sectorLineColor and 0xFF0000) or
                                        (sectorLineColor and 0xFF00) or (b.toInt() and 0xFF)
                                    onSectorLineColor(nc)
                                    syncSectorHex(nc)
                                },
                            )
                        }

                        SliderPreference(
                            title = "分隔线透明度",
                            summary = "虚线的整体不透明度，0% 完全隐藏分隔线",
                            valueText = "${kotlin.math.round(sectorLineAlpha * 100f).toInt()} %",
                            value = sectorLineAlpha * 100f,
                            valueRange = 0f..100f,
                            onValueChange = { onSectorLineAlpha(it / 100f) },
                        )
                        SliderPreference(
                            title = "虚线实线段长度",
                            summary = "每一段虚线的长度，越大线条越接近实线",
                            valueText = "${sectorDashLength.toInt()} dp",
                            value = sectorDashLength,
                            valueRange = 2f..20f,
                            onValueChange = onSectorDashLength,
                        )
                        SliderPreference(
                            title = "虚线间隔长度",
                            summary = "相邻虚线段之间的空隙，越大越稀疏",
                            valueText = "${sectorDashGap.toInt()} dp",
                            value = sectorDashGap,
                            valueRange = 2f..20f,
                            onValueChange = onSectorDashGap,
                        )
                        SliderPreference(
                            title = "分隔线粗细",
                            summary = "虚线线宽，细线更含蓄，粗线更醒目",
                            valueText = String.format("%.1f dp", sectorLineWidth),
                            value = sectorLineWidth,
                            valueRange = 0.5f..4f,
                            onValueChange = onSectorLineWidth,
                        )
                    }
                }
            }

            // ── 触发与进入 ──
            item(key = "trigger_title") {
                CollapsibleSectionTitle(
                    title = "触发与进入",
                    expanded = isOpen("trigger"),
                    onToggle = { toggle("trigger") },
                    summary = when {
                        showDelayMs.toInt() <= 0 -> "指纹解锁后立即弹出"
                        showDelayMs.toInt() == 500 -> "等待 500 ms（系统默认）"
                        else -> "等待 ${showDelayMs.toInt()} ms"
                    } + (if (faceUnlockQuickOpen) " · 人脸解锁可长按" else ""),
                )
            }
            if (isOpen("trigger")) item(key = "trigger_card") {
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
                }
            }
        }

        // ══ Tab 1：外观 ══
        if (selectedTab == 1) {
            // ── 材质与视觉 ──
            item(key = "material_title") {
                CollapsibleSectionTitle(
                    title = "材质与视觉",
                    expanded = isOpen("material"),
                    onToggle = { toggle("material") },
                    summary = (if (materialStyle == HyperOpenConfig.MATERIAL_SOFT_GLASS) "液态玻璃" else "高斯模糊") +
                        " · 全屏模糊 ${(fullscreenBlurMaskAlpha * 100).toInt()}%" +
                        if (layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) "" else (if (showRegionBg) " · 底衬开" else " · 底衬关"),
                )
            }
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
                    if (layoutStyle != HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
                        SwitchPreference(
                            title = "显示圆形视界底衬与边框",
                            summary = if (showRegionBg)
                                "当前显示毛玻璃底衬与圆形边框；关闭后屏上只保留图标"
                            else
                                "已关闭底衬与边框",
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
            }

            // ── 顶部提示胶囊参数 ──
            item(key = "header_pill_title") {
                CollapsibleSectionTitle(
                    title = "顶部提示胶囊",
                    expanded = isOpen("header_pill"),
                    onToggle = { toggle("header_pill") },
                    summary = (if (showHeaderPill) "已启用" else "已关闭") +
                        (if (showHeaderPill && showHeaderPillBg) " · 背景开" else "") +
                        (if (headerTextCentered) " · 居中" else " · 靠左"),
                )
            }
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
        }

        // ══ Tab 2：图标 ══
        if (selectedTab == 2) {
            // ── 图标与物理动力学 ──
            item(key = "icon_title") {
                CollapsibleSectionTitle(
                    title = "图标与物理动力学",
                    expanded = isOpen("icon"),
                    onToggle = { toggle("icon") },
                    summary = "尺寸 ${iconSize.toInt()} dp · 圆角 ${iconCornerRadius.toInt()}% · 放大 ${"%.2f".format(maxScale)}x · 辉光${if (iconGlowEffect) "开" else "关"}",
                )
            }
            if (isOpen("icon")) item(key = "icon_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SliderPreference(
                        title = "图标基础尺寸",
                        valueText = "${iconSize.toInt()} dp",
                        value = iconSize,
                        valueRange = 40f..80f,
                        onValueChange = onIconSize,
                    )
                    SliderPreference(
                        title = "图标圆角比例",
                        summary = "调节图标与气泡圆角弧度（50% 为正圆形，0% 为直角矩形）",
                        valueText = when {
                            iconCornerRadius.toInt() >= 50 -> "正圆 (50 %)"
                            iconCornerRadius.toInt() <= 0 -> "直角 (0 %)"
                            else -> "${iconCornerRadius.toInt()} %"
                        },
                        value = iconCornerRadius,
                        valueRange = 0f..50f,
                        onValueChange = onIconCornerRadius,
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
                        summary = "手指划过选中/放大目标图标时的动态透明度（全布局通用）",
                        valueText = "${(fisheyeZoomAlpha * 100).toInt()} %",
                        value = fisheyeZoomAlpha * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onFisheyeZoomAlpha(it / 100f) },
                    )
                    SliderPreference(
                        title = "鱼眼放大时其他图标透明度",
                        summary = "手指划过放大目标图标时，其余未选中图标的动态透明度",
                        valueText = "${(fisheyeOtherAlpha * 100).toInt()} %",
                        value = fisheyeOtherAlpha * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { onFisheyeOtherAlpha(it / 100f) },
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

            // ── 气泡与动效 ──
            item(key = "bubble_title") {
                CollapsibleSectionTitle(
                    title = "气泡与动效",
                    expanded = isOpen("bubble"),
                    onToggle = { toggle("bubble") },
                    summary = "留白 ${bubblePaddingPercent.toInt()}% · 模糊 ${bubbleBlurRadius.toInt()} dp · 压暗 ${(bubbleDimAlpha * 100).toInt()}%",
                )
            }
            if (isOpen("bubble")) item(key = "bubble_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
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
                }
            }
        }

        // ══ Tab 3：震动 ══
        if (selectedTab == 3) {
            // ── 进入面板震动 ──
            item(key = "haptic_expand_title") {
                CollapsibleSectionTitle(
                    title = "进入面板震动",
                    expanded = isOpen("haptic_expand"),
                    onToggle = { toggle("haptic_expand") },
                    summary = (if (hapticEnabled) hapticModeNameOf(hapticMode) else "已关闭") +
                        (if (hapticMode != HyperOpenConfig.HAPTIC_MODE_STOCK) " · 强度 ${(hapticStrength / 255f * 100).toInt()}%" else ""),
                )
            }
            if (isOpen("haptic_expand")) item(key = "haptic_expand_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = "开启进入面板震动",
                        summary = "长按指纹完成、快捷方式面板完全展开时触发",
                        checked = hapticEnabled,
                        onCheckedChange = {
                            onHapticEnabled(it)
                            if (it) triggerSampleHaptic(enabled = true)
                        },
                    )
                    if (hapticEnabled) {
                        RadioButtonPreference(
                            title = "系统原生触感 (HyperOS Stock)",
                            summary = "调用澎湃OS原生线性马达波形库,震动强度跟随系统",
                            selected = hapticMode == HyperOpenConfig.HAPTIC_MODE_STOCK,
                            onClick = {
                                onHapticMode(HyperOpenConfig.HAPTIC_MODE_STOCK)
                                triggerSampleHaptic(mode = HyperOpenConfig.HAPTIC_MODE_STOCK)
                            },
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
                                onValueChange = {
                                    onHapticDurationMs(it)
                                    triggerSampleHaptic(durationMs = it.toInt())
                                },
                            )
                        }
                        if (hapticMode != HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SliderPreference(
                                title = "震动强度",
                                summary = "调节马达脉冲振幅强弱",
                                valueText = "${
                                    ((hapticStrength / 255f) * 100).toInt().coerceIn(1, 100)
                                } % (${hapticStrength.toInt()})",
                                value = hapticStrength,
                                valueRange = 1f..255f,
                                onValueChange = {
                                    onHapticStrength(it)
                                    triggerSampleHaptic(strength = it.toInt())
                                },
                            )
                        }

                        if (hapticMode == HyperOpenConfig.HAPTIC_MODE_DOUBLE) {
                            SliderPreference(
                                title = "双脉冲间隔时间",
                                summary = "调节两次敲击脉冲之间的停顿间隔",
                                valueText = "${hapticDoubleGapMs.toInt()} ms",
                                value = hapticDoubleGapMs,
                                valueRange = 10f..60f,
                                onValueChange = {
                                    onHapticDoubleGapMs(it)
                                    triggerSampleHaptic(doubleGap = it.toInt())
                                },
                            )
                        }

                        if (hapticMode != HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SwitchPreference(
                                title = "反向制动脉冲 (Brake Pulse)",
                                summary = "在主震动末尾施加反向制动微脉冲",
                                checked = hapticBrakePulse,
                                onCheckedChange = {
                                    onHapticBrakePulse(it)
                                    triggerSampleHaptic(brake = it)
                                },
                            )
                        }

                        if (hapticMode == HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SmallTitle(text = "系统线性马达波形库选择")
                            RadioButtonPreference(
                                title = "长按回弹波形 (long_press)",
                                summary = "系统默认波形，带有充沛饱满的阻尼吸附感",
                                selected = hapticStockWaveExpand == HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS,
                                onClick = {
                                    onHapticStockWaveExpand(HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS)
                                    triggerSampleHaptic(
                                        mode = HyperOpenConfig.HAPTIC_MODE_STOCK,
                                        stockWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS
                                    )
                                },
                            )
                            RadioButtonPreference(
                                title = "网格微动波形 (mesh_normal)",
                                summary = "紧凑细腻的高频颗粒感，轻巧利落",
                                selected = hapticStockWaveExpand == HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL,
                                onClick = {
                                    onHapticStockWaveExpand(HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL)
                                    triggerSampleHaptic(
                                        mode = HyperOpenConfig.HAPTIC_MODE_STOCK,
                                        stockWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL
                                    )
                                },
                            )
                            RadioButtonPreference(
                                title = "轻触弹击波形 (flick)",
                                summary = "如同物理拨动开关的微弹质感",
                                selected = hapticStockWaveExpand == HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK,
                                onClick = {
                                    onHapticStockWaveExpand(HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK)
                                    triggerSampleHaptic(
                                        mode = HyperOpenConfig.HAPTIC_MODE_STOCK,
                                        stockWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK
                                    )
                                },
                            )
                            RadioButtonPreference(
                                title = "气泡轻爆波形 (pop)",
                                summary = "柔和轻快的微泡破裂声学振动",
                                selected = hapticStockWaveExpand == HyperOpenConfig.HAPTIC_STOCK_WAVE_POP,
                                onClick = {
                                    onHapticStockWaveExpand(HyperOpenConfig.HAPTIC_STOCK_WAVE_POP)
                                    triggerSampleHaptic(
                                        mode = HyperOpenConfig.HAPTIC_MODE_STOCK,
                                        stockWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_POP
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ── 滑动选择震动 ──
            item(key = "haptic_slide_title") {
                CollapsibleSectionTitle(
                    title = "滑动选择震动",
                    expanded = isOpen("haptic_slide"),
                    onToggle = { toggle("haptic_slide") },
                    summary = (if (hapticSlideEnabled) hapticModeNameOf(hapticSlideMode) else "已关闭") +
                        (if (hapticSlideMode != HyperOpenConfig.HAPTIC_MODE_STOCK) " · 强度 ${(hapticSlideStrength / 255f * 100).toInt()}%" else ""),
                )
            }
            if (isOpen("haptic_slide")) item(key = "haptic_slide_card") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = "开启滑动选择震动",
                        summary = "手指在各个图标间滑动吸附时提供独立震动反馈",
                        checked = hapticSlideEnabled,
                        onCheckedChange = {
                            onHapticSlideEnabled(it)
                            if (it) triggerSampleHaptic(isSlide = true, enabled = true)
                        },
                    )
                    if (hapticSlideEnabled) {
                        RadioButtonPreference(
                            title = "系统原生触感 (HyperOS Stock)",
                            summary = "调用澎湃OS原生线性马达波形库,震动强度跟随系统",
                            selected = hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_STOCK,
                            onClick = {
                                onHapticSlideMode(HyperOpenConfig.HAPTIC_MODE_STOCK)
                                triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_STOCK)
                            },
                        )
                        RadioButtonPreference(
                            title = "清脆短震 (Tick)",
                            summary = "极短促微动吸附手感",
                            selected = hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_TICK,
                            onClick = {
                                onHapticSlideMode(HyperOpenConfig.HAPTIC_MODE_TICK)
                                triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_TICK)
                            },
                        )
                        RadioButtonPreference(
                            title = "标准点按 (Click)",
                            summary = "稳健确认感",
                            selected = hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_CLICK,
                            onClick = {
                                onHapticSlideMode(HyperOpenConfig.HAPTIC_MODE_CLICK)
                                triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_CLICK)
                            },
                        )
                        RadioButtonPreference(
                            title = "双脉冲震动 (Double)",
                            summary = "两次轻快微震",
                            selected = hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_DOUBLE,
                            onClick = {
                                onHapticSlideMode(HyperOpenConfig.HAPTIC_MODE_DOUBLE)
                                triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_DOUBLE)
                            },
                        )
                        RadioButtonPreference(
                            title = "自定义时长脉冲",
                            summary = "自由设定滑动吸附时长",
                            selected = hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_CUSTOM,
                            onClick = {
                                onHapticSlideMode(HyperOpenConfig.HAPTIC_MODE_CUSTOM)
                                triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_CUSTOM)
                            },
                        )

                        if (hapticSlideMode != HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SliderPreference(
                                title = "震动强度",
                                summary = "调节滑动选择图标时的振幅强弱",
                                valueText = "${((hapticSlideStrength / 255f) * 100).toInt().coerceIn(1, 100)} % (${hapticSlideStrength.toInt()})",
                                value = hapticSlideStrength,
                                valueRange = 1f..255f,
                                onValueChange = {
                                    onHapticSlideStrength(it)
                                    triggerSampleHaptic(isSlide = true, strength = it.toInt())
                                },
                            )
                        }

                        if (hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_CUSTOM) {
                            SliderPreference(
                                title = "自定义震动附持续时长",
                                valueText = "${hapticSlideDurationMs.toInt()} ms",
                                value = hapticSlideDurationMs,
                                valueRange = 4f..80f,
                                onValueChange = {
                                    onHapticSlideDurationMs(it)
                                    triggerSampleHaptic(isSlide = true, slideDur = it.toInt())
                                },
                            )
                        }

                        if (hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_DOUBLE) {
                            SliderPreference(
                                title = "双脉冲间隔时间",
                                summary = "调节两次敲击脉冲之间的停顿间隔",
                                valueText = "${hapticSlideDoubleGapMs.toInt()} ms",
                                value = hapticSlideDoubleGapMs,
                                valueRange = 10f..60f,
                                onValueChange = {
                                    onHapticSlideDoubleGapMs(it)
                                    triggerSampleHaptic(isSlide = true, slideDoubleGap = it.toInt())
                                },
                            )
                        }

                        if (hapticSlideMode != HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SwitchPreference(
                                title = "反向制动脉冲 (Brake Pulse)",
                                summary = "在主震动末尾施加反向制动微脉冲",
                                checked = hapticSlideBrakePulse,
                                onCheckedChange = {
                                    onHapticSlideBrakePulse(it)
                                    triggerSampleHaptic(isSlide = true, slideBrake = it)
                                },
                            )
                        }

                        if (hapticSlideMode == HyperOpenConfig.HAPTIC_MODE_STOCK) {
                            SmallTitle(text = "系统线性马达波形库选择")
                            RadioButtonPreference(
                                title = "长按回弹波形 (long_press)",
                                summary = "系统默认波形，带有充沛饱满的阻尼吸附感",
                                selected = hapticStockWaveSlide == HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS,
                                onClick = {
                                    onHapticStockWaveSlide(HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS)
                                    triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_STOCK, slideWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS)
                                },
                            )
                            RadioButtonPreference(
                                title = "网格微动 (mesh_normal)",
                                summary = "齿轮滚珠质感",
                                selected = hapticStockWaveSlide == HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL,
                                onClick = {
                                    onHapticStockWaveSlide(HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL)
                                    triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_STOCK, slideWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL)
                                },
                            )
                            RadioButtonPreference(
                                title = "轻触弹击 (flick)",
                                summary = "轻拨微弹质感",
                                selected = hapticStockWaveSlide == HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK,
                                onClick = {
                                    onHapticStockWaveSlide(HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK)
                                    triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_STOCK, slideWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK)
                                },
                            )
                            RadioButtonPreference(
                                title = "气泡轻爆 (pop)",
                                summary = "轻柔微弹质感",
                                selected = hapticStockWaveSlide == HyperOpenConfig.HAPTIC_STOCK_WAVE_POP,
                                onClick = {
                                    onHapticStockWaveSlide(HyperOpenConfig.HAPTIC_STOCK_WAVE_POP)
                                    triggerSampleHaptic(isSlide = true, slideMode = HyperOpenConfig.HAPTIC_MODE_STOCK, slideWave = HyperOpenConfig.HAPTIC_STOCK_WAVE_POP)
                                },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
