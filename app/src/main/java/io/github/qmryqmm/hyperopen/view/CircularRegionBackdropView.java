package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;

public class CircularRegionBackdropView extends FrameLayout {
    private int style = HyperOSMaterialFactory.STYLE_SOFT_GLASS;
    private float blurRadiusDp = 25f;
    private float dimAlpha = 0f;
    private float currentRadiusPx = 0f;
    private HyperOSMaterialFactory.GlassParams glassParams;
    private boolean systemBlurEnabled = true;
    private final ViewMaterialController materialController = new ViewMaterialController(
        HyperOSMaterialFactory.SHAPE_CIRCLE,
        style,
        null,
        0,
        dimAlpha,
        50
    );

    public CircularRegionBackdropView(@NonNull Context context) {
        this(context, (ClassLoader) null);
    }

    public CircularRegionBackdropView(@NonNull Context context, @Nullable ClassLoader classLoader) {
        super(context);
        init();
    }

    public CircularRegionBackdropView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClipChildren(false);
        materialController.attach(this);
        refreshVisuals();
    }

    public void setSystemBlurEnabled(boolean enabled) {
        if (systemBlurEnabled != enabled) {
            systemBlurEnabled = enabled;
            refreshVisuals();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::refreshVisuals);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) {
            updateGeometry(Math.min(width, height) / 2f);
        }
    }

    public void setStyle(int style) {
        this.style = style;
        refreshVisuals();
    }

    public void setGlassParams(@Nullable HyperOSMaterialFactory.GlassParams params) {
        this.glassParams = params;
        refreshVisuals();
    }

    public void setBlurRadius(float radiusDp) {
        float clamped = Math.max(0f, radiusDp);
        if (Math.abs(blurRadiusDp - clamped) > 0.01f) {
            blurRadiusDp = clamped;
            refreshVisuals();
        }
    }

    public void setDimAlpha(float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (Math.abs(dimAlpha - clamped) > 0.0005f) {
            dimAlpha = clamped;
            refreshVisuals();
        }
    }

    public void reset() {
        materialController.release();
    }

    /** 下一次面板展开前恢复材质生命周期；收起期间保持释放，避免隐藏窗口残留 blur region。 */
    public void prepareForUse() {
        materialController.attach(this);
        refreshVisuals();
    }

    public void updateGeometry(float radiusPx) {
        if (radiusPx <= 0) return;
        currentRadiusPx = radiusPx;
        refreshVisuals();
    }

    private void refreshVisuals() {
        int blurPx = systemBlurEnabled
            ? Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
            ))
            : 0;
        materialController.update(style, glassParams, blurPx, dimAlpha, systemBlurEnabled);
        invalidate();
    }
}
