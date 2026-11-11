package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.layout.CircularHoneycombPacking;
import io.github.qmryqmm.hyperopen.layout.ArcFanPacking;
import io.github.qmryqmm.hyperopen.layout.HollowRingPacking;
import io.github.qmryqmm.hyperopen.layout.RingSectorPacking;
import io.github.qmryqmm.hyperopen.layout.StockFanPacking;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.BubblePanelAnimator;
import io.github.qmryqmm.hyperopen.util.FisheyeScale;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomHyperOpenContainer extends FrameLayout {

    private static final OvershootInterpolator EXPAND_INTERPOLATOR = new OvershootInterpolator(1.2f);

    private HyperOpenConfig config;
    private final ClassLoader systemuiClassLoader;
    private int resolvedStyle;
    private int lastBuiltIconSizePx = -1;

    private float fodCenterX = 540f;
    private float fodCenterY = 1920f;

    private LinearLayout headerPill;
    private TextView headerTitle;
    private TextView headerSubtitle;

    private CircularRegionBackdropView regionBackdrop;
    /** 全屏扇区分隔虚线覆盖层（独立于圆形底衬，虚线可延伸出屏幕） */
    private SectorOverlayView sectorOverlay;
    private FrameLayout bubblesContainer;
    private final List<CircularBubbleView> bubbles = new ArrayList<>();
    private final List<StockFanPacking.StockItemSpec> stockSpecs = new ArrayList<>();
    /** 空心圆环扇区触发模式的几何规格（非扇区模式时为 null） */
    private RingSectorPacking.Result ringSectorSpec = null;
    private CircularBubbleView currentSelectedBubble = null;
    private CircularBubbleView pendingFolderBubble = null;
    private CircularBubbleView activeFolderBubble = null;
    private final List<CircularBubbleView> folderSubBubbles = new ArrayList<>();
    private CircularBubbleView currentSelectedSubBubble = null;

    private final Runnable folderHoverRunnable = new Runnable() {
        @Override
        public void run() {
            if (pendingFolderBubble != null && pendingFolderBubble == currentSelectedBubble) {
                expandFolder(pendingFolderBubble);
            }
        }
    };

    private Vibrator vibrator;

    // 240Hz 触摸采样折叠（Coalescing）：每帧最多向主线程投递一次 onFingerMove，实现零 Runnable 分配
    private volatile float pendingTouchX;
    private volatile float pendingTouchY;
    private final AtomicBoolean movePending = new AtomicBoolean(false);
    private final Runnable coalescedMoveTask = new Runnable() {
        @Override
        public void run() {
            movePending.set(false);
            if (getVisibility() == View.VISIBLE) {
                onFingerMove(pendingTouchX, pendingTouchY);
            }
        }
    };

    public CustomHyperOpenContainer(Context context, HyperOpenConfig config, ClassLoader classLoader) {
        super(context);
        this.config = config;
        this.systemuiClassLoader = classLoader;
        init();
    }

    private void init() {
        resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(getContext(), config.materialStyle);
        initVibrator();
        buildHeader();

        // 圆形区域底衬
        regionBackdrop = new CircularRegionBackdropView(getContext(), systemuiClassLoader);
        regionBackdrop.setStyle(resolvedStyle);
        if (config != null) {
            regionBackdrop.setGlassParams(HyperOSMaterialFactory.GlassParams.fromConfig(config));
            regionBackdrop.setBlurRadius(config.regionBlurRadius);
            regionBackdrop.setDimAlpha(config.regionDimAlpha);
        }
        addView(regionBackdrop);

        // 扇区分隔虚线覆盖层：全屏铺设，虚线一直延伸到屏幕外；关闭底衬后只剩虚线与图标
        sectorOverlay = new SectorOverlayView(getContext());
        addView(sectorOverlay);

        // 图标容器 (允许子 View 放大溢出，不被矩形容器拦截)
        bubblesContainer = new FrameLayout(getContext());
        bubblesContainer.setClipChildren(false);
        bubblesContainer.setClipToPadding(false);
        setClipChildren(false);
        setClipToPadding(false);
        addView(bubblesContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void updateConfig(HyperOpenConfig newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
            if (config.layoutStyle != HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
                stockSpecs.clear();
            }
            if (config.layoutStyle != HyperOpenConfig.LAYOUT_RING || !config.ringSectorMode) {
                ringSectorSpec = null;
            }
            this.resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(getContext(), config.materialStyle);
            HyperOSMaterialFactory.GlassParams glassParams = HyperOSMaterialFactory.GlassParams.fromConfig(config);
            if (regionBackdrop != null) {
                regionBackdrop.setStyle(resolvedStyle);
                regionBackdrop.setGlassParams(glassParams);
                regionBackdrop.setBlurRadius(config.showRegionBg ? config.regionBlurRadius : 0);
                regionBackdrop.setDimAlpha(config.showRegionBg ? config.regionDimAlpha : 0f);
                // 扇区分隔线样式（颜色/透明度/虚线规格）热生效
                if (ringSectorSpec != null) {
                    float liveCenterY = fodCenterY - dpToPx(config.offsetY);
                    sectorOverlay.update(true, ringSectorSpec.count,
                            fodCenterX, liveCenterY,
                            ringSectorSpec.cancelRadius, ringSectorSpec.startAngleDeg,
                            config.sectorLineColor, config.sectorLineAlpha,
                            config.sectorDashLength, config.sectorDashGap, config.sectorLineWidth);
                } else {
                    sectorOverlay.clear();
                }
            }
            // 顶部提示参数（位置、文字大小、居中、模糊与背景）热生效
            applyHeaderPillStyleAndLayout();
            // 气泡材质、模糊半径、留白比例、圆角比例与透明度热生效
            for (CircularBubbleView b : bubbles) {
                b.setMaterialStyle(resolvedStyle);
                b.setBlurRadius(config.bubbleBlurRadius);
                b.setDimAlpha(config.bubbleDimAlpha);
                b.setBubblePaddingPercent(config.bubblePaddingPercent);
                b.setCornerRadiusPercent(config.iconCornerRadius);
                b.setIconAlpha(config.iconAlpha);
            }
        }
    }

    private void initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 统一执行震动反馈（支持系统原生线性马达波形库、解耦滑动与展开、制动反向脉冲、双脉冲间隔与自定义时长）
     */
    public static void performConfiguredHaptic(Vibrator vibrator, HyperOpenConfig cfg) {
        performConfiguredHaptic(null, vibrator, cfg, false);
    }

    public static void performConfiguredHaptic(Context context, Vibrator vibrator, HyperOpenConfig cfg) {
        performConfiguredHaptic(context, vibrator, cfg, false);
    }

    /**
     * 执行震动反馈（进入展开与手指滑动吸附完全解耦独立控制）：
     * @param isSlideAction true 为手指在展开面板上滑动选中气泡时的吸附震动；false 为唤醒展开/松手确认/测试震动
     */
    public static void performConfiguredHaptic(Context context, Vibrator vibrator, HyperOpenConfig cfg, boolean isSlideAction) {
        if (cfg == null) return;
        if (!isSlideAction && !cfg.hapticEnabled) return;
        if (isSlideAction && !cfg.hapticSlideEnabled) return;

        int mode = isSlideAction ? cfg.hapticSlideMode : cfg.hapticMode;

        // 模式 5：优先尝试调用小米官方线性马达系统级触感 (IHapticFeedBack)
        if (mode == HyperOpenConfig.HAPTIC_MODE_STOCK) {
            String patternName = isSlideAction ? resolveStockWaveName(cfg.hapticStockWaveSlide) : resolveStockWaveName(cfg.hapticStockWaveExpand);
            int baseStrength = isSlideAction ? cfg.hapticSlideStrength : cfg.hapticStrength;
            double intensityRatio = Math.max(0.1, Math.min(2.0, baseStrength / 140.0));
            if (tryMiuiExtHaptic(context, patternName, intensityRatio)) {
                return;
            }
        }

        if (vibrator == null || !vibrator.hasVibrator()) return;

        int baseStrength = isSlideAction ? cfg.hapticSlideStrength : cfg.hapticStrength;
        int strength = Math.max(1, Math.min(255, baseStrength));
        int customDur = isSlideAction ? Math.max(4, Math.min(80, cfg.hapticSlideDurationMs)) : Math.max(4, Math.min(80, cfg.hapticDurationMs));
        int doubleGap = isSlideAction ? Math.max(10, Math.min(60, cfg.hapticSlideDoubleGapMs)) : Math.max(10, Math.min(60, cfg.hapticDoubleGapMs));
        boolean brakePulse = isSlideAction ? cfg.hapticSlideBrakePulse : cfg.hapticBrakePulse;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect;
                switch (mode) {
                    case HyperOpenConfig.HAPTIC_MODE_STOCK:
                        // 降级：对齐小米原生在非线性马达机型上的官方降级参数（展开 24ms，滑动微动 12ms）
                        int stockDur = isSlideAction ? 12 : 24;
                        effect = VibrationEffect.createOneShot(stockDur, Math.min(255, (int) (strength * 1.1f)));
                        break;
                    case HyperOpenConfig.HAPTIC_MODE_TICK:
                        if (brakePulse) {
                            effect = VibrationEffect.createWaveform(
                                new long[]{0, 8, 3, 7},
                                new int[]{0, strength, 0, Math.max(30, (int) (strength * 0.70f))},
                                -1
                            );
                        } else {
                            effect = VibrationEffect.createOneShot(7, Math.max(1, (int) (strength * 0.75f)));
                        }
                        break;
                    case HyperOpenConfig.HAPTIC_MODE_HEAVY:
                        if (brakePulse) {
                            effect = VibrationEffect.createWaveform(
                                new long[]{0, 24, 4, 14},
                                new int[]{0, Math.min(255, (int) (strength * 1.25f)), 0, Math.max(40, (int) (strength * 0.75f))},
                                -1
                            );
                        } else {
                            effect = VibrationEffect.createOneShot(22, Math.min(255, (int) (strength * 1.25f)));
                        }
                        break;
                    case HyperOpenConfig.HAPTIC_MODE_DOUBLE:
                        effect = VibrationEffect.createWaveform(
                            new long[]{0, 9, doubleGap, 11},
                            new int[]{0, Math.max(1, (int) (strength * 0.8f)), 0, strength},
                            -1
                        );
                        break;
                    case HyperOpenConfig.HAPTIC_MODE_CUSTOM:
                        if (brakePulse && customDur >= 12) {
                            int mainDur = Math.max(6, (int) (customDur * 0.60f));
                            int brakeDur = Math.max(4, (int) (customDur * 0.35f));
                            effect = VibrationEffect.createWaveform(
                                new long[]{0, mainDur, 3, brakeDur},
                                new int[]{0, strength, 0, Math.max(25, (int) (strength * 0.65f))},
                                -1
                            );
                        } else {
                            effect = VibrationEffect.createOneShot(customDur, strength);
                        }
                        break;
                    case HyperOpenConfig.HAPTIC_MODE_CLICK:
                    default:
                        if (brakePulse) {
                            effect = VibrationEffect.createWaveform(
                                new long[]{0, 14, 3, 9},
                                new int[]{0, strength, 0, Math.max(30, (int) (strength * 0.70f))},
                                -1
                            );
                        } else {
                            effect = VibrationEffect.createOneShot(12, strength);
                        }
                        break;
                }
                vibrator.vibrate(effect);
            } else {
                int dur = mode == HyperOpenConfig.HAPTIC_MODE_STOCK ? (isSlideAction ? 12 : 24) : customDur;
                vibrator.vibrate(dur);
            }
        } catch (Throwable ignored) {}
    }

    private static String resolveStockWaveName(int waveId) {
        switch (waveId) {
            case HyperOpenConfig.HAPTIC_STOCK_WAVE_MESH_NORMAL:
                return "mesh_normal";
            case HyperOpenConfig.HAPTIC_STOCK_WAVE_FLICK:
                return "flick";
            case HyperOpenConfig.HAPTIC_STOCK_WAVE_POP:
                return "pop";
            case HyperOpenConfig.HAPTIC_STOCK_WAVE_LONG_PRESS:
            default:
                return "long_press";
        }
    }

    /**
     * 尝试调用小米澎湃OS / MIUI 专有的线性马达 RichTap 触觉引擎 (HapticFeedBackImpl / HapticFeedbackUtil)
     * 支持动态缩放振幅强度比例 (ratio 0.1 ~ 2.0，基准为 1.0)
     */
    public static boolean tryMiuiExtHaptic(Context context, String pattern, double intensityRatio) {
        if (context == null) return false;

        // 1. 优先尝试系统界面的全局单例 HapticFeedBackImpl (SystemUI 进程)
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> depClass = Class.forName("com.miui.systemui.MiuiDependency", false, cl);
            Class<?> ifaceClass = Class.forName("com.miui.interfaces.IHapticFeedBack", false, cl);
            java.lang.reflect.Method getMethod = depClass.getMethod("get", Class.class);
            Object impl = getMethod.invoke(null, ifaceClass);
            if (impl != null) {
                // 原生签名：extExtHapticFeedback(int, int, String, int, Handler)
                for (java.lang.reflect.Method m : impl.getClass().getMethods()) {
                    if ("extExtHapticFeedback".equals(m.getName()) && m.getParameterTypes().length == 5) {
                        m.invoke(impl, 1, -1, pattern, 0, null);
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. 若在 App 进程（MainActivity / 整体排版预览），通过 miui.util.HapticFeedbackUtil 驱动
        // 关键对齐：传入 always=true，保证在应用未获得前台纯物理焦点时，马达也能按系统原汁原味的振幅完整播放，消除强度衰减
        try {
            Class<?> utilClass = Class.forName("miui.util.HapticFeedbackUtil");
            java.lang.reflect.Constructor<?> ctor = utilClass.getConstructor(Context.class, boolean.class);
            // 传 true 代表 always 模式（不受当前 Window 焦点与 touch event 阻尼压制）
            Object util = ctor.newInstance(context, true);

            java.lang.reflect.Method perf = utilClass.getMethod("performHapticFeedback", String.class, boolean.class);
            Object ok = perf.invoke(util, pattern, false);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean tryMiuiExtHaptic(Context context, String pattern) {
        return tryMiuiExtHaptic(context, pattern, 1.0);
    }

    private void triggerHaptic() {
        performConfiguredHaptic(getContext(), vibrator, config, false);
    }

    private void triggerSlideHaptic() {
        performConfiguredHaptic(getContext(), vibrator, config, true);
    }

    private void buildHeader() {
        headerPill = new LinearLayout(getContext());
        headerPill.setOrientation(LinearLayout.VERTICAL);
        headerPill.setGravity(Gravity.CENTER);
        int padH = dpToPx(24);
        int padV = dpToPx(10);
        headerPill.setPadding(padH, padV, padH, padV);

        // 胶囊轮廓裁切：确保模糊/底衬都沿胶囊形渲染，alpha=0 禁用 3D 阴影多边形本影
        headerPill.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                float r = Math.min(w, h) / 2f;
                outline.setRoundRect(0, 0, w, h, r);
                outline.setAlpha(0.0f);
            }
        });
        headerPill.setClipToOutline(headerPill.getBackground() != null);

        // 尺寸/样式变化时同步刷新背景与硬件模糊圆角
        headerPill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0 && (w != (or - ol) || h != (ob - ot))) {
                applyHeaderPillBackground();
            }
        });

        headerTitle = new StrokeTextView(getContext());
        headerTitle.setText("滑动选择快捷方式");
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        headerTitle.getPaint().setFakeBoldText(true);
        headerPill.addView(headerTitle);

        headerSubtitle = new StrokeTextView(getContext());
        headerSubtitle.setText("移至目标图标后松手直接打开");
        headerSubtitle.setTextColor(0xAAFFFFFF);
        headerSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        headerPill.addView(headerSubtitle);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        lp.topMargin = dpToPx(100);
        addView(headerPill, lp);

        applyHeaderPillStyleAndLayout();
    }

    /**
     * 统一应用顶部提示的显隐、位置高度、字号大小、是否水平居中及胶囊毛玻璃背景
     */
    private void applyHeaderPillStyleAndLayout() {
        if (headerPill == null) return;
        boolean showHeader = config == null || config.showHeaderPill;
        headerPill.setVisibility(showHeader ? View.VISIBLE : View.GONE);

        int titleSp = config != null ? Math.max(12, Math.min(26, config.headerTitleSize)) : 17;
        int subtitleSp = config != null ? Math.max(9, Math.min(18, config.headerSubtitleSize)) : 11;
        boolean centered = config == null || config.headerTextCentered;
        int topMarginDp = config != null ? Math.max(20, Math.min(420, config.headerTopMargin)) : 100;

        if (headerTitle != null) {
            headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp);
            headerTitle.setGravity(centered ? Gravity.CENTER : Gravity.START);
        }
        if (headerSubtitle != null) {
            headerSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSp);
            headerSubtitle.setGravity(centered ? Gravity.CENTER : Gravity.START);
        }
        headerPill.setGravity(centered ? Gravity.CENTER : (Gravity.START | Gravity.CENTER_VERTICAL));

        ViewGroup.LayoutParams rawLp = headerPill.getLayoutParams();
        LayoutParams lp = (rawLp instanceof LayoutParams)
            ? (LayoutParams) rawLp
            : new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = (centered ? Gravity.CENTER_HORIZONTAL : Gravity.START) | Gravity.TOP;
        lp.topMargin = dpToPx(topMarginDp);
        lp.leftMargin = centered ? 0 : dpToPx(24);
        lp.rightMargin = centered ? 0 : dpToPx(24);
        headerPill.setLayoutParams(lp);

        applyHeaderPillBackground();
    }

    /**
     * 按 showHeaderPillBg 开关与 headerBlurRadius 独立模糊半径铺设/移除顶部提示的胶囊玻璃背景。
     * 关闭时背景置 null 且 outline 置空，只保留标题/副标题纯文字悬浮。
     */
    private void applyHeaderPillBackground() {
        if (headerPill == null) return;
        boolean show = config == null || config.showHeaderPillBg;
        if (show) {
            int blurDp = config != null ? config.headerBlurRadius : 25;
            int blurPx = blurDp > 0 ? dpToPx(blurDp) : 0;
            headerPill.setBackground(HyperOSMaterialFactory.createCompositeHeaderPillDrawable(headerPill, resolvedStyle, blurPx, config != null ? config.headerDimAlpha : 0f));
            headerPill.setClipToOutline(true);
        } else {
            if (headerPill.getBackground() != null) {
                headerPill.setBackground(null);
            }
            // 关键：必须先关掉裁切再清背景，否则空 outline 会把整个 View 内容裁没
            headerPill.setClipToOutline(false);
        }
        headerPill.invalidateOutline();
        headerPill.invalidate();
    }

    /**
     * 展开前强制恢复胶囊头部的胶囊形背景与 Z 序：
     * 配置热重载、原生提示 View 竞争注入等场景都可能导致
     * headerPill 背景被覆盖或形状回落为圆形，这里统一自愈。
     * showHeaderPillBg 关闭时反向自愈：确保背景保持移除状态。
     */
    public void ensureHeaderPillIntact() {
        if (headerPill == null) return;
        applyHeaderPillStyleAndLayout();
        if (headerPill.getParent() == this) {
            headerPill.bringToFront();
        }
        headerPill.invalidate();
    }

    /**
     * 判断当前已构建的气泡集合是否与最新启用项完全一致，一致则直接复用 View 实例避免重复 inflation 与 GC
     */
    private boolean canReuseExistingBubbles(List<ShortcutItem> activeItems, int iconSizePx) {
        if (lastBuiltIconSizePx != iconSizePx || bubbles.size() != activeItems.size() || bubbles.isEmpty()) {
            return false;
        }
        for (int i = 0; i < activeItems.size(); i++) {
            ShortcutItem a = bubbles.get(i).item;
            ShortcutItem b = activeItems.get(i);
            if (a == null || b == null) return false;
            if (!Objects.equals(a.id, b.id)
                    || !Objects.equals(a.title, b.title)
                    || !Objects.equals(a.subtitle, b.subtitle)
                    || !Objects.equals(a.actionType, b.actionType)
                    || !Objects.equals(a.actionPayload, b.actionPayload)
                    || !Objects.equals(a.iconPackage, b.iconPackage)
                    || !Objects.equals(a.iconGlyph, b.iconGlyph)
                    || a.tintColor != b.tintColor
                    || a.glyphPadEnabled != b.glyphPadEnabled
                    || a.glyphPadColor != b.glyphPadColor
                    || a.glyphPadMode != b.glyphPadMode
                    || a.appIconCropMode != b.appIconCropMode
                    || a.hintTextColor != b.hintTextColor
                    || Float.compare(a.hintTextAlpha, b.hintTextAlpha) != 0
                    || a.hintTextColorSub != b.hintTextColorSub
                    || Float.compare(a.hintTextAlphaSub, b.hintTextAlphaSub) != 0
                    || a.hintTextShadow != b.hintTextShadow
                    || a.hintTextStroke != b.hintTextStroke
                    || a.hintTextWeight != b.hintTextWeight
                    || Float.compare(a.hintTextLetterSpacing, b.hintTextLetterSpacing) != 0
                    || a.showHintTitle != b.showHintTitle
                    || a.showHintSubtitle != b.showHintSubtitle) {
                return false;
            }
        }
        return true;
    }

    /**
     * 设置指纹中心并根据最新配置构建网格与散开动画
     */
    public void setupAndExpand(float fodX, float fodY) {
        this.fodCenterX = fodX;
        this.fodCenterY = fodY;
        this.currentSelectedBubble = null;

        // 重置顶部提示文字为初始状态（含 hint* 自定义样式清空：颜色/投影/描边/字重/字距/显隐）
        if (headerTitle != null && headerSubtitle != null) {
            HintTextStyler.reset(headerTitle, true, config);
            HintTextStyler.reset(headerSubtitle, false, config);
            headerTitle.setText("滑动选择快捷方式");
            headerSubtitle.setText("移至目标图标后松手直接打开");
        }

        // 胶囊头部自愈：确保每次展开时胶囊背景都是胶囊形且在最上层
        ensureHeaderPillIntact();

        float regionDiameterPx = dpToPx(config.regionDiameter);
        float regionRadiusPx = regionDiameterPx / 2f;
        float regionCenterY = fodCenterY - dpToPx(config.offsetY);
        float iconSizePx = dpToPx(config.iconSize);
        int iconSizeInt = (int) iconSizePx;

        // 筛选启用的快捷项（提前计算：扇区触发模式需要按数量决定底衬与命中策略）
        List<ShortcutItem> activeItems = new ArrayList<>(config.shortcuts.size());
        for (ShortcutItem item : config.shortcuts) {
            if (item.enabled) activeItems.add(item);
        }
        // 按「图标数量」上限截取排序最前的 N 个启用项（0 = 显示全部）
        if (config.maxIconCount > 0 && activeItems.size() > config.maxIconCount) {
            activeItems = new ArrayList<>(activeItems.subList(0, config.maxIconCount));
        }

        // 空心圆环扇区触发模式：圆环布局 + 开关开启 + 快捷方式少于 6 个时生效
        boolean sectorMode = config.layoutStyle == HyperOpenConfig.LAYOUT_RING
                && config.ringSectorMode
                && !activeItems.isEmpty()
                && activeItems.size() < RingSectorPacking.MAX_SECTOR_COUNT_EXCLUSIVE;

        // 更新底衬大小与位置：完全跟随「显示圆形区域底衬」开关（分隔虚线由全屏覆盖层独立绘制）
        regionBackdrop.setVisibility(config.showRegionBg ? View.VISIBLE : View.GONE);
        regionBackdrop.setStyle(resolvedStyle);
        regionBackdrop.setGlassParams(HyperOSMaterialFactory.GlassParams.fromConfig(config));
        regionBackdrop.setBlurRadius(config.regionBlurRadius);
        regionBackdrop.setDimAlpha(config.regionDimAlpha);
        regionBackdrop.updateGeometry(regionRadiusPx);

        ViewGroup.LayoutParams existingRLp = regionBackdrop.getLayoutParams();
        LayoutParams rLp = (existingRLp instanceof LayoutParams)
            ? (LayoutParams) existingRLp
            : new LayoutParams((int) regionDiameterPx, (int) regionDiameterPx);
        rLp.width = (int) regionDiameterPx;
        rLp.height = (int) regionDiameterPx;
        rLp.leftMargin = (int) (fodCenterX - regionRadiusPx);
        rLp.topMargin = (int) (regionCenterY - regionRadiusPx);
        regionBackdrop.setLayoutParams(rLp);

        // 计算布局坐标（紧凑度偏移量对蜂窝、空心圆环与弧形扇面通用）
        float spacingOffsetPx = dpToPx(config.iconSpacingOffset);
        List<PointF> points;
        stockSpecs.clear();
        // 每次重建先清空扇区几何，仅扇区模式分支重新赋值（防止切换布局后残留旧判定）
        ringSectorSpec = null;

        if (config.layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
            // 系统经典扇形布局：直接调用 StockFanPacking（对齐小米系统 45°/60° 扇面与 Region 扇区多边形判定）
            // 扇面扩散半径对齐系统原生（约 140dp 视界尺度）
            float stockRadius = Math.max(regionRadiusPx * 0.95f, iconSizePx * 1.8f);
            List<StockFanPacking.StockItemSpec> specs = StockFanPacking.calculate(
                activeItems.size(),
                fodCenterX,
                fodCenterY,
                stockRadius,
                iconSizePx
            );
            stockSpecs.addAll(specs);
            points = new ArrayList<>(specs.size());
            for (StockFanPacking.StockItemSpec spec : specs) {
                points.add(spec.center);
            }
        } else if (config.layoutStyle == HyperOpenConfig.LAYOUT_RING) {
            if (sectorMode) {
                // 扇区触发：图标沿圆周均分排布，圆内取消、圆外按数量均分为扇形
                ringSectorSpec = RingSectorPacking.calculate(
                    activeItems.size(),
                    fodCenterX,
                    regionCenterY,
                    regionRadiusPx,
                    iconSizePx,
                    spacingOffsetPx
                );
                points = ringSectorSpec != null ? ringSectorSpec.points : new ArrayList<PointF>();
            } else {
                // 空心同心圆环布局：中心留空，图标排布在 ringCount 圈同心环上
                ringSectorSpec = null;
                points = HollowRingPacking.calculatePoints(
                    activeItems.size(),
                    fodCenterX,
                    regionCenterY,
                    regionRadiusPx,
                    iconSizePx,
                    spacingOffsetPx,
                    config.ringCount
                );
            }
        } else if (config.layoutStyle == HyperOpenConfig.LAYOUT_ARC) {
            // 弧形扇面布局：图标沿 150° 弧带排布，单弧放不下自动转双弧
            points = ArcFanPacking.calculatePoints(
                activeItems.size(),
                fodCenterX,
                regionCenterY,
                regionRadiusPx,
                iconSizePx,
                spacingOffsetPx
            );
        } else {
            points = CircularHoneycombPacking.calculatePoints(
                activeItems.size(),
                fodCenterX,
                regionCenterY,
                regionRadiusPx,
                iconSizePx,
                spacingOffsetPx
            );
        }

        // 布局容量不足时截断（如空心圆环放不下的启用项、圆盘过小），保证启用项与点集一一对应
        if (activeItems.size() > points.size()) {
            activeItems = new ArrayList<>(activeItems.subList(0, points.size()));
        }

        // 扇区分隔虚线：全屏覆盖层绘制取消区小圆 + N 条射出屏幕的径向虚线（与底衬解耦）
        if (sectorMode && ringSectorSpec != null && ringSectorSpec.count == activeItems.size()) {
            sectorOverlay.update(true, ringSectorSpec.count,
                    fodCenterX, regionCenterY,
                    ringSectorSpec.cancelRadius, ringSectorSpec.startAngleDeg,
                    config.sectorLineColor, config.sectorLineAlpha,
                    config.sectorDashLength, config.sectorDashGap, config.sectorLineWidth);
            int hlIdx = (currentSelectedBubble != null) ? bubbles.indexOf(currentSelectedBubble) : -1;
            int hlCol = (currentSelectedBubble != null) ? currentSelectedBubble.item.resolveIconPrimaryColor() : 0;
            sectorOverlay.setHighlight(hlIdx, hlCol, config.sectorHighlightEnabled);
        } else {
            sectorOverlay.clear();
        }

        int targetCount = Math.min(points.size(), activeItems.size());
        if (canReuseExistingBubbles(activeItems, iconSizeInt)) {
            // 复用已有气泡实例：仅更新目标坐标、Margin 与模糊通道，消除展开时的 View 树重建开销
            int reuseCount = Math.min(targetCount, bubbles.size());
            for (int i = 0; i < reuseCount; i++) {
                CircularBubbleView bubble = bubbles.get(i);
                PointF pt = points.get(i);
                ShortcutItem item = activeItems.get(i);
                float finalX = pt.x + dpToPx(item.customOffsetX);
                float finalY = pt.y + dpToPx(item.customOffsetY);
                bubble.targetX = finalX;
                bubble.targetY = finalY;
                bubble.setMaterialStyle(resolvedStyle);
                bubble.setBlurRadius(config.bubbleBlurRadius);
                bubble.setDimAlpha(config.bubbleDimAlpha);
                bubble.setBubblePaddingPercent(config.bubblePaddingPercent);
                bubble.setCornerRadiusPercent(config.iconCornerRadius);
                bubble.setIconAlpha(config.iconAlpha);
                bubble.ensureBlurAndRefreshBackground();

                LayoutParams bLp = (LayoutParams) bubble.getLayoutParams();
                int newLeft = (int) (finalX - iconSizePx / 2f);
                int newTop = (int) (finalY - iconSizePx / 2f);
                if (bLp.leftMargin != newLeft || bLp.topMargin != newTop || bLp.width != iconSizeInt || bLp.height != iconSizeInt) {
                    bLp.width = iconSizeInt;
                    bLp.height = iconSizeInt;
                    bLp.leftMargin = newLeft;
                    bLp.topMargin = newTop;
                    bubble.setLayoutParams(bLp);
                }
            }
        } else {
            bubblesContainer.removeAllViews();
            bubbles.clear();
            lastBuiltIconSizePx = iconSizeInt;

            // 实例化圆形气泡
            for (int i = 0; i < targetCount; i++) {
                ShortcutItem item = activeItems.get(i);
                PointF pt = points.get(i);
                float finalX = pt.x + dpToPx(item.customOffsetX);
                float finalY = pt.y + dpToPx(item.customOffsetY);

                CircularBubbleView bubble = new CircularBubbleView(
                    getContext(), item, resolvedStyle, iconSizeInt, systemuiClassLoader, config
                );
                bubble.targetX = finalX;
                bubble.targetY = finalY;

                LayoutParams bLp = new LayoutParams(iconSizeInt, iconSizeInt);
                bLp.leftMargin = (int) (finalX - iconSizePx / 2f);
                bLp.topMargin = (int) (finalY - iconSizePx / 2f);
                bubble.setLayoutParams(bLp);

                bubblesContainer.addView(bubble);
                bubbles.add(bubble);
            }
        }

        setAlpha(1.0f);
        playExpandAnimation();

        // 唤出快捷方式面板后的专属震动反馈（对齐系统原生 startShowQuickOpenItemAnimation 震动样式，且跟随模块震动参数）：
        triggerHaptic();
    }

    private void playExpandAnimation() {
        // 与应用内整体预览同源（BubblePanelAnimator 单一数据源）
        BubblePanelAnimator.playExpand(bubbles, fodCenterX, fodCenterY, config);
    }

    /**
     * 播放松手时的向心收拢与选中弹跳退场动画（120ms）：
     * 选中项放大微弹淡出，其余项向中心回缩飞散，结束后回调 onComplete
     */
    public void playDismissAnimation(final Runnable onComplete) {
        if (config == null || !config.dismissAnimEnabled || bubbles.isEmpty() || getVisibility() != View.VISIBLE) {
            if (onComplete != null) onComplete.run();
            return;
        }
        // 与应用内整体预览同源（BubblePanelAnimator 单一数据源）
        BubblePanelAnimator.playDismiss(bubbles, currentSelectedBubble, fodCenterX, fodCenterY, config, onComplete);
    }

    /**
     * 供手势监听线程（如 MiuiGestureMonitor binder 线程）高频调用：
     * 折叠多个 120Hz/240Hz ACTION_MOVE 采样点，每帧最多在主线程执行一次 {@link #onFingerMove(float, float)}，
     * 零 Runnable 对象分配。
     */
    public void scheduleFingerMove(float rawX, float rawY) {
        this.pendingTouchX = rawX;
        this.pendingTouchY = rawY;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            movePending.set(false);
            onFingerMove(rawX, rawY);
        } else if (movePending.compareAndSet(false, true)) {
            post(coalescedMoveTask);
        }
    }

    /**
     * 扇区式命中（空心圆环扇区 / 系统原生 Region 扇形）的鱼眼缩放与透明度应用。
     * <p>
     * 这两条判定路径没有欧氏距离参与，命中完全由角度 / Region 多边形决定。旧实现只套用
     * 选中强调倍率，导致「鱼眼最大放大倍率」在它们上面完全不生效。
     * 现在统一走同一套鱼眼曲线：
     * <ul>
     *   <li>命中项：锁定峰值 {@code maxScale}（与手指在扇区内的位置无关）。扇面会一直延伸到
     *       屏幕外，手指常离图标中心很远，若按距离衰减就会把峰值又抵消回去（这正是旧实现
     *       失效的根因），因此命中即满峰值，最终倍率在所有布局下都等于 {@code maxScale}；</li>
     *   <li>未命中邻居：按各自中心距离余弦衰减，衰减快慢由感应辐射半径决定（调小半径即可
     *       逼近「只放大命中图标」）；手指在扇区边界时仍能看到相邻图标微放大，
     *       鱼眼透镜感不再被扇区判定切断。</li>
     * </ul>
     * 未命中任何扇区（手指在中心取消区）时全部回到 1.0x 与常态透明度。
     */
    private void applySectorFisheye(float touchX, float touchY, CircularBubbleView closest,
                                    float influenceRadiusPx, HyperOpenConfig cfg) {
        // 命中已由角度 / Region 判定完成，这里只负责缩放与透明度的连续过渡
        if (closest == null) {
            // 手指落在中心取消区：完全回到常态，避免空放大造成「有目标」的错觉
            for (int i = 0, n = bubbles.size(); i < n; i++) {
                CircularBubbleView b = bubbles.get(i);
                b.applyScale(1.0f);
                b.setIconAlpha(cfg.iconAlpha);
            }
            return;
        }

        float maxScale = FisheyeScale.peakScale(cfg.maxScale);
        for (int i = 0, n = bubbles.size(); i < n; i++) {
            CircularBubbleView b = bubbles.get(i);
            float dist = (float) Math.hypot(touchX - b.targetX, touchY - b.targetY);
            // 命中项锁定峰值：扇区内手指可能离图标中心很远（扇面一直延伸到屏幕外），
            // 若按距离衰减就会把 maxScale 又抵消回去，这正是旧实现失效的根因
            float scale = (b == closest)
                ? maxScale
                : FisheyeScale.scaleForDistance(dist, influenceRadiusPx, cfg.maxScale);
            b.applyScale(scale);

            if (b == closest) {
                b.setIconAlpha(cfg.fisheyeZoomAlpha);
            } else {
                // 邻居透明度按距离在「常态 → 其他图标透明度」之间插值，与距离型布局手感一致
                float factor = FisheyeScale.cosineFactor(dist, influenceRadiusPx);
                b.setIconAlpha(cfg.iconAlpha + (cfg.fisheyeOtherAlpha - cfg.iconAlpha) * factor);
            }
        }
    }

    /**
     * 经典连续余弦鱼眼：缩放与透明度完全由各气泡中心到手指的距离决定。
     * <p>
     * 一级气泡（蜂窝 / 弧形扇面 / 非扇区圆环 / 非扇区系统扇形）与文件夹二级子气泡
     * 共用这同一份实现，保证两类图标的手感与视觉效果完全一致，子气泡没有任何额外的绘制效果。
     * <ul>
     *   <li>缩放：每个气泡独立按 {@code scaleForDistance} 连续衰减（命中气泡因为在阈值内
     *       距离很小，天然接近峰值）；</li>
     *   <li>透明度：最近气泡（在有效半径内）在 iconAlpha → fisheyeZoomAlpha 间插值，
     *       其余气泡统一在 iconAlpha → fisheyeOtherAlpha 间按焦点系数插值。</li>
     * </ul>
     */
    private void applyDistanceFisheye(List<CircularBubbleView> targets, float touchX, float touchY,
                                      CircularBubbleView closest, float minDistance,
                                      float influenceRadiusPx, float hitDistanceThreshold,
                                      HyperOpenConfig cfg) {
        float effectiveRadiusPx = Math.max(influenceRadiusPx, hitDistanceThreshold);
        float focusFactor = 0f;
        if (closest != null && minDistance < effectiveRadiusPx) {
            focusFactor = (minDistance < hitDistanceThreshold)
                ? 1.0f
                : FisheyeScale.cosineFactor(minDistance, effectiveRadiusPx);
        }
        float otherAlpha = cfg.iconAlpha + (cfg.fisheyeOtherAlpha - cfg.iconAlpha) * focusFactor;

        for (int i = 0, n = targets.size(); i < n; i++) {
            CircularBubbleView b = targets.get(i);
            float dx = touchX - b.targetX;
            float dy = touchY - b.targetY;
            float dist = (float) Math.hypot(dx, dy);

            float scale = FisheyeScale.scaleForDistance(dist, influenceRadiusPx, cfg.maxScale);
            float currentAlpha;
            if (b == closest && dist < effectiveRadiusPx) {
                float alphaFactor = (dist < hitDistanceThreshold)
                    ? 1.0f
                    : FisheyeScale.cosineFactor(dist, effectiveRadiusPx);
                currentAlpha = cfg.iconAlpha + (cfg.fisheyeZoomAlpha - cfg.iconAlpha) * alphaFactor;
            } else {
                currentAlpha = otherAlpha;
            }
            b.applyScale(scale);
            b.setIconAlpha(currentAlpha);
        }
    }

    /**
     * 手指滑动时调用：计算实时鱼眼动力学与命中目标
     */
    public void onFingerMove(float touchX, float touchY) {
        // 如果当前处于文件夹子气泡展开状态，优先处理子菜单选中判定
        if (activeFolderBubble != null && !folderSubBubbles.isEmpty()) {
            handleFolderSubBubbleMove(touchX, touchY);
            return;
        }

        boolean isStockSystem = (config != null && config.layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK);
        boolean useStockRegion = isStockSystem && config.stockSystemRegion;

        float influenceRadiusPx = dpToPx(config.influenceRadius);
        float iconSizePx = dpToPx(config.iconSize);
        float hitDistanceThreshold = iconSizePx * 0.95f;

        CircularBubbleView closest = null;
        float minDistance = Float.MAX_VALUE;

        boolean useRingSector = ringSectorSpec != null
                && config.layoutStyle == HyperOpenConfig.LAYOUT_RING
                && config.ringSectorMode;

        if (useRingSector) {
            // 空心圆环扇区触发：中心圆内取消，圆外按角度均分扇区命中。
            // 命中判定不看欧氏距离，但缩放仍走统一鱼眼曲线：命中项锁定 maxScale 峰值，
            // 未命中邻居按余弦衰减（幅度再乘扩散强度），保证「鱼眼最大放大倍率」在本布局同样生效。
            float regionCenterY = fodCenterY - dpToPx(config.offsetY);
            int hitIndex = RingSectorPacking.findTouchedIndex(
                    ringSectorSpec, touchX, touchY, fodCenterX, regionCenterY);
            if (hitIndex >= 0 && hitIndex < bubbles.size()) {
                closest = bubbles.get(hitIndex);
                minDistance = 0f;
            }
            applySectorFisheye(touchX, touchY, closest, influenceRadiusPx, config);
            int hlIdx = (closest != null) ? bubbles.indexOf(closest) : -1;
            int hlCol = (closest != null) ? closest.item.resolveIconPrimaryColor() : 0;
            sectorOverlay.setHighlight(hlIdx, hlCol, config.sectorHighlightEnabled);
        } else if (useStockRegion && !stockSpecs.isEmpty()) {
            sectorOverlay.setHighlight(-1, 0, false);
            // 系统原生 Region 扇形多边形判定模式
            int hitIndex = StockFanPacking.findTouchedIndex(stockSpecs, touchX, touchY, fodCenterX, fodCenterY);
            if (hitIndex >= 0 && hitIndex < bubbles.size()) {
                closest = bubbles.get(hitIndex);
                minDistance = 0f; // 明确命中扇区
            }
            applySectorFisheye(touchX, touchY, closest, influenceRadiusPx, config);
        } else {
            // 经典连续余弦鱼眼动力学与欧氏距离判定模式（蜂窝、弧形扇面、非扇区圆环、非扇区系统扇形通用）
            for (int i = 0, n = bubbles.size(); i < n; i++) {
                CircularBubbleView b = bubbles.get(i);
                float dx = touchX - b.targetX;
                float dy = touchY - b.targetY;
                float dist = (float) Math.hypot(dx, dy);
                if (dist < minDistance) {
                    minDistance = dist;
                    closest = b;
                }
            }

            applyDistanceFisheye(bubbles, touchX, touchY, closest, minDistance,
                    influenceRadiusPx, hitDistanceThreshold, config);
            sectorOverlay.setHighlight(-1, 0, false);
        }

        // 判定选中项
        boolean isSelected = (useRingSector || useStockRegion)
                ? (closest != null)
                : (closest != null && minDistance < hitDistanceThreshold);
        if (isSelected) {
            if (currentSelectedBubble != closest) {
                if (currentSelectedBubble != null) {
                    currentSelectedBubble.setSelectedState(false);
                }
                closest.setSelectedState(true);
                // 将被选中的气泡置于最顶层，避免放大时被相邻气泡遮挡
                closest.bringToFront();
                currentSelectedBubble = closest;

                // 文件夹二级菜单检测：若当前选中项是文件夹气泡，启动 250ms 悬停展开计时器
                removeCallbacks(folderHoverRunnable);
                if (ShortcutItem.ACTION_FOLDER.equals(closest.item.actionType)) {
                    pendingFolderBubble = closest;
                    int delay = (config != null) ? Math.max(100, config.folderHoverDelayMs) : 250;
                    postDelayed(folderHoverRunnable, delay);
                } else {
                    pendingFolderBubble = null;
                }

                // 更新顶部标题（含 hint* 自定义样式：颜色/透明度/投影/描边/字重/字距/显隐）
                HintTextStyler.apply(headerTitle, closest.item, true, config);
                HintTextStyler.apply(headerSubtitle, closest.item, false, config);

                triggerSlideHaptic();
            }
        } else {
            removeCallbacks(folderHoverRunnable);
            pendingFolderBubble = null;
            if (currentSelectedBubble != null) {
                currentSelectedBubble.setSelectedState(false);
                currentSelectedBubble = null;
                HintTextStyler.reset(headerTitle, true, config);
                HintTextStyler.reset(headerSubtitle, false, config);
                headerTitle.setText("滑动选择快捷方式");
                headerSubtitle.setText("移至目标图标后松手直接打开");
            }
        }
    }

    /**
     * 提取当前选中的快捷方式（支持二级文件夹子项选中）、触发触觉反馈并立即复位气泡状态
     */
    public ShortcutItem consumeSelectedAndReset() {
        ShortcutItem selected = null;
        if (currentSelectedSubBubble != null) {
            selected = currentSelectedSubBubble.item;
            triggerHaptic();
        } else if (currentSelectedBubble != null) {
            selected = currentSelectedBubble.item;
            triggerHaptic();
        }
        resetBubbles();
        return selected;
    }

    /**
     * 手指松开时调用：执行目标动作或取消
     */
    public void onFingerUp() {
        ShortcutItem selected = consumeSelectedAndReset();
        if (selected != null) {
            selected.execute(getContext());
        }
    }

    public void resetBubbles() {
        removeCallbacks(folderHoverRunnable);
        pendingFolderBubble = null;
        dismissFolderSubBubbles(false);
        movePending.set(false);
        removeCallbacks(coalescedMoveTask);
        setAlpha(1.0f);
        for (int i = 0, n = bubbles.size(); i < n; i++) {
            CircularBubbleView b = bubbles.get(i);
            b.setSelectedState(false);
            b.applyScale(1.0f);
            b.setIconAlpha(config != null ? config.iconAlpha : 1.0f);
            b.resetBlur();
        }
        currentSelectedBubble = null;
        currentSelectedSubBubble = null;
        if (regionBackdrop != null) {
            regionBackdrop.reset();
        }
        if (sectorOverlay != null) {
            sectorOverlay.setHighlight(-1, 0, false);
        }
    }

    /**
     * 悬停达到阈值时就地展开文件夹二级气泡
     */
    private void expandFolder(CircularBubbleView folderBubble) {
        if (folderBubble == null || folderBubble.item == null) return;
        List<ShortcutItem> children = folderBubble.item.parseFolderChildren();
        if (children.isEmpty()) return;

        // 先把文件夹图标完全放大到鱼眼峰值（maxScale），放大动画播完后再展开子项：
        // 避免「边放大边展开」导致子项起点位置与文件夹最终位置错位、观感突兀
        folderBubble.animate().cancel();
        folderBubble.animate()
            .scaleX(config != null ? config.maxScale : 1.5f)
            .scaleY(config != null ? config.maxScale : 1.5f)
            .setDuration(120)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .setUpdateListener(anim -> folderBubble.syncBlurCornerRadiusWithViewScale())
            .withEndAction(() -> {
                // 松手 / 选中已复位后不再展开（防竞态：放大动画期间用户抬手）
                if (currentSelectedBubble != folderBubble || activeFolderBubble != null) return;
                // 同步 fisheyeScale 字段，保证后续 applyScale 判定基准正确
                folderBubble.applyScale(config != null ? config.maxScale : 1.5f);
                performFolderExpansion(folderBubble, children);
            })
            .start();
    }

    /** 文件夹图标完全放大后的实际展开：其余普通图标线性恢复原大小（保持变暗），子项从文件夹中心扇形展开 */
    private void performFolderExpansion(CircularBubbleView folderBubble, List<ShortcutItem> children) {
        dismissFolderSubBubbles(false);
        activeFolderBubble = folderBubble;
        triggerSlideHaptic();

        // 其余一级气泡：线性恢复原本大小（鱼眼放大收回 1.0），仅保留变暗弱化
        for (CircularBubbleView b : bubbles) {
            if (b != folderBubble) {
                b.animate().cancel();
                b.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(140)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                    .withEndAction(() -> b.applyScale(1.0f))
                    .start();
                b.setIconAlpha(0.25f);
            }
        }

        int count = children.size();
        float iconSizePx = dpToPx(config != null ? config.iconSize : 60);
        int iconSizeInt = (int) iconSizePx;
        float subRadius = iconSizePx * 1.35f;

        // 计算扩散弧度范围：朝指纹中心外侧弧形展开，或均分圆周
        double baseAngle = Math.atan2(folderBubble.targetY - fodCenterY, folderBubble.targetX - fodCenterX);
        double angleStep = (count > 1) ? (Math.PI * 0.85 / (count - 1)) : 0;
        double startAngle = (count > 1) ? (baseAngle - Math.PI * 0.425) : baseAngle;

        for (int i = 0; i < count; i++) {
            ShortcutItem childItem = children.get(i);
            double angle = (count == 1) ? baseAngle : (startAngle + i * angleStep);
            float targetX = (float) (folderBubble.targetX + subRadius * Math.cos(angle));
            float targetY = (float) (folderBubble.targetY + subRadius * Math.sin(angle));

            CircularBubbleView sub = new CircularBubbleView(
                getContext(), childItem, resolvedStyle, iconSizeInt, systemuiClassLoader, config
            );
            sub.targetX = targetX;
            sub.targetY = targetY;

            LayoutParams lp = new LayoutParams(iconSizeInt, iconSizeInt);
            lp.leftMargin = (int) (targetX - iconSizePx / 2f);
            lp.topMargin = (int) (targetY - iconSizePx / 2f);
            sub.setLayoutParams(lp);

            sub.setTranslationX(folderBubble.targetX - targetX);
            sub.setTranslationY(folderBubble.targetY - targetY);
            sub.setScaleX(0.1f);
            sub.setScaleY(0.1f);
            sub.setAlpha(0f);

            bubblesContainer.addView(sub);
            folderSubBubbles.add(sub);

            sub.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setInterpolator(EXPAND_INTERPOLATOR)
                .setDuration(180)
                .setStartDelay(i * 15L)
                .setUpdateListener(anim -> sub.syncBlurCornerRadiusWithViewScale())
                .start();
        }
    }

    /**
     * 文件夹子气泡展开时的滑动命中计算。
     * <p>
     * 子气泡完全复用一级气泡的距离型鱼眼实现 {@link #applyDistanceFisheye}（缩放、透明度、
     * 命中阈值均一致，无任何额外绘制效果），仅命中判定对象换成子气泡集合。
     */
    private void handleFolderSubBubbleMove(float touchX, float touchY) {
        HyperOpenConfig cfg = config != null ? config : new HyperOpenConfig();
        float iconSizePx = dpToPx(cfg.iconSize);
        float influenceRadiusPx = dpToPx(cfg.influenceRadius);
        // 与一级气泡一致的命中阈值（旧版 1.1x 是子气泡专属的宽松判定，会造成两者手感不一致）
        float threshold = iconSizePx * 0.95f;
        CircularBubbleView closestSub = null;
        float minDist = Float.MAX_VALUE;

        for (CircularBubbleView sub : folderSubBubbles) {
            float dist = (float) Math.hypot(touchX - sub.targetX, touchY - sub.targetY);
            if (dist < minDist) {
                minDist = dist;
                closestSub = sub;
            }
        }

        // 子气泡与一级气泡共用同一份距离型鱼眼缩放与透明度插值
        applyDistanceFisheye(folderSubBubbles, touchX, touchY, closestSub, minDist,
                influenceRadiusPx, threshold, cfg);

        boolean hitSub = (closestSub != null && minDist < threshold);
        if (hitSub) {
            if (currentSelectedSubBubble != closestSub) {
                if (currentSelectedSubBubble != null) {
                    currentSelectedSubBubble.setSelectedState(false);
                }
                closestSub.setSelectedState(true);
                closestSub.bringToFront();
                currentSelectedSubBubble = closestSub;

                HintTextStyler.apply(headerTitle, closestSub.item, true, config);
                HintTextStyler.apply(headerSubtitle, closestSub.item, false, config);
                triggerSlideHaptic();
            }
        } else {
            if (currentSelectedSubBubble != null) {
                currentSelectedSubBubble.setSelectedState(false);
                currentSelectedSubBubble = null;
            }
            // 检查手指是否移回主文件夹气泡；若距离主气泡过远，关闭子菜单
            float distFromFolder = (activeFolderBubble != null)
                ? (float) Math.hypot(touchX - activeFolderBubble.targetX, touchY - activeFolderBubble.targetY)
                : Float.MAX_VALUE;
            if (distFromFolder > iconSizePx * 2.8f) {
                dismissFolderSubBubbles(true);
            }
        }
    }

    /**
     * 关闭并移除文件夹子气泡。
     *
     * @param animated true 时子项向文件夹中心收拢并淡出后再移除（悬停移出触发的关闭）；
     *                 false 时立即移除（松手退场 / 切换文件夹重新展开，外层动画已在进行）。
     */
    private void dismissFolderSubBubbles(boolean animated) {
        if (folderSubBubbles.isEmpty()) return;
        CircularBubbleView folder = activeFolderBubble;
        final java.util.List<CircularBubbleView> subs = new java.util.ArrayList<>(folderSubBubbles);
        folderSubBubbles.clear();
        currentSelectedSubBubble = null;
        activeFolderBubble = null;

        if (!animated) {
            for (CircularBubbleView sub : subs) {
                sub.animate().cancel();
                bubblesContainer.removeView(sub);
            }
        } else {
            // 子气泡向文件夹中心收拢 + 缩小 + 淡出，播完后从容器移除
            for (CircularBubbleView sub : subs) {
                sub.animate().cancel();
                float toX = (folder != null ? folder.targetX : sub.targetX) - sub.targetX;
                float toY = (folder != null ? folder.targetY : sub.targetY) - sub.targetY;
                sub.animate()
                    .translationX(toX)
                    .translationY(toY)
                    .scaleX(0.2f)
                    .scaleY(0.2f)
                    .alpha(0f)
                    .setDuration(140)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .setUpdateListener(anim -> sub.syncBlurCornerRadiusWithViewScale())
                    .withEndAction(() -> bubblesContainer.removeView(sub))
                    .start();
            }
        }

        // 恢复一级气泡大小与透明度（含文件夹图标）：
        // 有动画的关闭走线性过渡（与收拢动画同步），立即关闭保持瞬态复位
        for (CircularBubbleView b : bubbles) {
            b.animate().cancel();
            float targetAlpha = config != null ? config.iconAlpha : 1.0f;
            if (animated) {
                b.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(140)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                    .withEndAction(() -> b.applyScale(1.0f))
                    .start();
            } else {
                b.applyScale(1.0f);
            }
            b.setIconAlpha(targetAlpha);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
