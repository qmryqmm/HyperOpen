package io.github.qmryqmm.hyperopen.view;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.qmryqmm.hyperopen.config.HyperOpenConfig;
import io.github.qmryqmm.hyperopen.factory.HyperOSMaterialFactory;
import io.github.qmryqmm.hyperopen.model.ShortcutItem;
import io.github.qmryqmm.hyperopen.util.BlurRegionSession;
import io.github.qmryqmm.hyperopen.util.BubbleBlurSpec;

public class CircularBubbleView extends FrameLayout {

    public final ShortcutItem item;
    public float targetX;
    public float targetY;
    public float currentScale = 1.0f;
    private float fisheyeScale = 1.0f;
    private boolean isSelected = false;

    private int activeStyle;
    private final int sizePx;
    private float blurRadiusDp;
    private int paddingPercent;
    /** 气泡留白区域压暗原始值（气泡关闭时实际应用为 0） */
    private float dimAlpha;
    private int cornerRadiusPercent;
    /** 内部配置快照：热更新时经 {@link #updateConfig} 整体替换，避免停留在旧参数 */
    private HyperOpenConfig config;
    /** 上次应用的配置签名（updateConfig 变更检测） */
    private long lastConfigSignature;
    private final ImageView iconView;
    /** 光学层承载 View（背景 = CircularBubbleDrawable）：blur drawable 必须独占本 View 的背景槽 */
    private final View opticView;

    @NonNull
    private final HyperOSMaterialFactory.CircularBubbleDrawable opticDrawable;
    /** 系统级背景模糊统一会话（与圆形视界底衬 / 顶部提示胶囊共用同一生命周期实现） */
    @NonNull
    private final BlurRegionSession blurSession = new BlurRegionSession();
    private boolean systemBlurEnabled = true;

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader, HyperOpenConfig config) {
        super(context);
        this.item = item;
        this.activeStyle = style;
        this.sizePx = sizePx;
        this.config = config;
        // 材质参数统一走 HyperOpenConfig 组件级解析（柔光磨砂浓度驱动模糊半径、跟随全局等）
        this.blurRadiusDp = config != null ? config.effectiveBlurRadiusDp(HyperOpenConfig.COMPONENT_BUBBLE) : 25f;
        this.paddingPercent = config != null ? config.bubblePaddingPercent : 18;
        this.cornerRadiusPercent = config != null ? Math.max(0, Math.min(50, config.iconCornerRadius)) : 50;

        this.opticDrawable = new HyperOSMaterialFactory.CircularBubbleDrawable(
            activeStyle, item.tintColor, isSelected
        );
        this.opticDrawable.setGlassParams(glassParams());
        this.dimAlpha = config != null ? config.effectiveDimAlpha(HyperOpenConfig.COMPONENT_BUBBLE) : 0f;
        this.opticDrawable.setDimAlpha(this.dimAlpha);
        this.opticDrawable.setCornerRadiusPercent(this.cornerRadiusPercent);

        setLayoutParams(new LayoutParams(sizePx, sizePx));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth() > 0 ? view.getWidth() : sizePx;
                int h = view.getHeight() > 0 ? view.getHeight() : sizePx;
                float maxR = Math.min(w, h) / 2f;
                if (maxR <= 0f) return;
                // 圆角比例公式与 BubbleBlurSpec / 光学层 / 图标裁切严格同一实现
                float corner = BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
                outline.setRoundRect(0, 0, w, h, corner);
            }
        });
        setClipToOutline(true);
        setClipChildren(false);
        setClipToPadding(false);

        // 光学层承载 View：背景挂 CircularBubbleDrawable（与圆形视界底衬同一光学实现）。
        // blur drawable 需要独占本 View 的背景槽——系统界面 BackgroundBlurDrawable 的
        // 注册链路由 view.setBackground 驱动（见 BlurRegionSession 契约注释）
        opticView = new View(context);
        opticView.setBackground(opticDrawable);
        LayoutParams opticLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        opticLp.gravity = Gravity.CENTER;
        addView(opticView, opticLp);

        // 应用与圆形视界底衬统一的光学材质与背景模糊
        refreshBackground();

        // 中心图标
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        applyIconPadding();

        LayoutParams iconLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iconLp.gravity = Gravity.CENTER;
        addView(iconView, iconLp);

        // 应用图标透明度
        float initAlpha = config != null ? config.iconAlpha : 1.0f;
        iconView.setAlpha(initAlpha);
        opticDrawable.setAlpha(Math.round(Math.max(0f, Math.min(1f, initAlpha)) * 255f));

        // 绑定图标 (优先从包名解析应用图标，或使用默认彩色矢量图形)
        loadItemIcon();

        this.lastConfigSignature = config != null ? configSignature(config) : 0L;
    }

    public CircularBubbleView(@NonNull Context context, ShortcutItem item, int style, int sizePx, ClassLoader classLoader) {
        this(context, item, style, sizePx, classLoader, null);
    }

    /**
     * 配置热更新：替换内部配置快照并整体刷新。
     * 图标解析（裁切/底垫）、玻璃光学参数、模糊与压暗全部按新配置重新取值，
     * 消除「复用气泡持有旧配置快照导致材质参数热更新不生效」的问题
     * （真机 setupBubbles 复用分支与预览热更新链共用本入口）。
     * <p>
     * 以 {@link #configSignature} 做变更检测：Compose AndroidView 的 update 块
     * 每次重组都会调用本方法，参数未变时整链短路，避免图标重建与模糊反射更新开销。
     */
    public void updateConfig(@NonNull HyperOpenConfig newConfig) {
        long sig = configSignature(newConfig);
        this.config = newConfig;
        if (sig == lastConfigSignature) {
            return;
        }
        lastConfigSignature = sig;
        this.activeStyle = HyperOSMaterialFactory.resolveActiveStyle(getContext(), newConfig.materialStyle);
        this.blurRadiusDp = newConfig.effectiveBlurRadiusDp(HyperOpenConfig.COMPONENT_BUBBLE);
        this.paddingPercent = newConfig.bubblePaddingPercent;
        this.dimAlpha = newConfig.effectiveDimAlpha(HyperOpenConfig.COMPONENT_BUBBLE);
        this.cornerRadiusPercent = Math.max(0, Math.min(50, newConfig.iconCornerRadius));
        opticDrawable.setStyle(activeStyle);
        opticDrawable.setCornerRadiusPercent(this.cornerRadiusPercent);
        applyIconPadding();
        loadItemIcon();
        setIconAlpha(newConfig.iconAlpha);
        ensureBlurAndRefreshBackground();
    }

    /** 影响本气泡渲染的配置字段签名（模糊/压暗/磨砂/玻璃/留白/圆角/透明度/图标裁切与底垫） */
    private static long configSignature(@NonNull HyperOpenConfig c) {
        long h = 17;
        h = h * 31 + c.materialStyle;
        h = h * 31 + c.bubbleBlurRadius;
        h = h * 31 + Float.floatToIntBits(c.bubbleDimAlpha);
        h = h * 31 + (c.bubbleMaterialFollowGlobal ? 1 : 0);
        h = h * 31 + c.globalBlurRadius;
        h = h * 31 + Float.floatToIntBits(c.globalDimAlpha);
        h = h * 31 + Float.floatToIntBits(c.bubbleGlassMatteAlpha);
        h = h * 31 + c.glassPreset;
        h = h * 31 + Float.floatToIntBits(c.glassRefractIor);
        h = h * 31 + Float.floatToIntBits(c.glassThickness);
        h = h * 31 + Float.floatToIntBits(c.glassSpecular);
        h = h * 31 + Float.floatToIntBits(c.glassMatteAlpha);
        h = h * 31 + Float.floatToIntBits(c.glassBorderLight);
        h = h * 31 + c.bubblePaddingPercent;
        h = h * 31 + c.iconCornerRadius;
        h = h * 31 + Float.floatToIntBits(c.iconAlpha);
        h = h * 31 + c.appIconCropMode;
        h = h * 31 + (c.glyphPadEnabled ? 1 : 0);
        h = h * 31 + c.glyphPadColor;
        h = h * 31 + c.glyphPadMode;
        return h;
    }

    /**
     * 控制是否启用底层窗口级 SurfaceFlinger BackgroundBlurDrawable。
     * 应用内列表卡片（ShortcutCardList）关闭以避免逐图标触发全屏窗口模糊；
     * 主应用预览与真机面板均启用（壁纸 Surface 在窗口后方，窗口级模糊即实时背景模糊）。
     */
    public void setSystemBlurEnabled(boolean enabled) {
        if (this.systemBlurEnabled != enabled) {
            this.systemBlurEnabled = enabled;
            if (!enabled) {
                blurSession.release();
            }
            ensureBlurAndRefreshBackground();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (systemBlurEnabled) {
            post(this::ensureBlurAndRefreshBackground);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && systemBlurEnabled && (w != oldw || h != oldh)) {
            ensureBlurAndRefreshBackground();
        }
    }

    private void applyIconPadding() {
        int iconPadding = (int) (sizePx * (Math.max(0, Math.min(40, paddingPercent)) / 100f));
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    }

    /** 组件级统一材质参数（玻璃光学 + 磨砂浓度，与圆形视界底衬 / 顶部提示胶囊同源） */
    private HyperOSMaterialFactory.GlassParams glassParams() {
        if (config != null) {
            return config.glassParamsFor(HyperOpenConfig.COMPONENT_BUBBLE);
        }
        return HyperOSMaterialFactory.GlassParams.fromConfig(null);
    }

    /**
     * blur region 圆角 = 本地几何圆角（<b>不乘缩放</b>）。
     * <p>
     * background 挂载契约下，blur region 的注册锚点携带视图 RenderNode 变换
     * （setScaleX/Y 的鱼眼缩放），缩放由锚点天然携带；若再乘 scale 预补偿，
     * 缩放会被应用两次（区域按倍率²增长），表现为放大时气泡外鼓出一圈
     * 半透明磨砂底衬。历史上的「本地圆角 × scale」补偿只适用于旧的
     * 字段持有注册锚点（不含视图变换），background 契约下必须去掉。
     */
    private float getBlurCornerRadiusPx() {
        int w = getWidth() > 0 ? getWidth() : sizePx;
        int h = getHeight() > 0 ? getHeight() : sizePx;
        return BubbleBlurSpec.cornerRadiusPx(Math.min(w, h), cornerRadiusPercent);
    }

    /** blur region bounds = 视图本地 bounds（缩放由注册锚点携带，严禁再预缩放） */
    private Rect blurBounds() {
        int w = getWidth() > 0 ? getWidth() : sizePx;
        int h = getHeight() > 0 ? getHeight() : sizePx;
        return new Rect(0, 0, w, h);
    }

    private int getBlurRadiusPx() {
        // 留白 0% = 气泡整体关闭，气泡模糊跟随停用（开关规格统一由 BubbleBlurSpec 提供）
        if (!systemBlurEnabled || !BubbleBlurSpec.isEnabled(blurRadiusDp, paddingPercent)) return 0;
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, blurRadiusDp, getResources().getDisplayMetrics()
        ));
    }

    /**
     * 确保 SurfaceFlinger 硬件 BackgroundBlurDrawable 已按系统界面契约挂载注册，
     * 并与光学层合成为统一的圆形气泡背景。
     */
    public void ensureBlurAndRefreshBackground() {
        refreshBackground();
    }

    private void refreshBackground() {
        HyperOSMaterialFactory.GlassParams glassParams = glassParams();
        opticDrawable.setStyle(activeStyle);
        opticDrawable.setGlassParams(glassParams);
        opticDrawable.setSelected(isSelected);
        opticDrawable.setCornerRadiusPercent(cornerRadiusPercent);
        applyEffectiveDim();

        if (paddingPercent <= 0) {
            // 留白 0% = 气泡整体关闭：不绘制气泡底衬，模糊与压暗一并停用，仅保留图标
            blurSession.release();
            if (getBackground() != null) {
                setBackground(null);
            }
            if (opticView != null) {
                opticView.setVisibility(GONE);
            }
            return;
        }
        if (opticView != null) {
            opticView.setVisibility(VISIBLE);
        }

        int blurPx = getBlurRadiusPx();
        Drawable blur = null;
        if (blurPx > 0) {
            int tint = HyperOSMaterialFactory.getUnifiedBlurTint(activeStyle, glassParams);
            blur = blurSession.ensureFor(this, blurBounds(), getBlurCornerRadiusPx(), blurPx, tint);
        } else {
            blurSession.release();
        }
        // 系统界面契约：blur drawable 必须挂背景槽完成 blur region 注册
        if (getBackground() != blur) {
            setBackground(blur);
        }
        if (blur != null) {
            syncBlurCornerRadiusWithViewScale();
        }
    }

    public void setMaterialStyle(int style) {
        if (this.activeStyle != style) {
            this.activeStyle = style;
            opticDrawable.setStyle(style);
            ensureBlurAndRefreshBackground();
        }
    }

    /** 气泡留白区域背景压暗 (0.0 ~ 1.0)；留白 0%（气泡关闭）时实际应用为 0 */
    public void setDimAlpha(float alpha) {
        this.dimAlpha = alpha;
        applyEffectiveDim();
    }

    /** 按气泡开关应用压暗：留白 0% 气泡关闭时压暗跟随停用 */
    private void applyEffectiveDim() {
        opticDrawable.setDimAlpha(paddingPercent > 0 ? dimAlpha : 0f);
    }

    public void setBlurRadius(float radiusDp) {
        float clamped = Math.max(0f, radiusDp);
        if (Math.abs(this.blurRadiusDp - clamped) > 0.01f) {
            this.blurRadiusDp = clamped;
            ensureBlurAndRefreshBackground();
        }
    }

    public void setBubblePaddingPercent(int percent) {
        if (this.paddingPercent != percent) {
            this.paddingPercent = percent;
            applyIconPadding();
            invalidate();
            // 留白变化会切换气泡开关（0% 关闭气泡）：重建底衬、模糊与压暗
            ensureBlurAndRefreshBackground();
        }
    }

    public void setCornerRadiusPercent(int percent) {
        int clamped = Math.max(0, Math.min(50, percent));
        if (this.cornerRadiusPercent != clamped) {
            this.cornerRadiusPercent = clamped;
            opticDrawable.setCornerRadiusPercent(clamped);
            invalidateOutline();
            loadItemIcon();
            ensureBlurAndRefreshBackground();
        }
    }

    public int getCornerRadiusPercent() {
        return cornerRadiusPercent;
    }

    public void resetBlur() {
        animate().cancel();
        this.isSelected = false;
        this.fisheyeScale = 1.0f;
        this.currentScale = 1.0f;
        setScaleX(1.0f);
        setScaleY(1.0f);
        opticDrawable.setSelected(false);
        blurSession.release();
        refreshBackground();
    }

    public void setIconAlpha(float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (iconView != null && Math.abs(iconView.getAlpha() - clamped) > 0.002f) {
            iconView.setAlpha(clamped);
            opticDrawable.setAlpha(Math.round(clamped * 255f));
            opticDrawable.invalidateSelf();
        }
    }

    public float getIconAlpha() {
        return iconView != null ? iconView.getAlpha() : 1.0f;
    }

    /**
     * 选中状态只负责视觉反馈（辉光、气泡描边高亮）；缩放一律由鱼眼曲线经
     * {@link #applyScale(float)} 下发，不再叠加独立选中倍率，避免 maxScale 之外的隐式乘法。
     */
    public void setSelectedState(boolean selected) {
        if (this.isSelected != selected) {
            this.isSelected = selected;
            opticDrawable.setSelected(selected);
        }
    }

    public void applyScale(float scale) {
        if (Math.abs(this.fisheyeScale - scale) > 0.0005f) {
            this.fisheyeScale = scale;
            applyEffectiveScale();
        }
    }

    private void applyEffectiveScale() {
        if (Math.abs(this.currentScale - fisheyeScale) > 0.0005f || Math.abs(getScaleX() - fisheyeScale) > 0.0005f) {
            this.currentScale = fisheyeScale;
            setScaleX(fisheyeScale);
            setScaleY(fisheyeScale);
            // 鱼眼/动画逐帧同步背景模糊圆角：blur region 的 bounds 走变换后窗口坐标，
            // 圆角必须乘 scale 才能严格贴合放大后的图标区域（否则模糊呈圆角方形溢出）
            syncBlurCornerRadiusWithViewScale();
        }
    }

    /**
     * 同步 blur region 几何。
     * <p>
     * 缩放已由注册锚点（视图 RenderNode 变换）携带，本地几何（bounds / 圆角）
     * 不随缩放改变——保留此入口用于尺寸 / 圆角变化后的兜底同步；
     * 鱼眼逐帧调用时内部几何未变即短路，零反射开销。
     * （历史注释中「getLocationInWindow 不随 setScaleX/Y 变化需手动预缩放」的
     * 结论只适用于旧字段持有锚点；background 契约下再预缩放会双重放大，
     * 表现为放大时气泡外鼓出一圈半透明底衬。）
     */
    public void syncBlurCornerRadiusWithViewScale() {
        blurSession.syncGeometry(blurBounds(), getBlurCornerRadiusPx());
    }

    public void loadItemIcon() {
        // 统一由 IconGlyphFactory 解析，传入当前实时配置（裁切模式、Miuix 底垫及颜色等）
        Drawable d = IconGlyphFactory.resolve(getContext(), item, config);
        iconView.setImageDrawable(d);
        iconView.setColorFilter(null);
        if (d != null) {
            d.setCallback(iconView);
        }
        iconView.requestLayout();
        iconView.invalidate();
        invalidate();
    }
}
