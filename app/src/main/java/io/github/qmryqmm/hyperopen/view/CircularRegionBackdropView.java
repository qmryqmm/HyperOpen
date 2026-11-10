package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import io.github.qmryqmm.hyperopen.factory.HyperOSBlurHelper;
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;

/**
 * 圆形视界区域背景视图：
 * 真正的实时系统级局部背景毛玻璃（Real-time Live Backdrop Blur）：
 * 1. 每次唤起展开均重新校准 ViewRootImpl 硬件管道，解决第二次及后续唤出模糊丢失的生命周期问题；
 * 2. 彻底移除任何静态壁纸或死黑图层遮挡，确保 100% 实时穿透看清前台正在运行的应用；
 * 3. 由 SurfaceFlinger 硬件图层合成器通过 BackgroundBlurDrawable 在正圆物理区域内实施硬件高斯模糊；
 * 4. 严格正圆形 OutlineProvider 硬件裁切，确保圆内实时高斯模糊、圆外 100% 清晰原始画面；
 * 5. 叠加水润通透次表面散射、液态玻璃天穹切面高光与菲涅尔镜面圆环；
 * 6. 与全屏模糊彻底解耦，独立受控于自身的模糊半径与透明度。
 */
public class CircularRegionBackdropView extends FrameLayout {

    private int style = HyperOSMaterialFactory.STYLE_SOFT_GLASS;
    private float blurRadiusDp = 25f;
    private float dimAlpha = 0f;
    private float currentRadiusPx = 0f;
    private HyperOSMaterialFactory.GlassParams glassParams = null;

    private OverlayDrawView overlayView;
    private Drawable systemBackdropBlurDrawable;
    private WeakReference<Object> boundVriRef;
    private boolean systemBlurEnabled = true;

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

    /**
     * 控制是否启用底层窗口级 SurfaceFlinger BackgroundBlurDrawable。
     * 在主应用预览界面中由 Compose 的 textureBlur 渲染实时壁纸模糊，禁用此开关以避免双层毛玻璃底色叠加。
     */
    public void setSystemBlurEnabled(boolean enabled) {
        if (this.systemBlurEnabled != enabled) {
            this.systemBlurEnabled = enabled;
            if (!enabled) {
                systemBackdropBlurDrawable = null;
                boundVriRef = null;
                setBackground(null);
            } else if (currentRadiusPx > 0) {
                updateGeometry(currentRadiusPx);
            }
            refreshVisuals();
        }
    }

    private void init() {
        setClipChildren(false);

        // 1. 设置严格正圆形 OutlineProvider 硬件裁切 (圆内高斯模糊，圆外完全清晰，alpha=0 禁用 3D 阴影多边形本影)
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
                outline.setAlpha(0.0f);
            }
        });
        setClipToOutline(true);

        // 2. 顶层水润高光切面、次表面微晶漫反射与菲涅尔双向镜面圆环 (超高透光率，不遮挡底层实时画面)
        overlayView = new OverlayDrawView(getContext());
        addView(overlayView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        refreshVisuals();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (systemBlurEnabled) {
            post(() -> {
                float r = currentRadiusPx > 0 ? currentRadiusPx : (getWidth() > 0 ? getWidth() / 2f : 300f);
                updateGeometry(r);
            });
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE && currentRadiusPx > 0 && systemBlurEnabled) {
            Object currentVri = HyperOSBlurHelper.findViewRootImpl(this);
            Object boundVri = boundVriRef != null ? boundVriRef.get() : null;
            if (systemBackdropBlurDrawable == null || currentVri != boundVri) {
                post(() -> updateGeometry(currentRadiusPx));
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            updateGeometry(w / 2f);
        }
    }

    public void setStyle(int style) {
        if (this.style != style) {
            this.style = style;
            refreshVisuals();
            if (currentRadiusPx > 0) {
                updateGeometry(currentRadiusPx);
            }
        }
    }

    public void setGlassParams(@Nullable HyperOSMaterialFactory.GlassParams params) {
        this.glassParams = params;
        refreshVisuals();
        if (currentRadiusPx > 0) {
            updateGeometry(currentRadiusPx);
        }
    }

    public void setBlurRadius(float radiusDp) {
        float clamped = Math.max(0f, radiusDp);
        if (Math.abs(this.blurRadiusDp - clamped) > 0.01f) {
            this.blurRadiusDp = clamped;
            refreshVisuals();
            if (currentRadiusPx > 0) {
                updateGeometry(currentRadiusPx);
            }
        }
    }

    /** 圆形视界背景压暗 (0.0 ~ 1.0) */
    public void setDimAlpha(float alpha) {
        float c = Math.max(0f, Math.min(1f, alpha));
        if (Math.abs(this.dimAlpha - c) > 0.0005f) {
            this.dimAlpha = c;
            if (overlayView != null) overlayView.invalidate();
        }
    }

    /**
     * 重置模糊实例，避免跨生命周期持有失效的旧 ViewRootImpl 句柄
     */
    public void reset() {
        systemBackdropBlurDrawable = null;
        boundVriRef = null;
        setBackground(null);
    }

    /**
     * 实时设定物理圆形视界半径并同步更新 SurfaceFlinger 实时背景模糊通道
     */
    public void updateGeometry(float radiusPx) {
        if (radiusPx <= 0) return;
        this.currentRadiusPx = radiusPx;

        int blurPx = !systemBlurEnabled ? 0 : Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
        ));
        int tint = HyperOSMaterialFactory.getUnifiedBlurTint(style, glassParams);

        if (blurPx <= 0) {
            systemBackdropBlurDrawable = null;
            boundVriRef = null;
            setBackground(null);
        } else {
            Object currentVri = HyperOSBlurHelper.findViewRootImpl(this);
            Object boundVri = boundVriRef != null ? boundVriRef.get() : null;

            // 仅当未创建或宿主 ViewRootImpl 发生变化时才重新创建 BackgroundBlurDrawable
            if (systemBackdropBlurDrawable != null && currentVri != null && currentVri == boundVri) {
                HyperOSBlurHelper.updateBlurDrawable(systemBackdropBlurDrawable, radiusPx, blurPx, tint);
                try {
                    systemBackdropBlurDrawable.setVisible(true, false);
                } catch (Throwable ignored) {}
            } else {
                Drawable bd = HyperOSBlurHelper.createSystemBackdropBlurDrawable(this, radiusPx, blurPx, tint);
                if (bd != null) {
                    systemBackdropBlurDrawable = bd;
                    boundVriRef = currentVri != null ? new WeakReference<>(currentVri) : null;
                    setBackground(systemBackdropBlurDrawable);
                    try {
                        systemBackdropBlurDrawable.setVisible(true, true);
                    } catch (Throwable ignored) {}
                } else if (systemBackdropBlurDrawable != null) {
                    HyperOSBlurHelper.updateBlurDrawable(systemBackdropBlurDrawable, radiusPx, blurPx, tint);
                    try {
                        systemBackdropBlurDrawable.setVisible(true, false);
                    } catch (Throwable ignored) {}
                }
            }
        }

        if (overlayView != null) {
            overlayView.invalidate();
        }
        invalidate();
    }

    private void refreshVisuals() {
        setAlpha(1.0f);

        int blurPx = !systemBlurEnabled ? 0 : Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
        ));
        int tint = HyperOSMaterialFactory.getUnifiedBlurTint(style, glassParams);

        if (blurPx <= 0) {
            if (systemBackdropBlurDrawable != null) {
                systemBackdropBlurDrawable = null;
                boundVriRef = null;
                setBackground(null);
            }
        } else if (systemBackdropBlurDrawable != null) {
            HyperOSBlurHelper.updateBlurDrawable(systemBackdropBlurDrawable, currentRadiusPx, blurPx, tint);
        }

        if (overlayView != null) {
            overlayView.invalidate();
        }
        invalidate();
    }

    /**
     * 顶层深邃光影与双向菲涅尔镜面圆环 (复用 HyperOSMaterialFactory.drawUnifiedCircularOpticsCached 与气泡完全统一，零对象分配)
     */
    private class OverlayDrawView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final HyperOSMaterialFactory.CircularOpticsCache opticsCache =
            new HyperOSMaterialFactory.CircularOpticsCache();

        public OverlayDrawView(Context context) {
            super(context);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2.0f);
            dimPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (dimAlpha > 0.001f) {
                dimPaint.setColor(android.graphics.Color.argb(Math.round(dimAlpha * 255f), 0, 0, 0));
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, dimPaint);
            }
            HyperOSMaterialFactory.drawUnifiedCircularOpticsCached(
                canvas, getWidth(), getHeight(), style, false, bgPaint, highlightPaint, borderPaint, opticsCache, 50, glassParams
            );
        }
    }
}
