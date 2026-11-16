package io.github.qmryqmm.hyperopen.factory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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
     * 检测当前设备是否支持系统原生柔光玻璃材质（View.setMiGlass / setMiViewMaterialType 且系统属性开启）。
     */
    public static boolean isSoftGlassSupported() {
        return SystemNativeGlassAdapter.isSupported();
    }

    /**
     * 判断当前应使用的材质：柔光玻璃与高斯模糊二选一；设备不支持柔光玻璃时强制回退高斯模糊。
     */
    public static int resolveActiveStyle(Context context, int configStyle) {
        if (configStyle == STYLE_GAUSSIAN_BLUR || !isSoftGlassSupported()) {
            return STYLE_GAUSSIAN_BLUR;
        }
        return STYLE_SOFT_GLASS;
    }

    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_CAPSULE = 1;
    public static final int SHAPE_ROUND_RECT = 2;

    public static class MaterialDecorationDrawable extends Drawable {
        private final int shape;
        private int style;
        private GlassParams glassParams;
        private boolean selected;
        private boolean nativeGlassActive;
        private float dimAlpha;
        private int alphaModulation = 255;
        private int cornerRadiusPercent = 50;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MaterialDecorationDrawable(int shape, int style, @Nullable GlassParams params, float dimAlpha) {
            this.shape = shape;
            this.style = style;
            this.glassParams = params != null ? params : new GlassParams();
            this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(OPTICS_BORDER_STROKE_WIDTH);
            dimPaint.setStyle(Paint.Style.FILL);
            fallbackPaint.setStyle(Paint.Style.FILL);
        }

        public void setStyle(int style) {
            if (this.style != style) {
                this.style = style;
                invalidateSelf();
            }
        }

        public void setGlassParams(@Nullable GlassParams params) {
            GlassParams next = params != null ? params : new GlassParams();
            if (!next.equalsParams(this.glassParams)) {
                this.glassParams = next;
                invalidateSelf();
            }
        }

        public void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                invalidateSelf();
            }
        }

        public void setNativeGlassActive(boolean active) {
            if (this.nativeGlassActive != active) {
                this.nativeGlassActive = active;
                invalidateSelf();
            }
        }

        public void setDimAlpha(float alpha) {
            float clamped = Math.max(0f, Math.min(1f, alpha));
            if (Math.abs(this.dimAlpha - clamped) > 0.0005f) {
                this.dimAlpha = clamped;
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

        private float radiusOf(int width, int height) {
            if (shape == SHAPE_CIRCLE || shape == SHAPE_CAPSULE) {
                return Math.min(width, height) / 2f;
            }
            return BubbleBlurSpec.cornerRadiusOfHalfSize(Math.min(width, height) / 2f, cornerRadiusPercent);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            if (width <= 0 || height <= 0) return;
            float radius = radiusOf(width, height);
            float modulation = alphaModulation / 255f;

            if (dimAlpha > 0.001f) {
                dimPaint.setColor(android.graphics.Color.argb(Math.round(dimAlpha * 255f * modulation), 0, 0, 0));
                canvas.drawRoundRect(0f, 0f, width, height, radius, radius, dimPaint);
            }

            if (!nativeGlassActive) {
                float matte = clampMatte(glassParams.matteAlpha);
                int fallbackAlpha = Math.round((style == STYLE_SOFT_GLASS ? matte * 0.32f : matte * 0.16f) * 255f * modulation);
                fallbackPaint.setColor((Math.max(0, fallbackAlpha) << 24) | 0x00FFFFFF);
                canvas.drawRoundRect(0f, 0f, width, height, radius, radius, fallbackPaint);
            }

            float borderAlpha = (glassParams.borderLight * 0.35f + (selected ? 0.32f : 0.08f)) * modulation;
            float inset = OPTICS_BORDER_STROKE_WIDTH * 0.5f;
            borderPaint.setColor(android.graphics.Color.argb(Math.round(Math.min(1f, borderAlpha) * 255f), 255, 255, 255));
            canvas.drawRoundRect(
                inset, inset, width - inset, height - inset,
                Math.max(0f, radius - inset), Math.max(0f, radius - inset),
                borderPaint
            );
        }

        public void drawForGhost(@NonNull Canvas canvas) {
            boolean previous = nativeGlassActive;
            nativeGlassActive = false;
            try {
                draw(canvas);
            } finally {
                nativeGlassActive = previous;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            alphaModulation = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            borderPaint.setColorFilter(colorFilter);
            fallbackPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static class HeaderPillDrawable extends MaterialDecorationDrawable {
        public HeaderPillDrawable(int style) {
            this(style, 0f, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha) {
            this(style, dimAlpha, null);
        }

        public HeaderPillDrawable(int style, float dimAlpha, @Nullable GlassParams params) {
            super(SHAPE_CAPSULE, style, params, dimAlpha);
        }
    }

    public static class CircularBubbleDrawable extends MaterialDecorationDrawable {
        public CircularBubbleDrawable(int style, int tintColor, boolean isSelected) {
            super(SHAPE_ROUND_RECT, style, null, 0f);
            setSelected(isSelected);
        }
    }

    /**
     * HyperOS 系统级硬件原生玻璃渲染适配器
     * 严格对齐 MiuiSystemUI KeyguardBottomAreaInjector + MiGlassCompat + MiBlurCompat + GlassToken.Glass_Common_Small_Thin
     */
    public static final class SystemNativeGlassAdapter {
        private static volatile Method sSetMiGlassMethod;
        private static volatile Method sSetMiViewMaterialTypeMethod;
        private static volatile Method sSetMiGlassBlurRadiusMethod;
        private static volatile Method sSetMiGlassSdfMaxSizeMethod;
        private static volatile Method sSetMiBackgroundBlurModeMethod;
        private static volatile Method sSetMiBackgroundBlurRadiusMethod;
        private static volatile Method sSetMiViewBlurModeMethod;
        private static volatile Method sGetViewRootImplMethod;
        private static volatile Method sRenderNodeSetPassWindowBlurEnabledMethod;
        private static volatile Method sAddTextureViewMethod;
        private static volatile Method sRemoveTextureViewMethod;
        private static volatile Field sLateSetPassBlurEnabledField;
        private static volatile Field sNeedPassWindowBlurField;
        private static volatile Field sRenderNodeField;
        private static final Map<View, WeakReference<Object>> sBlurViewRoots =
            Collections.synchronizedMap(new WeakHashMap<>());
        private static volatile boolean sInitialized = false;
        private static volatile boolean sSupported = false;

        private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static Field findField(Class<?> type, String name) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static void ensureInit() {
            if (sInitialized) return;
            sInitialized = true;
            try {
                Class<?> viewClass = View.class;
                sSetMiGlassMethod = findMethod(viewClass, "setMiGlass", float[].class);
                sSetMiViewMaterialTypeMethod = findMethod(viewClass, "setMiViewMaterialType", int.class);
                sSetMiGlassBlurRadiusMethod = findMethod(viewClass, "setMiGlassBlurRadius", int.class, int.class);
                sSetMiGlassSdfMaxSizeMethod = findMethod(viewClass, "setMiGlassSdfMaxSize", float.class, float.class);
                sSetMiBackgroundBlurModeMethod = findMethod(viewClass, "setMiBackgroundBlurMode", int.class);
                sSetMiBackgroundBlurRadiusMethod = findMethod(viewClass, "setMiBackgroundBlurRadius", int.class);
                sSetMiViewBlurModeMethod = findMethod(viewClass, "setMiViewBlurMode", int.class);
                sGetViewRootImplMethod = findMethod(viewClass, "getViewRootImpl");
                sLateSetPassBlurEnabledField = findField(viewClass, "mLateSetPassBlurEnabled");
                sNeedPassWindowBlurField = findField(viewClass, "mNeedPassWindowBlur");
                sRenderNodeField = findField(viewClass, "mRenderNode");
                if (sRenderNodeField != null) {
                    sRenderNodeSetPassWindowBlurEnabledMethod = findMethod(
                        sRenderNodeField.getType(), "setPassWindowBlurEnabled", boolean.class
                    );
                }
                if (sGetViewRootImplMethod != null) {
                    Class<?> viewRootClass = sGetViewRootImplMethod.getReturnType();
                    sAddTextureViewMethod = findMethod(viewRootClass, "addTextureView", View.class);
                    sRemoveTextureViewMethod = findMethod(viewRootClass, "removeTextureView", View.class);
                }

                boolean bionicProp = true;
                try {
                    Class<?> spClass = Class.forName("android.os.SystemProperties");
                    Method getBool = spClass.getMethod("getBoolean", String.class, boolean.class);
                    bionicProp = Boolean.TRUE.equals(getBool.invoke(null, "persist.sys.bionic_material_supported", true));
                } catch (Throwable ignored) {}

                sSupported = sSetMiGlassMethod != null
                    && sSetMiViewMaterialTypeMethod != null
                    && sSetMiBackgroundBlurModeMethod != null
                    && sGetViewRootImplMethod != null
                    && sRenderNodeSetPassWindowBlurEnabledMethod != null
                    && sAddTextureViewMethod != null
                    && sLateSetPassBlurEnabledField != null
                    && sNeedPassWindowBlurField != null
                    && sRenderNodeField != null
                    && bionicProp;
            } catch (Throwable t) {
                sSupported = false;
            }
        }

        public static boolean isSupported() {
            ensureInit();
            return sSupported;
        }

        public static boolean applyGlass(@NonNull View view, @NonNull GlassParams params, int blurRadiusPx) {
            int width = view.getWidth();
            int height = view.getHeight();
            return applyGlass(view, params, blurRadiusPx, width, height);
        }

        private static Object getViewRoot(View view) {
            try {
                return sGetViewRootImplMethod.invoke(view);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static boolean forcePassWindowBlur(@NonNull View view, boolean enabled) {
            try {
                Object viewRoot = getViewRoot(view);
                if (enabled && (viewRoot == null || !view.isAttachedToWindow())) {
                    return false;
                }

                WeakReference<Object> previousRef = sBlurViewRoots.get(view);
                Object previousViewRoot = previousRef != null ? previousRef.get() : null;
                if (enabled
                    && previousViewRoot == viewRoot
                    && sNeedPassWindowBlurField.getBoolean(view)) {
                    sSetMiBackgroundBlurModeMethod.invoke(view, 0);
                    sSetMiBackgroundBlurModeMethod.invoke(view, 1);
                    return true;
                }

                sLateSetPassBlurEnabledField.setBoolean(view, false);
                sNeedPassWindowBlurField.setBoolean(view, enabled);
                Object renderNode = sRenderNodeField.get(view);
                if (renderNode != null) {
                    sRenderNodeSetPassWindowBlurEnabledMethod.invoke(renderNode, enabled);
                }
                if (previousViewRoot != null
                    && previousViewRoot != viewRoot
                    && sRemoveTextureViewMethod != null) {
                    sRemoveTextureViewMethod.invoke(previousViewRoot, view);
                }
                if (viewRoot != null) {
                    if (enabled) {
                        if (previousViewRoot == viewRoot && sRemoveTextureViewMethod != null) {
                            sRemoveTextureViewMethod.invoke(viewRoot, view);
                        }
                        sAddTextureViewMethod.invoke(viewRoot, view);
                        sBlurViewRoots.put(view, new WeakReference<>(viewRoot));
                    } else if (sRemoveTextureViewMethod != null) {
                        sRemoveTextureViewMethod.invoke(viewRoot, view);
                    }
                }
                if (!enabled) {
                    sBlurViewRoots.remove(view);
                }
                sSetMiBackgroundBlurModeMethod.invoke(view, 0);
                if (enabled) {
                    sSetMiBackgroundBlurModeMethod.invoke(view, 1);
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        public static boolean applyGlass(@NonNull View view, @NonNull GlassParams params, int blurRadiusPx, int widthPx, int heightPx) {
            ensureInit();
            if (!sSupported) return false;
            try {
                float[] glass = new float[42];
                glass[0] = 0.5f; glass[1] = 1f; glass[2] = 0f; glass[3] = 1f;
                glass[4] = 0.2f; glass[5] = 2f; glass[6] = 0.24f; glass[7] = 0.26f;
                glass[8] = 0.6f; glass[9] = 0.8f;
                glass[10] = 0.05f;
                glass[11] = 1f; glass[12] = 1f; glass[13] = 1f;
                glass[14] = clampMatte(params.matteAlpha);
                glass[15] = 0f; glass[16] = 0.4f; glass[17] = 1.5f;
                glass[18] = 1f;
                glass[19] = 50f; glass[20] = 4f;
                glass[21] = 60f * (params.thickness / 1.5f);
                glass[22] = 600f;
                glass[23] = 2f * (params.specular / 0.8f);
                glass[24] = 0.6f * (params.borderLight / 0.45f);
                glass[25] = -0.4f; glass[26] = 0.6f; glass[27] = -0.8f;
                glass[28] = 1.4f; glass[29] = 0.8f; glass[30] = 1.4f; glass[31] = 1f;
                glass[32] = params.ior;
                glass[33] = 1.5f;

                if (!forcePassWindowBlur(view, true)) {
                    return false;
                }
                if (sSetMiBackgroundBlurRadiusMethod != null) {
                    sSetMiBackgroundBlurRadiusMethod.invoke(view, Math.max(1, blurRadiusPx));
                }
                if (sSetMiViewBlurModeMethod != null) {
                    sSetMiViewBlurModeMethod.invoke(view, 1);
                }
                if (sSetMiGlassSdfMaxSizeMethod != null && widthPx > 0 && heightPx > 0) {
                    sSetMiGlassSdfMaxSizeMethod.invoke(view, (float) widthPx, (float) heightPx);
                }
                sSetMiViewMaterialTypeMethod.invoke(view, 1);
                sSetMiGlassMethod.invoke(view, (Object) glass);
                if (sSetMiGlassBlurRadiusMethod != null) {
                    float density = Math.max(1f, view.getResources().getDisplayMetrics().density);
                    float blurDp = Math.max(0f, blurRadiusPx / density);
                    int smallRadius = Math.max(15, Math.round(blurDp * 1.2f));
                    int bigRadius = Math.max(200, Math.round(blurDp * 20f));
                    sSetMiGlassBlurRadiusMethod.invoke(view, smallRadius, bigRadius);
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        public static void clearGlass(@NonNull View view) {
            ensureInit();
            if (!sSupported) return;
            forcePassWindowBlur(view, false);
            try {
                sSetMiViewMaterialTypeMethod.invoke(view, 0);
                sSetMiGlassMethod.invoke(view, (Object) new float[42]);
                if (sSetMiGlassBlurRadiusMethod != null) {
                    sSetMiGlassBlurRadiusMethod.invoke(view, 0, 0);
                }
                if (sSetMiViewBlurModeMethod != null) {
                    sSetMiViewBlurModeMethod.invoke(view, 0);
                }
                if (sSetMiBackgroundBlurRadiusMethod != null) {
                    sSetMiBackgroundBlurRadiusMethod.invoke(view, 0);
                }
            } catch (Throwable ignored) {}
        }
    }
}
