package io.github.qmryqmm.hyperopen.factory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;

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
        int alpha = Math.round(Math.max(0.04f, Math.min(0.40f, params.matteAlpha)) * 255f);
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

    public static Drawable createCircularBubbleDrawable(int style, int tintColor, boolean isSelected) {
        return new CircularBubbleDrawable(style, tintColor, isSelected, true);
    }

    public static Drawable createCircularBubbleDrawable(int style, int tintColor, boolean isSelected, boolean glowEnabled) {
        return new CircularBubbleDrawable(style, tintColor, isSelected, glowEnabled);
    }

    public static Drawable createHeaderPillDrawable(int style) {
        return new HeaderPillDrawable(style);
    }

    public static Drawable createHeaderPillDrawable(int style, float dimAlpha) {
        return new HeaderPillDrawable(style, dimAlpha);
    }

    public static Drawable createCompositeHeaderPillDrawable(@Nullable View view, int style, int blurRadiusPx) {
        return createCompositeHeaderPillDrawable(view, style, blurRadiusPx, 0f);
    }

    /**
     * 创建结合系统原生 BackgroundBlur 与 HeaderPill 物理光学样式的复合胶囊背景。
     * 模糊底色混合与光学参数与圆形视界区域底衬完全统一。
     */
    public static Drawable createCompositeHeaderPillDrawable(@Nullable View view, int style, int blurRadiusPx, float dimAlpha) {
        HeaderPillDrawable opticDrawable = new HeaderPillDrawable(style, dimAlpha);
        if (view == null || blurRadiusPx <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return opticDrawable;
        }
        int h = view.getHeight() > 0 ? view.getHeight() : 96;
        float cornerRadius = Math.max(1f, h / 2f - 1.5f);
        int tint = getUnifiedBlurTint(style);
        Drawable blurDrawable = HyperOSBlurHelper.createSystemBackdropBlurDrawable(view, cornerRadius, blurRadiusPx, tint);
        if (blurDrawable != null) {
            android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(new Drawable[]{blurDrawable, opticDrawable});
            layer.setLayerInset(0, 2, 2, 2, 2);
            return layer;
        }
        return opticDrawable;
    }

    /**
     * 正圆形物理光学层的 Shader 预构建缓存：
     * 气泡与视界底衬的尺寸在布局后恒定（鱼眼缩放走 RenderNode setScaleX/Y，不改变 bounds），
     * 缓存 Shader 后可在 onDraw 中实现 0 对象分配、0 Native SkShader 抖动。
     */
    public static final class CircularOpticsCache {
        private int cachedW = -1;
        private int cachedH = -1;
        private int cachedStyle = -1;
        private boolean cachedSelected = false;
        private GlassParams cachedParams = null;

        Shader lensRefraction;
        Shader specularAndCaustic;
        Shader borderGrad;
        Shader radialMatte;
        int gaussianBorderColor;

        void ensure(int width, int height, int style, boolean isSelected) {
            ensure(width, height, style, isSelected, null);
        }

        void ensure(int width, int height, int style, boolean isSelected, @Nullable GlassParams params) {
            boolean paramsEqual = (cachedParams == null && params == null)
                || (cachedParams != null && cachedParams.equalsParams(params));

            if (cachedW == width && cachedH == height && cachedStyle == style && cachedSelected == isSelected && paramsEqual) {
                return;
            }
            cachedW = width;
            cachedH = height;
            cachedStyle = style;
            cachedSelected = isSelected;
            cachedParams = params != null ? new GlassParams(params.ior, params.thickness, params.specular, params.matteAlpha, params.borderLight) : null;

            float cx = width / 2f;
            float cy = height / 2f;
            float radius = Math.min(cx, cy);
            if (radius <= 0f) return;

            GlassParams p = params != null ? params : new GlassParams();

            if (style == STYLE_SOFT_GLASS) {
                // ================= 桌面/系统级柔光玻璃 (Soft Light Glass) =================
                // 1. 哑光微晶扩散底衬：由中心向边缘微透柔化，无剧烈刺眼焦散
                float baseMatte = p.matteAlpha;
                int alphaCenter = Math.round(Math.max(0.02f, baseMatte * (isSelected ? 0.85f : 0.55f)) * 255f);
                int alphaMid = Math.round(Math.max(0.04f, baseMatte * (isSelected ? 1.25f : 0.90f)) * 255f);
                int alphaRim = Math.round(Math.min(0.55f, baseMatte * (1.15f + (p.thickness - 1.0f) * 0.15f)) * 255f);
                int alphaOuter = Math.round(Math.min(0.40f, baseMatte * 0.80f) * 255f);

                int centerClear = (alphaCenter << 24) | 0x00FFFFFF;
                int midRefract = (alphaMid << 24) | 0x00FFFFFF;
                int innerRim = (alphaRim << 24) | 0x00FFFFFF;
                int outerEdge = (alphaOuter << 24) | 0x00FFFFFF;

                lensRefraction = new RadialGradient(
                    cx, cy - radius * 0.05f, radius,
                    new int[]{centerClear, midRefract, innerRim, outerEdge},
                    new float[]{0f, 0.65f, 0.92f, 1f},
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
     * 使用预构建的 CircularOpticsCache 绘制正圆/圆角物理光学层（onDraw 零分配）
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
        @NonNull CircularOpticsCache cache
    ) {
        drawUnifiedCircularOpticsCached(
            canvas, width, height, style, isSelected, bgPaint, highlightPaint, borderPaint, cache, 50, null
        );
    }

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
        int cornerRadiusPercent
    ) {
        drawUnifiedCircularOpticsCached(
            canvas, width, height, style, isSelected, bgPaint, highlightPaint, borderPaint, cache, cornerRadiusPercent, null
        );
    }

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
        int clampedPercent = Math.max(0, Math.min(50, cornerRadiusPercent));
        float maxR = Math.min(w, h) / 2f;
        float cornerRadius = maxR * (clampedPercent / 50f);

        cache.ensure(width, height, style, isSelected, params);

        float halfStroke = borderPaint.getStrokeWidth() / 2f;
        float strokeCornerRadius = Math.max(0f, cornerRadius - halfStroke);

        if (style == STYLE_SOFT_GLASS) {
            bgPaint.setShader(cache.lensRefraction);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint);

            highlightPaint.setShader(cache.specularAndCaustic);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, highlightPaint);

            borderPaint.setShader(cache.borderGrad);
            canvas.drawRoundRect(halfStroke, halfStroke, w - halfStroke, h - halfStroke, strokeCornerRadius, strokeCornerRadius, borderPaint);
        } else {
            bgPaint.setShader(cache.radialMatte);
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint);

            borderPaint.setShader(null);
            borderPaint.setColor(cache.gaussianBorderColor);
            canvas.drawRoundRect(halfStroke, halfStroke, w - halfStroke, h - halfStroke, strokeCornerRadius, strokeCornerRadius, borderPaint);
        }
    }

    /**
     * 统一绘制正圆形物理光学层（供 CircularRegionBackdropView 与 CircularBubbleDrawable 共同复用）
     */
    public static void drawUnifiedCircularOptics(
        @NonNull Canvas canvas,
        int width,
        int height,
        int style,
        boolean isSelected,
        @NonNull Paint bgPaint,
        @NonNull Paint highlightPaint,
        @NonNull Paint borderPaint
    ) {
        drawUnifiedCircularOptics(canvas, width, height, style, isSelected, bgPaint, highlightPaint, borderPaint, null);
    }

    public static void drawUnifiedCircularOptics(
        @NonNull Canvas canvas,
        int width,
        int height,
        int style,
        boolean isSelected,
        @NonNull Paint bgPaint,
        @NonNull Paint highlightPaint,
        @NonNull Paint borderPaint,
        @Nullable GlassParams params
    ) {
        if (width <= 0 || height <= 0) return;
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(cx, cy);
        if (radius <= 0f) return;

        GlassParams p = params != null ? params : new GlassParams();
        float halfStroke = borderPaint.getStrokeWidth() / 2f;
        float strokeRadius = Math.max(0f, radius - halfStroke);

        if (style == STYLE_SOFT_GLASS) {
            // ================= 桌面/系统级柔光玻璃 (Soft Light Glass) =================
            float baseMatte = p.matteAlpha;
            int alphaCenter = Math.round(Math.max(0.02f, baseMatte * (isSelected ? 0.85f : 0.55f)) * 255f);
            int alphaMid = Math.round(Math.max(0.04f, baseMatte * (isSelected ? 1.25f : 0.90f)) * 255f);
            int alphaRim = Math.round(Math.min(0.55f, baseMatte * (1.15f + (p.thickness - 1.0f) * 0.15f)) * 255f);
            int alphaOuter = Math.round(Math.min(0.40f, baseMatte * 0.80f) * 255f);

            int centerClear = (alphaCenter << 24) | 0x00FFFFFF;
            int midRefract = (alphaMid << 24) | 0x00FFFFFF;
            int innerRim = (alphaRim << 24) | 0x00FFFFFF;
            int outerEdge = (alphaOuter << 24) | 0x00FFFFFF;

            RadialGradient lensRefraction = new RadialGradient(
                cx, cy - radius * 0.05f, radius,
                new int[]{centerClear, midRefract, innerRim, outerEdge},
                new float[]{0f, 0.65f, 0.92f, 1f},
                Shader.TileMode.CLAMP
            );
            bgPaint.setShader(lensRefraction);
            canvas.drawCircle(cx, cy, radius, bgPaint);

            float specFactor = p.specular;
            int topSpecularAlpha = Math.round(Math.min(0.70f, (isSelected ? 0.38f : 0.22f) * specFactor) * 255f);
            int topFadeAlpha = Math.round(Math.min(0.35f, (isSelected ? 0.14f : 0.06f) * specFactor) * 255f);
            int bottomReflectAlpha = Math.round(Math.min(0.28f, (isSelected ? 0.16f : 0.08f) * specFactor) * 255f);

            LinearGradient specularAndCaustic = new LinearGradient(
                0, cy - radius, 0, cy + radius,
                new int[]{(topSpecularAlpha << 24) | 0x00FFFFFF, (topFadeAlpha << 24) | 0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF, (bottomReflectAlpha << 24) | 0x00FFFFFF},
                new float[]{0f, 0.32f, 0.55f, 0.82f, 1f},
                Shader.TileMode.CLAMP
            );
            highlightPaint.setShader(specularAndCaustic);
            canvas.drawCircle(cx, cy, radius, highlightPaint);

            float borderFactor = p.borderLight;
            int bTopAlpha = Math.round(Math.min(0.95f, (isSelected ? 0.85f : 0.55f) * borderFactor) * 255f);
            int bMidAlpha = Math.round(Math.min(0.60f, (isSelected ? 0.40f : 0.22f) * borderFactor) * 255f);
            int bWaistAlpha = Math.round(Math.min(0.35f, (isSelected ? 0.18f : 0.08f) * borderFactor) * 255f);
            int bBottomAlpha = Math.round(Math.min(0.50f, (isSelected ? 0.35f : 0.18f) * borderFactor) * 255f);

            LinearGradient borderGrad = new LinearGradient(
                cx - radius * 0.2f, cy - radius, cx + radius * 0.2f, cy + radius,
                new int[]{(bTopAlpha << 24) | 0x00FFFFFF, (bMidAlpha << 24) | 0x00FFFFFF, (bWaistAlpha << 24) | 0x00FFFFFF, (bBottomAlpha << 24) | 0x00FFFFFF},
                new float[]{0f, 0.38f, 0.72f, 1f},
                Shader.TileMode.CLAMP
            );
            borderPaint.setShader(borderGrad);
            canvas.drawCircle(cx, cy, strokeRadius, borderPaint);
        } else {
            int d0 = isSelected ? 0x28FFFFFF : 0x14FFFFFF;
            int d1 = isSelected ? 0x14FFFFFF : 0x0AFFFFFF;
            RadialGradient radialMatte = new RadialGradient(
                cx, cy, radius,
                new int[]{d0, d1, 0x00FFFFFF},
                new float[]{0f, 0.70f, 1f},
                Shader.TileMode.CLAMP
            );
            bgPaint.setShader(radialMatte);
            canvas.drawCircle(cx, cy, radius, bgPaint);

            borderPaint.setShader(null);
            borderPaint.setColor(isSelected ? 0x98FFFFFF : 0x45FFFFFF);
            canvas.drawCircle(cx, cy, strokeRadius, borderPaint);
        }
    }

    /**
     * 顶部提示胶囊专用材质
     */
    public static class HeaderPillDrawable extends Drawable {
        private int style;
        private GlassParams glassParams = null;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dimAlpha = 0f;

        private final RectF pillRect = new RectF();
        private int cachedW = -1;
        private int cachedH = -1;
        private int cachedStyle = -1;
        private GlassParams cachedParams = null;
        private Shader cachedBgShader;
        private Shader cachedHlShader;
        private Shader cachedBorderShader;

        public HeaderPillDrawable(int style) {
            this(style, 0f, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha) {
            this(style, dimAlpha, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha, @Nullable GlassParams params) {
            this.style = style;
            this.glassParams = params;
            setDimAlpha(dimAlpha);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.5f);
        }

        public void setDimAlpha(float a) {
            this.dimAlpha = Math.max(0f, Math.min(1f, a));
            dimPaint.setStyle(Paint.Style.FILL);
            dimPaint.setColor(android.graphics.Color.argb(Math.round(this.dimAlpha * 255f), 0, 0, 0));
            invalidateSelf();
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

        private void ensureShaders(int width, int height) {
            boolean paramsEqual = (cachedParams == null && glassParams == null)
                || (cachedParams != null && cachedParams.equalsParams(glassParams));

            if (cachedW == width && cachedH == height && cachedStyle == style && paramsEqual) {
                return;
            }
            cachedW = width;
            cachedH = height;
            cachedStyle = style;
            cachedParams = glassParams != null ? new GlassParams(glassParams.ior, glassParams.thickness, glassParams.specular, glassParams.matteAlpha, glassParams.borderLight) : null;
            pillRect.set(1.5f, 1.5f, width - 1.5f, height - 1.5f);

            GlassParams p = glassParams != null ? glassParams : new GlassParams();

            if (style == STYLE_SOFT_GLASS) {
                int baseA = Math.round(Math.max(0.04f, Math.min(0.40f, p.matteAlpha)) * 255f);
                int topA = Math.round(Math.min(0.50f, baseA * 1.3f));
                int midA = Math.round(baseA * 0.7f);
                int botA = Math.round(baseA * 0.9f);

                cachedBgShader = new LinearGradient(
                    0, 0, 0, height,
                    new int[]{(topA << 24) | 0x00FFFFFF, (midA << 24) | 0x00FFFFFF, (botA << 24) | 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
                );

                int specA = Math.round(Math.min(0.50f, 0.20f * p.specular) * 255f);
                cachedHlShader = new LinearGradient(
                    0, 0, 0, height,
                    new int[]{(specA << 24) | 0x00FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f},
                    Shader.TileMode.CLAMP
                );

                int bLight = Math.round(Math.min(0.80f, p.borderLight * 0.7f) * 255f);
                int bFade = Math.round(Math.min(0.30f, p.borderLight * 0.25f) * 255f);
                cachedBorderShader = new LinearGradient(
                    0, 0, 0, height,
                    new int[]{(bLight << 24) | 0x00FFFFFF, (bFade << 24) | 0x00FFFFFF},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
                );
            } else {
                cachedBgShader = new LinearGradient(
                    0, 0, 0, height,
                    new int[]{0x14FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.70f, 1f},
                    Shader.TileMode.CLAMP
                );
                cachedHlShader = null;
                cachedBorderShader = null;
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;

            ensureShaders(width, height);
            float radius = Math.min(width, height) / 2f;

            if (dimAlpha > 0.001f) {
                canvas.drawRoundRect(pillRect, radius, radius, dimPaint);
            }

            if (style == STYLE_SOFT_GLASS) {
                bgPaint.setShader(cachedBgShader);
                canvas.drawRoundRect(pillRect, radius, radius, bgPaint);

                if (cachedHlShader != null) {
                    highlightPaint.setShader(cachedHlShader);
                    canvas.drawRoundRect(pillRect, radius, radius, highlightPaint);
                }

                if (cachedBorderShader != null) {
                    borderPaint.setShader(cachedBorderShader);
                    canvas.drawRoundRect(pillRect, radius, radius, borderPaint);
                }
            } else {
                bgPaint.setShader(cachedBgShader);
                canvas.drawRoundRect(pillRect, radius, radius, bgPaint);

                borderPaint.setShader(null);
                borderPaint.setColor(0x45FFFFFF);
                canvas.drawRoundRect(pillRect, radius, radius, borderPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            bgPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
            highlightPaint.setAlpha(alpha);
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
        private boolean glowEnabled;
        private GlassParams glassParams = null;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outerAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float dimAlpha = 0f;
        private int cornerRadiusPercent = 50;

        private final CircularOpticsCache opticsCache = new CircularOpticsCache();
        private int glowCachedW = -1;
        private int glowCachedH = -1;

        public CircularBubbleDrawable(int style, int tintColor, boolean isSelected) {
            this(style, tintColor, isSelected, true);
        }

        public CircularBubbleDrawable(int style, int tintColor, boolean isSelected, boolean glowEnabled) {
            this.style = style;
            this.tintColor = tintColor;
            this.isSelected = isSelected;
            this.glowEnabled = glowEnabled;
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(isSelected ? 2.0f : 1.5f);
        }

        public void setSelected(boolean selected) {
            if (this.isSelected != selected) {
                this.isSelected = selected;
                borderPaint.setStrokeWidth(selected ? 2.0f : 1.5f);
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

        public void setGlowEnabled(boolean enabled) {
            if (this.glowEnabled != enabled) {
                this.glowEnabled = enabled;
                invalidateSelf();
            }
        }

        public void setDimAlpha(float a) {
            float c = Math.max(0f, Math.min(1f, a));
            if (Math.abs(this.dimAlpha - c) > 0.0005f) {
                this.dimAlpha = c;
                dimPaint.setStyle(Paint.Style.FILL);
                dimPaint.setColor(android.graphics.Color.argb(Math.round(c * 255f), 0, 0, 0));
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

        public int getCornerRadiusPercent() {
            return cornerRadiusPercent;
        }

        private void ensureGlowShaders(int width, int height, float cx, float cy, float radius) {
            if (glowCachedW == width && glowCachedH == height) {
                return;
            }
            glowCachedW = width;
            glowCachedH = height;

            int coreColor = (tintColor & 0x00FFFFFF) | 0x66000000;
            RadialGradient glowShader = new RadialGradient(
                cx, cy, radius * 0.88f,
                new int[]{coreColor, (coreColor & 0x00FFFFFF) | 0x14000000, 0x00000000},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
            );
            glowPaint.setShader(glowShader);

            int auraColor = (tintColor & 0x00FFFFFF) | 0x38000000;
            RadialGradient auraShader = new RadialGradient(
                cx, cy, radius,
                new int[]{0x00000000, auraColor, 0x00000000},
                new float[]{0.68f, 0.88f, 1f},
                Shader.TileMode.CLAMP
            );
            outerAuraPaint.setShader(auraShader);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;

            float w = width;
            float h = height;
            float maxR = Math.min(w, h) / 2f;
            int clampedPercent = Math.max(0, Math.min(50, cornerRadiusPercent));
            float cornerRadius = maxR * (clampedPercent / 50f);

            if (dimAlpha > 0.001f) {
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, dimPaint);
            }

            drawUnifiedCircularOpticsCached(
                canvas, width, height, style, isSelected, bgPaint, highlightPaint, borderPaint, opticsCache, cornerRadiusPercent, glassParams
            );

            if (isSelected && glowEnabled) {
                ensureGlowShaders(width, height, w / 2f, h / 2f, maxR);
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, glowPaint);
                canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, outerAuraPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            bgPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
            highlightPaint.setAlpha(alpha);
            glowPaint.setAlpha(alpha);
            outerAuraPaint.setAlpha(alpha);
            if (dimAlpha > 0.001f) {
                dimPaint.setAlpha(Math.round((alpha / 255f) * dimAlpha * 255f));
            }
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
