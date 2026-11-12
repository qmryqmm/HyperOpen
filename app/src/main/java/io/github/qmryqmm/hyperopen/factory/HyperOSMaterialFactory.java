package io.github.qmryqmm.hyperopen.factory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
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

    /**
     * 正圆形物理光学层的 Shader 预构建缓存：
     * 气泡与视界底衬的尺寸在布局后恒定（鱼眼缩放走 RenderNode setScaleX/Y，不改变 bounds），
     * 缓存 Shader 后可在 onDraw 中实现 0 对象分配、0 Native SkShader 抖动。
     */
    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static final class CircularOpticsCache {
        private int cachedW = -1;
        private int cachedH = -1;
        private int cachedStyle = -1;
        private boolean cachedSelected = false;
        private int cachedCornerPercent = -1;
        private GlassParams cachedParams = null;

        Shader lensRefraction;
        Shader specularAndCaustic;
        Shader borderGrad;
        Shader radialMatte;
        int gaussianBorderColor;

        void ensure(int width, int height, int style, boolean isSelected, @Nullable GlassParams params, int cornerRadiusPercent) {
            boolean paramsEqual = (cachedParams == null && params == null)
                || (cachedParams != null && cachedParams.equalsParams(params));
            int clampedCorner = Math.max(0, Math.min(50, cornerRadiusPercent));

            if (cachedW == width && cachedH == height && cachedStyle == style && cachedSelected == isSelected
                    && cachedCornerPercent == clampedCorner && paramsEqual) {
                return;
            }
            cachedW = width;
            cachedH = height;
            cachedStyle = style;
            cachedSelected = isSelected;
            cachedCornerPercent = clampedCorner;
            cachedParams = params != null ? new GlassParams(params.ior, params.thickness, params.specular, params.matteAlpha, params.borderLight) : null;

            float cx = width / 2f;
            float cy = height / 2f;
            float radius = Math.min(cx, cy);
            if (radius <= 0f) return;

            GlassParams p = params != null ? params : new GlassParams();

            if (style == STYLE_SOFT_GLASS) {
                // ================= 桌面/系统级柔光玻璃 (Soft Light Glass) =================
                // 1. 哑光微晶扩散底衬：由中心向边缘微透柔化，无剧烈刺眼焦散
                // 磨砂浓度统一钳制（clampMatte）：气泡 / 圆形底衬 / 提示胶囊对同一参数响应一致
                float baseMatte = clampMatte(p.matteAlpha);
                int alphaCenter = Math.round(Math.max(0.02f, baseMatte * (isSelected ? 0.85f : 0.55f)) * 255f);
                int alphaMid = Math.round(Math.max(0.04f, baseMatte * (isSelected ? 1.25f : 0.90f)) * 255f);
                // thickness 增强：边缘光学厚度直接放大 rim 亮度（原系数 1.15/0.15 太弱，肉眼不可辨）
                int alphaRim = Math.round(Math.min(0.65f, baseMatte * (1.8f + (p.thickness - 1.0f) * 0.35f)) * 255f);
                int alphaOuter = Math.round(Math.min(0.50f, baseMatte * (0.9f + (p.thickness - 1.0f) * 0.10f)) * 255f);

                int centerClear = (alphaCenter << 24) | 0x00FFFFFF;
                int midRefract = (alphaMid << 24) | 0x00FFFFFF;
                int innerRim = (alphaRim << 24) | 0x00FFFFFF;
                int outerEdge = (alphaOuter << 24) | 0x00FFFFFF;

                // ior 折射率 + 圆角联合重映射渐变 stop 位置：
                // ior 越高 → 中心越通透、边缘越凝聚（stop 向 1.0 压缩）
                // cornerPercent 越低（角越方）→ rim 越贴近视觉边缘（stop 向 1.0 推）
                float cornerFactor = 1f - clampedCorner / 50f; // 0=正圆, 1=方形
                float midStop = clamp01(0.65f + (p.ior - 1.12f) * 0.20f + cornerFactor * 0.05f);
                float rimStop = clamp01(0.92f + (p.ior - 1.12f) * 0.06f + cornerFactor * 0.08f);
                if (rimStop <= midStop) rimStop = Math.min(1f, midStop + 0.05f);

                lensRefraction = new RadialGradient(
                    cx, cy - radius * 0.05f, radius,
                    new int[]{centerClear, midRefract, innerRim, outerEdge},
                    new float[]{0f, midStop, rimStop, 1f},
                    Shader.TileMode.CLAMP
                );

                // 2. 顶部轻柔漫天光（告别水滴反光切面，提供温润的自然渐变）
                float specFactor = p.specular;
                int topSpecularAlpha = Math.round(Math.min(0.70f, (isSelected ? 0.38f : 0.22f) * specFactor) * 255f);
                int topFadeAlpha = Math.round(Math.min(0.35f, (isSelected ? 0.14f : 0.06f) * specFactor) * 255f);
                int bottomReflectAlpha = Math.round(Math.min(0.28f, (isSelected ? 0.16f : 0.08f) * specFactor) * 255f);

                specularAndCaustic = new LinearGradient(
                    0, 0, 0, height,
                    new int[]{(topSpecularAlpha << 24) | 0x00FFFFFF, (topFadeAlpha << 24) | 0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF, (bottomReflectAlpha << 24) | 0x00FFFFFF},
                    new float[]{0f, 0.32f, 0.55f, 0.82f, 1f},
                    Shader.TileMode.CLAMP
                );

                // 3. 细腻低反差微光圆环描边 (柔和过渡，边缘不抢眼)
                float borderFactor = p.borderLight;
                int bTopAlpha = Math.round(Math.min(0.95f, (isSelected ? 0.85f : 0.55f) * borderFactor) * 255f);
                int bMidAlpha = Math.round(Math.min(0.60f, (isSelected ? 0.40f : 0.22f) * borderFactor) * 255f);
                int bWaistAlpha = Math.round(Math.min(0.35f, (isSelected ? 0.18f : 0.08f) * borderFactor) * 255f);
                int bBottomAlpha = Math.round(Math.min(0.50f, (isSelected ? 0.35f : 0.18f) * borderFactor) * 255f);

                borderGrad = new LinearGradient(
                    cx - radius * 0.2f, 0, cx + radius * 0.2f, height,
                    new int[]{(bTopAlpha << 24) | 0x00FFFFFF, (bMidAlpha << 24) | 0x00FFFFFF, (bWaistAlpha << 24) | 0x00FFFFFF, (bBottomAlpha << 24) | 0x00FFFFFF},
                    new float[]{0f, 0.38f, 0.72f, 1f},
                    Shader.TileMode.CLAMP
                );
                radialMatte = null;
            } else {
                int d0 = isSelected ? 0x28FFFFFF : 0x14FFFFFF;
                int d1 = isSelected ? 0x14FFFFFF : 0x0AFFFFFF;
                radialMatte = new RadialGradient(
                    cx, cy, radius,
                    new int[]{d0, d1, 0x00FFFFFF},
                    new float[]{0f, 0.70f, 1f},
                    Shader.TileMode.CLAMP
                );
                gaussianBorderColor = isSelected ? 0x98FFFFFF : 0x45FFFFFF;
                lensRefraction = null;
                specularAndCaustic = null;
                borderGrad = null;
            }
        }
    }

    /**
     * 使用预构建的 CircularOpticsCache 绘制正圆/圆角物理光学层（onDraw 零分配）。
     * 气泡、圆形视界底衬、顶部提示胶囊三个载体必须共用本入口，保证材质效果一致。
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
        float w = width;
        float h = height;
        // 圆角比例公式与 Outline / 图标裁切 / 模糊贴片统一（BubbleBlurSpec 唯一实现）
        float cornerRadius = BubbleBlurSpec.cornerRadiusOfHalfSize(Math.min(w, h) / 2f, cornerRadiusPercent);

        cache.ensure(width, height, style, isSelected, params, cornerRadiusPercent);

        if (style == STYLE_SOFT_GLASS) {
            bgPaint.setShader(cache.lensRefraction);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint);

            highlightPaint.setShader(cache.specularAndCaustic);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, highlightPaint);

            // 柔光微光描边（borderLight 参数驱动，非白色光晕）
            if (cache.borderGrad != null) {
                float halfStroke = borderPaint.getStrokeWidth() / 2f;
                float strokeCornerRadius = Math.max(0f, cornerRadius - halfStroke);
                borderPaint.setShader(cache.borderGrad);
                canvas.drawRoundRect(halfStroke, halfStroke, w - halfStroke, h - halfStroke, strokeCornerRadius, strokeCornerRadius, borderPaint);
            }
        } else {
            bgPaint.setShader(cache.radialMatte);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint);

            // 高斯模糊样式描边（选中高亮由 gaussianBorderColor 承载，与胶囊描边同一规格）。
            // borderPaint.getAlpha() 约定为外部 Drawable.setAlpha 的调制系数（255=无衰减），
            // 此处合成描边色后绘制并恢复调制系数，避免 setColor 覆盖 alpha 导致逐帧衰减。
            int modulation = borderPaint.getAlpha();
            float halfStroke = borderPaint.getStrokeWidth() / 2f;
            float strokeCornerRadius = Math.max(0f, cornerRadius - halfStroke);
            borderPaint.setShader(null);
            borderPaint.setColor(cache.gaussianBorderColor);
            borderPaint.setAlpha(Math.round(
                android.graphics.Color.alpha(cache.gaussianBorderColor) * (modulation / 255f)));
            canvas.drawRoundRect(halfStroke, halfStroke, w - halfStroke, h - halfStroke, strokeCornerRadius, strokeCornerRadius, borderPaint);
            borderPaint.setAlpha(modulation);
        }
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
