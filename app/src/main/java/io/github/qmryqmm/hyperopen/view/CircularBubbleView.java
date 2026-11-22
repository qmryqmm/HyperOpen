package io.github.qmryqmm.hyperopen.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.BlurRegionSession;
import io.github.qmryqmm.hyperopen.util.BubbleBlurSpec;

public class CircularBubbleView extends FrameLayout {

    private static final float FOLDER_DIM_RATIO = 0.25f;

    public final ShortcutItem item;
    public float targetX;
    public float targetY;
    public float currentScale = 1.0f;
    private float fisheyeScale = 1.0f;
    private float baseIconAlpha = 1.0f;
    private float folderDimProgress = 0f;
    @Nullable
    private ValueAnimator folderDimAnimator;
    private boolean isSelected = false;

    private int activeStyle;
    private final int sizePx;
    private float blurRadiusDp;
    private boolean bubbleBgEnabled;
    private int paddingPercent;
    /** 气泡区域压暗原始值（气泡材质关闭时实际应用为 0） */
    private float dimAlpha;
    private int cornerRadiusPercent;
    /** 内部配置快照：热更新时经 {@link #updateConfig} 整体替换，避免停留在旧参数 */
    private HyperOpenConfig config;
    /** 上次应用的配置签名（updateConfig 变更检测） */
    private long lastConfigSignature;
    private final ImageView iconView;
    /** 玻璃背景专用底层承载 View（高斯回退绘制 BackgroundBlurDrawable，柔光模式承载原生 MiGlass） */
    private final BlurCarrierView blurCarrierView;
    /** 光学层承载 View（背景 = CircularBubbleDrawable；柔光玻璃模式下同时承载系统原生硬件 setMiGlass） */
    private final View opticView;
    /** 幻影视图：玻璃层内采样「除自身外」邻居气泡的磨砂虚影（见 {@link IconGhostLayer}） */
    private final GhostView ghostView;
    IconGhostLayer ghostLayer;
    RenderNode ghostNode;
    float ghostSizePx;
    private final Path ghostClipPath = new Path();
    private final Rect ghostDrawableBounds = new Rect();
    /** 是否为文件夹展开后的二级子气泡（子气泡基准 Z 轴高于一级文件夹，避免被父级遮挡） */
    private boolean isSubBubble = false;

    @NonNull
    private final HyperOSMaterialFactory.CircularBubbleDrawable opticDrawable;
    /** 系统级背景模糊统一会话（与圆形视界底衬 / 顶部提示胶囊共用同一生命周期实现） */
    @NonNull
    private final BlurRegionSession blurSession = new BlurRegionSession();
    /** 上次同步到 blur region 的缩放；平移动画不重复重绘模糊载体 */
    private float lastBlurGeometryScale = -1f;
    private boolean systemBlurEnabled = true;

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader, HyperOpenConfig config) {
        super(context);
        this.item = item;
        this.activeStyle = style;
        this.sizePx = sizePx;
        this.config = config;
        // 材质参数统一走 HyperOpenConfig 组件级解析（柔光磨砂浓度驱动模糊半径、跟随全局等）
        this.blurRadiusDp = config != null ? config.effectiveBlurRadiusDp(HyperOpenConfig.COMPONENT_BUBBLE) : 25f;
        this.bubbleBgEnabled = config != null ? config.showBubbleBg : true;
        this.paddingPercent = config != null ? config.bubblePaddingPercent : 18;
        this.cornerRadiusPercent = config != null ? Math.max(0, Math.min(50, config.iconCornerRadius)) : 50;

        this.opticDrawable = new HyperOSMaterialFactory.CircularBubbleDrawable(
            activeStyle, item.tintColor, isSelected
        );
        this.opticDrawable.setGlassParams(glassParams());
        this.dimAlpha = config != null ? config.effectiveDimAlpha(HyperOpenConfig.COMPONENT_BUBBLE) : 0f;
        this.opticDrawable.setDimAlpha(this.dimAlpha);
        this.opticDrawable.setCornerRadiusPercent(this.cornerRadiusPercent);

        setLayoutParams(new LayoutParams(sizePx, sizePx));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth() > 0 ? view.getWidth() : sizePx;
                int h = view.getHeight() > 0 ? view.getHeight() : sizePx;
                float corner = BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
                outline.setRoundRect(0, 0, w, h, corner);
            }
        });
        setClipToOutline(true);
        setClipChildren(false);
        setClipToPadding(false);

        // 0. 玻璃背景底层承载视图（仅高斯回退使用；原生 MiGlass 仍挂在 opticView）
        blurCarrierView = new BlurCarrierView(context);
        LayoutParams blurLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        blurLp.gravity = Gravity.CENTER;
        addView(blurCarrierView, blurLp);

        // 1. 幻影视图：采样邻居气泡的磨砂虚影，最终输出由根层圆角 Outline 裁切
        ghostView = new GhostView(context);
        ghostView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth() > 0 ? view.getWidth() : sizePx;
                int h = view.getHeight() > 0 ? view.getHeight() : sizePx;
                float corner = BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
                outline.setRoundRect(0, 0, w, h, corner);
            }
        });
        ghostView.setClipToOutline(true);
        LayoutParams ghostLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        ghostLp.gravity = Gravity.CENTER;
        addView(ghostView, ghostLp);

        // 2. 光学层承载 View：背景挂 CircularBubbleDrawable，并设置圆角 Outline 供系统原生柔光玻璃 SDF 读取
        opticView = new View(context);
        opticView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth() > 0 ? view.getWidth() : sizePx;
                int h = view.getHeight() > 0 ? view.getHeight() : sizePx;
                float corner = BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
                outline.setRoundRect(0, 0, w, h, corner);
            }
        });
        opticView.setClipToOutline(true);
        opticView.setBackground(opticDrawable);
        LayoutParams opticLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        opticLp.gravity = Gravity.CENTER;
        addView(opticView, opticLp);

        // 应用与圆形视界底衬统一的光学材质与背景模糊
        refreshBackground();

        // 中心图标
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        applyIconPadding();

        LayoutParams iconLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iconLp.gravity = Gravity.CENTER;
        addView(iconView, iconLp);

        // 应用图标透明度
        float initAlpha = Math.max(0f, Math.min(1f, config != null ? config.iconAlpha : 1.0f));
        this.baseIconAlpha = initAlpha;
        this.folderDimProgress = 0f;
        iconView.setAlpha(initAlpha);
        opticDrawable.setAlpha(Math.round(initAlpha * 255f));

        // 绑定图标 (优先从包名解析应用图标，或使用默认彩色矢量图形)
        loadItemIcon();

        this.lastConfigSignature = config != null ? configSignature(config) : 0L;
    }

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader) {
        this(context, item, style, sizePx, classLoader, null);
    }

    /**
     * 配置热更新：替换内部配置快照并整体刷新。
     * 图标解析（裁切/底垫）、玻璃光学参数、模糊与压暗全部按新配置重新取值，
     * 消除「复用气泡持有旧配置快照导致材质参数热更新不生效」的问题
     * （真机 setupBubbles 复用分支与预览热更新链共用本入口）。
     * <p>
     * 以 {@link #configSignature} 做变更检测：Compose AndroidView 的 update 块
     * 每次重组都会调用本方法，参数未变时整链短路，避免图标重建与模糊反射更新开销。
     */
    public void updateConfig(@NonNull HyperOpenConfig newConfig) {
        long sig = configSignature(newConfig);
        this.config = newConfig;
        if (sig == lastConfigSignature) {
            return;
        }
        lastConfigSignature = sig;
        this.activeStyle = HyperOSMaterialFactory.resolveActiveStyle(getContext(), newConfig.materialStyle);
        this.blurRadiusDp = newConfig.effectiveBlurRadiusDp(HyperOpenConfig.COMPONENT_BUBBLE);
        this.bubbleBgEnabled = newConfig.showBubbleBg;
        this.paddingPercent = newConfig.bubblePaddingPercent;
        this.dimAlpha = newConfig.effectiveDimAlpha(HyperOpenConfig.COMPONENT_BUBBLE);
        this.cornerRadiusPercent = Math.max(0, Math.min(50, newConfig.iconCornerRadius));
        opticDrawable.setStyle(activeStyle);
        opticDrawable.setCornerRadiusPercent(this.cornerRadiusPercent);
        applyIconPadding();
        loadItemIcon();
        setIconAlpha(newConfig.iconAlpha);
        ensureBlurAndRefreshBackground();
    }

    /** 影响本气泡渲染的配置字段签名（模糊/压暗/磨砂/玻璃/留白/圆角/透明度/图标裁切与底垫） */
    private static long configSignature(@NonNull HyperOpenConfig c) {
        long h = 17;
        h = h * 31 + c.materialStyle;
        h = h * 31 + c.bubbleBlurRadius;
        h = h * 31 + Float.floatToIntBits(c.bubbleDimAlpha);
        h = h * 31 + (c.bubbleMaterialFollowGlobal ? 1 : 0);
        h = h * 31 + c.globalBlurRadius;
        h = h * 31 + Float.floatToIntBits(c.globalDimAlpha);
        h = h * 31 + Float.floatToIntBits(c.bubbleGlassMatteAlpha);
        h = h * 31 + c.glassPreset;
        h = h * 31 + Float.floatToIntBits(c.glassRefractIor);
        h = h * 31 + Float.floatToIntBits(c.glassThickness);
        h = h * 31 + Float.floatToIntBits(c.glassSpecular);
        h = h * 31 + Float.floatToIntBits(c.glassMatteAlpha);
        h = h * 31 + Float.floatToIntBits(c.glassBorderLight);
        h = h * 31 + Float.floatToIntBits(c.glassBackgroundSaturation);
        h = h * 31 + Float.floatToIntBits(c.glassBackgroundBrightness);
        h = h * 31 + Float.floatToIntBits(c.glassBackgroundBurn);
        h = h * 31 + Float.floatToIntBits(c.glassMatteWhiteMix);
        h = h * 31 + Float.floatToIntBits(c.glassMatteColorMix);
        h = h * 31 + Float.floatToIntBits(c.glassMatteColorPower);
        h = h * 31 + Float.floatToIntBits(c.glassEdgeRange);
        h = h * 31 + Float.floatToIntBits(c.glassEdgePower);
        h = h * 31 + Float.floatToIntBits(c.glassReflectOffset);
        h = h * 31 + (c.showBubbleBg ? 1 : 0);
        h = h * 31 + c.bubblePaddingPercent;
        h = h * 31 + c.iconCornerRadius;
        h = h * 31 + Float.floatToIntBits(c.iconAlpha);
        h = h * 31 + c.appIconCropMode;
        h = h * 31 + (c.glyphPadEnabled ? 1 : 0);
        h = h * 31 + c.glyphPadColor;
        h = h * 31 + c.glyphPadMode;
        return h;
    }

    /**
     * 控制是否启用底层窗口级 SurfaceFlinger BackgroundBlurDrawable。
     * 应用内列表卡片（ShortcutCardList）关闭以避免逐图标触发全屏窗口模糊；
     * 主应用预览与真机面板均启用（壁纸 Surface 在窗口后方，窗口级模糊即实时背景模糊）。
     */
    public void setSystemBlurEnabled(boolean enabled) {
        if (this.systemBlurEnabled != enabled) {
            this.systemBlurEnabled = enabled;
            if (!enabled) {
                blurSession.release();
            }
            ensureBlurAndRefreshBackground();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (systemBlurEnabled) {
            post(this::ensureBlurAndRefreshBackground);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelFolderDimAnimator();
        if (opticView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(opticView);
        }
        if (blurCarrierView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(blurCarrierView);
        }
        blurSession.release();
        if (blurCarrierView != null) {
            blurCarrierView.setVisibility(GONE);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && systemBlurEnabled && (w != oldw || h != oldh)) {
            ensureBlurAndRefreshBackground();
        }
    }

    private void applyIconPadding() {
        int effectivePadding = bubbleBgEnabled ? Math.max(0, Math.min(40, paddingPercent)) : 0;
        int iconPadding = (int) (sizePx * (effectivePadding / 100f));
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    }

    /** 组件级统一材质参数（玻璃光学 + 磨砂浓度，与圆形视界底衬 / 顶部提示胶囊同源） */
    private HyperOSMaterialFactory.GlassParams glassParams() {
        if (config != null) {
            return config.glassParamsFor(HyperOpenConfig.COMPONENT_BUBBLE);
        }
        return HyperOSMaterialFactory.GlassParams.fromConfig(null);
    }

    private int getBlurRadiusPx() {
        if (!systemBlurEnabled || !BubbleBlurSpec.isEnabled(blurRadiusDp, bubbleBgEnabled)) return 0;
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
        ));
    }

    /**
     * 确保 SurfaceFlinger 硬件 BackgroundBlurDrawable 已按系统界面契约挂载注册，
     * 并与光学层合成为统一的圆形气泡背景。
     */
    public void ensureBlurAndRefreshBackground() {
        refreshBackground();
    }

    private void refreshBackground() {
        int resolved = HyperOSMaterialFactory.resolveActiveStyle(getContext(), activeStyle);
        this.activeStyle = resolved;
        HyperOSMaterialFactory.GlassParams glassParams = glassParams();
        opticDrawable.setStyle(activeStyle);
        opticDrawable.setGlassParams(glassParams);
        opticDrawable.setSelected(isSelected);
        opticDrawable.setCornerRadiusPercent(cornerRadiusPercent);
        applyEffectiveDim();

        if (getBackground() != null) {
            setBackground(null);
        }

        if (!bubbleBgEnabled) {
            // 气泡材质已关闭：不绘制气泡底衬，模糊与压暗一并停用，仅保留图标
            blurSession.release();
            if (blurCarrierView != null) {
                HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(blurCarrierView);
                blurCarrierView.setVisibility(GONE);
            }
            if (opticView != null) {
                HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(opticView);
                opticDrawable.setNativeGlassActive(false);
                opticView.setVisibility(GONE);
            }
            return;
        }
        if (opticView != null) {
            opticView.setVisibility(VISIBLE);
            opticView.invalidateOutline();
        }

        int blurPx = getBlurRadiusPx();
        int w = getWidth() > 0 ? getWidth() : sizePx;
        int h = getHeight() > 0 ? getHeight() : sizePx;

        // 模式 A：柔光玻璃（且设备支持系统原生硬件柔光玻璃）
        if (activeStyle == HyperOSMaterialFactory.STYLE_SOFT_GLASS
                && HyperOSMaterialFactory.isSoftGlassSupported()
                && opticView != null && blurCarrierView != null) {
            blurSession.release();
            boolean applied = false;
            if (systemBlurEnabled) {
                applied = HyperOSMaterialFactory.SystemNativeGlassAdapter.applyGlass(
                    opticView, glassParams, Math.max(15, blurPx), w, h
                );
            }
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(blurCarrierView);
            opticDrawable.setNativeGlassActive(applied);
            blurCarrierView.setVisibility(GONE);
            if (applied) {
                if (opticView.getBackground() != null) {
                    opticView.setBackground(null);
                }
                if (opticView.getForeground() != opticDrawable) {
                    opticView.setForeground(opticDrawable);
                }
            } else {
                restoreOpticDrawableLayer();
            }
            opticView.invalidate();
            return;
        }

        // 模式 B：高斯模糊（或不支持原生柔光玻璃的回退模式）——启用方案一逆缩放 1.0 物理坐标系 BlurCarrierView
        if (opticView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(opticView);
            restoreOpticDrawableLayer();
        }
        if (blurCarrierView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(blurCarrierView);
        }
        opticDrawable.setNativeGlassActive(false);

        if (!isAttachedToWindow() || !isShown()) {
            blurSession.release();
            if (blurCarrierView != null) {
                blurCarrierView.setVisibility(GONE);
            }
        } else if (blurPx > 0 && blurCarrierView != null) {
            int tint = HyperOSMaterialFactory.getUnifiedBlurTint(activeStyle, glassParams);
            float s = Math.max(0.01f, getScaleX());
            int halfW = Math.round(w * s / 2f);
            int halfH = Math.round(h * s / 2f);
            Rect initBounds = new Rect(-halfW, -halfH, halfW, halfH);
            float initCorner = BubbleBlurSpec.scaledCornerRadiusPx(Math.min(w, h), cornerRadiusPercent, s);
            Drawable blur = blurSession.ensureFor(blurCarrierView, initBounds, initCorner, blurPx, tint);
            if (blur != null) {
                blur.setCallback(blurCarrierView);
                blurCarrierView.setVisibility(VISIBLE);
                blurCarrierView.invalidate();
            } else {
                blurCarrierView.setVisibility(GONE);
            }
        } else {
            blurSession.release();
            if (blurCarrierView != null) {
                blurCarrierView.setVisibility(GONE);
            }
        }
    }

    private void restoreOpticDrawableLayer() {
        if (opticView == null) return;
        if (opticView.getForeground() != null) {
            opticView.setForeground(null);
        }
        if (opticView.getBackground() != opticDrawable) {
            opticView.setBackground(opticDrawable);
        }
    }

    public void setMaterialStyle(int style) {
        int resolved = HyperOSMaterialFactory.resolveActiveStyle(getContext(), style);
        if (this.activeStyle != resolved) {
            this.activeStyle = resolved;
            opticDrawable.setStyle(resolved);
            ensureBlurAndRefreshBackground();
        }
    }

    /** 气泡留白区域背景压暗 (0.0 ~ 1.0)；留白 0%（气泡关闭）时实际应用为 0 */
    public void setDimAlpha(float alpha) {
        this.dimAlpha = alpha;
        applyEffectiveDim();
    }

    /** 按气泡开关应用压暗：气泡材质关闭时压暗跟随停用 */
    private void applyEffectiveDim() {
        opticDrawable.setDimAlpha(bubbleBgEnabled ? dimAlpha : 0f);
    }

    public void setBlurRadius(float radiusDp) {
        float clamped = Math.max(0f, radiusDp);
        if (Math.abs(this.blurRadiusDp - clamped) > 0.01f) {
            this.blurRadiusDp = clamped;
            ensureBlurAndRefreshBackground();
        }
    }

    public void setBubbleBgEnabled(boolean enabled) {
        if (this.bubbleBgEnabled != enabled) {
            this.bubbleBgEnabled = enabled;
            applyIconPadding();
            invalidate();
            ensureBlurAndRefreshBackground();
        }
    }

    public void setBubblePaddingPercent(int percent) {
        if (this.paddingPercent != percent) {
            this.paddingPercent = percent;
            applyIconPadding();
            invalidate();
        }
    }

    public void setCornerRadiusPercent(int percent) {
        int clamped = Math.max(0, Math.min(50, percent));
        if (this.cornerRadiusPercent != clamped) {
            this.cornerRadiusPercent = clamped;
            opticDrawable.setCornerRadiusPercent(clamped);
            invalidateOutline();
            if (opticView != null) {
                opticView.invalidateOutline();
            }
            if (ghostView != null) {
                ghostView.invalidateOutline();
            }
            loadItemIcon();
            ensureBlurAndRefreshBackground();
        }
    }

    public int getCornerRadiusPercent() {
        return cornerRadiusPercent;
    }

    public void resetBlur() {
        animate().cancel();
        resetFolderDim();
        this.isSelected = false;
        this.fisheyeScale = 1.0f;
        this.currentScale = 1.0f;
        setScaleX(1.0f);
        setScaleY(1.0f);
        setTranslationZ(isSubBubble ? 100f : 0f);
        opticDrawable.setSelected(false);
        blurSession.release();
        if (blurCarrierView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(blurCarrierView);
            blurCarrierView.setVisibility(GONE);
        }
        if (opticView != null) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(opticView);
            opticDrawable.setNativeGlassActive(false);
        }
        if (ghostView != null) {
            ghostView.clearBlurEffect();
            ghostView.invalidate();
        }
    }

    public void setIconAlpha(float alpha) {
        this.baseIconAlpha = Math.max(0f, Math.min(1f, alpha));
        applyEffectiveIconAlpha();
    }

    public float getIconAlpha() {
        return iconView != null ? iconView.getAlpha() : computeEffectiveIconAlpha();
    }

    private float computeFolderDimTargetAlpha() {
        float neutralAlpha = config != null ? Math.max(0f, Math.min(1f, config.iconAlpha)) : 1.0f;
        return Math.max(0f, Math.min(1f, neutralAlpha * FOLDER_DIM_RATIO));
    }

    private float computeEffectiveIconAlpha() {
        if (folderDimProgress <= 0.0005f) {
            return baseIconAlpha;
        }
        float dimTarget = computeFolderDimTargetAlpha();
        return Math.max(0f, Math.min(1f, baseIconAlpha + (dimTarget - baseIconAlpha) * folderDimProgress));
    }

    private void applyEffectiveIconAlpha() {
        float effective = computeEffectiveIconAlpha();
        if (iconView != null && Math.abs(iconView.getAlpha() - effective) > 0.001f) {
            iconView.setAlpha(effective);
            opticDrawable.setAlpha(Math.round(effective * 255f));
            opticDrawable.invalidateSelf();
        }
    }

    private void cancelFolderDimAnimator() {
        ValueAnimator animator = folderDimAnimator;
        folderDimAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void startFolderDimAnimator(float from, float to, long durationMs, @Nullable Runnable onFrame) {
        final ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        long actualDuration = Math.max(1L, Math.round(durationMs * Math.abs(to - from)));
        animator.setDuration(actualDuration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            folderDimProgress = (Float) animation.getAnimatedValue();
            applyEffectiveIconAlpha();
            if (onFrame != null) {
                onFrame.run();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (folderDimAnimator == animator) {
                    folderDimAnimator = null;
                }
            }
        });
        folderDimAnimator = animator;
        animator.start();
    }

    /** 文件夹展开/关闭时驱动独立压暗进度 (0f..1f)，避免与每帧鱼眼 baseIconAlpha 互相覆写。 */
    public void animateFolderDim(boolean dimmed, long durationMs) {
        animateFolderDim(dimmed, durationMs, null);
    }

    public void animateFolderDim(boolean dimmed, long durationMs, @Nullable Runnable onFrame) {
        float targetProgress = dimmed ? 1f : 0f;
        cancelFolderDimAnimator();
        if (durationMs <= 0L || Math.abs(folderDimProgress - targetProgress) <= 0.001f) {
            folderDimProgress = targetProgress;
            applyEffectiveIconAlpha();
            if (onFrame != null) {
                onFrame.run();
            }
            return;
        }
        startFolderDimAnimator(folderDimProgress, targetProgress, durationMs, onFrame);
    }

    /** 关闭恢复时从当前有效透明度反解连续起点，确保改写 baseIconAlpha 或打断展开时零跳变。 */
    public void animateFolderRestore(float targetBaseAlpha, long durationMs) {
        float prevEffective = computeEffectiveIconAlpha();
        this.baseIconAlpha = Math.max(0f, Math.min(1f, targetBaseAlpha));
        cancelFolderDimAnimator();
        if (durationMs <= 0L) {
            folderDimProgress = 0f;
            applyEffectiveIconAlpha();
            return;
        }
        float dimTarget = computeFolderDimTargetAlpha();
        float denom = baseIconAlpha - dimTarget;
        float startProgress = Math.abs(denom) > 0.001f
            ? Math.max(0f, Math.min(1f, (baseIconAlpha - prevEffective) / denom))
            : 0f;
        folderDimProgress = startProgress;
        applyEffectiveIconAlpha();
        if (startProgress <= 0.001f) {
            folderDimProgress = 0f;
            applyEffectiveIconAlpha();
            return;
        }
        startFolderDimAnimator(startProgress, 0f, durationMs, null);
    }

    /** 立即取消压暗动画并归零 folderDimProgress，恢复至当前 baseIconAlpha。 */
    public void resetFolderDim() {
        cancelFolderDimAnimator();
        if (folderDimProgress != 0f) {
            folderDimProgress = 0f;
            applyEffectiveIconAlpha();
        }
    }

    /**
     * 选中状态只负责视觉反馈（辉光、气泡描边高亮）；缩放一律由鱼眼曲线经
     * {@link #applyScale(float)} 下发，不再叠加独立选中倍率，避免 maxScale 之外的隐式乘法。
     */
    public void setSelectedState(boolean selected) {
        if (this.isSelected != selected) {
            this.isSelected = selected;
            opticDrawable.setSelected(selected);
        }
    }

    public void applyScale(float scale) {
        if (Math.abs(this.fisheyeScale - scale) > 0.0005f) {
            this.fisheyeScale = scale;
            applyEffectiveScale();
        }
    }

    private void applyEffectiveScale() {
        if (Math.abs(this.currentScale - fisheyeScale) > 0.0005f || Math.abs(getScaleX() - fisheyeScale) > 0.0005f) {
            this.currentScale = fisheyeScale;
            setScaleX(fisheyeScale);
            setScaleY(fisheyeScale);
            // 鱼眼缩放提升 Z 轴：子气泡基准 100f，一级气泡基准 0f；放大越大的气泡 Z 轴越高
            float baseZ = isSubBubble ? 100f : 0f;
            setTranslationZ(baseZ + Math.max(0f, (fisheyeScale - 1.0f) * 50f));
            // 鱼眼/动画逐帧同步背景模糊圆角：blur region 的 bounds 走变换后窗口坐标，
            // 圆角必须乘 scale 才能严格贴合放大后的图标区域（否则模糊呈圆角方形溢出）
            syncBlurCornerRadiusWithViewScale();
            // 仅在需要绘制虚影或需要清除旧虚影时失效，避免常态鱼眼微调触发昂贵的逐气泡模糊。
            if (ghostView != null && (isSubBubble
                    || fisheyeScale > 1.005f
                    || ghostView.hasBlurEffect())) {
                ghostView.invalidate();
            }
        }
    }

    /**
     * 同步 blur region 几何。
     * <p>
     * BlurCarrierView 在 onDraw 中按当前缩放同步窗口坐标 bounds 与圆角，
     * 本入口仅在缩放变化时失效承载视图，使其重新执行几何同步；
     * 平移或其他不改变缩放的动画不会触发重新绘制。
     */
    public void syncBlurCornerRadiusWithViewScale() {
        float scale = Math.max(0.01f, getScaleX());
        if (blurCarrierView != null
                && blurCarrierView.getVisibility() == VISIBLE
                && blurSession.isActive()
                && Math.abs(scale - lastBlurGeometryScale) > 0.002f) {
            blurCarrierView.invalidate();
        }
    }

    public void loadItemIcon() {
        // 统一由 IconGlyphFactory 解析，传入当前实时配置（裁切模式、Miuix 底垫及颜色等）
        Drawable d = IconGlyphFactory.resolve(getContext(), item, config);
        iconView.setImageDrawable(d);
        iconView.setColorFilter(null);
        if (d != null) {
            d.setCallback(iconView);
        }
        iconView.requestLayout();
        iconView.invalidate();
        invalidate();
    }

    /** 注入幻影层（容器持有、三载体玻璃层共用）；列表卡片等无重叠场景传 null 即可 */
    public void setGhostLayer(@Nullable IconGhostLayer layer) {
        if (this.ghostLayer == layer) return;
        this.ghostLayer = layer;
        if (ghostView != null) {
            ghostView.invalidate();
        }
    }

    /** 标记是否为文件夹展开后的子气泡（置于父级文件夹图标上方） */
    public void setIsSubBubble(boolean isSub) {
        this.isSubBubble = isSub;
        setTranslationZ(isSub ? 100f : 0f);
    }

    /** 显式清空幻影视图的硬件模糊滤镜与画面内容，消除残影 */
    public void clearGhost() {
        if (ghostView != null) {
            ghostView.clearBlurEffect();
            ghostView.invalidate();
        }
    }

    /** 驱动幻影视图同步重绘（动画展开/缩放过程逐帧调用） */
    public void syncGhost() {
        if (ghostView != null) {
            ghostView.invalidate();
        }
    }

    /**
     * 供 {@link IconGhostLayer} 录制的「完整视觉」：光学层（雾面+描边）+ 图标。
     * <b>刻意不含幻影视图与背景模糊</b>——前者避免幻影节点互相嵌套引用（递归），
     * 后者避免窗口级模糊被录进幻影（幻影只应承载图标内容）。
     */
    void drawVisualForGhost(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.save();
        float corner = BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
        ghostClipPath.reset();
        ghostClipPath.addRoundRect(0f, 0f, (float) w, (float) h, corner, corner, Path.Direction.CW);
        canvas.clipPath(ghostClipPath);

        // 1. 光学层（底衬 + 磨砂 + 边框）：直接绘制 opticDrawable，规避硬件加速 View 跨树 draw 的约束
        if (bubbleBgEnabled && opticDrawable != null) {
            ghostDrawableBounds.set(opticDrawable.getBounds());
            try {
                opticDrawable.setBounds(0, 0, w, h);
                opticDrawable.drawForGhost(canvas);
            } finally {
                opticDrawable.setBounds(ghostDrawableBounds);
            }
        }
        // 2. 图标层：直接调用 iconView.draw(canvas)，由 ImageView 自行处理 FIT_CENTER Matrix 与 padding，
        // 严禁直接篡改 iconView.getDrawable().setBounds()，否则会污染宿主 ImageView 自身的居中矩阵导致真实图标错位
        if (iconView != null && iconView.getVisibility() == VISIBLE) {
            float alpha = iconView.getAlpha();
            int alphaInt = Math.round(alpha * 255f);
            if (alphaInt > 0) {
                if (alphaInt < 255) {
                    canvas.saveLayerAlpha(0, 0, w, h, alphaInt);
                    iconView.draw(canvas);
                    canvas.restore();
                } else {
                    iconView.draw(canvas);
                }
            }
        }
        canvas.restore();
    }

    boolean isSubBubbleView() {
        return isSubBubble;
    }

    /**
     * 方案一：高斯模糊专用逆缩放 1.0 物理坐标系承载视图。
     * 通过在 onDraw 中绕中心逆缩放 1f/s，抵消父级 CircularBubbleView 的鱼眼缩放 s，
     * 使 BackgroundBlurDrawable 的本地挖孔路径 mRectPath 与提交给 SurfaceFlinger 的
     * 窗口物理坐标 BlurRegion 在 1.0 物理像素坐标系下 100% 全等，彻底消除刺角与变正圆悖论。
     */
    private class BlurCarrierView extends View {
        private final Rect carrierBounds = new Rect();

        BlurCarrierView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == blurSession.drawable() || super.verifyDrawable(who);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            Drawable bd = blurSession.drawable();
            int w = getWidth();
            int h = getHeight();
            if (bd == null || !bubbleBgEnabled || w <= 0 || h <= 0) {
                return;
            }
            float s = Math.max(0.01f, CircularBubbleView.this.getScaleX());
            lastBlurGeometryScale = s;
            int halfW = Math.round(w * s / 2f);
            int halfH = Math.round(h * s / 2f);
            carrierBounds.set(-halfW, -halfH, halfW, halfH);
            float scaledCorner = BubbleBlurSpec.scaledCornerRadiusPx(Math.min(w, h), cornerRadiusPercent, s);
            blurSession.syncGeometry(carrierBounds, scaledCorner);

            canvas.save();
            canvas.translate(w / 2f, h / 2f);
            canvas.scale(1f / s, 1f / s);
            bd.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * 幻影视图：绘制时反鱼眼变换后采样幻影节点——
     * 邻居虚影以容器真实坐标 / 真实大小呈现（放大中不会跟着放大，符合物理直觉）。
     */
    private class GhostView extends View {
        private float lastBlurPx = -1f;

        GhostView(Context context) {
            super(context);
            setWillNotDraw(false);
            setAlpha(0.65f);
        }

        private void updateBlurEffect(float blurPx) {
            float b = blurPx;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
            if (Math.abs(lastBlurPx - b) > 0.5f) {
                lastBlurPx = b;
                setRenderEffect(RenderEffect.createBlurEffect(b, b, Shader.TileMode.CLAMP));
            }
        }

        private void clearBlurEffect() {
            if (lastBlurPx >= 0f) {
                lastBlurPx = -1f;
                setRenderEffect(null);
            }
        }

        private boolean hasBlurEffect() {
            return lastBlurPx >= 0f;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (ghostLayer == null || !bubbleBgEnabled || getWidth() <= 0 || getHeight() <= 0) {
                clearBlurEffect();
                return;
            }
            // 本视图随气泡父级被缩放；GhostView 自身的 getScaleX() 恒为 1，
            // 必须读取父级 CircularBubbleView 的鱼眼倍率做反向缩放，幻影才保持真实大小。
            float s = CircularBubbleView.this.getScaleX();
            // 面板入场/退场的瞬时 View 动画缩放不生成邻居虚影；
            // 只有真实鱼眼状态或文件夹二级子项才承担高成本的逐气泡幻影模糊。
            boolean shouldDraw = isSubBubble
                    || (CircularBubbleView.this.fisheyeScale > 1.005f && s > 1.005f);
            if (!shouldDraw) {
                // 常态未放大且非子气泡，彻底移除硬件模糊滤镜，不绘制任何内容（录制空 DisplayList 消除残影）
                clearBlurEffect();
                return;
            }
            if (ghostLayer.getBlurRadiusPx() <= 0f) {
                clearBlurEffect();
                return;
            }
            updateBlurEffect(ghostLayer.getBlurRadiusPx());
            canvas.save();
            canvas.translate(getWidth() / 2f, getHeight() / 2f);
            canvas.scale(1f / s, 1f / s);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ghostLayer.drawGhost(canvas, CircularBubbleView.this);
            } else {
                ghostLayer.drawDirect(canvas, CircularBubbleView.this, s);
            }
            canvas.restore();
        }
    }
}
