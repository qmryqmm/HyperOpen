package io.github.qmryqmm.hyperopen.util;

/**
 * 鱼眼缩放动力学纯数学实现（零系统依赖）。
 * <p>
 * 抽成独立类的原因：App 内预览（{@code HyperOpenPreviewView}）与 SystemUI 真实容器
 * （{@code CustomHyperOpenContainer}）必须对「鱼眼最大放大倍率」给出完全一致的缩放曲线，
 * 否则预览与实机手感会出现偏差。
 * <p>
 * 统一语义（所有布局一致，无任何隐式乘法叠加）：
 * <ul>
 *   <li>命中图标：锁定峰值 {@code maxScale}（{@link #peakScale}），与手指距离、感应半径均无关
 *       ——距离型布局中命中判定阈值之外的余弦衰减不再吃掉峰值，扇区布局中扇面延伸到屏幕外
 *       同理；因此「只有命中图标放大」可以通过调小感应辐射范围实现；</li>
 *   <li>邻近图标：{@code 1 + (maxScale - 1) × cosineFactor}（{@link #scaleForDistance}），
 *       用于蜂窝 / 弧形扇面 / 非扇区圆环 / 非扇区系统扇形，以及扇区布局里未命中的邻居图标，
 *       衰减快慢完全由感应辐射半径决定。</li>
 * </ul>
 * 因此任意布局下「命中图标最终倍率 = maxScale」完全一致。
 */
public final class FisheyeScale {

    private FisheyeScale() {
        // 纯静态工具类
    }

    /**
     * 余弦衰减系数：距离 0 → 1.0（峰值），距离 >= radius → 0.0（无放大），中间平滑过渡。
     *
     * @param distancePx 图标中心到手指的距离（px）
     * @param radiusPx   感应辐射半径（px），<= 0 时视为不放大
     */
    public static float cosineFactor(float distancePx, float radiusPx) {
        if (radiusPx <= 0f) return 0f;
        if (distancePx <= 0f) return 1f;
        if (distancePx >= radiusPx) return 0f;
        return (float) Math.cos((distancePx / radiusPx) * (Math.PI / 2.0));
    }

    /**
     * 按距离计算邻近图标的鱼眼放大倍率：{@code 1 + (maxScale - 1) * cosineFactor}。
     *
     * @param distancePx 图标中心到手指的距离（px）
     * @param radiusPx   感应辐射半径（px）
     * @param maxScale   鱼眼最大放大倍率（配置项 max_scale）
     */
    public static float scaleForDistance(float distancePx, float radiusPx, float maxScale) {
        float peak = peakScale(maxScale);
        if (peak <= 1.0f) return 1.0f;
        return 1.0f + (peak - 1.0f) * cosineFactor(distancePx, radiusPx);
    }

    /**
     * 命中项的鱼眼峰值倍率：所有布局下命中图标统一锁定为该值。
     *
     * @param maxScale 鱼眼最大放大倍率（配置项 max_scale）
     */
    public static float peakScale(float maxScale) {
        // 低于 1.0 没有放大语义，兜底为不放大，避免图标被缩小
        return maxScale < 1.0f ? 1.0f : maxScale;
    }
}
