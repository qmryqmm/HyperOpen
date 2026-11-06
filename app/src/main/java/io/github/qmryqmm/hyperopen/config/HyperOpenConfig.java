package io.github.qmryqmm.hyperopen.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import io.github.qmryqmm.hyperopen.model.ShortcutItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.qmryqmm.hyperopen.util.IpcExecutor;

import de.robv.android.xposed.XSharedPreferences;

public class HyperOpenConfig {
    private static final String TAG = "HyperOpen_Config";

    public static final String PACKAGE_NAME = "io.github.qmryqmm.hyperopen";
    public static final String PREF_NAME = "hyper_open_settings";
    public static final String ACTION_CONFIG_CHANGED = "io.github.qmryqmm.hyperopen.ACTION_CONFIG_CHANGED";

    public static final int MATERIAL_SOFT_GLASS = 1;
    public static final int MATERIAL_GAUSSIAN_BLUR = 2;

    /** 应用图标裁切方案：1=抗锯齿圆形裁切, 2=自适应外扩满铺 (留白大小由留白滑块独立控制) */
    public static final int CROP_BITMAP_CIRCLE = 1;
    public static final int CROP_ADAPTIVE_EXPAND = 2;

    /** Miuix 矢量图标底垫渲染模式：0=彩色底板+白色图标, 1=微透磨砂底板, 2=反色(微透浅底+彩色图标) */
    public static final int GLYPH_PAD_MODE_SOLID = 0;
    public static final int GLYPH_PAD_MODE_TRANSLUCENT = 1;
    public static final int GLYPH_PAD_MODE_INVERTED = 2;

    /** 界面风格：0=Material You 深色玻璃, 1=MIUIX 浅色卡片 */
    public static final int UI_STYLE_MATERIAL = 0;
    public static final int UI_STYLE_MIUIX = 1;

    /** 主题模式：0=跟随系统, 1=浅色, 2=深色 */
    public static final int THEME_FOLLOW_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    /** 页面切换手势：0=关闭, 1=横滑切换 */
    public static final int PAGE_GESTURE_OFF = 0;
    public static final int PAGE_GESTURE_HORIZONTAL = 1;

    /** 震动模式：0=清脆短震(TICK), 1=标准点按(CLICK), 2=浑厚反馈(HEAVY_CLICK), 3=双脉冲(DOUBLE_CLICK), 4=自定义时长脉冲(CUSTOM), 5=系统原生触感(STOCK_MIUI) */
    public static final int HAPTIC_MODE_TICK = 0;
    public static final int HAPTIC_MODE_CLICK = 1;
    public static final int HAPTIC_MODE_HEAVY = 2;
    public static final int HAPTIC_MODE_DOUBLE = 3;
    public static final int HAPTIC_MODE_CUSTOM = 4;
    public static final int HAPTIC_MODE_STOCK = 5;

    /** MIUI 线性马达官方预设波形：0=长按回弹(long_press), 1=网格微动(mesh_normal), 2=轻触颗粒(flick), 3=弹性质感(pop) */
    public static final int HAPTIC_STOCK_WAVE_LONG_PRESS = 0;
    public static final int HAPTIC_STOCK_WAVE_MESH_NORMAL = 1;
    public static final int HAPTIC_STOCK_WAVE_FLICK = 2;
    public static final int HAPTIC_STOCK_WAVE_POP = 3;

    public int materialStyle = MATERIAL_GAUSSIAN_BLUR;
    /** 设置界面风格（仅 App 内生效）：UI_STYLE_MATERIAL / UI_STYLE_MIUIX */
    public int uiStyle = UI_STYLE_MATERIAL;
    /** 主题模式（仅 App 内生效）：THEME_FOLLOW_SYSTEM / THEME_LIGHT / THEME_DARK */
    public int themeMode = THEME_FOLLOW_SYSTEM;
    /** Monet 动态取色（仅 App 内 Material 轨生效） */
    public boolean monetColors = false;
    /** 顶栏/底栏毛玻璃 */
    public boolean barBlur = true;
    /** 悬浮底栏 */
    public boolean floatingNavBar = false;
    /** 预测性返回手势（Android 13+） */
    public boolean predictiveBack = true;
    /** 横移返回手势 */
    public boolean edgeBack = true;
    /** 页面切换手势：PAGE_GESTURE_OFF / PAGE_GESTURE_HORIZONTAL */
    public int pageSwitchGesture = PAGE_GESTURE_HORIZONTAL;
    public int offsetY = 100;          // dp
    public int regionDiameter = 220;   // dp
    public boolean showRegionBg = false;
    public boolean showHeaderPillBg = false;

    /** 顶部提示区域配置 */
    public boolean showHeaderPill = true;          // 是否显示顶部提示区域
    public int headerTopMargin = 100;              // 顶部提示垂直高度位置 (30 ~ 360 dp)
    public int headerTitleSize = 17;               // 标题文字大小 (12 ~ 26 sp)
    public int headerSubtitleSize = 11;            // 副标题文字大小 (9 ~ 18 sp)
    public int headerBlurRadius = 25;              // 顶部提示胶囊背景独立模糊半径 (0 ~ 50 dp)
    public boolean headerTextCentered = true;      // 顶部提示文字与胶囊是否水平居中

    public int iconSize = 60;          // dp
    /** 面板显示的快捷图标数量 (0 = 显示全部启用项，1 ~ 15 只显示排序最前的 N 个启用项) */
    public int maxIconCount = 0;
    /** 图标布局样式常量：蜂窝阵列（默认） */
    public static final int LAYOUT_HONEYCOMB = 0;
    /** 图标布局样式常量：空心同心圆环（中心留空，图标排布在同心环上） */
    public static final int LAYOUT_RING = 1;
    /** 图标布局样式常量：弧形扇面（150° 弧带排布，单弧放不下自动转双弧） */
    public static final int LAYOUT_ARC = 2;
    /** 图标布局样式常量：系统经典扇形（对齐 MIUI/HyperOS 原生 45°/60° 扇带与直线飞入动画） */
    public static final int LAYOUT_SYSTEM_STOCK = 3;
    /** 图标布局样式 (LAYOUT_HONEYCOMB / LAYOUT_RING / LAYOUT_ARC / LAYOUT_SYSTEM_STOCK，默认蜂窝) */
    public int layoutStyle = LAYOUT_HONEYCOMB;
    /** 系统经典布局：复用系统原生直线飞出展开动画（150ms 轨迹飞入） */
    public boolean stockSystemAnim = true;
    /** 系统经典布局：复用系统原生精准扇区多边形选中算法（关闭则走欧氏距离） */
    public boolean stockSystemRegion = true;
    /** 空心同心圆环布局的同心环圈数 (1 ~ 4，默认 2，中心保持空心) */
    public int ringCount = 2;
    /**
     * 空心圆环「扇区触发」模式：开启且快捷方式少于 6 个时，
     * 图标沿圆周均分排布，圆内为中心取消区，圆外按图标数量均分为扇形，
     * 每个扇形触发对应图标；该模式下禁止自定义排版（拖拽排序与单个图标偏移不生效）。
     */
    public boolean ringSectorMode = true;
    /** 扇区分隔线颜色 (ARGB，透明度由 sectorLineAlpha 独立控制，默认淡灰 #FFBFBFBF) */
    public int sectorLineColor = 0xFFBFBFBF;
    /** 扇区分隔线透明度 (0.0 ~ 1.0，默认 0.4) */
    public float sectorLineAlpha = 0.4f;
    /** 虚线实线段长度 (2 ~ 20 dp，默认 6) */
    public int sectorDashLength = 6;
    /** 虚线间隔长度 (2 ~ 20 dp，默认 5) */
    public int sectorDashGap = 5;
    /** 分隔线粗细 (0.5 ~ 4.0 dp，默认 1.2) */
    public float sectorLineWidth = 1.2f;
    public float maxScale = 1.40f;
    public int influenceRadius = 70;  // dp

    /** 图标透明度 (0.0f ~ 1.0f，默认 1.0f) */
    public float iconAlpha = 1.0f;
    /** 鱼眼效果放大时的图标透明度 (0.0f ~ 1.0f，默认 1.0f) */
    public float fisheyeZoomAlpha = 1.0f;
    /** 唤醒指纹快捷方式时全屏模糊遮罩模糊程度 (0.0f ~ 1.0f，默认 1.0f) */
    public float fullscreenBlurMaskAlpha = 1.0f;
    /** 唤醒指纹快捷方式时全屏遮罩背景压暗程度 (0.0f ~ 1.0f，默认 0.45f) */
    public float fullscreenDimAlpha = 0.45f;
    public float headerDimAlpha = 0f;   // 顶部提示胶囊背景压暗 (0.0 ~ 1.0)
    public float regionDimAlpha = 0f;   // 圆形视界背景压暗 (0.0 ~ 1.0)
    public float bubbleDimAlpha = 0f;   // 图标气泡留白区域压暗 (0.0 ~ 1.0)
    /** 指纹解锁后进入快捷方式面板的等待阈值 (0 ~ 2000 ms，默认 500 = 系统默认不干预，0 = 立即弹出) */
    public int showDelayMs = 500;
    /** 人脸识别成功停留在锁屏时，按指纹进桌面并支持长按唤出快捷方式 (默认开启) */
    public boolean faceUnlockQuickOpen = true;

    /* ===== 震动反馈配置（进入展开与手指滑动完全解耦独立控制） ===== */
    /** 进入快捷方式展开面板时的震动总开关 */
    public boolean hapticEnabled = true;
    public int hapticStrength = 140;               // 展开震动强度 (1 ~ 255，默认 140)
    public int hapticDurationMs = 24;              // 展开自定义震动时长 (4 ~ 80 ms，默认对齐官方降级 24 ms)
    public int hapticMode = HAPTIC_MODE_STOCK;     // 展开震动模式 (默认 5 = 系统原生触感)
    public int hapticStockWaveExpand = HAPTIC_STOCK_WAVE_LONG_PRESS; // 展开系统原生波形 (long_press)
    public boolean hapticBrakePulse = false;       // 展开反向制动煞停脉冲
    public int hapticDoubleGapMs = 28;             // 展开双脉冲间隔 (10 ~ 60 ms)

    /** 手指滑动划过气泡时的吸附震动总开关 */
    public boolean hapticSlideEnabled = true;
    public int hapticSlideStrength = 100;          // 滑动震动强度 (1 ~ 255，默认 100 偏柔和)
    public int hapticSlideDurationMs = 12;         // 滑动自定义时长 (4 ~ 80 ms，默认对齐官方降级 12 ms)
    public int hapticSlideMode = HAPTIC_MODE_STOCK;// 滑动震动模式 (默认 5 = 系统原生触感)
    public int hapticStockWaveSlide = HAPTIC_STOCK_WAVE_MESH_NORMAL; // 滑动系统原生波形 (mesh_normal)
    public boolean hapticSlideBrakePulse = false;  // 滑动反向制动煞停脉冲
    public int hapticSlideDoubleGapMs = 28;        // 滑动双脉冲间隔 (10 ~ 60 ms)

    /** 实机 FOD 中心坐标持久化（由 SystemUI 在真实展开时同步写入，供 App 预览 1:1 像素级对齐） */
    public float lastRealFodX = 0f;
    public float lastRealFodY = 0f;

    /** 图标的圆形视界区域背景高斯模糊半径 (0 ~ 50 dp，默认 25 dp，0 为关闭) */
    public int regionBlurRadius = 25;

    /** 图标分布紧凑度间距微调 (-10dp ~ 20dp，默认 5dp，负值更紧凑，正值更松散) */
    public int iconSpacingOffset = 5;
    /** 气泡内部图标留白比例 (0% ~ 40%，默认 0%，0% 即全屏满铺) */
    public int bubblePaddingPercent = 0;
    /** 图标气泡留白区域高斯模糊半径 (0 ~ 50 dp，默认 25 dp，0 为关闭模糊，样式与圆形视界底衬一致) */
    public int bubbleBlurRadius = 25;
    /** 手指选中图标时气泡内图标的缩放倍率 (1.00x ~ 1.50x，默认 1.15x) */
    public float selectedIconScale = 1.15f;

    /** 应用图标裁切方案 (CROP_BITMAP_CIRCLE / CROP_ADAPTIVE_EXPAND) */
    public int appIconCropMode = CROP_ADAPTIVE_EXPAND;
    /** Miuix 矢量图标是否启用圆形底垫 */
    public boolean glyphPadEnabled = true;
    /** Miuix 矢量图标底垫颜色 (ARGB) */
    public int glyphPadColor = 0xFF2A82E4;
    /** Miuix 矢量图标底垫质感模式 (GLYPH_PAD_MODE_SOLID / TRANSLUCENT / INVERTED) */
    public int glyphPadMode = GLYPH_PAD_MODE_TRANSLUCENT;
    /** 图标呼吸/激活外发光辉光动效 */
    public boolean iconGlowEffect = true;

    public List<ShortcutItem> shortcuts = new ArrayList<>();

    private static XSharedPreferences xPrefs;

    // ──────────────────────────────────────────────────────────────
    // SystemUI 进程内的配置缓存（异步刷新，展开路径零跨进程调用）
    // ──────────────────────────────────────────────────────────────

    /** 配置异步刷新完成回调（总在主线程回调） */
    public interface OnConfigUpdatedListener {
        void onConfigUpdated(HyperOpenConfig config);
    }

    /** 展开路径触发的被动刷新最小间隔：配置变更有广播实时推送，这里只是兜底 */
    private static final long PASSIVE_REFRESH_INTERVAL_MS = 60 * 1000L;

    private static volatile HyperOpenConfig sCached;
    private static volatile long sLastRefreshAt = 0L;
    private static final AtomicBoolean sRefreshing = new AtomicBoolean(false);
    private static volatile boolean sRefreshPending = false;
    private static volatile OnConfigUpdatedListener sListener;

    public static void setOnConfigUpdatedListener(OnConfigUpdatedListener l) {
        sListener = l;
    }

    /**
     * 立即返回当前缓存的配置，绝不发起跨进程调用。
     * 缓存尚未建立时以 XSharedPreferences（本地文件读取）兜底。
     * 供 SystemUI 主线程 / 手势线程上的展开路径使用。
     */
    public static HyperOpenConfig getCached() {
        HyperOpenConfig c = sCached;
        if (c != null) {
            return c;
        }
        try {
            c = loadFromXposed();
        } catch (Throwable t) {
            Log.w(TAG, "XSharedPreferences 兜底读取失败，使用默认配置: " + t);
            c = new HyperOpenConfig();
            c.shortcuts = getDefaultShortcuts();
        }
        if (sCached == null) {
            sCached = c;
        }
        return sCached;
    }

    /**
     * 节流版刷新：距上次刷新不足 {@link #PASSIVE_REFRESH_INTERVAL_MS} 时直接忽略。
     * 用于展开路径的兜底同步，避免每次呼出面板都去唤醒可能被冻结的模块进程。
     */
    public static void refreshAsyncThrottled(Context context) {
        if (System.currentTimeMillis() - sLastRefreshAt < PASSIVE_REFRESH_INTERVAL_MS) {
            return;
        }
        refreshAsync(context);
    }

    /**
     * 在后台 IPC 线程通过 ContentProvider 拉取最新配置，完成后更新缓存并在主线程回调。
     * 多次并发请求会被合并；刷新进行中收到的新请求会在本轮结束后补跑一次，保证不丢更新。
     */
    public static void refreshAsync(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        final Context ctx = app != null ? app : context;
        sRefreshPending = true;
        if (!sRefreshing.compareAndSet(false, true)) {
            return;
        }
        IpcExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    while (sRefreshPending) {
                        sRefreshPending = false;
                        final HyperOpenConfig loaded = load(ctx);
                        sCached = loaded;
                        sLastRefreshAt = System.currentTimeMillis();
                        final OnConfigUpdatedListener l = sListener;
                        if (l != null) {
                            IpcExecutor.runOnMain(new Runnable() {
                                @Override
                                public void run() {
                                    l.onConfigUpdated(loaded);
                                }
                            });
                        }
                    }
                } finally {
                    sRefreshing.set(false);
                }
                // 竞态兜底：标志复位前恰好有新请求进来
                if (sRefreshPending) {
                    refreshAsync(ctx);
                }
            }
        });
    }

    /**
     * 由 SystemUI 在真实展开时异步上报实机 FOD 中心坐标，供 App 预览实现 1:1 绝对坐标对齐
     */
    public static void reportRealFodCenterAsync(final Context context, final float fodX, final float fodY) {
        if (context == null || fodX <= 0f || fodY <= 0f) return;
        HyperOpenConfig cached = sCached;
        if (cached != null && Math.abs(cached.lastRealFodX - fodX) < 0.5f && Math.abs(cached.lastRealFodY - fodY) < 0.5f) {
            return;
        }
        if (cached != null) {
            cached.lastRealFodX = fodX;
            cached.lastRealFodY = fodY;
        }
        final Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        IpcExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putFloat("fod_x", fodX);
                    extras.putFloat("fod_y", fodY);
                    appCtx.getContentResolver().call(
                        android.net.Uri.parse("content://io.github.qmryqmm.hyperopen.provider"),
                        "save_fod_center", null, extras
                    );
                } catch (Throwable ignored) {}
            }
        });
    }

    /**
     * SystemUI 优先通过 ContentProvider 查询跨进程配置，失败时回退 XSharedPreferences
     *
     * 注意：这是同步跨进程调用，耗时不可控（模块进程可能被冻结/未启动）。
     * SystemUI 侧禁止在主线程 / 手势线程直接调用，请使用 {@link #getCached()} +
     * {@link #refreshAsync(Context)}。
     */
    public static HyperOpenConfig load(Context context) {
        if (context != null) {
            try {
                android.os.Bundle bundle = context.getContentResolver().call(
                    android.net.Uri.parse("content://io.github.qmryqmm.hyperopen.provider"),
                    "get_config", null, null
                );
                if (bundle != null) {
                    HyperOpenConfig config = new HyperOpenConfig();
                    config.materialStyle = bundle.getInt("material_style", MATERIAL_GAUSSIAN_BLUR);
                    config.offsetY = bundle.getInt("offset_y", 100);
                    config.regionDiameter = bundle.getInt("region_diameter", 220);
                    config.showRegionBg = bundle.getBoolean("show_region_bg", false);
                    config.showHeaderPillBg = bundle.getBoolean("show_header_pill_bg", false);
                    config.showHeaderPill = bundle.getBoolean("show_header_pill", true);
                    config.headerTopMargin = bundle.getInt("header_top_margin", 100);
                    config.headerTitleSize = bundle.getInt("header_title_size", 17);
                    config.headerSubtitleSize = bundle.getInt("header_subtitle_size", 11);
                    config.headerBlurRadius = bundle.getInt("header_blur_radius", 25);
                    config.headerTextCentered = bundle.getBoolean("header_text_centered", true);
                    config.iconSize = bundle.getInt("icon_size", 60);
                    config.maxIconCount = bundle.getInt("max_icon_count", 0);
                    config.layoutStyle = bundle.getInt("layout_style", LAYOUT_HONEYCOMB);
                    config.stockSystemAnim = bundle.getBoolean("stock_system_anim", true);
                    config.ringSectorMode = bundle.getBoolean("ring_sector_mode", true);
                    config.sectorLineColor = bundle.getInt("sector_line_color", 0xFFBFBFBF);
                    config.sectorLineAlpha = bundle.getFloat("sector_line_alpha", 0.4f);
                    config.sectorDashLength = bundle.getInt("sector_dash_length", 6);
                    config.sectorDashGap = bundle.getInt("sector_dash_gap", 5);
                    config.sectorLineWidth = bundle.getFloat("sector_line_width", 1.2f);
                    config.stockSystemRegion = bundle.getBoolean("stock_system_region", true);
                    config.ringCount = bundle.getInt("ring_count", 2);
                    config.maxScale = bundle.getFloat("max_scale", 1.40f);
                    config.influenceRadius = bundle.getInt("influence_radius", 70);
                    config.iconAlpha = bundle.getFloat("icon_alpha", 1.0f);
                    config.fisheyeZoomAlpha = bundle.getFloat("fisheye_zoom_alpha", 1.0f);
                    config.fullscreenBlurMaskAlpha = bundle.getFloat("fullscreen_blur_mask_alpha", 1.0f);
                    config.fullscreenDimAlpha = bundle.getFloat("fullscreen_dim_alpha", 0.45f);
                    config.headerDimAlpha = bundle.getFloat("header_dim_alpha", 0f);
                    config.regionDimAlpha = bundle.getFloat("region_dim_alpha", 0f);
                    config.bubbleDimAlpha = bundle.getFloat("bubble_dim_alpha", 0f);
                    config.showDelayMs = bundle.getInt("show_delay_ms", 500);
                    config.faceUnlockQuickOpen = bundle.getBoolean("face_unlock_quick_open", true);
                    config.hapticEnabled = bundle.getBoolean("haptic_enabled", true);
                    config.hapticStrength = bundle.getInt("haptic_strength", 140);
                    config.hapticDurationMs = bundle.getInt("haptic_duration_ms", 24);
                    config.hapticMode = bundle.getInt("haptic_mode", HAPTIC_MODE_STOCK);
                    config.hapticStockWaveExpand = bundle.getInt("haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
                    config.hapticBrakePulse = bundle.getBoolean("haptic_brake_pulse", false);
                    config.hapticDoubleGapMs = bundle.getInt("haptic_double_gap_ms", 28);
                    config.hapticSlideEnabled = bundle.getBoolean("haptic_slide_enabled", true);
                    config.hapticSlideStrength = bundle.getInt("haptic_slide_strength", 100);
                    config.hapticSlideDurationMs = bundle.getInt("haptic_slide_duration_ms", 12);
                    config.hapticSlideMode = bundle.getInt("haptic_slide_mode", HAPTIC_MODE_STOCK);
                    config.hapticStockWaveSlide = bundle.getInt("haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
                    config.hapticSlideBrakePulse = bundle.getBoolean("haptic_slide_brake_pulse", false);
                    config.hapticSlideDoubleGapMs = bundle.getInt("haptic_slide_double_gap_ms", 28);
                    config.lastRealFodX = bundle.getFloat("last_real_fod_x", 0f);
                    config.lastRealFodY = bundle.getFloat("last_real_fod_y", 0f);
                    config.regionBlurRadius = bundle.getInt("region_blur_radius", 25);
                    config.iconSpacingOffset = bundle.getInt("icon_spacing_offset", 5);
                    config.bubblePaddingPercent = bundle.getInt("bubble_padding_percent", 0);
                    config.bubbleBlurRadius = bundle.getInt("bubble_blur_radius", 25);
                    config.selectedIconScale = bundle.getFloat("selected_icon_scale", 1.15f);
                    config.appIconCropMode = bundle.getInt("app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
                    config.glyphPadEnabled = bundle.getBoolean("glyph_pad_enabled", true);
                    config.glyphPadColor = bundle.getInt("glyph_pad_color", 0xFF2A82E4);
                    config.glyphPadMode = bundle.getInt("glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
                    config.iconGlowEffect = bundle.getBoolean("icon_glow_effect", true);

                    String jsonStr = bundle.getString("shortcuts_json", null);
                    config.shortcuts = parseShortcutsJson(jsonStr);
                    Log.i(TAG, "成功通过 ContentProvider 读取最新配置: offsetY=" + config.offsetY + ", items=" + config.shortcuts.size());
                    return config;
                }
            } catch (Throwable t) {
                Log.w(TAG, "ContentProvider 查询异常，回退 XSharedPreferences: " + t.getMessage());
            }
        }
        return loadFromXposed();
    }

    /**
     * SystemUI 进程加载配置 (XSharedPreferences 兜底)
     */
    public static synchronized HyperOpenConfig loadFromXposed() {
        if (xPrefs == null) {
            // prefs 已迁 DE：优先读 /data/user_de/0/<pkg>/shared_prefs/<name>.xml，
            // 不存在时回退旧版 CE 路径（升级后未迁移的过渡场景）
            java.io.File deFile = new java.io.File(
                "/data/user_de/0/" + PACKAGE_NAME + "/shared_prefs/" + PREF_NAME + ".xml");
            xPrefs = deFile.exists()
                ? new XSharedPreferences(deFile)
                : new XSharedPreferences(PACKAGE_NAME, PREF_NAME);
            xPrefs.makeWorldReadable();
        } else {
            xPrefs.reload();
        }

        HyperOpenConfig config = new HyperOpenConfig();
        config.materialStyle = xPrefs.getInt("material_style", MATERIAL_GAUSSIAN_BLUR);
        config.offsetY = xPrefs.getInt("offset_y", 100);
        config.regionDiameter = xPrefs.getInt("region_diameter", 220);
        config.showRegionBg = xPrefs.getBoolean("show_region_bg", false);
        config.showHeaderPillBg = xPrefs.getBoolean("show_header_pill_bg", false);
        config.showHeaderPill = xPrefs.getBoolean("show_header_pill", true);
        config.headerTopMargin = xPrefs.getInt("header_top_margin", 100);
        config.headerTitleSize = xPrefs.getInt("header_title_size", 17);
        config.headerSubtitleSize = xPrefs.getInt("header_subtitle_size", 11);
        config.headerBlurRadius = xPrefs.getInt("header_blur_radius", 25);
        config.headerTextCentered = xPrefs.getBoolean("header_text_centered", true);
        config.iconSize = xPrefs.getInt("icon_size", 60);
        config.maxIconCount = xPrefs.getInt("max_icon_count", 0);
        config.layoutStyle = xPrefs.getInt("layout_style", LAYOUT_HONEYCOMB);
        config.stockSystemAnim = xPrefs.getBoolean("stock_system_anim", true);
        config.ringSectorMode = xPrefs.getBoolean("ring_sector_mode", true);
        config.sectorLineColor = xPrefs.getInt("sector_line_color", 0xFFBFBFBF);
        config.sectorLineAlpha = xPrefs.getFloat("sector_line_alpha", 0.4f);
        config.sectorDashLength = xPrefs.getInt("sector_dash_length", 6);
        config.sectorDashGap = xPrefs.getInt("sector_dash_gap", 5);
        config.sectorLineWidth = xPrefs.getFloat("sector_line_width", 1.2f);
        config.stockSystemRegion = xPrefs.getBoolean("stock_system_region", true);
        config.ringCount = xPrefs.getInt("ring_count", 2);
        config.maxScale = xPrefs.getFloat("max_scale", 1.40f);
        config.influenceRadius = xPrefs.getInt("influence_radius", 70);
        config.iconAlpha = xPrefs.getFloat("icon_alpha", 1.0f);
        config.fisheyeZoomAlpha = xPrefs.getFloat("fisheye_zoom_alpha", 1.0f);
        config.fullscreenBlurMaskAlpha = xPrefs.getFloat("fullscreen_blur_mask_alpha", 1.0f);
        config.fullscreenDimAlpha = xPrefs.getFloat("fullscreen_dim_alpha", 0.45f);
        config.headerDimAlpha = xPrefs.getFloat("header_dim_alpha", 0f);
        config.regionDimAlpha = xPrefs.getFloat("region_dim_alpha", 0f);
        config.bubbleDimAlpha = xPrefs.getFloat("bubble_dim_alpha", 0f);
        config.showDelayMs = xPrefs.getInt("show_delay_ms", 500);
        config.faceUnlockQuickOpen = xPrefs.getBoolean("face_unlock_quick_open", true);
        config.hapticEnabled = xPrefs.getBoolean("haptic_enabled", true);
        config.hapticStrength = xPrefs.getInt("haptic_strength", 140);
        config.hapticDurationMs = xPrefs.getInt("haptic_duration_ms", 24);
        config.hapticMode = xPrefs.getInt("haptic_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveExpand = xPrefs.getInt("haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
        config.hapticBrakePulse = xPrefs.getBoolean("haptic_brake_pulse", false);
        config.hapticDoubleGapMs = xPrefs.getInt("haptic_double_gap_ms", 28);
        config.hapticSlideEnabled = xPrefs.getBoolean("haptic_slide_enabled", true);
        config.hapticSlideStrength = xPrefs.getInt("haptic_slide_strength", 100);
        config.hapticSlideDurationMs = xPrefs.getInt("haptic_slide_duration_ms", 12);
        config.hapticSlideMode = xPrefs.getInt("haptic_slide_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveSlide = xPrefs.getInt("haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
        config.hapticSlideBrakePulse = xPrefs.getBoolean("haptic_slide_brake_pulse", false);
        config.hapticSlideDoubleGapMs = xPrefs.getInt("haptic_slide_double_gap_ms", 28);
        config.lastRealFodX = xPrefs.getFloat("last_real_fod_x", 0f);
        config.lastRealFodY = xPrefs.getFloat("last_real_fod_y", 0f);
        config.regionBlurRadius = xPrefs.getInt("region_blur_radius", 25);
        config.iconSpacingOffset = xPrefs.getInt("icon_spacing_offset", 5);
        config.bubblePaddingPercent = xPrefs.getInt("bubble_padding_percent", 0);
        config.bubbleBlurRadius = xPrefs.getInt("bubble_blur_radius", 25);
        config.selectedIconScale = xPrefs.getFloat("selected_icon_scale", 1.15f);
        config.appIconCropMode = xPrefs.getInt("app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
        config.glyphPadEnabled = xPrefs.getBoolean("glyph_pad_enabled", true);
        config.glyphPadColor = xPrefs.getInt("glyph_pad_color", 0xFF2A82E4);
        config.glyphPadMode = xPrefs.getInt("glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
        config.iconGlowEffect = xPrefs.getBoolean("icon_glow_effect", true);

        String jsonStr = xPrefs.getString("shortcuts_json", null);
        config.shortcuts = parseShortcutsJson(jsonStr);
        return config;
    }

    /**
     * 模块配置 Activity 加载配置
     */
    public static HyperOpenConfig loadFromApp(Context context) {
        // Direct Boot：配置统一存 DE 存储（旧版 CE 数据由 DePrefs 一次性迁移）
        SharedPreferences sp = io.github.qmryqmm.hyperopen.util.DePrefs.get(context, PREF_NAME);
        HyperOpenConfig config = new HyperOpenConfig();
        config.materialStyle = sp.getInt("material_style", MATERIAL_GAUSSIAN_BLUR);
        config.uiStyle = sp.getInt("ui_style", UI_STYLE_MATERIAL);
        config.themeMode = sp.getInt("theme_mode", THEME_FOLLOW_SYSTEM);
        config.monetColors = sp.getBoolean("monet_colors", false);
        config.barBlur = sp.getBoolean("bar_blur", true);
        config.floatingNavBar = sp.getBoolean("floating_nav_bar", false);
        config.predictiveBack = sp.getBoolean("predictive_back", true);
        config.edgeBack = sp.getBoolean("edge_back", true);
        config.pageSwitchGesture = sp.getInt("page_switch_gesture", PAGE_GESTURE_HORIZONTAL);
        config.offsetY = sp.getInt("offset_y", 100);
        config.regionDiameter = sp.getInt("region_diameter", 220);
        config.showRegionBg = sp.getBoolean("show_region_bg", false);
        config.showHeaderPillBg = sp.getBoolean("show_header_pill_bg", false);
        config.showHeaderPill = sp.getBoolean("show_header_pill", true);
        config.headerTopMargin = sp.getInt("header_top_margin", 100);
        config.headerTitleSize = sp.getInt("header_title_size", 17);
        config.headerSubtitleSize = sp.getInt("header_subtitle_size", 11);
        config.headerBlurRadius = sp.getInt("header_blur_radius", 25);
        config.headerTextCentered = sp.getBoolean("header_text_centered", true);
        config.iconSize = sp.getInt("icon_size", 60);
        config.maxIconCount = sp.getInt("max_icon_count", 0);
        config.layoutStyle = sp.getInt("layout_style", LAYOUT_HONEYCOMB);
        config.stockSystemAnim = sp.getBoolean("stock_system_anim", true);
        config.ringSectorMode = sp.getBoolean("ring_sector_mode", true);
        config.sectorLineColor = sp.getInt("sector_line_color", 0xFFBFBFBF);
        config.sectorLineAlpha = sp.getFloat("sector_line_alpha", 0.4f);
        config.sectorDashLength = sp.getInt("sector_dash_length", 6);
        config.sectorDashGap = sp.getInt("sector_dash_gap", 5);
        config.sectorLineWidth = sp.getFloat("sector_line_width", 1.2f);
        config.stockSystemRegion = sp.getBoolean("stock_system_region", true);
        config.ringCount = sp.getInt("ring_count", 2);
        config.maxScale = sp.getFloat("max_scale", 1.40f);
        config.influenceRadius = sp.getInt("influence_radius", 70);
        config.iconAlpha = sp.getFloat("icon_alpha", 1.0f);
        config.fisheyeZoomAlpha = sp.getFloat("fisheye_zoom_alpha", 1.0f);
        config.fullscreenBlurMaskAlpha = sp.getFloat("fullscreen_blur_mask_alpha", 1.0f);
        config.fullscreenDimAlpha = sp.getFloat("fullscreen_dim_alpha", 0.45f);
        config.headerDimAlpha = sp.getFloat("header_dim_alpha", 0f);
        config.regionDimAlpha = sp.getFloat("region_dim_alpha", 0f);
        config.bubbleDimAlpha = sp.getFloat("bubble_dim_alpha", 0f);
        config.showDelayMs = sp.getInt("show_delay_ms", 500);
        config.faceUnlockQuickOpen = sp.getBoolean("face_unlock_quick_open", true);
        config.hapticEnabled = sp.getBoolean("haptic_enabled", true);
        config.hapticStrength = sp.getInt("haptic_strength", 140);
        config.hapticDurationMs = sp.getInt("haptic_duration_ms", 24);
        config.hapticMode = sp.getInt("haptic_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveExpand = sp.getInt("haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
        config.hapticBrakePulse = sp.getBoolean("haptic_brake_pulse", false);
        config.hapticDoubleGapMs = sp.getInt("haptic_double_gap_ms", 28);
        config.hapticSlideEnabled = sp.getBoolean("haptic_slide_enabled", true);
        config.hapticSlideStrength = sp.getInt("haptic_slide_strength", 100);
        config.hapticSlideDurationMs = sp.getInt("haptic_slide_duration_ms", 12);
        config.hapticSlideMode = sp.getInt("haptic_slide_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveSlide = sp.getInt("haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
        config.hapticSlideBrakePulse = sp.getBoolean("haptic_slide_brake_pulse", false);
        config.hapticSlideDoubleGapMs = sp.getInt("haptic_slide_double_gap_ms", 28);
        config.lastRealFodX = sp.getFloat("last_real_fod_x", 0f);
        config.lastRealFodY = sp.getFloat("last_real_fod_y", 0f);
        config.regionBlurRadius = sp.getInt("region_blur_radius", 25);
        config.iconSpacingOffset = sp.getInt("icon_spacing_offset", 5);
        config.bubblePaddingPercent = sp.getInt("bubble_padding_percent", 0);
        config.bubbleBlurRadius = sp.getInt("bubble_blur_radius", 25);
        config.selectedIconScale = sp.getFloat("selected_icon_scale", 1.15f);
        config.appIconCropMode = sp.getInt("app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
        config.glyphPadEnabled = sp.getBoolean("glyph_pad_enabled", true);
        config.glyphPadColor = sp.getInt("glyph_pad_color", 0xFF2A82E4);
        config.glyphPadMode = sp.getInt("glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
        config.iconGlowEffect = sp.getBoolean("icon_glow_effect", true);

        String jsonStr = sp.getString("shortcuts_json", null);
        config.shortcuts = parseShortcutsJson(jsonStr);
        return config;
    }

    /**
     * 保存配置 (支持全局只读分享给 SystemUI + 实时广播通知)
     */
    public void save(Context context) {
        // Direct Boot：配置统一存 DE 存储
        SharedPreferences sp = io.github.qmryqmm.hyperopen.util.DePrefs.get(context, PREF_NAME);
        sp.edit()
            .putInt("material_style", materialStyle)
            .putInt("ui_style", uiStyle)
            .putInt("theme_mode", themeMode)
            .putBoolean("monet_colors", monetColors)
            .putBoolean("bar_blur", barBlur)
            .putBoolean("floating_nav_bar", floatingNavBar)
            .putBoolean("predictive_back", predictiveBack)
            .putBoolean("edge_back", edgeBack)
            .putInt("page_switch_gesture", pageSwitchGesture)
            .putInt("offset_y", offsetY)
            .putInt("region_diameter", regionDiameter)
            .putBoolean("show_region_bg", showRegionBg)
            .putBoolean("show_header_pill_bg", showHeaderPillBg)
            .putBoolean("show_header_pill", showHeaderPill)
            .putInt("header_top_margin", headerTopMargin)
            .putInt("header_title_size", headerTitleSize)
            .putInt("header_subtitle_size", headerSubtitleSize)
            .putInt("header_blur_radius", headerBlurRadius)
            .putBoolean("header_text_centered", headerTextCentered)
            .putInt("icon_size", iconSize)
            .putInt("max_icon_count", maxIconCount)
            .putInt("layout_style", layoutStyle)
            .putBoolean("stock_system_anim", stockSystemAnim)
            .putBoolean("stock_system_region", stockSystemRegion)
            .putBoolean("ring_sector_mode", ringSectorMode)
            .putInt("sector_line_color", sectorLineColor)
            .putFloat("sector_line_alpha", sectorLineAlpha)
            .putInt("sector_dash_length", sectorDashLength)
            .putInt("sector_dash_gap", sectorDashGap)
            .putFloat("sector_line_width", sectorLineWidth)
            .putInt("ring_count", ringCount)
            .putFloat("max_scale", maxScale)
            .putInt("influence_radius", influenceRadius)
            .putFloat("icon_alpha", iconAlpha)
            .putFloat("fisheye_zoom_alpha", fisheyeZoomAlpha)
            .putFloat("fullscreen_blur_mask_alpha", fullscreenBlurMaskAlpha)
            .putFloat("fullscreen_dim_alpha", fullscreenDimAlpha)
            .putFloat("header_dim_alpha", headerDimAlpha)
            .putFloat("region_dim_alpha", regionDimAlpha)
            .putFloat("bubble_dim_alpha", bubbleDimAlpha)
            .putInt("show_delay_ms", showDelayMs)
            .putBoolean("face_unlock_quick_open", faceUnlockQuickOpen)
            .putBoolean("haptic_enabled", hapticEnabled)
            .putInt("haptic_strength", hapticStrength)
            .putInt("haptic_duration_ms", hapticDurationMs)
            .putInt("haptic_mode", hapticMode)
            .putInt("haptic_stock_wave_expand", hapticStockWaveExpand)
            .putBoolean("haptic_brake_pulse", hapticBrakePulse)
            .putInt("haptic_double_gap_ms", hapticDoubleGapMs)
            .putBoolean("haptic_slide_enabled", hapticSlideEnabled)
            .putInt("haptic_slide_strength", hapticSlideStrength)
            .putInt("haptic_slide_duration_ms", hapticSlideDurationMs)
            .putInt("haptic_slide_mode", hapticSlideMode)
            .putInt("haptic_stock_wave_slide", hapticStockWaveSlide)
            .putBoolean("haptic_slide_brake_pulse", hapticSlideBrakePulse)
            .putInt("haptic_slide_double_gap_ms", hapticSlideDoubleGapMs)
            .putInt("region_blur_radius", regionBlurRadius)
            .putInt("icon_spacing_offset", iconSpacingOffset)
            .putInt("bubble_padding_percent", bubblePaddingPercent)
            .putInt("bubble_blur_radius", bubbleBlurRadius)
            .putFloat("selected_icon_scale", selectedIconScale)
            .putInt("app_icon_crop_mode", appIconCropMode)
            .putBoolean("glyph_pad_enabled", glyphPadEnabled)
            .putInt("glyph_pad_color", glyphPadColor)
            .putInt("glyph_pad_mode", glyphPadMode)
            .putBoolean("icon_glow_effect", iconGlowEffect)
            .putString("shortcuts_json", serializeShortcutsJson(shortcuts))
            .apply();

        // 开放 DE 目录与文件可读权限，为 XSharedPreferences 回退提供支持
        io.github.qmryqmm.hyperopen.util.DePrefs.applyWorldReadable(context, PREF_NAME);

        // 发送广播通知 SystemUI 实时热重载配置
        try {
            android.content.Intent intent = new android.content.Intent(ACTION_CONFIG_CHANGED);
            intent.setPackage("com.android.systemui");
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }

    public static List<ShortcutItem> parseShortcutsJson(String jsonStr) {
        List<ShortcutItem> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return getDefaultShortcuts();
        }
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(ShortcutItem.fromJson(obj));
            }
        } catch (Throwable t) {
            Log.e(TAG, "解析快捷方式 JSON 失败，回退到默认项", t);
            return getDefaultShortcuts();
        }
        return list;
    }

    public static String serializeShortcutsJson(List<ShortcutItem> items) {
        JSONArray arr = new JSONArray();
        for (ShortcutItem item : items) {
            arr.put(item.toJson());
        }
        return arr.toString();
    }

    public static List<ShortcutItem> getDefaultShortcuts() {
        List<ShortcutItem> items = new ArrayList<>();

        // 1. 微信扫一扫 (智能助理标准入口，配 Remix 扫一扫矢量图标 + 微信绿微透底垫)
        //    默认启动主空间微信扫码；要启动应用双开/分身（XSpace user 999）时在编辑界面「启动用户」里选择即可
        ShortcutItem wxScan = new ShortcutItem(
            "wx_scan", "微信扫一扫", "直接唤起扫码镜头",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.scanner.ui.BaseScanUI;end",
            "", "remix:qr-scan-2-line",
            0xFF07C160, true,
            true, 0xFF07C160, GLYPH_PAD_MODE_TRANSLUCENT
        );
        // userId 默认 0 = 主空间；要跨用户（如 999 双开）在编辑界面「启动用户」里选择，保存时会双写 URI 标记
        items.add(wxScan);

        // 2. 微信付款码 (智能助理官方付款码入口，配 Remix 银行卡矢量图标 + 微信绿微透底垫)
        items.add(new ShortcutItem(
            "wx_pay", "微信付款码", "出示收银台向商家付款",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;launchFlags=0x10000000;component=com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI;i.key_entry_scene=2;end",
            "", "remix:qr-code-line",
            0xFF07C160, true,
            true, 0xFF07C160, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 3. 微信收款码 (智能助理官方面对面二维码收款入口，配 Remix 付款码矢量图标 + 微信绿微透底垫)
        items.add(new ShortcutItem(
            "wx_receive", "微信收款码", "面对面二维码收款",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.collect.ui.CollectMainUI;end",
            "", "remix:money-cny-circle-line",
            0xFF07C160, true,
            true, 0xFF07C160, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 4. 支付宝扫一扫 (官方扫码协议，配 Remix 扫一扫图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
                "alipay_scan", "支付宝扫一扫", "扫码识别或骑行单车",
                ShortcutItem.ACTION_INTENT_URI, "intent://platformapi/startapp?appId=10000007#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end",
                "", "remix:qr-scan-2-line",
                0xFF1677FF, true,
                true, 0xFF1677FF, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 5. 支付宝付款码 (智能助理官方 Scheme，配 Remix 银行卡矢量图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
            "alipay_pay", "支付宝付款码", "出示收银台付款",
            ShortcutItem.ACTION_INTENT_URI, "alipays://platformapi/startapp?appId=20000056&chInfo=ch_xiaomiLeft",
            "", "remix:qr-code-fill",
            0xFF1677FF, true,
            true, 0xFF1677FF, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 6. 支付宝收款码 (智能助理官方收钱码协议，配 Remix 人民币矢量图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
            "alipay_receive", "支付宝收款码", "面对面二维码收钱",
            ShortcutItem.ACTION_INTENT_URI, "alipays://platformapi/startapp?appId=20000123&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
            "", "remix:money-cny-circle-fill",
            0xFF1677FF, true,
            true, 0xFF1677FF, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 7. 乘车码 (交通乘车码统一入口，配 Remix 地铁矢量图标 + 银联绿底垫)
        items.add(new ShortcutItem(
                "bus_code", "乘车码", "地铁公交一卡通刷码",
                ShortcutItem.ACTION_INTENT_URI, "intent://platformapi/startapp?appId=200011235#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end",
                "", "remix:subway-fill",
                0xFF52C41A, true,
                true, 0xFF52C41A, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 8. 手电筒 (系统底层 Camera Torch，配 Remix 手电筒矢量图标 + 暖阳橙微透底垫)
        items.add(new ShortcutItem(
            "flashlight", "手电筒", "开关后置 LED 强光",
            ShortcutItem.ACTION_FLASHLIGHT, "",
            "", "remix:flashlight-fill",
            0xFFFAAD14, true,
            true, 0xFFFAAD14, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        // 9. 街拍相机 (系统原生相机标准 Action，配 Remix 相机矢量图标 + 活力红底垫)
        items.add(new ShortcutItem(
            "camera", "街拍相机", "秒级开启抓拍",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;action=android.media.action.STILL_IMAGE_CAMERA;end",
            "", "remix:camera-fill",
            0xFFFF4D4F, true,
            true, 0xFFFF4D4F, GLYPH_PAD_MODE_TRANSLUCENT
        ));

        return items;
    }
}
