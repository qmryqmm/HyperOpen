package io.github.qmryqmm.hyperopen.view;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.util.BlurRegionSession;

/**
 * 顶部提示胶囊背景控制器：
 * 系统级 BackgroundBlur（{@link BlurRegionSession} 统一生命周期）+ 光学层
 * （{@link HyperOSMaterialFactory.HeaderPillDrawable}，与图标气泡 / 圆形视界底衬
 * 完全统一的材质参数与绘制方式）。
 * <p>
 * <b>注册契约（对齐系统界面官方用法，见 {@link BlurRegionSession}）：</b>
 * blur drawable 必须挂入宿主 View 的背景槽才能完成 blur region 注册——这里以
 * {@code LayerDrawable{blur, 光学层}} 合成背景挂载（blur 在底层、光学层在上层）。
 * blur drawable 由会话复用（创建一次、参数热更新），布局变化只重建廉价的合成壳，
 * 不再每次新建 blur drawable（旧实现每次布局变化都 new 一个，泄漏 blur region）。
 */
public final class HeaderPillBackdrop {

    /** blur 层相对胶囊轮廓的内缩（与历史版本一致：模糊贴片略收进圆角轮廓内侧） */
    private static final int BLUR_LAYER_INSET = 2;

    @NonNull
    private final HyperOSMaterialFactory.HeaderPillDrawable optic;
    @NonNull
    private final BlurRegionSession blurSession = new BlurRegionSession();
    @Nullable
    private LayerDrawable composite;

    private int style;
    @Nullable
    private HyperOSMaterialFactory.GlassParams params;
    private int blurRadiusPx;
    private float dimAlpha;

    public HeaderPillBackdrop(int style, @Nullable HyperOSMaterialFactory.GlassParams params, int blurRadiusPx, float dimAlpha) {
        this.style = style;
        this.params = params;
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
        this.optic = HyperOSMaterialFactory.createHeaderPillDrawable(style, this.dimAlpha, params);
    }

    /** 材质热更新：样式 / 玻璃参数 / 模糊半径 / 压暗即时生效（下一次 {@link #applyTo} 同步 blur 参数） */
    public void update(int style, @Nullable HyperOSMaterialFactory.GlassParams params, int blurRadiusPx, float dimAlpha) {
        boolean paramsEqual = (this.params == null && params == null)
            || (this.params != null && this.params.equalsParams(params));
        this.style = style;
        this.params = params;
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.dimAlpha = Math.max(0f, Math.min(1f, dimAlpha));
        if (!paramsEqual) {
            optic.setGlassParams(params);
        }
        optic.setStyle(style);
        optic.setDimAlpha(this.dimAlpha);
    }

    /**
     * 把合成背景（blur + 光学层）铺到胶囊上并同步 blur 参数（布局 / 参数变化后调用）。
     * blur 模糊半径为 0 时自动注销 blur region，仅保留光学层。
     */
    public void applyTo(@NonNull View pill) {
        int w = pill.getWidth();
        int h = pill.getHeight();

        Drawable blur = null;
        if (blurRadiusPx > 0 && w > 0 && h > 0) {
            float cornerRadiusPx = Math.min(w, h) / 2f;
            int tint = HyperOSMaterialFactory.getUnifiedBlurTint(style, params);
            // 缩放补偿传 null：胶囊不参与鱼眼缩放，bounds 由 LayerDrawable/View 布局管理
            blur = blurSession.ensureFor(pill, null, cornerRadiusPx, blurRadiusPx, tint);
        } else if (blurRadiusPx <= 0) {
            blurSession.release();
        }

        if (blur == null) {
            // 无模糊：仅光学层（与历史行为一致）
            if (pill.getBackground() != optic) {
                pill.setBackground(optic);
            }
            return;
        }

        if (composite == null || composite.getDrawable(0) != blur) {
            composite = new LayerDrawable(new Drawable[]{blur, optic});
            composite.setLayerInset(0, BLUR_LAYER_INSET, BLUR_LAYER_INSET, BLUR_LAYER_INSET, BLUR_LAYER_INSET);
        }
        if (pill.getBackground() != composite) {
            pill.setBackground(composite);
        }
    }

    /** 移除胶囊背景：清背景并显式注销 blur region */
    public void removeFrom(@NonNull View pill) {
        blurSession.release();
        Drawable bg = pill.getBackground();
        if (bg == composite || bg == optic) {
            pill.setBackground(null);
        }
        composite = null;
    }

    /** 释放 blur region（宿主 detach / 销毁时调用） */
    public void release() {
        blurSession.release();
        composite = null;
    }
}
