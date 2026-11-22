package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CircularHoneycombPacking {

    public static class PackPoint {
        public PointF point;
        public float distFromCenter;

        public PackPoint(float x, float y, float dist) {
            this.point = new PointF(x, y);
            this.distFromCenter = dist;
        }
    }

    /**
     * 在给定的圆形区域 (center, regionRadius) 内，生成紧密贴合整个圆形的蜂窝网格坐标
     *
     * @param count         需要的图标数量
     * @param centerX       圆盘中心 X (通常与指纹中心对齐)
     * @param centerY       圆盘中心 Y (指纹中心 - offsetY)
     * @param regionRadius  圆形区域半径
     * @param iconSize      单个图标的基础直径
     * @return 排序好的网格坐标列表 (由内圈向外圈扩散)
     */

    public static List<PointF> calculatePoints(int count, float centerX, float centerY, float regionRadius, float iconSize, float spacingOffset, float density) {
        List<PackPoint> candidates = new ArrayList<>();
        float d = density > 0f ? density : 1f;

        // 间距：图标大小 + 4dp 基础边距 + 紧凑度微调（保底 0.75 * iconSize 防过度重叠）
        float spacing = Math.max(iconSize * 0.75f, iconSize + 4f * d + spacingOffset);
        float rowHeight = spacing * 0.8660254f; // spacing * sqrt(3) / 2

        // 计算最大排查网格步数
        int maxGrid = (int) Math.ceil(regionRadius / rowHeight) + 1;

        for (int r = -maxGrid; r <= maxGrid; r++) {
            float y = r * rowHeight;
            // 奇数行做半个周期的横向交错偏移，形成六边形蜂窝
            float xOffset = (r % 2 != 0) ? (spacing / 2f) : 0f;

            for (int c = -maxGrid; c <= maxGrid; c++) {
                float x = c * spacing + xOffset;
                float dist = (float) Math.hypot(x, y);

                // 几何判定：图标完全收敛在圆盘内部
                if (dist + iconSize / 2f <= regionRadius + 3f * d) {
                    candidates.add(new PackPoint(centerX + x, centerY + y, dist));
                }
            }
        }

        // 关键算法：按距离圆心欧氏距离升序排序，使图标从圆心对称向外饱满填充
        Collections.sort(candidates, new Comparator<PackPoint>() {
            @Override
            public int compare(PackPoint o1, PackPoint o2) {
                return Float.compare(o1.distFromCenter, o2.distFromCenter);
            }
        });

        List<PointF> result = new ArrayList<>();
        int take = Math.min(count, candidates.size());
        for (int i = 0; i < take; i++) {
            result.add(candidates.get(i).point);
        }

        return result;
    }
}
