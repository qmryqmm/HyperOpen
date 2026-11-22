package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * 单圈空心圆环布局：中心保持空心，所有图标沿一圈均匀排布。
 * 与 {@link CircularHoneycombPacking} 共享同一套间距语义。
 */
public final class HollowRingPacking {

    private HollowRingPacking() {}

    public static List<PointF> calculatePoints(int count, float centerX, float centerY,
                                               float regionRadius, float iconSize,
                                               float spacingOffset, float density) {
        List<PointF> result = new ArrayList<>();
        if (count <= 0) return result;

        float d = density > 0f ? density : 1f;
        float spacing = Math.max(iconSize * 0.75f, iconSize + 4f * d + spacingOffset);
        float minRingRadius = count == 1
                ? iconSize * 1.15f
                : spacing / (2f * (float) Math.sin(Math.PI / count));
        minRingRadius = Math.max(minRingRadius, iconSize * 0.9f);

        float maxRingRadius = regionRadius + 3f * d - iconSize / 2f;
        if (maxRingRadius < minRingRadius) maxRingRadius = minRingRadius;
        float ringRadius = Math.min(maxRingRadius, Math.max(minRingRadius, regionRadius * 0.62f));

        float startAngle = (float) (-Math.PI / 2d);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + i * 2d * Math.PI / count;
            result.add(new PointF(
                    centerX + ringRadius * (float) Math.cos(angle),
                    centerY + ringRadius * (float) Math.sin(angle)
            ));
        }
        return result;
    }
}
