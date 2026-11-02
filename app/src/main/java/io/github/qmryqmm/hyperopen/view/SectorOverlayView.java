package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * 空心圆环「扇区触发」的全屏分隔线覆盖层。
 * <p>
 * 与圆形底衬分离，铺满整个容器（SystemUI 中即整屏），因此：
 * <ul>
 *   <li>取消区小圆按指纹中心绘制，圆心与扇形几何严格一致；</li>
 *   <li>N 条径向虚线从取消区边界一直延伸到屏幕之外，不受圆形视界半径裁剪；</li>
 *   <li>关闭圆形视界底衬后，屏幕上只剩分隔虚线与图标本身。</li>
 * </ul>
 * 不参与触摸分发（{@link #onTouchEvent} 恒返回 false），命中判定仍由容器按角度完成。
 */
public class SectorOverlayView extends View {

    private boolean enabled = false;
    private int count = 0;
    /** 圆心（容器坐标系，即指纹中心 / 圆形视界中心） */
    private float centerX = 0f;
    private float centerY = 0f;
    /** 取消区半径（px），虚线从此处向外发散射出 */
    private float cancelRadiusPx = 0f;
    /** 第一个扇区的中心角（度，-90 = 正上方） */
    private float startAngleDeg = -90f;

    private int lineColor = 0xFFBFBFBF;
    private float lineAlpha = 0.4f;
    private int dashLengthDp = 6;
    private int dashGapDp = 5;
    private float lineWidthDp = 1.2f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dashLenPx = -1;
    private int dashGapPx = -1;
    private int strokeW100 = -1;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public SectorOverlayView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // 纯装饰层：不吃触摸、不参与点击
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /**
     * 更新扇区分隔线几何与样式。参数全部未变化时不触发重绘。
     *
     * @param enabled       是否绘制
     * @param count         扇区数量（= 图标数量，1 时只画取消区小圆）
     * @param cx            圆心 X（容器坐标）
     * @param cy            圆心 Y（容器坐标）
     * @param cancelRadiusPx 取消区半径（px）
     * @param startAngleDeg 第一个扇区中心角（度，-90 = 正上方）
     */
    public void update(boolean enabled, int count, float cx, float cy, float cancelRadiusPx,
                       float startAngleDeg, int lineColor, float lineAlpha,
                       int dashLengthDp, int dashGapDp, float lineWidthDp) {
        float clampedAlpha = Math.max(0f, Math.min(1f, lineAlpha));
        boolean changed = this.enabled != enabled
                || this.count != count
                || Math.abs(this.centerX - cx) > 0.5f
                || Math.abs(this.centerY - cy) > 0.5f
                || Math.abs(this.cancelRadiusPx - cancelRadiusPx) > 0.5f
                || Math.abs(this.startAngleDeg - startAngleDeg) > 0.01f
                || this.lineColor != lineColor
                || Math.abs(this.lineAlpha - clampedAlpha) > 0.002f
                || this.dashLengthDp != dashLengthDp
                || this.dashGapDp != dashGapDp
                || Math.abs(this.lineWidthDp - lineWidthDp) > 0.01f;

        this.enabled = enabled;
        this.count = count;
        this.centerX = cx;
        this.centerY = cy;
        this.cancelRadiusPx = Math.max(0f, cancelRadiusPx);
        this.startAngleDeg = startAngleDeg;
        this.lineColor = lineColor;
        this.lineAlpha = clampedAlpha;
        this.dashLengthDp = Math.max(1, dashLengthDp);
        this.dashGapDp = Math.max(1, dashGapDp);
        this.lineWidthDp = Math.max(0.3f, lineWidthDp);

        if (changed) {
            invalidate();
        }
    }

    /** 关闭扇区绘制（切换布局、退出扇区模式时调用） */
    public void clear() {
        if (enabled || count != 0) {
            enabled = false;
            count = 0;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w;
            lastHeight = h;
            if (enabled) invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled || count <= 0) return;

        int alphaByte = Math.max(0, Math.min(255, Math.round(lineAlpha * 255f)));
        if (alphaByte == 0) return; // 透明度 0% 等价于完全隐藏
        if (getWidth() <= 0 || getHeight() <= 0) return;

        applyPaintStyle();

        // 1. 取消区小圆（圆心留白，圆内松手不触发）
        if (cancelRadiusPx > 1f) {
            canvas.drawCircle(centerX, centerY, cancelRadiusPx, paint);
        }

        if (count <= 1) return;

        // 2. 径向虚线：从取消区边界一路画到屏幕之外（取屏幕对角线长度，确保任何方向都出屏）
        float reach = (float) Math.hypot(getWidth(), getHeight()) + Math.max(getWidth(), getHeight());
        float stepDeg = 360f / count;
        float boundaryStart = startAngleDeg - stepDeg / 2f;
        for (int i = 0; i < count; i++) {
            double ang = Math.toRadians(boundaryStart + i * stepDeg);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            canvas.drawLine(
                    centerX + cancelRadiusPx * cos,
                    centerY + cancelRadiusPx * sin,
                    centerX + reach * cos,
                    centerY + reach * sin,
                    paint
            );
        }
    }

    /** 虚线规格与线宽按配置重建，仅在参数变化时重新分配 PathEffect 对象 */
    private void applyPaintStyle() {
        int dashLen = Math.max(1, Math.round(dp(dashLengthDp)));
        int dashGap = Math.max(1, Math.round(dp(dashGapDp)));
        int width100 = Math.round(dp(lineWidthDp) * 100f);
        if (dashLen != dashLenPx || dashGap != dashGapPx || width100 != strokeW100) {
            dashLenPx = dashLen;
            dashGapPx = dashGap;
            strokeW100 = width100;
            paint.setPathEffect(new DashPathEffect(new float[]{dashLen, dashGap}, 0f));
            paint.setStrokeWidth(Math.max(0.3f, dp(lineWidthDp)));
        }
        int alphaByte = Math.max(0, Math.min(255, Math.round(lineAlpha * 255f)));
        paint.setColor(Color.argb(alphaByte, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
