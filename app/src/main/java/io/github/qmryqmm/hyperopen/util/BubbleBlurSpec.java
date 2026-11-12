package io.github.qmryqmm.hyperopen.util;

/**
 * 气泡毛玻璃模糊规格单一数据源（纯数学，零系统依赖）。
 * <ul>
 *   <li>开关：{@link #isEnabled}（留白 0% = 气泡整体关闭，模糊跟随停用）；</li>
 *   <li>圆角：{@link #cornerRadiusOfHalfSize} / {@link #cornerRadiusPx}——
 *       图标气泡 OutlineProvider、光学层圆角、图标位图裁切、字形底垫与
 *       blur region 圆角全部经由此处换算，禁止任何一侧再抄散装公式。</li>
 * </ul>
 */
public final class BubbleBlurSpec {

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
     * 圆角半径统一公式（本地几何像素）：<b>唯一实现</b>，图标气泡 OutlineProvider、
     * 光学层圆角、图标位图裁切（AppIconCropProcessor）、字形底垫（GlyphDrawable /
     * MiuixVectorDrawable）与模糊贴片圆角全部经由此处换算，禁止任何一侧再各自写
     * {@code halfSize * percent / 50} 散装公式。
     *
     * @param halfSize      目标形状的半宽（px，本地未缩放几何）
     * @param cornerPercent 图标与气泡圆角比例 (0 ~ 50)：50 = 正圆（半宽），0 = 直角
     */
    public static float cornerRadiusOfHalfSize(float halfSize, int cornerPercent) {
        int clampedPercent = Math.max(0, Math.min(50, cornerPercent));
        float r = halfSize * (clampedPercent / 50f);
        return Math.min(halfSize, Math.max(0f, r));
    }

    /**
     * 模糊贴片圆角半径（本地几何像素）：
     * 与 ViewOutlineProvider 严格采用完全相同的几何公式，定义在 View 本地未缩放几何空间中：
     * 50% 正圆时返回半宽，25% 圆角矩形时返回半宽的 50%。
     * 缩放由注册锚点（视图 RenderNode 变换）携带，本地几何不预缩放。
     *
     * @param viewMinSizePx 气泡视图短边尺寸（px）
     */
    public static float cornerRadiusPx(float viewMinSizePx, int cornerPercent) {
        return cornerRadiusOfHalfSize(viewMinSizePx / 2f, cornerPercent);
    }
}
