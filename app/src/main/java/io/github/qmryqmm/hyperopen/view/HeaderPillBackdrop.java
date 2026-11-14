package io.github.qmryqmm.hyperopen.view;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;

public final class HeaderPillBackdrop {
    private ViewMaterialController controller;
    private int style;
    private HyperOSMaterialFactory.GlassParams params;
    private int blurRadiusPx;
    private float dimAlpha;

    public HeaderPillBackdrop(int style, @Nullable HyperOSMaterialFactory.GlassParams params, int blurRadiusPx, float dimAlpha) {
        this.style = style;
        this.params = params;
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
    }

    public void update(int style, @Nullable HyperOSMaterialFactory.GlassParams params, int blurRadiusPx, float dimAlpha) {
        this.style = style;
        this.params = params;
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
        if (controller != null) {
            controller.update(style, params, this.blurRadiusPx, this.dimAlpha, true);
        }
    }

    public void applyTo(@NonNull View pill) {
        if (controller == null) {
            controller = new ViewMaterialController(
                HyperOSMaterialFactory.SHAPE_CAPSULE,
                style,
                params,
                blurRadiusPx,
                dimAlpha,
                50
            );
        }
        controller.attach(pill);
        controller.update(style, params, blurRadiusPx, dimAlpha, true);
    }

    public void removeFrom(@NonNull View pill) {
        if (controller != null) {
            controller.release();
            controller = null;
        }
        if (pill.getBackground() != null) {
            pill.setBackground(null);
        }
        if (pill.getForeground() != null) {
            pill.setForeground(null);
        }
    }

    public void release() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
    }
}
