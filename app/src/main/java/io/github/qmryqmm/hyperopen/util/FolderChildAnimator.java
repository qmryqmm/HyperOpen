package io.github.qmryqmm.hyperopen.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.view.CircularBubbleView;

/** 文件夹二级子项弹出/收起动画的单一数据源。 */
public final class FolderChildAnimator {

    private FolderChildAnimator() {}

    private static AnimatorListenerAdapter createExpandEndListener(final CircularBubbleView child) {
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
                child.animate().setListener(null);
                child.endExternalScaleAnimation();
                if (!canceled && !child.isFisheyeScaleActive()) {
                    child.applyScale(1f);
                    child.ensureBlurAndRefreshBackground();
                }
            }
        };
    }

    public static void playExpand(List<? extends CircularBubbleView> children,
                                  CircularBubbleView folder,
                                  HyperOpenConfig config,
                                  List<Integer> animationOrder) {
        if (children.isEmpty()) return;
        int duration = config != null ? Math.max(0, config.folderChildExpandDurationMs) : 180;
        int stagger = config != null ? Math.max(0, config.folderChildExpandStaggerMs) : 15;
        int type = config != null
                ? Math.max(0, Math.min(3, config.folderChildExpandAnimType))
                : HyperOpenConfig.FOLDER_CHILD_EXPAND_FAN_SPRING;
        List<CircularBubbleView> ordered = orderBy(children, animationOrder);

        for (int i = 0; i < ordered.size(); i++) {
            CircularBubbleView child = ordered.get(i);
            child.animate().setListener(null).setUpdateListener(null).cancel();
            if (duration == 0) {
                child.endExternalScaleAnimation();
                child.clearFisheyeTakeover();
                child.setTranslationX(0f);
                child.setTranslationY(0f);
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(1f);
                child.applyScale(1f);
                continue;
            }

            child.prepareExternalScaleTarget(1f, true);
            if (type == HyperOpenConfig.FOLDER_CHILD_EXPAND_FADE_SCALE) {
                child.setTranslationX(0f);
                child.setTranslationY(0f);
                child.setScaleX(0.5f);
                child.setScaleY(0.5f);
                child.setAlpha(0f);
                child.syncBlurCornerRadiusWithViewScale();
                child.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .setDuration(duration)
                        .setStartDelay(i * (long) stagger)
                        .setUpdateListener(anim -> {
                            child.syncBlurCornerRadiusWithViewScale();
                            child.syncGhost();
                        })
                        .setListener(createExpandEndListener(child))
                        .start();
                continue;
            }

            child.setTranslationX(folder.targetX - child.targetX);
            child.setTranslationY(folder.targetY - child.targetY);
            if (type == HyperOpenConfig.FOLDER_CHILD_EXPAND_STOCK_LINE) {
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(0f);
                child.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .alpha(1f)
                        .setInterpolator(new DecelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(i * (long) stagger)
                        .setUpdateListener(anim -> {
                            child.syncBlurCornerRadiusWithViewScale();
                            child.syncGhost();
                        })
                        .setListener(createExpandEndListener(child))
                        .start();
            } else {
                float startScale = config != null
                        ? Math.max(0.05f, config.folderChildStartScale) : 0.1f;
                float overshoot = config != null
                        ? Math.max(0f, config.folderChildOvershoot) : 1.2f;
                child.setScaleX(startScale);
                child.setScaleY(startScale);
                child.setAlpha(0f);
                child.syncBlurCornerRadiusWithViewScale();
                long delay = type == HyperOpenConfig.FOLDER_CHILD_EXPAND_WAVE
                        ? waveDelay(i, ordered.size(), stagger)
                        : i * (long) stagger;
                child.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(new OvershootInterpolator(overshoot))
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> {
                            child.syncBlurCornerRadiusWithViewScale();
                            child.syncGhost();
                        })
                        .setListener(createExpandEndListener(child))
                        .start();
            }
        }
    }

    public static void playCollapse(List<? extends CircularBubbleView> children,
                                    CircularBubbleView folder,
                                    HyperOpenConfig config,
                                    Runnable onComplete) {
        if (children.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int duration = config != null ? Math.max(0, config.folderChildCollapseDurationMs) : 140;
        int stagger = config != null ? Math.max(0, config.folderChildCollapseStaggerMs) : 0;
        int type = config != null
                ? Math.max(0, Math.min(2, config.folderChildCollapseAnimType))
                : HyperOpenConfig.FOLDER_CHILD_COLLAPSE_CENTER;
        if (duration == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        int total = children.size();
        AtomicInteger finished = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finishOne = () -> {
            if (finished.incrementAndGet() >= total && completed.compareAndSet(false, true) && onComplete != null) {
                onComplete.run();
            }
        };

        for (int i = 0; i < children.size(); i++) {
            final CircularBubbleView child = children.get(i);
            child.animate().setListener(null).setUpdateListener(null).cancel();
            child.endExternalScaleAnimation();
            child.resetBlur();

            AnimatorListenerAdapter endListener = new AnimatorListenerAdapter() {
                private boolean done = false;

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (done) return;
                    done = true;
                    child.animate().setListener(null);
                    finishOne.run();
                }
            };

            if (type == HyperOpenConfig.FOLDER_CHILD_COLLAPSE_FADE_SCALE) {
                child.animate()
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0f)
                        .setInterpolator(new AccelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(i * (long) stagger)
                        .setUpdateListener(anim -> child.syncBlurCornerRadiusWithViewScale())
                        .setListener(endListener)
                        .start();
            } else {
                float toX = folder.targetX - child.targetX;
                float toY = folder.targetY - child.targetY;
                long delay = type == HyperOpenConfig.FOLDER_CHILD_COLLAPSE_REVERSE_WAVE
                        ? reverseWaveDelay(i, total, stagger)
                        : i * (long) stagger;
                child.animate()
                        .translationX(toX)
                        .translationY(toY)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0f)
                        .setInterpolator(new AccelerateInterpolator())
                        .setDuration(duration)
                        .setStartDelay(delay)
                        .setUpdateListener(anim -> child.syncBlurCornerRadiusWithViewScale())
                        .setListener(endListener)
                        .start();
            }
        }
    }

    private static List<CircularBubbleView> orderBy(List<? extends CircularBubbleView> children,
                                                    List<Integer> order) {
        if (order == null || order.size() != children.size()) return new ArrayList<>(children);
        List<CircularBubbleView> result = new ArrayList<>(children.size());
        for (int index : order) {
            if (index >= 0 && index < children.size()) result.add(children.get(index));
        }
        return result.size() == children.size() ? result : new ArrayList<>(children);
    }

    private static long waveDelay(int index, int count, int stagger) {
        int middle = Math.max(0, (count - 1) / 2);
        return Math.abs(index - middle) * (long) stagger;
    }

    /**
     * 反向波浪收拢延迟：两侧边缘先动（delay = 0），向中心依次递增，中心最后收拢。
     * 采用浮点对称中心 mid = (count - 1) / 2f 与 maxDist = mid，奇偶项下结果均严格非负且左右完全对称。
     */
    private static long reverseWaveDelay(int index, int count, int stagger) {
        if (count <= 1 || stagger <= 0) return 0L;
        float mid = (count - 1) / 2f;
        float maxDist = mid;
        return Math.max(0L, Math.round((maxDist - Math.abs(index - mid)) * stagger));
    }
}
