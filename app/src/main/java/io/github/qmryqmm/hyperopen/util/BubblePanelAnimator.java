package io.github.qmryqmm.hyperopen.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.view.CircularBubbleView;

/**
 * 面板气泡展开/收起动画的单一数据源。
 * 真实 SystemUI 容器与应用预览共享这里的所有时序公式。
 */
public final class BubblePanelAnimator {

    private BubblePanelAnimator() {}

    public static void applyHiddenState(List<? extends CircularBubbleView> bubbles, float fodX, float fodY) {
        for (CircularBubbleView b : bubbles) {
            b.animate().setListener(null).setUpdateListener(null).cancel();
            b.endExternalScaleAnimation();
            b.clearFisheyeTakeover();
            b.setTranslationX(fodX - b.targetX);
            b.setTranslationY(fodY - b.targetY);
            b.setScaleX(0.1f);
            b.setScaleY(0.1f);
            b.setAlpha(0f);
            b.applyScale(1.0f);
        }
    }

    public static void applyShownState(List<? extends CircularBubbleView> bubbles) {
        for (CircularBubbleView b : bubbles) {
            b.animate().setListener(null).setUpdateListener(null).cancel();
            b.endExternalScaleAnimation();
            b.clearFisheyeTakeover();
            b.setTranslationX(0f);
            b.setTranslationY(0f);
            b.setScaleX(1f);
            b.setScaleY(1f);
            b.setAlpha(1f);
            b.applyScale(1f);
        }
    }

    private static AnimatorListenerAdapter createExpandEndListener(final CircularBubbleView b) {
        return new AnimatorListenerAdapter() {
            private boolean canceled = false;
            private boolean done = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (done) return;
                done = true;
                b.animate().setListener(null);
                b.endExternalScaleAnimation();
                if (!canceled && !b.isFisheyeScaleActive()) {
                    b.applyScale(1f);
                    b.ensureBlurAndRefreshBackground();
                }
            }
        };
    }

    public static void playExpand(List<? extends CircularBubbleView> bubbles,
                                  float fodX, float fodY,
                                  HyperOpenConfig config) {
        int duration = config != null ? Math.max(0, config.animDurationMs) : 320;
        int stagger = config != null ? Math.max(0, config.panelExpandStaggerMs) : 12;
        int type = config != null
                ? HyperOpenConfig.normalizePanelExpandAnimType(
                        config.panelExpandAnimType, config.layoutStyle, false)
                : HyperOpenConfig.PANEL_EXPAND_BLOOM;

        for (int i = 0; i < bubbles.size(); i++) {
            CircularBubbleView b = bubbles.get(i);
            b.animate().setListener(null).setUpdateListener(null).cancel();
            if (duration == 0) {
                b.endExternalScaleAnimation();
                b.clearFisheyeTakeover();
                b.setTranslationX(0f);
                b.setTranslationY(0f);
                b.setScaleX(1f);
                b.setScaleY(1f);
                b.setAlpha(1f);
                b.applyScale(1f);
                continue;
            }

            b.prepareExternalScaleTarget(1f, true);
            long delay = Math.min(i * (long) stagger, 160L);
            if (type == HyperOpenConfig.PANEL_EXPAND_FADE_SCALE) {
                b.setTranslationX(0f);
                b.setTranslationY(0f);
                b.setScaleX(0.65f);
                b.setScaleY(0.65f);
                b.setAlpha(0f);
                b.syncBlurCornerRadiusWithViewScale();
                b.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(new DecelerateInterpolator(1.6f))
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .setListener(createExpandEndListener(b))
                        .start();
                continue;
            }

            b.setTranslationX(fodX - b.targetX);
            b.setTranslationY(fodY - b.targetY);
            if (type == HyperOpenConfig.PANEL_EXPAND_STOCK_LINE) {
                b.setScaleX(1f);
                b.setScaleY(1f);
                b.setAlpha(0f);
                b.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .alpha(1f)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setListener(createExpandEndListener(b))
                        .start();
            } else {
                b.setScaleX(0.1f);
                b.setScaleY(0.1f);
                b.setAlpha(0f);
                b.syncBlurCornerRadiusWithViewScale();
                b.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(new OvershootInterpolator(1.2f))
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .setListener(createExpandEndListener(b))
                        .start();
            }
        }
    }

    public static void playDismiss(List<? extends CircularBubbleView> bubbles,
                                   CircularBubbleView selected,
                                   float fodX, float fodY,
                                   HyperOpenConfig config,
                                   Runnable onComplete) {
        if (config == null || !config.dismissAnimEnabled || bubbles.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int type = Math.max(0, Math.min(3, config.panelCollapseAnimType));
        int duration = Math.max(0, config.panelCollapseDurationMs);
        int stagger = Math.max(0, config.panelCollapseStaggerMs);
        if (duration == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        int total = bubbles.size();
        AtomicInteger finished = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finishOne = () -> {
            if (finished.incrementAndGet() >= total && completed.compareAndSet(false, true) && onComplete != null) {
                onComplete.run();
            }
        };

        for (int i = 0; i < bubbles.size(); i++) {
            final CircularBubbleView b = bubbles.get(i);
            b.animate().setListener(null).setUpdateListener(null).cancel();
            b.beginExternalScaleAnimation(false);
            boolean isSelected = b == selected;
            long delay = isSelected ? 0L : Math.min(i * (long) stagger, 120L);
            float popScale = Math.max(1f, config.panelSelectedPopScale);

            AnimatorListenerAdapter endListener = new AnimatorListenerAdapter() {
                private boolean done = false;

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (done) return;
                    done = true;
                    b.animate().setListener(null);
                    b.endExternalScaleAnimation();
                    finishOne.run();
                }
            };

            if (type == HyperOpenConfig.PANEL_COLLAPSE_FADE_SCALE) {
                b.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0f)
                        .setInterpolator(new AccelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .setListener(endListener)
                        .start();
            } else if (isSelected && type == HyperOpenConfig.PANEL_COLLAPSE_SELECTED_POP) {
                b.animate()
                        .scaleX(popScale)
                        .scaleY(popScale)
                        .alpha(0f)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(duration)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .setListener(endListener)
                        .start();
            } else {
                float toX = fodX - b.targetX;
                float toY = fodY - b.targetY;
                float distanceFactor = type == HyperOpenConfig.PANEL_COLLAPSE_STOCK_LINE ? 1f : 0.7f;
                float endScale = type == HyperOpenConfig.PANEL_COLLAPSE_STOCK_LINE ? 0f : 0.2f;
                b.animate()
                        .translationX(toX * distanceFactor)
                        .translationY(toY * distanceFactor)
                        .scaleX(endScale)
                        .scaleY(endScale)
                        .alpha(0f)
                        .setInterpolator(type == HyperOpenConfig.PANEL_COLLAPSE_STOCK_LINE
                                ? new AccelerateInterpolator()
                                : new LinearInterpolator())
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> b.syncBlurCornerRadiusWithViewScale())
                        .setListener(endListener)
                        .start();
            }
        }
    }

}
