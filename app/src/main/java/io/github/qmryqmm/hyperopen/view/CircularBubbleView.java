package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.factory.HyperOSBlurHelper;
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.BubbleBlurSpec;

public class CircularBubbleView extends FrameLayout {

    public final ShortcutItem item;
    public float targetX;
    public float targetY;
    public float currentScale = 1.0f;
    private float fisheyeScale = 1.0f;
    private boolean isSelected = false;

    private int activeStyle;
    private final int sizePx;
    private float blurRadiusDp;
    private int paddingPercent;
    /** 气泡留白区域压暗原始值（气泡关闭时实际应用为 0） */
    private float dimAlpha;
    private int cornerRadiusPercent;
    private final HyperOpenConfig config;
    private final ImageView iconView;

    @NonNull
    private final HyperOSMaterialFactory.CircularBubbleDrawable opticDrawable;
    @Nullable
    private Drawable systemBackdropBlurDrawable;
    @Nullable
    private WeakReference<Object> boundVriRef;
    private float lastBlurCornerRadiusPx = -1f;
    private boolean systemBlurEnabled = true;
    @Nullable
    private Runnable onScaleChangedListener;

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader, HyperOpenConfig config) {
        super(context);
        this.item = item;
        this.activeStyle = style;
        this.sizePx = sizePx;
        this.config = config;
        this.blurRadiusDp = config != null ? config.bubbleBlurRadius : 25f;
        this.paddingPercent = config != null ? config.bubblePaddingPercent : 18;
        this.cornerRadiusPercent = config != null ? Math.max(0, Math.min(50, config.iconCornerRadius)) : 50;

        boolean glowEnabled = config == null || config.iconGlowEffect;
        this.opticDrawable = new HyperOSMaterialFactory.CircularBubbleDrawable(
            activeStyle, item.tintColor, isSelected, glowEnabled
        );
        this.dimAlpha = config != null ? config.bubbleDimAlpha : 0f;
        this.opticDrawable.setDimAlpha(this.dimAlpha);
        this.opticDrawable.setCornerRadiusPercent(this.cornerRadiusPercent);

        setLayoutParams(new LayoutParams(sizePx, sizePx));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth() > 0 ? view.getWidth() : sizePx;
                int h = view.getHeight() > 0 ? view.getHeight() : sizePx;
                float cx = w / 2f;
                float cy = h / 2f;
                float radius = Math.min(cx, cy) - 2.0f;
                if (radius <= 0f) return;
                float corner = radius * (Math.max(0, Math.min(50, cornerRadiusPercent)) / 50f);
                outline.setRoundRect(
                    Math.round(cx - radius),
                    Math.round(cy - radius),
                    Math.round(cx + radius),
                    Math.round(cy + radius),
                    corner
                );
            }
        });
        setClipToOutline(true);
        setClipChildren(false);
        setClipToPadding(false);

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
        float initAlpha = config != null ? config.iconAlpha : 1.0f;
        iconView.setAlpha(initAlpha);
        opticDrawable.setAlpha(Math.round(Math.max(0f, Math.min(1f, initAlpha)) * 255f));

        // 绑定图标 (优先从包名解析应用图标，或使用默认彩色矢量图形)
        loadItemIcon();
    }

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader) {
        this(context, item, style, sizePx, classLoader, null);
    }

    public void setOnScaleChangedListener(@Nullable Runnable listener) {
        this.onScaleChangedListener = listener;
    }

    /**
     * 控制是否启用底层窗口级 SurfaceFlinger BackgroundBlurDrawable。
     * 在主应用预览界面 (HyperOpenPreviewView) 中，已由 Compose 的 textureBlur 渲染实时壁纸模糊，
     * 禁用窗口级硬件模糊可避免在 Activity 窗口上叠加双层毛玻璃底色导致样式偏亮或闪烁。
     */
    public void setSystemBlurEnabled(boolean enabled) {
        if (this.systemBlurEnabled != enabled) {
            this.systemBlurEnabled = enabled;
            if (!enabled) {
                systemBackdropBlurDrawable = null;
                boundVriRef = null;
                lastBlurCornerRadiusPx = -1f;
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && systemBlurEnabled && (w != oldw || h != oldh)) {
            ensureBlurAndRefreshBackground();
        }
    }

    private void applyIconPadding() {
        int iconPadding = (int) (sizePx * (Math.max(0, Math.min(40, paddingPercent)) / 100f));
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    }

    /**
     * 计算用于 SurfaceFlinger BackgroundBlurDrawable 的圆角半径——
     * 规格统一由 {@link BubbleBlurSpec#cornerRadiusPx} 提供（50% 正圆乘 4.0f 交由 Skia 钳制为半宽，
     * 小于 50% 时按比例并随缩放同步），真实侧与预览斑点侧不得各自再抄公式。
     */
    private float getScaledBlurCornerRadiusPx() {
        int w = getWidth() > 0 ? getWidth() : sizePx;
        int h = getHeight() > 0 ? getHeight() : sizePx;
        return BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent, currentScale);
    }

    private int getBlurRadiusPx() {
        // 留白 0% = 气泡整体关闭，气泡模糊跟随停用（开关规格统一由 BubbleBlurSpec 提供）
        if (!systemBlurEnabled || !BubbleBlurSpec.isEnabled(blurRadiusDp, paddingPercent)) return 0;
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
        ));
    }

    /**
     * 确保 SurfaceFlinger 硬件 BackgroundBlurDrawable 已绑定至当前活跃的 ViewRootImpl，
     * 并与光学层合成为统一的圆形气泡背景。
     */
    public void ensureBlurAndRefreshBackground() {
        int blurPx = getBlurRadiusPx();
        float scaledRadiusPx = getScaledBlurCornerRadiusPx();
        int tint = HyperOSMaterialFactory.getUnifiedBlurTint(activeStyle);

        if (blurPx <= 0) {
            systemBackdropBlurDrawable = null;
            boundVriRef = null;
            lastBlurCornerRadiusPx = -1f;
        } else {
            Object currentVri = HyperOSBlurHelper.findViewRootImpl(this);
            Object boundVri = boundVriRef != null ? boundVriRef.get() : null;

            if (systemBackdropBlurDrawable == null || (currentVri != null && currentVri != boundVri)) {
                systemBackdropBlurDrawable = HyperOSBlurHelper.createSystemBackdropBlurDrawable(this, scaledRadiusPx, blurPx, tint);
                boundVriRef = currentVri != null ? new WeakReference<>(currentVri) : null;
                lastBlurCornerRadiusPx = scaledRadiusPx;
            } else {
                HyperOSBlurHelper.updateBlurDrawable(systemBackdropBlurDrawable, scaledRadiusPx, blurPx, tint);
                lastBlurCornerRadiusPx = scaledRadiusPx;
            }
            if (systemBackdropBlurDrawable != null) {
                try {
                    systemBackdropBlurDrawable.setVisible(true, false);
                } catch (Throwable ignored) {}
            }
        }
        refreshBackground();
    }

    private void refreshBackground() {
        boolean glowEnabled = config == null || config.iconGlowEffect;
        opticDrawable.setStyle(activeStyle);
        opticDrawable.setSelected(isSelected);
        opticDrawable.setGlowEnabled(glowEnabled);
        opticDrawable.setCornerRadiusPercent(cornerRadiusPercent);
        applyEffectiveDim();

        if (paddingPercent <= 0) {
            // 留白 0% = 气泡整体关闭：不绘制气泡底衬，模糊与压暗一并停用，仅保留图标
            systemBackdropBlurDrawable = null;
            boundVriRef = null;
            lastBlurCornerRadiusPx = -1f;
            if (getBackground() != null) {
                setBackground(null);
            }
            return;
        }

        int blurPx = getBlurRadiusPx();
        if (blurPx > 0 && systemBackdropBlurDrawable == null && isAttachedToWindow()) {
            float scaledRadiusPx = getScaledBlurCornerRadiusPx();
            int tint = HyperOSMaterialFactory.getUnifiedBlurTint(activeStyle);
            Object currentVri = HyperOSBlurHelper.findViewRootImpl(this);
            systemBackdropBlurDrawable = HyperOSBlurHelper.createSystemBackdropBlurDrawable(this, scaledRadiusPx, blurPx, tint);
            boundVriRef = currentVri != null ? new WeakReference<>(currentVri) : null;
            lastBlurCornerRadiusPx = scaledRadiusPx;
        }

        if (blurPx > 0 && systemBackdropBlurDrawable != null) {
            syncBlurCornerRadiusWithViewScale();
            Drawable currentBg = getBackground();
            if (currentBg instanceof LayerDrawable && ((LayerDrawable) currentBg).getNumberOfLayers() == 2
                    && ((LayerDrawable) currentBg).getDrawable(0) == systemBackdropBlurDrawable
                    && ((LayerDrawable) currentBg).getDrawable(1) == opticDrawable) {
                currentBg.invalidateSelf();
            } else {
                LayerDrawable layer = new LayerDrawable(new Drawable[]{systemBackdropBlurDrawable, opticDrawable});
                layer.setLayerInset(0, BubbleBlurSpec.INSET_PX, BubbleBlurSpec.INSET_PX, BubbleBlurSpec.INSET_PX, BubbleBlurSpec.INSET_PX);
                setBackground(layer);
            }
        } else {
            if (getBackground() != opticDrawable) {
                setBackground(opticDrawable);
            } else {
                opticDrawable.invalidateSelf();
            }
        }
    }

    public void setMaterialStyle(int style) {
        if (this.activeStyle != style) {
            this.activeStyle = style;
            opticDrawable.setStyle(style);
            ensureBlurAndRefreshBackground();
        }
    }

    /** 气泡留白区域背景压暗 (0.0 ~ 1.0)；留白 0%（气泡关闭）时实际应用为 0 */
    public void setDimAlpha(float alpha) {
        this.dimAlpha = alpha;
        applyEffectiveDim();
    }

    /** 按气泡开关应用压暗：留白 0% 气泡关闭时压暗跟随停用 */
    private void applyEffectiveDim() {
        opticDrawable.setDimAlpha(paddingPercent > 0 ? dimAlpha : 0f);
    }

    public void setBlurRadius(float radiusDp) {
        float clamped = Math.max(0f, radiusDp);
        if (Math.abs(this.blurRadiusDp - clamped) > 0.01f) {
            this.blurRadiusDp = clamped;
            ensureBlurAndRefreshBackground();
        }
    }

    public void setBubblePaddingPercent(int percent) {
        if (this.paddingPercent != percent) {
            this.paddingPercent = percent;
            applyIconPadding();
            invalidate();
            // 留白变化会切换气泡开关（0% 关闭气泡）：重建底衬、模糊与压暗
            ensureBlurAndRefreshBackground();
        }
    }

    public void setCornerRadiusPercent(int percent) {
        int clamped = Math.max(0, Math.min(50, percent));
        if (this.cornerRadiusPercent != clamped) {
            this.cornerRadiusPercent = clamped;
            if (this.config != null) {
                this.config.iconCornerRadius = clamped;
            }
            opticDrawable.setCornerRadiusPercent(clamped);
            invalidateOutline();
            loadItemIcon();
            ensureBlurAndRefreshBackground();
        }
    }

    public int getCornerRadiusPercent() {
        return cornerRadiusPercent;
    }

    public void resetBlur() {
        animate().cancel();
        this.isSelected = false;
        this.fisheyeScale = 1.0f;
        this.currentScale = 1.0f;
        setScaleX(1.0f);
        setScaleY(1.0f);
        opticDrawable.setSelected(false);
        systemBackdropBlurDrawable = null;
        boundVriRef = null;
        lastBlurCornerRadiusPx = -1f;
        refreshBackground();
    }

    public void setIconAlpha(float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (iconView != null && Math.abs(iconView.getAlpha() - clamped) > 0.002f) {
            iconView.setAlpha(clamped);
            opticDrawable.setAlpha(Math.round(clamped * 255f));
            opticDrawable.invalidateSelf();
        }
    }

    public float getIconAlpha() {
        return iconView != null ? iconView.getAlpha() : 1.0f;
    }

    /**
     * 选中状态只负责视觉反馈（辉光、气泡描边高亮）；缩放一律由鱼眼曲线经
     * {@link #applyScale(float)} 下发，不再叠加独立选中倍率，避免 maxScale 之外的隐式乘法。
     */
    public void setSelectedState(boolean selected) {
        if (this.isSelected != selected) {
            this.isSelected = selected;
            boolean glowEnabled = config == null || config.iconGlowEffect;
            opticDrawable.setGlowEnabled(glowEnabled);
            opticDrawable.setSelected(selected);
        }
    }

    public void applyScale(float scale) {
        if (Math.abs(this.fisheyeScale - scale) > 0.0005f) {
            this.fisheyeScale = scale;
            applyEffectiveScale();
            if (onScaleChangedListener != null) {
                onScaleChangedListener.run();
            }
        }
    }

    private void applyEffectiveScale() {
        if (Math.abs(this.currentScale - fisheyeScale) > 0.0005f || Math.abs(getScaleX() - fisheyeScale) > 0.0005f) {
            this.currentScale = fisheyeScale;
            setScaleX(fisheyeScale);
            setScaleY(fisheyeScale);
            if (cornerRadiusPercent < 50) {
                syncBlurCornerRadiusWithViewScale();
            }
        }
    }

    /**
     * 保持 BackgroundBlurDrawable 圆角半径始终覆盖缩放后正圆（仅在数值发生变化时才触发反射更新）
     */
    public void syncBlurCornerRadiusWithViewScale() {
        if (systemBackdropBlurDrawable != null) {
            float r = getScaledBlurCornerRadiusPx();
            if (Math.abs(r - lastBlurCornerRadiusPx) > 0.5f) {
                lastBlurCornerRadiusPx = r;
                HyperOSBlurHelper.updateCornerRadius(systemBackdropBlurDrawable, r);
            }
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
}
