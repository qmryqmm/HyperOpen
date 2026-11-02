package io.github.qmryqmm.hyperopen.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.qmryqmm.hyperopen.config.HyperOpenConfig
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory
import io.github.qmryqmm.hyperopen.layout.ArcFanPacking
import io.github.qmryqmm.hyperopen.layout.CircularHoneycombPacking
import io.github.qmryqmm.hyperopen.layout.HollowRingPacking
import io.github.qmryqmm.hyperopen.layout.RingSectorPacking
import io.github.qmryqmm.hyperopen.layout.StockFanPacking
import io.github.qmryqmm.hyperopen.model.ShortcutItem
import io.github.qmryqmm.hyperopen.view.CircularBubbleView
import io.github.qmryqmm.hyperopen.view.CircularRegionBackdropView
import io.github.qmryqmm.hyperopen.view.SectorOverlayView
import io.github.qmryqmm.hyperopen.view.CustomHyperOpenContainer
import io.github.qmryqmm.hyperopen.view.HintTextStyler
import io.github.qmryqmm.hyperopen.view.StrokeTextView

/**
 * 整体指纹快捷面板高保真预览与长按拖拽交互视图。
 * 复用与 SystemUI 完全一致的 CircularBubbleView、CircularRegionBackdropView 与 HeaderPill 渲染，
 * 确保所见即所得。
 * - 普通滑动：模拟指纹解锁后手指滑过快捷图标的鱼眼动力学吸附与实时聚焦反馈；
 * - 长按图标：震动进入自定义排版模式，可自由拖拽改变图标在蜂窝阵列中的位置顺序；
 * - 松手磁吸：其他图标平滑让位，松手后磁吸回网格并同步保存。
 */
data class BubbleBlurSpot(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val diameterPx: Float,
    val alpha: Float = 1.0f,
)

data class PreviewBlurSceneState(
    val regionCenterX: Float = 0f,
    val regionCenterY: Float = 0f,
    val regionDiameterPx: Float = 0f,
    val headerLeftPx: Float = 0f,
    val headerTopPx: Float = 0f,
    val headerWidthPx: Float = 0f,
    val headerHeightPx: Float = 0f,
    val backBtnLeftPx: Float = 0f,
    val backBtnTopPx: Float = 0f,
    val backBtnSizePx: Float = 0f,
    val bubbles: List<BubbleBlurSpot> = emptyList(),
)

/**
 * 整体指纹快捷面板高保真预览与长按拖拽交互视图。
 * 复用与 SystemUI 完全一致的 CircularBubbleView、CircularRegionBackdropView 与 HeaderPill 渲染，
 * 确保所见即所得。
 * - 普通滑动：模拟指纹解锁后手指滑过快捷图标的鱼眼动力学吸附与实时聚焦反馈；
 * - 长按图标：震动进入自定义排版模式，可自由拖拽改变图标在蜂窝阵列中的位置顺序；
 * - 松手磁吸：其他图标平滑让位，松手后磁吸回网格并同步保存。
 */
class HyperOpenPreviewView(
    context: Context,
    private var config: HyperOpenConfig,
    private val onBackClick: () -> Unit,
    private val onOrderChanged: (List<ShortcutItem>) -> Unit,
    private val onBlurSceneChanged: ((PreviewBlurSceneState) -> Unit)? = null,
) : FrameLayout(context) {

    private var resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(context, config.materialStyle)
    private var vibrator: Vibrator? = null

    private lateinit var topHeaderContainer: LinearLayout
    private lateinit var headerPill: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView
    private lateinit var editBanner: TextView

    private lateinit var regionBackdrop: CircularRegionBackdropView
    /** 全屏扇区分隔虚线覆盖层（独立于圆形底衬，虚线可延伸到屏幕外） */
    private lateinit var sectorOverlay: SectorOverlayView
    private lateinit var bubblesContainer: FrameLayout
    private val bubbleViews = mutableListOf<CircularBubbleView>()
    private val stockSpecs = mutableListOf<StockFanPacking.StockItemSpec>()
    private val activeShortcuts = mutableListOf<ShortcutItem>()
    private val gridPoints = mutableListOf<PointF>()

    private var lastFodX = 0f
    private var lastFodY = 0f
    private var lastRegionCenterX = 0f
    private var lastRegionCenterY = 0f
    private var lastRegionDiameterPx = 0f

    private var currentSelectedBubble: CircularBubbleView? = null
    /** 空心圆环扇区触发模式的几何规格（非扇区模式时为 null） */
    private var ringSectorSpec: RingSectorPacking.Result? = null

    // 拖拽排版相关状态
    var isEditMode: Boolean = false
        private set
    private var isTouching: Boolean = false
    private var draggingBubble: CircularBubbleView? = null
    private var dragTouchStartX = 0f
    private var dragTouchStartY = 0f
    private var isDraggingActive = false
    private val longPressRunnable = Runnable {
        val target = pendingPressBubble
        // 扇区触发模式下禁止自定义排版：图标位置由扇区几何决定，长按不进入拖拽编辑
        if (target != null && !isEditMode && !isDraggingActive && !isRingSectorActive()) {
            currentSelectedBubble?.setSelectedState(false)
            currentSelectedBubble = null
            enterEditMode(target)
        }
    }
    private var pendingPressBubble: CircularBubbleView? = null

    // 排版模式下的图标吸附开关（右上角按钮切换）；关闭后松手的图标保持在放手位置，不自动吸附回蜂窝槽位
    // 状态持久化到 SharedPreferences("hyperopen_ui_state")["snap_enabled"]，跨次进入排版界面记忆
    var snapEnabled: Boolean = true
        private set
    private var snapToggleButton: TextView? = null
    private val freePlacedBubbles = mutableSetOf<CircularBubbleView>()

    // 会话布局快照：进入界面/保存后捕获，用于返回键退出时判断是否存在未保存的排版修改
    private data class LayoutSig(val id: String, val offX: Float, val offY: Float)
    private var sessionSnapshot: List<LayoutSig> = emptyList()
    // 各快捷项进入会话时的原始偏移（item 与 MainActivity 列表共享对象，放弃修改时据此还原字段）
    private var pristineOffsets: Map<String, FloatArray> = emptyMap()

    init {
        snapEnabled = loadSnapEnabled()
        initVibrator()
        initViews()
    }

    private fun loadSnapEnabled(): Boolean {
        return try {
            context.getSharedPreferences("hyperopen_ui_state", Context.MODE_PRIVATE)
                .getBoolean("snap_enabled", true)
        } catch (_: Throwable) {
            true
        }
    }

    private fun persistSnapEnabled() {
        try {
            context.getSharedPreferences("hyperopen_ui_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("snap_enabled", snapEnabled)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibrator = vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {}
    }

    private fun triggerHaptic() {
        CustomHyperOpenContainer.performConfiguredHaptic(context, vibrator, config, false)
    }

    private fun triggerSlideHaptic() {
        CustomHyperOpenContainer.performConfiguredHaptic(context, vibrator, config, true)
    }

    private var lastLayoutW = 0
    private var lastLayoutH = 0

    private fun initViews() {
        clipChildren = false
        clipToPadding = false
        refreshBackdropMask()

        // 1. 顶部提示胶囊与编辑提示
        buildHeader()

        // 2. 蜂窝圆形区域底衬（预览界面由 Compose 的 textureBlur 统一渲染实时壁纸模糊，禁用窗口级 BackgroundBlur 避免双层底色叠加）
        regionBackdrop = CircularRegionBackdropView(context)
        regionBackdrop.setSystemBlurEnabled(false)
        regionBackdrop.setStyle(resolvedStyle)
        addView(regionBackdrop)

        // 2.1 扇区分隔虚线覆盖层：全屏绘制，虚线一直延伸到屏幕外；关闭底衬后只剩虚线与图标
        sectorOverlay = SectorOverlayView(context)
        addView(sectorOverlay)

        // 3. 气泡容器
        bubblesContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        addView(bubblesContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 4. 左上角小悬浮返回按钮
        buildFloatingBackButton()

        // 4.1 右上角吸附开关（仅排版模式可见）
        buildSnapToggleButton()

        // 5. 在首次测量前提前建立蜂窝气泡视图树
        setupLayout()

        // 6. 全屏虚线层插入后，把顶部提示重新提到最上层，避免虚线穿过提示胶囊
        topHeaderContainer.bringToFront()
    }

    private var floatingBackButton: FrameLayout? = null

    private fun buildFloatingBackButton() {
        val btnSize = dpToPx(40f).toInt()
        val backBtn = FrameLayout(context).apply {
            background = HyperOSMaterialFactory.createCircularBubbleDrawable(
                resolvedStyle, 0xFFFFFFFF.toInt(), false, false
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // 返回出口统一交给外部（MainActivity）：有未保存排版修改时弹「是否保存」确认框，
                // 否则直接退出排版界面。不在这里 exitEditMode/提交，防止未结算的让位动画中间值被写入。
                onBackClick()
            }
        }

        val iconView = android.widget.ImageView(context).apply {
            val arrowDrawable = io.github.qmryqmm.hyperopen.view.IconGlyphFactory.createGlyph("arrow_left", 0xFFFFFFFF.toInt())
            setImageDrawable(arrowDrawable)
            val p = dpToPx(10f).toInt()
            setPadding(p, p, p, p)
        }
        backBtn.addView(iconView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val btnLp = LayoutParams(btnSize, btnSize).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dpToPx(18f).toInt()
            topMargin = dpToPx(46f).toInt()
        }
        addView(backBtn, btnLp)
        floatingBackButton = backBtn
    }

    private fun snapToggleText(): String = if (snapEnabled) "图标吸附：开" else "图标吸附：关"

    private fun buildSnapToggleButton() {
        val btn = TextView(context).apply {
            text = snapToggleText()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            val padH = dpToPx(14f).toInt()
            setPadding(padH, 0, padH, 0)
            background = HyperOSMaterialFactory.createHeaderPillDrawable(resolvedStyle, config.headerDimAlpha)
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    outline.setRoundRect(0, 0, view.width, view.height, Math.min(view.width, view.height) / 2f)
                    outline.alpha = 0f
                }
            }
            clipToOutline = true
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener {
                snapEnabled = !snapEnabled
                persistSnapEnabled()
                text = snapToggleText()
                triggerHaptic()
                if (snapEnabled) {
                    // 重新开启吸附：所有被自由摆放的图标立即归位到蜂窝槽位
                    snapAllBubblesToGrid(animated = true)
                }
            }
        }
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(40f).toInt()).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dpToPx(18f).toInt()
            topMargin = dpToPx(46f).toInt()
        }
        addView(btn, lp)
        snapToggleButton = btn
    }

    /**
     * 将所有气泡（含被自由摆放的）归位到各自蜂窝槽位
     */
    private fun snapAllBubblesToGrid(animated: Boolean) {
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        freePlacedBubbles.clear()
        for (i in 0 until bubbleViews.size.coerceAtMost(gridPoints.size)) {
            val b = bubbleViews[i]
            val pt = gridPoints[i]
            b.item.customOffsetX = 0f
            b.item.customOffsetY = 0f
            b.targetX = pt.x
            b.targetY = pt.y
            val left = (pt.x - iconSizePx / 2f).toInt()
            val top = (pt.y - iconSizePx / 2f).toInt()
            val lp = b.layoutParams as LayoutParams
            if (animated) {
                val fromX = lp.leftMargin + b.translationX
                val fromY = lp.topMargin + b.translationY
                lp.leftMargin = left
                lp.topMargin = top
                b.layoutParams = lp
                b.translationX = fromX - left
                b.translationY = fromY - top
                b.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .setUpdateListener { notifyBlurSceneChanged() }
                    .withEndAction { notifyBlurSceneChanged() }
                    .start()
            } else {
                lp.leftMargin = left
                lp.topMargin = top
                b.layoutParams = lp
                b.translationX = 0f
                b.translationY = 0f
            }
        }
        notifyBlurSceneChanged()
    }

    private fun refreshFloatingBackButtonBackground() {
        val btn = floatingBackButton ?: return
        btn.background = HyperOSMaterialFactory.createCircularBubbleDrawable(
            resolvedStyle, 0xFFFFFFFF.toInt(), false, false
        )
        btn.invalidate()
    }

    private fun buildHeader() {
        topHeaderContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        headerPill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padH = dpToPx(24f).toInt()
            val padV = dpToPx(10f).toInt()
            setPadding(padH, padV, padH, padV)
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    val w = view.width
                    val h = view.height
                    if (w <= 0 || h <= 0) return
                    outline.setRoundRect(0, 0, w, h, Math.min(w, h) / 2f)
                    outline.alpha = 0f
                }
            }
            addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if ((r - l) != (or - ol) || (b - t) != (ob - ot) || l != ol || t != ot) {
                    applyHeaderPillBackground()
                    notifyBlurSceneChanged()
                }
            }
        }

        headerTitle = StrokeTextView(context).apply {
            text = "滑动选择快捷方式"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }

        headerSubtitle = StrokeTextView(context).apply {
            text = "移至目标图标后松手直接打开"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        headerPill.addView(headerTitle)
        headerPill.addView(headerSubtitle)

        // 编辑模式横幅
        editBanner = TextView(context).apply {
            text = "✦ 自定义排版模式：拖拽移动图标，点击空白完成 ✦"
            textSize = 11f
            setTextColor(0xFFFF9933.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val padH = dpToPx(14f).toInt()
            val padV = dpToPx(5f).toInt()
            setPadding(padH, padV, padH, padV)
            visibility = View.GONE
        }

        topHeaderContainer.addView(headerPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val bannerLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(8f).toInt()
        }
        topHeaderContainer.addView(editBanner, bannerLp)

        val topLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(100f).toInt()
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        addView(topHeaderContainer, topLp)

        applyHeaderPillStyleAndLayout()
    }

    /**
     * 1:1 对齐 CustomHyperOpenContainer：同步应用顶部提示显隐、高度位置、文字大小、水平居中和胶囊背景
     */
    private fun applyHeaderPillStyleAndLayout() {
        if (!::topHeaderContainer.isInitialized || !::headerPill.isInitialized) return
        val showHeader = config.showHeaderPill
        headerPill.visibility = if (showHeader) View.VISIBLE else View.GONE

        val titleSp = config.headerTitleSize.coerceIn(12, 26).toFloat()
        val subtitleSp = config.headerSubtitleSize.coerceIn(9, 18).toFloat()
        val centered = config.headerTextCentered
        val topMarginDp = config.headerTopMargin.coerceIn(20, 420).toFloat()

        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
        headerTitle.gravity = if (centered) Gravity.CENTER else Gravity.START
        headerSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSp)
        headerSubtitle.gravity = if (centered) Gravity.CENTER else Gravity.START

        headerPill.gravity = if (centered) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
        topHeaderContainer.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START

        val pillLp = (headerPill.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        pillLp.gravity = if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        pillLp.leftMargin = if (centered) 0 else dpToPx(24f).toInt()
        pillLp.rightMargin = if (centered) 0 else dpToPx(24f).toInt()
        headerPill.layoutParams = pillLp

        val topLp = (topHeaderContainer.layoutParams as? LayoutParams)
            ?: LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        topLp.topMargin = dpToPx(topMarginDp).toInt()
        topLp.gravity = Gravity.TOP or (if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START)
        topHeaderContainer.layoutParams = topLp

        applyHeaderPillBackground()
    }

    /**
     * 按 showHeaderPillBg 开关与 materialStyle 材质动态设置顶部提示胶囊背景
     */
    private fun applyHeaderPillBackground() {
        if (!::headerPill.isInitialized) return
        val show = config.showHeaderPill && config.showHeaderPillBg
        if (show) {
            headerPill.background = HyperOSMaterialFactory.createHeaderPillDrawable(resolvedStyle, config.headerDimAlpha)
            headerPill.clipToOutline = true
        } else {
            headerPill.background = null
            headerPill.clipToOutline = false
        }
        headerPill.invalidateOutline()
        headerPill.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != lastLayoutW || h != lastLayoutH || bubbleViews.isEmpty())) {
            setupLayout(w, h)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        // 注意：detach 时不再自动提交排版（旧实现裸提交未结算的动画中间值，导致返回键退出后图标错位）；
        // 是否保存由 MainActivity 的返回确认弹窗决定：commitLayoutChanges() / discardLayoutChanges()
        super.onDetachedFromWindow()
    }

    /**
     * 获取与真实锁屏 SystemUI (GxzwWindowFrameLayout 全屏物理窗口) 1:1 完全对齐的 FOD 指纹中心坐标：
     * 1) 优先使用 SystemUI 真实展开时上报并持久化的 lastRealFodX / lastRealFodY；
     * 2) 其次尝试读取系统属性 persist.vendor.sys.fp.fod.location.X_Y 与 size.width_height；
     * 3) 再次尝试反射调用 MiuiGxzwUtils；
     * 4) 最终使用 Display.getRealSize 真实物理屏幕高度 * 0.795f 兜底（绝不受 Activity 导航栏裁剪高度影响），
     *    并按当前预览 View 在屏幕上的真实 window location 偏移换算为局部坐标。
     */
    private fun calculateFodCoordinates(targetW: Float, targetH: Float): PointF {
        val loc = IntArray(2)
        try {
            getLocationOnScreen(loc)
        } catch (_: Throwable) {}
        val viewOffsetX = loc[0].toFloat()
        val viewOffsetY = loc[1].toFloat()

        // 1) 优先读取 SystemUI 真实上报的精准 FOD 中心坐标
        if (config.lastRealFodX > 0f && config.lastRealFodY > 0f) {
            return PointF(config.lastRealFodX - viewOffsetX, config.lastRealFodY - viewOffsetY)
        }

        // 2) 尝试读取小米底层 FOD 硬件坐标系统属性 (与 MiuiGxzwUtils 数据源一致)
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val locStr = (getMethod.invoke(null, "persist.vendor.sys.fp.fod.location.X_Y", "") as? String)?.trim().orEmpty()
            val sizeStr = (getMethod.invoke(null, "persist.vendor.sys.fp.fod.size.width_height", "") as? String)?.trim().orEmpty()
            if (locStr.contains(",") && sizeStr.contains(",")) {
                val locParts = locStr.split(",")
                val sizeParts = sizeStr.split(",")
                val fx = locParts[0].trim().toFloat()
                val fy = locParts[1].trim().toFloat()
                val fw = sizeParts[0].trim().toFloat()
                val fh = sizeParts[1].trim().toFloat()
                if (fw > 0f && fh > 0f && fy > 0f) {
                    return PointF(fx + fw / 2f - viewOffsetX, fy + fh / 2f - viewOffsetY)
                }
            }
        } catch (_: Throwable) {}

        // 3) 尝试反射读取 MiuiGxzwUtils
        try {
            val utilsClass = Class.forName("com.miui.keyguard.biometrics.fod.MiuiGxzwUtils")
            val caculateMethod = utilsClass.getMethod("caculateGxzwIconSize", Context::class.java)
            caculateMethod.invoke(null, context)
            val iconX = utilsClass.getField("GXZW_ICON_X").getInt(null)
            val iconY = utilsClass.getField("GXZW_ICON_Y").getInt(null)
            val iconW = utilsClass.getField("GXZW_ICON_WIDTH").getInt(null)
            val iconH = utilsClass.getField("GXZW_ICON_HEIGHT").getInt(null)
            if (iconW > 0 && iconH > 0 && iconY > 0) {
                return PointF(iconX + iconW / 2.0f - viewOffsetX, iconY + iconH / 2.0f - viewOffsetY)
            }
        } catch (_: Throwable) {}

        // 4) 真实物理屏幕尺寸兜底（使用 Display.getRealSize，与 HyperOpenHook.getDisplaySize 完全一致）
        val realSize = Point()
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealSize(realSize)
        } catch (_: Throwable) {}
        val realW = if (realSize.x > 0) realSize.x.toFloat() else targetW
        val realH = if (realSize.y > 0) realSize.y.toFloat() else targetH
        return PointF(realW / 2.0f - viewOffsetX, realH * 0.795f - viewOffsetY)
    }

    private fun refreshBackdropMask() {
        // 保持预览容器自身透明，避免遮挡下层 Compose 统一渲染的实时毛玻璃图层
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * 参数热更新：材质、胶囊背景开关、尺寸、气泡模糊半径与偏移即刻生效。
     * 若当前正处于手指按压、悬停放大或长按拖拽排版过程中，绝不重建视图树，避免打断手势或重置缩放。
     */
    fun updateConfig(newConfig: HyperOpenConfig) {
        if (isTouching || isEditMode || isDraggingActive) {
            this.config = newConfig
            return
        }
        val old = this.config
        val needRebuild = old.offsetY != newConfig.offsetY ||
            old.regionDiameter != newConfig.regionDiameter ||
            old.showRegionBg != newConfig.showRegionBg ||
            old.iconSize != newConfig.iconSize ||
            old.iconSpacingOffset != newConfig.iconSpacingOffset ||
            old.materialStyle != newConfig.materialStyle ||
            old.bubblePaddingPercent != newConfig.bubblePaddingPercent ||
            old.bubbleBlurRadius != newConfig.bubbleBlurRadius ||
            old.appIconCropMode != newConfig.appIconCropMode ||
            old.maxIconCount != newConfig.maxIconCount ||
            old.layoutStyle != newConfig.layoutStyle ||
            old.ringSectorMode != newConfig.ringSectorMode ||
            old.ringCount != newConfig.ringCount ||
            old.shortcuts.size != newConfig.shortcuts.size

        this.config = newConfig
        this.resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(context, config.materialStyle)
        refreshBackdropMask()
        if (::regionBackdrop.isInitialized) {
            regionBackdrop.setStyle(resolvedStyle)
            regionBackdrop.setBlurRadius(if (config.showRegionBg) config.regionBlurRadius.toFloat() else 0f)
            regionBackdrop.setDimAlpha(if (config.showRegionBg) config.regionDimAlpha else 0f)
            // 扇区分隔线样式（颜色/透明度/虚线规格）热生效
            val liveSpec = ringSectorSpec
            if (liveSpec != null && isRingSectorActive()) {
                sectorOverlay.update(
                    true,
                    liveSpec.count,
                    lastRegionCenterX,
                    lastRegionCenterY,
                    liveSpec.cancelRadius,
                    liveSpec.startAngleDeg,
                    config.sectorLineColor,
                    config.sectorLineAlpha,
                    config.sectorDashLength,
                    config.sectorDashGap,
                    config.sectorLineWidth
                )
            } else {
                sectorOverlay.clear()
            }
        }
        applyHeaderPillStyleAndLayout()
        refreshFloatingBackButtonBackground()
        for (b in bubbleViews) b.setDimAlpha(config.bubbleDimAlpha)
        if (needRebuild || bubbleViews.isEmpty()) {
            setupLayout()
        } else {
            notifyBlurSceneChanged()
        }
    }

    /**
     * 重建蜂窝网格布局并绑定气泡
     */
    fun setupLayout(w: Int = width, h: Int = height) {
        val targetW = if (w > 0) w else resources.displayMetrics.widthPixels
        val targetH = if (h > 0) h else resources.displayMetrics.heightPixels
        if (targetW <= 0 || targetH <= 0) return

        // 重建前暂存：若存在未保存的排版修改，先把当前视觉位置写回 item 字段并记住顺序，
        // 避免视图重建把 tentative 的拖拽/自由摆放结果丢回旧状态
        val hadUnsavedChanges = bubbleViews.isNotEmpty() && hasUnsavedLayoutChanges()
        val prevOrderIds = bubbleViews.map { it.item.id }
        if (hadUnsavedChanges) {
            settleBubblePositionsImmediately()
            flushOffsetsToItems()
        }

        lastLayoutW = targetW
        lastLayoutH = targetH

        bubblesContainer.removeAllViews()
        bubbleViews.clear()
        freePlacedBubbles.clear()
        activeShortcuts.clear()
        gridPoints.clear()
        currentSelectedBubble = null

        val fod = calculateFodCoordinates(targetW.toFloat(), targetH.toFloat())
        val fodX = fod.x
        val fodY = fod.y
        val regionCenterY = fodY - dpToPx(config.offsetY.toFloat())
        val regionDiameterPx = dpToPx(config.regionDiameter.toFloat())
        val regionRadiusPx = regionDiameterPx / 2f
        val iconSizePx = dpToPx(config.iconSize.toFloat())

        lastFodX = fodX
        lastFodY = fodY
        lastRegionCenterX = fodX
        lastRegionCenterY = regionCenterY
        lastRegionDiameterPx = regionDiameterPx

        // 筛选有效快捷项（提前：扇区触发模式需按数量决定底衬与命中策略）
        for (item in config.shortcuts) {
            if (item.enabled) activeShortcuts.add(item)
        }
        // 按「图标数量」上限截取排序最前的 N 个启用项（0 = 显示全部）
        if (config.maxIconCount > 0 && activeShortcuts.size > config.maxIconCount) {
            val limited = activeShortcuts.take(config.maxIconCount)
            activeShortcuts.clear()
            activeShortcuts.addAll(limited)
        }
        // 重建时保持上一次的排版顺序（未保存的拖拽排序不因重建而丢失）
        if (prevOrderIds.isNotEmpty() && prevOrderIds.size == activeShortcuts.size &&
            prevOrderIds.toSet() == activeShortcuts.map { it.id }.toSet()
        ) {
            val orderMap = prevOrderIds.mapIndexed { idx, id -> id to idx }.toMap()
            activeShortcuts.sortBy { orderMap[it.id] ?: Int.MAX_VALUE }
        }

        // 空心圆环扇区触发模式：圆环布局 + 开关开启 + 快捷方式少于 6 个时生效
        val sectorMode = config.layoutStyle == HyperOpenConfig.LAYOUT_RING &&
            config.ringSectorMode &&
            activeShortcuts.isNotEmpty() &&
            activeShortcuts.size < RingSectorPacking.MAX_SECTOR_COUNT_EXCLUSIVE

        // 定位底衬：完全跟随「显示圆形区域底衬」开关（分隔虚线已由全屏覆盖层独立绘制）
        regionBackdrop.visibility = if (config.showRegionBg) View.VISIBLE else View.GONE
        regionBackdrop.setSystemBlurEnabled(false)
        regionBackdrop.setStyle(resolvedStyle)
        regionBackdrop.setBlurRadius(if (config.showRegionBg) config.regionBlurRadius.toFloat() else 0f)
        regionBackdrop.setDimAlpha(if (config.showRegionBg) config.regionDimAlpha else 0f)
        regionBackdrop.updateGeometry(regionRadiusPx)
        val rLp = LayoutParams(regionDiameterPx.toInt(), regionDiameterPx.toInt()).apply {
            leftMargin = (fodX - regionRadiusPx).toInt()
            topMargin = (regionCenterY - regionRadiusPx).toInt()
        }
        regionBackdrop.layoutParams = rLp

        // 确保顶部提示胶囊与悬浮返回按钮样式即时更新
        applyHeaderPillStyleAndLayout()
        refreshFloatingBackButtonBackground()

        // 计算布局坐标（图标紧凑程度对蜂窝、空心圆环与弧形扇面通用）
        val spacingOffsetPx = dpToPx(config.iconSpacingOffset.toFloat())
        stockSpecs.clear()
        // 每次重建先清空扇区几何，仅扇区模式分支重新赋值（防止切换布局后残留旧判定）
        ringSectorSpec = null
        val points = if (config.layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
            // 系统经典扇形布局：调用 StockFanPacking
            val stockRadius = maxOf(regionRadiusPx * 0.95f, iconSizePx * 1.8f)
            val specs = StockFanPacking.calculate(
                activeShortcuts.size,
                fodX,
                fodY,
                stockRadius,
                iconSizePx
            )
            stockSpecs.addAll(specs)
            specs.map { it.center }
        } else if (config.layoutStyle == HyperOpenConfig.LAYOUT_RING) {
            if (sectorMode) {
                // 扇区触发：图标沿圆周均分排布，圆内取消、圆外按数量均分为扇形
                val spec = RingSectorPacking.calculate(
                    activeShortcuts.size,
                    fodX,
                    regionCenterY,
                    regionRadiusPx,
                    iconSizePx,
                    spacingOffsetPx
                )
                ringSectorSpec = spec
                spec?.points ?: emptyList()
            } else {
                // 空心同心圆环：中心留空，图标排布在 ringCount 圈同心环上
                ringSectorSpec = null
                HollowRingPacking.calculatePoints(
                    activeShortcuts.size,
                    fodX,
                    regionCenterY,
                    regionRadiusPx,
                    iconSizePx,
                    spacingOffsetPx,
                    config.ringCount
                )
            }
        } else if (config.layoutStyle == HyperOpenConfig.LAYOUT_ARC) {
            // 弧形扇面：图标沿 150° 弧带排布，单弧放不下自动转双弧
            ArcFanPacking.calculatePoints(
                activeShortcuts.size,
                fodX,
                regionCenterY,
                regionRadiusPx,
                iconSizePx,
                spacingOffsetPx
            )
        } else {
            CircularHoneycombPacking.calculatePoints(
                activeShortcuts.size,
                fodX,
                regionCenterY,
                regionRadiusPx,
                iconSizePx,
                spacingOffsetPx
            )
        }
        // 布局容量不足时截断，保证快捷项与点集一一对应
        if (activeShortcuts.size > points.size) {
            activeShortcuts.subList(points.size, activeShortcuts.size).clear()
        }
        gridPoints.addAll(points)

        // 扇区分隔虚线：全屏覆盖层绘制取消区小圆 + N 条射出屏幕的径向虚线（与底衬解耦）
        val sectorSpecForOverlay = ringSectorSpec
        if (sectorMode && sectorSpecForOverlay != null && sectorSpecForOverlay.count == activeShortcuts.size) {
            sectorOverlay.update(
                true,
                sectorSpecForOverlay.count,
                fodX,
                regionCenterY,
                sectorSpecForOverlay.cancelRadius,
                sectorSpecForOverlay.startAngleDeg,
                config.sectorLineColor,
                config.sectorLineAlpha,
                config.sectorDashLength,
                config.sectorDashGap,
                config.sectorLineWidth
            )
        } else {
            sectorOverlay.clear()
        }

        // 实例化气泡（预览界面禁用底层窗口级 BackgroundBlur，由 Compose 的 textureBlur 渲染实时壁纸模糊，确保样式与真实锁屏 1:1 对齐且无双层叠加闪烁）
        for (i in 0 until points.size) {
            val item = activeShortcuts[i]
            val pt = points[i]
            // 扇区触发模式下图标位置由扇区几何决定，忽略自定义偏移（禁止自定义排版）
            val finalX = if (sectorMode) pt.x else pt.x + dpToPx(item.customOffsetX)
            val finalY = if (sectorMode) pt.y else pt.y + dpToPx(item.customOffsetY)

            val bubble = CircularBubbleView(
                context, item, resolvedStyle, iconSizePx.toInt(), context.classLoader, config
            )
            bubble.setSystemBlurEnabled(false)
            bubble.setIconAlpha(config.iconAlpha)
            bubble.setOnScaleChangedListener {
                notifyBlurSceneChanged()
            }
            bubble.targetX = finalX
            bubble.targetY = finalY

            if (item.customOffsetX != 0f || item.customOffsetY != 0f) {
                freePlacedBubbles.add(bubble)
            }

            val bLp = LayoutParams(iconSizePx.toInt(), iconSizePx.toInt()).apply {
                leftMargin = (finalX - iconSizePx / 2f).toInt()
                topMargin = (finalY - iconSizePx / 2f).toInt()
            }
            bubble.layoutParams = bLp

            bubblesContainer.addView(bubble)
            bubbleViews.add(bubble)
        }

        // 立即同步执行 bubblesContainer 与内部所有气泡及 iconView 的 measure + layout
        val wSpec = MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY)
        bubblesContainer.measure(wSpec, hSpec)
        bubblesContainer.layout(0, 0, targetW, targetH)
        if (config.showRegionBg) {
            val rSpec = MeasureSpec.makeMeasureSpec(regionDiameterPx.toInt(), MeasureSpec.EXACTLY)
            regionBackdrop.measure(rSpec, rSpec)
            regionBackdrop.layout(
                rLp.leftMargin,
                rLp.topMargin,
                rLp.leftMargin + regionDiameterPx.toInt(),
                rLp.topMargin + regionDiameterPx.toInt()
            )
        }
        if (::sectorOverlay.isInitialized) {
            sectorOverlay.measure(wSpec, hSpec)
            sectorOverlay.layout(0, 0, targetW, targetH)
        }

        bubblesContainer.invalidate()
        if (!hadUnsavedChanges) {
            // 首次建立/无待保存修改时捕获会话快照与原始偏移（供返回键「是否保存」判断与「放弃」还原）
            sessionSnapshot = captureLayoutSignature()
            pristineOffsets = activeShortcuts.associate {
                it.id to floatArrayOf(it.customOffsetX, it.customOffsetY)
            }
        }
        notifyBlurSceneChanged()
        post { notifyBlurSceneChanged() }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 如果点击发生在左上角悬浮返回按钮区域，放行给子 View 处理点击
        floatingBackButton?.let { btn ->
            if (btn.visibility == View.VISIBLE) {
                val btnLp = btn.layoutParams as? LayoutParams
                if (btnLp != null) {
                    val l = btnLp.leftMargin
                    val t = btnLp.topMargin
                    val r = l + btn.width.coerceAtLeast(dpToPx(48f).toInt())
                    val b = t + btn.height.coerceAtLeast(dpToPx(48f).toInt())
                    if (ev.x >= l && ev.x <= r && ev.y >= t && ev.y <= b) {
                        return false
                    }
                }
            }
        }
        // 右上角吸附开关（仅排版模式可见）：放行触摸给按钮自身处理点击
        snapToggleButton?.let { btn ->
            if (btn.visibility == View.VISIBLE) {
                val slop = dpToPx(6f)
                if (ev.x >= btn.left - slop && ev.x <= btn.right + slop &&
                    ev.y >= btn.top - slop && ev.y <= btn.bottom + slop) {
                    return false
                }
            }
        }
        // 其余区域拦截触摸事件以便处理全局滑动动力学与拖拽
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                dragTouchStartX = x
                dragTouchStartY = y
                pendingPressBubble = findTouchedBubble(x, y)

                if (pendingPressBubble != null) {
                    if (isEditMode) {
                        // 编辑模式下直接抓取拖拽
                        startDragging(pendingPressBubble!!, x, y)
                    } else {
                        // 准备长按识别 (450ms 进入自定义排版)，同时立即展现按压鱼眼放大反馈
                        removeCallbacks(longPressRunnable)
                        postDelayed(longPressRunnable, 450)
                        handleFingerHover(x, y)
                    }
                } else if (!isEditMode) {
                    handleFingerHover(x, y)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val distFromDown = Math.hypot((x - dragTouchStartX).toDouble(), (y - dragTouchStartY).toDouble()).toFloat()
                if (distFromDown > dpToPx(12f) && !isDraggingActive) {
                    removeCallbacks(longPressRunnable)
                }

                if (isDraggingActive && draggingBubble != null) {
                    // 拖拽中：跟随手指自由移动
                    val iconSizePx = dpToPx(config.iconSize.toFloat())
                    val lp = draggingBubble!!.layoutParams as LayoutParams
                    lp.leftMargin = (x - iconSizePx / 2f).toInt()
                    lp.topMargin = (y - iconSizePx / 2f).toInt()
                    draggingBubble!!.layoutParams = lp

                    // 检测碰撞并实时重排其余气泡
                    checkDragReorder(x, y)
                    notifyBlurSceneChanged()
                } else if (!isEditMode) {
                    // 普通模拟指纹滑动模式：物理鱼眼动力学（手指停住时保持当前放大状态，绝不失效）
                    handleFingerHover(x, y)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                removeCallbacks(longPressRunnable)

                if (isDraggingActive && draggingBubble != null) {
                    // 结束拖拽：吸附回最终目标槽位
                    finishDragging()
                } else if (!isEditMode) {
                    resetHoverSelection()
                } else if (isEditMode && pendingPressBubble == null) {
                    // 编辑模式下点击空白处退出
                    exitEditMode()
                }
                pendingPressBubble = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findTouchedBubble(x: Float, y: Float): CircularBubbleView? {
        val threshold = dpToPx(config.iconSize.toFloat()) * 0.75f
        var best: CircularBubbleView? = null
        var minDist = Float.MAX_VALUE
        for (b in bubbleViews) {
            val dist = Math.hypot((x - b.targetX).toDouble(), (y - b.targetY).toDouble()).toFloat()
            if (dist <= threshold && dist < minDist) {
                minDist = dist
                best = b
            }
        }
        return best
    }

    /** 当前是否处于空心圆环扇区触发模式（该模式禁止自定义排版） */
    fun isRingSectorActive(): Boolean = ringSectorSpec != null &&
        config.layoutStyle == HyperOpenConfig.LAYOUT_RING &&
        config.ringSectorMode

    /**
     * 进入自定义排版模式
     */
    fun enterEditMode(initialTarget: CircularBubbleView? = null) {
        if (isEditMode) return
        isEditMode = true
        triggerHaptic()

        editBanner.visibility = View.VISIBLE
        editBanner.alpha = 0f
        editBanner.animate().alpha(1f).setDuration(200).start()

        snapToggleButton?.let {
            it.text = snapToggleText()
            it.alpha = 0f
            it.visibility = View.VISIBLE
            it.animate().alpha(1f).setDuration(200).start()
        }

        HintTextStyler.reset(headerTitle, true)
        HintTextStyler.reset(headerSubtitle, false)
        headerTitle.text = "自定义排版模式"
        headerTitle.setTextColor(0xFFFF9933.toInt())
        headerSubtitle.text = "长按并拖动图标交换位置，点击空白完成"

        // 所有气泡进入统一排版微放状态，清除单点悬停缩放
        for (b in bubbleViews) {
            b.setSelectedState(false)
            b.setIconAlpha(config.iconAlpha)
            b.applyScale(1.05f)
        }

        if (initialTarget != null) {
            startDragging(initialTarget, dragTouchStartX, dragTouchStartY)
        } else {
            notifyBlurSceneChanged()
        }
    }

    /** 退出排版编辑模式（仅 UI 状态复位，不提交保存；保存走 commitLayoutChanges()，由返回键确认弹窗触发） */
    fun exitEditMode() {
        if (!isEditMode) return
        isEditMode = false
        triggerHaptic()

        editBanner.animate().alpha(0f).setDuration(150).withEndAction {
            editBanner.visibility = View.GONE
        }.start()

        snapToggleButton?.let { btn ->
            btn.animate().alpha(0f).setDuration(150).withEndAction {
                btn.visibility = View.GONE
            }.start()
        }

        HintTextStyler.reset(headerTitle, true)
        HintTextStyler.reset(headerSubtitle, false)
        headerTitle.text = "滑动选择快捷方式"
        headerSubtitle.text = "移至目标图标后松手直接打开"

        for (b in bubbleViews) {
            b.setSelectedState(false)
            b.applyScale(1.0f)
        }
        notifyBlurSceneChanged()
    }

    /**
     * 立即结算所有进行中的让位/吸附动画：取消动画并把目标坐标写死进 LayoutParams。
     * 防止提交排版时读到 .x()/.y() 动画的中间值（旧版返回键退出后图标错位的根因之一）。
     */
    private fun settleBubblePositionsImmediately() {
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        for (b in bubbleViews) {
            try {
                b.animate().setListener(null).setUpdateListener(null).cancel()
            } catch (_: Throwable) {}
            val lp = b.layoutParams as? LayoutParams ?: continue
            if (b.targetX != 0f || b.targetY != 0f) {
                lp.leftMargin = (b.targetX - iconSizePx / 2f).toInt()
                lp.topMargin = (b.targetY - iconSizePx / 2f).toInt()
                b.layoutParams = lp
            }
            b.translationX = 0f
            b.translationY = 0f
        }
    }

    /** 把每项当前视觉位置相对蜂窝槽位的偏移写入 ShortcutItem（dp，setupLayout 可精确回环还原） */
    private fun flushOffsetsToItems() {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        for (i in 0 until bubbleViews.size) {
            val b = bubbleViews[i]
            val pt = if (i < gridPoints.size) gridPoints[i] else PointF(b.targetX, b.targetY)
            val lp = b.layoutParams as? LayoutParams ?: continue
            val curCenterX = lp.leftMargin + iconSizePx / 2f + b.translationX
            val curCenterY = lp.topMargin + iconSizePx / 2f + b.translationY
            b.item.customOffsetX = (curCenterX - pt.x) / density
            b.item.customOffsetY = (curCenterY - pt.y) / density
            b.targetX = curCenterX
            b.targetY = curCenterY
        }
    }

    /** 当前布局签名：每项 id + 相对蜂窝槽位的 dp 偏移（含 translation，所见即所得） */
    private fun captureLayoutSignature(): List<LayoutSig> {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        return bubbleViews.mapIndexed { i, b ->
            val pt = if (i < gridPoints.size) gridPoints[i] else PointF(b.targetX, b.targetY)
            val lp = b.layoutParams as? LayoutParams
            if (lp != null) {
                LayoutSig(
                    b.item.id,
                    (lp.leftMargin + iconSizePx / 2f + b.translationX - pt.x) / density,
                    (lp.topMargin + iconSizePx / 2f + b.translationY - pt.y) / density
                )
            } else {
                LayoutSig(b.item.id, 0f, 0f)
            }
        }
    }

    /** 是否存在未保存的排版修改（顺序变化或偏移差 > 0.6dp；容差吸收 .toInt() 取整噪声） */
    fun hasUnsavedLayoutChanges(): Boolean {
        if (bubbleViews.isEmpty()) return false
        val cur = captureLayoutSignature()
        if (cur.size != sessionSnapshot.size) return true
        for (i in cur.indices) {
            val a = cur[i]
            val b = sessionSnapshot[i]
            if (a.id != b.id) return true
            if (Math.abs(a.offX - b.offX) > 0.6f || Math.abs(a.offY - b.offY) > 0.6f) return true
        }
        return false
    }

    /**
     * 提交排版（返回确认弹窗「保存并退出」）：结算动画 → 吸附归位 → 偏移写回 item → 回调 onOrderChanged。
     * 与旧版 exitEditMode 的保存分支语义等价，但保证读到的都是最终位置而非动画中间值。
     */
    fun commitLayoutChanges() {
        if (bubbleViews.isEmpty()) return
        if (isDraggingActive && draggingBubble != null) {
            finishDragging()
        }
        settleBubblePositionsImmediately()
        if (snapEnabled && freePlacedBubbles.isNotEmpty()) {
            // 开启吸附时自由摆放的图标归位到蜂窝槽位（lp 立即写槽位，translation 动画仅作视觉过渡）
            snapAllBubblesToGrid(animated = true)
        }
        flushOffsetsToItems()
        if (snapEnabled) {
            for (b in bubbleViews) {
                b.item.customOffsetX = 0f
                b.item.customOffsetY = 0f
            }
        }
        exitEditMode()
        onOrderChanged(bubbleViews.map { it.item })
        sessionSnapshot = captureLayoutSignature()
        pristineOffsets = bubbleViews.associate {
            it.item.id to floatArrayOf(it.item.customOffsetX, it.item.customOffsetY)
        }
    }

    /** 放弃排版修改（返回确认弹窗「放弃」）：把 item 偏移字段还原为进入会话时的原始值（item 与外部列表共享对象，必须还原） */
    fun discardLayoutChanges() {
        if (isEditMode) {
            isEditMode = false
            editBanner.visibility = View.GONE
            snapToggleButton?.visibility = View.GONE
        }
        for (b in bubbleViews) {
            val p = pristineOffsets[b.item.id] ?: continue
            b.item.customOffsetX = p[0]
            b.item.customOffsetY = p[1]
        }
    }

    private fun startDragging(bubble: CircularBubbleView, touchX: Float, touchY: Float) {
        isDraggingActive = true
        draggingBubble = bubble
        bubble.setSelectedState(false)
        bubble.bringToFront()
        bubble.applyScale(1.22f)
        notifyBlurSceneChanged()
        triggerHaptic()
    }

    private fun checkDragReorder(touchX: Float, touchY: Float) {
        val currentIdx = bubbleViews.indexOf(draggingBubble)
        if (currentIdx < 0) return

        var closestSlot = currentIdx
        var minDistance = Float.MAX_VALUE
        val iconSizePx = dpToPx(config.iconSize.toFloat())

        for (i in 0 until gridPoints.size) {
            val pt = gridPoints[i]
            val dist = Math.hypot((touchX - pt.x).toDouble(), (touchY - pt.y).toDouble()).toFloat()
            if (dist < minDistance) {
                minDistance = dist
                closestSlot = i
            }
        }

        if (closestSlot != currentIdx && minDistance < iconSizePx * 0.75f) {
            // 在内存中重排 View 与 Item
            val movingBubble = bubbleViews.removeAt(currentIdx)
            bubbleViews.add(closestSlot, movingBubble)

            // 平滑将其他气泡过渡位移到新槽位
            for (i in 0 until bubbleViews.size) {
                val b = bubbleViews[i]
                val slotPt = gridPoints[i]
                // 关闭吸附时，被自由摆放的图标保持原位，不参与槽位重排
                if (b != draggingBubble && b in freePlacedBubbles) {
                    continue
                }
                b.targetX = slotPt.x
                b.targetY = slotPt.y

                if (b != draggingBubble) {
                    val lp = b.layoutParams as LayoutParams
                    val targetLeft = (slotPt.x - iconSizePx / 2f).toInt()
                    val targetTop = (slotPt.y - iconSizePx / 2f).toInt()

                    b.animate()
                        .x(targetLeft.toFloat())
                        .y(targetTop.toFloat())
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(1.1f))
                        .setUpdateListener { notifyBlurSceneChanged() }
                        .withEndAction {
                            lp.leftMargin = targetLeft
                            lp.topMargin = targetTop
                            b.layoutParams = lp
                            b.translationX = 0f
                            b.translationY = 0f
                            notifyBlurSceneChanged()
                        }
                        .start()
                }
            }
            notifyBlurSceneChanged()
            triggerHaptic()
        }
    }

    private fun finishDragging() {
        val b = draggingBubble ?: return
        isDraggingActive = false
        val idx = bubbleViews.indexOf(b)
        val iconSizePx = dpToPx(config.iconSize.toFloat())

        if (idx >= 0 && idx < gridPoints.size) {
            if (!snapEnabled) {
                // 关闭吸附：保持在放手位置，仅更新点击/悬停判定中心，顺序仍按最近槽位记录
                val lpFree = b.layoutParams as LayoutParams
                b.targetX = lpFree.leftMargin + iconSizePx / 2f
                b.targetY = lpFree.topMargin + iconSizePx / 2f
                freePlacedBubbles.add(b)
                b.applyScale(1.05f)
            } else {
                freePlacedBubbles.remove(b)
                val pt = gridPoints[idx]
                b.targetX = pt.x
                b.targetY = pt.y
                val targetLeft = (pt.x - iconSizePx / 2f).toInt()
                val targetTop = (pt.y - iconSizePx / 2f).toInt()

                b.applyScale(1.05f)

                val lp = b.layoutParams as LayoutParams
                lp.leftMargin = targetLeft
                lp.topMargin = targetTop
                b.layoutParams = lp
                b.translationX = 0f
                b.translationY = 0f
            }
        }
        draggingBubble = null
        notifyBlurSceneChanged()
        triggerHaptic()
    }

    /**
     * 模拟指纹滑动物理动力学
     */
    private fun handleFingerHover(touchX: Float, touchY: Float) {
        val isStockSystem = (config.layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK)
        val useStockRegion = isStockSystem && config.stockSystemRegion

        val influenceRadiusPx = dpToPx(config.influenceRadius.toFloat())
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        val hitDistanceThreshold = iconSizePx * 0.95f

        var closest: CircularBubbleView? = null
        var minDistance = Float.MAX_VALUE

        val sectorSpec = ringSectorSpec
        val useRingSector = isRingSectorActive() && sectorSpec != null

        if (useRingSector) {
            // 空心圆环扇区触发：中心圆内取消，圆外按角度均分扇区命中，不做距离鱼眼
            val hitIndex = RingSectorPacking.findTouchedIndex(
                sectorSpec,
                touchX,
                touchY,
                lastFodX,
                lastRegionCenterY
            )
            if (hitIndex >= 0 && hitIndex < bubbleViews.size) {
                closest = bubbleViews[hitIndex]
                minDistance = 0f
            }
            for (b in bubbleViews) {
                b.applyScale(if (b == closest) config.selectedIconScale else 1.0f)
                b.setIconAlpha(config.iconAlpha)
            }
        } else if (useStockRegion && stockSpecs.isNotEmpty()) {
            val hitIndex = StockFanPacking.findTouchedIndex(stockSpecs, touchX, touchY, lastFodX, lastFodY)
            if (hitIndex >= 0 && hitIndex < bubbleViews.size) {
                closest = bubbleViews[hitIndex]
                minDistance = 0f
            }
            for (b in bubbleViews) {
                val scale = if (b == closest) config.selectedIconScale else 1.0f
                b.applyScale(scale)
                b.setIconAlpha(config.iconAlpha)
            }
        } else {
            for (b in bubbleViews) {
                val dist = Math.hypot((touchX - b.targetX).toDouble(), (touchY - b.targetY).toDouble()).toFloat()
                if (dist < minDistance) {
                    minDistance = dist
                    closest = b
                }

                var scale = 1.0f
                var currentAlpha = config.iconAlpha
                if (dist < influenceRadiusPx) {
                    val factor = Math.cos((dist / influenceRadiusPx) * (Math.PI / 2.0)).toFloat()
                    scale = 1.0f + (config.maxScale - 1.0f) * factor
                    currentAlpha = config.iconAlpha + (config.fisheyeZoomAlpha - config.iconAlpha) * factor
                }
                b.applyScale(scale)
                b.setIconAlpha(currentAlpha)
            }
        }

        val isSelected = if (useRingSector || useStockRegion) (closest != null) else (closest != null && minDistance < hitDistanceThreshold)

        if (isSelected) {
            if (currentSelectedBubble != closest) {
                currentSelectedBubble?.setSelectedState(false)

                closest!!.setSelectedState(true)
                closest.bringToFront()
                currentSelectedBubble = closest

                // 更新顶部标题（含 hint* 自定义样式：颜色/透明度/投影/描边/字重/字距/显隐）
                HintTextStyler.apply(headerTitle, closest.item, true)
                HintTextStyler.apply(headerSubtitle, closest.item, false)
                triggerSlideHaptic()
            }
        } else {
            if (currentSelectedBubble != null) {
                currentSelectedBubble?.setSelectedState(false)
                currentSelectedBubble = null
                HintTextStyler.reset(headerTitle, true)
                HintTextStyler.reset(headerSubtitle, false)
                headerTitle.text = "滑动选择快捷方式"
                headerSubtitle.text = "移至目标图标后松手直接打开"
            }
        }
        notifyBlurSceneChanged()
    }

    private fun resetHoverSelection() {
        currentSelectedBubble?.setSelectedState(false)
        currentSelectedBubble = null
        for (b in bubbleViews) {
            b.applyScale(1.0f)
            b.setIconAlpha(config.iconAlpha)
        }
        HintTextStyler.reset(headerTitle, true)
        HintTextStyler.reset(headerSubtitle, false)
        headerTitle.text = "滑动选择快捷方式"
        headerSubtitle.text = "移至目标图标后松手直接打开"
        notifyBlurSceneChanged()
    }

    private fun notifyBlurSceneChanged() {
        val listener = onBlurSceneChanged ?: return
        val iconSizePx = dpToPx(config.iconSize.toFloat())
        val spots = ArrayList<BubbleBlurSpot>(bubbleViews.size)
        for (b in bubbleViews) {
            val lp = b.layoutParams as? LayoutParams
            val cx = if (lp != null) lp.leftMargin + iconSizePx / 2f + b.translationX else b.targetX
            val cy = if (lp != null) lp.topMargin + iconSizePx / 2f + b.translationY else b.targetY
            val scale = b.currentScale.coerceAtLeast(0.1f)
            // 内缩 3px (直径减 6px) 使预览毛玻璃圆严格收拢在 2.2px 气泡描边内侧，放大时绝不溢出边框
            val effectiveDiameter = (iconSizePx - 6f).coerceAtLeast(1f) * scale
            spots.add(
                BubbleBlurSpot(
                    id = b.item.id,
                    centerX = cx,
                    centerY = cy,
                    diameterPx = effectiveDiameter,
                    alpha = b.iconAlpha,
                )
            )
        }

        val headerLeft = if (::topHeaderContainer.isInitialized && ::headerPill.isInitialized) {
            (topHeaderContainer.left + headerPill.left).toFloat()
        } else 0f
        val headerTop = if (::topHeaderContainer.isInitialized && ::headerPill.isInitialized) {
            (topHeaderContainer.top + headerPill.top).toFloat()
        } else dpToPx(config.headerTopMargin.toFloat())
        val headerW = if (::headerPill.isInitialized && config.showHeaderPill) headerPill.width.toFloat() else 0f
        val headerH = if (::headerPill.isInitialized && config.showHeaderPill) headerPill.height.toFloat() else 0f

        listener(
            PreviewBlurSceneState(
                regionCenterX = lastRegionCenterX,
                regionCenterY = lastRegionCenterY,
                regionDiameterPx = lastRegionDiameterPx,
                headerLeftPx = headerLeft,
                headerTopPx = headerTop,
                headerWidthPx = headerW,
                headerHeightPx = headerH,
                backBtnLeftPx = dpToPx(18f),
                backBtnTopPx = dpToPx(46f),
                backBtnSizePx = (dpToPx(40f) - 6f).coerceAtLeast(1f),
                bubbles = spots,
            )
        )
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
