package io.github.qmryqmm.hyperopen.layout;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * MIUI / HyperOS 系统原始指纹快捷方式扇形排布与几何扇区生成器。
 * <p>
 * 纯数学实现，完全复刻从系统界面 (MiuiGxzwQuickOpenView.startShowQuickOpenItemAnimation) 逆向出的
 * 原生扇面布局坐标与 Path 差集 Region 扇形区域判定。
 * 零系统私有类依赖，保证在所有 Android 版本、预览界面与 SystemUI 容器中 1:1 绝对一致。
 */
public class StockFanPacking {

    public static class StockItemSpec {
        /** 图标中心点坐标 (展开后的静态位置) */
        public final PointF center;
        /** 图标自身的矩形区域 (以 center 为中心，长宽为 itemSize) */
        public final RectF itemRect;
        /** 对应的扇形多边形判定区域 (由大圆弧与小圆弧求差集构建) */
        public final Region region;
        /** 扇区中心角度（弧度制） */
        public final float angleRad;
        /** 扇区中心角度（角度制） */
        public final float angleDeg;

        public StockItemSpec(PointF center, RectF itemRect, Region region, float angleRad, float angleDeg) {
            this.center = center;
            this.itemRect = itemRect;
            this.region = region;
            this.angleRad = angleRad;
            this.angleDeg = angleDeg;
        }
    }

    /**
     * 计算系统原生扇形布局参数与每个 Item 的几何规格。
     *
     * @param count      图标数量 (系统原生支持 3~5 项，模块向上兼容到 7 项)
     * @param fodX       指纹中心 X 坐标
     * @param fodY       指纹中心 Y 坐标
     * @param itemRadius 图标扩散半径 (从指纹中心到图标中心的距离，系统原生约为 230~250px)
     * @param itemSize   单个图标的直径尺寸
     * @return 每个快捷项对应的规格列表 (包含中心坐标、外接矩形与扇形判定 Region)
     */
    public static List<StockItemSpec> calculate(int count, float fodX, float fodY, float itemRadius, float itemSize) {
        List<StockItemSpec> result = new ArrayList<>();
        if (count <= 0) return result;

        // 系统原生逻辑：
        // 5 个图标时，每个跨度 45 度；3 或 4 个图标时，每个跨度 60 度；其他数量按 180° / (count + 1) 或对称微调
        float angleStep;
        if (count == 5) {
            angleStep = 45.0f;
        } else if (count == 3 || count == 4) {
            angleStep = 60.0f;
        } else if (count <= 2) {
            angleStep = 60.0f;
        } else {
            // > 5 项：自动均分 180° 上半圆
            angleStep = 180.0f / (count + 1);
        }

        // 系统原生尺寸定义：
        // gxzw_quick_open_region_big = 400.0dp（直径 800dp），即从指纹中心往外延伸 400dp，完全覆盖大半个屏幕！
        // gxzw_quick_open_region_samll = 62.0dp（直径 124dp），手指滑出指纹核心区后即视为进入快捷方式扇区。
        // 此处 bigRadius 设定为至少 1200dp 级别（保证全屏高度范围均能命中），对齐系统原生全屏扇区判定行为。
        float bigRadius = Math.max(itemRadius * 4.0f, 2500f);
        float smallRadius = Math.max(itemSize * 0.45f, 50f);

        RectF bigArcRect = new RectF(fodX - bigRadius, fodY - bigRadius, fodX + bigRadius, fodY + bigRadius);
        RectF smallArcRect = new RectF(fodX - smallRadius, fodY - smallRadius, fodX + smallRadius, fodY + smallRadius);

        // Region 全局裁剪多边形边界（涵盖整个屏幕上半部分，即使滑到屏幕顶部依然命中）
        Region clipRegion = new Region(
            (int) (fodX - bigRadius),
            (int) (fodY - bigRadius),
            (int) (fodX + bigRadius),
            (int) (fodY + bigRadius)
        );

        // 系统原生起始角公式：
        // totalSpan = (count - 1) * angleStep
        // startAngle = -180.0f + (180.0f - totalSpan) / 2.0f
        // 这样扇面始终以正上方 (-90°) 为对称轴向上对称展开
        float totalSpan = (count - 1) * angleStep;
        float startAngle = -180.0f + (180.0f - totalSpan) / 2.0f;
        // 扇区边界的角度起点：比图标中心往左退半个角度步长
        float regionStartAngle = -180.0f + (180.0f - count * angleStep) / 2.0f;

        float halfSize = itemSize / 2.0f;

        for (int i = 0; i < count; i++) {
            float curAngleDeg = startAngle + i * angleStep;
            float curAngleRad = (float) Math.toRadians(curAngleDeg);

            // 1. 图标中心坐标：以指纹为圆心极坐标计算
            float cx = fodX + itemRadius * (float) Math.cos(curAngleRad);
            float cy = fodY + itemRadius * (float) Math.sin(curAngleRad);
            PointF center = new PointF(cx, cy);

            RectF itemRect = new RectF(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize);

            // 2. 扇形 Region 差集构建 (100% 还原系统原生 Path.arcTo + Path.op(DIFFERENCE))
            float sectorStartAngle = regionStartAngle + i * angleStep;
            Path outerPath = new Path();
            Path innerPath = new Path();

            outerPath.moveTo(fodX, fodY);
            innerPath.moveTo(fodX, fodY);

            outerPath.arcTo(bigArcRect, sectorStartAngle, angleStep, false);
            innerPath.arcTo(smallArcRect, sectorStartAngle, angleStep, false);

            outerPath.close();
            innerPath.close();

            outerPath.op(innerPath, Path.Op.DIFFERENCE);

            Region sectorRegion = new Region();
            sectorRegion.setPath(outerPath, clipRegion);

            result.add(new StockItemSpec(center, itemRect, sectorRegion, curAngleRad, curAngleDeg));
        }

        return result;
    }

    /**
     * 针对只要求提供中心坐标列表的通用接口（方便与现有 Packing 管道无缝对接）
     */
    public static List<PointF> calculatePoints(int count, float fodX, float fodY, float itemRadius, float itemSize) {
        List<StockItemSpec> specs = calculate(count, fodX, fodY, itemRadius, itemSize);
        List<PointF> pts = new ArrayList<>(specs.size());
        for (StockItemSpec spec : specs) {
            pts.add(spec.center);
        }
        return pts;
    }

    /**
     * 根据当前手指的绝对位置，判定落在哪个扇区中 (优先系统 Region 判定)。
     *
     * @param specs 系统规格列表
     * @param x     当前手指 X
     * @param y     当前手指 Y
     * @param fodX  指纹中心 X
     * @param fodY  指纹中心 Y
     * @return 选中的索引（-1 为未选中任何项）
     */
    public static int findTouchedIndex(List<StockItemSpec> specs, float x, float y, float fodX, float fodY) {
        if (specs == null || specs.isEmpty()) return -1;

        int ix = (int) x;
        int iy = (int) y;

        // 1. 优先使用系统原生 Region 差集判定（只要落在向外发散的扇区多边形内，即刻选中）
        for (int i = 0; i < specs.size(); i++) {
            StockItemSpec spec = specs.get(i);
            if (spec.region != null && spec.region.contains(ix, iy)) {
                return i;
            }
        }

        // 2. 角度射线判定（对齐系统原生无边界手势：只要离开指纹中心一段距离，按当前角度所在的扇区角范围直接命中）：
        // 即使手指滑动到屏幕最顶端、边缘甚至超出任何 Region 边界，依然能准确判定对应的快捷项！
        float dx = x - fodX;
        float dy = y - fodY;
        float distFromFod = (float) Math.hypot(dx, dy);
        if (distFromFod >= 40f && dy <= 50f) {
            // 计算当前手指相对于指纹中心的角度（-180° ~ 180°）
            float touchAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
            int bestIdx = -1;
            float minAngleDiff = Float.MAX_VALUE;
            for (int i = 0; i < specs.size(); i++) {
                StockItemSpec spec = specs.get(i);
                float diff = Math.abs(touchAngleDeg - spec.angleDeg);
                if (diff > 180f) diff = 360f - diff;
                if (diff < minAngleDiff) {
                    minAngleDiff = diff;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                return bestIdx;
            }
        }

        return -1;
    }
}
