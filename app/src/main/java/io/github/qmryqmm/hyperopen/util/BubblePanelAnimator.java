package io.github.qmryqmm.hyperopen.util;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.view.CircularBubbleView;

/**
 * 指纹快捷面板气泡「入场展开 / 松手退场」动画的单一数据源。
 * <p>
 * 真实 SystemUI 容器 {@link io.github.qmryqmm.hyperopen.view.CustomHyperOpenContainer} 与应用内
 * 整体预览 {@link io.github.qmryqmm.hyperopen.ui.HyperOpenPreviewView} 共用这一份实现，保证用户
 * 能看见/能感觉到的展开与退场时序完全一致，禁止两侧各自再抄一遍公式。
 * <ul>
 *   <li>入场：从 FOD 指纹中心平移飞出，scale 0.1→1 / alpha 0→1，OvershootInterpolator(1.2f)；</li>
 *   <li>系统原生动画布局：改用 DecelerateInterpolator 直线飞出、scale 保持 1；</li>
 *   <li>animDurationMs==0：瞬开，直接置满状态；</li>
 *   <li>逐项错峰 i*12ms，上限 80ms。</li>
 * </ul>
 */
public final class BubblePanelAnimator {

    private static final OvershootInterpolator EXPAND_INTERPOLATOR = new OvershootInterpolator(1.2f);

    private BubblePanelAnimator() {}

    /** 把面板所有气泡复位到「未展开」的隐藏初始态（面板不可见时使用）。 */
    public static void applyHiddenState(List<? extends CircularBubbleView> bubbles, float fodX, float fodY) {
        for (CircularBubbleView b : bubbles) {
            b.animate().cancel();
            b.setTranslationX(fodX - b.targetX);
            b.setTranslationY(fodY - b.targetY);
            b.setScaleX(0.1f);
            b.setScaleY(0.1f);
            b.setAlpha(0f);
            b.applyScale(1.0f);
        }
    }

    /** 把面板所有气泡复位到「稳定展开」的终态（alwaysShow 或动画被跳过时使用）。 */
    public static void applyShownState(List<? extends CircularBubbleView> bubbles) {
        for (CircularBubbleView b : bubbles) {
            b.animate().cancel();
            b.setTranslationX(0f);
            b.setTranslationY(0f);
            b.setScaleX(1.0f);
            b.setScaleY(1.0f);
            b.setAlpha(1f);
            b.applyScale(1.0f);
        }
    }

    /**
     * 播放入场展开动画。
     *
     * @param bubbles    气泡列表（顺序决定错峰）
     * @param fodX/fodY  FOD 指纹中心（局部坐标）
     * @param config     当前配置
     */
    public static void playExpand(List<? extends CircularBubbleView> bubbles, float fodX, float fodY,
                                  HyperOpenConfig config) {
        boolean isStockSystem = config != null
                && config.layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK;
        boolean useStockAnim = isStockSystem && config.stockSystemAnim;
        int duration = config != null ? Math.max(0, config.animDurationMs) : 320;

        for (int i = 0; i < bubbles.size(); i++) {
            final CircularBubbleView b = bubbles.get(i);
            b.animate().cancel();
            b.setTranslationX(fodX - b.targetX);
            b.setTranslationY(fodY - b.targetY);

            if (duration == 0) {
                b.setTranslationX(0f);
                b.setTranslationY(0f);
                b.setScaleX(1.0f);
                b.setScaleY(1.0f);
                b.setAlpha(1f);
                b.applyScale(1.0f);
            } else if (useStockAnim) {
                b.setScaleX(1.0f);
                b.setScaleY(1.0f);
                b.setAlpha(0f);
                b.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1f)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(0L)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .withEndAction(() -> b.applyScale(1.0f))
                        .start();
            } else {
                b.setScaleX(0.1f);
                b.setScaleY(0.1f);
                b.setAlpha(0f);
                b.syncBlurCornerRadiusWithViewScale();
                b.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1f)
                        .setInterpolator(EXPAND_INTERPOLATOR)
                        .setDuration(duration)
                        .setStartDelay(Math.min(i * 12L, 80L))
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .withEndAction(() -> b.applyScale(1.0f))
                        .start();
            }
        }
    }

    /**
     * 播放松手退场动画：选中项放大微弹淡出，其余向圆心回缩飞散。
     *
     * @param bubbles   气泡列表
     * @param selected  当前选中的气泡（可为 null）
     * @param fodX/fodY FOD 指纹中心（局部坐标）
     * @param config    当前配置（dismissAnimEnabled 关闭时立即回调 onComplete）
     * @param onComplete 全部结束后回调
     */
    public static void playDismiss(List<? extends CircularBubbleView> bubbles,
                                   CircularBubbleView selected,
                                   float fodX, float fodY,
                                   HyperOpenConfig config,
                                   Runnable onComplete) {
        if (config == null || !config.dismissAnimEnabled || bubbles.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int total = bubbles.size();
        final AtomicInteger finishedCount = new AtomicInteger(0);
        final AtomicBoolean called = new AtomicBoolean(false);
        Runnable finishOne = () -> {
            if (finishedCount.incrementAndGet() >= total && called.compareAndSet(false, true)) {
                if (onComplete != null) onComplete.run();
            }
        };

        for (CircularBubbleView b : bubbles) {
            b.animate().cancel();
            if (b == selected) {
                b.animate()
                        .scaleX(1.30f)
                        .scaleY(1.30f)
                        .alpha(0f)
                        .setDuration(120)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .withEndAction(finishOne)
                        .start();
            } else {
                float toX = fodX - b.targetX;
                float toY = fodY - b.targetY;
                b.animate()
                        .translationX(toX * 0.7f)
                        .translationY(toY * 0.7f)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0f)
                        .setDuration(110)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .withEndAction(finishOne)
                        .start();
            }
        }
    }
}
