package io.github.qmryqmm.hyperopen.view;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 图标幻影层：把被当前气泡压住的兄弟图标绘制到气泡玻璃中。
 * <p>
 * 每个气泡使用独立 RenderNode 录制相邻图标，经高斯模糊后绘制。
 * 最终圆角裁切由 CircularBubbleView 根层 Outline 完成。
 */
public final class IconGhostLayer {

    private static final float FOOTPRINT_MARGIN = 1.2f;

    @NonNull
    private final List<CircularBubbleView> mainBubbles;
    @NonNull
    private final List<CircularBubbleView> subBubbles;
    private float maxScale;
    private float blurRadiusPx;
    private float effectRadiusPx = -1f;
    private RenderEffect blurEffect;
    private CircularBubbleView excludedSource;

    public IconGhostLayer(@NonNull List<CircularBubbleView> mainBubbles,
                          @NonNull List<CircularBubbleView> subBubbles,
                          float maxScale) {
        this.mainBubbles = mainBubbles;
        this.subBubbles = subBubbles;
        this.maxScale = Math.max(1f, maxScale);
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = Math.max(1f, maxScale);
    }

    public void setBlurRadiusPx(float blurRadiusPx) {
        this.blurRadiusPx = Math.max(0f, blurRadiusPx);
    }

    public float getBlurRadiusPx() {
        return blurRadiusPx;
    }

    public void setExcludedSource(@Nullable CircularBubbleView source) {
        excludedSource = source;
    }

    public void drawDirect(@NonNull Canvas canvas, @NonNull CircularBubbleView target, float targetScale) {
        int targetWidth = target.getWidth();
        int targetHeight = target.getHeight();
        if (targetWidth <= 0 || targetHeight <= 0) return;

        float centerX = target.getX() + targetWidth / 2f;
        float centerY = target.getY() + targetHeight / 2f;
        float targetRadius = Math.max(targetWidth, targetHeight) / 2f * targetScale;

        drawDirectList(canvas, mainBubbles, target, centerX, centerY, targetRadius);
        drawDirectList(canvas, subBubbles, target, centerX, centerY, targetRadius);
    }

    private void drawDirectList(@NonNull Canvas canvas,
                                @NonNull List<CircularBubbleView> list,
                                @NonNull CircularBubbleView target,
                                float centerX,
                                float centerY,
                                float targetRadius) {
        float targetZ = target.getTranslationZ();
        for (int index = 0, count = list.size(); index < count; index++) {
            CircularBubbleView neighbor = list.get(index);
            if (!shouldDrawNeighbor(neighbor, target, targetZ)) continue;

            int neighborWidth = neighbor.getWidth();
            int neighborHeight = neighbor.getHeight();
            if (neighborWidth <= 0 || neighborHeight <= 0) continue;

            float scale = Math.max(0.1f, neighbor.getScaleX());
            float neighborX = neighbor.getX() + neighborWidth / 2f;
            float neighborY = neighbor.getY() + neighborHeight / 2f;
            float neighborRadius = Math.max(neighborWidth, neighborHeight) / 2f * scale;
            float distance = (float) Math.hypot(neighborX - centerX, neighborY - centerY);
            if (distance > targetRadius + neighborRadius) continue;

            canvas.save();
            canvas.translate(neighborX - centerX, neighborY - centerY);
            canvas.scale(scale, scale);
            canvas.translate(-neighborWidth / 2f, -neighborHeight / 2f);
            drawVisualWithAlpha(canvas, neighbor);
            canvas.restore();
        }
    }

    public void drawGhost(@NonNull Canvas canvas, @NonNull CircularBubbleView target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || blurRadiusPx <= 0f) return;
        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0) return;

        int size = footprintSizePx(target);
        RenderNode node = target.ghostNode;
        if (node == null || target.ghostSizePx != size) {
            node = new RenderNode("HyperOpenIconGhost");
            node.setPosition(0, 0, size, size);
            target.ghostNode = node;
            target.ghostSizePx = size;
        }

        recordExcluding(node, size, target);
        float effectiveBlur = Math.max(20f, blurRadiusPx);
        if (blurEffect == null || Math.abs(effectiveBlur - effectRadiusPx) > 0.5f) {
            effectRadiusPx = effectiveBlur;
            blurEffect = RenderEffect.createBlurEffect(effectiveBlur, effectiveBlur, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(blurEffect);
        node.setAlpha(0.65f);
        canvas.save();
        canvas.translate(-size / 2f, -size / 2f);
        canvas.drawRenderNode(node);
        canvas.restore();
    }

    private int footprintSizePx(@NonNull CircularBubbleView target) {
        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0) return 2;
        int padding = (int) Math.ceil(blurRadiusPx * 2f);
        return Math.max(2, (int) Math.ceil(Math.max(width, height) * maxScale * FOOTPRINT_MARGIN) + padding);
    }

    private void recordExcluding(@NonNull RenderNode node, int size, @NonNull CircularBubbleView target) {
        Canvas recording = node.beginRecording(size, size);
        float half = size / 2f;
        float centerX = target.getX() + target.getWidth() / 2f;
        float centerY = target.getY() + target.getHeight() / 2f;
        recording.translate(half - centerX, half - centerY);
        drawAllExcept(recording, mainBubbles, target, centerX, centerY, half);
        drawAllExcept(recording, subBubbles, target, centerX, centerY, half);
        node.endRecording();
    }

    private void drawAllExcept(@NonNull Canvas canvas,
                               @NonNull List<CircularBubbleView> list,
                               @NonNull CircularBubbleView target,
                               float centerX,
                               float centerY,
                               float half) {
        float targetZ = target.getTranslationZ();
        for (int index = 0, count = list.size(); index < count; index++) {
            CircularBubbleView neighbor = list.get(index);
            if (!shouldDrawNeighbor(neighbor, target, targetZ)) continue;

            int neighborWidth = neighbor.getWidth();
            int neighborHeight = neighbor.getHeight();
            if (neighborWidth <= 0 || neighborHeight <= 0) continue;

            float scaleX = Math.max(0.1f, neighbor.getScaleX());
            float scaleY = Math.max(0.1f, neighbor.getScaleY());
            float neighborX = neighbor.getX() + neighborWidth / 2f;
            float neighborY = neighbor.getY() + neighborHeight / 2f;
            float footprintWidth = neighborWidth * scaleX / 2f + half;
            float footprintHeight = neighborHeight * scaleY / 2f + half;
            if (Math.abs(neighborX - centerX) > footprintWidth
                    || Math.abs(neighborY - centerY) > footprintHeight) {
                continue;
            }

            canvas.save();
            canvas.translate(neighborX, neighborY);
            canvas.scale(scaleX, scaleY);
            canvas.translate(-neighborWidth / 2f, -neighborHeight / 2f);
            drawVisualWithAlpha(canvas, neighbor);
            canvas.restore();
        }
    }

    private boolean shouldDrawNeighbor(@NonNull CircularBubbleView neighbor,
                                       @NonNull CircularBubbleView target,
                                       float targetZ) {
        return neighbor != target
                && !(neighbor == excludedSource && target.isSubBubbleView())
                && neighbor.getVisibility() == android.view.View.VISIBLE
                && neighbor.getTranslationZ() < targetZ
                && neighbor.getAlpha() > 0.002f;
    }

    private void drawVisualWithAlpha(@NonNull Canvas canvas, @NonNull CircularBubbleView neighbor) {
        float alpha = neighbor.getAlpha();
        if (alpha < 0.998f) {
            canvas.saveLayerAlpha(0, 0, neighbor.getWidth(), neighbor.getHeight(),
                    Math.round(alpha * 255f));
            neighbor.drawVisualForGhost(canvas);
            canvas.restore();
        } else {
            neighbor.drawVisualForGhost(canvas);
        }
    }
}
