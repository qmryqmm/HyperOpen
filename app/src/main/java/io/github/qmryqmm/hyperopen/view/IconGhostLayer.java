package io.github.qmryqmm.hyperopen.view;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 图标幻影层（逐气泡「除自身外」录制）。
 * <p>
 * <b>解决的问题：</b>系统级 BackgroundBlurDrawable 只采样窗口后方的壁纸——气泡互相
 * 压盖时（鱼眼放大 / 文件夹展开），被压住的邻居图标会被模糊壁纸整块「替换」，
 * 而不是像真玻璃那样隔着磨砂虚化透出，玻璃质感断裂。
 * <p>
 * <b>做法（方案 B）：</b>每个气泡绘制玻璃层时，把「除自身外」兄弟气泡的完整视觉
 * （雾面 + 描边 + 图标，经 {@link CircularBubbleView#drawVisualForGhost}，刻意不含
 * 幻影层与背景模糊）按容器真实坐标 / 真实大小录制成 {@link RenderNode}，施加
 * {@link RenderEffect} 高斯模糊后采样进自己的玻璃层（画布反鱼眼变换，见
 * {@code GhostView}）——放大时透过玻璃看到的邻居是真实位置、真实大小的磨砂虚影。
 * <ul>
 *   <li>排除自身 → 无自影、无双重雾面叠加；</li>
 *   <li>录制含邻居雾面 → 邻居透过玻璃时保留自己的磨砂质感；</li>
 *   <li>节点尺寸覆盖「气泡 × 最大鱼眼倍率」足迹，放大过程中采样区永不缺内容；</li>
 *   <li>录制时按 AABB 剔除不相交兄弟，通常每次仅 2~4 次绘制；</li>
 *   <li>API 基线 33（与 AGSL 光学层同基线，目标三代系统界面 API 35/36/37 全覆盖）。</li>
 * </ul>
 * 无递归风险：{@link CircularBubbleView#drawVisualForGhost} 只画光学层与图标，
 * 不含幻影视图，幻影节点之间不会互相嵌套引用。
 */
public final class IconGhostLayer {

    /** 足迹余量系数：覆盖入场 OvershootInterpolator 等超出 maxScale 的瞬时放大 */
    private static final float FOOTPRINT_MARGIN = 1.2f;

    @NonNull
    private final List<CircularBubbleView> mainBubbles;
    @NonNull
    private final List<CircularBubbleView> subBubbles;
    private float maxScale;
    private float blurRadiusPx;
    private float effectRadiusPx = -1f;
    private RenderEffect blurEffect;

    /**
     * @param mainBubbles 容器的一级气泡列表（传<b>活引用</b>，内容变化自动可见）
     * @param subBubbles  文件夹二级子气泡列表（同上，可为空列表）
     * @param maxScale    鱼眼最大倍率（足迹尺寸依据）
     */
    public IconGhostLayer(@NonNull List<CircularBubbleView> mainBubbles, @NonNull List<CircularBubbleView> subBubbles, float maxScale) {
        this.mainBubbles = mainBubbles;
        this.subBubbles = subBubbles;
        this.maxScale = Math.max(1f, maxScale);
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = Math.max(1f, maxScale);
    }

    /** 幻影模糊半径（px）；<=0 时不录制（无模糊即无玻璃虚影语义） */
    public void setBlurRadiusPx(float blurRadiusPx) {
        this.blurRadiusPx = Math.max(0f, blurRadiusPx);
    }

    /**
     * 在目标气泡的幻影视图 onDraw 中调用：录制「除自身外」幻影并以高斯模糊绘出。
     * 录制与绘制同帧同步，无失效传播问题。
     */
    public void drawGhost(@NonNull Canvas canvas, @NonNull CircularBubbleView target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (blurRadiusPx <= 0f) return;
        int w = target.getWidth();
        int h = target.getHeight();
        if (w <= 0 || h <= 0) return;

        int size = Math.max(2, (int) Math.ceil(Math.max(w, h) * maxScale * FOOTPRINT_MARGIN));
        RenderNode node = target.ghostNode;
        if (node == null || target.ghostSizePx != size) {
            node = new RenderNode("HyperOpenIconGhost");
            target.ghostNode = node;
            target.ghostSizePx = size;
        }

        recordExcluding(node, size, target);

        if (blurEffect == null || Math.abs(blurRadiusPx - effectRadiusPx) > 0.5f) {
            effectRadiusPx = blurRadiusPx;
            blurEffect = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(blurEffect);
        canvas.drawRenderNode(node);
    }

    /** 录制「除目标外」兄弟气泡的完整视觉；采样区 = 目标视觉中心周围 size × size */
    private void recordExcluding(@NonNull RenderNode node, int size, @NonNull CircularBubbleView target) {
        Canvas rec = node.beginRecording(size, size);
        float half = size / 2f;
        // 目标气泡视觉中心（scale 以视图中心为轴，translation 计入后即视觉中心）
        float cx = target.getX() + target.getWidth() / 2f;
        float cy = target.getY() + target.getHeight() / 2f;
        // 容器坐标 → 幻影节点坐标：中心对中心
        rec.translate(half - cx, half - cy);

        drawAllExcept(rec, mainBubbles, target, cx, cy, half);
        drawAllExcept(rec, subBubbles, target, cx, cy, half);
        node.endRecording();
    }

    private void drawAllExcept(@NonNull Canvas rec, @NonNull List<CircularBubbleView> list,
                               @NonNull CircularBubbleView target, float cx, float cy, float half) {
        for (int i = 0, n = list.size(); i < n; i++) {
            CircularBubbleView o = list.get(i);
            if (o == target || o.getVisibility() != android.view.View.VISIBLE) continue;
            float alpha = o.getAlpha();
            if (alpha <= 0.002f) continue;

            int ow = o.getWidth();
            int oh = o.getHeight();
            if (ow <= 0 || oh <= 0) continue;
            float sx = Math.max(0.1f, o.getScaleX());
            float sy = Math.max(0.1f, o.getScaleY());
            float ocx = o.getX() + ow / 2f;
            float ocy = o.getY() + oh / 2f;
            // AABB 剔除：缩放足迹与采样区不相交的兄弟不录制
            float fw = ow * sx / 2f + half;
            float fh = oh * sy / 2f + half;
            if (Math.abs(ocx - cx) > fw || Math.abs(ocy - cy) > fh) continue;

            rec.save();
            // 以中心为轴的缩放 + 平移（与 RenderNode setScaleX/Y 的视觉几何一致）
            rec.translate(ocx, ocy);
            rec.scale(sx, sy);
            rec.translate(-ow / 2f, -oh / 2f);
            if (alpha < 0.998f) {
                rec.saveLayerAlpha(0, 0, ow, oh, Math.round(alpha * 255f));
                o.drawVisualForGhost(rec);
                rec.restore();
            } else {
                o.drawVisualForGhost(rec);
            }
            rec.restore();
        }
    }
}
