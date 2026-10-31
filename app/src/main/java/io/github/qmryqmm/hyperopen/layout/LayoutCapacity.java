package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.List;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;

/**
 * 布局容量探针：算出给定几何参数下排布引擎实际能容纳的图标数。
 * 与三套 Packing 的几何完全同源（调用方传入与 setupAndExpand/setupLayout 同一 dpToPx 管道的 px 值），
 * 不引入第二套公式，避免容量与实际排布失真。
 */
public class LayoutCapacity {

    /** 「图标数量」滑块的设计上限 (0~15)，容量结果同样钳制在此范围 */
    public static final int MAX_VISIBLE = 15;

    /** 探针数量：远大于 MAX_VISIBLE，保证蜂窝/圆环返回饱和点数 */
    private static final int PROBE_COUNT = 48;

    private LayoutCapacity() {
    }

    /**
     * @param layoutStyle      布局样式 (LAYOUT_HONEYCOMB / LAYOUT_RING / LAYOUT_ARC)
     * @param regionRadiusPx   圆形视界半径 px（与排布调用点的 regionRadiusPx 同源）
     * @param iconSizePx       图标直径 px
     * @param spacingOffsetPx  紧凑程度偏移 px
     * @param ringCount        圆环圈数（仅 LAYOUT_RING 生效）
     * @return 当前几何下最多可容纳的图标数（1 ~ MAX_VISIBLE）
     */
    public static int maxFeasibleCount(int layoutStyle, float regionRadiusPx, float iconSizePx,
                                       float spacingOffsetPx, int ringCount) {
        // 弧形扇面与系统扇形布局无蜂窝拥挤几何上限（系统扇形经典支持 3~5 项，向上兼容至多项），按滑块设计上限处理
        if (layoutStyle == HyperOpenConfig.LAYOUT_ARC || layoutStyle == HyperOpenConfig.LAYOUT_SYSTEM_STOCK) {
            return MAX_VISIBLE;
        }

        List<PointF> pts;
        if (layoutStyle == HyperOpenConfig.LAYOUT_RING) {
            pts = HollowRingPacking.calculatePoints(PROBE_COUNT, 0f, 0f,
                    regionRadiusPx, iconSizePx, spacingOffsetPx, ringCount);
        } else {
            pts = CircularHoneycombPacking.calculatePoints(PROBE_COUNT, 0f, 0f,
                    regionRadiusPx, iconSizePx, spacingOffsetPx);
        }
        int n = pts.size();
        if (n < 1) n = 1;
        return Math.min(n, MAX_VISIBLE);
    }
}
