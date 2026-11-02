package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * 空心圆环「扇区触发」几何：
 * <p>
 * 快捷方式数量较少（1 ~ 6 个）时，空心圆环布局的中心留白区过大、图标稀疏，鱼眼欧氏距离判定
 * 容易误触。本类把整个圆形视界改造成「大圆套小圆」的扇区结构：
 * <ul>
 *   <li>图标沿半径 R 的圆周均分排布，R 同时也是触发距离（图标阵列围成的圆）；</li>
 *   <li>该圆以内为取消返回区（手指在中心留白区松手不触发任何动作）；</li>
 *   <li>该圆以外按图标数量均分为 N 个扇形，每个扇形对应一个图标，靠角度判定命中；</li>
 *   <li>扇区分隔线由视图层以淡灰色虚线绘制（本类只提供几何参数）。</li>
 * </ul>
 * 纯数学实现，SystemUI 真实面板与 App 内预览共用同一套几何，保证 1:1 一致。
 */
public class RingSectorPacking {

    public static class Result {
        /** 图标中心坐标（按扇区角度顺序排列，第 i 个对应第 i 个扇区） */
        public final List<PointF> points;
        /** 图标中心所在圆半径（= 触发距离半径 R） */
        public final float ringRadius;
        /** 取消区半径（图标阵列内边界 = R - iconSize/2，圆内松手不触发） */
        public final float cancelRadius;
        /** 第一个图标的中心角（度，-90 = 正上方），扇区以它为中心展开 */
        public final float startAngleDeg;
        /** 扇区数量（= 图标数量） */
        public final int count;

        public Result(List<PointF> points, float ringRadius, float cancelRadius,
                      float startAngleDeg, int count) {
            this.points = points;
            this.ringRadius = ringRadius;
            this.cancelRadius = cancelRadius;
            this.startAngleDeg = startAngleDeg;
            this.count = count;
        }
    }

    /** 扇区触发模式支持的快捷方式数量上限（不含）：1 ~ 6 个支持扇区，7 个及以上回退常规空心圆环布局 */
    public static final int MAX_SECTOR_COUNT_EXCLUSIVE = 7;

    /**
     * 计算扇区触发布局。
     *
     * @param count         图标数量（1 ~ 6）
     * @param cx            圆心 X（通常为指纹中心 X）
     * @param cy            圆心 Y（指纹中心 - offsetY）
     * @param regionRadius  圆形视界半径（扇区向外延伸的边界）
     * @param iconSize      单个图标直径
     * @param spacingOffset 紧凑度偏移量（与蜂窝 / 空心圆环布局同一语义）
     * @return 扇区几何规格；数量为 0 时返回 null
     */
    public static Result calculate(int count, float cx, float cy,
                                   float regionRadius, float iconSize, float spacingOffset) {
        if (count <= 0) return null;

        // 间距语义与空心圆环 / 蜂窝完全一致：图标大小 + 基础边距 + 紧凑度微调
        float spacing = Math.max(iconSize * 0.75f, iconSize + 12f + spacingOffset);

        // 图标中心圆半径：圆周上相邻图标弦长 >= spacing 的最小半径
        float minRingRadius;
        if (count == 1) {
            minRingRadius = iconSize * 1.15f;
        } else {
            minRingRadius = spacing / (2f * (float) Math.sin(Math.PI / count));
            minRingRadius = Math.max(minRingRadius, iconSize * 0.95f);
        }

        // 视觉收边：图标不贴着视界边缘，也不挤在正中心——
        // 取「不重叠所需的最小半径」与「视界半径的 62%」中较大者，
        // 让中心取消区足够大（滑出后才触发），外侧也留出可感知的扇区区域。
        float ringRadius = Math.max(minRingRadius, regionRadius * 0.62f);

        // 不超出圆形视界：图标外缘需收在视界内
        float maxRadius = regionRadius - iconSize * 0.5f - 8f;
        if (maxRadius > 0 && ringRadius > maxRadius) {
            ringRadius = maxRadius;
        }
        // 兜底：至少保证图标内边界为正
        ringRadius = Math.max(ringRadius, iconSize * 0.60f);

        // 取消区 = 图标阵列内边界（圆内松手不触发任何快捷方式）
        float cancelRadius = Math.max(0f, ringRadius - iconSize * 0.5f);

        // 第一个图标位于正上方（-90°），其余按 360/N 均分
        float startAngleDeg = -90f;
        float stepDeg = 360f / count;

        List<PointF> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double ang = Math.toRadians(startAngleDeg + i * stepDeg);
            float x = cx + ringRadius * (float) Math.cos(ang);
            float y = cy + ringRadius * (float) Math.sin(ang);
            points.add(new PointF(x, y));
        }

        return new Result(points, ringRadius, cancelRadius, startAngleDeg, count);
    }

    /**
     * 判定当前触摸点对应的快捷项索引。
     *
     * @return 命中的扇区索引；位于取消区（中心留白）返回 -1
     */
    public static int findTouchedIndex(Result spec, float touchX, float touchY,
                                       float cx, float cy) {
        if (spec == null || spec.count <= 0) return -1;

        float dx = touchX - cx;
        float dy = touchY - cy;
        float dist = (float) Math.hypot(dx, dy);

        // 取消区：图标阵列围成的圆以内，松手返回不触发
        if (dist < spec.cancelRadius) return -1;

        if (spec.count == 1) return 0;

        float stepDeg = 360f / spec.count;
        float touchAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        // 归一到 [0, 360) 相对第一个图标中心角的偏差，再四舍五入到最近的扇区中心
        float diff = (touchAngleDeg - spec.startAngleDeg) % 360f;
        if (diff < 0f) diff += 360f;
        int index = Math.round(diff / stepDeg) % spec.count;
        if (index < 0) index += spec.count;
        return index;
    }

    /**
     * 扇区分隔线的角度起点（度）：第 i 条分隔线位于第 i 个扇区的左边界。
     * 绘制虚线时使用，与 {@link #findTouchedIndex} 的扇区划分严格一致。
     */
    public static float sectorBoundaryAngleDeg(Result spec, int index) {
        if (spec == null || spec.count <= 0) return 0f;
        float stepDeg = 360f / spec.count;
        return spec.startAngleDeg - stepDeg / 2f + index * stepDeg;
    }
}
