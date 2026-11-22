package io.github.qmryqmm.hyperopen.view;

import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.util.BlurRegionSession;

public final class ViewMaterialController {
    private final BlurRegionSession blurSession = new BlurRegionSession();
    private final HyperOSMaterialFactory.MaterialDecorationDrawable decoration;
    private final int shape;
    private final int cornerRadiusPercent;
    private View host;
    private int style;
    private HyperOSMaterialFactory.GlassParams params;
    private int blurRadiusPx;
    private float dimAlpha;
    private boolean systemBlurEnabled = true;
    private boolean released;
    private boolean pendingApply = false;
    private View.OnAttachStateChangeListener attachListener;
    private View.OnLayoutChangeListener layoutChangeListener;
    private final ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(@NonNull View target, @NonNull Outline outline) {
            int width = target.getWidth();
            int height = target.getHeight();
            if (width <= 0 || height <= 0) return;
            float radius = cornerRadiusPx(width, height);
            if (shape == HyperOSMaterialFactory.SHAPE_CIRCLE) {
                outline.setOval(0, 0, width, height);
            } else {
                outline.setRoundRect(0, 0, width, height, radius);
            }
            outline.setAlpha(0f);
        }
    };

    public ViewMaterialController(
        int shape,
        int style,
        @Nullable HyperOSMaterialFactory.GlassParams params,
        int blurRadiusPx,
        float dimAlpha,
        int cornerRadiusPercent
    ) {
        this.shape = shape;
        this.style = style;
        this.params = params != null ? params : new HyperOSMaterialFactory.GlassParams();
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
        this.cornerRadiusPercent = Math.max(0, Math.min(50, cornerRadiusPercent));
        this.decoration = new HyperOSMaterialFactory.MaterialDecorationDrawable(
            shape, style, this.params, this.dimAlpha
        );
        this.decoration.setCornerRadiusPercent(this.cornerRadiusPercent);
    }

    public void attach(@NonNull View host) {
        if (this.host == host && !released) {
            apply();
            return;
        }
        release();
        released = false;
        pendingApply = false;
        this.host = host;
        attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View view) {
                view.post(() -> {
                    if (!released && ViewMaterialController.this.host == view) {
                        apply();
                    }
                });
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View view) {
                clearLayers(view);
                blurSession.release();
            }
        };
        layoutChangeListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (released || ViewMaterialController.this.host != view) {
                return;
            }
            int w = right - left;
            int h = bottom - top;
            int oldW = oldRight - oldLeft;
            int oldH = oldBottom - oldTop;
            if (w > 0 && h > 0 && view.getVisibility() == View.VISIBLE
                    && (pendingApply || w != oldW || h != oldH)) {
                pendingApply = false;
                apply();
            }
        };
        host.setOutlineProvider(outlineProvider);
        host.setClipToOutline(true);
        host.addOnAttachStateChangeListener(attachListener);
        host.addOnLayoutChangeListener(layoutChangeListener);
        apply();
    }

    public void update(
        int style,
        @Nullable HyperOSMaterialFactory.GlassParams params,
        int blurRadiusPx,
        float dimAlpha,
        boolean systemBlurEnabled
    ) {
        this.style = style;
        this.params = params != null ? params : new HyperOSMaterialFactory.GlassParams();
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
        this.systemBlurEnabled = systemBlurEnabled;
        decoration.setStyle(style);
        decoration.setGlassParams(this.params);
        decoration.setDimAlpha(this.dimAlpha);
        apply();
    }

    public void apply() {
        View view = host;
        if (view == null || released) {
            return;
        }
        if (!view.isAttachedToWindow()
                || view.getVisibility() != View.VISIBLE
                || view.getWidth() <= 0
                || view.getHeight() <= 0) {
            pendingApply = true;
            return;
        }
        pendingApply = false;
        view.invalidateOutline();

        int resolvedStyle = HyperOSMaterialFactory.resolveActiveStyle(view.getContext(), style);
        decoration.setStyle(resolvedStyle);
        decoration.setGlassParams(params);
        decoration.setDimAlpha(dimAlpha);
        view.setForeground(decoration);

        if (!systemBlurEnabled || blurRadiusPx <= 0) {
            HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(view);
            blurSession.release();
            view.setBackground(null);
            decoration.setNativeGlassActive(false);
            return;
        }

        float cornerRadius = cornerRadiusPx(view.getWidth(), view.getHeight());
        if (resolvedStyle == HyperOSMaterialFactory.STYLE_SOFT_GLASS
            && HyperOSMaterialFactory.isSoftGlassSupported()) {
            blurSession.release();
            view.setBackground(null);
            boolean applied = HyperOSMaterialFactory.SystemNativeGlassAdapter.applyGlass(
                view, params, blurRadiusPx, view.getWidth(), view.getHeight()
            );
            decoration.setNativeGlassActive(applied);
            return;
        }

        HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(view);
        decoration.setNativeGlassActive(false);
        int tint = HyperOSMaterialFactory.getUnifiedBlurTint(resolvedStyle, params);
        Drawable blur = blurSession.ensureFor(view, null, cornerRadius, blurRadiusPx, tint);
        view.setBackground(blur);
    }

    public void release() {
        released = true;
        pendingApply = false;
        if (host != null) {
            clearLayers(host);
            if (attachListener != null) {
                host.removeOnAttachStateChangeListener(attachListener);
            }
            if (layoutChangeListener != null) {
                host.removeOnLayoutChangeListener(layoutChangeListener);
            }
        }
        attachListener = null;
        layoutChangeListener = null;
        blurSession.release();
        host = null;
    }

    private void clearLayers(@NonNull View view) {
        HyperOSMaterialFactory.SystemNativeGlassAdapter.clearGlass(view);
        decoration.setNativeGlassActive(false);
        if (view.getForeground() == decoration) {
            view.setForeground(null);
        }
        if (view.getBackground() != null) {
            view.setBackground(null);
        }
    }

    private float cornerRadiusPx(int width, int height) {
        if (shape == HyperOSMaterialFactory.SHAPE_CIRCLE || shape == HyperOSMaterialFactory.SHAPE_CAPSULE) {
            return Math.min(width, height) / 2f;
        }
        return io.github.qmryqmm.hyperopen.util.BubbleBlurSpec.cornerRadiusOfHalfSize(
            Math.min(width, height) / 2f, cornerRadiusPercent
        );
    }
}
