package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 弧形扇面布局：图标沿一条 150° 弧带排布（弧顶朝上，覆盖拇指热区），
 * 单弧放不下时自动转双弧（外弧约 62% + 内弧约 38%）。
 * 与 {@link CircularHoneycombPacking} 共享同一套间距语义（iconSpacingOffset 紧凑程度通用生效），
 * 返回点集按距圆心欧氏距离升序排列，保证展开动画节奏一致。
 */
public class ArcFanPacking {

    /** 弧带张角 150° */
    private static final float SPAN = (float) (150.0d * Math.PI / 180.0d);

    public static class PackPoint {
        public PointF point;
        public float distFromCenter;

        public PackPoint(float x, float y, float dist) {
            this.point = new PointF(x, y);
            this.distFromCenter = dist;
        }
    }

    /**
     * 在给定圆形区域 (center, regionRadius) 内生成弧形扇面坐标。
     *
     * @param count         需要的图标数量
     * @param centerX       弧心 X (通常与指纹中心对齐)
     * @param centerY       弧心 Y (指纹中心 - offsetY)
     * @param regionRadius  圆形区域半径 (弧带最大半径由它收边)
     * @param iconSize      单个图标的基础直径
     * @param spacingOffset 紧凑程度偏移量 (与蜂窝布局同一语义)
     * @return 排序好的坐标列表 (由内向外扩散；单弧半径相等时保持左→右顺序)
     */
    public static List<PointF> calculatePoints(int count, float centerX, float centerY,
                                               float regionRadius, float iconSize,
                                               float spacingOffset, float density) {
        List<PointF> result = new ArrayList<>();
        if (count <= 0) return result;

        float d = density > 0f ? density : 1f;
        // 间距语义与蜂窝完全一致：图标大小 + 4dp 基础边距 + 紧凑度微调（保底防重叠）
        float minSpacing = Math.max(iconSize * 0.75f, iconSize + 4f * d + spacingOffset);
        // 弧带可用最大半径：与蜂窝同款 3dp 容差收边，且不超出视界圆 92%
        float rMax = Math.min(regionRadius + 3f * d - iconSize / 2f, regionRadius * 0.92f);
        // 单弧所需半径（弧长近似弦长）；1 个图标退化为顶点
        float rNeed = count > 1 ? (count - 1) * minSpacing / SPAN : iconSize;

        List<PackPoint> pts = new ArrayList<>();
        float aTop = (float) (-Math.PI / 2d);
        if (rNeed <= rMax && count <= 7) {
            // 单弧：整条 150° 弧均分（起始角 -90°-75°，正上方为弧顶）
            float rr = clamp(Math.max(rNeed, iconSize * 0.8f), iconSize * 0.7f, rMax);
            float a0 = aTop - SPAN / 2f;
            for (int i = 0; i < count; i++) {
                float a = count == 1 ? aTop : a0 + i * SPAN / (count - 1);
                pts.add(new PackPoint(centerX + rr * (float) Math.cos(a),
                        centerY + rr * (float) Math.sin(a), rr));
            }
        } else {
            // 双弧：外弧约 62%、内弧约 38%；内弧收窄角度域防边缘重叠
            int outer = (int) Math.ceil(count * 0.62d);
            int inner = count - outer;
            float a0 = aTop - SPAN / 2f;
            for (int i = 0; i < outer; i++) {
                float a = a0 + (outer == 1 ? SPAN / 2f : i * SPAN / (outer - 1));
                pts.add(new PackPoint(centerX + rMax * (float) Math.cos(a),
                        centerY + rMax * (float) Math.sin(a), rMax));
            }
            if (inner > 0) {
                // 内弧半径退让 1.15 倍间距；极端紧凑参数下保底不穿圆心
                float rIn = Math.max(rMax - minSpacing * 1.15f, iconSize * 0.7f);
                for (int i = 0; i < inner; i++) {
                    float a = inner == 1 ? aTop : a0 + 0.12f + i * (SPAN - 0.24f) / (inner - 1);
                    pts.add(new PackPoint(centerX + rIn * (float) Math.cos(a),
                            centerY + rIn * (float) Math.sin(a), rIn));
                }
            }
        }

        // 由内向外排序（同半径稳定排序保持角度顺序），展开动画节奏与蜂窝一致
        Collections.sort(pts, new Comparator<PackPoint>() {
            @Override
            public int compare(PackPoint o1, PackPoint o2) {
                return Float.compare(o1.distFromCenter, o2.distFromCenter);
            }
        });
        for (PackPoint p : pts) result.add(p.point);
        return result;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
