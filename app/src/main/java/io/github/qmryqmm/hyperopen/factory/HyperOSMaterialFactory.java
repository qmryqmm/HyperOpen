package io.github.qmryqmm.hyperopen.factory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.util.BubbleBlurSpec;

/**
 * HyperOS 材质工程：柔光玻璃（Soft Light Glass）与高斯模糊（Gaussian Blur）次世代实现。
 * 针对澎湃OS 2.0 / HyperOS 物理光学渲染管线深度优化：
 * 1. 深度对齐系统桌面与系统界面原生 Soft Glass Token 规范；
 * 2. 哑光磨砂微晶扩散与细柔双极反差边框增强；
 * 3. 硬件着色器 GPU 级实时光栅化，0 额外 CPU 渲染损耗。
 */
public class HyperOSMaterialFactory {
    public static final int STYLE_SOFT_GLASS = 1;
    public static final int STYLE_GAUSSIAN_BLUR = 2;

    /** 统一光学描边宽度：气泡、圆形视界底衬、顶部提示胶囊共用，禁止各处私自取值 */
    public static final float OPTICS_BORDER_STROKE_WIDTH = 1.5f;

    /**
     * 磨砂浓度统一值域：与各设置页滑块（5%~40%）及 blur tint 采样范围一致。
     * 所有材质绘制与 tint 合成都必须经此钳制，保证同一参数在三种载体上响应完全一致。
     */
    public static float clampMatte(float matteAlpha) {
        return Math.max(0.04f, Math.min(0.40f, matteAlpha));
    }

    /**
     * 柔光玻璃系统级可微调光学参数结构体
     */
    public static class GlassParams {
        public float ior = 1.0f;
        public float thickness = 1.5f;
        public float specular = 0.6f;
        public float matteAlpha = 0.15f;
        public float borderLight = 0.35f;

        public GlassParams() {}

        public GlassParams(float ior, float thickness, float specular, float matteAlpha, float borderLight) {
            this.ior = ior;
            this.thickness = thickness;
            this.specular = specular;
            this.matteAlpha = matteAlpha;
            this.borderLight = borderLight;
        }

        public static GlassParams fromConfig(@Nullable HyperOpenConfig config) {
            GlassParams p = new GlassParams();
            if (config == null) return p;
            if (config.glassPreset == HyperOpenConfig.GLASS_PRESET_DEFAULT) {
                p.ior = 1.12f;
                p.thickness = 1.5f;
                p.specular = 0.8f;
                p.matteAlpha = 0.12f;
                p.borderLight = 0.45f;
            } else {
                p.ior = config.glassRefractIor;
                p.thickness = config.glassThickness;
                p.specular = config.glassSpecular;
                p.matteAlpha = config.glassMatteAlpha;
                p.borderLight = config.glassBorderLight;
            }
            return p;
        }

        public boolean equalsParams(@Nullable GlassParams other) {
            if (other == null) return false;
            return Math.abs(ior - other.ior) < 0.001f
                && Math.abs(thickness - other.thickness) < 0.001f
                && Math.abs(specular - other.specular) < 0.001f
                && Math.abs(matteAlpha - other.matteAlpha) < 0.001f
                && Math.abs(borderLight - other.borderLight) < 0.001f;
        }

        /** 生成仅磨砂浓度不同的副本（组件级「柔光磨砂浓度」独立参数用），其余光学参数保持一致 */
        public GlassParams withMatteAlpha(float matteAlpha) {
            GlassParams p = new GlassParams(ior, thickness, specular, matteAlpha, borderLight);
            return p;
        }
    }

    /**
     * 全局统一的实时背景毛玻璃底色混合（与圆形视界区域底衬完全对齐）：
     * - 柔光玻璃使用晶莹通透微光混合 (0x16FFFFFF)，最大化保留底层实时动态色彩与柔和对比度
     * - 高斯模糊使用沉稳哑光微透混合 (0x12FFFFFF)，绝不死黑
     */
    public static final int BLUR_TINT_SOFT_GLASS = 0x16FFFFFF;
    public static final int BLUR_TINT_GAUSSIAN_BLUR = 0x12FFFFFF;

    public static int getUnifiedBlurTint(int style) {
        return (style == STYLE_SOFT_GLASS) ? BLUR_TINT_SOFT_GLASS : BLUR_TINT_GAUSSIAN_BLUR;
    }

    public static int getUnifiedBlurTint(int style, @Nullable GlassParams params) {
        if (style != STYLE_SOFT_GLASS || params == null) {
            return getUnifiedBlurTint(style);
        }
        int alpha = Math.round(clampMatte(params.matteAlpha) * 255f);
        return (alpha << 24) | 0x00FFFFFF;
    }

    /**
     * 判断当前应使用的材质：柔光玻璃与高斯模糊二选一，默认柔光玻璃
     */
    public static int resolveActiveStyle(Context context, int configStyle) {
        if (configStyle == STYLE_GAUSSIAN_BLUR) {
            return STYLE_GAUSSIAN_BLUR;
        }
        return STYLE_SOFT_GLASS;
    }

    /** 带玻璃光学参数的气泡材质（与图标气泡 / 圆形底衬严格同一套 GlassParams） */
    public static CircularBubbleDrawable createCircularBubbleDrawable(int style, int tintColor, boolean isSelected, @Nullable GlassParams params) {
        CircularBubbleDrawable d = new CircularBubbleDrawable(style, tintColor, isSelected);
        d.setGlassParams(params);
        return d;
    }

    /** 带玻璃光学参数的胶囊材质（与气泡/圆形底衬严格同一套 GlassParams），参数为空时用系统默认 */
    public static HeaderPillDrawable createHeaderPillDrawable(int style, float dimAlpha, @Nullable GlassParams params) {
        return new HeaderPillDrawable(style, dimAlpha, params);
    }

    // ==================== AGSL SDF 统一光学着色器 ====================
    //
    // 单一圆角矩形 SDF（有符号距离场）剖面函数承载全部边缘光学：
    // 雾面基底、贴边菲涅尔环（R/G/B 微色散）、天穹高光弧、顶部漫天光/底部反射、
    // 细描边、微晶噪点——气泡 / 圆形底衬 / 胶囊+开关三个载体逐像素同源，
    // 0~50% 全圆角档位（正圆 / 25% 圆角方 / 胶囊 / 直角）均数学精确贴边，
    // 不存在旧径向渐变在宽组件上"中间浮一个圆"的几何失真。
    // 输出为预乘 alpha 白系色（rgb ≤ a）；基于 android.graphics.RuntimeShader
    // （API 33+，Android 13），目标三代系统界面（API 35/36/37）全部满足。
    private static final String OPTICS_SHADER_SRC =
        "uniform float2 uSize;\n" +
        "uniform float uCorner;\n" +
        "uniform float uMatte;\n" +
        "uniform float uThickness;\n" +
        "uniform float uSpecular;\n" +
        "uniform float uIor;\n" +
        "uniform float uBorderLight;\n" +
        "uniform float uSelected;\n" +
        "uniform float uMode;\n" +
        "uniform float uAlpha;\n" +
        "\n" +
        "float sdRoundedBox(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;\n" +
        "}\n" +
        "\n" +
        "float hash21(float2 p) {\n" +
        "    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);\n" +
        "    float d = dot(p3, p3.yzx + 33.33);\n" +
        "    p3 += float3(d, d, d);\n" +
        "    return fract((p3.x + p3.y) * p3.z);\n" +
        "}\n" +
        "\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 hs = uSize * 0.5;\n" +
        "    float2 p = fragCoord - hs;\n" +
        "    float r = clamp(uCorner, 0.0, min(hs.x, hs.y));\n" +
        "    float sd = sdRoundedBox(p, hs, r);\n" +
        "    float inside = 1.0 - smoothstep(-1.0, 0.0, sd);\n" +
        "    float x = max(0.0, -sd);\n" +
        "    float halfMin = max(min(hs.x, hs.y), 1.0);\n" +
        "    float t = clamp(x / halfMin, 0.0, 1.0);\n" +
        "    float vTop = clamp(0.5 - p.y / max(uSize.y, 1.0), 0.0, 1.0);\n" +
        "    float vBottom = clamp(0.5 + p.y / max(uSize.y, 1.0), 0.0, 1.0);\n" +
        "\n" +
        "    float3 col;\n" +
        "    if (uMode < 0.5) {\n" +
        "        // 雾面基底：中心通透、边缘凝聚（磨砂浓度驱动）\n" +
        "        float frost = uMatte * mix(0.92, 0.55, smoothstep(0.0, 1.0, t));\n" +
        "        // 贴边等距菲涅尔环 + 微色散（R/G/B 边缘错位 0.8px，真玻璃微彩虹）\n" +
        "        float rimPos = max(1.2, 0.08 * halfMin);\n" +
        "        float rimW = max(1.6, 0.05 * halfMin);\n" +
        "        float disp = 0.8;\n" +
        "        float rimR = exp(-pow((x - (rimPos - disp)) / rimW, 2.0));\n" +
        "        float rimG = exp(-pow((x - rimPos) / rimW, 2.0));\n" +
        "        float rimB = exp(-pow((x - (rimPos + disp)) / rimW, 2.0));\n" +
        "        float rimGain = uMatte * (1.8 + (uThickness - 1.0) * 0.35);\n" +
        "        // 天穹高光弧：沿上缘轮廓的弧光（厚度/高光驱动）\n" +
        "        float arc = exp(-pow((x - rimPos) / (rimW * 1.5), 2.0)) * pow(vTop, 2.2) * uSpecular * 0.30;\n" +
        "        // 顶部漫天光 + 底部反射 + 微晶噪点\n" +
        "        float spec = uSpecular * (0.22 * vTop * vTop + 0.10 * vBottom * vBottom + 0.04);\n" +
        "        float noise = (hash21(fragCoord * 0.9) - 0.5) * uMatte * 0.10;\n" +
        "        float base = frost + spec + arc + noise;\n" +
        "        col = float3(base + rimR * rimGain, base + rimG * rimGain, base + rimB * rimGain);\n" +
        "        // 细描边（最外 1.5px，顶部更亮），borderLight 驱动\n" +
        "        float borderBand = 1.0 - smoothstep(0.0, 1.5, x);\n" +
        "        float border = uBorderLight * (0.12 + 0.50 * vTop);\n" +
        "        col += float3(border, border, border) * borderBand;\n" +
        "        // ior 折射率：内侧辉光微调；选中态整体增益\n" +
        "        col *= 1.0 + (uIor - 1.12) * 0.12 * (1.0 - t);\n" +
        "        col *= 1.0 + uSelected * 0.22;\n" +
        "    } else {\n" +
        "        // 高斯模糊样式：中心柔光 + 贴边描边（选中高亮）\n" +
        "        float frost = (0.078 + 0.079 * uSelected) * smoothstep(0.0, 0.9, t) + uMatte * 0.10;\n" +
        "        float borderBand = 1.0 - smoothstep(0.0, 1.5, x);\n" +
        "        float border = (0.27 + 0.326 * uSelected) * (0.6 + 0.4 * vTop);\n" +
        "        col = float3(frost, frost, frost) + float3(border, border, border) * borderBand;\n" +
        "    }\n" +
        "\n" +
        "    float k = inside * uAlpha;\n" +
        "    float3 cp = clamp(col, 0.0, 1.0) * k;\n" +
        "    float am = max(max(cp.r, cp.g), cp.b);\n" +
        "    return half4(half3(cp), half(am));\n" +
        "}\n";

    /** 光学层 RuntimeShader 持有者：每载体一个实例，逐帧仅注入 uniform（无编译抖动） */
    public static final class CircularOpticsCache {
        private RuntimeShader shader;

        /** API < 33 时返回 null（目标三代系统界面均不触发），调用方走简单雾面兜底 */
        @Nullable
        RuntimeShader shader() {
            if (shader == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    shader = new RuntimeShader(OPTICS_SHADER_SRC);
                } catch (Throwable ignored) {
                    shader = null;
                }
            }
            return shader;
        }
    }

    /**
     * 统一物理光学层绘制（AGSL SDF 单着色器）：气泡、圆形视界底衬、顶部提示胶囊
     * 三个载体必须共用本入口。柔光玻璃与高斯模糊两种材质在同一剖面函数内切换，
     * 0~50% 全圆角档位（正圆 / 圆角方 / 胶囊 / 直角）均数学精确贴边。
     * <p>
     * bgPaint.getAlpha() 约定为外部 Drawable.setAlpha 的调制系数（255=无衰减），
     * 读取后由着色器 uAlpha 承载并复位 paint，避免二次叠加；
     * highlightPaint / borderPaint 参数仅为保持三载体调用签名统一而保留，不再单独绘制。
     */
    public static void drawUnifiedCircularOpticsCached(
        @NonNull Canvas canvas,
        int width,
        int height,
        int style,
        boolean isSelected,
        @NonNull Paint bgPaint,
        @NonNull Paint highlightPaint,
        @NonNull Paint borderPaint,
        @NonNull CircularOpticsCache cache,
        int cornerRadiusPercent,
        @Nullable GlassParams params
    ) {
        if (width <= 0 || height <= 0) return;
        GlassParams p = params != null ? params : new GlassParams();
        // 圆角比例公式与 Outline / 图标裁切 / 模糊贴片统一（BubbleBlurSpec 唯一实现）
        float cornerRadius = BubbleBlurSpec.cornerRadiusOfHalfSize(Math.min(width, height) / 2f, cornerRadiusPercent);
        float alphaMod = Math.max(0f, Math.min(1f, bgPaint.getAlpha() / 255f));
        bgPaint.setAlpha(255);

        RuntimeShader shader = cache.shader();
        if (shader == null) {
            // API < 33 兜底（目标三代系统界面均不触发）：简单雾面圆角矩形
            int a = Math.round(Math.min(1f, clampMatte(p.matteAlpha) * (isSelected ? 1.2f : 0.9f)) * 255f * alphaMod);
            bgPaint.setShader(null);
            bgPaint.setColor((Math.max(0, Math.min(255, a)) << 24) | 0x00FFFFFF);
            canvas.drawRoundRect(0f, 0f, width, height, cornerRadius, cornerRadius, bgPaint);
            return;
        }

        shader.setFloatUniform("uSize", width, height);
        shader.setFloatUniform("uCorner", cornerRadius);
        shader.setFloatUniform("uMatte", clampMatte(p.matteAlpha));
        shader.setFloatUniform("uThickness", Math.max(0f, Math.min(4f, p.thickness)));
        shader.setFloatUniform("uSpecular", Math.max(0f, Math.min(2f, p.specular)));
        shader.setFloatUniform("uIor", Math.max(1f, Math.min(2f, p.ior)));
        shader.setFloatUniform("uBorderLight", Math.max(0f, Math.min(1f, p.borderLight)));
        shader.setFloatUniform("uSelected", isSelected ? 1f : 0f);
        shader.setFloatUniform("uMode", style == STYLE_GAUSSIAN_BLUR ? 1f : 0f);
        shader.setFloatUniform("uAlpha", alphaMod);

        bgPaint.setShader(shader);
        canvas.drawRect(0f, 0f, width, height, bgPaint);
        bgPaint.setShader(null);
    }

    /**
     * 顶部提示胶囊专用材质。
     * <p>
     * 光学绘制完全委托 {@link #drawUnifiedCircularOpticsCached}（与气泡、圆形视界底衬
     * 严格同一套 shader 构建与绘制方式，胶囊自身仅提供圆角胶囊形边界与压暗层），
     * 禁止在此类内再复制任何材质渐变公式。
     */
    public static class HeaderPillDrawable extends Drawable {
        private int style;
        private GlassParams glassParams = null;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dimAlpha = 0f;
        /** setAlpha 调制系数（与 CircularBubbleDrawable 一致）：整层随宿主透明度联动 */
        private int alphaModulation = 255;

        /** 与气泡 / 圆形视界底衬共用的统一光学 shader 缓存 */
        private final CircularOpticsCache opticsCache = new CircularOpticsCache();

        public HeaderPillDrawable(int style) {
            this(style, 0f, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha) {
            this(style, dimAlpha, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha, @Nullable GlassParams params) {
            this.style = style;
            this.glassParams = params;
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(OPTICS_BORDER_STROKE_WIDTH);
            setDimAlpha(dimAlpha);
        }

        public void setDimAlpha(float a) {
            this.dimAlpha = Math.max(0f, Math.min(1f, a));
            dimPaint.setStyle(Paint.Style.FILL);
            invalidateSelf();
        }

        public void setStyle(int newStyle) {
            if (this.style != newStyle) {
                this.style = newStyle;
                invalidateSelf();
            }
        }

        public void setGlassParams(@Nullable GlassParams params) {
            if (this.glassParams != params && (this.glassParams == null || !this.glassParams.equalsParams(params))) {
                this.glassParams = params;
                invalidateSelf();
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;

            // 胶囊形 = 50% 全圆角（cornerRadiusPercent=50 时统一路径的圆角恰为半高）
            float radius = Math.min(width, height) / 2f;

            if (dimAlpha > 0.001f) {
                dimPaint.setColor(android.graphics.Color.argb(Math.round(dimAlpha * 255f * (alphaModulation / 255f)), 0, 0, 0));
                canvas.drawRoundRect(0f, 0f, width, height, radius, radius, dimPaint);
            }

            // 光学层完全走统一路径：与气泡、圆形视界底衬同一套材质参数与绘制方式
            bgPaint.setAlpha(alphaModulation);
            highlightPaint.setAlpha(alphaModulation);
            borderPaint.setAlpha(alphaModulation);
            drawUnifiedCircularOpticsCached(
                canvas, width, height, style, false, bgPaint, highlightPaint, borderPaint, opticsCache, 50, glassParams
            );
        }

        @Override
        public void setAlpha(int alpha) {
            this.alphaModulation = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bgPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * 专属自定义圆形气泡材质 Drawable
     */
    public static class CircularBubbleDrawable extends Drawable {
        private int style;
        private final int tintColor;
        private boolean isSelected;
        private GlassParams glassParams = null;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dimAlpha = 0f;
        /** setAlpha 调制系数：底衬整层随图标透明度联动（与 HeaderPillDrawable 一致） */
        private int alphaModulation = 255;
        private int cornerRadiusPercent = 50;

        private final CircularOpticsCache opticsCache = new CircularOpticsCache();

        public CircularBubbleDrawable(int style, int tintColor, boolean isSelected) {
            this.style = style;
            this.tintColor = tintColor;
            this.isSelected = isSelected;
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(OPTICS_BORDER_STROKE_WIDTH);
        }

        public void setSelected(boolean selected) {
            if (this.isSelected != selected) {
                this.isSelected = selected;
                invalidateSelf();
            }
        }

        public void setStyle(int newStyle) {
            if (this.style != newStyle) {
                this.style = newStyle;
                invalidateSelf();
            }
        }

        public void setGlassParams(@Nullable GlassParams params) {
            this.glassParams = params;
            invalidateSelf();
        }

        public void setDimAlpha(float a) {
            float c = Math.max(0f, Math.min(1f, a));
            if (Math.abs(this.dimAlpha - c) > 0.0005f) {
                this.dimAlpha = c;
                dimPaint.setStyle(Paint.Style.FILL);
                invalidateSelf();
            }
        }

        public void setCornerRadiusPercent(int percent) {
            int clamped = Math.max(0, Math.min(50, percent));
            if (this.cornerRadiusPercent != clamped) {
                this.cornerRadiusPercent = clamped;
                invalidateSelf();
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;

            float w = width;
            float h = height;
            // 圆角比例公式与 Outline / 图标裁切 / 模糊贴片统一（BubbleBlurSpec 唯一实现）
            float cornerRadius = BubbleBlurSpec.cornerRadiusOfHalfSize(Math.min(w, h) / 2f, cornerRadiusPercent);

            if (dimAlpha > 0.001f) {
                dimPaint.setColor(android.graphics.Color.argb(Math.round(dimAlpha * 255f * (alphaModulation / 255f)), 0, 0, 0));
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, dimPaint);
            }

            bgPaint.setAlpha(alphaModulation);
            highlightPaint.setAlpha(alphaModulation);
            borderPaint.setAlpha(alphaModulation);
            drawUnifiedCircularOpticsCached(
                canvas, width, height, style, isSelected, bgPaint, highlightPaint, borderPaint, opticsCache, cornerRadiusPercent, glassParams
            );
        }

        @Override
        public void setAlpha(int alpha) {
            this.alphaModulation = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            bgPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * HyperOS 系统级硬件原生玻璃渲染适配器 (反射调用 View.setMiGlass 与 View.setMiViewMaterialType)
     */
    public static final class SystemNativeGlassAdapter {
        private static volatile Method sSetMiGlassMethod;
        private static volatile Method sSetMiViewMaterialTypeMethod;
        private static volatile Method sSetMiGlassBlurRadiusMethod;
        private static volatile boolean sInitialized = false;
        private static volatile boolean sSupported = false;

        private static void ensureInit() {
            if (sInitialized) return;
            sInitialized = true;
            try {
                Class<?> viewClass = View.class;
                try {
                    sSetMiGlassMethod = viewClass.getMethod("setMiGlass", float[].class);
                    sSetMiGlassMethod.setAccessible(true);
                } catch (Throwable ignored) {}

                try {
                    sSetMiViewMaterialTypeMethod = viewClass.getMethod("setMiViewMaterialType", int.class);
                    sSetMiViewMaterialTypeMethod.setAccessible(true);
                } catch (Throwable ignored) {}

                try {
                    sSetMiGlassBlurRadiusMethod = viewClass.getDeclaredMethod("setMiGlassBlurRadius", int.class, int.class);
                    sSetMiGlassBlurRadiusMethod.setAccessible(true);
                } catch (Throwable ignored) {}

                sSupported = (sSetMiGlassMethod != null && sSetMiViewMaterialTypeMethod != null);
            } catch (Throwable t) {
                sSupported = false;
            }
        }

        public static boolean isSupported() {
            ensureInit();
            return sSupported;
        }

        public static boolean applyGlass(@NonNull View view, @NonNull GlassParams params, int blurRadiusPx) {
            ensureInit();
            if (!sSupported) return false;
            try {
                float[] p = new float[42];
                // 1. blend (luminance & darker)
                p[0] = 0.5f; p[1] = 1.0f; p[2] = 0.0f; p[3] = 1.0f;
                p[4] = 0.2f; p[5] = 1.4f; p[6] = 0.1f; p[7] = 0.0f;
                p[8] = 0.6f; p[9] = 0.8f;
                // 2. inner
                p[10] = 0.05f;
                p[11] = 1.0f; p[12] = 1.0f; p[13] = 1.0f; p[14] = params.matteAlpha;
                p[15] = 0.0f; p[16] = 0.3f; p[17] = 1.2f;
                // 3. shape
                p[18] = 1.0f;
                p[19] = 50.0f; p[20] = 4.0f;
                p[21] = params.thickness * 40.0f;
                p[22] = 600.0f;
                // 4. reflect
                p[23] = params.specular * 1.5f;
                p[24] = params.specular;
                // 5. directionalLight
                p[25] = -0.4f; p[26] = 0.6f; p[27] = -0.8f;
                p[28] = 1.4f; p[29] = 0.8f; p[30] = 1.4f; p[31] = 1.0f;
                // 6. refract ior
                p[32] = params.ior;
                // 7. blurBg
                p[33] = 1.4f; p[34] = 0.0f; p[35] = 0.0f;
                p[36] = 0.0f;
                // 8. mask
                p[37] = 0.0f; p[38] = 0.0f; p[39] = 0.0f;
                p[40] = 0.0f; p[41] = 0.0f;

                sSetMiViewMaterialTypeMethod.invoke(view, 1);
                sSetMiGlassMethod.invoke(view, (Object) p);
                if (sSetMiGlassBlurRadiusMethod != null && blurRadiusPx > 0) {
                    sSetMiGlassBlurRadiusMethod.invoke(view, blurRadiusPx, blurRadiusPx * 2);
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        public static void clearGlass(@NonNull View view) {
            ensureInit();
            if (!sSupported) return;
            try {
                sSetMiViewMaterialTypeMethod.invoke(view, 0);
                sSetMiGlassMethod.invoke(view, (Object) new float[42]);
                if (sSetMiGlassBlurRadiusMethod != null) {
                    sSetMiGlassBlurRadiusMethod.invoke(view, 0, 0);
                }
            } catch (Throwable ignored) {}
        }
    }
}
