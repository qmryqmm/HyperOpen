package io.github.qmryqmm.hyperopen.util;

/**
 * 气泡毛玻璃模糊规格单一数据源（纯数学，零系统依赖）。
 * <p>
 * 真实 SystemUI 的窗口级 {@code BackgroundBlurDrawable} 与应用内预览的 Compose
 * {@code textureBlur} 斑点是两套渲染机制，无法共用同一个绘制函数；本类把两套渲染
 * 必须一致的**规格**（开关条件、内缩像素、圆角半径公式）收敛为唯一实现，禁止两侧
 * 各自再抄一份公式——历史上斑点直径/内缩/圆角各写一份，任何渲染改动都要人肉同步。
 * <ul>
 *   <li>开关：{@link #isEnabled}（留白 0% = 气泡整体关闭，模糊跟随停用）；</li>
 *   <li>内缩：{@link #INSET_PX}，模糊贴片严格位于描边内侧，绝不溢出边框；</li>
 *   <li>圆角：{@link #cornerRadiusPx}，50% 正圆交由渲染端钳制为半宽，
 *       小于 50% 时按比例并随缩放同步。</li>
 * </ul>
 */
public final class BubbleBlurSpec {

    /** 气泡毛玻璃贴片内缩像素：0 表示无缝铺满气泡边界，由 OutlineProvider 统一实施硬件级圆角裁切 */
    public static final int INSET_PX = 0;

    /**
     * FOD 指纹中心在「真实物理屏幕高度」上的兜底比例（0.795）。
     * 预览侧 calculateFodCoordinates 与 Compose 层圆形底衬兜底共用此值，
     * 保证两处兜底公式不漂移。
     */
    public static final float FOD_CENTER_Y_RATIO = 0.795f;

    private BubbleBlurSpec() {
        // 纯静态工具类
    }

    /**
     * 气泡模糊是否启用：模糊半径与气泡留白均有效（留白 0% = 气泡关闭，模糊跟随停用）。
     *
     * @param blurRadiusDp   气泡留白区域模糊半径 (dp)
     * @param paddingPercent 气泡内部留白比例 (0 ~ 40)
     */
    public static boolean isEnabled(float blurRadiusDp, int paddingPercent) {
        return blurRadiusDp > 0f && paddingPercent > 0;
    }

    /**
     * 模糊贴片基础正圆半径：半宽。
     *
     * @param viewMinSizePx 气泡视图短边尺寸（px）
     */
    public static float coreRadiusPx(float viewMinSizePx) {
        return Math.max(1f, viewMinSizePx / 2f);
    }

    /**
     * 模糊贴片圆角半径（本地几何像素）：
     * 与 ViewOutlineProvider 严格采用完全相同的几何公式，定义在 View 本地未缩放几何空间中：
     * 50% 正圆时返回半宽，25% 圆角矩形时返回半宽的 50%。
     * 缩放由 RenderNode GPU 矩阵整体等比放大，严禁在 Java 层再乘 scale 进行二次放大，
     * 避免破坏三层图层的 1:1 物理几何对应关系。
     *
     * @param viewMinSizePx 气泡视图短边基础尺寸（px，未缩放）
     * @param cornerPercent 图标与气泡圆角比例 (0 ~ 50)
     */
    public static float cornerRadiusPx(float viewMinSizePx, int cornerPercent) {
        float halfWidth = viewMinSizePx / 2f;
        int clampedPercent = Math.max(0, Math.min(50, cornerPercent));
        if (clampedPercent >= 50) {
            return halfWidth;
        }
        float r = halfWidth * (clampedPercent / 50f);
        return Math.min(halfWidth, Math.max(0f, r));
    }

    public static float cornerRadiusPx(float viewMinSizePx, int cornerPercent, float scale) {
        return cornerRadiusPx(viewMinSizePx, cornerPercent);
    }
}
