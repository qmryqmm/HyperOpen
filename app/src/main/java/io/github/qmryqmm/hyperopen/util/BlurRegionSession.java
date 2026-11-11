package io.github.qmryqmm.hyperopen.util;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import io.github.qmryqmm.hyperopen.factory.HyperOSBlurHelper;

/**
 * SurfaceFlinger BackgroundBlurDrawable 生命周期统一管理（单一实现）。
 * <p>
 * <b>注册契约（对齐系统界面 com.android.systemui 官方用法，勿再偏离）：</b>
 * {@code MiuiKeyboardShortcuts} 的标准序列为
 * {@code vri.createBackgroundBlurDrawable() → setBlurRadius → setCornerRadius → setColor →
 * <b>view.setBackground(drawable)</b>}，{@code DeviceEntryIconViewBinder} 在布局变化时对
 * <b>背景槽里的</b> BackgroundBlurDrawable 调 {@code setCornerRadius} 更新。
 * 即：blur drawable <b>必须挂到宿主 View 的背景槽</b>（可为 LayerDrawable 底层），
 * blur region 的注册链路由 View.setBackground / 布局驱动——严禁字段持有 +
 * 空 Canvas draw 的旁路注册（该旁路曾在一次重构中丢失注册时机，导致组件模糊全部消失）。
 * <p>
 * 本会话负责：创建 / 更新 → ViewRootImpl 重绑定自愈 → 参数（模糊半径 / 圆角 / tint）
 * 与缩放补偿几何（bounds 以中心缩放 + 圆角 ×scale，见
 * {@link io.github.qmryqmm.hyperopen.util.BubbleBlurSpec}）→ 显式注销。
 * 背景挂载由调用方完成（可直接 setBackground，或作为 LayerDrawable 底层后整体挂载）；
 * 若 blur drawable 是 LayerDrawable 底层，本会话只写底层自身的 bounds，
 * 绝不触碰 LayerDrawable，避免 View 自动 setBounds 与手动缩放补偿互相覆盖。
 */
public final class BlurRegionSession {

    /** 圆角变化判定阈值（px）：低于该值的差异不触发几何重写 */
    private static final float RADIUS_EPSILON_PX = 0.15f;

    @Nullable
    private Drawable blurDrawable;
    @Nullable
    private WeakReference<Object> boundVriRef;
    private float lastCornerRadiusPx = -1f;
    private final Rect lastBounds = new Rect();

    /**
     * 创建或更新 blur drawable 并同步缩放补偿几何，返回供宿主挂载的实例。
     *
     * @param host           宿主 View（定位 ViewRootImpl；创建后应挂入其背景槽）
     * @param scaledBounds   blur region 的补偿 bounds（不缩放的载体传 null，由 View 布局管理）
     * @param cornerRadiusPx blur region 圆角（与 bounds 几何严格 1:1）
     * @param blurRadiusPx   模糊半径 px；&lt;=0 时注销并返回 null
     * @param tintColor      模糊底色混合色
     * @return 可挂载的 blur drawable；blurRadiusPx&lt;=0 或创建失败时为 null
     */
    @Nullable
    public Drawable ensureFor(@NonNull View host, @Nullable Rect scaledBounds, float cornerRadiusPx, int blurRadiusPx, int tintColor) {
        if (blurRadiusPx <= 0) {
            release();
            return null;
        }

        Object currentVri = HyperOSBlurHelper.findViewRootImpl(host);
        Object boundVri = boundVriRef != null ? boundVriRef.get() : null;

        // 仅当未创建或宿主 ViewRootImpl 发生变化时才重新创建（跨生命周期旧句柄失效自愈）
        if (blurDrawable == null || (currentVri != null && currentVri != boundVri)) {
            blurDrawable = HyperOSBlurHelper.createSystemBackdropBlurDrawable(host, cornerRadiusPx, blurRadiusPx, tintColor);
            boundVriRef = currentVri != null ? new WeakReference<>(currentVri) : null;
            lastCornerRadiusPx = cornerRadiusPx;
            lastBounds.setEmpty();
        } else {
            HyperOSBlurHelper.updateBlurDrawable(blurDrawable, cornerRadiusPx, blurRadiusPx, tintColor);
        }

        if (blurDrawable != null && scaledBounds != null) {
            blurDrawable.setBounds(scaledBounds);
            lastBounds.set(scaledBounds);
            lastCornerRadiusPx = cornerRadiusPx;
        }
        return blurDrawable;
    }

    /**
     * 仅同步缩放补偿几何（鱼眼缩放 / 动画逐帧路径），模糊半径与 tint 不变。
     * blur region 的 bounds 走变换后窗口坐标，圆角必须与 bounds 严格 1:1，
     * 否则放大时模糊区域呈「大矩形 + 小圆角」溢出图标视觉区域。
     * 仅在几何实际变化时写入，避免逐帧反射调用。
     */
    public void syncGeometry(@NonNull Rect scaledBounds, float cornerRadiusPx) {
        if (blurDrawable == null) return;
        if (Math.abs(cornerRadiusPx - lastCornerRadiusPx) > RADIUS_EPSILON_PX || !lastBounds.equals(scaledBounds)) {
            lastCornerRadiusPx = cornerRadiusPx;
            lastBounds.set(scaledBounds);
            blurDrawable.setBounds(scaledBounds);
            HyperOSBlurHelper.updateCornerRadius(blurDrawable, cornerRadiusPx);
        }
    }

    /** 注销 blur region（radius=0 触发注销）并释放全部引用；调用方应同时把背景槽清空 */
    public void release() {
        if (blurDrawable != null) {
            try {
                HyperOSBlurHelper.updateBlurDrawable(blurDrawable, 0f, 0, 0);
            } catch (Throwable ignored) {}
        }
        blurDrawable = null;
        boundVriRef = null;
        lastCornerRadiusPx = -1f;
        lastBounds.setEmpty();
    }

    /** blur drawable 是否已创建 */
    public boolean isActive() {
        return blurDrawable != null;
    }

    /** 当前 blur drawable（可能为 null），供宿主挂载 / 摘除背景 */
    @Nullable
    public Drawable drawable() {
        return blurDrawable;
    }
}
