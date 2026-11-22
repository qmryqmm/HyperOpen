package io.github.qmryqmm.hyperopen.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /**
     * @deprecated 已由 {@link ShortcutItem#glyphPadAlpha}（独立前景色 + 纯色底垫透明度）取代，
     * 仅保留常量用于旧配置反序列化迁移与 IPC 兼容。
     */
    @Deprecated
    public static final int GLYPH_PAD_MODE_SOLID = 0;
    @Deprecated
    public static final int GLYPH_PAD_MODE_TRANSLUCENT = 1;
    @Deprecated
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

    /** 面板展开动画类型：原生直线 / 弹性绽放 / 淡入缩放 */
    public static final int PANEL_EXPAND_STOCK_LINE = 1;
    public static final int PANEL_EXPAND_BLOOM = 2;
    public static final int PANEL_EXPAND_FADE_SCALE = 3;

    /** 面板收起动画类型：向心收拢 / 淡出缩小 / 选中回弹 / 原生直线回收 */
    public static final int PANEL_COLLAPSE_CENTER = 0;
    public static final int PANEL_COLLAPSE_FADE_SCALE = 1;
    public static final int PANEL_COLLAPSE_SELECTED_POP = 2;
    public static final int PANEL_COLLAPSE_STOCK_LINE = 3;

    /** 文件夹子项展开方向 */
    public static final int FOLDER_CHILD_DIRECTION_OUTWARD = 1;
    public static final int FOLDER_CHILD_DIRECTION_INWARD = 2;
    public static final int FOLDER_CHILD_DIRECTION_UP = 3;
    public static final int FOLDER_CHILD_DIRECTION_DOWN = 4;
    public static final int FOLDER_CHILD_DIRECTION_LEFT = 5;
    public static final int FOLDER_CHILD_DIRECTION_RIGHT = 6;
    public static final int FOLDER_CHILD_DIRECTION_CUSTOM = 7;

    /** 文件夹子项展开动画类型 */
    public static final int FOLDER_CHILD_EXPAND_FAN_SPRING = 0;
    public static final int FOLDER_CHILD_EXPAND_STOCK_LINE = 1;
    public static final int FOLDER_CHILD_EXPAND_FADE_SCALE = 2;
    public static final int FOLDER_CHILD_EXPAND_WAVE = 3;

    /** 文件夹子项收起动画类型 */
    public static final int FOLDER_CHILD_COLLAPSE_CENTER = 0;
    public static final int FOLDER_CHILD_COLLAPSE_FADE_SCALE = 1;
    public static final int FOLDER_CHILD_COLLAPSE_REVERSE_WAVE = 2;

    /** 将旧版“跟随布局”转换成明确动画类型；旧数值 0 不再对外暴露。 */
    public static int normalizePanelExpandAnimType(int storedType, int layoutStyle,
                                                   boolean legacyStockSystemAnim) {
        if (storedType >= PANEL_EXPAND_STOCK_LINE && storedType <= PANEL_EXPAND_FADE_SCALE) {
            return storedType;
        }
        return layoutStyle == LAYOUT_SYSTEM_STOCK && legacyStockSystemAnim
                ? PANEL_EXPAND_STOCK_LINE
                : PANEL_EXPAND_BLOOM;
    }

    /** 旧版智能避边使用 0；现统一迁移为固定的“背离指纹”方向。 */
    public static int normalizeFolderChildDirectionMode(int storedMode) {
        if (storedMode == 0) {
            return FOLDER_CHILD_DIRECTION_OUTWARD;
        }
        return Math.max(FOLDER_CHILD_DIRECTION_OUTWARD, Math.min(FOLDER_CHILD_DIRECTION_CUSTOM, storedMode));
    }

    public int materialStyle = MATERIAL_GAUSSIAN_BLUR;
    /**
     * 设置界面风格（历史兼容字段，当前 App 统一使用 Miuix Compose UI，仅保留用于旧版 SharedPreferences / ConfigBackup 读写兼容）
     */
    @Deprecated
    public int uiStyle = UI_STYLE_MATERIAL;
    /** 主题模式（仅 App 内生效）：THEME_FOLLOW_SYSTEM / THEME_LIGHT / THEME_DARK */
    public int themeMode = THEME_FOLLOW_SYSTEM;
    /** Monet 动态取色（仅 App 内 Material 轨生效） */
    public boolean monetColors = false;
    /** 顶栏/底栏毛玻璃（历史兼容字段，当前 UI 栏毛玻璃固定由 Miuix 主题驱动，保留用于旧配置读写兼容） */
    public boolean barBlur = true;
    /** 悬浮底栏 */
    public boolean floatingNavBar = false;
    /** 预测性返回手势（Android 13+，历史兼容字段，当前由系统与 BackHandler 统一接管，保留用于配置读写兼容） */
    public boolean predictiveBack = true;
    /** 横移返回手势（历史兼容字段，当前 UI 未直接消费，保留用于旧配置读写兼容） */
    @Deprecated
    public boolean edgeBack = true;
    /** 页面切换手势（历史兼容字段，当前 UI 未直接消费，保留用于旧配置读写兼容）：PAGE_GESTURE_OFF / PAGE_GESTURE_HORIZONTAL */
    @Deprecated
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
    /** 系统经典布局：复用系统原生精准扇区多边形选中算法（关闭则走欧氏距离） */
    public boolean stockSystemRegion = true;
    /**
     * 空心圆环「扇区触发」模式：开启且快捷方式少于 6 个时，
     * 图标沿圆周均分排布，圆内为中心取消区，圆外按图标数量均分为扇形，
     * 每个扇形触发对应图标；该模式下强制开启图标吸附（禁止关闭吸附）。
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
    /** 扇区分隔线粗细 (0.5 ~ 4.0 dp，默认 1.2) */
    public float sectorLineWidth = 1.2f;
    /** 空心圆环扇区触发模式下是否高亮当前选中扇区 */
    public boolean sectorHighlightEnabled = true;

    /** 面板展开动画时长 (0 ~ 500 ms，默认 320 ms，0 为无动画瞬显) */
    public int animDurationMs = 320;
    public int panelExpandAnimType = PANEL_EXPAND_BLOOM;
    public int panelExpandStaggerMs = 12;
    /** 松手时是否启用向心收拢/弹跳动效 */
    public boolean dismissAnimEnabled = true;
    public int panelCollapseAnimType = PANEL_COLLAPSE_SELECTED_POP;
    public int panelCollapseDurationMs = 120;
    public int panelCollapseStaggerMs = 0;
    public float panelSelectedPopScale = 1.30f;

    /** 全局提示文字颜色模式：0=智能跟随图标底色/主题色(默认), 1=纯白高对比, 2=自定义统一色 */
    public static final int HINT_COLOR_MODE_AUTO = 0;
    public static final int HINT_COLOR_MODE_WHITE = 1;
    public static final int HINT_COLOR_MODE_CUSTOM = 2;
    public int globalHintColorMode = HINT_COLOR_MODE_AUTO;
    public int globalHintCustomColor = 0xFFFFFFFF;
    /** 全局默认提示文字描边宽度 (0 ~ 4 dp，默认 0) */
    public int defaultHintTextStroke = 0;
    /** 全局默认提示文字投影强度 (0 ~ 100，默认 0) */
    public int defaultHintTextShadow = 0;
    /** 全局默认标题字重 (0=Light, 1=Regular, 2=Medium, 3=Bold，默认 3) */
    public int defaultHintTextWeight = 3;
    /** 全局默认字间距 (-2 ~ +6，默认 0) */
    public float defaultHintTextLetterSpacing = 0f;
    /** 全局默认是否显示提示标题 (默认 true) */
    public boolean defaultShowHintTitle = true;
    /** 全局默认是否显示提示副标题 (默认 true) */
    public boolean defaultShowHintSubtitle = true;

    /** 文件夹二级气泡悬停展开延迟 (100 ~ 600 ms，默认 250 ms) */
    public int folderHoverDelayMs = 250;
    public float folderChildDisplayRangeScale = 1f;
    public int folderChildDirectionMode = FOLDER_CHILD_DIRECTION_OUTWARD;
    public float folderChildDirectionOffsetDeg = 0f;
    public int folderChildOrderMode = 0;
    /** 文件夹子项图标紧凑程度微调 (-10dp ~ 20dp，默认 8dp，负值更紧凑，正值更松散；仅对子项图标生效) */
    public int folderChildMinGapDp = 8;
    public float folderChildSizeScale = 1f;
    public int folderChildExpandAnimType = FOLDER_CHILD_EXPAND_FAN_SPRING;
    public int folderChildExpandDurationMs = 180;
    public int folderChildExpandStaggerMs = 15;
    public int folderChildCollapseAnimType = FOLDER_CHILD_COLLAPSE_CENTER;
    public int folderChildCollapseDurationMs = 140;
    public int folderChildCollapseStaggerMs = 0;
    public float folderChildStartScale = 0.1f;
    public float folderChildOvershoot = 1.2f;

    /** 鱼眼最大放大倍率：所有布局下命中图标的统一最终倍率 (1.10x ~ 2.00x，默认 1.60x) */
    public float maxScale = 1.60f;
    /** 感应辐射半径 (dp)：离手指多远开始放大，超出后回到常态；调小可实现「只放大命中图标」 */
    public int influenceRadius = 70;  // dp

    /** 图标透明度 (0.0f ~ 1.0f，默认 1.0f) */
    public float iconAlpha = 1.0f;
    /** 鱼眼效果放大时的图标透明度 (0.0f ~ 1.0f，默认 1.0f) */
    public float fisheyeZoomAlpha = 1.0f;
    /** 鱼眼效果放大时其他未选中图标的透明度 (0.0f ~ 1.0f，默认 1.0f) */
    public float fisheyeOtherAlpha = 1.0f;
    /** 图标与气泡圆角比例 (0 ~ 50%，默认 50% = 正圆形，0% = 直角矩形) */
    public int iconCornerRadius = 50;
    /** 唤醒指纹快捷方式时全屏模糊遮罩模糊程度 (0.0f ~ 1.0f，默认 1.0f) */
    public float fullscreenBlurMaskAlpha = 1.0f;
    /** 唤醒指纹快捷方式时全屏遮罩背景压暗程度 (0.0f ~ 1.0f，默认 0.45f) */
    public float fullscreenDimAlpha = 0.45f;
    public float headerDimAlpha = 0f;   // 顶部提示胶囊背景压暗 (0.0 ~ 1.0)
    public float regionDimAlpha = 0f;   // 圆形图标显示区域背景压暗 (0.0 ~ 1.0)
    public float bubbleDimAlpha = 0f;   // 图标气泡留白区域压暗 (0.0 ~ 1.0)
    /** 指纹解锁后进入快捷方式面板的等待阈值 (0 ~ 2000 ms，默认 500 = 系统默认不干预，0 = 立即弹出) */
    public int showDelayMs = 500;
    /** 关闭系统界面进入指纹快捷方式时的 600ms 圆环加载过渡动画 (默认 false = 保持系统默认加载圆环) */
    public boolean disableSystemEnterAnim = false;
    /** 人脸识别成功停留在锁屏时，按指纹进桌面并支持长按唤出快捷方式 (默认开启) */
    public boolean faceUnlockQuickOpen = true;
    /** 蓝牙设备或 Android 可信设备解锁成功停留在锁屏时，按指纹进桌面并支持长按唤出快捷方式 (默认开启) */
    public boolean bleUnlockQuickOpen = true;

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

    /** 面板图标分布紧凑程度微调 (-10dp ~ 20dp，默认 5dp，负值更紧凑，正值更松散；仅对面板图标生效) */
    public int iconSpacingOffset = 5;
    /**
     * 气泡内部留白比例 (0 ~ 40%)：0% 时无外圈留白、材质直接贴合图标本体；
     * 气泡底衬/模糊/压暗开关由 {@link #showBubbleBg} 独立控制。
     */
    public int bubblePaddingPercent = 0;
    /** 是否启用图标气泡/背景材质（柔光玻璃或高斯模糊、光学边框与压暗）；开启且留白 0% 时材质直接贴合图标本体 */
    public boolean showBubbleBg = false;
    /** 图标气泡留白区域高斯模糊半径 (0 ~ 50 dp，默认 25 dp，0 为关闭模糊，样式与圆形视界底衬一致) */
    public int bubbleBlurRadius = 25;

    /** 应用图标全局默认裁切方案 (CROP_BITMAP_CIRCLE / CROP_ADAPTIVE_EXPAND；单项 ShortcutItem.appIconCropMode 优先，此字段作为全局回退与旧配置兼容保留) */
    public int appIconCropMode = CROP_ADAPTIVE_EXPAND;
    /** Miuix 矢量图标全局默认底垫开关（历史兼容字段，现已改为按单项 ShortcutItem.glyphPadEnabled 独立配置，保留用于旧配置与 ConfigBackup 兼容） */
    public boolean glyphPadEnabled = true;
    /** Miuix 矢量图标全局默认底垫颜色 (ARGB；历史兼容字段，现已改为按单项 ShortcutItem.glyphPadColor 独立配置，保留用于旧配置与 ConfigBackup 兼容) */
    public int glyphPadColor = 0xFF2A82E4;
    /** Miuix 矢量图标底垫质感模式 (GLYPH_PAD_MODE_SOLID / TRANSLUCENT / INVERTED；历史兼容字段，现已由 ShortcutItem.glyphPadAlpha 取代，保留用于旧配置与 ConfigBackup 兼容) */
    public int glyphPadMode = GLYPH_PAD_MODE_TRANSLUCENT;
    /** 图标呼吸/激活外发光辉光动效（已移除，保留字段兼容旧配置读取） */
    @Deprecated
    public boolean iconGlowEffect = false;

    // ── 柔光玻璃系统级细调参数 ──
    public static final int GLASS_PRESET_DEFAULT = 0;
    public static final int GLASS_PRESET_CUSTOM = 1;

    /** 柔光玻璃预设 (0=系统默认值, 1=自定义调参) */
    public int glassPreset = GLASS_PRESET_DEFAULT;
    /** 折射率 / 光学畸变曲率 (1.0 ~ 2.0，系统默认 1.12f) */
    public float glassRefractIor = 1.12f;
    /** 边缘光学厚度 (0.5 ~ 4.0，系统默认 1.5f) */
    public float glassThickness = 1.5f;
    /** 镜面反射与高光强度 (0.0 ~ 2.0，系统默认 0.8f) */
    public float glassSpecular = 0.8f;
    /** 雾面底色不透明度 (0.05 ~ 0.40，系统默认 0.12f) */
    public float glassMatteAlpha = 0.12f;
    /** 边缘微光描边亮度 (0.10 ~ 1.00，系统默认 0.45f) */
    public float glassBorderLight = 0.45f;
    /** 玻璃内背景采样饱和度 (0 ~ 3，系统默认 1.5) */
    public float glassBackgroundSaturation = 1.5f;
    /** 玻璃内背景采样亮度 (-1 ~ 1，系统默认 0) */
    public float glassBackgroundBrightness = 0f;
    /** 玻璃内背景暗部烧灼强度 (0 ~ 1，系统默认 0) */
    public float glassBackgroundBurn = 0f;
    /** 雾面白色混合量 (0 ~ 1，系统默认 0) */
    public float glassMatteWhiteMix = 0f;
    /** 雾面颜色混合量 (0 ~ 1，系统默认 0.4) */
    public float glassMatteColorMix = 0.4f;
    /** 雾面颜色幂曲线 (0.25 ~ 4，系统默认 1.5) */
    public float glassMatteColorPower = 1.5f;
    /** 边缘光作用范围 (0 ~ 100，系统默认 50) */
    public float glassEdgeRange = 50f;
    /** 边缘光幂曲线 (1 ~ 10，系统默认 4) */
    public float glassEdgePower = 4f;
    /** 表面反射纹理偏移 (0 ~ 1200，系统默认 600) */
    public float glassReflectOffset = 600f;

    // ── 全局模糊材质（BlurMaterialSettingsPage）──
    /** 统一模糊半径 (0 ~ 50 dp)，跟随全局时三组件共用 */
    public int globalBlurRadius = 25;
    /** 统一压暗程度 (0.0 ~ 1.0)，跟随全局时三组件共用 */
    public float globalDimAlpha = 0f;

    // ── 每组件材质跟随全局开关 ──
    public boolean bubbleMaterialFollowGlobal = true;
    public boolean regionMaterialFollowGlobal = true;
    public boolean headerMaterialFollowGlobal = true;

    // ── 每组件柔光玻璃 matteAlpha（跟随关闭时生效，Plan B 驱动模糊半径）──
    public float bubbleGlassMatteAlpha = 0.12f;
    public float regionGlassMatteAlpha = 0.12f;
    public float headerGlassMatteAlpha = 0.12f;

    /** 模糊基线一次性自愈标记（见 {@link #healBlurBaselineIfNeeded}） */
    public boolean blurBaselineHealed = false;

    // ──────────────────────────────────────────────────────────────
    // 组件级材质有效值解析（唯一数据源）
    //
    // 图标气泡 / 图标显示区域底衬 / 顶部提示胶囊三种载体的模糊、压暗与磨砂参数
    // 必须经由此处的 effective* 系列解析后再喂给渲染层：
    //   · 模糊半径：以模糊滑块为基准（跟随全局取 globalBlurRadius，否则取组件独立值）；
    //     柔光玻璃模式下磨砂浓度只做向上增益，绝不把模糊抽空；
    //   · 磨砂浓度：全局（预设/自定义玻璃参数）或组件级「柔光磨砂浓度」，
    //     驱动柔光雾面渐变与模糊底色 tint；
    //   · 「跟随全局材质」关闭时取组件独立参数，开启时取全局参数；
    // 真机（CustomHyperOpenContainer）与 App 预览（HyperOpenPreviewView / ShortcutCardList）
    // 都必须走同一入口，禁止任何一侧再各自换算，否则预览与实机效果必然漂移。
    // ──────────────────────────────────────────────────────────────

    /** 材质载体：图标气泡 */
    public static final int COMPONENT_BUBBLE = 0;
    /** 材质载体：图标显示区域底衬 */
    public static final int COMPONENT_REGION = 1;
    /** 材质载体：顶部提示胶囊 */
    public static final int COMPONENT_HEADER = 2;

    /**
     * 柔光磨砂浓度 (0.0 ~ 0.40) → 模糊半径 dp (0 ~ 50) 的换算。
     * 仅作为柔光模式下磨砂对模糊的「向上增益」，绝不作为模糊的唯一来源
     * （历史上以此值整体替换模糊半径，磨砂滑块被拖低时模糊随之塌缩消失）。
     */
    public static int matteToBlurDp(float matteAlpha) {
        float clamped = Math.max(0f, Math.min(0.40f, matteAlpha));
        return (int) (clamped / 0.40f * 50f);
    }

    /** 该组件材质是否跟随全局 */
    public boolean materialFollowGlobal(int component) {
        switch (component) {
            case COMPONENT_BUBBLE: return bubbleMaterialFollowGlobal;
            case COMPONENT_REGION: return regionMaterialFollowGlobal;
            default: return headerMaterialFollowGlobal;
        }
    }

    /** 该组件独立的模糊半径滑块原始值 (dp) */
    private int rawBlurRadiusFor(int component) {
        switch (component) {
            case COMPONENT_BUBBLE: return bubbleBlurRadius;
            case COMPONENT_REGION: return regionBlurRadius;
            default: return headerBlurRadius;
        }
    }

    /**
     * 该组件最终生效的磨砂浓度：
     * 跟随全局 → 全局 {@link HyperOSMaterialFactory.GlassParams}（预设或自定义）的 matteAlpha；
     * 独立 → 组件级「柔光磨砂浓度」滑块值。柔光玻璃的雾面渐变与模糊底色 tint 都用此值。
     */
    public float effectiveMatteAlpha(int component) {
        float custom;
        if (materialFollowGlobal(component)) {
            custom = HyperOSMaterialFactory.GlassParams.fromConfig(this).matteAlpha;
        } else {
            switch (component) {
                case COMPONENT_BUBBLE: custom = bubbleGlassMatteAlpha; break;
                case COMPONENT_REGION: custom = regionGlassMatteAlpha; break;
                default: custom = headerGlassMatteAlpha; break;
            }
        }
        return HyperOSMaterialFactory.clampMatte(custom);
    }

    /**
     * 该组件最终生效的模糊半径 (dp)：
     * <ul>
     *   <li>柔光玻璃模式：直接由当前组件生效的磨砂浓度（{@link #effectiveMatteAlpha} → {@link #matteToBlurDp}）
     *       全段线性驱动（5%~40% 对应 6~50dp），不被高斯模式残留的 globalBlurRadius / *BlurRadius 锁死；</li>
     *   <li>高斯模糊模式（含不支持柔光玻璃设备经 {@link #adaptUnsupportedSoftGlass} 降级后）：
     *       跟随全局取 globalBlurRadius，否则取组件独立模糊滑块值，设 0 即关闭模糊。</li>
     * </ul>
     */
    public int effectiveBlurRadiusDp(int component) {
        if (materialStyle == MATERIAL_SOFT_GLASS) {
            return matteToBlurDp(effectiveMatteAlpha(component));
        }
        int base = materialFollowGlobal(component) ? globalBlurRadius : rawBlurRadiusFor(component);
        return Math.max(0, base);
    }

    /**
     * 全屏模糊半径的本地近似值（px）。
     *
     * <p>SystemUI 侧真实半径由 {@code BlurUtils#blurRadiusOfRatio} 计算：
     * {@code lerp(min_window_blur_radius, max_window_blur_radius, ratio)}。
     * App 进程拿不到该实例，这里按同一语义使用 1px ~ 100px 近似；
     * 真机侧会优先传入 {@code BlurUtils} 算出的精确 px 值。</p>
     */
    public float approximateFullscreenBlurRadiusPx() {
        float ratio = Math.max(0f, Math.min(1f, fullscreenBlurMaskAlpha));
        return ratio <= 0.001f ? 0f : (1f + 99f * ratio);
    }

    /**
     * 「全屏模糊 + 组件自身模糊」叠加后的组件模糊半径 (dp)。
     *
     * <p>两个 BackgroundBlurDrawable 不会在同一窗口里做物理两级采样：
     * 气泡/底衬区域会用自己的半径覆盖窗口级全屏模糊，半径小于全屏时就会出现
     * 一块比周围更清晰的「洞」。这里用高斯近似的组合半径
     * {@code sqrt(r_fullscreen² + r_component²)}，保证组件区域永远不比全屏更清晰，
     * 同时保留组件自身的额外磨砂强度。</p>
     *
     * <p>组件自身模糊为 0 时返回 0：不注册自己的 drawable，直接继承窗口级全屏模糊。</p>
     *
     * @param fullscreenBlurPx 全屏模糊半径 px；传负数时使用本地近似值
     */
    public float stackedBlurRadiusDp(int component, float density, float fullscreenBlurPx) {
        float componentDp = effectiveBlurRadiusDp(component);
        if (componentDp <= 0.01f) return 0f;
        float d = Math.max(0.01f, density);
        float fsPx = fullscreenBlurPx >= 0f ? fullscreenBlurPx : approximateFullscreenBlurRadiusPx();
        float componentPx = componentDp * d;
        return (float) Math.sqrt(componentPx * componentPx + fsPx * fsPx) / d;
    }

    /**
     * 该组件最终生效的压暗程度 (0.0 ~ 1.0)：
     * 柔光玻璃模式下不叠加高斯模式的黑底压暗蒙层（避免柔光下残留无法关闭的黑底）；
     * 高斯模式下跟随全局用 globalDimAlpha，否则用组件独立值。
     */
    public float effectiveDimAlpha(int component) {
        if (materialStyle == MATERIAL_SOFT_GLASS) {
            return 0f;
        }
        float v;
        if (materialFollowGlobal(component)) {
            v = globalDimAlpha;
        } else {
            switch (component) {
                case COMPONENT_BUBBLE: v = bubbleDimAlpha; break;
                case COMPONENT_REGION: v = regionDimAlpha; break;
                default: v = headerDimAlpha; break;
            }
        }
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * 模糊/压暗基线一次性自愈（所有加载入口统一调用）。
     * <p>
     * 历史背景：重构前真机侧恒以组件独立 *BlurRadius / *DimAlpha 渲染，
     * 「跟随全局」与全局滑块从未在真机生效，于是配置里残留了
     * 「globalBlurRadius=0 / globalDimAlpha=0，但组件值 >0 且跟随全局=开」的
     * 陷阱状态（多为调试滑块时留下）。统一生效链路后若不治愈，模糊/压暗会整体消失。
     * 此处一次性把全局值提升为跟随中的组件最大值；治愈标记持久化后，
     * 用户再显式设置的 0（已关闭模糊/不压暗）会被尊重，不再干预。
     */
    public void healBlurBaselineIfNeeded() {
        if (blurBaselineHealed) return;
        blurBaselineHealed = true;
        if (globalBlurRadius <= 0) {
            int maxRaw = 0;
            if (bubbleMaterialFollowGlobal) maxRaw = Math.max(maxRaw, bubbleBlurRadius);
            if (regionMaterialFollowGlobal) maxRaw = Math.max(maxRaw, regionBlurRadius);
            if (headerMaterialFollowGlobal) maxRaw = Math.max(maxRaw, headerBlurRadius);
            if (maxRaw > 0) globalBlurRadius = maxRaw;
        }
        if (globalDimAlpha <= 0f) {
            float maxDim = 0f;
            if (bubbleMaterialFollowGlobal) maxDim = Math.max(maxDim, bubbleDimAlpha);
            if (regionMaterialFollowGlobal) maxDim = Math.max(maxDim, regionDimAlpha);
            if (headerMaterialFollowGlobal) maxDim = Math.max(maxDim, headerDimAlpha);
            if (maxDim > 0f) globalDimAlpha = maxDim;
        }
    }

    /**
     * 在不支持柔光玻璃材质的设备上，统一将柔光玻璃配置降级为高斯模糊并做模糊半径补偿。
     * 所有加载入口与备份对比共用此规则，确保降级应用后的生效配置与导入/预览基线一致。
     */
    public static void adaptUnsupportedSoftGlass(HyperOpenConfig config) {
        if (config == null || HyperOSMaterialFactory.isSoftGlassSupported()) {
            return;
        }
        if (config.materialStyle != MATERIAL_SOFT_GLASS) {
            return;
        }
        HyperOSMaterialFactory.GlassParams globalGlass =
                HyperOSMaterialFactory.GlassParams.fromConfig(config);
        int compensatedGlobal = matteToBlurDp(
                HyperOSMaterialFactory.clampMatte(globalGlass.matteAlpha));
        if (compensatedGlobal > config.globalBlurRadius) {
            config.globalBlurRadius = compensatedGlobal;
        }
        if (!config.bubbleMaterialFollowGlobal) {
            int compensatedBubble = matteToBlurDp(
                    HyperOSMaterialFactory.clampMatte(config.bubbleGlassMatteAlpha));
            if (compensatedBubble > config.bubbleBlurRadius) {
                config.bubbleBlurRadius = compensatedBubble;
            }
        }
        if (!config.regionMaterialFollowGlobal) {
            int compensatedRegion = matteToBlurDp(
                    HyperOSMaterialFactory.clampMatte(config.regionGlassMatteAlpha));
            if (compensatedRegion > config.regionBlurRadius) {
                config.regionBlurRadius = compensatedRegion;
            }
        }
        if (!config.headerMaterialFollowGlobal) {
            int compensatedHeader = matteToBlurDp(
                    HyperOSMaterialFactory.clampMatte(config.headerGlassMatteAlpha));
            if (compensatedHeader > config.headerBlurRadius) {
                config.headerBlurRadius = compensatedHeader;
            }
        }
        config.materialStyle = MATERIAL_GAUSSIAN_BLUR;
    }

    /**
     * 该组件最终生效的玻璃光学参数：全局 GlassParams 基础上叠加组件级磨砂浓度。
     * 图标气泡、图标显示区域底衬、顶部提示胶囊必须共用此入口，保证严格同一套材质参数。
     */
    public HyperOSMaterialFactory.GlassParams glassParamsFor(int component) {
        return HyperOSMaterialFactory.GlassParams.fromConfig(this).withMatteAlpha(effectiveMatteAlpha(component));
    }

    public List<ShortcutItem> shortcuts = new ArrayList<>();

    private static XSharedPreferences xPrefs;
    private static boolean xPrefsIsDe = false;
    private static final ExecutorService SAVE_IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HyperOpen-ConfigSave");
        t.setDaemon(true);
        return t;
    });

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
                if (bundle != null && !bundle.isEmpty()) {
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
                    config.ringSectorMode = bundle.getBoolean("ring_sector_mode", true);
                    config.sectorLineColor = bundle.getInt("sector_line_color", 0xFFBFBFBF);
                    config.sectorLineAlpha = bundle.getFloat("sector_line_alpha", 0.4f);
                    config.sectorDashLength = bundle.getInt("sector_dash_length", 6);
                    config.sectorDashGap = bundle.getInt("sector_dash_gap", 5);
                    config.sectorLineWidth = bundle.getFloat("sector_line_width", 1.2f);
                    config.sectorHighlightEnabled = bundle.getBoolean("sector_highlight_enabled", true);
                    config.animDurationMs = bundle.getInt("anim_duration_ms", 320);
                    config.panelExpandAnimType = normalizePanelExpandAnimType(
                            bundle.getInt("panel_expand_anim_type", PANEL_EXPAND_BLOOM),
                            config.layoutStyle,
                            bundle.getBoolean("stock_system_anim", true)
                    );
                    config.panelExpandStaggerMs = bundle.getInt("panel_expand_stagger_ms", 12);
                    config.dismissAnimEnabled = bundle.getBoolean("dismiss_anim_enabled", true);
                    config.panelCollapseAnimType = bundle.getInt("panel_collapse_anim_type", PANEL_COLLAPSE_SELECTED_POP);
                    config.panelCollapseDurationMs = bundle.getInt("panel_collapse_duration_ms", 120);
                    config.panelCollapseStaggerMs = bundle.getInt("panel_collapse_stagger_ms", 0);
                    config.panelSelectedPopScale = bundle.getFloat("panel_selected_pop_scale", 1.30f);
                    config.globalHintColorMode = bundle.getInt("global_hint_color_mode", HINT_COLOR_MODE_AUTO);
                    config.globalHintCustomColor = bundle.getInt("global_hint_custom_color", 0xFFFFFFFF);
                    config.defaultHintTextStroke = bundle.getInt("default_hint_text_stroke", 0);
                    config.defaultHintTextShadow = bundle.getInt("default_hint_text_shadow", 0);
                    config.defaultHintTextWeight = bundle.getInt("default_hint_text_weight", 3);
                    config.defaultHintTextLetterSpacing = bundle.getFloat("default_hint_text_letter_spacing", 0f);
                    config.defaultShowHintTitle = bundle.getBoolean("default_show_hint_title", true);
                    config.defaultShowHintSubtitle = bundle.getBoolean("default_show_hint_subtitle", true);
                    config.folderHoverDelayMs = bundle.getInt("folder_hover_delay_ms", 250);
                    config.stockSystemRegion = bundle.getBoolean("stock_system_region", true);
                    config.folderChildDisplayRangeScale = bundle.getFloat("folder_child_display_range_scale", 1f);
                    config.folderChildDirectionMode = normalizeFolderChildDirectionMode(
                            bundle.getInt("folder_child_direction_mode", FOLDER_CHILD_DIRECTION_OUTWARD)
                    );
                    config.folderChildDirectionOffsetDeg = bundle.getFloat("folder_child_direction_offset_deg", 0f);
                    config.folderChildOrderMode = bundle.getInt("folder_child_order_mode", 0);
                    config.folderChildMinGapDp = bundle.getInt("folder_child_min_gap_dp", 8);
                    config.folderChildSizeScale = bundle.getFloat("folder_child_size_scale", 1f);
                    config.folderChildExpandAnimType = bundle.getInt("folder_child_expand_anim_type", FOLDER_CHILD_EXPAND_FAN_SPRING);
                    config.folderChildExpandDurationMs = bundle.getInt("folder_child_expand_duration_ms", 180);
                    config.folderChildExpandStaggerMs = bundle.getInt("folder_child_expand_stagger_ms", 15);
                    config.folderChildCollapseAnimType = bundle.getInt("folder_child_collapse_anim_type", FOLDER_CHILD_COLLAPSE_CENTER);
                    config.folderChildCollapseDurationMs = bundle.getInt("folder_child_collapse_duration_ms", 140);
                    config.folderChildCollapseStaggerMs = bundle.getInt("folder_child_collapse_stagger_ms", 0);
                    config.folderChildStartScale = bundle.getFloat("folder_child_start_scale", 0.1f);
                    config.folderChildOvershoot = bundle.getFloat("folder_child_overshoot", 1.2f);
                    config.maxScale = bundle.getFloat("max_scale", 1.60f);
                    config.influenceRadius = bundle.getInt("influence_radius", 70);
                    config.iconAlpha = bundle.getFloat("icon_alpha", 1.0f);
                    config.fisheyeZoomAlpha = bundle.getFloat("fisheye_zoom_alpha", 1.0f);
                    config.fisheyeOtherAlpha = bundle.getFloat("fisheye_other_alpha", 1.0f);
                    config.iconCornerRadius = bundle.getInt("icon_corner_radius", 50);
                    config.fullscreenBlurMaskAlpha = bundle.getFloat("fullscreen_blur_mask_alpha", 1.0f);
                    config.fullscreenDimAlpha = bundle.getFloat("fullscreen_dim_alpha", 0.45f);
                    config.headerDimAlpha = bundle.getFloat("header_dim_alpha", 0f);
                    config.regionDimAlpha = bundle.getFloat("region_dim_alpha", 0f);
                    config.bubbleDimAlpha = bundle.getFloat("bubble_dim_alpha", 0f);
                    config.showDelayMs = bundle.getInt("show_delay_ms", 500);
                    config.disableSystemEnterAnim = bundle.getBoolean("disable_system_enter_anim", false);
                    config.faceUnlockQuickOpen = bundle.getBoolean("face_unlock_quick_open", true);
                    config.bleUnlockQuickOpen = bundle.getBoolean("ble_unlock_quick_open", true);
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
                    config.showBubbleBg = bundle.containsKey("show_bubble_bg")
                            ? bundle.getBoolean("show_bubble_bg", false)
                            : (config.bubblePaddingPercent > 0);
                    config.bubbleBlurRadius = bundle.getInt("bubble_blur_radius", 25);
                    // 旧版 selected_icon_scale 折算迁移：峰值 maxScale × selectedIconScale 一次性合并进 maxScale
                    migrateLegacySelectedScale(config, bundle.getFloat("selected_icon_scale", 1.0f));
                    config.appIconCropMode = bundle.getInt("app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
                    config.glyphPadEnabled = bundle.getBoolean("glyph_pad_enabled", true);
                    config.glyphPadColor = bundle.getInt("glyph_pad_color", 0xFF2A82E4);
                    config.glyphPadMode = bundle.getInt("glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
                    config.iconGlowEffect = false;

                    config.glassPreset = bundle.getInt("glass_preset", GLASS_PRESET_DEFAULT);
                    config.glassRefractIor = bundle.getFloat("glass_refract_ior", 1.12f);
                    config.glassThickness = bundle.getFloat("glass_thickness", 1.5f);
                    config.glassSpecular = bundle.getFloat("glass_specular", 0.8f);
                    config.glassMatteAlpha = bundle.getFloat("glass_matte_alpha", 0.12f);
                    config.glassBorderLight = bundle.getFloat("glass_border_light", 0.45f);
                    config.glassBackgroundSaturation = bundle.getFloat("glass_background_saturation", 1.5f);
                    config.glassBackgroundBrightness = bundle.getFloat("glass_background_brightness", 0f);
                    config.glassBackgroundBurn = bundle.getFloat("glass_background_burn", 0f);
                    config.glassMatteWhiteMix = bundle.getFloat("glass_matte_white_mix", 0f);
                    config.glassMatteColorMix = bundle.getFloat("glass_matte_color_mix", 0.4f);
                    config.glassMatteColorPower = bundle.getFloat("glass_matte_color_power", 1.5f);
                    config.glassEdgeRange = bundle.getFloat("glass_edge_range", 50f);
                    config.glassEdgePower = bundle.getFloat("glass_edge_power", 4f);
                    config.glassReflectOffset = bundle.getFloat("glass_reflect_offset", 600f);
                    config.globalBlurRadius = bundle.getInt("global_blur_radius", 25);
                    config.globalDimAlpha = bundle.getFloat("global_dim_alpha", 0f);
                    config.bubbleMaterialFollowGlobal = bundle.getBoolean("bubble_material_follow_global", true);
                    config.regionMaterialFollowGlobal = bundle.getBoolean("region_material_follow_global", true);
                    config.headerMaterialFollowGlobal = bundle.getBoolean("header_material_follow_global", true);
                    config.bubbleGlassMatteAlpha = bundle.getFloat("bubble_glass_matte_alpha", 0.12f);
                    config.regionGlassMatteAlpha = bundle.getFloat("region_glass_matte_alpha", 0.12f);
                    config.headerGlassMatteAlpha = bundle.getFloat("header_glass_matte_alpha", 0.12f);
                    config.blurBaselineHealed = bundle.getBoolean("blur_baseline_healed", false);

                    if (!bundle.containsKey("global_blur_radius")) {
                        migrateFromLegacy(config);
                    }
                    config.healBlurBaselineIfNeeded();
                    adaptUnsupportedSoftGlass(config);

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
        java.io.File deFile = new java.io.File(
            "/data/user_de/0/" + PACKAGE_NAME + "/shared_prefs/" + PREF_NAME + ".xml");
        boolean deReady = deFile.exists() && deFile.canRead();
        if (xPrefs == null) {
            // prefs 已迁 DE：优先读 /data/user_de/0/<pkg>/shared_prefs/<name>.xml，
            // 开机早期尚不存在或暂不可读时先回退 CE，后续调用一旦 DE 就绪再热切换过去。
            if (deReady) {
                xPrefs = new XSharedPreferences(deFile);
                xPrefsIsDe = true;
            } else {
                xPrefs = new XSharedPreferences(PACKAGE_NAME, PREF_NAME);
                xPrefsIsDe = false;
            }
            xPrefs.makeWorldReadable();
        } else if (!xPrefsIsDe && deReady) {
            xPrefs = new XSharedPreferences(deFile);
            xPrefs.makeWorldReadable();
            xPrefsIsDe = true;
            Log.i(TAG, "XSharedPreferences 已从 CE 热切换至 DE: " + deFile.getAbsolutePath());
        } else {
            xPrefs.reload();
        }

        HyperOpenConfig config = new HyperOpenConfig();
        config.materialStyle = safeGetInt(xPrefs, "material_style", MATERIAL_GAUSSIAN_BLUR);
        config.offsetY = safeGetInt(xPrefs, "offset_y", 100);
        config.regionDiameter = safeGetInt(xPrefs, "region_diameter", 220);
        config.showRegionBg = safeGetBoolean(xPrefs, "show_region_bg", false);
        config.showHeaderPillBg = safeGetBoolean(xPrefs, "show_header_pill_bg", false);
        config.showHeaderPill = safeGetBoolean(xPrefs, "show_header_pill", true);
        config.headerTopMargin = safeGetInt(xPrefs, "header_top_margin", 100);
        config.headerTitleSize = safeGetInt(xPrefs, "header_title_size", 17);
        config.headerSubtitleSize = safeGetInt(xPrefs, "header_subtitle_size", 11);
        config.headerBlurRadius = safeGetInt(xPrefs, "header_blur_radius", 25);
        config.headerTextCentered = safeGetBoolean(xPrefs, "header_text_centered", true);
        config.iconSize = safeGetInt(xPrefs, "icon_size", 60);
        config.maxIconCount = safeGetInt(xPrefs, "max_icon_count", 0);
        config.layoutStyle = safeGetInt(xPrefs, "layout_style", LAYOUT_HONEYCOMB);
        config.ringSectorMode = safeGetBoolean(xPrefs, "ring_sector_mode", true);
        config.sectorLineColor = safeGetInt(xPrefs, "sector_line_color", 0xFFBFBFBF);
        config.sectorLineAlpha = safeGetFloat(xPrefs, "sector_line_alpha", 0.4f);
        config.sectorDashLength = safeGetInt(xPrefs, "sector_dash_length", 6);
        config.sectorDashGap = safeGetInt(xPrefs, "sector_dash_gap", 5);
        config.sectorLineWidth = safeGetFloat(xPrefs, "sector_line_width", 1.2f);
        config.sectorHighlightEnabled = safeGetBoolean(xPrefs, "sector_highlight_enabled", true);
        config.animDurationMs = safeGetInt(xPrefs, "anim_duration_ms", 320);
        config.panelExpandAnimType = normalizePanelExpandAnimType(
                safeGetInt(xPrefs, "panel_expand_anim_type", PANEL_EXPAND_BLOOM),
                config.layoutStyle,
                safeGetBoolean(xPrefs, "stock_system_anim", true)
        );
        config.panelExpandStaggerMs = safeGetInt(xPrefs, "panel_expand_stagger_ms", 12);
        config.dismissAnimEnabled = safeGetBoolean(xPrefs, "dismiss_anim_enabled", true);
        config.panelCollapseAnimType = safeGetInt(xPrefs, "panel_collapse_anim_type", PANEL_COLLAPSE_SELECTED_POP);
        config.panelCollapseDurationMs = safeGetInt(xPrefs, "panel_collapse_duration_ms", 120);
        config.panelCollapseStaggerMs = safeGetInt(xPrefs, "panel_collapse_stagger_ms", 0);
        config.panelSelectedPopScale = safeGetFloat(xPrefs, "panel_selected_pop_scale", 1.30f);
        config.globalHintColorMode = safeGetInt(xPrefs, "global_hint_color_mode", HINT_COLOR_MODE_AUTO);
        config.globalHintCustomColor = safeGetInt(xPrefs, "global_hint_custom_color", 0xFFFFFFFF);
        config.defaultHintTextStroke = safeGetInt(xPrefs, "default_hint_text_stroke", 0);
        config.defaultHintTextShadow = safeGetInt(xPrefs, "default_hint_text_shadow", 0);
        config.defaultHintTextWeight = safeGetInt(xPrefs, "default_hint_text_weight", 3);
        config.defaultHintTextLetterSpacing = safeGetFloat(xPrefs, "default_hint_text_letter_spacing", 0f);
        config.defaultShowHintTitle = safeGetBoolean(xPrefs, "default_show_hint_title", true);
        config.defaultShowHintSubtitle = safeGetBoolean(xPrefs, "default_show_hint_subtitle", true);
        config.folderHoverDelayMs = safeGetInt(xPrefs, "folder_hover_delay_ms", 250);
        config.stockSystemRegion = safeGetBoolean(xPrefs, "stock_system_region", true);
        config.folderChildDisplayRangeScale = safeGetFloat(xPrefs, "folder_child_display_range_scale", 1f);
        config.folderChildDirectionMode = normalizeFolderChildDirectionMode(
                safeGetInt(xPrefs, "folder_child_direction_mode", FOLDER_CHILD_DIRECTION_OUTWARD)
        );
        config.folderChildDirectionOffsetDeg = safeGetFloat(xPrefs, "folder_child_direction_offset_deg", 0f);
        config.folderChildOrderMode = safeGetInt(xPrefs, "folder_child_order_mode", 0);
        config.folderChildMinGapDp = safeGetInt(xPrefs, "folder_child_min_gap_dp", 8);
        config.folderChildSizeScale = safeGetFloat(xPrefs, "folder_child_size_scale", 1f);
        config.folderChildExpandAnimType = safeGetInt(xPrefs, "folder_child_expand_anim_type", FOLDER_CHILD_EXPAND_FAN_SPRING);
        config.folderChildExpandDurationMs = safeGetInt(xPrefs, "folder_child_expand_duration_ms", 180);
        config.folderChildExpandStaggerMs = safeGetInt(xPrefs, "folder_child_expand_stagger_ms", 15);
        config.folderChildCollapseAnimType = safeGetInt(xPrefs, "folder_child_collapse_anim_type", FOLDER_CHILD_COLLAPSE_CENTER);
        config.folderChildCollapseDurationMs = safeGetInt(xPrefs, "folder_child_collapse_duration_ms", 140);
        config.folderChildCollapseStaggerMs = safeGetInt(xPrefs, "folder_child_collapse_stagger_ms", 0);
        config.folderChildStartScale = safeGetFloat(xPrefs, "folder_child_start_scale", 0.1f);
        config.folderChildOvershoot = safeGetFloat(xPrefs, "folder_child_overshoot", 1.2f);
        config.maxScale = safeGetFloat(xPrefs, "max_scale", 1.60f);
        config.influenceRadius = safeGetInt(xPrefs, "influence_radius", 70);
        config.iconAlpha = safeGetFloat(xPrefs, "icon_alpha", 1.0f);
        config.fisheyeZoomAlpha = safeGetFloat(xPrefs, "fisheye_zoom_alpha", 1.0f);
        config.fisheyeOtherAlpha = safeGetFloat(xPrefs, "fisheye_other_alpha", 1.0f);
        config.iconCornerRadius = safeGetInt(xPrefs, "icon_corner_radius", 50);
        config.fullscreenBlurMaskAlpha = safeGetFloat(xPrefs, "fullscreen_blur_mask_alpha", 1.0f);
        config.fullscreenDimAlpha = safeGetFloat(xPrefs, "fullscreen_dim_alpha", 0.45f);
        config.headerDimAlpha = safeGetFloat(xPrefs, "header_dim_alpha", 0f);
        config.regionDimAlpha = safeGetFloat(xPrefs, "region_dim_alpha", 0f);
        config.bubbleDimAlpha = safeGetFloat(xPrefs, "bubble_dim_alpha", 0f);
        config.showDelayMs = safeGetInt(xPrefs, "show_delay_ms", 500);
        config.disableSystemEnterAnim = safeGetBoolean(xPrefs, "disable_system_enter_anim", false);
        config.faceUnlockQuickOpen = safeGetBoolean(xPrefs, "face_unlock_quick_open", true);
        config.bleUnlockQuickOpen = safeGetBoolean(xPrefs, "ble_unlock_quick_open", true);
        config.hapticEnabled = safeGetBoolean(xPrefs, "haptic_enabled", true);
        config.hapticStrength = safeGetInt(xPrefs, "haptic_strength", 140);
        config.hapticDurationMs = safeGetInt(xPrefs, "haptic_duration_ms", 24);
        config.hapticMode = safeGetInt(xPrefs, "haptic_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveExpand = safeGetInt(xPrefs, "haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
        config.hapticBrakePulse = safeGetBoolean(xPrefs, "haptic_brake_pulse", false);
        config.hapticDoubleGapMs = safeGetInt(xPrefs, "haptic_double_gap_ms", 28);
        config.hapticSlideEnabled = safeGetBoolean(xPrefs, "haptic_slide_enabled", true);
        config.hapticSlideStrength = safeGetInt(xPrefs, "haptic_slide_strength", 100);
        config.hapticSlideDurationMs = safeGetInt(xPrefs, "haptic_slide_duration_ms", 12);
        config.hapticSlideMode = safeGetInt(xPrefs, "haptic_slide_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveSlide = safeGetInt(xPrefs, "haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
        config.hapticSlideBrakePulse = safeGetBoolean(xPrefs, "haptic_slide_brake_pulse", false);
        config.hapticSlideDoubleGapMs = safeGetInt(xPrefs, "haptic_slide_double_gap_ms", 28);
        config.lastRealFodX = safeGetFloat(xPrefs, "last_real_fod_x", 0f);
        config.lastRealFodY = safeGetFloat(xPrefs, "last_real_fod_y", 0f);
        config.regionBlurRadius = safeGetInt(xPrefs, "region_blur_radius", 25);
        config.iconSpacingOffset = safeGetInt(xPrefs, "icon_spacing_offset", 5);
        config.bubblePaddingPercent = safeGetInt(xPrefs, "bubble_padding_percent", 0);
        config.showBubbleBg = xPrefs.contains("show_bubble_bg")
                ? safeGetBoolean(xPrefs, "show_bubble_bg", false)
                : (config.bubblePaddingPercent > 0);
        config.bubbleBlurRadius = safeGetInt(xPrefs, "bubble_blur_radius", 25);
        // 旧版 selected_icon_scale 折算迁移：峰值 maxScale × selectedIconScale 一次性合并进 maxScale
        migrateLegacySelectedScale(config, safeGetFloat(xPrefs, "selected_icon_scale", 1.0f));
        config.appIconCropMode = safeGetInt(xPrefs, "app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
        config.glyphPadEnabled = safeGetBoolean(xPrefs, "glyph_pad_enabled", true);
        config.glyphPadColor = safeGetInt(xPrefs, "glyph_pad_color", 0xFF2A82E4);
        config.glyphPadMode = safeGetInt(xPrefs, "glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
        config.iconGlowEffect = false;

        config.glassPreset = safeGetInt(xPrefs, "glass_preset", GLASS_PRESET_DEFAULT);
        config.glassRefractIor = safeGetFloat(xPrefs, "glass_refract_ior", 1.12f);
        config.glassThickness = safeGetFloat(xPrefs, "glass_thickness", 1.5f);
        config.glassSpecular = safeGetFloat(xPrefs, "glass_specular", 0.8f);
        config.glassMatteAlpha = safeGetFloat(xPrefs, "glass_matte_alpha", 0.12f);
        config.glassBorderLight = safeGetFloat(xPrefs, "glass_border_light", 0.45f);
        config.glassBackgroundSaturation = safeGetFloat(xPrefs, "glass_background_saturation", 1.5f);
        config.glassBackgroundBrightness = safeGetFloat(xPrefs, "glass_background_brightness", 0f);
        config.glassBackgroundBurn = safeGetFloat(xPrefs, "glass_background_burn", 0f);
        config.glassMatteWhiteMix = safeGetFloat(xPrefs, "glass_matte_white_mix", 0f);
        config.glassMatteColorMix = safeGetFloat(xPrefs, "glass_matte_color_mix", 0.4f);
        config.glassMatteColorPower = safeGetFloat(xPrefs, "glass_matte_color_power", 1.5f);
        config.glassEdgeRange = safeGetFloat(xPrefs, "glass_edge_range", 50f);
        config.glassEdgePower = safeGetFloat(xPrefs, "glass_edge_power", 4f);
        config.glassReflectOffset = safeGetFloat(xPrefs, "glass_reflect_offset", 600f);
        config.globalBlurRadius = safeGetInt(xPrefs, "global_blur_radius", 25);
        config.globalDimAlpha = safeGetFloat(xPrefs, "global_dim_alpha", 0f);
        config.bubbleMaterialFollowGlobal = safeGetBoolean(xPrefs, "bubble_material_follow_global", true);
        config.regionMaterialFollowGlobal = safeGetBoolean(xPrefs, "region_material_follow_global", true);
        config.headerMaterialFollowGlobal = safeGetBoolean(xPrefs, "header_material_follow_global", true);
        config.bubbleGlassMatteAlpha = safeGetFloat(xPrefs, "bubble_glass_matte_alpha", 0.12f);
        config.regionGlassMatteAlpha = safeGetFloat(xPrefs, "region_glass_matte_alpha", 0.12f);
        config.headerGlassMatteAlpha = safeGetFloat(xPrefs, "header_glass_matte_alpha", 0.12f);
        config.blurBaselineHealed = safeGetBoolean(xPrefs, "blur_baseline_healed", false);

        // 旧配置迁移
        if (!xPrefs.contains("global_blur_radius")) {
            migrateFromLegacy(config);
        }
        config.healBlurBaselineIfNeeded();
        adaptUnsupportedSoftGlass(config);

        String jsonStr = safeGetString(xPrefs, "shortcuts_json", null);
        config.shortcuts = parseShortcutsJson(jsonStr);
        return config;
    }

    /**
     * 模块配置 Activity 加载配置（默认包含快捷方式列表反序列化）
     */
    public static HyperOpenConfig loadFromApp(Context context) {
        return loadFromApp(context, true);
    }

    /**
     * 模块配置 Activity 加载配置
     *
     * @param loadShortcuts true 时解析 shortcuts_json；false 时跳过 JSON 反序列化（供仅更新面板参数的快速保存或预览镜像使用）
     */
    @SuppressWarnings("deprecation")
    public static HyperOpenConfig loadFromApp(Context context, boolean loadShortcuts) {
        // Direct Boot：配置统一存 DE 存储（旧版 CE 数据由 DePrefs 一次性迁移）
        SharedPreferences sp = io.github.qmryqmm.hyperopen.util.DePrefs.get(context, PREF_NAME);
        HyperOpenConfig config = new HyperOpenConfig();
        config.materialStyle = safeGetInt(sp, "material_style", MATERIAL_GAUSSIAN_BLUR);
        config.uiStyle = safeGetInt(sp, "ui_style", UI_STYLE_MATERIAL);
        config.themeMode = safeGetInt(sp, "theme_mode", THEME_FOLLOW_SYSTEM);
        config.monetColors = safeGetBoolean(sp, "monet_colors", false);
        config.barBlur = safeGetBoolean(sp, "bar_blur", true);
        config.floatingNavBar = safeGetBoolean(sp, "floating_nav_bar", false);
        config.predictiveBack = safeGetBoolean(sp, "predictive_back", true);
        config.edgeBack = safeGetBoolean(sp, "edge_back", true);
        config.pageSwitchGesture = safeGetInt(sp, "page_switch_gesture", PAGE_GESTURE_HORIZONTAL);
        config.offsetY = safeGetInt(sp, "offset_y", 100);
        config.regionDiameter = safeGetInt(sp, "region_diameter", 220);
        config.showRegionBg = safeGetBoolean(sp, "show_region_bg", false);
        config.showHeaderPillBg = safeGetBoolean(sp, "show_header_pill_bg", false);
        config.showHeaderPill = safeGetBoolean(sp, "show_header_pill", true);
        config.headerTopMargin = safeGetInt(sp, "header_top_margin", 100);
        config.headerTitleSize = safeGetInt(sp, "header_title_size", 17);
        config.headerSubtitleSize = safeGetInt(sp, "header_subtitle_size", 11);
        config.headerBlurRadius = safeGetInt(sp, "header_blur_radius", 25);
        config.headerTextCentered = safeGetBoolean(sp, "header_text_centered", true);
        config.iconSize = safeGetInt(sp, "icon_size", 60);
        config.maxIconCount = safeGetInt(sp, "max_icon_count", 0);
        config.layoutStyle = safeGetInt(sp, "layout_style", LAYOUT_HONEYCOMB);
        config.ringSectorMode = safeGetBoolean(sp, "ring_sector_mode", true);
        config.sectorLineColor = safeGetInt(sp, "sector_line_color", 0xFFBFBFBF);
        config.sectorLineAlpha = safeGetFloat(sp, "sector_line_alpha", 0.4f);
        config.sectorDashLength = safeGetInt(sp, "sector_dash_length", 6);
        config.sectorDashGap = safeGetInt(sp, "sector_dash_gap", 5);
        config.sectorLineWidth = safeGetFloat(sp, "sector_line_width", 1.2f);
        config.sectorHighlightEnabled = safeGetBoolean(sp, "sector_highlight_enabled", true);
        config.animDurationMs = safeGetInt(sp, "anim_duration_ms", 320);
        config.panelExpandAnimType = normalizePanelExpandAnimType(
                safeGetInt(sp, "panel_expand_anim_type", PANEL_EXPAND_BLOOM),
                config.layoutStyle,
                safeGetBoolean(sp, "stock_system_anim", true)
        );
        config.panelExpandStaggerMs = safeGetInt(sp, "panel_expand_stagger_ms", 12);
        config.dismissAnimEnabled = safeGetBoolean(sp, "dismiss_anim_enabled", true);
        config.panelCollapseAnimType = safeGetInt(sp, "panel_collapse_anim_type", PANEL_COLLAPSE_SELECTED_POP);
        config.panelCollapseDurationMs = safeGetInt(sp, "panel_collapse_duration_ms", 120);
        config.panelCollapseStaggerMs = safeGetInt(sp, "panel_collapse_stagger_ms", 0);
        config.panelSelectedPopScale = safeGetFloat(sp, "panel_selected_pop_scale", 1.30f);
        config.globalHintColorMode = safeGetInt(sp, "global_hint_color_mode", HINT_COLOR_MODE_AUTO);
        config.globalHintCustomColor = safeGetInt(sp, "global_hint_custom_color", 0xFFFFFFFF);
        config.defaultHintTextStroke = safeGetInt(sp, "default_hint_text_stroke", 0);
        config.defaultHintTextShadow = safeGetInt(sp, "default_hint_text_shadow", 0);
        config.defaultHintTextWeight = safeGetInt(sp, "default_hint_text_weight", 3);
        config.defaultHintTextLetterSpacing = safeGetFloat(sp, "default_hint_text_letter_spacing", 0f);
        config.defaultShowHintTitle = safeGetBoolean(sp, "default_show_hint_title", true);
        config.defaultShowHintSubtitle = safeGetBoolean(sp, "default_show_hint_subtitle", true);
        config.folderHoverDelayMs = safeGetInt(sp, "folder_hover_delay_ms", 250);
        config.stockSystemRegion = safeGetBoolean(sp, "stock_system_region", true);
        config.folderChildDisplayRangeScale = safeGetFloat(sp, "folder_child_display_range_scale", 1f);
        config.folderChildDirectionMode = normalizeFolderChildDirectionMode(
                safeGetInt(sp, "folder_child_direction_mode", FOLDER_CHILD_DIRECTION_OUTWARD)
        );
        config.folderChildDirectionOffsetDeg = safeGetFloat(sp, "folder_child_direction_offset_deg", 0f);
        config.folderChildOrderMode = safeGetInt(sp, "folder_child_order_mode", 0);
        config.folderChildMinGapDp = safeGetInt(sp, "folder_child_min_gap_dp", 8);
        config.folderChildSizeScale = safeGetFloat(sp, "folder_child_size_scale", 1f);
        config.folderChildExpandAnimType = safeGetInt(sp, "folder_child_expand_anim_type", FOLDER_CHILD_EXPAND_FAN_SPRING);
        config.folderChildExpandDurationMs = safeGetInt(sp, "folder_child_expand_duration_ms", 180);
        config.folderChildExpandStaggerMs = safeGetInt(sp, "folder_child_expand_stagger_ms", 15);
        config.folderChildCollapseAnimType = safeGetInt(sp, "folder_child_collapse_anim_type", FOLDER_CHILD_COLLAPSE_CENTER);
        config.folderChildCollapseDurationMs = safeGetInt(sp, "folder_child_collapse_duration_ms", 140);
        config.folderChildCollapseStaggerMs = safeGetInt(sp, "folder_child_collapse_stagger_ms", 0);
        config.folderChildStartScale = safeGetFloat(sp, "folder_child_start_scale", 0.1f);
        config.folderChildOvershoot = safeGetFloat(sp, "folder_child_overshoot", 1.2f);
        config.maxScale = safeGetFloat(sp, "max_scale", 1.60f);
        config.influenceRadius = safeGetInt(sp, "influence_radius", 70);
        config.iconAlpha = safeGetFloat(sp, "icon_alpha", 1.0f);
        config.fisheyeZoomAlpha = safeGetFloat(sp, "fisheye_zoom_alpha", 1.0f);
        config.fisheyeOtherAlpha = safeGetFloat(sp, "fisheye_other_alpha", 1.0f);
        config.iconCornerRadius = safeGetInt(sp, "icon_corner_radius", 50);
        config.fullscreenBlurMaskAlpha = safeGetFloat(sp, "fullscreen_blur_mask_alpha", 1.0f);
        config.fullscreenDimAlpha = safeGetFloat(sp, "fullscreen_dim_alpha", 0.45f);
        config.headerDimAlpha = safeGetFloat(sp, "header_dim_alpha", 0f);
        config.regionDimAlpha = safeGetFloat(sp, "region_dim_alpha", 0f);
        config.bubbleDimAlpha = safeGetFloat(sp, "bubble_dim_alpha", 0f);
        config.showDelayMs = safeGetInt(sp, "show_delay_ms", 500);
        config.disableSystemEnterAnim = safeGetBoolean(sp, "disable_system_enter_anim", false);
        config.faceUnlockQuickOpen = safeGetBoolean(sp, "face_unlock_quick_open", true);
        config.bleUnlockQuickOpen = safeGetBoolean(sp, "ble_unlock_quick_open", true);
        config.hapticEnabled = safeGetBoolean(sp, "haptic_enabled", true);
        config.hapticStrength = safeGetInt(sp, "haptic_strength", 140);
        config.hapticDurationMs = safeGetInt(sp, "haptic_duration_ms", 24);
        config.hapticMode = safeGetInt(sp, "haptic_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveExpand = safeGetInt(sp, "haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
        config.hapticBrakePulse = safeGetBoolean(sp, "haptic_brake_pulse", false);
        config.hapticDoubleGapMs = safeGetInt(sp, "haptic_double_gap_ms", 28);
        config.hapticSlideEnabled = safeGetBoolean(sp, "haptic_slide_enabled", true);
        config.hapticSlideStrength = safeGetInt(sp, "haptic_slide_strength", 100);
        config.hapticSlideDurationMs = safeGetInt(sp, "haptic_slide_duration_ms", 12);
        config.hapticSlideMode = safeGetInt(sp, "haptic_slide_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveSlide = safeGetInt(sp, "haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
        config.hapticSlideBrakePulse = safeGetBoolean(sp, "haptic_slide_brake_pulse", false);
        config.hapticSlideDoubleGapMs = safeGetInt(sp, "haptic_slide_double_gap_ms", 28);
        config.lastRealFodX = safeGetFloat(sp, "last_real_fod_x", 0f);
        config.lastRealFodY = safeGetFloat(sp, "last_real_fod_y", 0f);
        config.regionBlurRadius = safeGetInt(sp, "region_blur_radius", 25);
        config.iconSpacingOffset = safeGetInt(sp, "icon_spacing_offset", 5);
        config.bubblePaddingPercent = safeGetInt(sp, "bubble_padding_percent", 0);
        config.showBubbleBg = sp.contains("show_bubble_bg")
                ? safeGetBoolean(sp, "show_bubble_bg", false)
                : (config.bubblePaddingPercent > 0);
        config.bubbleBlurRadius = safeGetInt(sp, "bubble_blur_radius", 25);
        // 旧版 selected_icon_scale 折算迁移：峰值 maxScale × selectedIconScale 一次性合并进 maxScale
        migrateLegacySelectedScale(config, safeGetFloat(sp, "selected_icon_scale", 1.0f));
        config.appIconCropMode = safeGetInt(sp, "app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
        config.glyphPadEnabled = safeGetBoolean(sp, "glyph_pad_enabled", true);
        config.glyphPadColor = safeGetInt(sp, "glyph_pad_color", 0xFF2A82E4);
        config.glyphPadMode = safeGetInt(sp, "glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
        config.iconGlowEffect = false;

        config.glassPreset = safeGetInt(sp, "glass_preset", GLASS_PRESET_DEFAULT);
        config.glassRefractIor = safeGetFloat(sp, "glass_refract_ior", 1.12f);
        config.glassThickness = safeGetFloat(sp, "glass_thickness", 1.5f);
        config.glassSpecular = safeGetFloat(sp, "glass_specular", 0.8f);
        config.glassMatteAlpha = safeGetFloat(sp, "glass_matte_alpha", 0.12f);
        config.glassBorderLight = safeGetFloat(sp, "glass_border_light", 0.45f);
        config.glassBackgroundSaturation = safeGetFloat(sp, "glass_background_saturation", 1.5f);
        config.glassBackgroundBrightness = safeGetFloat(sp, "glass_background_brightness", 0f);
        config.glassBackgroundBurn = safeGetFloat(sp, "glass_background_burn", 0f);
        config.glassMatteWhiteMix = safeGetFloat(sp, "glass_matte_white_mix", 0f);
        config.glassMatteColorMix = safeGetFloat(sp, "glass_matte_color_mix", 0.4f);
        config.glassMatteColorPower = safeGetFloat(sp, "glass_matte_color_power", 1.5f);
        config.glassEdgeRange = safeGetFloat(sp, "glass_edge_range", 50f);
        config.glassEdgePower = safeGetFloat(sp, "glass_edge_power", 4f);
        config.glassReflectOffset = safeGetFloat(sp, "glass_reflect_offset", 600f);
        config.globalBlurRadius = safeGetInt(sp, "global_blur_radius", 25);
        config.globalDimAlpha = safeGetFloat(sp, "global_dim_alpha", 0f);
        config.bubbleMaterialFollowGlobal = safeGetBoolean(sp, "bubble_material_follow_global", true);
        config.regionMaterialFollowGlobal = safeGetBoolean(sp, "region_material_follow_global", true);
        config.headerMaterialFollowGlobal = safeGetBoolean(sp, "header_material_follow_global", true);
        config.bubbleGlassMatteAlpha = safeGetFloat(sp, "bubble_glass_matte_alpha", 0.12f);
        config.regionGlassMatteAlpha = safeGetFloat(sp, "region_glass_matte_alpha", 0.12f);
        config.headerGlassMatteAlpha = safeGetFloat(sp, "header_glass_matte_alpha", 0.12f);
        config.blurBaselineHealed = safeGetBoolean(sp, "blur_baseline_healed", false);

        // 旧配置迁移：global_blur_radius key 不存在 = v0.50 时代旧配置
        if (!sp.contains("global_blur_radius")) {
            migrateFromLegacy(config);
        }
        config.healBlurBaselineIfNeeded();
        adaptUnsupportedSoftGlass(config);

        if (loadShortcuts) {
            String jsonStr = safeGetString(sp, "shortcuts_json", null);
            config.shortcuts = parseShortcutsJson(jsonStr);
        }
        return config;
    }

    private static int safeGetInt(SharedPreferences sp, String key, int def) {
        try {
            return sp.getInt(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取整型配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    private static float safeGetFloat(SharedPreferences sp, String key, float def) {
        try {
            float v = sp.getFloat(key, def);
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                Log.w(TAG, "读取浮点配置出现非法值(NaN/Inf)，回退默认值: key=" + key);
                return def;
            }
            return v;
        } catch (ClassCastException e) {
            Log.w(TAG, "读取浮点配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    private static boolean safeGetBoolean(SharedPreferences sp, String key, boolean def) {
        try {
            return sp.getBoolean(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取布尔配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    @SuppressWarnings("unused")
    private static long safeGetLong(SharedPreferences sp, String key, long def) {
        try {
            return sp.getLong(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取长整型配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    private static String safeGetString(SharedPreferences sp, String key, String def) {
        try {
            return sp.getString(key, def);
        } catch (ClassCastException e) {
            Log.w(TAG, "读取字符串配置类型不匹配，回退默认值: key=" + key, e);
            return def;
        }
    }

    /**
     * 从备份 JSON 的 {@code values} 对象构建规范化面板配置，与 {@link #loadFromApp}
     * 使用完全一致的默认值与旧版迁移规则，供差异检测使用（不读取 App 界面设置及 FOD 实测坐标）。
     */
    public static HyperOpenConfig fromBackupValues(JSONObject values) {
        HyperOpenConfig config = new HyperOpenConfig();
        config.materialStyle = readInt(values, "material_style", MATERIAL_GAUSSIAN_BLUR);
        config.offsetY = readInt(values, "offset_y", 100);
        config.regionDiameter = readInt(values, "region_diameter", 220);
        config.showRegionBg = readBoolean(values, "show_region_bg", false);
        config.showHeaderPillBg = readBoolean(values, "show_header_pill_bg", false);
        config.showHeaderPill = readBoolean(values, "show_header_pill", true);
        config.headerTopMargin = readInt(values, "header_top_margin", 100);
        config.headerTitleSize = readInt(values, "header_title_size", 17);
        config.headerSubtitleSize = readInt(values, "header_subtitle_size", 11);
        config.headerBlurRadius = readInt(values, "header_blur_radius", 25);
        config.headerTextCentered = readBoolean(values, "header_text_centered", true);
        config.iconSize = readInt(values, "icon_size", 60);
        config.maxIconCount = readInt(values, "max_icon_count", 0);
        config.layoutStyle = readInt(values, "layout_style", LAYOUT_HONEYCOMB);
        config.ringSectorMode = readBoolean(values, "ring_sector_mode", true);
        config.sectorLineColor = readInt(values, "sector_line_color", 0xFFBFBFBF);
        config.sectorLineAlpha = readFloat(values, "sector_line_alpha", 0.4f);
        config.sectorDashLength = readInt(values, "sector_dash_length", 6);
        config.sectorDashGap = readInt(values, "sector_dash_gap", 5);
        config.sectorLineWidth = readFloat(values, "sector_line_width", 1.2f);
        config.sectorHighlightEnabled = readBoolean(values, "sector_highlight_enabled", true);
        config.animDurationMs = readInt(values, "anim_duration_ms", 320);
        config.panelExpandAnimType = normalizePanelExpandAnimType(
                readInt(values, "panel_expand_anim_type", PANEL_EXPAND_BLOOM),
                config.layoutStyle,
                readBoolean(values, "stock_system_anim", true)
        );
        config.panelExpandStaggerMs = readInt(values, "panel_expand_stagger_ms", 12);
        config.dismissAnimEnabled = readBoolean(values, "dismiss_anim_enabled", true);
        config.panelCollapseAnimType = readInt(values, "panel_collapse_anim_type", PANEL_COLLAPSE_SELECTED_POP);
        config.panelCollapseDurationMs = readInt(values, "panel_collapse_duration_ms", 120);
        config.panelCollapseStaggerMs = readInt(values, "panel_collapse_stagger_ms", 0);
        config.panelSelectedPopScale = readFloat(values, "panel_selected_pop_scale", 1.30f);
        config.globalHintColorMode = readInt(values, "global_hint_color_mode", HINT_COLOR_MODE_AUTO);
        config.globalHintCustomColor = readInt(values, "global_hint_custom_color", 0xFFFFFFFF);
        config.defaultHintTextStroke = readInt(values, "default_hint_text_stroke", 0);
        config.defaultHintTextShadow = readInt(values, "default_hint_text_shadow", 0);
        config.defaultHintTextWeight = readInt(values, "default_hint_text_weight", 3);
        config.defaultHintTextLetterSpacing = readFloat(values, "default_hint_text_letter_spacing", 0f);
        config.defaultShowHintTitle = readBoolean(values, "default_show_hint_title", true);
        config.defaultShowHintSubtitle = readBoolean(values, "default_show_hint_subtitle", true);
        config.folderHoverDelayMs = readInt(values, "folder_hover_delay_ms", 250);
        config.stockSystemRegion = readBoolean(values, "stock_system_region", true);
        config.folderChildDisplayRangeScale = readFloat(values, "folder_child_display_range_scale", 1f);
        config.folderChildDirectionMode = normalizeFolderChildDirectionMode(
                readInt(values, "folder_child_direction_mode", FOLDER_CHILD_DIRECTION_OUTWARD)
        );
        config.folderChildDirectionOffsetDeg = readFloat(values, "folder_child_direction_offset_deg", 0f);
        config.folderChildOrderMode = readInt(values, "folder_child_order_mode", 0);
        config.folderChildMinGapDp = readInt(values, "folder_child_min_gap_dp", 8);
        config.folderChildSizeScale = readFloat(values, "folder_child_size_scale", 1f);
        config.folderChildExpandAnimType = readInt(values, "folder_child_expand_anim_type", FOLDER_CHILD_EXPAND_FAN_SPRING);
        config.folderChildExpandDurationMs = readInt(values, "folder_child_expand_duration_ms", 180);
        config.folderChildExpandStaggerMs = readInt(values, "folder_child_expand_stagger_ms", 15);
        config.folderChildCollapseAnimType = readInt(values, "folder_child_collapse_anim_type", FOLDER_CHILD_COLLAPSE_CENTER);
        config.folderChildCollapseDurationMs = readInt(values, "folder_child_collapse_duration_ms", 140);
        config.folderChildCollapseStaggerMs = readInt(values, "folder_child_collapse_stagger_ms", 0);
        config.folderChildStartScale = readFloat(values, "folder_child_start_scale", 0.1f);
        config.folderChildOvershoot = readFloat(values, "folder_child_overshoot", 1.2f);
        config.maxScale = readFloat(values, "max_scale", 1.60f);
        config.influenceRadius = readInt(values, "influence_radius", 70);
        config.iconAlpha = readFloat(values, "icon_alpha", 1.0f);
        config.fisheyeZoomAlpha = readFloat(values, "fisheye_zoom_alpha", 1.0f);
        config.fisheyeOtherAlpha = readFloat(values, "fisheye_other_alpha", 1.0f);
        config.iconCornerRadius = readInt(values, "icon_corner_radius", 50);
        config.fullscreenBlurMaskAlpha = readFloat(values, "fullscreen_blur_mask_alpha", 1.0f);
        config.fullscreenDimAlpha = readFloat(values, "fullscreen_dim_alpha", 0.45f);
        config.headerDimAlpha = readFloat(values, "header_dim_alpha", 0f);
        config.regionDimAlpha = readFloat(values, "region_dim_alpha", 0f);
        config.bubbleDimAlpha = readFloat(values, "bubble_dim_alpha", 0f);
        config.showDelayMs = readInt(values, "show_delay_ms", 500);
        config.disableSystemEnterAnim = readBoolean(values, "disable_system_enter_anim", false);
        config.faceUnlockQuickOpen = readBoolean(values, "face_unlock_quick_open", true);
        config.bleUnlockQuickOpen = readBoolean(values, "ble_unlock_quick_open", true);
        config.hapticEnabled = readBoolean(values, "haptic_enabled", true);
        config.hapticStrength = readInt(values, "haptic_strength", 140);
        config.hapticDurationMs = readInt(values, "haptic_duration_ms", 24);
        config.hapticMode = readInt(values, "haptic_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveExpand = readInt(values, "haptic_stock_wave_expand", HAPTIC_STOCK_WAVE_LONG_PRESS);
        config.hapticBrakePulse = readBoolean(values, "haptic_brake_pulse", false);
        config.hapticDoubleGapMs = readInt(values, "haptic_double_gap_ms", 28);
        config.hapticSlideEnabled = readBoolean(values, "haptic_slide_enabled", true);
        config.hapticSlideStrength = readInt(values, "haptic_slide_strength", 100);
        config.hapticSlideDurationMs = readInt(values, "haptic_slide_duration_ms", 12);
        config.hapticSlideMode = readInt(values, "haptic_slide_mode", HAPTIC_MODE_STOCK);
        config.hapticStockWaveSlide = readInt(values, "haptic_stock_wave_slide", HAPTIC_STOCK_WAVE_MESH_NORMAL);
        config.hapticSlideBrakePulse = readBoolean(values, "haptic_slide_brake_pulse", false);
        config.hapticSlideDoubleGapMs = readInt(values, "haptic_slide_double_gap_ms", 28);
        config.regionBlurRadius = readInt(values, "region_blur_radius", 25);
        config.iconSpacingOffset = readInt(values, "icon_spacing_offset", 5);
        config.bubblePaddingPercent = readInt(values, "bubble_padding_percent", 0);
        config.showBubbleBg = (values != null && values.has("show_bubble_bg"))
                ? readBoolean(values, "show_bubble_bg", false)
                : (config.bubblePaddingPercent > 0);
        config.bubbleBlurRadius = readInt(values, "bubble_blur_radius", 25);
        migrateLegacySelectedScale(config, readFloat(values, "selected_icon_scale", 1.0f));
        config.appIconCropMode = readInt(values, "app_icon_crop_mode", CROP_ADAPTIVE_EXPAND);
        config.glyphPadEnabled = readBoolean(values, "glyph_pad_enabled", true);
        config.glyphPadColor = readInt(values, "glyph_pad_color", 0xFF2A82E4);
        config.glyphPadMode = readInt(values, "glyph_pad_mode", GLYPH_PAD_MODE_TRANSLUCENT);
        config.iconGlowEffect = false;

        config.glassPreset = readInt(values, "glass_preset", GLASS_PRESET_DEFAULT);
        config.glassRefractIor = readFloat(values, "glass_refract_ior", 1.12f);
        config.glassThickness = readFloat(values, "glass_thickness", 1.5f);
        config.glassSpecular = readFloat(values, "glass_specular", 0.8f);
        config.glassMatteAlpha = readFloat(values, "glass_matte_alpha", 0.12f);
        config.glassBorderLight = readFloat(values, "glass_border_light", 0.45f);
        config.glassBackgroundSaturation = readFloat(values, "glass_background_saturation", 1.5f);
        config.glassBackgroundBrightness = readFloat(values, "glass_background_brightness", 0f);
        config.glassBackgroundBurn = readFloat(values, "glass_background_burn", 0f);
        config.glassMatteWhiteMix = readFloat(values, "glass_matte_white_mix", 0f);
        config.glassMatteColorMix = readFloat(values, "glass_matte_color_mix", 0.4f);
        config.glassMatteColorPower = readFloat(values, "glass_matte_color_power", 1.5f);
        config.glassEdgeRange = readFloat(values, "glass_edge_range", 50f);
        config.glassEdgePower = readFloat(values, "glass_edge_power", 4f);
        config.glassReflectOffset = readFloat(values, "glass_reflect_offset", 600f);
        config.globalBlurRadius = readInt(values, "global_blur_radius", 25);
        config.globalDimAlpha = readFloat(values, "global_dim_alpha", 0f);
        config.bubbleMaterialFollowGlobal = readBoolean(values, "bubble_material_follow_global", true);
        config.regionMaterialFollowGlobal = readBoolean(values, "region_material_follow_global", true);
        config.headerMaterialFollowGlobal = readBoolean(values, "header_material_follow_global", true);
        config.bubbleGlassMatteAlpha = readFloat(values, "bubble_glass_matte_alpha", 0.12f);
        config.regionGlassMatteAlpha = readFloat(values, "region_glass_matte_alpha", 0.12f);
        config.headerGlassMatteAlpha = readFloat(values, "header_glass_matte_alpha", 0.12f);
        config.blurBaselineHealed = readBoolean(values, "blur_baseline_healed", false);

        if (values == null || !values.has("global_blur_radius")) {
            migrateFromLegacy(config);
        }
        config.healBlurBaselineIfNeeded();
        adaptUnsupportedSoftGlass(config);
        return config;
    }

    /** 逐项比对面板参数是否完全一致（忽略 App 界面设置、运行时状态及快捷方式列表）。 */
    public boolean panelParamsEquals(HyperOpenConfig other) {
        if (this == other) return true;
        if (other == null) return false;
        return materialStyle == other.materialStyle
                && offsetY == other.offsetY
                && regionDiameter == other.regionDiameter
                && showRegionBg == other.showRegionBg
                && showHeaderPillBg == other.showHeaderPillBg
                && showHeaderPill == other.showHeaderPill
                && headerTopMargin == other.headerTopMargin
                && headerTitleSize == other.headerTitleSize
                && headerSubtitleSize == other.headerSubtitleSize
                && headerBlurRadius == other.headerBlurRadius
                && headerTextCentered == other.headerTextCentered
                && iconSize == other.iconSize
                && maxIconCount == other.maxIconCount
                && layoutStyle == other.layoutStyle
                && ringSectorMode == other.ringSectorMode
                && sectorLineColor == other.sectorLineColor
                && floatEq(sectorLineAlpha, other.sectorLineAlpha)
                && sectorDashLength == other.sectorDashLength
                && sectorDashGap == other.sectorDashGap
                && floatEq(sectorLineWidth, other.sectorLineWidth)
                && sectorHighlightEnabled == other.sectorHighlightEnabled
                && animDurationMs == other.animDurationMs
                && panelExpandAnimType == other.panelExpandAnimType
                && panelExpandStaggerMs == other.panelExpandStaggerMs
                && dismissAnimEnabled == other.dismissAnimEnabled
                && panelCollapseAnimType == other.panelCollapseAnimType
                && panelCollapseDurationMs == other.panelCollapseDurationMs
                && panelCollapseStaggerMs == other.panelCollapseStaggerMs
                && floatEq(panelSelectedPopScale, other.panelSelectedPopScale)
                && globalHintColorMode == other.globalHintColorMode
                && globalHintCustomColor == other.globalHintCustomColor
                && defaultHintTextStroke == other.defaultHintTextStroke
                && defaultHintTextShadow == other.defaultHintTextShadow
                && defaultHintTextWeight == other.defaultHintTextWeight
                && floatEq(defaultHintTextLetterSpacing, other.defaultHintTextLetterSpacing)
                && defaultShowHintTitle == other.defaultShowHintTitle
                && defaultShowHintSubtitle == other.defaultShowHintSubtitle
                && folderHoverDelayMs == other.folderHoverDelayMs
                && stockSystemRegion == other.stockSystemRegion
                && floatEq(folderChildDisplayRangeScale, other.folderChildDisplayRangeScale)
                && folderChildDirectionMode == other.folderChildDirectionMode
                && floatEq(folderChildDirectionOffsetDeg, other.folderChildDirectionOffsetDeg)
                && folderChildOrderMode == other.folderChildOrderMode
                && folderChildMinGapDp == other.folderChildMinGapDp
                && floatEq(folderChildSizeScale, other.folderChildSizeScale)
                && folderChildExpandAnimType == other.folderChildExpandAnimType
                && folderChildExpandDurationMs == other.folderChildExpandDurationMs
                && folderChildExpandStaggerMs == other.folderChildExpandStaggerMs
                && folderChildCollapseAnimType == other.folderChildCollapseAnimType
                && folderChildCollapseDurationMs == other.folderChildCollapseDurationMs
                && folderChildCollapseStaggerMs == other.folderChildCollapseStaggerMs
                && floatEq(folderChildStartScale, other.folderChildStartScale)
                && floatEq(folderChildOvershoot, other.folderChildOvershoot)
                && floatEq(maxScale, other.maxScale)
                && influenceRadius == other.influenceRadius
                && floatEq(iconAlpha, other.iconAlpha)
                && floatEq(fisheyeZoomAlpha, other.fisheyeZoomAlpha)
                && floatEq(fisheyeOtherAlpha, other.fisheyeOtherAlpha)
                && iconCornerRadius == other.iconCornerRadius
                && floatEq(fullscreenBlurMaskAlpha, other.fullscreenBlurMaskAlpha)
                && floatEq(fullscreenDimAlpha, other.fullscreenDimAlpha)
                && floatEq(headerDimAlpha, other.headerDimAlpha)
                && floatEq(regionDimAlpha, other.regionDimAlpha)
                && floatEq(bubbleDimAlpha, other.bubbleDimAlpha)
                && showDelayMs == other.showDelayMs
                && disableSystemEnterAnim == other.disableSystemEnterAnim
                && faceUnlockQuickOpen == other.faceUnlockQuickOpen
                && bleUnlockQuickOpen == other.bleUnlockQuickOpen
                && hapticEnabled == other.hapticEnabled
                && hapticStrength == other.hapticStrength
                && hapticDurationMs == other.hapticDurationMs
                && hapticMode == other.hapticMode
                && hapticStockWaveExpand == other.hapticStockWaveExpand
                && hapticBrakePulse == other.hapticBrakePulse
                && hapticDoubleGapMs == other.hapticDoubleGapMs
                && hapticSlideEnabled == other.hapticSlideEnabled
                && hapticSlideStrength == other.hapticSlideStrength
                && hapticSlideDurationMs == other.hapticSlideDurationMs
                && hapticSlideMode == other.hapticSlideMode
                && hapticStockWaveSlide == other.hapticStockWaveSlide
                && hapticSlideBrakePulse == other.hapticSlideBrakePulse
                && hapticSlideDoubleGapMs == other.hapticSlideDoubleGapMs
                && regionBlurRadius == other.regionBlurRadius
                && iconSpacingOffset == other.iconSpacingOffset
                && showBubbleBg == other.showBubbleBg
                && bubblePaddingPercent == other.bubblePaddingPercent
                && bubbleBlurRadius == other.bubbleBlurRadius
                && appIconCropMode == other.appIconCropMode
                && glyphPadEnabled == other.glyphPadEnabled
                && glyphPadColor == other.glyphPadColor
                && glyphPadMode == other.glyphPadMode
                && glassPreset == other.glassPreset
                && floatEq(glassRefractIor, other.glassRefractIor)
                && floatEq(glassThickness, other.glassThickness)
                && floatEq(glassSpecular, other.glassSpecular)
                && floatEq(glassMatteAlpha, other.glassMatteAlpha)
                && floatEq(glassBorderLight, other.glassBorderLight)
                && floatEq(glassBackgroundSaturation, other.glassBackgroundSaturation)
                && floatEq(glassBackgroundBrightness, other.glassBackgroundBrightness)
                && floatEq(glassBackgroundBurn, other.glassBackgroundBurn)
                && floatEq(glassMatteWhiteMix, other.glassMatteWhiteMix)
                && floatEq(glassMatteColorMix, other.glassMatteColorMix)
                && floatEq(glassMatteColorPower, other.glassMatteColorPower)
                && floatEq(glassEdgeRange, other.glassEdgeRange)
                && floatEq(glassEdgePower, other.glassEdgePower)
                && floatEq(glassReflectOffset, other.glassReflectOffset)
                && globalBlurRadius == other.globalBlurRadius
                && floatEq(globalDimAlpha, other.globalDimAlpha)
                && bubbleMaterialFollowGlobal == other.bubbleMaterialFollowGlobal
                && regionMaterialFollowGlobal == other.regionMaterialFollowGlobal
                && headerMaterialFollowGlobal == other.headerMaterialFollowGlobal
                && floatEq(bubbleGlassMatteAlpha, other.bubbleGlassMatteAlpha)
                && floatEq(regionGlassMatteAlpha, other.regionGlassMatteAlpha)
                && floatEq(headerGlassMatteAlpha, other.headerGlassMatteAlpha);
    }

    private static boolean floatEq(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static int readInt(JSONObject values, String key, int def) {
        if (values == null) return def;
        JSONObject item = values.optJSONObject(key);
        if (item == null || item.isNull("v")) return def;
        return item.optInt("v", def);
    }

    private static float readFloat(JSONObject values, String key, float def) {
        if (values == null) return def;
        JSONObject item = values.optJSONObject(key);
        if (item == null || item.isNull("v")) return def;
        double d = item.optDouble("v", def);
        return Double.isNaN(d) || Double.isInfinite(d) ? def : (float) d;
    }

    private static boolean readBoolean(JSONObject values, String key, boolean def) {
        if (values == null) return def;
        JSONObject item = values.optJSONObject(key);
        if (item == null || item.isNull("v")) return def;
        return item.optBoolean("v", def);
    }

    /**
     * v0.50 旧配置迁移：global_blur_radius 等字段不存在时自动适配。
     * <p>
     * 旧配置中 bubbleBlurRadius/regionBlurRadius/headerBlurRadius 是独立生效的，
     * 新增的 followGlobal=true + globalBlurRadius 会覆盖它们。
     * 迁移策略：followGlobal=false 保留旧值；同时反推 *GlassMatteAlpha 使
     * Plan B 映射 matteToBlur(matteAlpha) 还原旧模糊半径。
     */
    private static void migrateFromLegacy(HyperOpenConfig config) {
        config.bubbleMaterialFollowGlobal = false;
        config.regionMaterialFollowGlobal = false;
        config.headerMaterialFollowGlobal = false;
        config.globalBlurRadius = config.bubbleBlurRadius;
        config.globalDimAlpha = config.bubbleDimAlpha;
        // Plan B 反推：matteToBlur(m) = m / 0.40 * 50  =>  m = blurRadius / 50 * 0.40
        config.bubbleGlassMatteAlpha = clampMatte(config.bubbleBlurRadius / 50f * 0.40f);
        config.regionGlassMatteAlpha = clampMatte(config.regionBlurRadius / 50f * 0.40f);
        config.headerGlassMatteAlpha = clampMatte(config.headerBlurRadius / 50f * 0.40f);
    }

    private static float clampMatte(float v) {
        return Math.max(0.05f, Math.min(0.40f, v));
    }

    /**
     * 旧版参数迁移：命中图标最终倍率从 maxScale × selectedIconScale 改为 maxScale 单值，
     * 老用户的旧 selected_icon_scale ≠ 1.0 时，把乘积一次性折算进 maxScale，保证命中峰值不缩水
     * （如 1.40 × 1.15 ≈ 1.61x）。新装用户旧键缺失时传入 1.0f，等价于不迁移。
     */
    private static void migrateLegacySelectedScale(HyperOpenConfig config, float legacySelectedScale) {
        if (legacySelectedScale > 1.001f) {
            float merged = config.maxScale * legacySelectedScale;
            // 上限对齐设置页滑块最大值 2.00x，避免极端旧配置折算出离谱倍率
            config.maxScale = Math.min(2.0f, Math.max(1.0f, merged));
        }
    }

    /**
     * 保存配置 (支持全局只读分享给 SystemUI + 实时广播通知，默认同步写入 shortcuts_json)
     */
    public void save(Context context) {
        save(context, true);
    }

    /**
     * 保存配置 (支持全局只读分享给 SystemUI + 实时广播通知)
     *
     * @param writeShortcuts true 时将 {@link #shortcuts} 序列化写入 {@code shortcuts_json}；
     *                       false 时保留 SharedPreferences 中已有的 {@code shortcuts_json}，避免纯参数滑块调整时重复序列化全部快捷项
     */
    @SuppressWarnings("deprecation")
    public void save(Context context, boolean writeShortcuts) {
        if (context == null) {
            return;
        }
        // Direct Boot：配置统一存 DE 存储
        final Context appCtx = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        final SharedPreferences sp = io.github.qmryqmm.hyperopen.util.DePrefs.get(appCtx, PREF_NAME);
        SharedPreferences.Editor editor = sp.edit()
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
            .putBoolean("stock_system_region", stockSystemRegion)
            .putBoolean("ring_sector_mode", ringSectorMode)
            .putInt("sector_line_color", sectorLineColor)
            .putFloat("sector_line_alpha", sectorLineAlpha)
            .putInt("sector_dash_length", sectorDashLength)
            .putInt("sector_dash_gap", sectorDashGap)
            .putFloat("sector_line_width", sectorLineWidth)
            .putBoolean("sector_highlight_enabled", sectorHighlightEnabled)
            .putInt("anim_duration_ms", animDurationMs)
            .putInt("panel_expand_anim_type", panelExpandAnimType)
            .putInt("panel_expand_stagger_ms", panelExpandStaggerMs)
            .putBoolean("dismiss_anim_enabled", dismissAnimEnabled)
            .putInt("panel_collapse_anim_type", panelCollapseAnimType)
            .putInt("panel_collapse_duration_ms", panelCollapseDurationMs)
            .putInt("panel_collapse_stagger_ms", panelCollapseStaggerMs)
            .putFloat("panel_selected_pop_scale", panelSelectedPopScale)
            .putInt("global_hint_color_mode", globalHintColorMode)
            .putInt("global_hint_custom_color", globalHintCustomColor)
            .putInt("default_hint_text_stroke", defaultHintTextStroke)
            .putInt("default_hint_text_shadow", defaultHintTextShadow)
            .putInt("default_hint_text_weight", defaultHintTextWeight)
            .putFloat("default_hint_text_letter_spacing", defaultHintTextLetterSpacing)
            .putBoolean("default_show_hint_title", defaultShowHintTitle)
            .putBoolean("default_show_hint_subtitle", defaultShowHintSubtitle)
            .putInt("folder_hover_delay_ms", folderHoverDelayMs)
            .putFloat("folder_child_display_range_scale", folderChildDisplayRangeScale)
            .putInt("folder_child_direction_mode", folderChildDirectionMode)
            .putFloat("folder_child_direction_offset_deg", folderChildDirectionOffsetDeg)
            .putInt("folder_child_order_mode", folderChildOrderMode)
            .putInt("folder_child_min_gap_dp", folderChildMinGapDp)
            .putFloat("folder_child_size_scale", folderChildSizeScale)
            .putInt("folder_child_expand_anim_type", folderChildExpandAnimType)
            .putInt("folder_child_expand_duration_ms", folderChildExpandDurationMs)
            .putInt("folder_child_expand_stagger_ms", folderChildExpandStaggerMs)
            .putInt("folder_child_collapse_anim_type", folderChildCollapseAnimType)
            .putInt("folder_child_collapse_duration_ms", folderChildCollapseDurationMs)
            .putInt("folder_child_collapse_stagger_ms", folderChildCollapseStaggerMs)
            .putFloat("folder_child_start_scale", folderChildStartScale)
            .putFloat("folder_child_overshoot", folderChildOvershoot)
            .putFloat("max_scale", maxScale)
            .putInt("influence_radius", influenceRadius)
            .putFloat("icon_alpha", iconAlpha)
            .putFloat("fisheye_zoom_alpha", fisheyeZoomAlpha)
            .putFloat("fisheye_other_alpha", fisheyeOtherAlpha)
            .putInt("icon_corner_radius", iconCornerRadius)
            .putFloat("fullscreen_blur_mask_alpha", fullscreenBlurMaskAlpha)
            .putFloat("fullscreen_dim_alpha", fullscreenDimAlpha)
            .putFloat("header_dim_alpha", headerDimAlpha)
            .putFloat("region_dim_alpha", regionDimAlpha)
            .putFloat("bubble_dim_alpha", bubbleDimAlpha)
            .putInt("show_delay_ms", showDelayMs)
            .putBoolean("disable_system_enter_anim", disableSystemEnterAnim)
            .putBoolean("face_unlock_quick_open", faceUnlockQuickOpen)
            .putBoolean("ble_unlock_quick_open", bleUnlockQuickOpen)
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
            .putBoolean("show_bubble_bg", showBubbleBg)
            .putInt("bubble_padding_percent", bubblePaddingPercent)
            .putInt("bubble_blur_radius", bubbleBlurRadius)
            // 旧版选中强调倍率已废弃：命中图标最终倍率统一为 max_scale 单值，写入时清掉遗留键
            .remove("selected_icon_scale")
            .remove("ring_count")
            .remove("stock_system_anim")
            .remove("folder_child_layout_mode")
            .remove("folder_child_spread_scale")
            .remove("folder_child_arc_span_deg")
            .putInt("app_icon_crop_mode", appIconCropMode)
            .putBoolean("glyph_pad_enabled", glyphPadEnabled)
            .putInt("glyph_pad_color", glyphPadColor)
            .putInt("glyph_pad_mode", glyphPadMode)
            .putBoolean("icon_glow_effect", false)
            .putInt("glass_preset", glassPreset)
            .putFloat("glass_refract_ior", glassRefractIor)
            .putFloat("glass_thickness", glassThickness)
            .putFloat("glass_specular", glassSpecular)
            .putFloat("glass_matte_alpha", glassMatteAlpha)
            .putFloat("glass_border_light", glassBorderLight)
            .putFloat("glass_background_saturation", glassBackgroundSaturation)
            .putFloat("glass_background_brightness", glassBackgroundBrightness)
            .putFloat("glass_background_burn", glassBackgroundBurn)
            .putFloat("glass_matte_white_mix", glassMatteWhiteMix)
            .putFloat("glass_matte_color_mix", glassMatteColorMix)
            .putFloat("glass_matte_color_power", glassMatteColorPower)
            .putFloat("glass_edge_range", glassEdgeRange)
            .putFloat("glass_edge_power", glassEdgePower)
            .putFloat("glass_reflect_offset", glassReflectOffset)
            .putInt("global_blur_radius", globalBlurRadius)
            .putBoolean("blur_baseline_healed", blurBaselineHealed)
            .putFloat("global_dim_alpha", globalDimAlpha)
            .putBoolean("bubble_material_follow_global", bubbleMaterialFollowGlobal)
            .putBoolean("region_material_follow_global", regionMaterialFollowGlobal)
            .putBoolean("header_material_follow_global", headerMaterialFollowGlobal)
            .putFloat("bubble_glass_matte_alpha", bubbleGlassMatteAlpha)
            .putFloat("region_glass_matte_alpha", regionGlassMatteAlpha)
            .putFloat("header_glass_matte_alpha", headerGlassMatteAlpha);
        if (writeShortcuts) {
            editor.putString("shortcuts_json", serializeShortcutsJson(shortcuts));
        } else if (!sp.contains("shortcuts_json")) {
            List<ShortcutItem> fallback = (shortcuts != null && !shortcuts.isEmpty())
                    ? shortcuts : getDefaultShortcuts();
            editor.putString("shortcuts_json", serializeShortcutsJson(fallback));
        }
        editor.apply();

        // 在后台单线程串行执行器上等待 SharedPreferencesImpl 完成写盘（经写锁屏障），
        // 再恢复 DE 只读权限并通知 SystemUI，避免 apply() 异步落盘把文件权重置回 0660。
        SAVE_IO_EXECUTOR.execute(() -> {
            try {
                sp.edit().commit();
                io.github.qmryqmm.hyperopen.util.DePrefs.applyWorldReadable(appCtx, PREF_NAME);
                android.content.Intent intent = new android.content.Intent(ACTION_CONFIG_CHANGED);
                intent.setPackage("com.android.systemui");
                appCtx.sendBroadcast(intent);
            } catch (Throwable t) {
                Log.w(TAG, "异步落盘或广播配置失败", t);
            }
        });
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
            0xFFFFFFFF, true,
            true, 0xFF07C160, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        );
        // userId 默认 0 = 主空间；要跨用户（如 999 双开）在编辑界面「启动用户」里选择，保存时会双写 URI 标记
        items.add(wxScan);

        // 2. 微信付款码 (智能助理官方付款码入口，配 Remix 银行卡矢量图标 + 微信绿微透底垫)
        items.add(new ShortcutItem(
            "wx_pay", "微信付款码", "出示收银台向商家付款",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;launchFlags=0x10000000;component=com.tencent.mm/com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI;i.key_entry_scene=2;end",
            "", "remix:qr-code-line",
            0xFFFFFFFF, true,
            true, 0xFF07C160, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 3. 微信收款码 (智能助理官方面对面二维码收款入口，配 Remix 付款码矢量图标 + 微信绿微透底垫)
        items.add(new ShortcutItem(
            "wx_receive", "微信收款码", "面对面二维码收款",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.tencent.mm;component=com.tencent.mm/.plugin.collect.ui.CollectMainUI;end",
            "", "remix:money-cny-circle-line",
            0xFFFFFFFF, true,
            true, 0xFF07C160, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 4. 支付宝扫一扫 (官方扫码协议，配 Remix 扫一扫图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
                "alipay_scan", "支付宝扫一扫", "扫码识别或骑行单车",
                ShortcutItem.ACTION_INTENT_URI, "intent://platformapi/startapp?appId=10000007#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end",
                "", "remix:qr-scan-2-line",
                0xFFFFFFFF, true,
                true, 0xFF1677FF, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 5. 支付宝付款码 (智能助理官方 Scheme，配 Remix 银行卡矢量图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
            "alipay_pay", "支付宝付款码", "出示收银台付款",
            ShortcutItem.ACTION_INTENT_URI, "alipays://platformapi/startapp?appId=20000056&chInfo=ch_xiaomiLeft",
            "", "remix:qr-code-fill",
            0xFFFFFFFF, true,
            true, 0xFF1677FF, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 6. 支付宝收款码 (智能助理官方收钱码协议，配 Remix 人民币矢量图标 + 支付宝蓝微透底垫)
        items.add(new ShortcutItem(
            "alipay_receive", "支付宝收款码", "面对面二维码收钱",
            ShortcutItem.ACTION_INTENT_URI, "alipays://platformapi/startapp?appId=20000123&chInfo=ch_xiaomi_left&sceneCode=KF_CHANGSHANG&shareUserId=2088831085791813&partnerId=ch_xiaomi_left&pikshemo=YES",
            "", "remix:money-cny-circle-fill",
            0xFFFFFFFF, true,
            true, 0xFF1677FF, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 7. 乘车码 (交通乘车码统一入口，配 Remix 地铁矢量图标 + 银联绿底垫)
        items.add(new ShortcutItem(
                "bus_code", "乘车码", "地铁公交一卡通刷码",
                ShortcutItem.ACTION_INTENT_URI, "intent://platformapi/startapp?appId=200011235#Intent;scheme=alipays;package=com.eg.android.AlipayGphone;end",
                "", "remix:subway-fill",
                0xFFFFFFFF, true,
                true, 0xFF52C41A, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 8. 手电筒 (系统底层 Camera Torch，配 Remix 手电筒矢量图标 + 暖阳橙微透底垫)
        items.add(new ShortcutItem(
            "flashlight", "手电筒", "开关后置 LED 强光",
            ShortcutItem.ACTION_FLASHLIGHT, "",
            "", "remix:flashlight-fill",
            0xFFFFFFFF, true,
            true, 0xFFFAAD14, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        // 9. 街拍相机 (系统原生相机标准 Action，配 Remix 相机矢量图标 + 活力红底垫)
        items.add(new ShortcutItem(
            "camera", "街拍相机", "秒级开启抓拍",
            ShortcutItem.ACTION_INTENT_URI, "intent:#Intent;action=android.media.action.STILL_IMAGE_CAMERA;end",
            "", "remix:camera-fill",
            0xFFFFFFFF, true,
            true, 0xFFFF4D4F, ShortcutItem.DEFAULT_GLYPH_PAD_ALPHA
        ));

        return items;
    }
}
